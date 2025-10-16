package org.qbitspark.nexgatenotificationserver.service.orchestrator;

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
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationOrchestrator {

    private final NotificationRepository notificationRepository;
    private final EmailService emailService;

    public void process(NotificationEvent event) {
        log.info("Processing notification: type={}, recipients={}",
                event.getType(), event.getRecipients().size());

        String correlationId = UUID.randomUUID().toString();

        for (Recipient recipient : event.getRecipients()) {
            NotificationEntity notification = NotificationEntity.builder()
                    .correlationId(correlationId)
                    .userId(recipient.getUserId())
                    .recipientEmail(recipient.getEmail())
                    .recipientPhone(recipient.getPhone())
                    .recipientName(recipient.getName())
                    .type(event.getType())
                    .channels(event.getChannels())
                    .status(NotificationStatus.PROCESSING)
                    .templateData(event.getData())
                    .build();

            // Save first
            notification = notificationRepository.save(notification);

            // Send email if channel includes EMAIL
            if (event.getChannels().contains(NotificationChannel.EMAIL)) {
                EmailResult result = sendEmailForType(event.getType().name(), recipient.getEmail(), event.getData());

                if (result.isSuccess()) {
                    notification.setStatus(NotificationStatus.SENT);
                    notification.setSentAt(LocalDateTime.now());
                    log.info("Email sent successfully to: {}", recipient.getEmail());
                } else {
                    notification.setStatus(NotificationStatus.FAILED);
                    log.error("Email failed for: {}", recipient.getEmail());
                }

                notificationRepository.save(notification);
            }
        }
    }

    private EmailResult sendEmailForType(String type, String to, Map<String, Object> data) {
        return switch (type) {
            case "ORDER_CONFIRMATION" -> emailService.sendOrderConfirmation(to, data);
            case "PAYMENT_RECEIVED" -> emailService.sendPaymentReceived(to, data);
            default -> {
                log.warn("Unknown notification type: {}", type);
                yield EmailResult.builder().success(false).errorMessage("Unknown type").build();
            }
        };
    }
}