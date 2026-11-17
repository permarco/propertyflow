package com.pertegato.propertyflow.access.service;

import com.pertegato.propertyflow.access.contract.StaffAccessDecision;
import com.pertegato.propertyflow.access.contract.WebAccountContext;
import com.pertegato.propertyflow.access.persistence.StaffAccountStore;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "PROPERTYFLOW_TEST_DATABASE_URL", matches = ".+")
class StaffAccountPostgresTest {

    @Test
    void firstAdministratorAndAuditAreUniqueAndRoleChangesTakeEffectOnNextRead() throws Exception {
        String url = System.getenv("PROPERTYFLOW_TEST_DATABASE_URL");
        String user = setting("PROPERTYFLOW_TEST_DATABASE_USER");
        String password = setting("PROPERTYFLOW_TEST_DATABASE_PASSWORD");
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url, user, password);
        JdbcTemplate database = new JdbcTemplate(dataSource);
        assertEquals("propertyflow_test", database.queryForObject("select current_database()", String.class));
        assertTrue(database.queryForObject("select version()", String.class).startsWith("PostgreSQL "));

        String schema = "staff_test_" + UUID.randomUUID().toString().replace("-", "");
        try {
            Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/backend-migration")
                    .schemas(schema)
                    .load()
                    .migrate();
            String separator = url.contains("?") ? "&" : "?";
            DriverManagerDataSource isolated = new DriverManagerDataSource(url + separator + "currentSchema=" + schema, user, password);
            JdbcTemplate staffDb = new JdbcTemplate(isolated);
            StaffAccountService accounts = new StaffAccountService(new StaffAccountStore(staffDb));
            TransactionTemplate writes = new TransactionTemplate(new DataSourceTransactionManager(isolated));

            assertExactlyOneInitialAdministrator(
                    () -> writes.execute(status -> accounts.createInitialAdministrator("Verwaltung", " Staff@Example.test ")),
                    () -> writes.execute(status -> accounts.createInitialAdministrator("Weitere Verwaltung", "other@example.test")));
            assertEquals(Boolean.FALSE, writes.execute(status -> accounts.createInitialAdministrator("Wiederholung", "again@example.test")));

            UUID accountId = staffDb.queryForObject("select account_id from pf_staff_setup where completed_at is not null", UUID.class);
            assertEquals(1, staffDb.queryForObject("select count(*) from pf_staff_account", Integer.class));
            assertTrue(staffDb.queryForObject("select account_administration from pf_staff_account where id = ?", Boolean.class, accountId));
            WebAccountContext context = new WebAccountContext(accountId);
            assertEquals(StaffAccessDecision.ALLOWED, accounts.checkStaffAccess(context));

            staffDb.update("update pf_staff_account set staff_role = false, account_administration = false where id = ?", accountId);
            assertEquals(StaffAccessDecision.FORBIDDEN, accounts.checkStaffAccess(context));
        } finally {
            database.execute("drop schema if exists " + schema + " cascade");
        }
    }

    private static void assertExactlyOneInitialAdministrator(Supplier<Boolean> first, Supplier<Boolean> second) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService threads = Executors.newFixedThreadPool(2)) {
            Future<Boolean> one = threads.submit(() -> {
                start.await();
                return first.get();
            });
            Future<Boolean> two = threads.submit(() -> {
                start.await();
                return second.get();
            });
            start.countDown();
            assertTrue(one.get(10, TimeUnit.SECONDS) ^ two.get(10, TimeUnit.SECONDS));
        }
    }

    private static String setting(String name) {
        return System.getenv().getOrDefault(name, "propertyflow_test");
    }
}
