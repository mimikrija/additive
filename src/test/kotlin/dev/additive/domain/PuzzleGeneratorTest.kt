package dev.additive.domain

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class PuzzleGeneratorTest {
    @Test
    fun `generates addition problems by default`() {
        assertEquals(ProblemType.ADDITION, PuzzleGenerator(Random(42)).generate().type)
    }

    @Test
    fun `generates addition problems within range`() {
        assertValidProblems(ProblemType.ADDITION, Int::plus)
    }

    @Test
    fun `generates subtraction problems within range`() {
        assertValidProblems(ProblemType.SUBTRACTION, Int::minus)
    }

    @Test
    fun `generates multiplication problems within range`() {
        assertValidProblems(ProblemType.MULTIPLICATION, Int::times)
    }

    private fun assertValidProblems(type: ProblemType, expectedAnswer: (Int, Int) -> Int) {
        val generator = PuzzleGenerator(Random(42))
        val problems = List(2_000) { generator.generate(type) }

        for (problem in problems) {
            assertEquals(type, problem.type)
            assertContains(0..30, problem.first)
            assertContains(0..30, problem.second)
            assertContains(0..30, problem.answer)
            assertEquals(expectedAnswer(problem.first, problem.second), problem.answer)
        }

        val answers = problems.map { it.answer }
        assertContains(answers, 0)
        assertContains(answers, 30)
    }
}
