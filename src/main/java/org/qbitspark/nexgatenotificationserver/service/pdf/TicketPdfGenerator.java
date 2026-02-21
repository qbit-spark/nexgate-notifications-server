package org.qbitspark.nexgatenotificationserver.service.pdf;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Generates Eventbrite-style ticket PDFs.
 *
 * One page per ticket. All tickets for a buyer are bundled into a single PDF.
 * QR code is generated from the JWT token string provided by NextGate.
 */
@Slf4j
@Service
public class TicketPdfGenerator {

    @Value("${app.frontend.url:https://nexgate.com}")
    private String frontendUrl;

    // ── Brand colours ──────────────────────────────────────────────────────────
    private static final BaseColor BRAND_PURPLE    = new BaseColor(102, 126, 234);   // #667eea
    private static final BaseColor BRAND_DARK      = new BaseColor(26,  26,  46);    // #1a1a2e
    private static final BaseColor LIGHT_GREY      = new BaseColor(245, 245, 247);   // #f5f5f7
    private static final BaseColor MID_GREY        = new BaseColor(150, 150, 160);   // #9696a0
    private static final BaseColor WHITE           = BaseColor.WHITE;
    private static final BaseColor DIVIDER_COLOR   = new BaseColor(220, 220, 228);   // #dcdce4
    private static final BaseColor SUCCESS_GREEN   = new BaseColor(76,  175,  80);   // #4caf50

    // ── Fonts ──────────────────────────────────────────────────────────────────
    private Font fontTitleLarge;
    private Font fontTitleMedium;
    private Font fontLabel;
    private Font fontValue;
    private Font fontSmall;
    private Font fontBrand;
    private Font fontTicketNumber;
    private boolean fontsInitialized = false;

    // ──────────────────────────────────────────────────────────────────────────
    //  PUBLIC API
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Generate a bundled PDF containing one page per ticket.
     * Used for the BUYER email attachment.
     *
     * @param eventData  the full data map from the notification event
     * @param tickets    list of ticket maps (each ticket has attendee info + ticketId)
     * @param qrCodes    Map&lt;ticketId, jwtTokenString&gt;
     * @return PDF bytes
     */
    public byte[] generateBundledPdf(
            Map<String, Object> eventData,
            List<Map<String, Object>> tickets,
            Map<String, String> qrCodes) throws DocumentException, IOException, WriterException {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A5, 0, 0, 0, 0);
        PdfWriter writer = PdfWriter.getInstance(document, baos);
        document.open();
        initFonts();

        for (int i = 0; i < tickets.size(); i++) {
            Map<String, Object> ticket = tickets.get(i);
            String ticketId = str(ticket, "ticketId");
            String jwtToken = qrCodes.getOrDefault(ticketId, ticketId);

            if (i > 0) document.newPage();
            renderTicketPage(writer, document, eventData, ticket, jwtToken, i + 1, tickets.size());
            // Page 2 of each ticket — full QR page
            document.newPage();
            renderQrPage(writer, document, eventData, ticket, jwtToken);
        }

        document.close();
        log.info("✅ Generated bundled PDF with {} ticket(s)", tickets.size());
        return baos.toByteArray();
    }

    /**
     * Generate a single-ticket PDF.
     * Used for attendee email attachments.
     *
     * @param eventData  the full data map from the notification event
     * @param ticket     the specific ticket map for this attendee
     * @param jwtToken   JWT token string — used to generate the QR code
     * @return PDF bytes
     */
    public byte[] generateSingleTicketPdf(
            Map<String, Object> eventData,
            Map<String, Object> ticket,
            String jwtToken) throws DocumentException, IOException, WriterException {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A5, 0, 0, 0, 0);
        PdfWriter writer = PdfWriter.getInstance(document, baos);
        document.open();
        initFonts();

        renderTicketPage(writer, document, eventData, ticket, jwtToken, 1, 1);

        // Page 2 — full-page QR code
        document.newPage();
        renderQrPage(writer, document, eventData, ticket, jwtToken);

        document.close();
        log.info("✅ Generated single ticket PDF for ticketId={}", str(ticket, "ticketId"));
        return baos.toByteArray();
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  TICKET PAGE RENDERER
    // ──────────────────────────────────────────────────────────────────────────

    private void renderTicketPage(
            PdfWriter writer,
            Document document,
            Map<String, Object> eventData,
            Map<String, Object> ticket,
            String jwtToken,
            int ticketIndex,
            int totalTickets) throws DocumentException, IOException, WriterException {

        PdfContentByte cb = writer.getDirectContent();

        float pageW = document.getPageSize().getWidth();
        float pageH = document.getPageSize().getHeight();

        // ── 1. Dark header band ────────────────────────────────────────────────
        float headerH = pageH * 0.30f;
        cb.setColorFill(BRAND_DARK);
        cb.rectangle(0, pageH - headerH, pageW, headerH);
        cb.fill();

        // Accent stripe at very top
        cb.setColorFill(BRAND_PURPLE);
        cb.rectangle(0, pageH - 6, pageW, 6);
        cb.fill();

        // "NEXGATE" brand top-left
        ColumnText.showTextAligned(cb, Element.ALIGN_LEFT,
                new Phrase("NEXGATE", fontBrand),
                24, pageH - 28, 0);

        // Ticket counter top-right  e.g. "2 / 5"
        if (totalTickets > 1) {
            ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT,
                    new Phrase(ticketIndex + " / " + totalTickets, fontSmall),
                    pageW - 24, pageH - 28, 0);
        }

        // Event name — large
        Map<String, Object> event = castMap(eventData.get("event"));
        String eventName = event != null ? str(event, "name") : "Event";
        String eventDate = event != null ? str(event, "date") : "";
        String eventTime = event != null ? str(event, "time") : "";
        String eventVenue = event != null ? str(event, "venue") : "";

        // Event name (may be long — wrap within header)
        float textY = pageH - 55;
        Font bigEventFont = new Font(Font.FontFamily.HELVETICA, 18, Font.BOLD, WHITE);
        PdfPTable headerTable = new PdfPTable(1);
        headerTable.setTotalWidth(pageW - 48);
        PdfPCell nameCell = new PdfPCell(new Phrase(eventName, bigEventFont));
        nameCell.setBorder(Rectangle.NO_BORDER);
        nameCell.setBackgroundColor(BRAND_DARK);
        nameCell.setPadding(0);
        headerTable.addCell(nameCell);
        headerTable.writeSelectedRows(0, -1, 24, textY, cb);

        // Date / time line
        String dateTime = eventDate + (eventTime.isBlank() ? "" : "  •  " + eventTime);
        Font dtFont = new Font(Font.FontFamily.HELVETICA, 10, Font.NORMAL, MID_GREY);
        ColumnText.showTextAligned(cb, Element.ALIGN_LEFT,
                new Phrase("📅  " + dateTime, dtFont),
                24, pageH - headerH + 38, 0);

        // Venue line
        Font venueFont = new Font(Font.FontFamily.HELVETICA, 10, Font.NORMAL, MID_GREY);
        ColumnText.showTextAligned(cb, Element.ALIGN_LEFT,
                new Phrase("📍  " + eventVenue, venueFont),
                24, pageH - headerH + 20, 0);

        // ── 2. White body ──────────────────────────────────────────────────────
        float bodyTop   = pageH - headerH;
        float bodyH     = pageH * 0.48f;

        cb.setColorFill(WHITE);
        cb.rectangle(0, bodyTop - bodyH, pageW, bodyH);
        cb.fill();

        // Attendee info
        Map<String, Object> attendee = castMap(ticket.get("attendee"));
        String attendeeName  = attendee != null ? str(attendee, "name")  : "—";
        String ticketType      = cleanTicketType(str(ticket, "ticketType"));
        String ticketId        = str(ticket, "ticketId");
        String ticketSeries    = str(ticket, "series");
        // Prefer series number (e.g. "VIPP-0003") over raw UUID
        String ticketIdDisplay = !ticketSeries.isBlank() ? ticketSeries
                : (ticketId.length() > 20 ? ticketId.substring(0, 20) + "…" : ticketId);
        String bookingRef      = resolveBookingRef(eventData);

        float infoY = bodyTop - 20;

        // ATTENDEE label + value
        drawLabelValue(cb, "ATTENDEE", attendeeName,
                24, pageW / 2 - 12, infoY, fontLabel, fontTitleMedium);

        // TICKET TYPE label + value
        drawLabelValue(cb, "TICKET TYPE", ticketType,
                pageW / 2 + 12, pageW - 24, infoY, fontLabel, fontValue);

        // Divider
        float divY = infoY - 52;
        cb.setColorStroke(DIVIDER_COLOR);
        cb.setLineWidth(0.5f);
        cb.moveTo(24, divY);
        cb.lineTo(pageW - 24, divY);
        cb.stroke();

        // TICKET # label + value
        drawLabelValue(cb, "TICKET #", ticketIdDisplay,
                24, pageW / 2 - 12, divY - 10, fontLabel, fontTicketNumber);

        // BOOKING REF label + value
        drawLabelValue(cb, "BOOKING REF", bookingRef,
                pageW / 2 + 12, pageW - 24, divY - 10, fontLabel, fontValue);

        // ── 3. Perforated tear-line ────────────────────────────────────────────
        float tearY = bodyTop - bodyH;
        drawDashedLine(cb, 0, tearY, pageW, tearY);

        // Scissors icon hint
        Font scissorsFont = new Font(Font.FontFamily.HELVETICA, 8, Font.NORMAL, MID_GREY);
        ColumnText.showTextAligned(cb, Element.ALIGN_CENTER,
                new Phrase("✂", scissorsFont),
                pageW / 2, tearY - 2, 0);

        // ── 4. QR section (bottom stub) ────────────────────────────────────────
        float stubH = pageH - headerH - bodyH;

        cb.setColorFill(LIGHT_GREY);
        cb.rectangle(0, 0, pageW, stubH);
        cb.fill();

        // Stub: just show "SEE NEXT PAGE" — full QR is on page 2
        Font scanFont = new Font(Font.FontFamily.HELVETICA, 7, Font.BOLD, MID_GREY);
        ColumnText.showTextAligned(cb, Element.ALIGN_CENTER,
                new Phrase("▶  QR CODE ON NEXT PAGE", scanFont),
                pageW / 2, stubH / 2 + 4, 0);
        Font scanFont2 = new Font(Font.FontFamily.HELVETICA, 6, Font.NORMAL, MID_GREY);
        ColumnText.showTextAligned(cb, Element.ALIGN_CENTER,
                new Phrase("SCAN TO VERIFY AT ENTRANCE", scanFont2),
                pageW / 2, stubH / 2 - 8, 0);
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  QR FULL PAGE
    // ──────────────────────────────────────────────────────────────────────────

    private void renderQrPage(
            PdfWriter writer,
            Document document,
            Map<String, Object> eventData,
            Map<String, Object> ticket,
            String jwtToken) throws DocumentException, IOException {

        PdfContentByte cb = writer.getDirectContent();
        float pageW = document.getPageSize().getWidth();
        float pageH = document.getPageSize().getHeight();

        // ── Background: dark top band + white body ────────────────────────────
        float topBandH = 60f;
        cb.setColorFill(BRAND_DARK);
        cb.rectangle(0, pageH - topBandH, pageW, topBandH);
        cb.fill();

        // Purple accent stripe
        cb.setColorFill(BRAND_PURPLE);
        cb.rectangle(0, pageH - 5, pageW, 5);
        cb.fill();

        // White body
        cb.setColorFill(WHITE);
        cb.rectangle(0, 0, pageW, pageH - topBandH);
        cb.fill();

        // Brand label in top band
        ColumnText.showTextAligned(cb, Element.ALIGN_LEFT,
                new Phrase("NEXGATE", fontBrand),
                24, pageH - 36, 0);

        // "YOUR TICKET" label top-right
        Font qrPageLabel = new Font(Font.FontFamily.HELVETICA, 7, Font.BOLD, BRAND_PURPLE);
        ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT,
                new Phrase("SCAN TO ENTER", qrPageLabel),
                pageW - 24, pageH - 36, 0);

        // ── Event name ────────────────────────────────────────────────────────
        Map<String, Object> event = castMap(eventData.get("event"));
        String eventName = event != null ? str(event, "name") : "Event";

        Font eventNameFont = new Font(Font.FontFamily.HELVETICA, 13, Font.BOLD, BRAND_DARK);
        ColumnText ct = new ColumnText(cb);
        ct.setSimpleColumn(24, pageH - topBandH - 40, pageW - 24, pageH - topBandH - 10);
        ct.addText(new Phrase(eventName, eventNameFont));
        ct.go();

        // Attendee name + ticket type under event name
        Map<String, Object> attendee = castMap(ticket.get("attendee"));
        String attendeeName    = attendee != null ? str(attendee, "name") : "—";
        String ticketType      = cleanTicketType(str(ticket, "ticketType"));
        String series          = str(ticket, "series");
        String seriesDisplay   = !series.isBlank() ? series : str(ticket, "ticketId");

        Font attendeeFont = new Font(Font.FontFamily.HELVETICA, 10, Font.NORMAL, MID_GREY);
        ColumnText.showTextAligned(cb, Element.ALIGN_LEFT,
                new Phrase(attendeeName + "  ·  " + ticketType, attendeeFont),
                24, pageH - topBandH - 52, 0);

        // ── Divider ───────────────────────────────────────────────────────────
        cb.setColorStroke(DIVIDER_COLOR);
        cb.setLineWidth(0.5f);
        cb.moveTo(24, pageH - topBandH - 62);
        cb.lineTo(pageW - 24, pageH - topBandH - 62);
        cb.stroke();

        // ── Big QR code centred on the page ───────────────────────────────────
        byte[] qrBytes = generateQrBytes(jwtToken, 400);
        if (qrBytes != null) {
            Image qrImage = Image.getInstance(qrBytes);
            float qrSize  = Math.min(pageW, pageH) * 0.82f;
            qrImage.scaleAbsolute(qrSize, qrSize);
            float qrX = (pageW - qrSize) / 2f;
            float qrY = (pageH * 0.5f) - (qrSize / 2f) - 10;
            qrImage.setAbsolutePosition(qrX, qrY);
            cb.addImage(qrImage);
        }

        // ── Booking ref + Ticket series side by side below QR ───────────────
        String bookingRef    = resolveBookingRef(eventData);
        Font labelFont = new Font(Font.FontFamily.HELVETICA, 7,  Font.BOLD,   MID_GREY);
        Font refFont   = new Font(Font.FontFamily.COURIER,   11, Font.BOLD,   BRAND_DARK);
        Font serFont   = new Font(Font.FontFamily.COURIER,   11, Font.BOLD,   BRAND_PURPLE);

        float bottomY  = 38;
        float leftCol  = pageW * 0.25f;
        float rightCol = pageW * 0.75f;

        // Labels
        ColumnText.showTextAligned(cb, Element.ALIGN_CENTER,
                new Phrase("BOOKING REF", labelFont), leftCol, bottomY, 0);
        ColumnText.showTextAligned(cb, Element.ALIGN_CENTER,
                new Phrase("TICKET SERIES", labelFont), rightCol, bottomY, 0);

        // Values
        ColumnText.showTextAligned(cb, Element.ALIGN_CENTER,
                new Phrase(bookingRef, refFont), leftCol, bottomY - 14, 0);
        ColumnText.showTextAligned(cb, Element.ALIGN_CENTER,
                new Phrase(seriesDisplay, serFont), rightCol, bottomY - 14, 0);

        // Thin divider between the two columns
        cb.setColorStroke(DIVIDER_COLOR);
        cb.setLineWidth(0.5f);
        cb.moveTo(pageW / 2, bottomY + 8);
        cb.lineTo(pageW / 2, bottomY - 18);
        cb.stroke();
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  HELPERS
    // ──────────────────────────────────────────────────────────────────────────

    private void drawLabelValue(PdfContentByte cb,
                                String label, String value,
                                float x1, float x2, float topY,
                                Font lFont, Font vFont) throws DocumentException {
        ColumnText ct = new ColumnText(cb);
        ct.setSimpleColumn(x1, topY - 40, x2, topY);
        ct.addText(new Phrase(label + "\n", lFont));
        ct.addText(new Phrase(value, vFont));
        ct.go();
    }

    private void drawDashedLine(PdfContentByte cb,
                                float x1, float y1,
                                float x2, float y2) {
        cb.saveState();
        cb.setColorStroke(MID_GREY);
        cb.setLineWidth(0.8f);
        cb.setLineDash(4f, 4f, 0f);
        cb.moveTo(x1, y1);
        cb.lineTo(x2, y2);
        cb.stroke();
        cb.restoreState();
    }

    private byte[] generateQrBytes(String content, int size) {
        if (content == null || content.isBlank()) {
            log.error("❌ Cannot generate QR code — content is empty/null");
            return null;
        }
        log.info("🔲 Generating QR code: contentLength={}, size={}px", content.length(), size);
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 1);

            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints);

            BufferedImage image = MatrixToImageWriter.toBufferedImage(matrix);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", baos);
            log.info("✅ QR code generated: {} bytes", baos.size());
            return baos.toByteArray();

        } catch (WriterException | IOException e) {
            log.error("❌ Failed to generate QR code: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Strip trailing garbage like "- true", "- IN_PERSON" that can appear
     * if attendanceMode or boolean flags get concatenated into the ticket name.
     */
    private String cleanTicketType(String raw) {
        if (raw == null || raw.isBlank()) return "—";
        // Remove trailing "- <word>" pattern (case-insensitive)
        return raw.replaceAll("\\s*-\\s*\\w+$", "").trim();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object o) {
        if (o instanceof Map) return (Map<String, Object>) o;
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castList(Object o) {
        if (o instanceof List) return (List<Map<String, Object>>) o;
        return List.of();
    }

    private String str(Map<String, Object> map, String key) {
        if (map == null) return "";
        Object v = map.get(key);
        return v != null ? v.toString() : "";
    }

    private String resolveBookingRef(Map<String, Object> eventData) {
        Map<String, Object> booking = castMap(eventData.get("booking"));
        if (booking != null) {
            String id = str(booking, "id");
            if (!id.isBlank()) return id;
        }
        return "—";
    }

    private void initFonts() {
        if (fontsInitialized) return;
        fontTitleLarge   = new Font(Font.FontFamily.HELVETICA, 22, Font.BOLD,   BRAND_DARK);
        fontTitleMedium  = new Font(Font.FontFamily.HELVETICA, 15, Font.BOLD,   BRAND_DARK);
        fontLabel        = new Font(Font.FontFamily.HELVETICA,  7, Font.BOLD,   MID_GREY);
        fontValue        = new Font(Font.FontFamily.HELVETICA, 11, Font.BOLD,   BRAND_DARK);
        fontSmall        = new Font(Font.FontFamily.HELVETICA,  8, Font.NORMAL, WHITE);
        fontBrand        = new Font(Font.FontFamily.HELVETICA, 11, Font.BOLD,   WHITE);
        fontTicketNumber = new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD,   BRAND_PURPLE);
        fontsInitialized = true;
    }
}