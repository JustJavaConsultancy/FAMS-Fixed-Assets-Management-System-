package com.example.fams.reports;

import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Renders a {@link ReportData} set to Excel (.xlsx via Apache POI) or PDF (via iText 7).
 * Both formats use the same column order and formatting so the two exports stay in sync.
 */
@Service
public class ReportExportService {

    private static final String[] HEADERS = {
            "Asset Code", "Asset Name", "Category", "Department", "Custodian",
            "Acquisition Date", "Acquisition Cost", "Accumulated Depreciation",
            "Net Book Value", "Status"
    };

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private String currency(BigDecimal value) {
        NumberFormat formatter = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-NG"));
        formatter.setMaximumFractionDigits(2);
        return formatter.format(value == null ? BigDecimal.ZERO : value);
    }

    private String date(LocalDate value) {
        return value == null ? "" : value.format(DATE_FMT);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /** Ordered display values for a single data row (parity between Excel and PDF). */
    private String[] rowValues(ReportRow row) {
        return new String[]{
                nullToEmpty(row.assetCode()),
                nullToEmpty(row.assetName()),
                nullToEmpty(row.category()),
                nullToEmpty(row.department()),
                nullToEmpty(row.custodian()),
                date(row.acquisitionDate()),
                currency(row.acquisitionCost()),
                currency(row.accumulatedDepreciation()),
                currency(row.netBookValue()),
                nullToEmpty(row.status())
        };
    }

    // ------------------------------------------------------------------ Excel

    public byte[] toExcel(ReportData data) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet(safeSheetName(data.getReportName()));

            // Title + generated date rows
            CellStyle titleStyle = workbook.createCellStyle();
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleStyle.setFont(titleFont);

            Row titleRow = sheet.createRow(0);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue(nullToEmpty(data.getReportName()));
            titleCell.setCellStyle(titleStyle);

            Row metaRow = sheet.createRow(1);
            metaRow.createCell(0).setCellValue("Generated: "
                    + (data.getGeneratedDate() == null ? "" : data.getGeneratedDate().format(DATE_FMT)));

            // Header row
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            int headerRowIdx = 3;
            Row header = sheet.createRow(headerRowIdx);
            for (int c = 0; c < HEADERS.length; c++) {
                Cell cell = header.createCell(c);
                cell.setCellValue(HEADERS[c]);
                cell.setCellStyle(headerStyle);
            }

            // Data rows
            int rowIdx = headerRowIdx + 1;
            List<ReportRow> rows = data.getRows();
            if (rows == null || rows.isEmpty()) {
                Row empty = sheet.createRow(rowIdx);
                empty.createCell(0).setCellValue("No records found for the selected criteria.");
            } else {
                for (ReportRow r : rows) {
                    Row dataRow = sheet.createRow(rowIdx++);
                    String[] values = rowValues(r);
                    for (int c = 0; c < values.length; c++) {
                        dataRow.createCell(c).setCellValue(values[c]);
                    }
                }
                // Totals row
                Row totals = sheet.createRow(rowIdx + 1);
                Cell totalsLabel = totals.createCell(0);
                totalsLabel.setCellValue("TOTALS (" + data.getTotalRecords() + " records)");
                totalsLabel.setCellStyle(headerStyle);
                totals.createCell(6).setCellValue(currency(data.getTotalValue()));
                totals.createCell(7).setCellValue(currency(data.getTotalDepreciation()));
                totals.createCell(8).setCellValue(currency(data.getTotalNetBookValue()));
            }

            for (int c = 0; c < HEADERS.length; c++) {
                sheet.autoSizeColumn(c);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to generate Excel export", ex);
        }
    }

    private String safeSheetName(String name) {
        String base = (name == null || name.isBlank()) ? "Report" : name.trim();
        // Excel sheet names: max 31 chars, no : \ / ? * [ ]
        base = base.replaceAll("[:\\\\/?*\\[\\]]", " ");
        return base.length() > 31 ? base.substring(0, 31) : base;
    }

    // -------------------------------------------------------------------- PDF

    public byte[] toPdf(ReportData data) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(out);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf, PageSize.A4.rotate());

            document.add(new Paragraph(nullToEmpty(data.getReportName()))
                    .setBold()
                    .setFontSize(16));
            document.add(new Paragraph("Generated: "
                    + (data.getGeneratedDate() == null ? "" : data.getGeneratedDate().format(DATE_FMT)))
                    .setFontSize(9));

            float[] columnWidths = {8, 14, 10, 10, 12, 9, 9, 10, 9, 8};
            Table table = new Table(UnitValue.createPercentArray(columnWidths)).useAllAvailableWidth();

            for (String h : HEADERS) {
                table.addHeaderCell(new com.itextpdf.layout.element.Cell()
                        .add(new Paragraph(h).setBold().setFontSize(8)));
            }

            List<ReportRow> rows = data.getRows();
            if (rows == null || rows.isEmpty()) {
                com.itextpdf.layout.element.Cell emptyCell = new com.itextpdf.layout.element.Cell(1, HEADERS.length)
                        .add(new Paragraph("No records found for the selected criteria.").setFontSize(9));
                emptyCell.setTextAlignment(TextAlignment.CENTER);
                table.addCell(emptyCell);
            } else {
                for (ReportRow r : rows) {
                    for (String value : rowValues(r)) {
                        table.addCell(new com.itextpdf.layout.element.Cell()
                                .add(new Paragraph(value).setFontSize(8)));
                    }
                }
            }

            document.add(table);

            if (rows != null && !rows.isEmpty()) {
                document.add(new Paragraph(
                        "Totals — Records: " + data.getTotalRecords()
                                + "   Acquisition Cost: " + currency(data.getTotalValue())
                                + "   Accumulated Depreciation: " + currency(data.getTotalDepreciation())
                                + "   Net Book Value: " + currency(data.getTotalNetBookValue()))
                        .setBold()
                        .setFontSize(9));
            }

            document.close();
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to generate PDF export", ex);
        }
    }
}
