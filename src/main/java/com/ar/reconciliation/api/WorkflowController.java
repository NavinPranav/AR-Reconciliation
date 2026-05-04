package com.ar.reconciliation.api;

import com.ar.reconciliation.api.dto.WorkflowResponse;
import com.ar.reconciliation.api.dto.WorkflowSubmissionRequest;
import com.ar.reconciliation.domain.WorkflowEvent;
import com.ar.reconciliation.repository.WorkflowEventRepository;
import com.ar.reconciliation.service.WorkflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/workflows")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;
    private final WorkflowEventRepository eventRepository;

    @PostMapping
    public ResponseEntity<WorkflowResponse> submit(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody WorkflowSubmissionRequest request) {
        var run = workflowService.submit(idempotencyKey, request.payload());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(WorkflowResponse.from(run));
    }

    @GetMapping("/{id}")
    public WorkflowResponse get(@PathVariable UUID id) {
        return WorkflowResponse.from(workflowService.get(id));
    }

    @GetMapping("/{id}/events")
    public List<WorkflowEvent> events(@PathVariable UUID id) {
        return eventRepository.findByWorkflowIdOrderByOccurredAtAsc(id);
    }
}
