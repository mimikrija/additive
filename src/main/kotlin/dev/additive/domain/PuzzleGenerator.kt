package dev.additive.domain

import kotlin.random.Random

class PuzzleGenerator(private val random: Random = Random.Default) {
    fun generate(difficulty: Difficulty, type: ProblemType = ProblemType.ADDITION): Problem = when (type) {
        ProblemType.ADDITION -> {
            val total = random.nextInt(difficulty.maxNumber + 1)
            val first = random.nextInt(total + 1)
            Problem(first, total - first, type)
        }

        ProblemType.SUBTRACTION -> {
            val first = random.nextInt(difficulty.maxNumber + 1)
            val second = random.nextInt(first + 1)
            Problem(first, second, type)
        }

        ProblemType.MULTIPLICATION -> {
            val maxProduct = requireNotNull(difficulty.multiplicationMax) {
                "Multiplication is unavailable at ${difficulty.name}"
            }
            val first = random.nextInt(maxProduct + 1)
            val maxSecond = if (first == 0) maxProduct else maxProduct / first
            val second = random.nextInt(maxSecond + 1)
            Problem(first, second, type)
        }
    }
}
