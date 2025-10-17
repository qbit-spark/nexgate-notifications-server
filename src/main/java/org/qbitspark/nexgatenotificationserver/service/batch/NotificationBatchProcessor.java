package org.qbitspark.nexgatenotificationserver.service.batch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.qbitspark.nexgatenotificationserver.dto.EmailResult;
import org.qbitspark.nexgatenotificationserver.dto.NotificationEvent;
import org.qbitspark.nexgatenotificationserver.dto.Recipient;
import org.qbitspark.nexgatenotificationserver.entity.NotificationEntity;
import org.qbitspark.nexgatenotificationserver.enums.NotificationChannel;
import org.qbitspark.nexgatenotificationserver.enums.NotificationStatus;
import org.qbitspark.nexgatenotificationserver.repository.NotificationRepository;
import org.qbitspark.nexgatenotificationserver.service.channel.EmailService;
import org.qbitspark.nexgatenotificationserver.service.channel.InAppService;
import org.qbitspark.nexgatenotificationserver.service.channel.SmsService;
import org.qbitspark.nexgatenotificationserver.service.channel.PushService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationBatchProcessor {

    private final NotificationRepository notificationRepository;
    private final EmailService emailService;
    private final SmsService smsService;
    private final PushService pushService;
    private final InAppService inAppService;

    @Async("notificationExecutor")
    public CompletableFuture<Void> processBatch(
            String correlationId,
            int batchNumber,
            List<Recipient> recipients,
            NotificationEvent event
    ) {
        log.info("🔄 Processing batch #{} (correlationId: {}, recipients: {}, channels: {})",
                batchNumber, correlationId, recipients.size(), event.getChannels());

        long startTime = System.currentTimeMillis();

        for (Recipient recipient : recipients) {
            processRecipient(correlationId, recipient, event);
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("✅ Batch #{} completed in {}ms", batchNumber, duration);

        return CompletableFuture.completedFuture(null);
    }

    private void processRecipient(String correlationId, Recipient recipient, NotificationEvent event) {
        NotificationEntity notification = NotificationEntity.builder()
                .correlationId(correlationId)
                .userId(recipient.getUserId())
                .recipientEmail(recipient.getEmail())
                .recipientPhone(recipient.getPhone())
                .recipientName(recipient.getName())
                .type(event.getType())
                .channels(event.getChannels())
                .status(NotificationStatus.PROCESSING)
                .templateData((Map<String, Object>) event.getData())
                .build();

        notification = notificationRepository.save(notification);
        log.info("💾 Saved notification {} for user {} (channels: {})",
                notification.getId(), recipient.getUserId(), event.getChannels());

        // Track results per channel
        Map<NotificationChannel, Boolean> channelResults = new HashMap<>();

        // Send via all requested channels
        for (NotificationChannel channel : event.getChannels()) {
            boolean success = sendViaChannel(channel, recipient, event);
            channelResults.put(channel, success);
        }

        // Determine final status
        NotificationStatus finalStatus = determineFinalStatus(channelResults);
        notification.setStatus(finalStatus);

        if (finalStatus == NotificationStatus.SENT || finalStatus == NotificationStatus.PARTIAL) {
            notification.setSentAt(LocalDateTime.now());
        }

        notificationRepository.save(notification);

        // Log summary
        logChannelResults(recipient, channelResults, finalStatus);
    }

    private boolean sendViaChannel(NotificationChannel channel, Recipient recipient, NotificationEvent event) {
        return switch (channel) {
            case EMAIL -> sendEmail(recipient, event);
            case SMS -> sendSms(recipient, event);
            case PUSH -> sendPush(recipient, event);
            case IN_APP -> sendInApp(recipient, event);
            case WEBHOOK -> sendWebhook(recipient, event);
            case CHAT_APP -> sendChatApp(recipient, event);
        };
    }

    private boolean sendEmail(Recipient recipient, NotificationEvent event) {
        if (recipient.getEmail() == null || recipient.getEmail().isBlank()) {
            log.warn("📧 ⚠️ No email address for user {}, skipping EMAIL channel", recipient.getUserId());
            return false;
        }

        EmailResult result = emailService.send(
                event.getType(),
                recipient.getEmail(),
                (Map<String, Object>) event.getData()
        );
        return result.isSuccess();
    }

    private boolean sendSms(Recipient recipient, NotificationEvent event) {
        if (recipient.getPhone() == null || recipient.getPhone().isBlank()) {
            log.warn("📱 ⚠️ No phone number for user {}, skipping SMS channel", recipient.getUserId());
            return false;
        }

        return smsService.send(
                event.getType(),
                recipient.getPhone(),
                (Map<String, Object>) event.getData()
        );
    }

    private boolean sendPush(Recipient recipient, NotificationEvent event) {
        if (recipient.getUserId() == null || recipient.getUserId().isBlank()) {
            log.warn("🔔 ⚠️ No userId for recipient, skipping PUSH channel");
            return false;
        }

        return pushService.send(
                event.getType(),
                recipient.getUserId(),
                (Map<String, Object>) event.getData()
        );
    }


    private boolean sendInApp(Recipient recipient, NotificationEvent event) {
        if (recipient.getUserId() == null || recipient.getUserId().isBlank()) {
            log.warn("📬 ⚠️ No userId for recipient, skipping IN_APP channel");
            return false;
        }

        return inAppService.send(
                event.getType(),
                recipient.getUserId(),
                (Map<String, Object>) event.getData()
        );
    }


    private boolean sendWebhook(Recipient recipient, NotificationEvent event) {
        log.info("🪝 [MOCK] Webhook would be called for userId: {} (type: {})",
                recipient.getUserId(), event.getType());
        // TODO: Implement webhook service
        return true;
    }

    private boolean sendChatApp(Recipient recipient, NotificationEvent event) {
        log.info("💬 [MOCK] Chat app message would be sent to userId: {} (type: {})",
                recipient.getUserId(), event.getType());
        // TODO: Implement chat app service
        return true;
    }

    private NotificationStatus determineFinalStatus(Map<NotificationChannel, Boolean> channelResults) {
        long successCount = channelResults.values().stream().filter(success -> success).count();
        long totalCount = channelResults.size();

        if (successCount == 0) {
            return NotificationStatus.FAILED;
        } else if (successCount == totalCount) {
            return NotificationStatus.SENT;
        } else {
            return NotificationStatus.PARTIAL;
        }
    }

    private void logChannelResults(Recipient recipient, Map<NotificationChannel, Boolean> channelResults, NotificationStatus finalStatus) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("📊 Channel Results for %s (%s): ", recipient.getName(), recipient.getUserId()));

        for (Map.Entry<NotificationChannel, Boolean> entry : channelResults.entrySet()) {
            String icon = entry.getValue() ? "✅" : "❌";
            sb.append(String.format("%s %s ", icon, entry.getKey()));
        }

        sb.append(String.format("| Final Status: %s", finalStatus));
        log.info(sb.toString());
    }
}