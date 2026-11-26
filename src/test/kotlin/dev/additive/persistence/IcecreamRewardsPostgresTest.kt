package dev.additive.persistence

import dev.additive.domain.Problem
import dev.additive.domain.ProblemType
import org.flywaydb.core.Flyway
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IcecreamRewardsPostgresTest {
    @Test
    fun `a child can earn one ice cream per ordered expression`() {
        PostgreSQLContainer<Nothing>("postgres:16.11-alpine").use { postgres ->
            postgres.start()
            Flyway.configure().dataSource(postgres.jdbcUrl, postgres.username, postgres.password).load().migrate()

            DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
                val child = connection.insertChild()
                val otherChild = connection.insertChild()
                val twoPlusThree = Problem(2, 3, ProblemType.ADDITION)

                assertEquals(1, connection.insertReward(child, twoPlusThree))
                val duplicate = assertFailsWith<SQLException> { connection.insertReward(child, twoPlusThree) }
                assertEquals("23505", duplicate.sqlState)

                assertEquals(1, connection.insertReward(child, Problem(3, 2, ProblemType.ADDITION)))
                assertEquals(1, connection.insertReward(child, Problem(3, 2, ProblemType.SUBTRACTION)))
                assertEquals(1, connection.insertReward(otherChild, twoPlusThree))
            }
        }
    }

    private fun Connection.insertChild(): UUID {
        val childId = UUID.randomUUID()
        prepareStatement("INSERT INTO children (id) VALUES (?)").use { statement ->
            statement.setObject(1, childId)
            statement.executeUpdate()
        }
        return childId
    }

    private fun Connection.insertReward(childId: UUID, problem: Problem): Int =
        prepareStatement(
            "INSERT INTO icecreams (child_id, operation, first_operand, second_operand) VALUES (?, ?, ?, ?)",
        ).use { statement ->
            statement.setObject(1, childId)
            statement.setString(2, problem.type.name)
            statement.setInt(3, problem.first)
            statement.setInt(4, problem.second)
            statement.executeUpdate()
        }
}
