package org.qbitspark.nexgatenotificationserver.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.qbitspark.nexgatenotificationserver.api.client.ApiClientGate;
import org.qbitspark.nexgatenotificationserver.api.payload.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ServiceAuthClient {

    private final ApiClientGate apiClientGate;
    private final ObjectMapper objectMapper;

    @Value("${parent.server.url}")
    private String parentServerUrl;

    @Value("${parent.server.service.api-key}")
    private String apiKey;

    @Value("${parent.server.service.secret-key}")
    private String secretKey;

    public <T, R> ApiResponse<R> postWithAuth(String endpoint, T body, Class<R> responseType) {
        try {
            // Build full URL
            String url = parentServerUrl + endpoint;

            // Generate timestamp
            String timestamp = Instant.now().toString();

            // Convert body to JSON string
            String bodyJson = objectMapper.writeValueAsString(body);

            // Generate HMAC signature
            String signature = HmacUtils.generateSignature(timestamp, bodyJson, endpoint, secretKey);

            // Build headers with authentication
            Map<String, String> headers = new HashMap<>();
            headers.put("X-Service-Key", apiKey);
            headers.put("X-Timestamp", timestamp);
            headers.put("X-Signature", signature);
            headers.put("Content-Type", "application/json");

            log.info("Sending authenticated request to: {}", url);
            log.debug("Headers: X-Service-Key={}, X-Timestamp={}", apiKey, timestamp);

            // Make the API call
            return apiClientGate.post(url, body, headers, responseType);

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize request body", e);
            return ApiResponse.<R>builder()
                    .success(false)
                    .errorMessage("Failed to serialize request: " + e.getMessage())
                    .statusCode(500)
                    .build();
        } catch (Exception e) {
            log.error("Service auth client error", e);
            return ApiResponse.<R>builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .statusCode(500)
                    .build();
        }
    }
}