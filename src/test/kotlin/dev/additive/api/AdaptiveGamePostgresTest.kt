package dev.additive.api

import com.fasterxml.jackson.databind.JsonNode
import dev.additive.Application
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext
import org.springframework.boot.runApplication
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import org.testcontainers.containers.PostgreSQLContainer
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AdaptiveGamePostgresTest {
    @Test
    fun `a playable game resumes and counts a repeated expression without rewarding it twice`() {
        PostgreSQLContainer<Nothing>("postgres:16.11-alpine").use { postgres ->
            postgres.start()
            runApplication<Application>(
                "--server.port=0",
                "--spring.datasource.url=${postgres.jdbcUrl}",
                "--spring.datasource.username=${postgres.username}",
                "--spring.datasource.password=${postgres.password}",
            ).use { context ->
                val port = (context as ServletWebServerApplicationContext).webServer.port
                val client = RestClient.create("http://localhost:$port")
                val token = assertNotNull(client.post().uri("/api/children").retrieve().body(JsonNode::class.java))
                    .path("token").asText()
                val game = RestClient.builder().baseUrl("http://localhost:$port")
                    .defaultHeader("X-Game-Token", token).build()

                val first = game.issueProblem()
                assertEquals(0, game.progress().path("bestCorrectStreak").asInt())
                assertFalse(first.has("answer"))
                val firstId = first.path("id").asText()
                assertEquals(first, game.issueProblem())
                assertFailsWith<HttpClientErrorException.Conflict> { game.skip(firstId) }
                val operandOne = first.path("first").asInt()
                val operandTwo = first.path("second").asInt()
                val operation = first.path("type").asText()
                val answer = first.answer()
                val choices = first.path("choices").map { it.asInt() }
                assertEquals(5, choices.toSet().size)
                assertTrue(answer in choices)
                assertTrue(choices.all { it in 0..7 })
                assertFailsWith<HttpClientErrorException.BadRequest> { game.submit(UUID.randomUUID(), firstId, 8) }

                val wrongAnswer = choices.first { it != answer }
                val wrongId = UUID.randomUUID()
                val wrong = game.submit(wrongId, firstId, wrongAnswer)
                assertFalse(wrong.path("correct").asBoolean())
                assertEquals(wrong, game.submit(wrongId, firstId, wrongAnswer))
                assertEquals(listOf(wrongAnswer), game.issueProblem().path("triedChoices").map { it.asInt() })
                assertEquals(1, game.progress().path("progress").path("wrongStreak").asInt())
                assertFailsWith<HttpClientErrorException.Conflict> { game.submit(wrongId, firstId, answer) }

                val start = CountDownLatch(1)
                val results = Executors.newFixedThreadPool(2).use { executor ->
                    val submissions = List(2) {
                        executor.submit<JsonNode> {
                            start.await()
                            game.submit(UUID.randomUUID(), firstId, answer)
                        }
                    }
                    start.countDown()
                    submissions.map { it.get(10, TimeUnit.SECONDS) }
                }
                assertTrue(results.all { it.path("correct").asBoolean() })
                assertEquals(1, results.count { it.path("counted").asBoolean() })
                assertEquals(1, results.count { it.path("rewarded").asBoolean() })
                assertEquals(1, game.progress().path("progress").path("correctStreak").asInt())

                val repeatId = game.issueProblem().path("id").asText()
                assertNotEquals(firstId, repeatId)
                val jdbc = context.getBean(JdbcTemplate::class.java)
                assertEquals(1, jdbc.update(
                    "UPDATE problems SET operation = ?, first_operand = ?, second_operand = ?, answer = ? WHERE id = ?",
                    operation, operandOne, operandTwo, answer, UUID.fromString(repeatId),
                ))
                val repeated = game.submit(UUID.randomUUID(), repeatId, answer)
                assertTrue(repeated.path("counted").asBoolean())
                assertFalse(repeated.path("rewarded").asBoolean())
                val progress = game.progress()
                assertEquals(2, progress.path("progress").path("correctStreak").asInt())
                assertEquals(2, progress.path("currentCorrectStreak").asInt())
                assertEquals(2, progress.path("bestCorrectStreak").asInt())
                assertEquals(1, progress.path("icecreams").asInt())

                jdbc.update("UPDATE children SET level = 'LEVEL_2' WHERE token = ?", UUID.fromString(token))
                val toSkip = game.issueProblem()
                val toSkipId = toSkip.path("id").asText()
                for (choice in toSkip.wrongChoices()) {
                    game.submit(UUID.randomUUID(), toSkipId, choice)
                }
                assertEquals(4, game.progress().path("progress").path("wrongStreak").asInt())
                game.skip(toSkipId)
                game.skip(toSkipId)
                assertFalse(game.submit(UUID.randomUUID(), toSkipId, toSkip.answer()).path("counted").asBoolean())
                val afterSkip = game.progress()
                assertEquals(4, afterSkip.path("progress").path("wrongStreak").asInt())
                assertEquals(0, afterSkip.path("currentCorrectStreak").asInt())
                assertEquals(2, afterSkip.path("bestCorrectStreak").asInt())

                val next = game.issueProblem()
                val nextId = next.path("id").asText()
                assertNotEquals(toSkipId, nextId)
                game.submit(UUID.randomUUID(), nextId, next.wrongChoices().first())
                val demoted = game.progress()
                assertEquals("LEVEL_1", demoted.path("progress").path("difficulty").asText())
                assertEquals(0, demoted.path("progress").path("wrongStreak").asInt())
                assertEquals(1, demoted.path("icecreams").asInt())
            }
        }
    }

    private fun RestClient.issueProblem(): JsonNode =
        assertNotNull(post().uri("/api/me/problem").retrieve().body(JsonNode::class.java))

    private fun RestClient.submit(attemptId: UUID, problemId: String, answer: Int): JsonNode {
        val form = LinkedMultiValueMap<String, String>()
        form.add("attemptId", attemptId.toString())
        form.add("problemId", problemId)
        form.add("answer", answer.toString())
        return assertNotNull(
            post().uri("/api/me/attempts").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form).retrieve().body(JsonNode::class.java),
        )
    }

    private fun RestClient.progress(): JsonNode =
        assertNotNull(get().uri("/api/me/progress").retrieve().body(JsonNode::class.java))

    private fun RestClient.skip(problemId: String) {
        post().uri("/api/me/problem/skip?problemId={problemId}", problemId).retrieve().toBodilessEntity()
    }

    private fun JsonNode.answer(): Int {
        val first = path("first").asInt()
        val second = path("second").asInt()
        return if (path("type").asText() == "ADDITION") first + second else first - second
    }

    private fun JsonNode.wrongChoices(): List<Int> {
        val correct = answer()
        return path("choices").map { it.asInt() }.filter { it != correct }
    }
}
