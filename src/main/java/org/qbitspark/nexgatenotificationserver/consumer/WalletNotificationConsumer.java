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
public class WalletNotificationConsumer {

    private final NotificationOrchestrator orchestrator;

    @RabbitListener(queues = "notification.wallet.queue")
    public void handleWalletNotification(NotificationEvent event) {
        log.info("💰 Received wallet notification: type={}, recipients={}",
                event.getType(), event.getRecipients().size());

        orchestrator.process(event);
    }
}