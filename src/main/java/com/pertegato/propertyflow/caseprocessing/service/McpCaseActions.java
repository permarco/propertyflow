package com.pertegato.propertyflow.caseprocessing.service;

import com.pertegato.propertyflow.access.contract.McpAccountContext;
import com.pertegato.propertyflow.access.contract.WebAccountContext;
import com.pertegato.propertyflow.caseprocessing.contract.CaseActionException;
import com.pertegato.propertyflow.caseprocessing.contract.CaseReopening;
import com.pertegato.propertyflow.caseprocessing.contract.CaseStatus;
import com.pertegato.propertyflow.caseprocessing.contract.CreateCaseCommand;
import com.pertegato.propertyflow.caseprocessing.contract.McpCaseReading;
import com.pertegato.propertyflow.caseprocessing.contract.McpMessageConfirmations.CaseRequest;
import com.pertegato.propertyflow.caseprocessing.contract.McpMessageConfirmations.Change;
import com.pertegato.propertyflow.caseprocessing.contract.McpMessageConfirmations.NewCase;
import com.pertegato.propertyflow.caseprocessing.contract.McpMessageConfirmations.Status;
import com.pertegato.propertyflow.caseprocessing.contract.McpMessageConfirmations.Transition;
import com.pertegato.propertyflow.caseprocessing.contract.StaffTaskDecisions;
import com.pertegato.propertyflow.caseprocessing.contract.StaffTaskDecisions.StaffTask;
import com.pertegato.propertyflow.caseprocessing.contract.TenantCaseView;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseReopenStore;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffDecisionStore;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("propertyflow-backend")
@ConditionalOnProperty(name = "propertyflow.mcp.backend.enabled", havingValue = "true")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class McpCaseActions {

    private static final Pattern CASE_ID = Pattern.compile(McpCaseReading.CASE_ID_PATTERN);

    private final PersistentCaseService cases;
    private final StaffCaseService staffCases;
    private final StaffTaskDecisions staffDecisions;
    private final CaseReopening reopening;
    private final StaffDecisionStore decisions;
    private final CaseReopenStore reopenOrders;

    McpCaseActions(PersistentCaseService cases, StaffCaseService staffCases, StaffTaskDecisions staffDecisions,
            CaseReopening reopening, StaffDecisionStore decisions, CaseReopenStore reopenOrders) {
        this.cases = cases;
        this.staffCases = staffCases;
        this.staffDecisions = staffDecisions;
        this.reopening = reopening;
        this.decisions = decisions;
        this.reopenOrders = reopenOrders;
    }

    Basis prepare(McpAccountContext account, UUID operation, CaseRequest request) {
        return switch (request) {
            case NewCase intake -> {
                CreateCaseCommand input = validateIntake(account, operation, intake);
                yield new Basis(null, input.title().strip(), input.description().strip(), input.unit().strip(), 0, null, null);
            }
            case Transition transition -> prepareTransition(account, transition);
        };
    }

    boolean matches(McpAccountContext account, UUID operation, CaseRequest request, Basis basis) {
        return switch (request) {
            case NewCase intake -> {
                CreateCaseCommand input = validateIntake(account, operation, intake);
                yield input.title().strip().equals(basis.title()) && input.unit().strip().equals(basis.argument())
                        && input.description().strip().equals(basis.text());
            }
            case Transition transition -> Objects.equals(transition.caseId(), basis.caseId())
                    && transition.change() != null && transition.change().name().equals(basis.argument());
        };
    }

    private CreateCaseCommand validateIntake(McpAccountContext account, UUID operation, NewCase intake) {
        return cases.validateForAccount(account.accountId(),
                new CreateCaseCommand(operation.toString(), intake.title(), intake.unit(), intake.description(), null));
    }

    private Basis prepareTransition(McpAccountContext account, Transition request) {
        require(request.caseId() != null && CASE_ID.matcher(request.caseId()).matches() && request.change() != null);
        WebAccountContext actor = new WebAccountContext(account.accountId());
        TenantCaseView view = staffCases.readPublicForStaff(actor, request.caseId());
        if (request.change() == Change.WIEDEREROEFFNEN) {
            require(view.state().status() == CaseStatus.CLOSED);
            return new Basis(request.caseId(), view.title(), "", request.change().name(), view.version(), null, null);
        }

        StaffTask task = staffDecisions.current(actor, request.caseId());
        require(task.available() && task.version() == view.version() && task.taskKey() != null && task.reviewToken() != null
                && (request.change() == Change.FORTSETZEN ? task.canContinue() : task.canClose()));
        return new Basis(request.caseId(), view.title(), "", request.change().name(), view.version(), Long.valueOf(task.taskKey()),
                task.reviewToken());
    }

    void execute(McpAccountContext account, UUID operation, boolean intake, Basis basis, Consumer<String> onCreated) {
        if (intake) {
            cases.createForMcp(account.accountId(),
                    new CreateCaseCommand(operation.toString(), basis.title(), basis.argument(), basis.text(), null), onCreated);
            return;
        }
        WebAccountContext actor = new WebAccountContext(account.accountId());
        switch (Change.valueOf(basis.argument())) {
            case FORTSETZEN -> staffDecisions.continueWork(actor, basis.caseId(), operation, basis.taskKey(), basis.version(), basis.reviewToken());
            case ABSCHLIESSEN -> staffDecisions.closeCase(actor, basis.caseId(), operation, basis.taskKey(), basis.version(), basis.reviewToken());
            case WIEDEREROEFFNEN -> reopening.request(actor, basis.caseId(), operation, basis.version());
        }
    }

    Optional<Outcome> outcome(McpAccountContext account, UUID operation, boolean intake, Basis basis) {
        if (intake) {
            return cases.findSubmission(account.accountId(), operation).map(caseId -> new Outcome(Status.SENT, caseId));
        }
        String state = Change.valueOf(basis.argument()) == Change.WIEDEREROEFFNEN
                ? reopenOrders.find(basis.caseId(), account.accountId(), operation).map(CaseReopenStore.Order::state).orElse(null)
                : decisions.find(basis.caseId(), account.accountId(), operation).map(StaffDecisionStore.Decision::state).orElse(null);
        return Optional.ofNullable(state).map(value -> new Outcome(switch (value) {
            case "APPLIED" -> Status.SENT;
            case "REJECTED" -> Status.REJECTED;
            default -> Status.SUBMITTED;
        }, basis.caseId()));
    }

    private static void require(boolean valid) {
        if (!valid) {
            throw new CaseActionException("MCP_CASE_ACTION_UNAVAILABLE");
        }
    }

    record Basis(String caseId, String title, String text, String argument, long version, Long taskKey, String reviewToken) {
        @Override
        public String toString() {
            return "MCP-Fallaktion [redacted]";
        }
    }

    record Outcome(Status status, String caseId) {
    }
}
