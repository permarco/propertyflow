package com.pertegato.propertyflow.caseprocessing.service;

import com.pertegato.propertyflow.access.contract.StaffAccessDecision;
import com.pertegato.propertyflow.access.contract.StaffAccountAccess;
import com.pertegato.propertyflow.access.contract.WebAccountContext;
import com.pertegato.propertyflow.caseprocessing.contract.CaseActionException;
import com.pertegato.propertyflow.caseprocessing.contract.FollowUpCommands;
import com.pertegato.propertyflow.caseprocessing.delivery.FollowUpGateway;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseDataStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWaitStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWaitStore.Wait;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.CaseState;
import com.pertegato.propertyflow.caseprocessing.persistence.FollowUpCommandStore;
import com.pertegato.propertyflow.caseprocessing.persistence.FollowUpCommandStore.Command;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffDecisionStore;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@Service
@Profile("propertyflow-backend")
public class FollowUpCommandService implements FollowUpCommands {

    private final StaffAccountAccess access;
    private final CaseWorkflowStore workflow;
    private final CaseDataStore cases;
    private final CaseWaitStore waits;
    private final StaffDecisionStore decisions;
    private final FollowUpCommandStore commands;
    private final FollowUpGateway engine;
    private final TransactionTemplate writes;
    private final JsonMapper json = JsonMapper.builder().build();

    public FollowUpCommandService(StaffAccountAccess access, CaseWorkflowStore workflow, CaseDataStore cases, CaseWaitStore waits,
            StaffDecisionStore decisions, FollowUpCommandStore commands, FollowUpGateway engine, PlatformTransactionManager transactions) {
        this.access = access;
        this.workflow = workflow;
        this.cases = cases;
        this.waits = waits;
        this.decisions = decisions;
        this.commands = commands;
        this.engine = engine;
        writes = new TransactionTemplate(transactions);
        writes.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public Current current(WebAccountContext actor, String caseId) {
        authorize(actor);
        Basis basis = writes.execute(status -> basis(caseId));
        if (basis == null || !live(basis)) {
            return new Current(false, null, 0);
        }
        return new Current(true, basis.waiting().id(), basis.version());
    }

    @Override
    public String submit(WebAccountContext actor, String caseId, UUID operation, UUID waitId, long version,
            boolean delete, Integer days, LocalDate date, String reason) {
        return submit(actor, caseId, operation, waitId, version, delete ? "DELETE" : "CHANGE", days, date, reason);
    }

    @Override
    public String close(WebAccountContext actor, String caseId, UUID operation, UUID waitId, long version, String reason) {
        return submit(actor, caseId, operation, waitId, version, "CLOSE", null, null, reason);
    }

    private String submit(WebAccountContext actor, String caseId, UUID operation, UUID waitId, long version,
            String action, Integer days, LocalDate date, String reason) {
        authorize(actor);
        boolean delete = "DELETE".equals(action);
        boolean close = "CLOSE".equals(action);
        if (operation == null || waitId == null || version < 0 || reason == null || reason.length() > 1000
                || delete && (days != null || date != null)) {
            throw new CaseActionException("INVALID_INPUT");
        }
        String input = close ? json.writeValueAsString(new CloseInput(waitId, version, action, reason))
                : json.writeValueAsString(new Input(waitId, version, delete, days, date == null ? null : date.toString(), reason));
        Command existing = commands.find(caseId, actor.accountId(), operation).orElse(null);
        if (existing != null) {
            require(existing.input().equals(input), "IDEMPOTENCY_CONFLICT");
            return result(existing);
        }
        Instant due = delete || close ? null : FollowUpDate.calculate(Clock.systemUTC(), days, date);
        Basis candidate = writes.execute(status -> basis(caseId));
        require(candidate != null && candidate.version() == version && candidate.waiting().id().equals(waitId), "CASE_CHANGED");
        require(live(candidate), "WORKFLOW_UNAVAILABLE");
        Command prepared = writes.execute(status -> {
            authorize(actor);
            Basis current = basis(caseId);
            Command concurrent = commands.find(caseId, actor.accountId(), operation).orElse(null);
            if (concurrent != null) {
                require(concurrent.input().equals(input), "IDEMPOTENCY_CONFLICT");
                return concurrent;
            }
            require(candidate.equals(current), "CASE_CHANGED");
            return commands.prepare(workflow.lockCase(caseId).orElseThrow(), candidate.waiting(), actor.accountId(), operation,
                    candidate.fingerprint(), action, input, reason, due);
        });
        if (commands.attempt(prepared.id())) {
            try {
                if (close) {
                    engine.close(prepared.id(), candidate.waiting().child(), candidate.tenant(), waitId);
                } else {
                    engine.correlate(prepared.id(), candidate.waiting().child(), candidate.tenant(), waitId, delete);
                }
            } catch (CaseActionException rejected) {
                commands.reject(prepared.id());
                throw rejected;
            }
            // An uncertain response is never automatically sent again to another wait.
        }
        return result(commands.get(prepared.id()));
    }

    @Override
    public String result(WebAccountContext actor, String caseId, UUID operation) {
        authorize(actor);
        return result(commands.find(caseId, actor.accountId(), operation).orElseThrow(() -> new CaseActionException("COMMAND_NOT_FOUND")));
    }

    public Applied apply(UUID id, long leader, long child, long staff, boolean delete) {
        return writes.execute(status -> {
            Command command = commands.get(id);
            CaseState state = workflow.lockCase(command.caseId()).orElseThrow();
            Wait previous = waits.get(command.waitId()).orElseThrow();
            if (previous.leader() != leader || previous.child() == null || previous.child() != child || previous.staff() != staff
                    || !(delete ? "DELETE" : "CHANGE").equals(command.action())) {
                throw new WorkflowRejectedException("OTHER_FOLLOW_UP_COMMAND");
            }
            if ("APPLYING".equals(command.state()) || "APPLIED".equals(command.state())) {
                UUID resultingWait = delete || waits.get(command.id()).isEmpty() ? previous.id() : command.id();
                boolean current = workflow.leadingInstance(state.id()).filter(key -> key == leader).isPresent()
                        && (delete ? "IN_PROGRESS".equals(state.status())
                                : waits.active(state.id()).filter(waiting -> waiting.id().equals(resultingWait)).isPresent());
                if (!current) {
                    return new Applied(id, previous.id(), previous.dueAt(), delete, false);
                }
                return new Applied(command.id(), resultingWait, command.due(), delete, true);
            }
            if (!"PENDING".equals(command.state()) || !"ACTIVE".equals(previous.state())
                    || !"WAITING_FOR_FOLLOW_UP".equals(state.status()) || state.version() != command.version()
                    || workflow.leadingInstance(state.id()).filter(key -> key == leader).isEmpty()
                    || !decisions.reviewFingerprint(state.id(), leader).equals(command.fingerprint())
                    || access.checkStaffAccess(new WebAccountContext(command.actor())) != StaffAccessDecision.ALLOWED) {
                commands.reject(id);
                return new Applied(id, previous.id(), previous.dueAt(), delete, false);
            }
            if (!delete && previous.dueAt().equals(command.due())) {
                commands.unchanged(id);
                return new Applied(id, previous.id(), previous.dueAt(), false, true);
            }
            commands.apply(command, state, previous);
            return new Applied(id, delete ? previous.id() : id, command.due(), delete, true);
        });
    }

    public void confirmed(UUID id) {
        commands.applied(id);
    }

    public boolean prepareClosure(UUID id, long leader, long child, long staff) {
        return Boolean.TRUE.equals(writes.execute(status -> {
            Command command = commands.get(id);
            CaseState state = workflow.lockCase(command.caseId()).orElseThrow();
            Wait waiting = waits.get(command.waitId()).orElseThrow();
            if (!"CLOSE".equals(command.action()) || waiting.leader() != leader || waiting.child() == null
                    || waiting.child() != child || waiting.staff() != staff
                    || workflow.leadingInstance(state.id()).filter(key -> key == leader).isEmpty()) {
                throw new WorkflowRejectedException("OTHER_FOLLOW_UP_CLOSURE");
            }
            // Nach Annahme prüft der gemeinsame Abschlussweg erneut; ein Retry darf den bereits gewählten Pfad nicht wechseln.
            if ("APPLYING".equals(command.state()) || "APPLIED".equals(command.state())) {
                return true;
            }
            if (!"PENDING".equals(command.state()) || !"ACTIVE".equals(waiting.state())
                    || !"WAITING_FOR_FOLLOW_UP".equals(state.status()) || state.version() != command.version()
                    || !decisions.reviewFingerprint(state.id(), leader).equals(command.fingerprint())
                    || access.checkStaffAccess(new WebAccountContext(command.actor())) != StaffAccessDecision.ALLOWED) {
                commands.reject(id);
                return false;
            }
            commands.prepareClosure(id);
            return true;
        }));
    }

    private Basis basis(String caseId) {
        CaseState state = workflow.lockCase(caseId).orElseThrow(() -> new CaseActionException("CASE_NOT_AVAILABLE"));
        Wait waiting = waits.active(caseId).orElse(null);
        CaseDataStore.Leader leader = cases.leader(caseId).orElse(null);
        if (!"WAITING_FOR_FOLLOW_UP".equals(state.status()) || waiting == null || waiting.child() == null || leader == null
                || leader.key() != waiting.leader() || commands.pending(caseId) || commands.interruptPending(waiting)) {
            return null;
        }
        return new Basis(state.version(), waiting, leader.tenant(), decisions.reviewFingerprint(caseId, leader.key()));
    }

    private boolean live(Basis basis) {
        Wait waiting = basis.waiting();
        return engine.available(waiting.child(), waiting.leader(), waiting.staff(), basis.tenant(), waiting.id());
    }

    private String result(Command command) {
        if ("REJECTED".equals(command.state())) {
            throw new CaseActionException("CASE_CHANGED");
        }
        return "APPLIED".equals(command.state()) ? "APPLIED" : "PENDING";
    }

    private void authorize(WebAccountContext actor) {
        StaffAccessDecision decision = actor == null ? StaffAccessDecision.UNAUTHENTICATED : access.checkStaffAccess(actor);
        require(decision == StaffAccessDecision.ALLOWED, decision == StaffAccessDecision.UNAUTHENTICATED ? "UNAUTHENTICATED" : "FORBIDDEN");
    }

    private void require(boolean condition, String code) {
        if (!condition) {
            throw new CaseActionException(code);
        }
    }

    public record Applied(UUID command, UUID waitId, Instant due, boolean delete, boolean accepted) {
    }

    private record Basis(long version, Wait waiting, String tenant, String fingerprint) {
    }

    private record Input(UUID waitId, long version, boolean delete, Integer days, String date, String reason) {
    }

    private record CloseInput(UUID waitId, long version, String action, String reason) {
    }
}
