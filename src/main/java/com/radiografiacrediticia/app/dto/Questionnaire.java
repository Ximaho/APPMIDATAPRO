package com.radiografiacrediticia.app.dto;

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

    public record Question(String id, String text, List<String> options) {
        public String paramName() {
            return PARAM_PREFIX + id;
        }
    }

    public static final List<Question> QUESTIONS = List.of(
            new Question("reportes_negativos",
                    "¿Tienes actualmente reportes negativos en centrales de riesgo?",
                    List.of("Sí", "No", "No sé")),
            new Question("moras",
                    "¿Has estado en mora en los últimos 12 meses?",
                    List.of("No", "Sí, pero ya pagué", "Sí, y sigo en mora")),
            new Question("cobro",
                    "¿Tienes alguna deuda castigada, en cobro jurídico o con una casa de cobranza?",
                    List.of("No", "Sí", "No sé")),
            new Question("productos",
                    "¿Cuántos productos de crédito activos tienes (tarjetas, préstamos, celular a crédito…)?",
                    List.of("Ninguno", "1 a 2", "3 a 5", "Más de 5")),
            new Question("uso_tarjetas",
                    "¿Qué parte del cupo de tus tarjetas de crédito usas normalmente?",
                    List.of("No tengo tarjetas", "Menos del 30 %", "Entre 30 % y 70 %", "Más del 70 %")),
            new Question("solicitudes",
                    "¿Cuántas veces has solicitado crédito en los últimos 6 meses?",
                    List.of("Ninguna", "1 a 2", "3 o más")),
            new Question("antiguedad",
                    "¿Hace cuánto tienes tu primer producto financiero?",
                    List.of("Nunca he tenido", "Menos de 1 año", "1 a 3 años", "Más de 3 años")),
            new Question("ingresos",
                    "¿Cómo recibes principalmente tus ingresos?",
                    List.of("Empleado con contrato", "Independiente", "Pensionado", "Sin ingresos fijos")));

    private Questionnaire() {
    }

    /**
     * Toma del formulario solo las respuestas válidas (pregunta conocida y opción permitida),
     * en el orden del cuestionario. La clave es el texto de la pregunta.
     */
    public static Map<String, String> extractAnswers(Map<String, String> params) {
        Map<String, String> answers = new LinkedHashMap<>();
        for (Question question : QUESTIONS) {
            String value = params.get(question.paramName());
            if (value != null && question.options().contains(value)) {
                answers.put(question.text(), value);
            }
        }
        return answers;
    }

    /** Valores elegidos por id de pregunta, para volver a marcar el formulario tras un error. */
    public static Map<String, String> selectedById(Map<String, String> params) {
        Map<String, String> selected = new LinkedHashMap<>();
        for (Question question : QUESTIONS) {
            String value = params.get(question.paramName());
            if (value != null && question.options().contains(value)) {
                selected.put(question.id(), value);
            }
        }
        return selected;
    }

    public static boolean isComplete(Map<String, String> answers) {
        return answers.size() == QUESTIONS.size();
    }

    /** Texto plano "- pregunta: respuesta" que se envía a Claude. */
    public static String format(Map<String, String> answers) {
        return answers.entrySet().stream()
                .map(e -> "- " + e.getKey() + " " + e.getValue())
                .collect(Collectors.joining("\n"));
    }
}
