package com.ar.reconciliation.api;

import com.ar.reconciliation.service.WorkflowService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/workflows/bulk")
@RequiredArgsConstructor
@Slf4j
public class BulkUploadController {

    private final WorkflowService workflowService;
    private final ObjectMapper objectMapper;

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<Map<String, Object>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "idempotencyPrefix", defaultValue = "bulk") String prefix
    ) throws IOException, CsvException {

        int submitted = 0;
        int failed = 0;

        try (CSVReader reader = new CSVReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            List<String[]> rows = reader.readAll();
            if (rows.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Empty CSV"));
            }

            String[] headers = rows.get(0);
            for (int i = 1; i < rows.size(); i++) {
                String[] row = rows.get(i);
                try {
                    ObjectNode payload = objectMapper.createObjectNode();
                    for (int j = 0; j < headers.length && j < row.length; j++) {
                        payload.put(toCamelCase(headers[j]), row[j]);
                    }
                    String idemKey = prefix + ":" + i + ":"
                            + payload.path("invoiceId").asText("")
                            + ":" + payload.path("paymentId").asText("");
                    workflowService.submit(idemKey, payload);
                    submitted++;
                } catch (Exception ex) {
                    log.error("Failed to submit row {}", i, ex);
                    failed++;
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("submitted", submitted);
        result.put("failed", failed);
        return ResponseEntity.accepted().body(result);
    }

    private static String toCamelCase(String header) {
        String cleaned = header.trim().toLowerCase().replaceAll("[^a-z0-9]+", " ");
        String[] parts = cleaned.split(" ");
        StringBuilder sb = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            if (parts[i].isEmpty()) continue;
            sb.append(Character.toUpperCase(parts[i].charAt(0)));
            sb.append(parts[i].substring(1));
        }
        return sb.toString();
    }
}
