package dev.additive.api

import dev.additive.domain.Difficulty
import dev.additive.domain.Problem
import dev.additive.domain.ProblemType
import dev.additive.domain.PuzzleGenerator
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal

@RestController
@RequestMapping("/api/problems")
class ProblemController {
    private val generator = PuzzleGenerator()
    private val operandRange = 0..Difficulty.entries.maxOf { maxOf(it.maxNumber, it.multiplicationMax ?: 0) }
    private val operationsByDifficulty = Difficulty.entries.associateWith { difficulty ->
        ProblemType.entries.filter { it != ProblemType.MULTIPLICATION || difficulty.multiplicationMax != null }
    }

    @GetMapping("/options")
    fun options(): Map<Difficulty, List<ProblemType>> = operationsByDifficulty

    @GetMapping
    fun generateProblem(
        @RequestParam("difficulty") difficulty: Difficulty,
        @RequestParam("type") type: ProblemType,
    ): ProblemPrompt {
        if (type !in operationsByDifficulty.getValue(difficulty)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "$type is unavailable at $difficulty")
        }
        return ProblemPrompt(generator.generate(difficulty, type))
    }

    @GetMapping("/addition")
    fun generateAdditionProblem(): ProblemPrompt = ProblemPrompt(generator.generate(Difficulty.LEVEL_1))

    @PostMapping("/check", consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE])
    fun checkAnswer(
        @RequestParam("first") first: Int,
        @RequestParam("second") second: Int,
        @RequestParam("type") type: ProblemType,
        @RequestParam("answer") answer: BigDecimal,
    ): AnswerResult {
        if (first !in operandRange || second !in operandRange) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Operands are outside the practice range")
        }
        val expectedAnswer = Problem(first, second, type).answer.toBigDecimal()
        return AnswerResult(answer.compareTo(expectedAnswer) == 0)
    }
}

data class ProblemPrompt(val first: Int, val second: Int, val type: ProblemType) {
    constructor(problem: Problem) : this(problem.first, problem.second, problem.type)
}

data class AnswerResult(val correct: Boolean)
