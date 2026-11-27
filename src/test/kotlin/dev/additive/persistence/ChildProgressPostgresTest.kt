package dev.additive.persistence

import dev.additive.domain.Difficulty
import dev.additive.domain.Progress
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.DriverManager
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChildProgressPostgresTest {
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

            DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
                connection.prepareStatement("SELECT level, correct_streak, wrong_streak FROM children WHERE id = ?")
                    .use { statement ->
                        statement.setObject(1, childId)
                        statement.executeQuery().use { row ->
                            assertTrue(row.next())
                            assertEquals("LEVEL_2", row.getString("level"))
                            assertEquals(0, row.getInt("correct_streak"))
                            assertEquals(0, row.getInt("wrong_streak"))
                        }
                    }
            }
        }
    }
}
