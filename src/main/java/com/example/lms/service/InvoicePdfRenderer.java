package com.example.lms.service;

import com.example.lms.entity.InvoiceEntity;
import com.example.lms.util.InvoiceLabels;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * Draws an invoice PDF from the saved invoice row only (nothing is read from the setup or the course again).
 * Same layout as the Invoice Setup preview: logo + title + number on top, customer, line item, totals, messages.
 *
 * The built-in PDF fonts are used, so characters outside Latin-1 (for example Tamil names) are not drawn.
 */
@Component
public class InvoicePdfRenderer {

    private static final Logger log = LoggerFactory.getLogger(InvoicePdfRenderer.class);

    private static final Color TEXT = new Color(0x33, 0x33, 0x33);
    private static final Color MUTED = new Color(0x6f, 0x6f, 0x6f);
    private static final Color LINE = new Color(0xdd, 0xdd, 0xdd);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    public byte[] render(InvoiceEntity inv) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 48, 48, 48, 48);
        try {
            PdfWriter.getInstance(doc, out);
            doc.addTitle(label(inv, "invoice") + " " + inv.getInvoiceNumber());
            doc.open();

            doc.add(header(inv));
            doc.add(spacer(18));
            doc.add(customer(inv));
            doc.add(spacer(22));
            doc.add(lineItem(inv));
            doc.add(totals(inv));
            addNote(doc, inv.getTaxMessage(), 11, MUTED, Font.NORMAL);
            addNote(doc, inv.getThankYouMessage(), 12, TEXT, Font.BOLD);

            doc.close();
        } catch (RuntimeException e) {   // DocumentException is a RuntimeException in OpenPDF 2.x
            if (doc.isOpen()) {
                doc.close();
            }
            throw new IllegalStateException("Could not create the invoice PDF.", e);
        }
        return out.toByteArray();
    }

    // ------------------------------------------------------------------
    // sections
    // ------------------------------------------------------------------

    /** Left: logo + title. Right: invoice number + date. */
    private PdfPTable header(InvoiceEntity inv) throws DocumentException {
        PdfPTable t = table(new float[]{3f, 2f});

        PdfPCell left = noBorder();
        Image logo = logoOf(inv);
        if (logo != null) {
            left.addElement(logo);
        }
        Paragraph title = new Paragraph(label(inv, "invoice"), font(26, Font.BOLD, TEXT));
        title.setSpacingBefore(logo != null ? 8 : 0);
        left.addElement(title);
        t.addCell(left);

        PdfPCell right = noBorder();
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);
        right.addElement(rightAligned(label(inv, "invoiceNumber").toUpperCase(Locale.ENGLISH), 9, Font.NORMAL, MUTED));
        right.addElement(rightAligned(inv.getInvoiceNumber(), 14, Font.BOLD, TEXT));
        Paragraph date = rightAligned(label(inv, "invoiceDate") + ": " + inv.getIssuedAt().format(DATE), 10, Font.NORMAL, MUTED);
        date.setSpacingBefore(6);
        right.addElement(date);
        t.addCell(right);
        return t;
    }

    private PdfPTable customer(InvoiceEntity inv) throws DocumentException {
        PdfPTable t = table(new float[]{1f});
        PdfPCell c = noBorder();
        c.addElement(new Paragraph(label(inv, "customerDetails"), font(10, Font.BOLD, MUTED)));

        c.addElement(new Paragraph(nz(inv.getBillingName()), font(12, Font.BOLD, TEXT)));
        if (!nz(inv.getCompanyName()).isEmpty()) {
            c.addElement(new Paragraph(inv.getCompanyName(), font(11, Font.NORMAL, TEXT)));
        }
        c.addElement(new Paragraph(nz(inv.getBillingEmail()), font(11, Font.NORMAL, TEXT)));
        c.addElement(new Paragraph(nz(inv.getAddress()), font(11, Font.NORMAL, TEXT)));
        String cityLine = String.join(", ", nonBlank(inv.getCity(), inv.getState(), inv.getPostalCode()));
        c.addElement(new Paragraph(cityLine, font(11, Font.NORMAL, TEXT)));
        c.addElement(new Paragraph(nz(inv.getCountry()), font(11, Font.NORMAL, TEXT)));
        if (!nz(inv.getVatNumber()).isEmpty()) {
            Paragraph vat = new Paragraph("VAT: " + inv.getVatNumber(), font(10, Font.NORMAL, MUTED));
            vat.setSpacingBefore(6);
            c.addElement(vat);
        }
        t.addCell(c);
        return t;
    }

    /** One row: the course (quantity 1). */
    private PdfPTable lineItem(InvoiceEntity inv) throws DocumentException {
        PdfPTable t = table(new float[]{5f, 1.2f, 1.8f, 1.8f});
        t.addCell(headCell(label(inv, "description"), Element.ALIGN_LEFT));
        t.addCell(headCell(label(inv, "quantity"), Element.ALIGN_RIGHT));
        t.addCell(headCell(label(inv, "unitPrice"), Element.ALIGN_RIGHT));
        t.addCell(headCell(label(inv, "price"), Element.ALIGN_RIGHT));

        t.addCell(bodyCell(inv.getCourseName(), Element.ALIGN_LEFT, Font.BOLD));
        t.addCell(bodyCell("1", Element.ALIGN_RIGHT, Font.NORMAL));
        t.addCell(bodyCell(money(inv, inv.getSubtotal()), Element.ALIGN_RIGHT, Font.NORMAL));
        t.addCell(bodyCell(money(inv, inv.getSubtotal()), Element.ALIGN_RIGHT, Font.NORMAL));
        return t;
    }

    /** Subtotal, tax (only when there is tax), total. Aligned to the right half of the page. */
    private PdfPTable totals(InvoiceEntity inv) throws DocumentException {
        PdfPTable t = table(new float[]{1f, 1f});
        t.setSpacingBefore(10);
        PdfPCell blank = noBorder();

        PdfPTable rows = table(new float[]{3f, 2f});
        addTotalRow(rows, label(inv, "subtotal"), money(inv, inv.getSubtotal()), Font.NORMAL, false);
        if (inv.getTaxAmount().signum() > 0) {
            String taxLabel = label(inv, inv.isPricesIncludeTax() ? "taxIncluded" : "tax")
                    + " (" + inv.getTaxRate().stripTrailingZeros().toPlainString() + "%)";
            addTotalRow(rows, taxLabel, money(inv, inv.getTaxAmount()), Font.NORMAL, false);
        }
        addTotalRow(rows, label(inv, "totalAmount"), money(inv, inv.getTotal()), Font.BOLD, true);

        t.addCell(blank);
        PdfPCell right = noBorder();
        right.addElement(rows);
        t.addCell(right);
        return t;
    }

    private void addNote(Document doc, String text, float size, Color color, int style) throws DocumentException {
        if (nz(text).trim().isEmpty()) {
            return;
        }
        Paragraph p = new Paragraph(text.trim(), font(size, style, color));
        p.setSpacingBefore(24);
        doc.add(p);
    }

    // ------------------------------------------------------------------
    // small helpers
    // ------------------------------------------------------------------

    private void addTotalRow(PdfPTable t, String label, String value, int style, boolean topLine) {
        PdfPCell l = noBorder();
        l.setPhrase(new Phrase(label, font(11, style, TEXT)));
        PdfPCell r = noBorder();
        r.setHorizontalAlignment(Element.ALIGN_RIGHT);
        r.setPhrase(new Phrase(value, font(11, style, TEXT)));
        if (topLine) {
            for (PdfPCell c : List.of(l, r)) {
                c.setBorder(Rectangle.TOP);
                c.setBorderColor(LINE);
            }
        }
        t.addCell(l);
        t.addCell(r);
    }

    private PdfPCell headCell(String text, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text, font(10, Font.BOLD, MUTED)));
        c.setHorizontalAlignment(align);
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColor(LINE);
        c.setPadding(6);
        return c;
    }

    private PdfPCell bodyCell(String text, int align, int style) {
        PdfPCell c = new PdfPCell(new Phrase(nz(text), font(11, style, TEXT)));
        c.setHorizontalAlignment(align);
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColor(LINE);
        c.setPadding(8);
        return c;
    }

    private PdfPCell noBorder() {
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.NO_BORDER);
        c.setPadding(4);
        return c;
    }

    private PdfPTable table(float[] widths) throws DocumentException {
        PdfPTable t = new PdfPTable(widths.length);
        t.setWidthPercentage(100);
        t.setWidths(widths);
        return t;
    }

    private Paragraph spacer(float height) {
        Paragraph p = new Paragraph(" ");
        p.setLeading(height);
        return p;
    }

    private Paragraph rightAligned(String text, float size, int style, Color color) {
        Paragraph p = new Paragraph(text, font(size, style, color));
        p.setAlignment(Element.ALIGN_RIGHT);
        return p;
    }

    private Font font(float size, int style, Color color) {
        return FontFactory.getFont(FontFactory.HELVETICA, size, style, color);
    }

    /** The admin's own label when set, otherwise the default one. */
    private String label(InvoiceEntity inv, String key) {
        String custom = inv.getLabels() == null ? null : inv.getLabels().get(key);
        if (custom != null && !custom.isBlank()) {
            return custom.trim();
        }
        return InvoiceLabels.DEFAULTS.getOrDefault(key, key);
    }

    private String money(InvoiceEntity inv, BigDecimal amount) {
        String symbol = "USD".equals(inv.getCurrency()) ? "$" : inv.getCurrency() + " ";
        return String.format(Locale.US, "%s%,.2f", symbol, amount);
    }

    /** The logo is a "data:image/png|jpeg;base64,..." URL. A broken logo is skipped, never fails the invoice. */
    private Image logoOf(InvoiceEntity inv) {
        String logo = inv.getLogo();
        if (logo == null || logo.isBlank()) {
            return null;
        }
        try {
            int comma = logo.indexOf(',');
            Image image = Image.getInstance(Base64.getDecoder().decode(logo.substring(comma + 1)));
            image.scaleToFit(130, 56);
            return image;
        } catch (Exception e) {
            log.warn("Invoice logo skipped, invoiceId={}: {}", inv.getId(), e.getMessage());
            return null;
        }
    }

    private static List<String> nonBlank(String... values) {
        return java.util.Arrays.stream(values).filter(v -> v != null && !v.isBlank()).map(String::trim).toList();
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }
}