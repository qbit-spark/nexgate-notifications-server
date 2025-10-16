package org.qbitspark.nexgatenotificationserver.provider.push.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.qbitspark.nexgatenotificationserver.api.client.ApiClientGate;
import org.qbitspark.nexgatenotificationserver.api.payload.ApiResponse;
import org.qbitspark.nexgatenotificationserver.dto.PushResult;
import org.qbitspark.nexgatenotificationserver.provider.push.PushProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "push.provider", havingValue = "gotify", matchIfMissing = true)
public class GotifyPushProvider implements PushProvider {

    private final ApiClientGate apiClient;

    @Value("${push.gotify.url}")
    private String gotifyUrl;

    @Value("${push.gotify.token}")
    private String gotifyToken;

    @Value("${push.gotify.enabled:false}")
    private boolean enabled;

    @Override
    public String getProviderName() {
        return "gotify";
    }

    @Override
    public boolean isAvailable() {
        return enabled && gotifyUrl != null && !gotifyUrl.isBlank()
                && gotifyToken != null && !gotifyToken.isBlank();
    }

    @Override
    public PushResult sendPush(String userId, String title, String message, int priority) {
        if (!isAvailable()) {
            log.warn("🔔 Gotify push provider not configured properly - using mock mode");
            return mockPush(userId, title, message, priority);
        }

        try {
            log.info("🔔 Sending push notification via Gotify to userId: {}", userId);

            // Build Gotify message payload
            Map<String, Object> payload = new HashMap<>();
            payload.put("title", title);
            payload.put("message", message);
            payload.put("priority", priority); // Gotify priorities: 0-10

            // Add extras (optional metadata)
            Map<String, Object> extras = new HashMap<>();
            extras.put("userId", userId);
            extras.put("timestamp", System.currentTimeMillis());
            payload.put("extras", extras);

            // Gotify API endpoint: POST /message?token=xxx
            String apiUrl = gotifyUrl + "/message?token=" + gotifyToken;

            log.info("🔔 Gotify request: title='{}', priority={}, userId={}",
                    title, priority, userId);

            // Make API call
            ApiResponse<Map> response = apiClient.post(apiUrl, payload, null, Map.class);

            if (response.isSuccess() && response.getData() != null) {
                Map<?, ?> data = response.getData();

                // Gotify returns: {"id": 123, "appid": 1, "message": "...", "title": "...", ...}
                String messageId = data.containsKey("id") ?
                        String.valueOf(data.get("id")) : "unknown";

                log.info("✅ Push notification sent successfully via Gotify. MessageId: {}", messageId);

                return PushResult.builder()
                        .success(true)
                        .messageId(messageId)
                        .provider("gotify")
                        .recipient(userId)
                        .statusCode(200)
                        .build();

            } else {
                log.error("❌ Gotify API call failed: {}", response.getErrorMessage());

                return PushResult.builder()
                        .success(false)
                        .errorMessage(response.getErrorMessage())
                        .provider("gotify")
                        .recipient(userId)
                        .statusCode(response.getStatusCode())
                        .build();
            }

        } catch (Exception e) {
            log.error("❌ Gotify push notification exception: {}", e.getMessage(), e);

            return PushResult.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .provider("gotify")
                    .recipient(userId)
                    .statusCode(500)
                    .build();
        }
    }

    private PushResult mockPush(String userId, String title, String message, int priority) {
        log.info("🔔 [MOCK] Push notification would be sent via Gotify:");
        log.info("   URL: {}", gotifyUrl);
        log.info("   UserId: {}", userId);
        log.info("   Title: {}", title);
        log.info("   Message: {}", message.substring(0, Math.min(100, message.length())) + "...");
        log.info("   Priority: {}", priority);
        log.info("   💡 To enable real push, set: push.gotify.enabled=true");

        return PushResult.builder()
                .success(true)
                .messageId("MOCK-PUSH-" + System.currentTimeMillis())
                .provider("gotify-mock")
                .recipient(userId)
                .statusCode(200)
                .build();
    }
}