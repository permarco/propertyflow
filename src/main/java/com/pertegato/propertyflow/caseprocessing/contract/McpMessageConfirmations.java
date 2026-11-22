package com.pertegato.propertyflow.caseprocessing.contract;

import com.pertegato.propertyflow.access.contract.McpAccountContext;
import com.pertegato.propertyflow.access.contract.McpScope;
import java.util.UUID;

public interface McpMessageConfirmations {

    UUID prepare(McpAccountContext account, McpScope scope, String caseId, String text, boolean answerRequired);

    View continuation(McpAccountContext account, McpScope scope, UUID operation, String caseId, String text,
            boolean answerRequired, boolean cancel);

    UUID prepareCaseAction(McpAccountContext account, CaseRequest request);

    View continueCaseAction(McpAccountContext account, UUID operation, CaseRequest request, boolean cancel);

    View view(UUID account, boolean staff, UUID operation);

    View decide(UUID account, boolean staff, UUID operation, boolean confirm);

    void auditDenied(McpAccountContext account, McpScope scope, String outcome);

    enum Status {
        WAITING,
        SUBMITTED,
        SENT,
        CANCELLED,
        REJECTED
    }

    sealed interface CaseRequest permits NewCase, Transition {
        McpScope scope();
    }

    record NewCase(String title, String unit, String description) implements CaseRequest {
        @Override
        public McpScope scope() {
            return McpScope.TENANT_INTAKE;
        }

        @Override
        public String toString() {
            return "NewCase[redacted]";
        }
    }

    record Transition(String caseId, Change change) implements CaseRequest {
        @Override
        public McpScope scope() {
            return McpScope.STAFF_TRANSITIONS;
        }
    }

    enum Change {
        FORTSETZEN("Bearbeitung fortsetzen"),
        ABSCHLIESSEN("Fall abschliessen"),
        WIEDEREROEFFNEN("Fall wiedereröffnen");

        private final String label;

        Change(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    record View(UUID operation, String caseId, String title, String text, boolean answerRequired, Status status, UUID messageId,
            McpScope scope, String actionArgument) {

        public View(UUID operation, String caseId, String title, String text, boolean answerRequired, Status status, UUID messageId) {
            this(operation, caseId, title, text, answerRequired, status, messageId, null, null);
        }

        public boolean intake() {
            return scope == McpScope.TENANT_INTAKE;
        }

        public boolean transition() {
            return scope == McpScope.STAFF_TRANSITIONS;
        }

        public String changeLabel() {
            return transition() ? Change.valueOf(actionArgument).label() : "";
        }

        @Override
        public String toString() {
            return "MCP-Bestätigung " + operation + " (" + status + ")";
        }
    }
}
