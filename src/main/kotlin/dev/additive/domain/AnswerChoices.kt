package dev.additive.domain

import kotlin.math.abs
import kotlin.random.Random

internal fun answerChoices(answer: Int, difficulty: Difficulty, random: Random): List<Int> {
    val alternatives = (0..difficulty.maxNumber).filter { it != answer }
    val nearby = alternatives.filter { abs(it - answer) <= 3 }.shuffled(random).take(2)
    val other = (alternatives - nearby.toSet()).shuffled(random).take(2)
    return (nearby + other + answer).shuffled(random)
}
