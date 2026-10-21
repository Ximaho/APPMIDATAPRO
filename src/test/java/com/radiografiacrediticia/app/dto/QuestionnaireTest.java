package com.radiografiacrediticia.app.dto;

import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionnaireTest {

    @Test
    void extractsSingleMultipleAndOpenAnswers() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("q_situacion", "Aparecen obligaciones que no reconozco.");
        params.add("q_situacion", "No sé cómo está mi historial crediticio.");
        params.add("q_revisar", "Embargos o procesos de cobro.");
        params.add("q_revisar", "Deudas que no reconozco o posible suplantación.");
        params.add("q_objetivo", "Respuesta que no existe");
        params.add("q_pagos", "Actualmente pago mis obligaciones a tiempo.");
        params.add("q_pagos", "No estoy seguro(a).");

        Map<String, String> answers = Questionnaire.extractAnswers(params);

        assertThat(answers)
                .containsEntry("¿Cuál de estas situaciones describe mejor su vida crediticia actual?",
                        "Aparecen obligaciones que no reconozco.; No sé cómo está mi historial crediticio.")
                .containsEntry("¿Qué situaciones desea que revisemos?",
                        "Deudas que no reconozco o posible suplantación.; Embargos o procesos de cobro.")
                .doesNotContainKey("¿Qué desea conseguir principalmente con Radiografía Crediticia?")
                // La pregunta 5 es de selección única: se conserva solo la primera opción válida.
                .containsEntry("¿Cuál es su situación frente a los pagos?", "Actualmente pago mis obligaciones a tiempo.");
        assertThat(Questionnaire.isComplete(answers)).isFalse();
    }

    @Test
    void openAnswerIsOptionalForCompleteness() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        Questionnaire.QUESTIONS.stream().filter(Questionnaire.Question::isRequired)
                .forEach(q -> params.add(q.paramName(), q.options().get(0)));

        assertThat(Questionnaire.isComplete(Questionnaire.extractAnswers(params))).isTrue();
    }

    @Test
    void documentQuestionsAreAllPresent() {
        assertThat(Questionnaire.QUESTIONS).hasSize(7).noneMatch(Questionnaire.Question::isOpen);
        // Preguntas 1, 2, 3, 6 y 7 de opción múltiple; 4 y 5 de una sola respuesta.
        assertThat(Questionnaire.QUESTIONS.stream().filter(Questionnaire.Question::isMultiple))
                .extracting(Questionnaire.Question::id)
                .containsExactly("situacion", "estado_obligaciones", "revisar", "informacion", "objetivo");
    }
}
