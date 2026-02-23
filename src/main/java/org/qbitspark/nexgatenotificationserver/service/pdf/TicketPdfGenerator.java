package org.qbitspark.nexgatenotificationserver.service.pdf;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.xhtmlrenderer.pdf.ITextRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.*;
import java.util.Base64;
import java.util.List;

@Slf4j
@Service
public class TicketPdfGenerator {

    @Value("${app.logo.path:}")
    private String logoPath;

    public byte[] generateBundledPdf(
            Map<String, Object> eventData,
            List<Map<String, Object>> tickets,
            Map<String, String> qrCodes) throws Exception {

        StringBuilder pages = new StringBuilder();
        for (int i = 0; i < tickets.size(); i++) {
            Map<String, Object> ticket = tickets.get(i);
            String ticketId = str(ticket, "ticketId");
            String jwt      = qrCodes.getOrDefault(ticketId, ticketId);
            String qrBase64 = generateQrBase64(jwt);
            pages.append(buildTicketPage(eventData, ticket, qrBase64, i < tickets.size() - 1));
        }

        byte[] pdf = renderHtmlToPdf(wrapHtml(pages.toString()));
        log.info("✅ Bundled PDF generated: {} page(s)", tickets.size());
        return pdf;
    }

    public byte[] generateSingleTicketPdf(
            Map<String, Object> eventData,
            Map<String, Object> ticket,
            String jwtToken) throws Exception {

        String qrBase64 = generateQrBase64(jwtToken);
        String html     = wrapHtml(buildTicketPage(eventData, ticket, qrBase64, false));
        byte[] pdf      = renderHtmlToPdf(html);
        log.info("✅ Single ticket PDF generated: ticketId={}", str(ticket, "ticketId"));
        return pdf;
    }

    private byte[] renderHtmlToPdf(String html) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ITextRenderer renderer = new ITextRenderer();
        renderer.setDocumentFromString(html);
        renderer.layout();
        renderer.createPDF(baos);
        return baos.toByteArray();
    }

    private String buildTicketPage(Map<String, Object> eventData,
                                   Map<String, Object> ticket,
                                   String qrBase64,
                                   boolean addPageBreak) {

        Map<String, Object> event    = castMap(eventData.get("event"));
        Map<String, Object> attendee = castMap(ticket.get("attendee"));
        Map<String, Object> booking  = castMap(eventData.get("booking"));

        String eventName    = event    != null ? str(event,    "name")       : "Event";
        String eventVenue   = event    != null ? str(event,    "venue")      : "";
        String attendeeName = attendee != null ? str(attendee, "name")       : "—";
        String series       = str(ticket, "series");

        // bookingRef — check multiple possible locations
        String bookingRef = "";
        if (booking != null && !str(booking, "bookingRef").isBlank()) {
            bookingRef = str(booking, "bookingRef");
        } else if (!str(ticket, "bookingRef").isBlank()) {
            bookingRef = str(ticket, "bookingRef");
        } else if (!str(eventData, "bookingRef").isBlank()) {
            bookingRef = str(eventData, "bookingRef");
        }

        String shortRef     = bookingRef.contains("-")
                ? bookingRef.substring(bookingRef.lastIndexOf("-") + 1)
                : bookingRef;
        String ticketNumber = shortRef.isBlank() ? series : shortRef + "-" + series;

        String logoTag      = buildLogoTag();
        String dateTimeHtml = buildDateTimeHtml(event);
        String pageBreakDiv = addPageBreak ? "<div class=\"page-break\"></div>" : "";

        return String.format("""
            <div class="ticket">
                <div class="left-panel">
                    <div class="top-row">
                        <div class="brand">NexGate</div>
                        <div class="badge-cell"><span class="badge">ATTENDEE</span></div>
                    </div>
                    <div class="divider">&#160;</div>
                    <div class="event-name">%s</div>
                    <div class="divider">&#160;</div>
                    <div class="field-grid">
                        <div class="field-row">
                            <div class="field-cell">
                                <div class="field-label">Name Of Attendee:</div>
                                <div class="field-value">%s</div>
                            </div>
                            <div class="field-cell">
                                <div class="field-label">Seat:</div>
                                <div class="field-value">%s</div>
                            </div>
                        </div>
                        %s
                        <div class="field-row">
                            <div class="field-cell-full">
                                <div class="field-label">Venue:</div>
                                <div class="field-value">%s</div>
                            </div>
                        </div>
                    </div>
                    <div class="divider-bottom">&#160;</div>
                    <div class="ticket-num-label">Ticket Number:</div>
                    <div class="ticket-num-value">%s</div>
                </div>
                <div class="right-panel">
                    <div class="qr-card">
                        <div class="qr-wrapper">
                            <img src="data:image/png;base64,%s" alt="QR Code"/>
                            <div class="qr-logo">%s</div>
                        </div>
                    </div>
                    <div class="scan-label">SCAN TO VERIFY</div>
                </div>
            </div>
            %s
            """,
                eventName.toUpperCase(),
                attendeeName, series,
                dateTimeHtml,
                eventVenue,
                ticketNumber,
                qrBase64,
                logoTag,
                pageBreakDiv
        );
    }

    private String buildDateTimeHtml(Map<String, Object> event) {
        if (event == null) return "";

        String scheduleType = str(event, "scheduleType");
        String dates        = str(event, "dates");
        String times        = str(event, "times");
        String date         = str(event, "date");
        String time         = str(event, "time");

        if ("MULTI".equals(scheduleType) && !dates.isBlank()) {
            String[] dateArr = dates.split("\\|");
            String[] timeArr = times.isBlank() ? new String[]{} : times.split("\\|");
            StringBuilder sb = new StringBuilder();
            sb.append("""
                <div class="field-row">
                    <div class="field-cell">
                        <div class="field-label">Date:</div>
                """);
            for (String d : dateArr) {
                sb.append(String.format(
                        "<div class=\"field-value schedule-val\">%s</div>", formatDate(d.trim())));
            }
            sb.append("</div><div class=\"field-cell\"><div class=\"field-label\">Time:</div>");
            for (int i = 0; i < dateArr.length; i++) {
                String t = (timeArr.length > i) ? timeArr[i]
                        : (timeArr.length > 0  ? timeArr[timeArr.length - 1] : "—");
                sb.append(String.format(
                        "<div class=\"field-value schedule-val\">%s</div>", t.trim()));
            }
            sb.append("</div></div>");
            return sb.toString();
        }

        if ("RANGE".equals(scheduleType) && !dates.isBlank()) {
            String[] parts = dates.split("–");
            String formatted = parts.length == 2
                    ? formatDate(parts[0].trim()) + " – " + formatDate(parts[1].trim())
                    : formatDate(dates);
            String formattedTime = !times.isBlank() ? times : (time.isBlank() ? "—" : time);
            return String.format("""
                <div class="field-row">
                    <div class="field-cell">
                        <div class="field-label">Date:</div>
                        <div class="field-value">%s</div>
                    </div>
                    <div class="field-cell">
                        <div class="field-label">Time:</div>
                        <div class="field-value">%s</div>
                    </div>
                </div>
                """, formatted, formattedTime);
        }

        String formattedDate = !dates.isBlank() ? formatDate(dates) : formatDate(date);
        String formattedTime = !times.isBlank() ? times : (time.isBlank() ? "—" : time);

        return String.format("""
            <div class="field-row">
                <div class="field-cell">
                    <div class="field-label">Date:</div>
                    <div class="field-value">%s</div>
                </div>
                <div class="field-cell">
                    <div class="field-label">Time:</div>
                    <div class="field-value">%s</div>
                </div>
            </div>
            """, formattedDate, formattedTime);
    }

    private String buildLogoTag() {
        if (logoPath != null && !logoPath.isBlank()) {
            return String.format("<img src=\"file://%s\" alt=\"logo\"/>", logoPath);
        }
        return "<span style=\"color:#F97316;font-size:8pt;font-weight:bold;\">N</span>";
    }

    private String wrapHtml(String body) {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN"
                "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">
            <html xmlns="http://www.w3.org/1999/xhtml">
            <head>
                <meta charset="UTF-8"/>
                <style>
                    @page { size:660pt 340pt; margin:0; background-color:#12131a; }
                    * { margin:0; padding:0; box-sizing:border-box; font-family:Helvetica,Arial,sans-serif; }
                    body { background:#12131a; padding:0; }
                    .page-break { page-break-after:always; }
                    .ticket { width:660pt; height:340pt; display:table; border-radius:16pt; overflow:hidden; }
                    .left-panel {
                        display:table-cell; width:370pt; background-color:#F97316;
                        vertical-align:top; padding:24pt 26pt; border-radius:16pt 0 0 16pt;
                    }
                    .right-panel {
                        display:table-cell; width:290pt; background-color:#ffffff;
                        vertical-align:middle; text-align:center; padding:20pt;
                        border-radius:0 16pt 16pt 0;
                    }
                    .top-row { display:table; width:100%; margin-bottom:6pt; }
                    .brand { display:table-cell; vertical-align:middle; color:#ffffff; font-size:12pt; font-weight:bold; }
                    .badge-cell { display:table-cell; vertical-align:middle; text-align:right; }
                    .badge { display:inline-block; background-color:#ffffff; color:#F97316; font-size:7pt; font-weight:bold; padding:4pt 14pt; border-radius:20pt; }
                    .event-name { color:#ffffff; font-size:15pt; font-weight:bold; line-height:1.25; margin-top:6pt; margin-bottom:10pt; }
                    .divider { width:100%; height:0.8pt; background-color:rgba(255,255,255,0.4); margin-bottom:12pt; font-size:0; line-height:0; }
                    .divider-bottom { width:100%; height:0.8pt; background-color:rgba(255,255,255,0.4); margin-bottom:10pt; font-size:0; line-height:0; }
                    .field-grid { display:table; width:100%; }
                    .field-row { display:table-row; }
                    .field-cell { display:table-cell; width:50%; padding-bottom:10pt; padding-right:20pt; vertical-align:top; }
                    .field-cell-full { display:block; width:100%; padding-bottom:10pt; }
                    .field-label { color:#ffffff; font-size:7.5pt; margin-bottom:2pt; }
                    .field-value { color:#ffffff; font-size:11pt; font-weight:bold; }
                    .schedule-val { margin-bottom:3pt; font-size:10.5pt; }
                    .ticket-num-label { color:#ffffff; font-size:7.5pt; margin-bottom:3pt; }
                    .ticket-num-value { color:#ffffff; font-size:10pt; font-weight:bold; font-family:Courier,monospace; letter-spacing:0.5pt; }
                    .qr-card { display:inline-block; border:1pt solid #E0E0E0; border-radius:12pt; padding:14pt; background:#ffffff; }
                    .qr-wrapper { position:relative; display:inline-block; }
                    .qr-card img { width:195pt; height:195pt; display:block; }
                    .qr-logo { position:absolute; top:50%; left:50%; width:38pt; height:38pt; margin-top:-19pt; margin-left:-19pt; background-color:#ffffff; border-radius:8pt; border:2pt solid #F97316; padding:4pt; text-align:center; }
                    .qr-logo img { width:26pt; height:26pt; display:block; }
                    .scan-label { color:#999999; font-size:7.5pt; font-weight:bold; margin-top:10pt; letter-spacing:1.5pt; }
                </style>
            </head>
            <body>
            """ + body + """
            </body>
            </html>
            """;
    }

    private String generateQrBase64(String content) {
        if (content == null || content.isBlank()) return "";
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 1);
            BitMatrix matrix = new QRCodeWriter()
                    .encode(content, BarcodeFormat.QR_CODE, 420, 420, hints);
            BufferedImage img = MatrixToImageWriter.toBufferedImage(matrix);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "PNG", out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            log.error("QR generation failed: {}", e.getMessage());
            return "";
        }
    }

    private String formatDate(String raw) {
        if (raw == null || raw.isBlank()) return "—";
        try {
            LocalDate d   = LocalDate.parse(raw.trim());
            int       day = d.getDayOfMonth();
            String    dow = d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
            String    mon = d.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
            return dow + ", " + day + daySuffix(day) + " " + mon + " " + d.getYear();
        } catch (Exception e) {
            // already formatted — try to shorten day and month names
            return raw
                    .replaceAll("(?i)Monday",    "Mon").replaceAll("(?i)Tuesday",   "Tue")
                    .replaceAll("(?i)Wednesday", "Wed").replaceAll("(?i)Thursday",  "Thu")
                    .replaceAll("(?i)Friday",    "Fri").replaceAll("(?i)Saturday",  "Sat")
                    .replaceAll("(?i)Sunday",    "Sun")
                    .replaceAll("(?i)January",   "Jan").replaceAll("(?i)February",  "Feb")
                    .replaceAll("(?i)March",     "Mar").replaceAll("(?i)April",     "Apr")
                    .replaceAll("(?i)May",       "May").replaceAll("(?i)June",      "Jun")
                    .replaceAll("(?i)July",      "Jul").replaceAll("(?i)August",    "Aug")
                    .replaceAll("(?i)September", "Sep").replaceAll("(?i)October",   "Oct")
                    .replaceAll("(?i)November",  "Nov").replaceAll("(?i)December",  "Dec");
        }
    }

    private String daySuffix(int day) {
        if (day >= 11 && day <= 13) return "th";
        return switch (day % 10) {
            case 1  -> "st";
            case 2  -> "nd";
            case 3  -> "rd";
            default -> "th";
        };
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