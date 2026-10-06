package com.radiografiacrediticia.app.service;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.List;
import com.lowagie.text.ListItem;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import com.radiografiacrediticia.app.dto.AnalysisResult;
import com.radiografiacrediticia.app.model.CreditAnalysis;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Genera en memoria el informe PDF de una radiografía con la identidad visual de la marca:
 * fondo crema, encabezado naranja con el logotipo y tipografía Chopin.
 */
@Service
public class PdfReportService {

    private static final Color NARANJA = new Color(0xF6, 0x66, 0x4C);
    private static final Color CREMA = new Color(0xF1, 0xE8, 0xE1);
    private static final Color DURAZNO = new Color(0xF6, 0xE2, 0xD1);
    private static final Color NEGRO = Color.BLACK;
    private static final Color GRIS = new Color(0x5C, 0x52, 0x4D);

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final BaseFont regular;
    private final BaseFont bold;
    private final byte[] wordmark;

    public PdfReportService() {
        this.regular = loadFont("static/fonts/Chopin-Medium.otf");
        this.bold = loadFont("static/fonts/Chopin-ExtraBold.otf");
        this.wordmark = readResource("static/img/logo-wordmark.png");
    }

    /**
     * @param analysis       entidad con los metadatos del análisis
     * @param result         resultado estructurado (puntaje, resumen, problemas, plan de acción)
     * @param userName       nombre a mostrar
     * @param identification documento de identidad del titular (puede ser nulo)
     * @param answers        respuestas del cuestionario (puede estar vacío)
     * @return bytes del PDF generado
     */
    public byte[] generateReport(CreditAnalysis analysis, AnalysisResult result, String userName,
                                 String identification, Map<String, String> answers) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 48, 48, 40, 56);
        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            writer.setPageEvent(new BrandPageEvent());
            document.addTitle("Radiografía crediticia");
            document.addAuthor("Radiografía Crediticia");
            document.addCreator("Radiografía Crediticia");
            document.open();

            addHeader(document, analysis);
            addUserInfo(document, analysis, userName, identification);
            addScore(document, result);

            addSectionTitle(document, "Diagnóstico");
            Paragraph summary = new Paragraph(result.summary(), font(regular, 10.5f, NEGRO));
            summary.setLeading(15f);
            summary.setAlignment(Element.ALIGN_JUSTIFIED);
            document.add(summary);

            addSectionTitle(document, "Problemas detectados");
            addBulletList(document, result.problems(), "No se detectaron problemas relevantes.");

            addSectionTitle(document, "Tu plan de acción");
            addBulletList(document, result.recommendations(), "Sin recomendaciones adicionales.");

            addSectionTitle(document, "Lo que nos contaste");
            Paragraph activity = new Paragraph(analysis.getActivityDescription(), font(regular, 10f, NEGRO));
            activity.setLeading(14f);
            document.add(activity);

            if (answers != null && !answers.isEmpty()) {
                addSectionTitle(document, "Tus respuestas");
                PdfPTable table = new PdfPTable(new float[]{3f, 1.3f});
                table.setWidthPercentage(100);
                answers.forEach((question, answer) -> {
                    table.addCell(infoCell(question, font(regular, 9.5f, NEGRO)));
                    table.addCell(infoCell(answer, font(bold, 9.5f, NARANJA)));
                });
                document.add(table);
            }

            Paragraph disclaimer = new Paragraph(
                    "Aviso: esta radiografía es una estimación generada por inteligencia artificial a partir de la "
                            + "información suministrada por el usuario. No es un reporte oficial de DataCrédito Experian "
                            + "ni de TransUnion, ni constituye asesoría financiera o legal. El puntaje real puede diferir.",
                    font(regular, 8f, GRIS));
            disclaimer.setSpacingBefore(24f);
            document.add(disclaimer);
        } catch (DocumentException | IOException e) {
            throw new IllegalStateException("No fue posible generar el informe PDF", e);
        } finally {
            if (document.isOpen()) {
                document.close();
            }
        }
        return out.toByteArray();
    }

    private void addHeader(Document document, CreditAnalysis analysis) throws DocumentException, IOException {
        PdfPTable header = new PdfPTable(new float[]{1.6f, 1f});
        header.setWidthPercentage(100);

        Image logo = Image.getInstance(wordmark);
        logo.scaleToFit(210, 60);
        PdfPCell logoCell = new PdfPCell(logo, false);
        logoCell.setBackgroundColor(NARANJA);
        logoCell.setBorder(Rectangle.NO_BORDER);
        logoCell.setPadding(16f);
        logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        header.addCell(logoCell);

        PdfPCell meta = new PdfPCell();
        meta.setBackgroundColor(NARANJA);
        meta.setBorder(Rectangle.NO_BORDER);
        meta.setPadding(16f);
        meta.setVerticalAlignment(Element.ALIGN_MIDDLE);
        Paragraph title = new Paragraph("Tu radiografía crediticia", font(bold, 12, CREMA));
        title.setAlignment(Element.ALIGN_RIGHT);
        meta.addElement(title);
        String date = analysis.getCreatedAt() != null ? analysis.getCreatedAt().format(DATE_FORMAT) : "";
        Paragraph sub = new Paragraph("N.º " + analysis.getId() + "  ·  " + date, font(regular, 9, CREMA));
        sub.setAlignment(Element.ALIGN_RIGHT);
        meta.addElement(sub);
        header.addCell(meta);

        header.setSpacingAfter(14f);
        document.add(header);
    }

    private void addUserInfo(Document document, CreditAnalysis analysis, String userName, String identification)
            throws DocumentException {
        PdfPTable info = new PdfPTable(new float[]{1.2f, 3f});
        info.setWidthPercentage(100);
        addInfoRow(info, "Titular", userName);
        addInfoRow(info, "Identificación", identification);
        int count = analysis.getImageCount() == null ? 0 : analysis.getImageCount();
        java.util.List<String> sources = new java.util.ArrayList<>();
        if (count > 0) {
            sources.add(count + " captura(s): " + analysis.getImageFileNames());
        }
        if (analysis.getQuestionnaireJson() != null) {
            sources.add("cuestionario");
        }
        addInfoRow(info, "Fuentes", String.join(" + ", sources));
        info.setSpacingAfter(12f);
        document.add(info);
    }

    private void addInfoRow(PdfPTable table, String label, String value) {
        table.addCell(infoCell(label, font(bold, 9.5f, NARANJA)));
        table.addCell(infoCell(value == null || value.isBlank() ? "-" : value, font(regular, 10, NEGRO)));
    }

    private PdfPCell infoCell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(DURAZNO);
        cell.setBorderWidth(1f);
        cell.setPadding(6f);
        return cell;
    }

    private void addScore(Document document, AnalysisResult result) throws DocumentException {
        PdfPTable box = new PdfPTable(new float[]{1f, 2f});
        box.setWidthPercentage(100);

        PdfPCell scoreCell = new PdfPCell();
        scoreCell.setBackgroundColor(DURAZNO);
        scoreCell.setBorder(Rectangle.NO_BORDER);
        scoreCell.setPadding(14f);
        Paragraph score = new Paragraph(String.valueOf(result.estimatedScore()), font(bold, 38, NARANJA));
        score.setAlignment(Element.ALIGN_CENTER);
        scoreCell.addElement(score);
        Paragraph range = new Paragraph("de " + AnalysisResult.MIN_SCORE + " a " + AnalysisResult.MAX_SCORE,
                font(regular, 9, GRIS));
        range.setAlignment(Element.ALIGN_CENTER);
        scoreCell.addElement(range);
        box.addCell(scoreCell);

        PdfPCell levelCell = new PdfPCell();
        levelCell.setBackgroundColor(DURAZNO);
        levelCell.setBorder(Rectangle.NO_BORDER);
        levelCell.setPadding(14f);
        levelCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        levelCell.addElement(new Paragraph("PUNTAJE ESTIMADO", font(regular, 9, NARANJA)));
        levelCell.addElement(new Paragraph(result.riskLevel(), font(bold, 17, NARANJA)));
        levelCell.addElement(new Paragraph(
                result.problems().size() + " problema(s) detectado(s) · "
                        + result.recommendations().size() + " paso(s) en tu plan de acción",
                font(regular, 10, NEGRO)));
        box.addCell(levelCell);

        box.setSpacingAfter(6f);
        document.add(box);
    }

    private void addSectionTitle(Document document, String title) throws DocumentException {
        Paragraph p = new Paragraph(title, font(bold, 13, NARANJA));
        p.setSpacingBefore(14f);
        p.setSpacingAfter(6f);
        document.add(p);
    }

    private void addBulletList(Document document, java.util.List<String> items, String emptyText)
            throws DocumentException {
        if (items.isEmpty()) {
            document.add(new Paragraph(emptyText, font(regular, 10.5f, NEGRO)));
            return;
        }
        List list = new List(List.UNORDERED);
        list.setListSymbol(new Chunk("•  ", font(bold, 11, NARANJA)));
        list.setIndentationLeft(8f);
        for (String item : items) {
            ListItem li = new ListItem(item, font(regular, 10.5f, NEGRO));
            li.setLeading(14f);
            li.setSpacingAfter(3f);
            list.add(li);
        }
        document.add(list);
    }

    private static Font font(BaseFont base, float size, Color color) {
        return new Font(base, size, Font.NORMAL, color);
    }

    private static BaseFont loadFont(String path) {
        try {
            byte[] bytes = readResource(path);
            String name = path.substring(path.lastIndexOf('/') + 1);
            return BaseFont.createFont(name, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, bytes, null);
        } catch (DocumentException | IOException e) {
            throw new IllegalStateException("No se pudo cargar la fuente " + path, e);
        }
    }

    private static byte[] readResource(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("No se encontró el recurso " + path, e);
        }
    }

    /** Fondo crema en cada página y pie con numeración. */
    private class BrandPageEvent extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte under = writer.getDirectContentUnder();
            under.saveState();
            under.setColorFill(CREMA);
            Rectangle page = document.getPageSize();
            under.rectangle(0, 0, page.getWidth(), page.getHeight());
            under.fill();
            under.restoreState();

            Phrase footer = new Phrase("Radiografía Crediticia  ·  Página " + writer.getPageNumber(),
                    font(regular, 8, NARANJA));
            ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_CENTER, footer,
                    (document.left() + document.right()) / 2, document.bottom() - 24, 0);
        }
    }
}
