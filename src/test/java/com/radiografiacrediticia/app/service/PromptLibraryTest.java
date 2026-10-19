package com.radiografiacrediticia.app.service;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class PromptLibraryTest {

    @Test
    void buildsPromptFromEditableFilesWithoutEditorComments() {
        String prompt = new PromptLibrary().systemPrompt();

        assertThat(prompt)
                .contains("analista senior de riesgo crediticio")
                .contains("<base_de_conocimiento>")
                .contains("Ley 1266 de 2008")
                .contains("<documento nombre=\"02-metodologia-puntaje.md\">")
                .contains("ARTÍCULO 13. Permanencia de la información")
                .contains("Juzgado 803 Civil Municipal Transitorio de Medellín")
                .contains("Comportamiento histórico de pago")
                .contains("no mencione un puntaje total ni una categoría")
                .contains("Reglas del formato de respuesta")
                .contains("Dirígete siempre a la persona de \"usted\"")
                .doesNotContain("<!--")
                .doesNotContain("edita este archivo");
    }

    @Test
    void technicalRulesAreAlwaysIncludedEvenIfFilesAreEdited() {
        PromptLibrary library = new PromptLibrary(
                new ByteArrayResource("Responde como un auditor.".getBytes(StandardCharsets.UTF_8)),
                java.util.List.of(new ClassPathResource("prompts/no-existe.md")));

        assertThat(library.systemPrompt())
                .startsWith("Responde como un auditor.")
                .contains("entregue los siete factores")
                .doesNotContain("<base_de_conocimiento>");
    }
}
