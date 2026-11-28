package dev.additive.persistence

import dev.additive.domain.Difficulty
import dev.additive.domain.DifficultyPolicy
import dev.additive.domain.Progress
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.update
import java.util.UUID

internal object Children : Table("children") {
    val id = uuid("id")
    val token = uuid("token").databaseGenerated()
    val level = text("level").databaseGenerated()
    val correctStreak = integer("correct_streak").databaseGenerated()
    val wrongStreak = integer("wrong_streak").databaseGenerated()

    override val primaryKey = PrimaryKey(id)
}

internal fun Transaction.advanceChildProgress(childId: UUID, correct: Boolean): Progress? {
    val child = Children.select(Children.level, Children.correctStreak, Children.wrongStreak)
        .where { Children.id eq childId }
        .forUpdate()
        .singleOrNull() ?: return null

    val next = DifficultyPolicy.next(child.toProgress(), correct)
    Children.update({ Children.id eq childId }) {
        it[level] = next.difficulty.name
        it[correctStreak] = next.correctStreak
        it[wrongStreak] = next.wrongStreak
    }
    return next
}

internal fun ResultRow.toProgress() = Progress(
    difficulty = Difficulty.valueOf(this[Children.level]),
    correctStreak = this[Children.correctStreak],
    wrongStreak = this[Children.wrongStreak],
)
