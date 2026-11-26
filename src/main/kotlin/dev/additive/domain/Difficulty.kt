package dev.additive.domain

enum class Difficulty(val maxNumber: Int, val multiplicationMax: Int? = null) {
    LEVEL_1(maxNumber = 7),
    LEVEL_2(maxNumber = 10),
    LEVEL_3(maxNumber = 16, multiplicationMax = 12),
    LEVEL_4(maxNumber = 21, multiplicationMax = 21),
}
