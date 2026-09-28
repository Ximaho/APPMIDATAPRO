package com.midatacredito.app.service;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.List;
import com.lowagie.text.ListItem;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import com.midatacredito.app.dto.AnalysisResult;
import com.midatacredito.app.model.CreditAnalysis;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;

/**
 * Genera en memoria el informe PDF de un análisis crediticio usando OpenPDF.
 * Estructura: encabezado, datos del usuario, puntaje estimado con nivel de riesgo,
 * resumen, problemas detectados, recomendaciones y aviso legal.
 */
@Service
public class PdfReportService {

    private static final Color PRIMARY = new Color(0x1E, 0x3A, 0x8A);
    private static final Color MUTED = new Color(0x6B, 0x72, 0x80);
    private static final Color LIGHT_BG = new Color(0xF3, 0xF4, 0xF6);
    private static final Color DANGER = new Color(0xB9, 0x1C, 0x1C);
    private static final Color SUCCESS = new Color(0x04, 0x78, 0x57);

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, Color.WHITE);
    private final Font subtitleFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.WHITE);
    private final Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, PRIMARY);
    private final Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 10.5f, Color.DARK_GRAY);
    private final Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, MUTED);
    private final Font smallFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8.5f, MUTED);

    /**
     * @param analysis  entidad con los metadatos del análisis
     * @param result    resultado estructurado (puntaje, resumen, problemas, recomendaciones)
     * @param userName  nombre a mostrar en el informe
     * @return bytes del PDF generado
     */
    public byte[] generateReport(CreditAnalysis analysis, AnalysisResult result, String userName) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 48, 48, 48, 56);
        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            writer.setPageEvent(new FooterEvent());
            document.addTitle("Informe de análisis crediticio");
            document.addAuthor("MiDataCrédito Analyzer");
            document.addCreator("MiDataCrédito Analyzer");
            document.open();

            addHeader(document, analysis);
            addUserInfo(document, analysis, userName);
            addScore(document, result);

            addSectionTitle(document, "Resumen del diagnóstico");
            Paragraph summary = new Paragraph(result.summary(), bodyFont);
            summary.setLeading(15f);
            summary.setAlignment(Element.ALIGN_JUSTIFIED);
            document.add(summary);

            addSectionTitle(document, "Problemas detectados");
            addBulletList(document, result.problems(), DANGER, "No se detectaron problemas relevantes.");

            addSectionTitle(document, "Recomendaciones de mejora");
            addBulletList(document, result.recommendations(), SUCCESS, "Sin recomendaciones adicionales.");

            addSectionTitle(document, "Actividad económica reportada");
            Paragraph activity = new Paragraph(analysis.getActivityDescription(), bodyFont);
            activity.setLeading(14f);
            document.add(activity);

            Paragraph disclaimer = new Paragraph(
                    "Aviso: este informe es una estimación generada por inteligencia artificial a partir de la "
                            + "información suministrada por el usuario. No es un reporte oficial de DataCrédito Experian "
                            + "ni constituye asesoría financiera o legal. El puntaje real puede diferir.",
                    smallFont);
            disclaimer.setSpacingBefore(24f);
            document.add(disclaimer);
        } catch (DocumentException e) {
            throw new IllegalStateException("No fue posible generar el informe PDF", e);
        } finally {
            if (document.isOpen()) {
                document.close();
            }
        }
        return out.toByteArray();
    }

    private void addHeader(Document document, CreditAnalysis analysis) throws DocumentException {
        PdfPTable header = new PdfPTable(1);
        header.setWidthPercentage(100);

        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(PRIMARY);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(16f);
        cell.addElement(new Paragraph("Informe de Análisis Crediticio", titleFont));
        String date = analysis.getCreatedAt() != null ? analysis.getCreatedAt().format(DATE_FORMAT) : "";
        cell.addElement(new Paragraph("Consulta N.º " + analysis.getId() + "  ·  " + date, subtitleFont));
        header.addCell(cell);

        header.setSpacingAfter(14f);
        document.add(header);
    }

    private void addUserInfo(Document document, CreditAnalysis analysis, String userName) throws DocumentException {
        PdfPTable info = new PdfPTable(new float[]{1.2f, 3f});
        info.setWidthPercentage(100);
        addInfoRow(info, "Titular", userName);
        addInfoRow(info, "Archivo analizado", analysis.getImageFileName());
        addInfoRow(info, "Modelo de IA", analysis.getModelUsed());
        info.setSpacingAfter(12f);
        document.add(info);
    }

    private void addInfoRow(PdfPTable table, String label, String value) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setBorder(Rectangle.BOTTOM);
        labelCell.setBorderColor(LIGHT_BG);
        labelCell.setPadding(5f);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value == null ? "-" : value, bodyFont));
        valueCell.setBorder(Rectangle.BOTTOM);
        valueCell.setBorderColor(LIGHT_BG);
        valueCell.setPadding(5f);
        table.addCell(valueCell);
    }

    private void addScore(Document document, AnalysisResult result) throws DocumentException {
        Color scoreColor = scoreColor(result.estimatedScore());

        PdfPTable box = new PdfPTable(new float[]{1f, 2f});
        box.setWidthPercentage(100);

        PdfPCell scoreCell = new PdfPCell();
        scoreCell.setBackgroundColor(LIGHT_BG);
        scoreCell.setBorder(Rectangle.NO_BORDER);
        scoreCell.setPadding(14f);
        scoreCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        Paragraph score = new Paragraph(String.valueOf(result.estimatedScore()),
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 36, scoreColor));
        score.setAlignment(Element.ALIGN_CENTER);
        scoreCell.addElement(score);
        Paragraph range = new Paragraph("de " + AnalysisResult.MIN_SCORE + " a " + AnalysisResult.MAX_SCORE, labelFont);
        range.setAlignment(Element.ALIGN_CENTER);
        scoreCell.addElement(range);
        box.addCell(scoreCell);

        PdfPCell levelCell = new PdfPCell();
        levelCell.setBackgroundColor(LIGHT_BG);
        levelCell.setBorder(Rectangle.NO_BORDER);
        levelCell.setPadding(14f);
        levelCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        levelCell.addElement(new Paragraph("Puntaje crediticio estimado", labelFont));
        levelCell.addElement(new Paragraph(result.riskLevel(),
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, scoreColor)));
        levelCell.addElement(new Paragraph(
                result.problems().size() + " problema(s) detectado(s) · "
                        + result.recommendations().size() + " recomendación(es)", bodyFont));
        box.addCell(levelCell);

        box.setSpacingAfter(6f);
        document.add(box);
    }

    private void addSectionTitle(Document document, String title) throws DocumentException {
        Paragraph p = new Paragraph(title, sectionFont);
        p.setSpacingBefore(14f);
        p.setSpacingAfter(6f);
        document.add(p);
    }

    private void addBulletList(Document document, java.util.List<String> items, Color bulletColor, String emptyText)
            throws DocumentException {
        if (items.isEmpty()) {
            document.add(new Paragraph(emptyText, bodyFont));
            return;
        }
        List list = new List(List.UNORDERED);
        list.setListSymbol(new Chunk("•  ", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, bulletColor)));
        list.setIndentationLeft(8f);
        for (String item : items) {
            ListItem li = new ListItem(item, bodyFont);
            li.setLeading(14f);
            li.setSpacingAfter(3f);
            list.add(li);
        }
        document.add(list);
    }

    private static Color scoreColor(int score) {
        if (score >= 700) {
            return SUCCESS;
        } else if (score >= 550) {
            return new Color(0xB4, 0x53, 0x09);
        }
        return DANGER;
    }

    /** Pie de página con numeración. */
    private class FooterEvent extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            Phrase footer = new Phrase("MiDataCrédito Analyzer  ·  Página " + writer.getPageNumber(), smallFont);
            ColumnText.showTextAligned(cb, Element.ALIGN_CENTER, footer,
                    (document.left() + document.right()) / 2, document.bottom() - 24, 0);
        }
    }
}
