package com.ar.reconciliation;

import com.ar.reconciliation.domain.WorkflowRun;
import com.ar.reconciliation.domain.WorkflowState;
import com.ar.reconciliation.repository.WorkflowRunRepository;
import com.ar.reconciliation.service.WorkflowService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Testcontainers
class WorkflowIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ar_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.rabbitmq.host", rabbit::getHost);
        r.add("spring.rabbitmq.port", rabbit::getAmqpPort);
        // Disable failure injection for happy-path tests; specific tests can override.
        r.add("ar.failure-injection.enabled", () -> "false");
    }

    @Autowired WorkflowService workflowService;
    @Autowired WorkflowRunRepository runRepository;
    @Autowired ObjectMapper objectMapper;

    @Test
    void happyPath_shouldReachCompleted() {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("invoiceId", "INV-001");
        payload.put("paymentId", "INV-001-P");
        payload.put("amount", 1500.0);
        payload.put("currency", "USD");

        WorkflowRun run = workflowService.submit("test-key-1", payload);
        UUID id = run.getId();

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            WorkflowRun current = runRepository.findById(id).orElseThrow();
            assertThat(current.getCurrentState()).isEqualTo(WorkflowState.COMPLETED);
            assertThat(current.getStageOutputs().has("INGESTION")).isTrue();
            assertThat(current.getStageOutputs().has("MATCHING")).isTrue();
            assertThat(current.getStageOutputs().has("VALIDATION")).isTrue();
            assertThat(current.getStageOutputs().has("ROUTING")).isTrue();
        });
    }

    @Test
    void duplicateSubmission_shouldReturnSameWorkflow() {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("invoiceId", "INV-DUP");
        payload.put("paymentId", "INV-DUP-P");
        payload.put("amount", 100.0);

        WorkflowRun first = workflowService.submit("dup-key", payload);
        WorkflowRun second = workflowService.submit("dup-key", payload);

        assertThat(second.getId()).isEqualTo(first.getId());
    }
}
