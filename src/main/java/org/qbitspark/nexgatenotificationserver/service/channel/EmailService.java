package org.qbitspark.nexgatenotificationserver.service.channel;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.qbitspark.nexgatenotificationserver.dto.EmailMessage;
import org.qbitspark.nexgatenotificationserver.dto.EmailResult;
import org.qbitspark.nexgatenotificationserver.provider.email.EmailProvider;
import org.qbitspark.nexgatenotificationserver.service.template.TemplateService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final TemplateService templateService;
    private final EmailProvider emailProvider;

    @Value("${email.from:noreply@nexgate.com}")
    private String fromEmail;

    public EmailResult sendOrderConfirmation(String to, Map<String, Object> data) {
        return sendEmail(to, "Order Confirmation", "order_confirmation", data);
    }

    public EmailResult sendPaymentReceived(String to, Map<String, Object> data) {
        return sendEmail(to, "Payment Received", "payment_received", data);
    }

    private EmailResult sendEmail(String to, String subject, String templateName, Map<String, Object> data) {
        try {
            String html = templateService.renderEmailTemplate(templateName, data);

            EmailMessage message = EmailMessage.builder()
                    .to(to)
                    .from(fromEmail)
                    .subject(subject)
                    .htmlBody(html)
                    .build();

            return emailProvider.sendEmail(message);

        } catch (Exception e) {
            log.error("Failed to send email: {}", e.getMessage());
            return EmailResult.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }
}