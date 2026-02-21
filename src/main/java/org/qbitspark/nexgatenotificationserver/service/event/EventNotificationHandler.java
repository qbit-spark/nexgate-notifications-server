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
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Handles event-domain notifications.
 *
 * Two flows:
 *  1. BUYER  (EVENT_BOOKING_CONFIRMED)        — one recipient, all-tickets PDF bundled
 *  2. ATTENDEE (EVENT_ATTENDEE_TICKET_ISSUED) — one recipient per attendee, single-ticket PDF
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventNotificationHandler {

    private final EmailService emailService;
    private final SmsService smsService;
    private final InAppService inAppService;
    private final TicketPdfGenerator pdfGenerator;

    // ──────────────────────────────────────────────────────────────────────────
    //  BUYER FLOW
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
            // ── Generate bundled PDF for all tickets ──────────────────────────
            List<Map<String, Object>> tickets = extractTickets(data);
            Map<String, String> qrCodes = extractQrCodes(data);

            byte[] bundledPdf = pdfGenerator.generateBundledPdf(data, tickets, qrCodes);
            String pdfFileName = buildPdfFileName(data, "booking");

            // ── Email (with PDF attachment) ───────────────────────────────────
            if (shouldSendChannel(event, NotificationChannel.EMAIL)) {
                emailService.sendWithAttachment(
                        NotificationType.EVENT_BOOKING_CONFIRMED,
                        buyer.getEmail(),
                        data,
                        bundledPdf,
                        pdfFileName
                );
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
    //  ATTENDEE FLOW
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

        // Each recipient is one attendee — find their specific ticket
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

        log.info("🎫 Processing attendee notification: email={}, registered={}",
                attendee.getEmail(), isRegistered);

        // Attendee payload uses "currentTicket" (single ticket map).
        // Buyer payload uses "tickets" list — try both.
        Map<String, Object> myTicket = extractCurrentTicket(data);
        if (myTicket == null) {
            myTicket = findTicketForAttendee(tickets, attendee.getEmail());
        }
        if (myTicket == null) {
            log.warn("⚠️ No ticket found for attendee email={}, using first ticket", attendee.getEmail());
            myTicket = tickets.isEmpty() ? Map.of() : tickets.get(0);
        }

        // Resolve JWT token — qrCodes map is keyed by ticketId
        String ticketId = str(myTicket, "ticketId");
        String jwtToken = qrCodes.get(ticketId);

        // Fallback: if only one entry in map, use it regardless of key match
        if ((jwtToken == null || jwtToken.isBlank()) && !qrCodes.isEmpty()) {
            jwtToken = qrCodes.values().iterator().next();
            log.warn("⚠️ ticketId '{}' not found in qrCodes map — using first available token", ticketId);
        }

        if (jwtToken == null || jwtToken.isBlank()) {
            throw new IllegalArgumentException(
                    "JWT token is empty for ticketId=" + ticketId + " — cannot generate QR code");
        }

        log.info("🔑 JWT token resolved: ticketId={}, tokenLength={}", ticketId, jwtToken.length());

        // Generate single-ticket PDF
        byte[] ticketPdf = pdfGenerator.generateSingleTicketPdf(data, myTicket, jwtToken);
        String pdfFileName = buildPdfFileName(data, "ticket-" + ticketId);

        // Build per-attendee data (adds isRegistered flag for template switching)
        Map<String, Object> attendeeData = buildAttendeeData(data, myTicket, isRegistered);

        // ── Email ─────────────────────────────────────────────────────────────
        if (shouldSendChannel(event, NotificationChannel.EMAIL)) {
            emailService.sendWithAttachment(
                    NotificationType.EVENT_ATTENDEE_TICKET_ISSUED,
                    attendee.getEmail(),
                    attendeeData,
                    ticketPdf,
                    pdfFileName
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
    //  HELPERS
    // ──────────────────────────────────────────────────────────────────────────

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
            if (attendeeObj instanceof Map) {
                Object attendeeEmail = ((Map<?, ?>) attendeeObj).get("email");
                if (email != null && email.equalsIgnoreCase(String.valueOf(attendeeEmail))) {
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

        // Shallow copy + add attendee-specific flags
        java.util.HashMap<String, Object> attendeeData = new java.util.HashMap<>(originalData);
        attendeeData.put("currentTicket", ticket);
        attendeeData.put("isRegistered", isRegistered);
        attendeeData.put("isGuest", !isRegistered);

        // Also expose attendee directly at top level for template convenience
        if (ticket.get("attendee") != null) {
            attendeeData.put("attendee", ticket.get("attendee"));
        }

        return attendeeData;
    }

    private String buildPdfFileName(Map<String, Object> data, String suffix) {
        Object bookingObj = data.get("booking");
        String bookingId = "booking";
        if (bookingObj instanceof Map) {
            Object id = ((Map<?, ?>) bookingObj).get("id");
            if (id != null) bookingId = id.toString();
        }
        return "nexgate-" + suffix + "-" + bookingId + ".pdf";
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