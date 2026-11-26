package dev.additive.api

import dev.additive.domain.Difficulty
import dev.additive.domain.Problem
import dev.additive.domain.PuzzleGenerator
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class ProblemController {
    private val generator = PuzzleGenerator()

    @GetMapping("/api/problems/addition")
    fun generateAdditionProblem(): Problem = generator.generate(Difficulty.LEVEL_1)
}
