package dev.additive.domain

data class Progress(
    val difficulty: Difficulty = Difficulty.LEVEL_1,
    val correctStreak: Int = 0,
    val wrongStreak: Int = 0,
)

object DifficultyPolicy {
    private const val PROMOTION_STREAK = 20
    private const val DEMOTION_STREAK = 5

    fun next(progress: Progress, correct: Boolean): Progress {
        val level = progress.difficulty

        if (correct) {
            val streak = (progress.correctStreak + 1).coerceAtMost(PROMOTION_STREAK)
            val higher = Difficulty.entries.getOrNull(level.ordinal + 1)
            return if (streak == PROMOTION_STREAK && higher != null) {
                Progress(higher)
            } else {
                Progress(level, correctStreak = streak)
            }
        }

        val streak = (progress.wrongStreak + 1).coerceAtMost(DEMOTION_STREAK)
        val lower = Difficulty.entries.getOrNull(level.ordinal - 1)
        return if (streak == DEMOTION_STREAK && lower != null) {
            Progress(lower)
        } else {
            Progress(level, wrongStreak = streak)
        }
    }
}
