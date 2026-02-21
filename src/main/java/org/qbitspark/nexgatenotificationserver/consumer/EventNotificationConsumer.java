package org.qbitspark.nexgatenotificationserver.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.qbitspark.nexgatenotificationserver.dto.NotificationEvent;
import org.qbitspark.nexgatenotificationserver.enums.NotificationType;
import org.qbitspark.nexgatenotificationserver.service.event.EventNotificationHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventNotificationConsumer {

    private final EventNotificationHandler eventNotificationHandler;

    @RabbitListener(queues = "notification.event.queue")
    public void handleEventNotification(NotificationEvent event) {
        log.info("🎫 Received event notification: type={}, recipients={}",
                event.getType(), event.getRecipients().size());

        if (event.getType() == NotificationType.EVENT_BOOKING_CONFIRMED) {
            eventNotificationHandler.handleBuyerNotification(event);

        } else if (event.getType() == NotificationType.EVENT_ATTENDEE_TICKET_ISSUED) {
            eventNotificationHandler.handleAttendeeNotification(event);

        } else {
            log.warn("⚠️ Unknown event notification type: {}", event.getType());
        }
    }
}