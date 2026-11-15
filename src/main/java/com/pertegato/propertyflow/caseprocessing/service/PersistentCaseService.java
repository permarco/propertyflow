package com.pertegato.propertyflow.caseprocessing.service;

import com.pertegato.propertyflow.access.contract.SubmissionContext;
import com.pertegato.propertyflow.caseprocessing.contract.CaseAccepted;
import com.pertegato.propertyflow.caseprocessing.contract.CaseCreation;
import com.pertegato.propertyflow.caseprocessing.contract.CaseErrorCode;
import com.pertegato.propertyflow.caseprocessing.contract.CaseInputValidation;
import com.pertegato.propertyflow.caseprocessing.contract.CaseReading;
import com.pertegato.propertyflow.caseprocessing.contract.CaseServiceException;
import com.pertegato.propertyflow.caseprocessing.contract.CreateCaseCommand;
import com.pertegato.propertyflow.caseprocessing.contract.TenantCaseView;
import com.pertegato.propertyflow.caseprocessing.delivery.PersonalLinkCodec;
import com.pertegato.propertyflow.caseprocessing.persistence.AcceptanceMailOrderEntity;
import com.pertegato.propertyflow.caseprocessing.persistence.AcceptanceMailOrderRepository;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseAccessEntity;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseAccessRepository;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseEntity;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseRepository;
import com.pertegato.propertyflow.caseprocessing.persistence.ProcessStartOrderEntity;
import com.pertegato.propertyflow.caseprocessing.persistence.ProcessStartOrderRepository;
import com.pertegato.propertyflow.caseprocessing.persistence.SubmissionEntity;
import com.pertegato.propertyflow.caseprocessing.persistence.SubmissionRepository;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Profile("propertyflow-backend & !frontend-demo")
public class PersistentCaseService implements CaseCreation, CaseReading {

    private final CaseRepository cases;
    private final CaseAccessRepository accesses;
    private final SubmissionRepository submissions;
    private final ProcessStartOrderRepository startOrders;
    private final AcceptanceMailOrderRepository mailOrders;
    private final PersonalLinkCodec links;
    private final TransactionTemplate writes;
    private final TransactionTemplate reads;

    public PersistentCaseService(CaseRepository cases, CaseAccessRepository accesses, SubmissionRepository submissions,
            ProcessStartOrderRepository startOrders, AcceptanceMailOrderRepository mailOrders, PersonalLinkCodec links,
            PlatformTransactionManager transactions) {
        this.cases = cases;
        this.accesses = accesses;
        this.submissions = submissions;
        this.startOrders = startOrders;
        this.mailOrders = mailOrders;
        this.links = links;
        this.writes = new TransactionTemplate(transactions);
        // The public contract promises a confirmed commit, even if the caller already has a transaction.
        this.writes.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.reads = new TransactionTemplate(transactions);
        this.reads.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.reads.setReadOnly(true);
    }

    @Override
    public CaseAccepted create(SubmissionContext context, CreateCaseCommand command) {
        if (!(context instanceof SubmissionContext.Guest guest)) {
            throw failure(CaseErrorCode.UNAUTHENTICATED);
        }
        CreateCaseCommand input = CaseInputValidation.validateGuest(command);
        String fingerprint = fingerprint(input);
        try {
            try {
                return writes.execute(transaction -> createOrReplay(guest.sessionScope(), input, fingerprint));
            } catch (DataIntegrityViolationException collision) {
                // PostgreSQL waits on the unique key. The losing transaction is fully rolled back before this read.
                return reads.execute(transaction -> submissions.findByGuestScopeAndOperationId(guest.sessionScope(), input.operationId())
                        .map(existing -> replay(existing, fingerprint))
                        .orElseThrow(() -> failure(CaseErrorCode.STORAGE_UNAVAILABLE)));
            }
        } catch (DataAccessException | TransactionException exception) {
            throw failure(CaseErrorCode.STORAGE_UNAVAILABLE);
        }
    }

    private CaseAccepted createOrReplay(UUID guestScope, CreateCaseCommand input, String fingerprint) {
        Optional<SubmissionEntity> existing = submissions.findByGuestScopeAndOperationId(guestScope, input.operationId());
        if (existing.isPresent()) {
            return replay(existing.get(), fingerprint);
        }
        String caseId = UUID.randomUUID().toString();
        UUID accessId = UUID.randomUUID();
        Instant now = Instant.now();
        String token = links.generateToken();
        cases.saveAndFlush(new CaseEntity(caseId, input.title().strip(), input.unit().strip(), input.description().strip(),
                input.guestEmail(), now));
        accesses.saveAndFlush(new CaseAccessEntity(accessId, caseId, links.hash(token), links.encrypt(accessId, token)));
        startOrders.save(new ProcessStartOrderEntity(UUID.randomUUID(), caseId, now));
        mailOrders.save(new AcceptanceMailOrderEntity(UUID.randomUUID(), caseId, accessId, now));
        submissions.saveAndFlush(new SubmissionEntity(UUID.randomUUID(), guestScope, input.operationId(), fingerprint, caseId, accessId));
        return new CaseAccepted(caseId, token, false);
    }

    private CaseAccepted replay(SubmissionEntity submission, String fingerprint) {
        if (!submission.getFingerprint().equals(fingerprint)) {
            throw failure(CaseErrorCode.IDEMPOTENCY_CONFLICT);
        }
        CaseAccessEntity access = accesses.findById(submission.getAccessId())
                .filter(value -> value.getRevokedAt() == null && value.getCaseId().equals(submission.getCaseId()))
                .orElseThrow(() -> failure(CaseErrorCode.REPLAY_ACCESS_UNAVAILABLE));
        // Only the original, still-authorized access is restored by the delivery function; never a replacement link.
        String token = links.restore(access.getId(), access.getEncryptedToken());
        return new CaseAccepted(submission.getCaseId(), token, true);
    }

    @Override
    public TenantCaseView readByToken(String token) {
        if (!links.isWellFormed(token)) {
            throw failure(CaseErrorCode.CASE_NOT_AVAILABLE);
        }
        try {
            return reads.execute(transaction -> {
                CaseAccessEntity access = accesses.findByTokenHashAndRevokedAtIsNull(links.hash(token))
                        .orElseThrow(() -> failure(CaseErrorCode.CASE_NOT_AVAILABLE));
                CaseEntity stored = cases.findById(access.getCaseId())
                        .orElseThrow(() -> failure(CaseErrorCode.CASE_NOT_AVAILABLE));
                return CaseProjections.tenantView(stored);
            });
        } catch (DataAccessException | TransactionException exception) {
            throw failure(CaseErrorCode.STORAGE_UNAVAILABLE);
        }
    }


    private String fingerprint(CreateCaseCommand input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String field : List.of(input.title(), input.unit(), input.description(), input.guestEmail())) {
                byte[] bytes = field.getBytes(StandardCharsets.UTF_8);
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Required digest is unavailable");
        }
    }

    private CaseServiceException failure(CaseErrorCode code) {
        return new CaseServiceException(code, Map.of());
    }
}
