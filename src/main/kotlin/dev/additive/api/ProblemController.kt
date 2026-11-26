package dev.additive.api

import dev.additive.domain.Difficulty
import dev.additive.domain.Problem
import dev.additive.domain.ProblemType
import dev.additive.domain.PuzzleGenerator
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/problems")
class ProblemController {
    private val generator = PuzzleGenerator()
    private val operationsByDifficulty = Difficulty.entries.associateWith { difficulty ->
        ProblemType.entries.filter { it != ProblemType.MULTIPLICATION || difficulty.multiplicationMax != null }
    }

    @GetMapping("/options")
    fun options(): Map<Difficulty, List<ProblemType>> = operationsByDifficulty

    @GetMapping
    fun generateProblem(
        @RequestParam("difficulty") difficulty: Difficulty,
        @RequestParam("type") type: ProblemType,
    ): Problem {
        if (type !in operationsByDifficulty.getValue(difficulty)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "$type is unavailable at $difficulty")
        }
        return generator.generate(difficulty, type)
    }

    @GetMapping("/addition")
    fun generateAdditionProblem(): Problem = generator.generate(Difficulty.LEVEL_1)
}
