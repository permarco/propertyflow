package com.pertegato.propertyflow.caseprocessing.service;

import com.pertegato.propertyflow.caseprocessing.persistence.CaseOperationsStore;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "PROPERTYFLOW_TEST_DATABASE_URL", matches = ".+")
class CaseOperationsPostgresTest {

    @Test
    void readsOnlyAttentionStatesWithoutMutationsAndBoundsLargeResults() {
        String url = System.getenv("PROPERTYFLOW_TEST_DATABASE_URL");
        DriverManagerDataSource source = new DriverManagerDataSource(url, setting("PROPERTYFLOW_TEST_DATABASE_USER"),
                setting("PROPERTYFLOW_TEST_DATABASE_PASSWORD"));
        JdbcTemplate administration = new JdbcTemplate(source);
        assertThat(administration.queryForObject("SELECT current_database()", String.class)).isEqualTo("propertyflow_test");
        String schema = "operations_test_" + UUID.randomUUID().toString().replace("-", "");
        try {
            Flyway.configure().dataSource(source).schemas(schema).locations("classpath:db/backend-migration").load().migrate();
            DriverManagerDataSource isolated = new DriverManagerDataSource(url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema,
                    setting("PROPERTYFLOW_TEST_DATABASE_USER"), setting("PROPERTYFLOW_TEST_DATABASE_PASSWORD"));
            JdbcTemplate database = new JdbcTemplate(isolated);
            CaseOperationsStore store = new CaseOperationsStore(database);
            assertThat(store.attention()).isEmpty();
            database.update("""
                    INSERT INTO pf_case (id,title,original_unit,description,contact_email,received_at,last_tenant_contact_at,status,initial_message_id)
                    VALUES ('CASE-A','Test','Objekt','Beschreibung','private@example.test',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'RECEIVED',?)
                    """, UUID.randomUUID());
            UUID start = UUID.randomUUID();
            database.update("INSERT INTO pf_process_start_order (id,case_id,created_at) VALUES (?,'CASE-A',CURRENT_TIMESTAMP)", start);
            assertThat(store.attention()).isEmpty();
            database.update("UPDATE pf_process_start_order SET state='BLOCKED' WHERE id=?", start);
            UUID access = UUID.randomUUID();
            UUID mail = UUID.randomUUID();
            database.update("INSERT INTO pf_case_access (id,case_id,token_hash,encrypted_token) VALUES (?,'CASE-A','hash','SECRET-TOKEN')", access);
            database.update("INSERT INTO pf_acceptance_mail_order (id,case_id,access_id,created_at) VALUES (?,'CASE-A',?,CURRENT_TIMESTAMP)", mail, access);
            database.update("""
                    INSERT INTO pf_mail_delivery (id,case_id,acceptance_order_id,notification_type,state)
                    VALUES (?,'CASE-A',?,'ACCEPTED','UNCERTAIN')
                    """, mail, mail);
            UUID proof = UUID.randomUUID();
            database.update("""
                    INSERT INTO pf_access_verification
                        (id,case_id,actor_id,case_version,previous_email,target_email,identity_evidence,code_digest,expires_at,consumed_at)
                    VALUES (?,'CASE-A',?,0,'old@example.test','new@example.test','PRIVATE-EVIDENCE','PRIVATE-CODE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                    """, proof, UUID.randomUUID());
            database.update("INSERT INTO pf_access_mail_order (id,case_id,state) VALUES (?,'CASE-A','FAILED')", proof);
            List<CaseOperationsStore.Entry> first = store.attention();
            assertThat(first).extracting(CaseOperationsStore.Entry::kind).containsExactlyInAnyOrder("PROCESS_START", "CASE_MAIL", "ACCESS_MAIL");
            assertThat(first.toString()).doesNotContain("SECRET-TOKEN", "PRIVATE-EVIDENCE", "PRIVATE-CODE", "@example.test");
            assertThat(store.attention()).isEqualTo(first);
            assertThat(database.queryForObject("SELECT state FROM pf_mail_delivery WHERE id=?", String.class, mail)).isEqualTo("UNCERTAIN");
            database.update("UPDATE pf_mail_delivery SET state='SENT',start_state='CONFIRMED' WHERE id=?", mail);
            database.update("UPDATE pf_access_mail_order SET state='SENT' WHERE id=?", proof);
            database.update("UPDATE pf_process_start_order SET state='PENDING',created_at=CURRENT_TIMESTAMP-INTERVAL '6 minutes' WHERE id=?", start);
            assertThat(store.attention()).hasSize(1);
            database.update("""
                    INSERT INTO pf_case_data_command
                        (id,operation_id,case_id,actor_id,action_type,payload,expected_version,leading_instance_key,tenant_id,state,created_at)
                    SELECT gen_random_uuid(), gen_random_uuid()::text, 'CASE-A',gen_random_uuid(),'ADD_MEMO','INTERNAL',0,1,'<default>',
                        'REJECTED',CURRENT_TIMESTAMP FROM generate_series(1,105)
                    """);
            assertThat(store.attention()).hasSize(101).allMatch(entry -> entry.kind().equals("CASE_COMMAND"));
            database.update("UPDATE pf_case_data_command SET created_at=CURRENT_TIMESTAMP-INTERVAL '8 days'");
            assertThat(store.attention()).hasSize(1);
        } finally {
            if (!schema.matches("operations_test_[a-f0-9]{32}")) {
                throw new IllegalArgumentException("UNSAFE_TEST_SCHEMA");
            }
            administration.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    private String setting(String name) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? "propertyflow_test" : value;
    }
}
