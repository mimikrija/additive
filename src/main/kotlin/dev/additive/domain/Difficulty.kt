package dev.additive.domain

enum class Difficulty(val maxNumber: Int) {
    LEVEL_1(maxNumber = 7),
    LEVEL_2(maxNumber = 10),
    LEVEL_3(maxNumber = 16),
    LEVEL_4(maxNumber = 21),
}
