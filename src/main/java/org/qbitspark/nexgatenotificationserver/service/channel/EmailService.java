package org.qbitspark.nexgatenotificationserver.service.channel;

import jakarta.mail.internet.MimeMessage;
import jakarta.mail.util.ByteArrayDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.qbitspark.nexgatenotificationserver.dto.EmailMessage;
import org.qbitspark.nexgatenotificationserver.dto.EmailResult;
import org.qbitspark.nexgatenotificationserver.enums.NotificationType;
import org.qbitspark.nexgatenotificationserver.provider.email.EmailProvider;
import org.qbitspark.nexgatenotificationserver.service.template.TemplateService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final TemplateService templateService;
    private final EmailProvider emailProvider;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${email.app.name:NexGate}")
    private String appName;

    // ── Standard send (no attachment) ─────────────────────────────────────────
    public EmailResult send(NotificationType type, String to, Map<String, Object> data) {
        String templateName = getTemplateForType(type, data);
        String subject = getSubjectForType(type, data);

        log.info("📧 Preparing email: type={}, template={}, to={}", type, templateName, to);

        String htmlBody = templateService.renderEmailTemplate(templateName, data);

        EmailMessage emailMessage = EmailMessage.builder()
                .to(to).from(fromEmail).subject(subject)
                .htmlBody(htmlBody).build();

        EmailResult result = emailProvider.sendEmail(emailMessage);
        logResult(result, to);
        return result;
    }

    // ── Send with PDF attachment ───────────────────────────────────────────────
    public EmailResult sendWithAttachment(
            NotificationType type,
            String to,
            Map<String, Object> data,
            byte[] pdfBytes,
            String pdfFileName) {

        String templateName = getTemplateForType(type, data);
        String subject = getSubjectForType(type, data);

        log.info("📧 Preparing email+PDF: type={}, template={}, to={}, pdf={}",
                type, templateName, to, pdfFileName);

        String htmlBody = templateService.renderEmailTemplate(templateName, data);

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(fromEmail, appName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);

            // Attach PDF
            if (pdfBytes != null && pdfBytes.length > 0) {
                helper.addAttachment(pdfFileName,
                        new ByteArrayDataSource(pdfBytes, "application/pdf"));
                log.info("📎 Attached PDF: {} ({} bytes)", pdfFileName, pdfBytes.length);
            }

            mailSender.send(mimeMessage);

            log.info("✅ Email+PDF sent to: {}", to);
            return EmailResult.builder()
                    .success(true).provider("glueemail-smtp").build();

        } catch (Exception e) {
            log.error("❌ Email+PDF failed to {}: {}", to, e.getMessage(), e);
            return EmailResult.builder()
                    .success(false).errorMessage(e.getMessage()).provider("glueemail-smtp").build();
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  TEMPLATE MAPPING
    // ──────────────────────────────────────────────────────────────────────────

    private String getTemplateForType(NotificationType type, Map<String, Object> data) {
        return switch (type) {
            case ORDER_CONFIRMATION    -> "order_confirmation";
            case ORDER_SHIPPED         -> "order_shipped";
            case ORDER_DELIVERED       -> "order_delivered";
            case PAYMENT_RECEIVED      -> "payment_received";
            case PAYMENT_FAILURE       -> "payment_failure";
            case CART_ABANDONMENT      -> "cart_abandonment";
            case CHECKOUT_EXPIRY       -> "checkout_expiry";
            case WALLET_BALANCE_UPDATE -> "wallet_balance_update";
            case INSTALLMENT_DUE       -> "installment_due";
            case SHOP_NEW_ORDER        -> "shop_new_order";
            case SHOP_LOW_INVENTORY    -> "shop_low_inventory";
            case GROUP_PURCHASE_COMPLETE   -> "group_purchase_complete";
            case GROUP_PURCHASE_CREATED    -> "group_purchase_created";
            case GROUP_MEMBER_JOINED       -> "group_member_joined";
            case GROUP_SEATS_TRANSFERRED   -> "group_seats_transferred";
            case WELCOME_EMAIL         -> "welcome_email";
            case PROMOTIONAL_OFFER     -> "promotional_offer";

            // ── Event ─────────────────────────────────────────────────────────
            case EVENT_BOOKING_CONFIRMED      -> "event_booking_confirmed";
            case EVENT_ATTENDEE_TICKET_ISSUED -> resolveAttendeeTemplate(data);
            case EVENT_ORGANIZER_NEW_BOOKING  -> "event_organizer_new_booking";
        };
    }

    /**
     * Non-registered attendees get a template with an extra "Create Account" button.
     */
    private String resolveAttendeeTemplate(Map<String, Object> data) {
        Object isGuest = data.get("isGuest");
        if (Boolean.TRUE.toString().equalsIgnoreCase(String.valueOf(isGuest))) {
            return "event_attendee_ticket_guest";   // has "Create Account" CTA
        }
        return "event_attendee_ticket_issued";       // registered user template
    }

    private String getSubjectForType(NotificationType type, Map<String, Object> data) {
        return switch (type) {
            case ORDER_CONFIRMATION    -> "Order Confirmation - Your Order Has Been Received";
            case ORDER_SHIPPED         -> "Your Order Has Been Shipped";
            case ORDER_DELIVERED       -> "Your Order Has Been Delivered";
            case PAYMENT_RECEIVED      -> "Payment Received Successfully";
            case PAYMENT_FAILURE       -> "Payment Failed - Action Required";
            case CART_ABANDONMENT      -> "You Left Items in Your Cart";
            case CHECKOUT_EXPIRY       -> "Your Checkout Session Has Expired";
            case WALLET_BALANCE_UPDATE -> "Your Wallet Balance Has Been Updated";
            case INSTALLMENT_DUE       -> "Installment Payment Due Reminder";
            case SHOP_NEW_ORDER        -> "New Order Received in Your Shop";
            case SHOP_LOW_INVENTORY    -> "Low Inventory Alert";
            case GROUP_PURCHASE_COMPLETE   -> "Group Purchase Completed Successfully";
            case GROUP_PURCHASE_CREATED    -> "New Group Purchase Started for Your Product";
            case GROUP_MEMBER_JOINED       -> "New Member Joined Your Group Purchase";
            case GROUP_SEATS_TRANSFERRED   -> "Seats Transferred Successfully";
            case WELCOME_EMAIL         -> "Welcome to Nexgate!";
            case PROMOTIONAL_OFFER     -> "Special Offer Just for You";

            // ── Event ─────────────────────────────────────────────────────────
            case EVENT_BOOKING_CONFIRMED ->
                    "🎫 Booking Confirmed - " + resolveEventName(data);
            case EVENT_ATTENDEE_TICKET_ISSUED ->
                    "🎟️ Your Ticket is Ready - " + resolveEventName(data);
            case EVENT_ORGANIZER_NEW_BOOKING ->
                    "📋 New Booking Received - " + resolveEventName(data);
        };
    }

    private String resolveEventName(Map<String, Object> data) {
        Object eventObj = data.get("event");
        if (eventObj instanceof Map) {
            Object name = ((Map<?, ?>) eventObj).get("name");
            if (name != null) return name.toString();
        }
        return "Your Event";
    }

    private void logResult(EmailResult result, String to) {
        if (result.isSuccess()) {
            log.info("✅ Email sent to {}: messageId={}", to, result.getMessageId());
        } else {
            log.error("❌ Email failed to {}: {}", to, result.getErrorMessage());
        }
    }
}