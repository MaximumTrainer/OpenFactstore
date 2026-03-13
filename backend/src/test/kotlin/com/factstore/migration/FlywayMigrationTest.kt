package com.factstore.migration

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

/**
 * Integration test that boots the full Spring context with Flyway enabled against a real
 * PostgreSQL instance. Validates that all migration scripts in db/migration/ apply cleanly.
 *
 * Requires a running PostgreSQL instance. Set DB_HOST, DB_PORT, DB_NAME, DB_USERNAME,
 * DB_PASSWORD environment variables before running (or use the CI postgres service).
 *
 * Run via: ./gradlew migrationTest
 */
@SpringBootTest
@ActiveProfiles("migration-test")
class FlywayMigrationTest {

    @Test
    fun `flyway migrations apply cleanly against PostgreSQL`() {
        // Successfully loading the Spring context proves that all Flyway migration scripts
        // in db/migration/ executed without error against a real PostgreSQL instance.
    }
}
