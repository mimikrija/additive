package dev.additive.persistence

import dev.additive.domain.Difficulty
import dev.additive.domain.Progress
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.DriverManager
import java.util.UUID
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChildProgressPostgresTest {
    @Test
    fun `twenty concurrent correct updates promote once without losing progress`() {
        PostgreSQLContainer<Nothing>("postgres:16.11-alpine").use { postgres ->
            postgres.start()
            Flyway.configure().dataSource(postgres.jdbcUrl, postgres.username, postgres.password).load().migrate()
            val database = Database.connect(
                url = postgres.jdbcUrl,
                driver = postgres.driverClassName,
                user = postgres.username,
                password = postgres.password,
            )
            val childId = UUID.randomUUID()
            transaction(database) { Children.insert { it[id] = childId } }

            val updateCount = 20
            val start = CyclicBarrier(updateCount)
            val results = Executors.newFixedThreadPool(updateCount).use { executor ->
                val updates = List(updateCount) {
                    executor.submit<Progress?> {
                        transaction(database) {
                            exec("SET LOCAL statement_timeout = '10s'")
                            start.await(10, SECONDS)
                            advanceChildProgress(childId, correct = true)
                        }
                    }
                }
                updates.map { it.get(25, SECONDS) }
            }

            val promoted = Progress(Difficulty.LEVEL_2)
            val expectedUpdates = (1..19).map { Progress(correctStreak = it) }.toSet() + promoted
            assertEquals(expectedUpdates, results.toSet())
            transaction(database) {
                val child = Children.selectAll().where { Children.id eq childId }.single()
                assertEquals(promoted, child.toProgress())
                assertEquals(20L, child[Children.currentCorrectRun])
                assertEquals(20L, child[Children.bestCorrectStreak])
            }
        }
    }

    @Test
    fun `an existing child starts at level one and persists changes across transactions`() {
        PostgreSQLContainer<Nothing>("postgres:16.11-alpine").use { postgres ->
            postgres.start()
            val flyway = Flyway.configure().dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            flyway.target("1").load().migrate()
            val childId = UUID.randomUUID()
            DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
                connection.prepareStatement("INSERT INTO children (id) VALUES (?)").use { statement ->
                    statement.setObject(1, childId)
                    statement.executeUpdate()
                }
            }
            flyway.target("latest").load().migrate()

            val database = Database.connect(
                url = postgres.jdbcUrl,
                driver = postgres.driverClassName,
                user = postgres.username,
                password = postgres.password,
            )
            assertEquals(Progress(wrongStreak = 1), transaction(database) { advanceChildProgress(childId, false) })
            val beforePromotion = transaction(database) {
                repeat(18) { advanceChildProgress(childId, true) }
                advanceChildProgress(childId, true)
            }
            assertEquals(Progress(correctStreak = 19), beforePromotion)
            assertEquals(Progress(Difficulty.LEVEL_2), transaction(database) { advanceChildProgress(childId, true) })
            assertEquals(Progress(Difficulty.LEVEL_2, correctStreak = 1), transaction(database) {
                advanceChildProgress(childId, true)
            })

            DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
                connection.prepareStatement(
                    "SELECT level, correct_streak, wrong_streak, current_correct_run, best_correct_streak FROM children WHERE id = ?",
                )
                    .use { statement ->
                        statement.setObject(1, childId)
                        statement.executeQuery().use { row ->
                            assertTrue(row.next())
                            assertEquals("LEVEL_2", row.getString("level"))
                            assertEquals(1, row.getInt("correct_streak"))
                            assertEquals(0, row.getInt("wrong_streak"))
                            assertEquals(21L, row.getLong("current_correct_run"))
                            assertEquals(21L, row.getLong("best_correct_streak"))
                        }
                    }
            }
        }
    }

    @Test
    fun `personal best migration recovers streaks from counted attempts`() {
        PostgreSQLContainer<Nothing>("postgres:16.11-alpine").use { postgres ->
            postgres.start()
            val flyway = Flyway.configure().dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            flyway.target("4").load().migrate()

            DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeUpdate(
                        """
                        WITH child AS (
                            INSERT INTO children (id) VALUES (gen_random_uuid()) RETURNING id
                        ), issued AS (
                            INSERT INTO problems (id, child_id, sequence_number, level, operation,
                                first_operand, second_operand, answer, solved)
                            SELECT gen_random_uuid(), child.id, sequence_number, 'LEVEL_1', 'ADDITION', 2, 3, 5, TRUE
                            FROM child CROSS JOIN generate_series(1, 5) AS sequence_number
                            RETURNING id, sequence_number
                        )
                        INSERT INTO attempts (id, problem_id, submitted_answer, correct, counted, rewarded)
                        SELECT gen_random_uuid(), id, 5, TRUE, TRUE, FALSE FROM issued
                        UNION ALL
                        SELECT gen_random_uuid(), id, 4, FALSE, TRUE, FALSE FROM issued WHERE sequence_number = 4
                        UNION ALL
                        SELECT gen_random_uuid(), id, 4, FALSE, FALSE, FALSE FROM issued WHERE sequence_number = 5
                        """.trimIndent(),
                    )
                    flyway.target("latest").load().migrate()

                    statement.executeQuery("SELECT current_correct_run, best_correct_streak FROM children").use { row ->
                        assertTrue(row.next())
                        assertEquals(2L, row.getLong("current_correct_run"))
                        assertEquals(3L, row.getLong("best_correct_streak"))
                    }
                }
            }
        }
    }
}
