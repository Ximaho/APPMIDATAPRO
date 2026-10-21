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
import java.time.LocalDate;
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

    private final PetitionService petitionService;

    public PdfReportService(PetitionService petitionService) {
        this.petitionService = petitionService;
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
     * @param contact        correo y celular para las notificaciones del derecho de petición (no se muestran
     *                       en el informe)
     * @return bytes del PDF generado
     */
    public byte[] generateReport(CreditAnalysis analysis, AnalysisResult result, String userName,
                                 String identification, Map<String, String> answers,
                                 PetitionService.Contact contact) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 48, 48, 40, 56);
        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            BrandPageEvent pageEvent = new BrandPageEvent();
            writer.setPageEvent(pageEvent);
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

            if (!result.legacy()) {
                addSectionTitle(document, "Aspectos favorables");
                addBulletList(document, result.favorableAspects(), "No se identificaron aspectos favorables verificables.");
            }

            addSectionTitle(document, result.legacy() ? "Problemas detectados" : "Alertas principales");
            addBulletList(document, result.problems(), "No se detectaron alertas relevantes.");

            if (!result.legacy()) {
                addSectionTitle(document, "Datos por verificar");
                addBulletList(document, result.dataToVerify(), "No hay datos pendientes de verificar.");
            }

            addSectionTitle(document, "Acciones recomendadas");
            addBulletList(document, result.recommendations(), "Sin recomendaciones adicionales.");

            java.util.List<AnalysisResult.EntityAction> petitions = result.petitions();
            if (!result.entityActions().isEmpty()) {
                addSectionTitle(document, "Pasos a seguir con cada entidad");
                addEntityActions(document, result.entityActions(), petitions);
            }
            if (!petitions.isEmpty()) {
                addSectionTitle(document, "Formatos incluidos");
                Paragraph intro = new Paragraph("Al final de este informe encontrará "
                        + (petitions.size() == 1 ? "un derecho de petición personalizado" : petitions.size()
                        + " derechos de petición personalizados") + ". Para usarlos:", font(regular, 10.5f, NEGRO));
                intro.setSpacingAfter(4f);
                document.add(intro);
                addNumberedList(document, PETITION_INSTRUCTIONS);
            }

            if (answers != null && !answers.isEmpty()) {
                addSectionTitle(document, "Sus respuestas");
                PdfPTable table = new PdfPTable(new float[]{3f, 1.3f});
                table.setWidthPercentage(100);
                answers.forEach((question, answer) -> {
                    table.addCell(infoCell(question, font(regular, 9.5f, NEGRO)));
                    table.addCell(infoCell(answer.replace("; ", "\n"), font(bold, 9.5f, NARANJA)));
                });
                document.add(table);
            }

            Paragraph notice = new Paragraph(AnalysisResult.DISCLAIMER, font(regular, 8.5f, GRIS));
            notice.setSpacingBefore(24f);
            notice.setAlignment(Element.ALIGN_JUSTIFIED);
            document.add(notice);
            Paragraph aiNotice = new Paragraph("El diagnóstico y las acciones recomendadas son generados con apoyo de "
                    + "inteligencia artificial y no constituyen asesoría legal ni financiera.", font(regular, 8f, GRIS));
            aiNotice.setSpacingBefore(4f);
            document.add(aiNotice);

            LocalDate today = analysis.getCreatedAt() != null ? analysis.getCreatedAt().toLocalDate() : LocalDate.now();
            for (AnalysisResult.EntityAction petition : petitions) {
                document.newPage();
                pageEvent.plainPages = true;
                petitionService.write(document, userName, identification, contact, petition, today);
            }
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
        Paragraph title = new Paragraph("Su radiografía crediticia", font(bold, 12, CREMA));
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
        Paragraph range = new Paragraph("sobre " + AnalysisResult.MAX_SCORE, font(regular, 9, GRIS));
        range.setAlignment(Element.ALIGN_CENTER);
        scoreCell.addElement(range);
        box.addCell(scoreCell);

        PdfPCell levelCell = new PdfPCell();
        levelCell.setBackgroundColor(DURAZNO);
        levelCell.setBorder(Rectangle.NO_BORDER);
        levelCell.setPadding(14f);
        levelCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        levelCell.addElement(new Paragraph(result.legacy() ? "PUNTAJE ESTIMADO (METODOLOGÍA ANTERIOR)"
                : "PUNTAJE ESTIMADO DE RADIOGRAFÍA CREDITICIA", font(regular, 9, NARANJA)));
        levelCell.addElement(new Paragraph("Clasificación interna: " + result.classification(), font(bold, 14, NARANJA)));
        if (result.confidence() != null) {
            levelCell.addElement(new Paragraph("Nivel de confianza: " + result.confidence(), font(regular, 10, NEGRO)));
            if (result.lowConfidence()) {
                levelCell.addElement(new Paragraph("Estimación con información limitada: revise los datos por verificar.",
                        font(regular, 9, GRIS)));
            }
        }
        box.addCell(levelCell);

        box.setSpacingAfter(6f);
        document.add(box);
    }

    /** Cómo usar el derecho de petición incluido como anexo. */
    static final java.util.List<String> PETITION_INSTRUCTIONS = java.util.List.of(
            "Imprímalo y complete a mano los espacios en blanco (ciudad, lugar de expedición de la cédula y domicilio).",
            "Revise que el nombre, la cédula y la entidad sean correctos, y fírmelo.",
            "Adjunte una copia de su cédula de ciudadanía.",
            "Radíquelo por los canales oficiales de la entidad (oficina, formulario de PQRS en su página web o correo de "
                    + "servicio al cliente) y guarde el número o la constancia de radicado.",
            "La entidad tiene 15 días hábiles para responder un reclamo (art. 16, Ley 1266 de 2008), prorrogables por 8 "
                    + "días hábiles más. Si no responde de fondo, puede acudir a la Superintendencia Financiera o a la "
                    + "Superintendencia de Industria y Comercio, o presentar una acción de tutela por habeas data.");

    private void addEntityActions(Document document, java.util.List<AnalysisResult.EntityAction> actions,
                                  java.util.List<AnalysisResult.EntityAction> petitions) throws DocumentException {
        for (AnalysisResult.EntityAction action : actions) {
            PdfPTable box = new PdfPTable(1);
            box.setWidthPercentage(100);
            PdfPCell cell = new PdfPCell();
            cell.setBackgroundColor(DURAZNO);
            cell.setBorder(Rectangle.NO_BORDER);
            cell.setPadding(10f);
            cell.addElement(new Paragraph(action.entity(), font(bold, 12, NARANJA)));
            if (!action.obligationReference().isEmpty()) {
                cell.addElement(new Paragraph("Obligación: " + action.obligationReference(), font(regular, 9, GRIS)));
            }
            if (!action.issue().isEmpty()) {
                Paragraph issue = new Paragraph(action.issue(), font(regular, 10, NEGRO));
                issue.setSpacingAfter(4f);
                cell.addElement(issue);
            }
            int n = 1;
            for (String step : action.steps()) {
                Paragraph p = new Paragraph(n++ + ". " + step, font(regular, 10, NEGRO));
                p.setIndentationLeft(6f);
                p.setLeading(13.5f);
                cell.addElement(p);
            }
            if (action.petitionRecommended()) {
                Paragraph annex = new Paragraph("Incluye derecho de petición: Anexo " + (petitions.indexOf(action) + 1),
                        font(bold, 9.5f, NARANJA));
                annex.setSpacingBefore(4f);
                cell.addElement(annex);
            }
            box.addCell(cell);
            box.setSpacingAfter(8f);
            document.add(box);
        }
    }

    private void addNumberedList(Document document, java.util.List<String> items) throws DocumentException {
        int n = 1;
        for (String item : items) {
            Paragraph p = new Paragraph(n++ + ". " + item, font(regular, 10, NEGRO));
            p.setIndentationLeft(8f);
            p.setLeading(14f);
            p.setSpacingAfter(2f);
            document.add(p);
        }
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
        /** Las páginas de los derechos de petición van en hoja blanca, sin marca. */
        boolean plainPages;

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            if (plainPages) {
                return;
            }
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
