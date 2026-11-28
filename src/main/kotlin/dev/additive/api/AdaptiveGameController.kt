package dev.additive.api

import dev.additive.persistence.AdaptiveGameStore
import dev.additive.persistence.AttemptConflict
import dev.additive.persistence.GameNotFound
import dev.additive.persistence.ProblemNotFound
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

private const val GAME_TOKEN = "X-Game-Token"

@RestController
@RequestMapping("/api")
class AdaptiveGameController(private val game: AdaptiveGameStore) {
    @PostMapping("/children")
    @ResponseStatus(HttpStatus.CREATED)
    fun createChild() = GameToken(game.createChild())

    @PostMapping("/me/problem")
    fun issueProblem(@RequestHeader(GAME_TOKEN, required = false) token: UUID?) =
        game.issueProblem(token ?: throw GameNotFound())

    @PostMapping("/me/attempts")
    fun submit(
        @RequestHeader(GAME_TOKEN, required = false) token: UUID?,
        @RequestParam attemptId: UUID,
        @RequestParam problemId: UUID,
        @RequestParam answer: Int,
    ) = if (answer >= 0) {
        game.submit(token ?: throw GameNotFound(), attemptId, problemId, answer)
    } else {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Answer must be nonnegative")
    }

    @GetMapping("/me/progress")
    fun progress(@RequestHeader(GAME_TOKEN, required = false) token: UUID?) =
        ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(game.progress(token ?: throw GameNotFound()))

    @ExceptionHandler(GameNotFound::class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    fun unknownGame() = Unit

    @ExceptionHandler(ProblemNotFound::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun unknownProblem() = Unit

    @ExceptionHandler(AttemptConflict::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun attemptConflict() = Unit
}

data class GameToken(val token: UUID)
