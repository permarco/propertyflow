package com.pertegato.propertyflow.caseprocessing.service;

import com.pertegato.propertyflow.access.contract.SubmissionContext;
import com.pertegato.propertyflow.access.contract.VerificationCodeDelivery;
import com.pertegato.propertyflow.access.service.AccountAdministrationService;
import com.pertegato.propertyflow.access.service.StaffAccountService;
import com.pertegato.propertyflow.access.service.TenantAccountService;
import com.pertegato.propertyflow.caseprocessing.contract.CaseAccepted;
import com.pertegato.propertyflow.caseprocessing.contract.CaseActionException;
import com.pertegato.propertyflow.caseprocessing.contract.CaseServiceException;
import com.pertegato.propertyflow.caseprocessing.contract.CreateCaseCommand;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("propertyflow-backend")
@EnabledIfEnvironmentVariable(named = "PROPERTYFLOW_TEST_DATABASE_URL", matches = ".+")
class AccountLifecyclePostgresTest {
    @Autowired
    private JdbcTemplate database;
    @Autowired
    private PersistentCaseService cases;
    @Autowired
    private TenantAccountService tenants;
    @Autowired
    private StaffAccountService staff;
    @Autowired
    private AccountAdministrationService administration;
    @Autowired
    private CaseAccessAdministrationService access;
    @Autowired
    private TenantMessageService messages;
    @MockitoBean
    private VerificationCodeDelivery delivery;
    private final AtomicReference<String> code = new AtomicReference<>();
    private UUID administrator;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("PROPERTYFLOW_TEST_DATABASE_URL"));
        properties.add("spring.datasource.username", () -> "propertyflow_test");
        properties.add("spring.datasource.password", () -> "propertyflow_test");
        properties.add("propertyflow.links.encryption-key", () -> "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        properties.add("propertyflow.workflow.enabled", () -> false);
        properties.add("propertyflow.case-mail.enabled", () -> false);
    }

    @BeforeEach
    void prepare() {
        assertEquals("propertyflow_test", database.queryForObject("SELECT current_database()", String.class));
        database.execute("TRUNCATE pf_case, pf_staff_account, pf_staff_setup, pf_tenant_account, pf_account_audit CASCADE");
        database.update("INSERT INTO pf_staff_setup (id) VALUES (1)");
        staff.createInitialAdministrator("Testverwaltung", "admin@example.test");
        administrator = database.queryForObject("SELECT id FROM pf_staff_account", UUID.class);
        database.update("UPDATE pf_staff_account SET case_access_administration = true");
        doAnswer(invocation -> {
            code.set(invocation.getArgument(1));
            return null;
        }).when(delivery).send(anyString(), anyString());
    }

    @Test
    void verifiedAccountSeesOnlyExactCurrentContactAndSubmissionUsesVerifiedEmail() {
        CaseAccepted first = guest("a@example.test");
        guest("other@example.test");
        CaseAccepted legacy = guest("legacy@example.test");
        database.update("UPDATE pf_case SET contact_email = ? WHERE id = ?", "mıeter@example.test", legacy.caseId());
        UUID account = tenants.authenticate("a@example.test", true).orElseThrow();
        assertEquals(account, tenants.authenticate("a@example.test", true).orElseThrow());
        assertEquals(List.of(first.caseId()), cases.listForAccount(account).stream().map(view -> view.state().caseId()).toList());
        UUID other = tenants.authenticate("mieter@example.test", true).orElseThrow();
        assertTrue(cases.listForAccount(other).isEmpty());
        assertThrows(CaseServiceException.class, () -> cases.readForAccount(other, first.caseId()));
        CaseAccepted own = cases.create(new SubmissionContext.Tenant(account), command("forged@example.test"));
        assertEquals("a@example.test", database.queryForObject("SELECT contact_email FROM pf_case WHERE id = ?", String.class, own.caseId()));
        assertEquals(2, cases.listForAccount(account).size());
        assertThrows(CaseActionException.class, () -> messages.sendForAccount(other, first.caseId(), "send", "Fremdversuch"));
        assertEquals(0, database.queryForObject("SELECT count(*) FROM pf_tenant_message", Integer.class));
    }

    @Test
    void addressChangeAtomicallyRevokesLinkAndOldAccountAndIsIdempotentWithoutCamunda() {
        CaseAccepted original = guest("a@example.test");
        UUID oldAccount = tenants.authenticate("a@example.test", true).orElseThrow();
        UUID newAccount = tenants.authenticate("b@example.test", true).orElseThrow();
        UUID proof = access.requestVerification(administrator, original.caseId(), 0, "b@example.test", "Mietvertrag und Rückruf an bekannte Nummer geprüft.");
        String correct = code.get();
        assertThrows(IllegalArgumentException.class, () -> access.verifyAndReplace(administrator, original.caseId(), 0, proof, "wrong", "replace", "b@example.test"));
        assertEquals(original.caseId(), cases.readByToken(original.personalAccessToken()).state().caseId());
        access.verifyAndReplace(administrator, original.caseId(), 0, proof, correct, "replace", "b@example.test");
        access.verifyAndReplace(administrator, original.caseId(), 0, proof, correct, "replace", "b@example.test");
        assertThrows(CaseServiceException.class, () -> cases.readByToken(original.personalAccessToken()));
        assertTrue(cases.listForAccount(oldAccount).isEmpty());
        assertEquals(original.caseId(), cases.readForAccount(newAccount, original.caseId()).state().caseId());
        assertEquals("a@example.test", database.queryForObject("SELECT original_contact_email FROM pf_case WHERE id = ?", String.class, original.caseId()));
        assertEquals(1, database.queryForObject("SELECT count(*) FROM pf_case_access WHERE revoked_at IS NULL", Integer.class));
        assertEquals(1, database.queryForObject("SELECT count(*) FROM pf_access_mail_order", Integer.class));
        assertEquals("RECEIVED", database.queryForObject("SELECT status FROM pf_case WHERE id = ?", String.class, original.caseId()));
    }

    @Test
    void wrongProofAndRolledBackReplacementPreserveContactAndLink() {
        CaseAccepted original = guest("a@example.test");
        UUID proof = access.requestVerification(administrator, original.caseId(), 0, "b@example.test", "Mietvertrag und Rückruf an bekannte Nummer geprüft.");
        assertThrows(ResponseStatusException.class, () -> access.verifyAndReplace(administrator, original.caseId(), 0, proof, code.get(), "replace", "evil@example.test"));
        database.execute("ALTER TABLE pf_access_mail_order ADD CONSTRAINT test_reject_access_mail CHECK (false) NOT VALID");
        try {
            assertThrows(DataAccessException.class,
                    () -> access.verifyAndReplace(administrator, original.caseId(), 0, proof, code.get(), "replace", "b@example.test"));
        } finally {
            database.execute("ALTER TABLE pf_access_mail_order DROP CONSTRAINT test_reject_access_mail");
        }
        assertEquals(original.caseId(), cases.readByToken(original.personalAccessToken()).state().caseId());
        assertEquals(0, database.queryForObject("SELECT count(*) FROM pf_access_mail_order", Integer.class));
    }

    @Test
    void administrativePermissionsAndLastAdministratorAreEnforced() {
        UUID worker = administration.create(administrator, "Mitarbeiter", "worker@example.test");
        assertThrows(ResponseStatusException.class, () -> administration.create(worker, "Unzulässig", "no@example.test"));
        assertThrows(ResponseStatusException.class, () -> administration.requireCaseAccessAdministrator(worker));
        assertThrows(IllegalArgumentException.class, () -> administration.revoke(administrator, administrator, "self-block", true));
        administration.revoke(administrator, worker, "block", true);
        administration.revoke(administrator, worker, "block", true);
        assertThrows(ResponseStatusException.class, () -> administration.revoke(administrator, worker, "block", false));
        assertFalse(database.queryForObject("SELECT active FROM pf_staff_account WHERE id = ?", Boolean.class, worker));
        assertEquals(2, database.queryForObject("SELECT count(*) FROM pf_account_audit", Integer.class));
    }

    @Test
    void unverifiedExpiredOrExhaustedProofCannotReplaceAccess() {
        CaseAccepted original = guest("a@example.test");
        UUID proof = access.requestVerification(administrator, original.caseId(), 0, "a@example.test", "Mietvertrag und Rückruf an bekannte Nummer geprüft.");
        String correct = code.get();
        assertThrows(ResponseStatusException.class,
                () -> access.replace(administrator, original.caseId(), 0, proof, "replace", null, false));
        for (int index = 0; index < 5; index++) {
            assertThrows(IllegalArgumentException.class, () -> access.verify(administrator, original.caseId(), proof, "wrong"));
        }
        assertThrows(ResponseStatusException.class, () -> access.verify(administrator, original.caseId(), proof, correct));
        UUID expired = access.requestVerification(administrator, original.caseId(), 0, "a@example.test", "Mietvertrag und Rückruf an bekannte Nummer geprüft.");
        database.update("UPDATE pf_access_verification SET expires_at = CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE id = ?", expired);
        assertThrows(ResponseStatusException.class, () -> access.verify(administrator, original.caseId(), expired, code.get()));
        assertEquals(original.caseId(), cases.readByToken(original.personalAccessToken()).state().caseId());
    }

    @Test
    void proofCannotChangeAnotherAddressOrBeUsedAfterRightsRevocation() {
        CaseAccepted original = guest("a@example.test");
        UUID proof = access.requestVerification(administrator, original.caseId(), 0, "b@example.test", "Mietvertrag und Rückruf an bekannte Nummer geprüft.");
        access.verify(administrator, original.caseId(), proof, code.get());
        assertThrows(ResponseStatusException.class, () -> access.replace(administrator, original.caseId(), 0, proof, "replace", null, false));
        database.update("UPDATE pf_staff_account SET case_access_administration = false WHERE id = ?", administrator);
        assertThrows(ResponseStatusException.class,
                () -> access.replace(administrator, original.caseId(), 0, proof, "replace", "b@example.test", true));
        assertEquals(original.caseId(), cases.readByToken(original.personalAccessToken()).state().caseId());
    }

    @Test
    void concurrentRevocationsCannotRemoveBothAdministrators() throws Exception {
        UUID second = administration.create(administrator, "Zweite Verwaltung", "second@example.test");
        database.update("UPDATE pf_staff_account SET account_administration = true WHERE id = ?", second);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(() -> revoke(administrator, second));
            Future<Boolean> other = executor.submit(() -> revoke(second, administrator));
            assertTrue(first.get() ^ other.get());
        }
        assertEquals(1, database.queryForObject("SELECT count(*) FROM pf_staff_account WHERE active AND staff_role AND account_administration", Integer.class));
    }

    private boolean revoke(UUID actor, UUID target) {
        try {
            administration.revoke(actor, target, "parallel-block", true);
            return true;
        } catch (ResponseStatusException | IllegalArgumentException exception) {
            return false;
        }
    }

    private CaseAccepted guest(String email) {
        return cases.create(new SubmissionContext.Guest(UUID.randomUUID()), command(email));
    }

    private CreateCaseCommand command(String email) {
        return new CreateCaseCommand(UUID.randomUUID().toString(), "Testfenster", "Testweg 1", "Das Fenster klemmt.", email);
    }
}
