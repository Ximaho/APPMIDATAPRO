package com.radiografiacrediticia.app.dto;

import org.springframework.util.MultiValueMap;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Cuestionario alternativo para quien no tiene acceso a las capturas de su reporte.
 * Las respuestas se envían a Claude como texto y se guardan con el análisis.
 */
public final class Questionnaire {

    /** Prefijo de los parámetros del formulario: {@code q_<id>}. */
    public static final String PARAM_PREFIX = "q_";

    /** Longitud máxima de la respuesta abierta. */
    public static final int MAX_OPEN_ANSWER = 1000;

    /** Separador de las opciones elegidas en una pregunta de selección múltiple. */
    private static final String MULTI_SEPARATOR = "; ";

    public enum Type {
        /** Una sola opción (botones de radio). */
        SINGLE,
        /** Varias opciones (casillas). */
        MULTIPLE,
        /** Texto libre, opcional. */
        OPEN
    }

    public record Question(String id, String text, Type type, List<String> options) {
        public String paramName() {
            return PARAM_PREFIX + id;
        }

        public boolean isOpen() {
            return type == Type.OPEN;
        }

        public boolean isMultiple() {
            return type == Type.MULTIPLE;
        }

        /** Las preguntas cerradas son obligatorias cuando no se suben capturas; la abierta no. */
        public boolean isRequired() {
            return type != Type.OPEN;
        }
    }

    public static final List<Question> QUESTIONS = List.of(
            new Question("situacion",
                    "¿Cuál de estas situaciones describe mejor su vida crediticia actual?",
                    Type.MULTIPLE, List.of(
                    "Tengo obligaciones en mora y reportes negativos.",
                    "Tengo obligaciones en mora, pero no sé si estoy reportado.",
                    "Pagué una o varias deudas, pero no sé cómo aparecen en mi historial.",
                    "Tengo reportes negativos que considero incorrectos o injustos.",
                    "Aparecen obligaciones que no reconozco.",
                    "No tengo reportes negativos que conozca y quiero fortalecer mi perfil.",
                    "No sé cómo está mi historial crediticio.")),
            new Question("estado_obligaciones",
                    "¿Cuál es el estado de sus obligaciones actualmente?",
                    Type.MULTIPLE, List.of(
                    "Tengo obligaciones al día.",
                    "Tengo una o varias obligaciones en mora.",
                    "Pagué todas mis obligaciones.",
                    "Pagué algunas, pero otras siguen en mora.",
                    "No tengo obligaciones crediticias actualmente.",
                    "No estoy seguro(a).")),
            new Question("revisar",
                    "¿Qué situaciones desea que revisemos?",
                    Type.MULTIPLE, List.of(
                    "Reportes negativos por mora.",
                    "Obligaciones pagadas que aún aparecen reportadas.",
                    "Obligaciones castigadas.",
                    "Deudas que no reconozco o posible suplantación.",
                    "Información incorrecta o desactualizada.",
                    "Antigüedad de los reportes o deudas.",
                    "Embargos o procesos de cobro.",
                    "Quiero mejorar o cuidar mi perfil crediticio.",
                    "No sé qué aparece en mi historial.")),
            new Question("antiguedad_mora",
                    "¿Hace cuánto comenzó la mora más antigua que le preocupa?",
                    Type.SINGLE, List.of(
                    "Hace menos de 6 meses.",
                    "Entre 6 meses y 1 año.",
                    "Entre 1 y 2 años.",
                    "Entre 2 y 5 años.",
                    "Entre 5 y 8 años.",
                    "Hace más de 8 años.",
                    "No conozco la fecha.",
                    "No he tenido obligaciones en mora.")),
            new Question("pagos",
                    "¿Cuál es su situación frente a los pagos?",
                    Type.SINGLE, List.of(
                    "Actualmente pago mis obligaciones a tiempo.",
                    "Tengo retrasos ocasionales, pero estoy al día.",
                    "Tengo obligaciones atrasadas y quiero ponerme al día.",
                    "Quiero negociar o acordar el pago de mis deudas.",
                    "Ya pagué una o varias deudas y necesito revisar mi historial.",
                    "No tengo obligaciones activas.",
                    "No estoy seguro(a).")),
            new Question("informacion",
                    "¿Con qué información cuenta para analizar su caso?",
                    Type.MULTIPLE, List.of(
                    "Tengo capturas recientes de mi historial crediticio.",
                    "Tengo comprobantes de pago o paz y salvos.",
                    "Tengo comunicaciones, reclamaciones o respuestas de entidades.",
                    "Tengo documentos de cobro o procesos judiciales.",
                    "No tengo documentos ni capturas disponibles.",
                    "No sé cómo consultar mi historial crediticio.")),
            new Question("objetivo",
                    "¿Qué desea conseguir principalmente con Radiografía Crediticia?",
                    Type.MULTIPLE, List.of(
                    "Saber qué reportes negativos tengo.",
                    "Identificar si existen fundamentos para solicitar la eliminación de reportes.",
                    "Verificar la actualización de una deuda que ya pagué.",
                    "Revisar si un reporte o una obligación presenta posibles irregularidades.",
                    "Saber cómo pagar o negociar mis deudas y recuperar mi vida crediticia.",
                    "Mejorar mis hábitos y fortalecer mi perfil para futuros créditos.",
                    "Entender mi situación y saber qué pasos seguir.")));

    private Questionnaire() {
    }

    /**
     * Toma del formulario solo las respuestas válidas (pregunta conocida y opción permitida),
     * en el orden del cuestionario. La clave es el texto de la pregunta; en las de selección
     * múltiple las opciones elegidas se unen con "; ".
     */
    public static Map<String, String> extractAnswers(MultiValueMap<String, String> params) {
        Map<String, String> answers = new LinkedHashMap<>();
        selectedById(params).forEach((id, values) -> {
            Question question = byId(id);
            answers.put(question.text(), String.join(MULTI_SEPARATOR, values));
        });
        return answers;
    }

    /**
     * Valores válidos elegidos por id de pregunta (lista vacía si no hay), en el orden de las opciones.
     * Sirve también para volver a marcar el formulario tras un error.
     */
    public static Map<String, List<String>> selectedById(MultiValueMap<String, String> params) {
        Map<String, List<String>> selected = new LinkedHashMap<>();
        for (Question question : QUESTIONS) {
            List<String> raw = params.getOrDefault(question.paramName(), List.of());
            List<String> values;
            if (question.isOpen()) {
                String text = raw.isEmpty() || raw.get(0) == null ? "" : raw.get(0).strip();
                if (text.length() > MAX_OPEN_ANSWER) {
                    text = text.substring(0, MAX_OPEN_ANSWER);
                }
                values = text.isEmpty() ? List.of() : List.of(text);
            } else {
                values = question.options().stream().filter(raw::contains).toList();
                if (!question.isMultiple() && values.size() > 1) {
                    values = values.subList(0, 1);
                }
            }
            if (!values.isEmpty()) {
                selected.put(question.id(), values);
            }
        }
        return selected;
    }

    /** Todas las preguntas obligatorias (las cerradas) tienen respuesta. */
    public static boolean isComplete(Map<String, String> answers) {
        return QUESTIONS.stream()
                .filter(Question::isRequired)
                .allMatch(q -> answers.containsKey(q.text()));
    }

    /** Texto plano "- pregunta respuesta" que se envía a Claude. */
    public static String format(Map<String, String> answers) {
        return answers.entrySet().stream()
                .map(e -> "- " + e.getKey() + " " + e.getValue())
                .collect(Collectors.joining("\n"));
    }

    private static Question byId(String id) {
        return QUESTIONS.stream().filter(q -> q.id().equals(id)).findFirst().orElseThrow();
    }
}
