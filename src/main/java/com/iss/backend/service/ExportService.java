package com.iss.backend.service;

import com.iss.backend.entity.Application;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Downloadable reports. Every list can be downloaded as an Excel workbook or a PDF,
 * built from the same headers and rows so both files always match.
 */
@Service
public class ExportService {

    public static final String[] APPLICATION_HEADERS =
            {"Name", "NIC", "Index Number", "Email", "School", "Stream", "Status"};

    private static final Color MAROON = new Color(0x7b, 0x1e, 0x2e);
    private static final Color STRIPE = new Color(0xfa, 0xf6, 0xee);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a");

    /** One row per application, in the order of APPLICATION_HEADERS. */
    public static List<List<String>> applicationRows(List<Application> apps) {
        return apps.stream().map(app -> List.of(
                text(app.getStudent().getFullName()),
                text(app.getStudent().getNic()),
                text(app.getStudent().getIndexNumber()),
                text(app.getStudent().getEmail()),
                app.getSchool() != null ? text(app.getSchool().getSchoolName()) : "",
                app.getStream() != null ? text(app.getStream().getStreamName()) : "",
                statusLabel(app.getStatus()))).toList();
    }

    /**
     * Builds the download response.
     *
     * @param format   "pdf" for a PDF, anything else for Excel
     * @param baseName file name without extension, e.g. "selected_students"
     */
    public ResponseEntity<byte[]> download(String format, String baseName, String title,
                                           String[] headers, List<List<String>> rows) {
        boolean pdf = "pdf".equalsIgnoreCase(format);
        byte[] body = pdf ? pdf(title, headers, rows) : excel(title, headers, rows);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(baseName + (pdf ? ".pdf" : ".xlsx")).build().toString())
                .contentType(pdf ? MediaType.APPLICATION_PDF
                        : MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }


    // =====================================================
    // EXCEL
    // =====================================================

    public byte[] excel(String title, String[] headers, List<List<String>> rows) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            // Sheet names are limited to 31 characters and some symbols
            Sheet sheet = workbook.createSheet(title.replaceAll("[\\\\/?*\\[\\]:]", " ")
                    .substring(0, Math.min(31, title.length())));

            CellStyle headStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font bold = workbook.createFont();
            bold.setBold(true);
            headStyle.setFont(bold);
            headStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headStyle);
            }

            int rowNum = 1;
            for (List<String> values : rows) {
                Row row = sheet.createRow(rowNum++);
                for (int i = 0; i < values.size(); i++) row.createCell(i).setCellValue(values.get(i));
            }

            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
            sheet.createFreezePane(0, 1);

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Could not create the Excel file", e);
        }
    }


    // =====================================================
    // PDF
    // =====================================================

    public byte[] pdf(String title, String[] headers, List<List<String>> rows) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(headers.length > 5 ? PageSize.A4.rotate() : PageSize.A4, 36, 36, 40, 40);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setPageEvent(new PageNumbers());
            doc.open();

            Paragraph brand = new Paragraph("INTERVIEW SELECTION SYSTEM",
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, MAROON));
            doc.add(brand);

            Paragraph heading = new Paragraph(title, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16));
            heading.setSpacingBefore(2);
            doc.add(heading);

            Paragraph meta = new Paragraph("Generated on " + LocalDateTime.now().format(STAMP)
                    + "   |   " + rows.size() + (rows.size() == 1 ? " record" : " records"),
                    FontFactory.getFont(FontFactory.HELVETICA, 9, Color.GRAY));
            meta.setSpacingAfter(12);
            doc.add(meta);

            PdfPTable table = new PdfPTable(headers.length);
            table.setWidthPercentage(100);
            table.setHeaderRows(1);

            Font headFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, headFont));
                cell.setBackgroundColor(MAROON);
                cell.setPadding(6);
                cell.setBorderColor(MAROON);
                table.addCell(cell);
            }

            Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 9);
            int r = 0;
            for (List<String> values : rows) {
                for (String v : values) {
                    PdfPCell cell = new PdfPCell(new Phrase(v, bodyFont));
                    cell.setPadding(5);
                    cell.setBorderColor(new Color(0xe6, 0xdf, 0xd2));
                    if (r % 2 == 1) cell.setBackgroundColor(STRIPE);
                    table.addCell(cell);
                }
                r++;
            }

            if (rows.isEmpty()) {
                PdfPCell empty = new PdfPCell(new Phrase("No records.", bodyFont));
                empty.setColspan(headers.length);
                empty.setPadding(8);
                empty.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(empty);
            }

            doc.add(table);
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Could not create the PDF file", e);
        }
    }

    /** "Page X" at the bottom of every page. */
    private static class PageNumbers extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            Phrase footer = new Phrase(new Chunk("Page " + writer.getPageNumber(),
                    FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY)));
            com.lowagie.text.pdf.ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_CENTER, footer,
                    (document.left() + document.right()) / 2, document.bottom() - 20, 0);
        }
    }


    // =====================================================
    // HELPERS
    // =====================================================

    private static String text(String s) {
        return s == null ? "" : s;
    }

    /** Same wording as the status badges in the web pages. */
    public static String statusLabel(String status) {
        if (status == null) return "";
        return switch (status) {
            case "APPLIED" -> "Pending";
            case "SHORTLISTED" -> "Shortlisted";
            case "INTERVIEW_SCHEDULED" -> "Interview Scheduled";
            case "INTERVIEWED" -> "Interviewed";
            case "SELECTED" -> "Selected";
            case "NOT_SELECTED" -> "Not Selected";
            case "NOT_QUALIFIED" -> "Not Qualified";
            case "SCHEDULED" -> "Pending";
            case "COMPLETED" -> "Completed";
            default -> status;
        };
    }
}
