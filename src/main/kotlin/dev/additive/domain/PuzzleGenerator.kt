package dev.additive.domain

import kotlin.random.Random

private const val MAX_NUMBER = 30

class PuzzleGenerator(private val random: Random = Random.Default) {
    fun generate(type: ProblemType = ProblemType.ADDITION): Problem = when (type) {
        ProblemType.ADDITION -> {
            val total = random.nextInt(MAX_NUMBER + 1)
            val first = random.nextInt(total + 1)
            Problem(first, total - first, type)
        }

        ProblemType.SUBTRACTION -> {
            val first = random.nextInt(MAX_NUMBER + 1)
            val second = random.nextInt(first + 1)
            Problem(first, second, type)
        }

        ProblemType.MULTIPLICATION -> {
            val first = random.nextInt(MAX_NUMBER + 1)
            val maxSecond = if (first == 0) MAX_NUMBER else MAX_NUMBER / first
            val second = random.nextInt(maxSecond + 1)
            Problem(first, second, type)
        }
    }
}
