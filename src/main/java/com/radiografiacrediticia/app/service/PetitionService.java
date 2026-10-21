package com.radiografiacrediticia.app.service;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.radiografiacrediticia.app.dto.AnalysisResult.EntityAction;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Genera el derecho de petición dirigido a una entidad, a partir del formato
 * "05. DERECHO DE PETICIÓN (no abogado)". El texto jurídico es fijo; solo se personalizan la fecha,
 * la entidad, el titular, su identificación, la obligación y los datos de notificación (correo y celular
 * de la propia persona). Los datos que
 * la aplicación no conoce (ciudad, lugar de expedición, firma) quedan como espacios para completar a mano.
 */
@Service
public class PetitionService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy",
            new Locale("es", "CO"));
    private static final String BLANK = "____________________";

    private final Font normal = FontFactory.getFont(FontFactory.HELVETICA, 10f, Color.BLACK);
    private final Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10f, Color.BLACK);
    private final Font italic = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8.5f, Color.BLACK);

    /** Datos de notificación de la persona: correo de su cuenta y celular del formulario. */
    public record Contact(String email, String phone) {
    }

    /** Derecho de petición como PDF independiente, listo para imprimir y firmar. */
    public byte[] generate(String holderName, String holderId, Contact contact, EntityAction action, LocalDate date) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 60, 60, 48, 48);
        try {
            PdfWriter.getInstance(document, out);
            document.addTitle("Derecho de petición - " + action.entity());
            document.open();
            write(document, holderName, holderId, contact, action, date);
        } catch (DocumentException e) {
            throw new IllegalStateException("No fue posible generar el derecho de petición", e);
        } finally {
            if (document.isOpen()) {
                document.close();
            }
        }
        return out.toByteArray();
    }

    /** Escribe el derecho de petición en un documento abierto, desde la posición actual. */
    public void write(Document document, String holderName, String holderId, Contact contact, EntityAction action,
                      LocalDate date)
            throws DocumentException {
        add(document, BLANK + ", " + date.format(DATE), normal, Element.ALIGN_LEFT, 18);

        add(document, "Señores", normal, Element.ALIGN_LEFT, 0);
        add(document, action.entity().toUpperCase(new Locale("es", "CO")), bold, Element.ALIGN_LEFT, 0);
        add(document, "Ciudad: " + BLANK, normal, Element.ALIGN_LEFT, 14);

        add(document, "REFERENCIA: Derecho de Petición (Art. 23 C.N.)", bold, Element.ALIGN_LEFT,
                StringUtils.hasText(action.obligationReference()) ? 0 : 14);
        if (StringUtils.hasText(action.obligationReference())) {
            add(document, "Obligación: " + action.obligationReference(), normal, Element.ALIGN_LEFT, 14);
        }

        add(document, mixed(
                bold(orBlank(holderName)),
                text(", identificado(a) con cédula de ciudadanía No. "),
                bold(orBlank(holderId)),
                text(" expedida en " + BLANK + ", domiciliado(a) en " + BLANK + ", obrando en nombre propio, "
                        + "en ejercicio del Derecho de Petición consagrado en el artículo 23 de la Constitución "
                        + "Nacional y los derechos al BUEN NOMBRE, al DEBIDO PROCESO y la LEY DE HABEAS DATA; "
                        + "solicito las siguientes:")), Element.ALIGN_JUSTIFIED, 10);

        add(document, "PETICIONES", bold, Element.ALIGN_CENTER, 10);

        add(document, "PRIMERO: Solicito respetuosamente me informen si poseo actualmente reportes con esta entidad, "
                + "en caso de ser afirmativo informar sobre los siguientes puntos:", bold, Element.ALIGN_JUSTIFIED, 4);
        for (String item : List.of(
                "Especificar si en la actualidad dichas obligaciones, deudas o créditos se encuentran activas o "
                        + "canceladas y desde cuándo.",
                "Establecer la fecha en que se realizó el reporte inicial por parte de esta entidad ante las "
                        + "Centrales de Riesgo.",
                "Informar el saldo de la deuda a la fecha.",
                "Valor inicial de la deuda.",
                "Tasa de interés.",
                "Totalidad de intereses aplicados a la fecha, incluido honorarios.")) {
            Paragraph p = new Paragraph("•  " + item, normal);
            p.setIndentationLeft(18);
            p.setLeading(13f);
            p.setAlignment(Element.ALIGN_JUSTIFIED);
            document.add(p);
        }
        spacer(document, 8);

        add(document, "SEGUNDO: Favor enviar copia auténtica del documento que respalda la obligación a mi cargo, "
                + "en donde sea visible la firma, huella o certificado de autenticidad de ese documento.",
                bold, Element.ALIGN_JUSTIFIED, 8);
        add(document, "TERCERO: Establecer si hubo reporte negativo ante las Centrales de Riesgo, asimismo, la fecha "
                + "del reporte inicial y a cuál central de riesgo se realizó el reporte.", bold, Element.ALIGN_JUSTIFIED, 8);
        add(document, "CUARTO: Copia de la notificación por escrito con la debida firma de recibido o certificado de "
                + "entrega, donde se me informaba que iba a ser reportado a las centrales de riesgo, en cumplimiento de "
                + "la LEY ESTATUTARIA 1266 DE 2008, así:", bold, Element.ALIGN_JUSTIFIED, 4);
        add(document, "“ARTÍCULO 12. REQUISITOS ESPECIALES PARA FUENTES… Las fuentes deberán actualizar mensualmente "
                + "la información suministrada al operador, sin perjuicio de lo dispuesto en el Título III de la presente "
                + "ley… El reporte de información negativa sobre incumplimiento de obligaciones de cualquier naturaleza, "
                + "que hagan las fuentes de información a los operadores de bancos de datos de información financiera, "
                + "crediticia, comercial, de servicios y la proveniente de terceros países, sólo procederá previa "
                + "comunicación al titular de la información, con el fin de que este pueda demostrar o efectuar el pago "
                + "de la obligación, así como controvertir aspectos tales como el monto de la obligación o cuota y la "
                + "fecha de exigibilidad. Dicha comunicación podrá incluirse en los extractos periódicos que las fuentes "
                + "de información envíen a sus clientes… En todo caso, las fuentes de información podrán efectuar el "
                + "reporte de la información transcurridos veinte (20) días calendario siguientes a la fecha de envío "
                + "de la comunicación en la última dirección de domicilio del afectado que se encuentre registrada en "
                + "los archivos de la fuente de la información y sin perjuicio, si es del caso, de dar cumplimiento a la "
                + "obligación de informar al operador, que la información se encuentra en discusión por parte de su "
                + "titular, cuando se haya presentado solicitud de rectificación o actualización y esta aún no haya "
                + "sido resuelta”.", italic, Element.ALIGN_JUSTIFIED, 8);
        add(document, "QUINTO: Copia de la autorización por mí firmada para el reporte a las Centrales de Riesgo.",
                bold, Element.ALIGN_JUSTIFIED, 8);
        add(document, "SEXTO: Si mi reporte negativo se encuentra en las centrales de riesgo por más de 8 años, les "
                + "solicito le sea borrado su historial de conformidad con la Ley 2157 de 2021 “Borrón y Cuenta Nueva”.",
                bold, Element.ALIGN_JUSTIFIED, 4);
        add(document, "“El dato negativo y los datos cuyo contenido haga referencia al tiempo de mora, tipo de cobro, "
                + "estado de la cartera y, en general aquellos datos referentes a una situación de incumplimiento de "
                + "obligaciones caducarán una vez cumplido el término de ocho (8) años, contados a partir del momento en "
                + "que entre en mora la obligación; cumplido este término deberán ser eliminados de la base de datos.”",
                italic, Element.ALIGN_JUSTIFIED, 8);
        add(document, "SÉPTIMO: Si el reporte negativo que realizó la empresa NO cumple a cabalidad con la legalidad "
                + "sustentada en cualquiera de las seis peticiones anteriores, fundamentadas en la normatividad vigente, "
                + "les solicito ordenar a la central de riesgo donde me encuentre reportado eliminar el historial negativo "
                + "de sus bases de datos en el término de 48 horas, so pena de iniciar demanda por los perjuicios y daños "
                + "causados.", bold, Element.ALIGN_JUSTIFIED, 8);

        add(document, "“DERECHO A LA INFORMACIÓN-Procedimiento y divulgación de datos de usuarios de servicios "
                + "financieros… Los datos que registran, procesan y divulgan las centrales de riesgo, sobre el "
                + "comportamiento de los usuarios del sistema financiero, es de interés general, porque el crédito “es un "
                + "factor fundamental en la vida económica, particularmente en el sistema capitalista (..) y este requiere "
                + "de la confianza del público para operar normalmente”. Fundamentada la garantía de conocer y hacer "
                + "conocer los hábitos de pago de los usuarios del crédito en el interés general, que comporta la "
                + "estabilidad del sistema financiero, surge una primera limitación de dicha garantía en función de los "
                + "datos que resultan efectivamente evaluables en el señalamiento de políticas individuales de crédito. "
                + "En este sentido, en la sentencia SU-082 de 1995 esta Corte sostuvo que la información que registran "
                + "procesan y divulgan las centrales de riesgo debe ser completa, para que pueda ser tenida como veraz, "
                + "de modo que “[e]n lo atinente a un crédito, por ejemplo, un banco no daría información completa, si se "
                + "limitara a expresar que el deudor ya no debe nada y ocultara el hecho de que el pago se obtuvo merced a "
                + "un proceso de ejecución, o que la obligación permaneció en mora por mucho tiempo. Igualmente, no sería "
                + "completa si no se informara desde qué fecha el cliente está a paz y salvo”.", italic,
                Element.ALIGN_JUSTIFIED, 6);
        add(document, "“PRINCIPIO DE LA BUENA FE-Relaciones económicas entre entidades de crédito y sus clientes… Las "
                + "entidades de crédito y sus clientes se encuentran vinculados por relaciones económicas fundadas en el "
                + "postulado de la buena fe y en el deber de respetar los derechos ajenos y no abusar de los propios, "
                + "conforme lo ordenan los artículos 83 y 95 de la Carta Política. Debe entenderse, entonces, que las "
                + "personas que entablan relaciones de crédito y simultáneamente autorizan develar aspectos de su "
                + "intimidad, que incluso pueden perjudicarlas, confían en que su acreedor divulgará la información sólo "
                + "cuando las circunstancias efectivamente lo justifiquen, y en que sus facultades de intervenir en la "
                + "recolección, tratamiento y circulación de los datos serán respetadas en las diversas etapas del "
                + "proceso informático, de manera que sus actividades económicas no sufrirán tropiezos por la "
                + "divulgación sorpresiva de datos adversos”.", italic, Element.ALIGN_JUSTIFIED, 6);
        add(document, "“CENTRAL DE RIESGOS-Datos negativos no constituyen sanción… La información atinente a la "
                + "atención de sus obligaciones por parte de los usuarios del crédito, registrada en las centrales de "
                + "riesgo, no constituye una sanción, sino una herramienta que dicho sector requiere para evaluar las "
                + "condiciones del crédito, partiendo del conocimiento real del riesgo que el solicitante podría "
                + "representar para el prestamista, conforme a sus hábitos de pago”.", italic, Element.ALIGN_JUSTIFIED, 12);

        add(document, "ANEXO", bold, Element.ALIGN_LEFT, 2);
        add(document, "- Copia de mi cédula de ciudadanía.", normal, Element.ALIGN_LEFT, 12);

        add(document, "NOTIFICACIONES", bold, Element.ALIGN_LEFT, 2);
        add(document, "Las notificaciones las recibiré al correo electrónico: "
                + orBlank(contact == null ? null : contact.email())
                + " y al número de celular: " + orBlank(contact == null ? null : contact.phone()) + ".",
                normal, Element.ALIGN_JUSTIFIED, 14);

        // Despedida y firma juntas: si no caben en la página, pasan completas a la siguiente.
        PdfPTable closing = new PdfPTable(1);
        closing.setWidthPercentage(100);
        closing.setKeepTogether(true);
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(0);
        cell.addElement(paragraph(new Phrase("Gracias por su colaboración.", normal), Element.ALIGN_LEFT, 4));
        cell.addElement(paragraph(new Phrase("De ustedes,", normal), Element.ALIGN_LEFT, 30));
        cell.addElement(paragraph(new Phrase("___________________________________________", normal), Element.ALIGN_LEFT, 0));
        cell.addElement(paragraph(new Phrase(orBlank(holderName), bold), Element.ALIGN_LEFT, 0));
        cell.addElement(paragraph(new Phrase("C.C. " + orBlank(holderId) + " de " + BLANK, normal), Element.ALIGN_LEFT, 0));
        closing.addCell(cell);
        document.add(closing);
    }

    private static String orBlank(String value) {
        return StringUtils.hasText(value) ? value.trim() : BLANK;
    }

    private Chunk text(String s) {
        return new Chunk(s, normal);
    }

    private Chunk bold(String s) {
        return new Chunk(s, bold);
    }

    private static Phrase mixed(Chunk... chunks) {
        Phrase phrase = new Phrase();
        for (Chunk c : chunks) {
            phrase.add(c);
        }
        return phrase;
    }

    private static void add(Document document, String text, Font font, int align, float spacingAfter)
            throws DocumentException {
        add(document, new Phrase(text, font), align, spacingAfter);
    }

    private static void add(Document document, Phrase phrase, int align, float spacingAfter) throws DocumentException {
        document.add(paragraph(phrase, align, spacingAfter));
    }

    private static Paragraph paragraph(Phrase phrase, int align, float spacingAfter) {
        Paragraph p = new Paragraph(phrase);
        p.setAlignment(align);
        p.setLeading(13f);
        p.setSpacingAfter(spacingAfter);
        return p;
    }

    private static void spacer(Document document, float height) throws DocumentException {
        Paragraph p = new Paragraph(" ");
        p.setLeading(height);
        document.add(p);
    }
}
