package org.qbitspark.nexgatenotificationserver.provider.email.impl;


import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.qbitspark.nexgatenotificationserver.dto.EmailMessage;
import org.qbitspark.nexgatenotificationserver.dto.EmailResult;
import org.qbitspark.nexgatenotificationserver.provider.email.EmailProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;



@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "email.provider", havingValue = "glueemail", matchIfMissing = true)
public class GlueEmailProvider implements EmailProvider {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${email.app.name:NexGate}")
    private String appName;

    @Override
    public String getProviderName() {
        return "glueemail";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public EmailResult sendEmail(EmailMessage message) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(fromEmail, appName);
            helper.setTo(message.getTo());
            helper.setSubject(message.getSubject());
            helper.setText(message.getHtmlBody(), true); // true = HTML

            mailSender.send(mimeMessage);

            log.info("Email sent successfully via SMTP to: {}", message.getTo());

            return EmailResult.builder()
                    .success(true)
                    .messageId(mimeMessage.getMessageID())
                    .provider("glueemail-smtp")
                    .build();

        } catch (Exception e) {
            log.error("GlueEmail SMTP failed: {}", e.getMessage(), e);
            return EmailResult.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .provider("glueemail-smtp")
                    .build();
        }
    }
}