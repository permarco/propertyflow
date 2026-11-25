package com.pertegato.propertyflow.caseprocessing.service;

import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.CaseState;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.Entry;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore;
import com.pertegato.propertyflow.caseprocessing.persistence.TenantReplyStore.Call;
import com.pertegato.propertyflow.caseprocessing.persistence.TenantReplyStore;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("propertyflow-backend")
@Transactional(propagation = Propagation.REQUIRES_NEW)
@RequiredArgsConstructor
public class AutomaticQuestionRoundService {

    private final CaseWorkflowStore workflow;
    private final TenantReplyStore replies;

    public Call source(UUID decision, String caseId, long leader) {
        return source(decision, caseId, leader, "QUESTION");
    }

    public Call source(UUID decision, String caseId, long leader, String kind) {
        require("QUESTION".equals(kind) || "CLOSE".equals(kind));
        return replies.automaticSource(decision, caseId, leader, kind);
    }

    public Entry prepare(UUID decision, String caseId, long leader, long element, UUID replyCall) {
        return prepare(decision, caseId, leader, element, replyCall, "QUESTION");
    }

    public Entry prepare(UUID decision, String caseId, long leader, long element, UUID replyCall, String kind) {
        CaseState state = workflow.lockCase(caseId).orElseThrow();
        require(workflow.leadingInstance(caseId).filter(key -> key == leader).isPresent());
        Call source = source(decision, caseId, leader, kind);
        require(source.id().equals(replyCall));
        Entry existing = workflow.questionEntry(decision, caseId).orElse(null);
        if (existing != null) {
            require(existing.prepareElementKey() == element && kind.equals(workflow.entryKind(existing.id())));
            return existing;
        }
        require("WAITING_FOR_TENANT".equals(state.status()) && !workflow.replyWaiting(caseId, leader));
        // Die gespeicherte Entscheidung erlaubt den Aufruf, keine Veröffentlichung auf ihrer alten Grundlage.
        // Das neue Kind liest und prüft den aktuellen Fallstand über die zentrale Auswertung erneut.
        return workflow.createAutomaticEntry(state, leader, element, decision, kind);
    }

    private void require(boolean valid) {
        if (!valid) {
            throw new WorkflowRejectedException("INVALID_QUESTION_ROUND_CONTEXT");
        }
    }
}
