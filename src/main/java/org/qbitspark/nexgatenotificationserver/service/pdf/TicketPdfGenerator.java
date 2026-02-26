package org.qbitspark.nexgatenotificationserver.service.pdf;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import lombok.extern.slf4j.Slf4j;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Component
@Slf4j
public class TicketPdfGenerator {

    @Value("${app.ticket.svg-template:templates/ticket-template.svg}")
    private String svgTemplatePath;

    // High-res render — same as main backend, keeps PDF sharp when zoomed
    private static final float CROP_X   = 60f;
    private static final float CROP_Y   = 400f;
    private static final float RENDER_W = 2520f;
    private static final float RENDER_H = 1460f;
    private static final int   QR_SIZE  = 200;

    public byte[] generateSingleTicketPdf(
            Map<String, Object> eventData,
            Map<String, Object> ticket,
            String jwtToken) throws Exception {

        String svgTemplate = loadSvgTemplate();
        String filledSvg   = buildSvg(svgTemplate, eventData, ticket, jwtToken);
        return svgToPdfBytes(filledSvg);
    }

    private String loadSvgTemplate() throws IOException {
        ClassPathResource resource = new ClassPathResource(svgTemplatePath);
        try (InputStream is = resource.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @SuppressWarnings("unchecked")
    private String buildSvg(String template,
                            Map<String, Object> eventData,
                            Map<String, Object> ticket,
                            String jwtToken) {

        // Extract nested sub-maps from the notification payload
        Map<String, Object> event    = castMap(eventData.get("event"));
        Map<String, Object> booking  = castMap(eventData.get("booking"));
        Map<String, Object> attendee = castMap(ticket.get("attendee"));

        // Event title
        String eventTitle = trimWithEllipsis(event != null ? str(event, "name") : "Event", 35);

        // Attendee name
        String attendeeName = attendee != null ? str(attendee, "name") : "\u2014";
        if (attendeeName.isBlank()) attendeeName = "\u2014";

        // Seat / series
        String series = str(ticket, "series");
        if (series.isBlank()) series = str(ticket, "ticketType");

        // Date range + time — formatted as "20th Feb, 2026" (same as main backend)
        String dateRange;
        String eventTime;
        if (event != null) {
            String scheduleType = str(event, "scheduleType");
            if ("RANGE".equals(scheduleType)) {
                String dates = str(event, "dates");
                String[] parts = dates.split("[\u2013-]", 2);
                dateRange = parts.length == 2
                        ? formatDateRange(parts[0].trim(), parts[1].trim())
                        : formatSingleDate(dates.trim());
            } else {
                String rawDate = str(event, "date");
                if (rawDate.isBlank()) {
                    String dates = str(event, "dates");
                    rawDate = dates.contains("|") ? dates.split("\\|")[0].trim() : dates.trim();
                }
                dateRange = formatSingleDate(rawDate);
            }
            eventTime = str(event, "time");
            if (eventTime.isBlank()) {
                String times = str(event, "times");
                if (!times.isBlank()) eventTime = times.split("\\|")[0].trim();
            }
        } else {
            dateRange = "\u2014";
            eventTime = "\u2014";
        }

        // Venue
        String venue = trimWithEllipsis(event != null ? str(event, "venue") : "\u2014", 40);

        // Ticket number — use what the main backend already set on the ticket entity
        String ticketNumber = str(ticket, "ticketNumber");
        if (ticketNumber.isBlank()) {
            // fallback: build from bookingRef + series
            String bookingRef = booking != null ? str(booking, "bookingRef") : "";
            if (bookingRef.isBlank() && booking != null) bookingRef = str(booking, "id");
            if (bookingRef.isBlank()) bookingRef = str(ticket, "bookingRef");
            String shortRef = bookingRef.contains("-")
                    ? bookingRef.substring(bookingRef.lastIndexOf("-") + 1)
                    : bookingRef;
            ticketNumber = shortRef.isBlank() ? series : shortRef + "-" + series;
        }

        String qrDataUri = generateQrDataUri(jwtToken, QR_SIZE);

        String svg = template;
        svg = svg.replaceFirst("(<svg[^>]*>)", "$1<rect width=\"100%\" height=\"100%\" fill=\"#3b1f0e\"/>");
        svg = svg.replace("{{EVENT_TITLE}}",      escapeXml(eventTitle.toUpperCase()))
                .replace("{{ATTENDEE_NAME}}",    escapeXml(attendeeName))
                .replace("{{SEAT_TYPE}}",        escapeXml(series))
                .replace("{{EVENT_DATE_RANGE}}", escapeXml(dateRange))
                .replace("{{EVENT_TIME}}",       escapeXml(eventTime.isBlank() ? "\u2014" : eventTime))
                .replace("{{VENUE}}",            escapeXml(venue))
                .replace("{{TICKET_NUMBER}}",    escapeXml(ticketNumber))
                .replace("{{QR_CODE_BASE64}}",   qrDataUri);
        return svg;
    }

    // Same high-res pipeline as main backend — renders at 2520x1460 to stay sharp when zoomed
    private byte[] svgToPdfBytes(String svgContent) throws Exception {
        PNGTranscoder png = new PNGTranscoder();
        png.addTranscodingHint(PNGTranscoder.KEY_WIDTH,  RENDER_W);
        png.addTranscodingHint(PNGTranscoder.KEY_HEIGHT, RENDER_H);
        png.addTranscodingHint(PNGTranscoder.KEY_AOI,
                new Rectangle2D.Float(CROP_X, CROP_Y, RENDER_W, RENDER_H));

        ByteArrayOutputStream pngOut = new ByteArrayOutputStream();
        TranscoderInput input = new TranscoderInput(new StringReader(svgContent));
        input.setURI("file:///");
        png.transcode(input, new TranscoderOutput(pngOut));

        com.itextpdf.text.Document doc = new com.itextpdf.text.Document(
                new com.itextpdf.text.Rectangle(RENDER_W, RENDER_H), 0, 0, 0, 0);
        ByteArrayOutputStream pdfOut = new ByteArrayOutputStream();
        com.itextpdf.text.pdf.PdfWriter writer =
                com.itextpdf.text.pdf.PdfWriter.getInstance(doc, pdfOut);
        writer.setFullCompression();
        doc.open();
        com.itextpdf.text.Image img =
                com.itextpdf.text.Image.getInstance(pngOut.toByteArray());
        img.scaleAbsolute(RENDER_W, RENDER_H);
        img.setAbsolutePosition(0, 0);
        doc.add(img);
        doc.close();
        return pdfOut.toByteArray();
    }

    // Accepted input formats:
    //   ISO:       "2026-02-20"
    //   Full:      "Friday, February 20, 2026"   ← what main backend puts in event.date
    //   DateTime:  "2026-02-20T15:00:00"
    // All produce:  "20th Feb, 2026"
    private static final List<DateTimeFormatter> DATE_PARSERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,                                    // 2026-02-20
            DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH),  // Friday, February 20, 2026
            DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH),        // February 20, 2026
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)          // 20 February 2026
    );

    private String formatSingleDate(String raw) {
        if (raw == null || raw.isBlank()) return "\u2014";
        String trimmed = raw.trim();
        // Try LocalDateTime first (e.g. "2026-02-20T15:00:00")
        try {
            LocalDate d = LocalDateTime.parse(trimmed).toLocalDate();
            return toDisplayDate(d);
        } catch (Exception ignored) {}
        // Try each date-only parser
        for (DateTimeFormatter fmt : DATE_PARSERS) {
            try {
                LocalDate d = LocalDate.parse(trimmed, fmt);
                return toDisplayDate(d);
            } catch (Exception ignored) {}
        }
        return raw; // last resort: return as-is
    }

    private String toDisplayDate(LocalDate d) {
        return d.getDayOfMonth() + daySuffix(d.getDayOfMonth()) + " "
                + d.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH)
                + ", " + d.getYear();
    }

    private String formatDateRange(String startRaw, String endRaw) {
        try {
            LocalDate start = parseDate(startRaw);
            LocalDate end   = parseDate(endRaw);
            if (start == null || end == null) return startRaw + " \u2013 " + endRaw;
            if (start.equals(end)) return toDisplayDate(start);
            if (start.getYear() != end.getYear()) {
                return toDisplayDate(start) + " \u2013 " + toDisplayDate(end);
            }
            String startPart = start.getDayOfMonth() + daySuffix(start.getDayOfMonth()) + " "
                    + start.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH);
            return startPart + " \u2013 " + toDisplayDate(end);
        } catch (Exception e) {
            return startRaw + " \u2013 " + endRaw;
        }
    }

    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String trimmed = raw.trim();
        try { return LocalDateTime.parse(trimmed).toLocalDate(); } catch (Exception ignored) {}
        for (DateTimeFormatter fmt : DATE_PARSERS) {
            try { return LocalDate.parse(trimmed, fmt); } catch (Exception ignored) {}
        }
        return null;
    }

    private String daySuffix(int day) {
        if (day >= 11 && day <= 13) return "th";
        return switch (day % 10) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
    }

    private String trimWithEllipsis(String value, int maxChars) {
        if (value == null) return "";
        return value.length() <= maxChars ? value
                : value.substring(0, maxChars - 1).stripTrailing() + "\u2026";
    }

    private String generateQrDataUri(String content, int size) {
        if (content == null || content.isBlank()) {
            log.warn("Empty QR content — QR will be blank");
            return "";
        }
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 1);
            BitMatrix matrix = new QRCodeWriter()
                    .encode(content, BarcodeFormat.QR_CODE, size, size, hints);
            BufferedImage img = MatrixToImageWriter.toBufferedImage(matrix);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "PNG", baos);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            log.error("QR generation failed: {}", e.getMessage());
            return "";
        }
    }

    private String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object o) {
        return (o instanceof Map) ? (Map<String, Object>) o : null;
    }

    private String str(Map<String, Object> map, String key) {
        if (map == null) return "";
        Object v = map.get(key);
        return v != null ? v.toString() : "";
    }
}