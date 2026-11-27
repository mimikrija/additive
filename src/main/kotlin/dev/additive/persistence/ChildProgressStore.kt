package dev.additive.persistence

import dev.additive.domain.Difficulty
import dev.additive.domain.DifficultyPolicy
import dev.additive.domain.Progress
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.util.UUID

private object Children : Table("children") {
    val id = uuid("id")
    val level = text("level")
    val correctStreak = integer("correct_streak")
    val wrongStreak = integer("wrong_streak")

    override val primaryKey = PrimaryKey(id)
}

internal fun Transaction.advanceChildProgress(childId: UUID, correct: Boolean): Progress? {
    val child = Children.selectAll()
        .where { Children.id eq childId }
        .forUpdate()
        .singleOrNull() ?: return null

    val current = Progress(
        difficulty = Difficulty.valueOf(child[Children.level]),
        correctStreak = child[Children.correctStreak],
        wrongStreak = child[Children.wrongStreak],
    )
    val next = DifficultyPolicy.next(current, correct)
    Children.update({ Children.id eq childId }) {
        it[level] = next.difficulty.name
        it[correctStreak] = next.correctStreak
        it[wrongStreak] = next.wrongStreak
    }
    return next
}
