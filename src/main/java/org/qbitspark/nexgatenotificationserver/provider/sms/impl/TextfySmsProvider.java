package org.qbitspark.nexgatenotificationserver.provider.sms.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.qbitspark.nexgatenotificationserver.api.client.ApiClientGate;
import org.qbitspark.nexgatenotificationserver.api.payload.ApiResponse;
import org.qbitspark.nexgatenotificationserver.dto.SmsResult;
import org.qbitspark.nexgatenotificationserver.provider.sms.SmsProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "sms.provider", havingValue = "textfy", matchIfMissing = true)
public class TextfySmsProvider implements SmsProvider {

    private final ApiClientGate apiClient;

    @Value("${sms.textfy.api-url}")
    private String apiUrl;

    @Value("${sms.textfy.api-key}")
    private String apiKey;

    @Value("${sms.textfy.sender-name}")
    private String defaultSenderName;

    @Value("${sms.textfy.enabled:false}")
    private boolean enabled;

    @Value("${sms.textfy.batch-size:100}")
    private int batchSize;

    @Override
    public String getProviderName() {
        return "textfy";
    }

    @Override
    public boolean isAvailable() {
        return enabled && apiKey != null && !apiKey.isBlank()
                && apiUrl != null && !apiUrl.isBlank();
    }

    @Override
    public SmsResult sendSms(String to, String message, String senderId) {
        // Single SMS - use batch method with 1 recipient
        List<SmsResult> results = sendSmsBatch(List.of(to), message, senderId);
        return results.isEmpty() ? createFailedResult(to, "No result returned") : results.get(0);
    }

    @Override
    public List<SmsResult> sendSmsBatch(List<String> recipients, String message, String senderId) {
        // Same message to multiple recipients
        Map<String, String> recipientMessages = recipients.stream()
                .collect(Collectors.toMap(phone -> phone, phone -> message));

        return sendSmsBatchCustom(recipientMessages, senderId);
    }

    @Override
    public List<SmsResult> sendSmsBatchCustom(Map<String, String> recipientMessages, String senderId) {
        if (!isAvailable()) {
            log.warn("📱 Textify SMS provider not configured properly - using mock mode");
            return mockSmsBatch(recipientMessages, senderId);
        }

        try {
            log.info("📱 Sending batch SMS via Textify to {} recipients", recipientMessages.size());

            // Build messages array for Textify API
            List<Map<String, String>> messages = new ArrayList<>();
            for (Map.Entry<String, String> entry : recipientMessages.entrySet()) {
                String formattedPhone = formatPhone(entry.getKey());
                Map<String, String> smsMessage = new HashMap<>();
                smsMessage.put("receiver", formattedPhone);
                smsMessage.put("content", entry.getValue());
                messages.add(smsMessage);
            }

            // Prepare request body
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("sender_name", senderId != null ? senderId : defaultSenderName);
            requestBody.put("is_scheduled", false);
            requestBody.put("scheduled_date", null);
            requestBody.put("messages", messages);

            // Prepare headers
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", apiKey);
            headers.put("Content-Type", "application/json");

            log.info("📱 Textify batch request: recipients={}, sender={}",
                    recipientMessages.size(), senderId);

            // Make API call
            ApiResponse<Map> response = apiClient.post(apiUrl, requestBody, headers, Map.class);

            List<SmsResult> results = new ArrayList<>();

            if (response.isSuccess() && response.getData() != null) {
                Map<?, ?> data = response.getData();
                boolean success = data.containsKey("success") && (boolean) data.get("success");
                String responseMessage = data.containsKey("message") ?
                        String.valueOf(data.get("message")) : "No message";

                // Textify returns single success/failure for entire batch
                // So we create individual results for each recipient
                for (String recipient : recipientMessages.keySet()) {
                    if (success) {
                        results.add(createSuccessResult(recipient, responseMessage));
                    } else {
                        results.add(createFailedResult(recipient, responseMessage));
                    }
                }

                if (success) {
                    log.info("✅ Batch SMS sent successfully via Textify. Response: {}", responseMessage);
                } else {
                    log.error("❌ Textify batch SMS failed: {}", responseMessage);
                }
            } else {
                log.error("❌ Textify API call failed: {}", response.getErrorMessage());

                // Create failed results for all recipients
                for (String recipient : recipientMessages.keySet()) {
                    results.add(createFailedResult(recipient, response.getErrorMessage()));
                }
            }

            return results;

        } catch (Exception e) {
            log.error("❌ Textify batch SMS exception: {}", e.getMessage(), e);

            // Create failed results for all recipients
            return recipientMessages.keySet().stream()
                    .map(recipient -> createFailedResult(recipient, e.getMessage()))
                    .collect(Collectors.toList());
        }
    }

    private String formatPhone(String phone) {
        // Remove + if present (Textify uses E.164 without +)
        return phone.startsWith("+") ? phone.substring(1) : phone;
    }

    private SmsResult createSuccessResult(String recipient, String message) {
        return SmsResult.builder()
                .success(true)
                .messageId(UUID.randomUUID().toString())
                .provider("textfy")
                .recipient(recipient)
                .statusCode(200)
                .build();
    }

    private SmsResult createFailedResult(String recipient, String errorMessage) {
        return SmsResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .provider("textfy")
                .recipient(recipient)
                .statusCode(400)
                .build();
    }

    private List<SmsResult> mockSmsBatch(Map<String, String> recipientMessages, String senderId) {
        List<SmsResult> results = new ArrayList<>();

        log.info("📱 [MOCK] Batch SMS would be sent via Textify:");
        log.info("   API URL: {}", apiUrl);
        log.info("   Recipients: {}", recipientMessages.size());
        log.info("   From: {}", senderId != null ? senderId : defaultSenderName);
        log.info("   💡 To enable real SMS, set: sms.textfy.enabled=true");

        for (Map.Entry<String, String> entry : recipientMessages.entrySet()) {
            String messageId = "MOCK-" + UUID.randomUUID().toString().substring(0, 8);

            log.info("   📱 To: {}, Message: {} (length: {} chars), MessageId: {}",
                    entry.getKey(),
                    entry.getValue().substring(0, Math.min(50, entry.getValue().length())) + "...",
                    entry.getValue().length(),
                    messageId);

            results.add(SmsResult.builder()
                    .success(true)
                    .messageId(messageId)
                    .provider("textfy-mock")
                    .recipient(entry.getKey())
                    .statusCode(200)
                    .build());
        }

        return results;
    }
}