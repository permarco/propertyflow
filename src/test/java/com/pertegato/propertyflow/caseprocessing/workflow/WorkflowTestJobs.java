package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.StartOrder;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.search.response.ProcessInstance;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

final class WorkflowTestJobs {

    private static final Duration WAIT_LIMIT = Duration.ofSeconds(25);

    private WorkflowTestJobs() {
    }

    static long rootInstanceKey(CamundaClient engine, StartOrder order) throws InterruptedException {
        long deadline = System.nanoTime() + WAIT_LIMIT.toNanos();
        do {
            List<ProcessInstance> matches = engine.newProcessInstanceSearchRequest()
                    .filter(filter -> filter.businessId(order.id().toString()).processDefinitionKey(order.definitionKey()).tenantId(order.tenantId()))
                    .send().join().items();
            if (matches.size() == 1 && matches.getFirst().getParentProcessInstanceKey() == null) {
                return matches.getFirst().getProcessInstanceKey();
            }
            if (!matches.isEmpty()) {
                throw new AssertionError("Der Startauftrag " + order.id() + " ist keiner eindeutigen Hauptprozessinstanz zugeordnet.");
            }
            Thread.sleep(100);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Keine Hauptprozessinstanz für Startauftrag " + order.id());
    }

    static ActivatedJob activateForRoot(CamundaClient engine, String type, long expectedRoot) throws InterruptedException {
        long deadline = System.nanoTime() + WAIT_LIMIT.toNanos();
        do {
            List<ActivatedJob> jobs = engine.newActivateJobsCommand()
                    .jobType(type)
                    .maxJobsToActivate(1)
                    .timeout(Duration.ofSeconds(30))
                    .requestTimeout(Duration.ofSeconds(1))
                    .send().join().getJobs();
            if (!jobs.isEmpty()) {
                ActivatedJob job = jobs.getFirst();
                if (job.getProcessInstanceKey() != expectedRoot && !Objects.equals(job.getRootProcessInstanceKey(), expectedRoot)) {
                    throw new AssertionError("Fremder Job " + type + " erhalten. Erwarteter Hauptprozess " + expectedRoot
                            + ", erhaltener Job " + job.getKey() + ", Instanz " + job.getProcessInstanceKey()
                            + ", Hauptprozess " + job.getRootProcessInstanceKey());
                }
                return job;
            }
            Thread.sleep(100);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Kein Job " + type + " für Hauptprozess " + expectedRoot + " innerhalb von " + WAIT_LIMIT.toSeconds() + " s.");
    }
}
