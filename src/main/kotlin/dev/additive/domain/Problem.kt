package dev.additive.domain

enum class ProblemType {
    ADDITION,
    SUBTRACTION,
}

data class Problem(val first: Int, val second: Int, val type: ProblemType) {
    val answer: Int
        get() = when (type) {
            ProblemType.ADDITION -> first + second
            ProblemType.SUBTRACTION -> first - second
        }
}
