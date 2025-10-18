
package org.qbitspark.nexgatenotificationserver.consumer;import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.qbitspark.nexgatenotificationserver.dto.NotificationEvent;
import org.qbitspark.nexgatenotificationserver.service.orchestrator.NotificationOrchestrator;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;@Slf4j
@Component
@RequiredArgsConstructor
public class GroupPurchaseNotificationConsumer {
    private final NotificationOrchestrator orchestrator;

    @RabbitListener(queues = "notification.group_purchase.queue")
    public void handleGroupPurchaseNotification(NotificationEvent event) {
        log.info("🎉 Received group purchase notification: type={}, recipients={}",
                event.getType(), event.getRecipients().size());

        orchestrator.process(event);
    }
}