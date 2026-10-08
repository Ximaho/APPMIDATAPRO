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
                .contains("Reglas del formato de respuesta")
                .doesNotContain("<!--")
                .doesNotContain("edita este archivo");
    }

    @Test
    void technicalRulesAreAlwaysIncludedEvenIfFilesAreEdited() {
        PromptLibrary library = new PromptLibrary(
                new ByteArrayResource("Responde como un auditor.".getBytes(StandardCharsets.UTF_8)),
                new ClassPathResource("prompts/no-existe.md"));

        assertThat(library.systemPrompt())
                .startsWith("Responde como un auditor.")
                .contains("estimated_score: entero entre 150 y 950")
                .doesNotContain("<base_de_conocimiento>");
    }
}
