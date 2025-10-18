package org.qbitspark.nexgatenotificationserver.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.qbitspark.nexgatenotificationserver.dto.NotificationEvent;
import org.qbitspark.nexgatenotificationserver.service.orchestrator.NotificationOrchestrator;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShopNotificationConsumer {

    private final NotificationOrchestrator orchestrator;

    @RabbitListener(queues = "notification.shop.queue")
    public void handleShopNotification(NotificationEvent event) {
        log.info("🏪 Received shop notification: type={}, recipients={}",
                event.getType(), event.getRecipients().size());

        orchestrator.process(event);
    }
}