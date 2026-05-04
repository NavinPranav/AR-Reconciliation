package com.ar.reconciliation.service;

import com.ar.reconciliation.domain.WorkflowEvent;
import com.ar.reconciliation.domain.WorkflowState;
import com.ar.reconciliation.repository.WorkflowEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventLogService {

    private final WorkflowEventRepository eventRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void log(UUID workflowId, String eventType,
                    WorkflowState fromState, WorkflowState toState,
                    JsonNode payload, String errorMessage) {
        WorkflowEvent event = WorkflowEvent.builder()
                .workflowId(workflowId)
                .eventType(eventType)
                .fromState(fromState)
                .toState(toState)
                .payload(payload)
                .errorMessage(errorMessage)
                .build();
        eventRepository.save(event);
    }
}
