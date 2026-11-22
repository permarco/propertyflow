package com.pertegato.propertyflow.caseprocessing.service;

import com.pertegato.propertyflow.access.contract.SubmissionContext;
import com.pertegato.propertyflow.access.contract.TenantAccountAccess;
import com.pertegato.propertyflow.caseprocessing.contract.CaseAccepted;
import com.pertegato.propertyflow.caseprocessing.contract.CaseCreation;
import com.pertegato.propertyflow.caseprocessing.contract.CaseErrorCode;
import com.pertegato.propertyflow.caseprocessing.contract.CaseInputValidation;
import com.pertegato.propertyflow.caseprocessing.contract.CaseReading;
import com.pertegato.propertyflow.caseprocessing.contract.CaseServiceException;
import com.pertegato.propertyflow.caseprocessing.contract.CaseStatusView;
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
import com.pertegato.propertyflow.caseprocessing.persistence.StaffMessageStore;
import com.pertegato.propertyflow.caseprocessing.persistence.SubmissionEntity;
import com.pertegato.propertyflow.caseprocessing.persistence.SubmissionRepository;
import com.pertegato.propertyflow.caseprocessing.persistence.TenantMessageStore;
import com.pertegato.propertyflow.caseprocessing.persistence.UrgencyStore;
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
import java.util.function.Consumer;
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

    private final TenantAccountAccess tenantAccounts;
    private final CaseRepository cases;
    private final CaseAccessRepository accesses;
    private final SubmissionRepository submissions;
    private final ProcessStartOrderRepository startOrders;
    private final AcceptanceMailOrderRepository mailOrders;
    private final PersonalLinkCodec links;
    private final TransactionTemplate writes;
    private final TransactionTemplate reads;
    private final UrgencyStore urgencies;
    private final TenantMessageStore messages;
    private final StaffMessageStore staffMessages;

    public PersistentCaseService(CaseRepository cases, CaseAccessRepository accesses, SubmissionRepository submissions,
            ProcessStartOrderRepository startOrders, AcceptanceMailOrderRepository mailOrders, PersonalLinkCodec links,
            PlatformTransactionManager transactions, UrgencyStore urgencies, TenantMessageStore messages, StaffMessageStore staffMessages,
            TenantAccountAccess tenantAccounts) {
        this.tenantAccounts = tenantAccounts;
        this.cases = cases;
        this.accesses = accesses;
        this.submissions = submissions;
        this.startOrders = startOrders;
        this.mailOrders = mailOrders;
        this.links = links;
        this.urgencies = urgencies;
        this.messages = messages;
        this.staffMessages = staffMessages;
        this.writes = new TransactionTemplate(transactions);
        // The public contract promises a confirmed commit, even if the caller already has a transaction.
        this.writes.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.reads = new TransactionTemplate(transactions);
        this.reads.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.reads.setReadOnly(true);
    }

    @Override
    public CaseAccepted create(SubmissionContext context, CreateCaseCommand command) {
        return create(context, command, null);
    }

    CaseAccepted createForMcp(UUID accountId, CreateCaseCommand command, Consumer<String> onCreated) {
        return create(new SubmissionContext.Tenant(accountId), command, onCreated);
    }

    private CaseAccepted create(SubmissionContext context, CreateCaseCommand command, Consumer<String> onCreated) {
        UUID scope;
        CreateCaseCommand input;
        if (context instanceof SubmissionContext.Guest guest) {
            scope = guest.sessionScope();
            input = CaseInputValidation.validateGuest(command);
        } else if (context instanceof SubmissionContext.Tenant tenant) {
            scope = submissionScope(tenant.accountId());
            input = validateForAccount(tenant.accountId(), command);
        } else {
            throw failure(CaseErrorCode.UNAUTHENTICATED);
        }
        String fingerprint = fingerprint(input);
        try {
            try {
                return writes.execute(transaction -> {
                    CaseAccepted result = createOrReplay(scope, input, fingerprint);
                    if (onCreated != null && !result.replayed()) {
                        onCreated.accept(result.caseId());
                    }
                    return result;
                });
            } catch (DataIntegrityViolationException collision) {
                // PostgreSQL waits on the unique key. The losing transaction is fully rolled back before this read.
                return reads.execute(transaction -> submissions.findByGuestScopeAndOperationId(scope, input.operationId())
                        .map(existing -> replay(existing, fingerprint))
                        .orElseThrow(() -> failure(CaseErrorCode.STORAGE_UNAVAILABLE)));
            }
        } catch (DataAccessException | TransactionException exception) {
            throw failure(CaseErrorCode.STORAGE_UNAVAILABLE);
        }
    }

    CreateCaseCommand validateForAccount(UUID accountId, CreateCaseCommand command) {
        String email = tenantAccounts.verifiedEmail(accountId).orElseThrow(() -> failure(CaseErrorCode.UNAUTHENTICATED));
        CreateCaseCommand input = command == null ? null
                : new CreateCaseCommand(command.operationId(), command.title(), command.unit(), command.description(), email);
        return CaseInputValidation.validateGuest(input);
    }

    Optional<String> findSubmission(UUID accountId, UUID operation) {
        return reads.execute(transaction -> submissions.findByGuestScopeAndOperationId(submissionScope(accountId), operation.toString())
                .map(SubmissionEntity::getCaseId));
    }

    private static UUID submissionScope(UUID accountId) {
        return UUID.nameUUIDFromBytes(("tenant:" + accountId).getBytes(StandardCharsets.UTF_8));
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
                return CaseProjections.tenantView(stored, urgencies.publicHistory(stored.getId()), messages.history(stored.getId()), staffMessages.history(stored.getId()));
            });
        } catch (DataAccessException | TransactionException exception) {
            throw failure(CaseErrorCode.STORAGE_UNAVAILABLE);
        }
    }


    public List<TenantCaseView> listForAccount(UUID accountId) {
        return reads.execute(transaction -> {
            String email = tenantAccounts.verifiedEmail(accountId).orElseThrow(() -> failure(CaseErrorCode.UNAUTHENTICATED));
            return cases.findByContactEmailOrderByReceivedAtDesc(email).stream()
                    .map(stored -> CaseProjections.tenantView(stored, List.of(), List.of(), List.of())).toList();
        });
    }

    public TenantCaseView readForAccount(UUID accountId, String caseId) {
        return reads.execute(transaction -> {
            CaseEntity stored = findForAccount(accountId, caseId);
            return CaseProjections.tenantView(stored, urgencies.publicHistory(caseId), messages.history(caseId), staffMessages.history(caseId));
        });
    }

    CaseStatusView readStatusForAccount(UUID accountId, String caseId) {
        try {
            return reads.execute(transaction -> CaseProjections.state(findForAccount(accountId, caseId)));
        } catch (DataAccessException | TransactionException exception) {
            throw failure(CaseErrorCode.STORAGE_UNAVAILABLE);
        }
    }

    private CaseEntity findForAccount(UUID accountId, String caseId) {
        String email = tenantAccounts.verifiedEmail(accountId)
                .orElseThrow(() -> failure(CaseErrorCode.UNAUTHENTICATED));
        return cases.findById(caseId)
                .filter(stored -> email.equals(stored.getContactEmail()))
                .orElseThrow(() -> failure(CaseErrorCode.CASE_NOT_AVAILABLE));
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
