package com.pertegato.propertyflow.config;

import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Prüft den vollständigen Neuaufbau aus den Moduldateien in einem isolierten Testschema.
 */
@EnabledIfEnvironmentVariable(named = "PROPERTYFLOW_TEST_DATABASE_URL", matches = ".+")
class BackendMigrationPostgresTest {

    @Test
    void moduleInitialFilesCreateCompleteSchemaAndCanBeValidatedOnRestart() {
        String url = System.getenv("PROPERTYFLOW_TEST_DATABASE_URL");
        DriverManagerDataSource source = new DriverManagerDataSource(url,
                setting("PROPERTYFLOW_TEST_DATABASE_USER"), setting("PROPERTYFLOW_TEST_DATABASE_PASSWORD"));
        JdbcTemplate database = new JdbcTemplate(source);
        assertEquals("propertyflow_test", database.queryForObject("SELECT current_database()", String.class));
        String schema = "migration_test_" + UUID.randomUUID().toString().replace("-", "");
        try {
            Flyway flyway = Flyway.configure()
                    .dataSource(source)
                    .schemas(schema)
                    .locations("classpath:db/backend-migration")
                    .load();
            assertEquals(11, flyway.migrate().migrationsExecuted);
            assertEquals(56, database.queryForObject("""
                    SELECT count(*) FROM information_schema.tables WHERE table_schema = ?
                        AND table_name <> 'flyway_schema_history'
                    """, Integer.class, schema));
            assertEquals(List.of("access/V1__initial_schema.sql", "caseprocessing/V2__initial_schema.sql",
                    "intake/V3__initial_schema.sql", "triage/V4__initial_schema.sql", "recommendation/V5__initial_schema.sql",
                    "updates/V6__account_management.sql", "updates/V7__case_access_administration.sql",
                    "updates/V8__administrative_command_contracts.sql", "updates/V9__mcp_oauth.sql",
                    "updates/V10__mcp_message_confirmation.sql", "updates/V11__mcp_case_actions.sql"),
                    database.queryForList("SELECT script FROM " + schema
                            + ".flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank", String.class));
            assertEquals(1, database.queryForObject("SELECT count(*) FROM " + schema
                    + ".pf_staff_setup WHERE id = 1 AND account_id IS NULL", Integer.class));
            flyway.validate();
            assertEquals(0, flyway.migrate().migrationsExecuted);
        } finally {
            if (!schema.matches("migration_test_[a-f0-9]{32}")) {
                throw new IllegalArgumentException("UNSAFE_TEST_SCHEMA");
            }
            database.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    private String setting(String name) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? "propertyflow_test" : value;
    }
}
