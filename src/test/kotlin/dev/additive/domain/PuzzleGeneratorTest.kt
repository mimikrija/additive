package dev.additive.domain

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class PuzzleGeneratorTest {
    private val additionAndSubtractionLimits = mapOf(
        Difficulty.LEVEL_1 to 7,
        Difficulty.LEVEL_2 to 10,
        Difficulty.LEVEL_3 to 16,
        Difficulty.LEVEL_4 to 21,
    )

    @Test
    fun `a seeded problem is independent of earlier calls`() {
        val generator = PuzzleGenerator()
        val first = generator.generate(Difficulty.LEVEL_2, seed = 42L)

        generator.generate(Difficulty.LEVEL_2, seed = 99L)

        assertEquals(first, generator.generate(Difficulty.LEVEL_2, seed = 42L))
    }

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

    private fun assertValidProblems(
        difficulty: Difficulty,
        type: ProblemType,
        maxNumber: Int,
        expectedAnswer: (Int, Int) -> Int,
    ) {
        val generator = PuzzleGenerator(Random(42))
        val problems = List(2_000) { generator.generate(difficulty, type) }
        val operandRange = 0..maxNumber
        val positiveRange = 1..maxNumber
        val answerRange = if (type == ProblemType.ADDITION) positiveRange else operandRange

        for (problem in problems) {
            assertEquals(type, problem.type)
            assertContains(positiveRange, problem.first)
            assertContains(operandRange, problem.second)
            assertContains(answerRange, problem.answer)
            assertEquals(expectedAnswer(problem.first, problem.second), problem.answer)
        }

        val limitedValues = problems.map { if (type == ProblemType.SUBTRACTION) it.first else it.answer }
        assertContains(limitedValues, 1)
        assertContains(limitedValues, maxNumber)
    }
}
