package dev.additive.domain

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PuzzleGeneratorTest {
    private val additionAndSubtractionLimits = mapOf(
        Difficulty.LEVEL_1 to 7,
        Difficulty.LEVEL_2 to 10,
        Difficulty.LEVEL_3 to 16,
        Difficulty.LEVEL_4 to 21,
    )
    private val multiplicationLimits = mapOf(
        Difficulty.LEVEL_3 to 12,
        Difficulty.LEVEL_4 to 21,
    )

    @Test
    fun `generates addition problems by default`() {
        assertEquals(ProblemType.ADDITION, PuzzleGenerator().generate(Difficulty.LEVEL_1).type)
    }

    @Test
    fun `limits addition answers by difficulty`() {
        for ((difficulty, maxNumber) in additionAndSubtractionLimits) {
            assertValidProblems(difficulty, ProblemType.ADDITION, maxNumber, Int::plus)
        }
    }

    @Test
    fun `limits the first subtraction number by difficulty and keeps answers nonnegative`() {
        for ((difficulty, maxNumber) in additionAndSubtractionLimits) {
            assertValidProblems(difficulty, ProblemType.SUBTRACTION, maxNumber, Int::minus)
        }
    }

    @Test
    fun `limits multiplication answers by difficulty`() {
        for ((difficulty, maxNumber) in multiplicationLimits) {
            assertValidProblems(difficulty, ProblemType.MULTIPLICATION, maxNumber, Int::times)
        }
    }

    @Test
    fun `does not generate multiplication problems at level two or below`() {
        val generator = PuzzleGenerator()

        for (difficulty in listOf(Difficulty.LEVEL_1, Difficulty.LEVEL_2)) {
            assertFailsWith<IllegalArgumentException>("Multiplication should be unavailable at $difficulty") {
                generator.generate(difficulty, ProblemType.MULTIPLICATION)
            }
        }
    }

    private fun assertValidProblems(
        difficulty: Difficulty,
        type: ProblemType,
        maxNumber: Int,
        expectedAnswer: (Int, Int) -> Int,
    ) {
        val generator = PuzzleGenerator(Random(42))
        val problems = List(2_000) { generator.generate(difficulty, type) }
        val allowedRange = 0..maxNumber

        for (problem in problems) {
            assertEquals(type, problem.type)
            assertContains(allowedRange, problem.first)
            assertContains(allowedRange, problem.second)
            assertContains(allowedRange, problem.answer)
            assertEquals(expectedAnswer(problem.first, problem.second), problem.answer)
        }

        val limitedValues = problems.map { if (type == ProblemType.SUBTRACTION) it.first else it.answer }
        assertContains(limitedValues, 0)
        assertContains(limitedValues, maxNumber)
    }
}
