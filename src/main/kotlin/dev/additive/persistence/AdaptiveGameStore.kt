package dev.additive.persistence

import dev.additive.domain.Difficulty
import dev.additive.domain.ProblemType
import dev.additive.domain.Progress
import dev.additive.domain.PuzzleGenerator
import dev.additive.domain.answerChoices
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.exists
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.notExists
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.springframework.stereotype.Repository
import java.util.UUID
import javax.sql.DataSource
import kotlin.random.Random

private const val ACCURACY_WINDOW_SIZE = 20

private object IssuedProblems : Table("problems") {
    val id = uuid("id")
    val childId = uuid("child_id")
    val sequence = long("sequence_number")
    val level = text("level")
    val operation = text("operation")
    val first = integer("first_operand")
    val second = integer("second_operand")
    val answer = integer("answer")
    val solved = bool("solved").databaseGenerated()
    val skipped = bool("skipped").databaseGenerated()
    override val primaryKey = PrimaryKey(id)
}

private object Attempts : Table("attempts") {
    val id = uuid("id")
    val problemId = uuid("problem_id")
    val answer = integer("submitted_answer")
    val correct = bool("correct")
    val counted = bool("counted")
    val rewarded = bool("rewarded")
    override val primaryKey = PrimaryKey(id)
}

private object Icecreams : Table("icecreams") {
    val childId = uuid("child_id")
    val operation = text("operation")
    val first = integer("first_operand")
    val second = integer("second_operand")
    override val primaryKey = PrimaryKey(childId, operation, first, second)
}

@Repository
class AdaptiveGameStore(dataSource: DataSource) {
    private val database = Database.connect(dataSource)
    private val generator = PuzzleGenerator()

    fun createChild(): UUID = transaction(database) {
        val gameToken = UUID.randomUUID()
        Children.insert {
            it[id] = UUID.randomUUID()
            it[token] = gameToken
        }
        gameToken
    }

    fun issueProblem(token: UUID): IssuedPrompt = transaction(database) {
        val child = lockChild(token)
        val childId = child[Children.id]
        val latest = IssuedProblems.selectAll().where { IssuedProblems.childId eq childId }
            .orderBy(IssuedProblems.sequence, SortOrder.DESC).limit(1).singleOrNull()
        if (latest != null && !latest[IssuedProblems.solved] && !latest[IssuedProblems.skipped]) {
            val problemId = latest[IssuedProblems.id]
            return@transaction IssuedPrompt(
                id = problemId,
                first = latest[IssuedProblems.first],
                second = latest[IssuedProblems.second],
                type = ProblemType.valueOf(latest[IssuedProblems.operation]),
                choices = latest.choices(),
                triedChoices = wrongAnswers(problemId),
            )
        }

        val nextSequence = (latest?.get(IssuedProblems.sequence) ?: 0L) + 1
        val difficulty = child.toProgress().difficulty
        val seed = childId.mostSignificantBits xor childId.leastSignificantBits xor nextSequence
        val problem = generator.generate(difficulty, seed)
        val problemId = UUID.randomUUID()
        IssuedProblems.insert {
            it[id] = problemId
            it[IssuedProblems.childId] = childId
            it[sequence] = nextSequence
            it[level] = difficulty.name
            it[operation] = problem.type.name
            it[first] = problem.first
            it[second] = problem.second
            it[answer] = problem.answer
        }
        IssuedPrompt(problemId, problem.first, problem.second, problem.type, choices(problemId, problem.answer, difficulty))
    }

    fun submit(token: UUID, attemptId: UUID, problemId: UUID, answer: Int): SubmittedAttempt = transaction(database) {
        val childId = lockChild(token)[Children.id]
        val problem = problemForChild(problemId, childId)

        val previous = Attempts.selectAll().where { Attempts.id eq attemptId }.singleOrNull()
        if (previous != null) {
            if (previous[Attempts.problemId] != problemId || previous[Attempts.answer] != answer) {
                throw AttemptConflict()
            }
            return@transaction SubmittedAttempt(
                correct = previous[Attempts.correct],
                counted = previous[Attempts.counted],
                rewarded = previous[Attempts.rewarded],
            )
        }

        if (answer !in problem.choices()) throw InvalidAnswerChoice()

        val correct = answer == problem[IssuedProblems.answer]
        val counted = !problem[IssuedProblems.solved] && !problem[IssuedProblems.skipped]
        val rewarded = counted && correct && awardIcecream(childId, problem)
        if (counted) {
            advanceChildProgress(childId, correct)
            if (correct) {
                IssuedProblems.update({ IssuedProblems.id eq problemId }) { it[solved] = true }
            }
        }
        val inserted = Attempts.insertIgnore {
            it[id] = attemptId
            it[Attempts.problemId] = problemId
            it[Attempts.answer] = answer
            it[Attempts.correct] = correct
            it[Attempts.counted] = counted
            it[Attempts.rewarded] = rewarded
        }.insertedCount
        if (inserted == 0) throw AttemptConflict()
        SubmittedAttempt(correct, counted, rewarded)
    }

    fun skip(token: UUID, problemId: UUID): Unit = transaction(database) {
        val childId = lockChild(token)[Children.id]
        val problem = problemForChild(problemId, childId)
        if (problem[IssuedProblems.skipped]) return@transaction
        if (problem[IssuedProblems.solved] || wrongAnswers(problemId).isEmpty()) throw CannotSkipProblem()

        IssuedProblems.update({ IssuedProblems.id eq problemId }) { it[skipped] = true }
    }

    fun progress(token: UUID): GameProgress = transaction(database) {
        val child = lockChild(token)
        val childId = child[Children.id]
        GameProgress(
            progress = child.toProgress(),
            icecreams = Icecreams.selectAll().where { Icecreams.childId eq childId }.count(),
            currentCorrectStreak = child[Children.currentCorrectRun],
            bestCorrectStreak = child[Children.bestCorrectStreak],
            recentAccuracy = recentAccuracy(childId),
        )
    }

    private fun recentAccuracy(childId: UUID): RecentAccuracy {
        fun countedAttempts() = Attempts.select(Attempts.id).where {
            (Attempts.problemId eq IssuedProblems.id) and (Attempts.counted eq true)
        }
        val firstTryCorrect = notExists(countedAttempts().andWhere { Attempts.correct eq false })
        val results = IssuedProblems.select(firstTryCorrect)
            .where { (IssuedProblems.childId eq childId) and exists(countedAttempts()) }
            .orderBy(IssuedProblems.sequence, SortOrder.DESC)
            .limit(ACCURACY_WINDOW_SIZE)
            .map { it[firstTryCorrect] }

        return RecentAccuracy(results.count { it }, results.size)
    }

    private fun lockChild(token: UUID): ResultRow = Children.selectAll()
        .where { Children.token eq token }.forUpdate().singleOrNull() ?: throw GameNotFound()

    private fun problemForChild(problemId: UUID, childId: UUID): ResultRow = IssuedProblems.selectAll()
        .where { (IssuedProblems.id eq problemId) and (IssuedProblems.childId eq childId) }
        .singleOrNull() ?: throw ProblemNotFound()

    private fun ResultRow.choices(): List<Int> = choices(
        this[IssuedProblems.id], this[IssuedProblems.answer], Difficulty.valueOf(this[IssuedProblems.level]),
    )

    private fun choices(problemId: UUID, answer: Int, difficulty: Difficulty): List<Int> =
        answerChoices(answer, difficulty, Random(problemId.mostSignificantBits xor problemId.leastSignificantBits))

    private fun wrongAnswers(problemId: UUID): List<Int> = Attempts.select(Attempts.answer).withDistinct()
        .where { (Attempts.problemId eq problemId) and (Attempts.correct eq false) and (Attempts.counted eq true) }
        .orderBy(Attempts.answer).map { it[Attempts.answer] }

    private fun awardIcecream(childId: UUID, problem: ResultRow): Boolean = Icecreams.insertIgnore {
        it[Icecreams.childId] = childId
        it[operation] = problem[IssuedProblems.operation]
        it[first] = problem[IssuedProblems.first]
        it[second] = problem[IssuedProblems.second]
    }.insertedCount == 1
}

data class IssuedPrompt(
    val id: UUID,
    val first: Int,
    val second: Int,
    val type: ProblemType,
    val choices: List<Int>,
    val triedChoices: List<Int> = emptyList(),
)
data class SubmittedAttempt(val correct: Boolean, val counted: Boolean, val rewarded: Boolean)
data class GameProgress(
    val progress: Progress,
    val icecreams: Long,
    val currentCorrectStreak: Long,
    val bestCorrectStreak: Long,
    val recentAccuracy: RecentAccuracy,
)

data class RecentAccuracy(val firstTryCorrect: Int, val attempted: Int, val windowSize: Int = ACCURACY_WINDOW_SIZE)

class GameNotFound : RuntimeException()
class ProblemNotFound : RuntimeException()
class AttemptConflict : RuntimeException()
class CannotSkipProblem : RuntimeException()
class InvalidAnswerChoice : RuntimeException()
