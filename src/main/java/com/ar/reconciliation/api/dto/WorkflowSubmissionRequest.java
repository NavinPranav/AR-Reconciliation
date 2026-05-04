package com.ar.reconciliation.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

public record WorkflowSubmissionRequest(
        @NotNull JsonNode payload
) {
}
