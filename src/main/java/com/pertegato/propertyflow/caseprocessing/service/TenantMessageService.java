package com.pertegato.propertyflow.caseprocessing.service;

import com.pertegato.propertyflow.access.contract.TenantAccountAccess;
import com.pertegato.propertyflow.caseprocessing.contract.CaseActionException;
import com.pertegato.propertyflow.caseprocessing.contract.TenantMessageSubmission;
import com.pertegato.propertyflow.caseprocessing.delivery.CaseProcessGateway;
import com.pertegato.propertyflow.caseprocessing.delivery.PersonalLinkCodec;
import com.pertegato.propertyflow.caseprocessing.delivery.TenantMessageGateway;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseDataStore.Leader;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseDataStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.CaseState;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore;
import com.pertegato.propertyflow.caseprocessing.persistence.TenantMessageStore.Access;
import com.pertegato.propertyflow.caseprocessing.persistence.TenantMessageStore.Message;
import com.pertegato.propertyflow.caseprocessing.persistence.TenantMessageStore;
import com.pertegato.propertyflow.intake.contract.CaseDescription;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Profile("propertyflow-backend & !frontend-demo")
public class TenantMessageService implements TenantMessageSubmission {

    private final TenantAccountAccess accounts;
    private final TenantMessageStore messages;
    private final CaseWorkflowStore workflow;
    private final CaseDataStore cases;
    private final PersonalLinkCodec links;
    private final CaseProcessGateway availability;
    private final TenantMessageGateway gateway;
    private final TransactionTemplate writes;

    public TenantMessageService(TenantMessageStore messages, CaseWorkflowStore workflow, CaseDataStore cases, PersonalLinkCodec links,
            CaseProcessGateway availability, TenantMessageGateway gateway, PlatformTransactionManager transactions, TenantAccountAccess accounts) {
        this.accounts = accounts;
        this.messages = messages;
        this.workflow = workflow;
        this.cases = cases;
        this.links = links;
        this.availability = availability;
        this.gateway = gateway;
        writes = new TransactionTemplate(transactions);
        writes.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public UUID send(String token, String operation, String rawText) {
        if (!links.isWellFormed(token)) {
            throw new CaseActionException("CASE_NOT_AVAILABLE");
        }
        String hash = links.hash(token);
        return sendAuthorized(lock -> messages.access(hash, lock).orElseThrow(() -> new CaseActionException("CASE_NOT_AVAILABLE")),
                operation, rawText, null, null);
    }

    public UUID sendForAccount(UUID accountId, String caseId, String operation, String rawText) {
        return sendForAccount(accountId, caseId, operation, rawText, null);
    }

    UUID sendForAccount(UUID accountId, String caseId, String operation, String rawText, Consumer<UUID> onStored) {
        if (operation == null || operation.isBlank() || operation.length() > 80) {
            throw new CaseActionException("INVALID_MESSAGE");
        }
        return sendAuthorized(lock -> accountAccess(accountId, caseId, lock),
                accountOperation(accountId, operation), rawText, accountId, onStored);
    }

    boolean availableForAccount(UUID accountId, String caseId) {
        accountAccess(accountId, caseId, false);
        return cases.leader(caseId).filter(leader -> availability.active(leader.key(), leader.tenant())).isPresent();
    }

    Optional<Message> findForAccount(UUID accountId, String caseId, String operation) {
        return messages.find(accountAccess(accountId, caseId, false), accountOperation(accountId, operation));
    }

    private Access accountAccess(UUID accountId, String caseId, boolean lock) {
        String email = accounts.verifiedEmail(accountId).orElseThrow(() -> new CaseActionException("CASE_NOT_AVAILABLE"));
        return messages.accountAccess(caseId, email, lock).orElseThrow(() -> new CaseActionException("CASE_NOT_AVAILABLE"));
    }

    private String accountOperation(UUID accountId, String operation) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(("account:" + accountId + ":" + operation).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Die Absende-ID kann nicht geprüft werden.", exception);
        }
    }

    private UUID sendAuthorized(Function<Boolean, Access> authorize, String operation, String rawText, UUID actor,
            Consumer<UUID> onStored) {
        String text = CaseDescription.normalizeLineEndings(rawText);
        if (operation == null || operation.isBlank() || operation.length() > 80 || text == null || text.isBlank() || text.length() > 2000) {
            throw new CaseActionException("INVALID_MESSAGE");
        }
        Access access = authorize.apply(false);
        Message previous = messages.find(access, operation).orElse(null);
        if (previous != null) {
            sameText(previous, text);
            if (previous.correlated()) {
                return previous.id();
            }
        }
        Leader leader = cases.leader(access.caseId()).orElseThrow(() -> new CaseActionException("PROCESS_NOT_READY"));
        if (!availability.active(leader.key(), leader.tenant())) {
            throw new IllegalStateException("WORKFLOW_UNAVAILABLE");
        }
        Message saved = writes.execute(status -> {
            CaseState state = workflow.lockCase(access.caseId()).orElseThrow(() -> new CaseActionException("CASE_NOT_AVAILABLE"));
            Access current = authorize.apply(true);
            if (!access.equals(current)) {
                throw new CaseActionException("CASE_NOT_AVAILABLE");
            }
            Message existing = messages.find(access, operation).orElse(null);
            if (existing != null) {
                sameText(existing, text);
                if (existing.correlated()) {
                    return existing;
                }
            }
            if ("CLOSED".equals(state.status())) {
                throw new CaseActionException("CASE_CLOSED");
            }
            if (!cases.leader(access.caseId()).filter(leader::equals).isPresent()
                    || existing != null && existing.leader() != leader.key()) {
                throw new CaseActionException("PROCESS_CHANGED");
            }
            if (existing != null) {
                return existing;
            }
            Message created = messages.save(access, operation, text, leader.key(), leader.tenant());
            if (actor != null) {
                messages.recordActor(created.id(), actor);
            }
            if (onStored != null) {
                onStored.accept(created.id());
            }
            return created;
        });
        if (!saved.correlated()) {
            gateway.correlate(saved.id(), saved.leader(), saved.tenant());
            messages.correlated(saved.id());
        }
        return saved.id();
    }

    public boolean receive(String caseId, UUID messageId, long leader, long child, long parent, String tenant) {
        return Boolean.TRUE.equals(writes.execute(status -> {
            CaseState state = workflow.lockCase(caseId).orElseThrow(() -> new WorkflowRejectedException("UNKNOWN_CASE"));
            Message message = messages.get(messageId);
            if (!message.caseId().equals(caseId) || message.leader() != leader || !message.tenant().equals(tenant)) {
                throw new WorkflowRejectedException("OTHER_MESSAGE_PROCESS");
            }
            if ("CLOSED".equals(state.status()) || workflow.leadingInstance(caseId).filter(key -> key == leader).isEmpty()) {
                messages.supersede(messageId);
                return false;
            }
            if (message.child() != null) {
                return message.child() == child && message.parentElement() == parent && "RECEIVED".equals(message.state());
            }
            messages.receive(messageId, child, parent);
            return true;
        }));
    }

    private void sameText(Message message, String text) {
        if (!message.text().equals(text)) {
            throw new CaseActionException("IDEMPOTENCY_CONFLICT");
        }
    }

    public String phase(String caseId, long leader) {
        return writes.execute(status -> {
            CaseState state = workflow.lockCase(caseId).orElseThrow(() -> new WorkflowRejectedException("UNKNOWN_CASE"));
            return workflow.leadingInstance(caseId).filter(key -> key == leader).isPresent() ? state.status() : "CLOSED";
        });
    }
}
