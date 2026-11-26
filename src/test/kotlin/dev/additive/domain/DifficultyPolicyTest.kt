package dev.additive.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class DifficultyPolicyTest {
    @Test
    fun `promotes on the twentieth consecutive correct answer and resets streaks`() {
        val before = Progress(Difficulty.LEVEL_1, correctStreak = 18)

        val nineteenth = DifficultyPolicy.next(before, correct = true)
        val twentieth = DifficultyPolicy.next(nineteenth, correct = true)

        assertEquals(Progress(Difficulty.LEVEL_1, correctStreak = 19), nineteenth)
        assertEquals(Progress(Difficulty.LEVEL_2), twentieth)
    }

    @Test
    fun `demotes on the sixth consecutive wrong answer and resets streaks`() {
        val before = Progress(Difficulty.LEVEL_3, wrongStreak = 4)

        val fifth = DifficultyPolicy.next(before, correct = false)
        val sixth = DifficultyPolicy.next(fifth, correct = false)

        assertEquals(Progress(Difficulty.LEVEL_3, wrongStreak = 5), fifth)
        assertEquals(Progress(Difficulty.LEVEL_2), sixth)
    }

    @Test
    fun `switching outcomes breaks the previous streak`() {
        val before = Progress(Difficulty.LEVEL_2, correctStreak = 19)

        val wrong = DifficultyPolicy.next(before, correct = false)
        val correct = DifficultyPolicy.next(wrong, correct = true)

        assertEquals(Progress(Difficulty.LEVEL_2, wrongStreak = 1), wrong)
        assertEquals(Progress(Difficulty.LEVEL_2, correctStreak = 1), correct)
    }

    @Test
    fun `streaks saturate at the highest and lowest levels`() {
        val top = Progress(Difficulty.LEVEL_4, correctStreak = 19)
        val bottom = Progress(Difficulty.LEVEL_1, wrongStreak = 5)

        val topAtLimit = DifficultyPolicy.next(top, correct = true)
        val bottomAtLimit = DifficultyPolicy.next(bottom, correct = false)

        assertEquals(Progress(Difficulty.LEVEL_4, correctStreak = 20), topAtLimit)
        assertEquals(topAtLimit, DifficultyPolicy.next(topAtLimit, correct = true))
        assertEquals(Progress(Difficulty.LEVEL_1, wrongStreak = 6), bottomAtLimit)
        assertEquals(bottomAtLimit, DifficultyPolicy.next(bottomAtLimit, correct = false))
    }
}
