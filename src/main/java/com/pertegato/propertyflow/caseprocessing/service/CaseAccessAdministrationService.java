package com.pertegato.propertyflow.caseprocessing.service;

import com.pertegato.propertyflow.access.contract.AccountAdministration;
import com.pertegato.propertyflow.access.contract.EmailAddress;
import com.pertegato.propertyflow.access.contract.VerificationCodeDelivery;
import com.pertegato.propertyflow.caseprocessing.delivery.PersonalLinkCodec;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseAccessAdministrationStore.CaseContact;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseAccessAdministrationStore.Verification;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseAccessAdministrationStore;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
@Profile("propertyflow-backend & !frontend-demo")
public class CaseAccessAdministrationService {
    private final AccountAdministration accounts;
    private final CaseAccessAdministrationStore store;
    private final VerificationCodeDelivery delivery;
    private final PersonalLinkCodec links;
    private final TransactionTemplate writes;
    private final SecureRandom random = new SecureRandom();
    private final SecretKeySpec codeKey;

    public CaseAccessAdministrationService(AccountAdministration accounts, CaseAccessAdministrationStore store,
            VerificationCodeDelivery delivery, PersonalLinkCodec links, PlatformTransactionManager transactions) {
        this.accounts = accounts;
        this.store = store;
        this.delivery = delivery;
        this.links = links;
        this.writes = new TransactionTemplate(transactions);
        this.writes.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        byte[] secret = new byte[32];
        random.nextBytes(secret);
        codeKey = new SecretKeySpec(secret, "HmacSHA256");
    }

    public CaseContact contact(UUID actor, String caseId) {
        accounts.requireCaseAccessAdministrator(actor);
        return store.contact(caseId, false).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    public UUID requestVerification(UUID actor, String caseId, long version, String targetEmail, String evidence) {
        accounts.requireCaseAccessAdministrator(actor);
        String email = EmailAddress.normalize(targetEmail);
        if (evidence == null || evidence.strip().length() < 20 || evidence.length() > 1000) {
            throw new IllegalArgumentException("Dokumentieren Sie die unabhängige Identitätsprüfung mit 20 bis 1’000 Zeichen.");
        }
        UUID id = UUID.randomUUID();
        String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        writes.executeWithoutResult(transaction -> {
            accounts.requireCaseAccessAdministrator(actor);
            CaseContact current = store.contact(caseId, true).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            if (current.version() != version) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Fallstand geändert; erneut prüfen.");
            }
            if (!store.allowVerification(caseId, actor)) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
            }
            store.saveVerification(id, actor, current, email, evidence.strip(), digest(id, code));
        });
        // Die E-Mail bestätigt nur den Postfachzugriff. Die dokumentierte Identitätsprüfung bleibt zusätzlich erforderlich.
        delivery.send(email, code);
        return id;
    }

    public void verify(UUID actor, String caseId, UUID proofId, String code) {
        accounts.requireCaseAccessAdministrator(actor);
        boolean accepted = Boolean.TRUE.equals(writes.execute(transaction -> {
            accounts.requireCaseAccessAdministrator(actor);
            Verification proof = store.lockVerification(proofId).orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
            if (!proof.actor().equals(actor) || !proof.caseId().equals(caseId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            }
            if (proof.consumed() || proof.verified()) {
                return true;
            }
            if (!Instant.now().isBefore(proof.expiresAt()) || proof.attempts() >= 5) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Prüfung abgelaufen.");
            }
            if (code == null || !code.matches("[0-9]{6}") || !MessageDigest.isEqual(
                    proof.digest().getBytes(StandardCharsets.US_ASCII), digest(proofId, code).getBytes(StandardCharsets.US_ASCII))) {
                store.failedAttempt(proofId);
                return false;
            }
            store.verified(proofId);
            return true;
        }));
        if (!accepted) {
            throw new IllegalArgumentException("Der Bestätigungscode ist ungültig.");
        }
    }

    public void verifyAndReplace(UUID actor, String caseId, long version, UUID proofId, String code, String operation, String email) {
        verify(actor, caseId, proofId, code);
        replace(actor, caseId, version, proofId, operation, email, true);
    }

    public void replace(UUID actor, String caseId, long version, UUID proofId, String operation, String email, boolean changeContact) {
        accounts.requireCaseAccessAdministrator(actor);
        if (operation == null || operation.isBlank() || operation.length() > 80) {
            throw new IllegalArgumentException("Eine gültige Absende-ID ist erforderlich.");
        }
        writes.executeWithoutResult(transaction -> {
            accounts.requireCaseAccessAdministrator(actor);
            CaseContact current = store.contact(caseId, true).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            Verification proof = store.lockVerification(proofId).orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
            if (!proof.actor().equals(actor) || !proof.caseId().equals(caseId) || proof.version() != version || !proof.verified()
                    || changeContact && !proof.email().equals(EmailAddress.normalize(email))
                    || !changeContact && !proof.previousEmail().equals(proof.email())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            }
            if (proof.consumed()) {
                if (!operation.equals(proof.operation())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT);
                }
                return;
            }
            if (!Instant.now().isBefore(proof.expiresAt()) || current.version() != version) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Prüfung abgelaufen oder Fallstand geändert.");
            }
            String token = links.generateToken();
            UUID accessId = UUID.randomUUID();
            store.replace(proof, operation, accessId, links.hash(token), links.encrypt(accessId, token));
        });
    }

    private String digest(UUID id, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(codeKey);
            return HexFormat.of().formatHex(mac.doFinal((id + ":" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Die Kontaktprüfung ist nicht verfügbar.", exception);
        }
    }
}
