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
public class PaymentNotificationConsumer {

    private final NotificationOrchestrator orchestrator;

    @RabbitListener(queues = "notification.payment.queue")
    public void handlePaymentNotification(NotificationEvent event) {
        log.info("💳 Received payment notification: type={}, recipients={}",
                event.getType(), event.getRecipients().size());

        orchestrator.process(event);
    }
}