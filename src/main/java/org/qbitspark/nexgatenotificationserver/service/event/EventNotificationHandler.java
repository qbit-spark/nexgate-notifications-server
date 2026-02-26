package org.qbitspark.nexgatenotificationserver.service.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.qbitspark.nexgatenotificationserver.dto.NotificationEvent;
import org.qbitspark.nexgatenotificationserver.dto.Recipient;
import org.qbitspark.nexgatenotificationserver.enums.NotificationChannel;
import org.qbitspark.nexgatenotificationserver.enums.NotificationType;
import org.qbitspark.nexgatenotificationserver.service.channel.EmailService;
import org.qbitspark.nexgatenotificationserver.service.channel.InAppService;
import org.qbitspark.nexgatenotificationserver.service.channel.SmsService;
import org.qbitspark.nexgatenotificationserver.service.pdf.TicketPdfGenerator;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import jakarta.mail.util.ByteArrayDataSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Handles event-domain notifications.
 *
 * BUYER  (EVENT_BOOKING_CONFIRMED)         — one email, one PDF attachment PER TICKET
 * ATTENDEE (EVENT_ATTENDEE_TICKET_ISSUED)  — one email per attendee, one PDF for their ticket
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventNotificationHandler {

    private final EmailService emailService;
    private final SmsService smsService;
    private final InAppService inAppService;
    private final TicketPdfGenerator pdfGenerator;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${email.app.name:NexGate}")
    private String appName;

    // ──────────────────────────────────────────────────────────────────────────
    //  BUYER FLOW  — all tickets as separate PDF attachments in one email
    // ──────────────────────────────────────────────────────────────────────────

    @Async("notificationExecutor")
    public void handleBuyerNotification(NotificationEvent event) {
        Map<String, Object> data = event.getData();
        List<Recipient> recipients = event.getRecipients();

        if (recipients == null || recipients.isEmpty()) {
            log.warn("⚠️ EVENT_BOOKING_CONFIRMED received with no recipients");
            return;
        }

        Recipient buyer = recipients.get(0);
        log.info("🛒 Processing buyer notification for: {}", buyer.getEmail());

        try {
            List<Map<String, Object>> tickets = extractTickets(data);
            Map<String, String> qrCodes = extractQrCodes(data);

            if (tickets.isEmpty()) {
                log.warn("⚠️ No tickets found in booking data — sending email without attachment");
                if (shouldSendChannel(event, NotificationChannel.EMAIL)) {
                    emailService.send(NotificationType.EVENT_BOOKING_CONFIRMED, buyer.getEmail(), data);
                }
            } else {
                // ── Generate one PDF per ticket ───────────────────────────────
                List<PdfAttachment> attachments = new ArrayList<>();
                for (Map<String, Object> ticket : tickets) {
                    String ticketId = str(ticket, "ticketId");
                    String jwt = qrCodes.get(ticketId);
                    if (jwt == null || jwt.isBlank()) {
                        if (!qrCodes.isEmpty()) {
                            jwt = qrCodes.values().iterator().next();
                            log.warn("⚠️ JWT not found for ticketId={}, using fallback", ticketId);
                        } else {
                            log.error("❌ No JWT token available for ticketId={} — skipping PDF", ticketId);
                            continue;
                        }
                    }
                    try {
                        byte[] pdfBytes = pdfGenerator.generateSingleTicketPdf(data, ticket, jwt);
                        String series = str(ticket, "series");
                        String fileName = "ticket-" + (series.isBlank() ? ticketId : series) + ".pdf";
                        attachments.add(new PdfAttachment(fileName, pdfBytes));
                        log.info("📄 Generated PDF: {}", fileName);
                    } catch (Exception e) {
                        log.error("❌ Failed to generate PDF for ticketId={}: {}", ticketId, e.getMessage(), e);
                    }
                }

                // ── Email with all ticket PDFs attached ───────────────────────
                if (shouldSendChannel(event, NotificationChannel.EMAIL)) {
                    sendEmailWithMultipleAttachments(
                            NotificationType.EVENT_BOOKING_CONFIRMED,
                            buyer.getEmail(),
                            data,
                            attachments
                    );
                }
            }

            // ── SMS ───────────────────────────────────────────────────────────
            if (shouldSendChannel(event, NotificationChannel.SMS) && buyer.getPhone() != null) {
                smsService.send(NotificationType.EVENT_BOOKING_CONFIRMED, buyer.getPhone(), data);
            }

            // ── In-App ────────────────────────────────────────────────────────
            if (shouldSendChannel(event, NotificationChannel.IN_APP) && buyer.getUserId() != null) {
                inAppService.send(NotificationType.EVENT_BOOKING_CONFIRMED, buyer.getUserId(), data);
            }

            log.info("✅ Buyer notification complete for: {}", buyer.getEmail());

        } catch (Exception e) {
            log.error("❌ Failed to send buyer notification to {}: {}", buyer.getEmail(), e.getMessage(), e);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  ATTENDEE FLOW  — one email, one PDF for their specific ticket
    // ──────────────────────────────────────────────────────────────────────────

    @Async("notificationExecutor")
    public void handleAttendeeNotification(NotificationEvent event) {
        Map<String, Object> data = event.getData();
        List<Recipient> recipients = event.getRecipients();

        if (recipients == null || recipients.isEmpty()) {
            log.warn("⚠️ EVENT_ATTENDEE_TICKET_ISSUED received with no recipients");
            return;
        }

        Map<String, String> qrCodes = extractQrCodes(data);
        List<Map<String, Object>> tickets = extractTickets(data);

        for (Recipient attendee : recipients) {
            try {
                processAttendee(event, data, attendee, tickets, qrCodes);
            } catch (Exception e) {
                log.error("❌ Failed to send attendee notification to {}: {}",
                        attendee.getEmail(), e.getMessage(), e);
            }
        }
    }

    private void processAttendee(
            NotificationEvent event,
            Map<String, Object> data,
            Recipient attendee,
            List<Map<String, Object>> tickets,
            Map<String, String> qrCodes) throws Exception {

        boolean isRegistered = Boolean.TRUE.toString()
                .equalsIgnoreCase(String.valueOf(data.getOrDefault("attendeeIsRegistered", "true")));

        log.info("🎫 Processing attendee: email={}, registered={}", attendee.getEmail(), isRegistered);

        // Find this attendee's ticket
        Map<String, Object> myTicket = extractCurrentTicket(data);
        if (myTicket == null) myTicket = findTicketForAttendee(tickets, attendee.getEmail());
        if (myTicket == null) {
            log.warn("⚠️ No ticket found for attendee={}, using first ticket", attendee.getEmail());
            myTicket = tickets.isEmpty() ? Map.of() : tickets.get(0);
        }

        String ticketId = str(myTicket, "ticketId");
        String jwtToken = qrCodes.get(ticketId);
        if ((jwtToken == null || jwtToken.isBlank()) && !qrCodes.isEmpty()) {
            jwtToken = qrCodes.values().iterator().next();
            log.warn("⚠️ JWT not found for ticketId={}, using fallback", ticketId);
        }
        if (jwtToken == null || jwtToken.isBlank()) {
            throw new IllegalArgumentException("No JWT token for ticketId=" + ticketId);
        }

        // Generate single ticket PDF
        byte[] ticketPdf = pdfGenerator.generateSingleTicketPdf(data, myTicket, jwtToken);
        String series    = str(myTicket, "series");
        String fileName  = "ticket-" + (series.isBlank() ? ticketId : series) + ".pdf";

        Map<String, Object> attendeeData = buildAttendeeData(data, myTicket, isRegistered);

        // ── Email with single PDF ─────────────────────────────────────────────
        if (shouldSendChannel(event, NotificationChannel.EMAIL)) {
            emailService.sendWithAttachment(
                    NotificationType.EVENT_ATTENDEE_TICKET_ISSUED,
                    attendee.getEmail(),
                    attendeeData,
                    ticketPdf,
                    fileName
            );
        }

        // ── SMS ───────────────────────────────────────────────────────────────
        if (shouldSendChannel(event, NotificationChannel.SMS) && attendee.getPhone() != null) {
            smsService.send(NotificationType.EVENT_ATTENDEE_TICKET_ISSUED, attendee.getPhone(), attendeeData);
        }

        // ── In-App — registered users only ───────────────────────────────────
        if (isRegistered
                && shouldSendChannel(event, NotificationChannel.IN_APP)
                && attendee.getUserId() != null) {
            inAppService.send(NotificationType.EVENT_ATTENDEE_TICKET_ISSUED, attendee.getUserId(), attendeeData);
        }

        log.info("✅ Attendee notification complete for: {}", attendee.getEmail());
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  MULTI-ATTACHMENT EMAIL
    //  Sends one email with multiple PDF attachments (buyer flow)
    // ──────────────────────────────────────────────────────────────────────────

    private void sendEmailWithMultipleAttachments(
            NotificationType type,
            String to,
            Map<String, Object> data,
            List<PdfAttachment> attachments) {

        try {
            // Use EmailService to build the HTML body via the template system
            // We re-use the existing helper that prepares subject + rendered HTML
            String subject  = resolveSubject(type, data);
            String htmlBody = emailService.renderTemplate(type, data);

            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(fromEmail, appName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);

            for (PdfAttachment att : attachments) {
                helper.addAttachment(att.fileName(),
                        new ByteArrayDataSource(att.pdfBytes(), "application/pdf"));
                log.info("📎 Attached: {} ({} bytes)", att.fileName(), att.pdfBytes().length);
            }

            mailSender.send(mimeMessage);
            log.info("✅ Email+{} PDFs sent to: {}", attachments.size(), to);

        } catch (Exception e) {
            log.error("❌ Multi-attachment email failed to {}: {}", to, e.getMessage(), e);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  HELPERS
    // ──────────────────────────────────────────────────────────────────────────

    record PdfAttachment(String fileName, byte[] pdfBytes) {}

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractTickets(Map<String, Object> data) {
        Object t = data.get("tickets");
        if (t instanceof List) return (List<Map<String, Object>>) t;
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> extractQrCodes(Map<String, Object> data) {
        Object q = data.get("qrCodes");
        if (q instanceof Map) return (Map<String, String>) q;
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractCurrentTicket(Map<String, Object> data) {
        Object ct = data.get("currentTicket");
        if (ct instanceof Map) return (Map<String, Object>) ct;
        return null;
    }

    private Map<String, Object> findTicketForAttendee(
            List<Map<String, Object>> tickets, String email) {
        for (Map<String, Object> ticket : tickets) {
            Object attendeeObj = ticket.get("attendee");
            if (attendeeObj instanceof Map<?, ?> att) {
                Object attEmail = att.get("email");
                if (email != null && email.equalsIgnoreCase(String.valueOf(attEmail))) {
                    return ticket;
                }
            }
        }
        return null;
    }

    private Map<String, Object> buildAttendeeData(
            Map<String, Object> originalData,
            Map<String, Object> ticket,
            boolean isRegistered) {
        java.util.HashMap<String, Object> out = new java.util.HashMap<>(originalData);
        out.put("currentTicket", ticket);
        out.put("isRegistered", isRegistered);
        out.put("isGuest", !isRegistered);
        if (ticket.get("attendee") != null) out.put("attendee", ticket.get("attendee"));
        return out;
    }

    private String resolveSubject(NotificationType type, Map<String, Object> data) {
        Object eventObj = data.get("event");
        String eventName = "Your Event";
        if (eventObj instanceof Map<?, ?> e) {
            Object n = e.get("name");
            if (n != null) eventName = n.toString();
        }
        return switch (type) {
            case EVENT_BOOKING_CONFIRMED      -> "🎫 Booking Confirmed - " + eventName;
            case EVENT_ATTENDEE_TICKET_ISSUED -> "🎟️ Your Ticket is Ready - " + eventName;
            default -> "NexGate Notification";
        };
    }

    private boolean shouldSendChannel(NotificationEvent event, NotificationChannel channel) {
        return event.getChannels() != null && event.getChannels().contains(channel);
    }

    private String str(Map<String, Object> map, String key) {
        if (map == null) return "";
        Object v = map.get(key);
        return v != null ? v.toString() : "";
    }
}