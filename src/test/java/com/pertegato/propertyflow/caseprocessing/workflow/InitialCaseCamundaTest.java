package com.pertegato.propertyflow.caseprocessing.workflow;

import com.pertegato.propertyflow.access.contract.SubmissionContext;
import com.pertegato.propertyflow.caseprocessing.contract.CaseAccepted;
import com.pertegato.propertyflow.caseprocessing.contract.CaseCreation;
import com.pertegato.propertyflow.caseprocessing.contract.CaseReading;
import com.pertegato.propertyflow.caseprocessing.contract.CaseStatus;
import com.pertegato.propertyflow.caseprocessing.contract.CreateCaseCommand;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseWorkflowStore.StartOrder;
import com.pertegato.propertyflow.caseprocessing.service.CaseWorkflowService;
import com.pertegato.propertyflow.caseprocessing.workflow.ProcessStartGateway.StartResult;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.command.ClientHttpException;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.worker.JobClient;
import java.net.URI;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Real engine tests. Workers are invoked with real activated jobs so failure windows can be controlled.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "propertyflow.workflow.enabled=false")
@ActiveProfiles("propertyflow-backend")
@EnabledIfEnvironmentVariable(named = "PROPERTYFLOW_TEST_DATABASE_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "PROPERTYFLOW_TEST_CAMUNDA_URL", matches = ".+")
class InitialCaseCamundaTest {

    @Autowired
    private CaseCreation creation;

    @Autowired
    private CaseReading reading;

    @Autowired
    private CaseWorkflowStore store;

    @Autowired
    private CaseWorkflowService service;

    @Autowired
    private JdbcTemplate database;

    private CamundaClient camunda;
    private InitialCaseWorkers workers;
    private CamundaProcessStartGateway gateway;
    private CaseAccepted accepted;
    private StartOrder order;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("PROPERTYFLOW_TEST_DATABASE_URL"));
        properties.add("spring.datasource.username", () -> "propertyflow_test");
        properties.add("spring.datasource.password", () -> "propertyflow_test");
        properties.add("propertyflow.links.encryption-key", () -> Base64.getEncoder().encodeToString(new byte[32]));
    }

    @BeforeEach
    void setUp() {
        assertEquals("propertyflow_test", database.queryForObject("SELECT current_database()", String.class));
        database.execute("TRUNCATE pf_case CASCADE");
        camunda = CamundaClient.newClientBuilder()
                .restAddress(URI.create(System.getenv("PROPERTYFLOW_TEST_CAMUNDA_URL")))
                .preferRestOverGrpc(true)
                .defaultRequestTimeout(Duration.ofSeconds(5))
                .build();
        DeploymentEvent deployment = camunda.newDeployResourceCommand()
                .addResourceFromClasspath("workflow/initial-case.bpmn")
                .addResourceFromClasspath("workflow/initial-automatic.bpmn")
                .send().join();
        long definitionKey = deployment.getProcesses().stream()
                .filter(process -> InitialCaseWorkers.CASE_PROCESS.equals(process.getBpmnProcessId()))
                .findFirst().orElseThrow().getProcessDefinitionKey();
        workers = new InitialCaseWorkers(camunda, service);
        gateway = new CamundaProcessStartGateway(camunda);
        accepted = creation.create(new SubmissionContext.Guest(UUID.randomUUID()),
                new CreateCaseCommand(UUID.randomUUID().toString(), "Heizung kalt", "Wohnung 4", "Seit gestern kalt.", "tenant@example.test"));
        order = store.claim(definitionKey, "<default>", Duration.ofMinutes(2)).orElseThrow();
    }

    @AfterEach
    void cleanUp() {
        if (camunda != null) {
            if (order != null) {
                for (ProcessInstance instance : camunda.newProcessInstanceSearchRequest()
                        .filter(filter -> filter.businessId(order.id().toString()).processDefinitionKey(order.definitionKey()))
                        .send().join().items()) {
                    if (instance.getState().name().equals("ACTIVE")) {
                        camunda.newCancelInstanceCommand(instance.getProcessInstanceKey()).send().join();
                    }
                }
            }
            camunda.close();
        }
    }

    @Test
    void realEngineRegistersParentAndChildBeforeInitialStatusCommit() throws Exception {
        startAndPrepare();
        workers.register(camunda, job(InitialCaseWorkers.REGISTER));
        assertStatus(CaseStatus.RECEIVED);
        assertEquals("CONFIRMED", database.queryForObject("SELECT state FROM pf_process_start_order", String.class));
        finishInitial();
        assertStatus(CaseStatus.UNDER_CLARIFICATION);
        assertEquals(1, count("pf_case_status_event"));
        assertEquals(1, count("pf_case_mail_order"));
        ActivatedJob next = job("propertyflow-test-unimplemented-triage");
        assertEquals("Triage", next.getElementId());
        assertTrue(next.getProcessInstanceKey() != store.leadingInstance(accepted.caseId()).orElseThrow());
    }

    @Test
    void lostStartResponseAndDuplicateStartKeepOneRoot() throws Exception {
        assertEquals(StartResult.STARTED, gateway.ensureStarted(order));
        ClientHttpException duplicate = assertThrows(ClientHttpException.class, () -> camunda.newCreateInstanceCommand()
                .processDefinitionKey(order.definitionKey())
                .businessId(order.id().toString())
                .variables(Map.of("caseId", accepted.caseId(), "startOrderRef", order.id().toString()))
                .send().join());
        assertEquals(409, duplicate.code());
        assertEquals(StartResult.EXISTING, gateway.ensureStarted(order));
        assertEquals("PENDING", database.queryForObject("SELECT state FROM pf_process_start_order", String.class));
        assertStatus(CaseStatus.RECEIVED);
        workers.prepareKey(camunda, job(InitialCaseWorkers.PREPARE_KEY));
        workers.register(camunda, job(InitialCaseWorkers.REGISTER));
        finishInitial();
        assertStatus(CaseStatus.UNDER_CLARIFICATION);
    }

    @Test
    void simultaneousStartersCreateExactlyOneActiveRoot() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        Callable<StartResult> start = () -> {
            barrier.await();
            return gateway.ensureStarted(order);
        };
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<StartResult>> results = executor.invokeAll(List.of(start, start));
            List<StartResult> actual = List.of(results.get(0).get(), results.get(1).get());
            assertTrue(actual.contains(StartResult.STARTED));
            assertTrue(actual.contains(StartResult.EXISTING));
        }
        await(() -> camunda.newProcessInstanceSearchRequest()
                .filter(filter -> filter.businessId(order.id().toString()).processDefinitionKey(order.definitionKey()))
                .send().join().items().size() == 1);
        workers.prepareKey(camunda, job(InitialCaseWorkers.PREPARE_KEY));
        workers.register(camunda, job(InitialCaseWorkers.REGISTER));
        finishInitial();
        assertEquals(1, count("pf_case_status_event"));
    }

    @Test
    void registrationCommitSurvivesLostJobCompletion() throws Exception {
        startAndPrepare();
        ActivatedJob registration = job(InitialCaseWorkers.REGISTER);
        JobClient disconnected = mock(JobClient.class);
        when(disconnected.newCompleteCommand(registration.getKey())).thenThrow(new ClientException("Injected lost completion"));
        assertThrows(ClientException.class, () -> workers.register(disconnected, registration));
        assertEquals("CONFIRMED", database.queryForObject("SELECT state FROM pf_process_start_order", String.class));
        assertStatus(CaseStatus.RECEIVED);
        ActivatedJob redelivered = job(InitialCaseWorkers.REGISTER);
        assertEquals(registration.getKey(), redelivered.getKey());
        workers.register(camunda, redelivered);
        finishInitial();
        assertStatus(CaseStatus.UNDER_CLARIFICATION);
        assertEquals(1, count("pf_case_process"));
    }

    @Test
    void statusCommitSurvivesLostJobCompletionWithoutSecondMail() throws Exception {
        startAndPrepare();
        workers.register(camunda, job(InitialCaseWorkers.REGISTER));
        workers.prepareInitial(camunda, job(InitialCaseWorkers.PREPARE_INITIAL));
        workers.registerAutomatic(camunda, job(InitialCaseWorkers.REGISTER_AUTOMATIC));
        ActivatedJob status = job(InitialCaseWorkers.CLARIFICATION);
        JobClient disconnected = mock(JobClient.class);
        when(disconnected.newCompleteCommand(status.getKey())).thenThrow(new ClientException("Injected lost completion"));
        assertThrows(ClientException.class, () -> workers.clarification(disconnected, status));
        assertStatus(CaseStatus.UNDER_CLARIFICATION);
        workers.clarification(camunda, job(InitialCaseWorkers.CLARIFICATION));
        assertEquals(1, count("pf_case_status_event"));
        assertEquals(1, count("pf_case_mail_order"));
    }

    @Test
    void foreignRootCannotClaimAnExistingStartOrder() throws Exception {
        camunda.newCreateInstanceCommand().processDefinitionKey(order.definitionKey()).businessId(UUID.randomUUID().toString())
                .variables(Map.of("caseId", accepted.caseId(), "startOrderRef", order.id().toString())).send().join();
        ActivatedJob invalid = job(InitialCaseWorkers.PREPARE_KEY);
        workers.prepareKey(camunda, invalid);
        assertEquals(0, count("pf_case_process"));
        assertStatus(CaseStatus.RECEIVED);
        await(() -> !camunda.newIncidentSearchRequest()
                .filter(filter -> filter.processInstanceKey(invalid.getProcessInstanceKey()))
                .send().join().items().isEmpty());
        camunda.newCancelInstanceCommand(invalid.getProcessInstanceKey()).send().join();
    }

    private void startAndPrepare() throws Exception {
        assertEquals(StartResult.STARTED, gateway.ensureStarted(order));
        workers.prepareKey(camunda, job(InitialCaseWorkers.PREPARE_KEY));
    }

    private void finishInitial() throws Exception {
        workers.prepareInitial(camunda, job(InitialCaseWorkers.PREPARE_INITIAL));
        workers.registerAutomatic(camunda, job(InitialCaseWorkers.REGISTER_AUTOMATIC));
        workers.clarification(camunda, job(InitialCaseWorkers.CLARIFICATION));
    }

    private ActivatedJob job(String type) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline) {
            List<ActivatedJob> jobs = camunda.newActivateJobsCommand().jobType(type).maxJobsToActivate(1)
                    .timeout(Duration.ofSeconds(3)).send().join().getJobs();
            if (!jobs.isEmpty()) {
                ActivatedJob job = jobs.getFirst();
                await(() -> {
                    try {
                        ProcessInstance instance = camunda.newProcessInstanceGetRequest(job.getProcessInstanceKey()).send().join();
                        if (instance.getParentElementInstanceKey() != null) {
                            camunda.newElementInstanceGetRequest(instance.getParentElementInstanceKey()).send().join();
                        }
                        return true;
                    } catch (ClientException exception) {
                        return false;
                    }
                });
                return job;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("No job for " + type);
    }

    private void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Engine state did not become visible");
    }

    private void assertStatus(CaseStatus expected) {
        assertEquals(expected, reading.readByToken(accepted.personalAccessToken()).state().status());
    }

    private int count(String table) {
        return database.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
