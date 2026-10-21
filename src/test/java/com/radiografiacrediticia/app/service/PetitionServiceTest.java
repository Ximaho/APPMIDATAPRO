package com.radiografiacrediticia.app.service;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.radiografiacrediticia.app.dto.AnalysisResult;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PetitionServiceTest {

    @Test
    void fillsTemplateWithHolderEntityAndNotificationData() throws Exception {
        PetitionService service = new PetitionService("tramites@radiografia.test", "3001234567");
        AnalysisResult.EntityAction action = new AnalysisResult.EntityAction("Bancolombia", "Mora reportada",
                List.of("Radicar"), true, "Tarjeta de crédito terminada en 1234");

        byte[] pdf = service.generate("Ana Pérez Gómez", "1020304050", action, LocalDate.of(2026, 10, 21));

        String text = allText(pdf).replaceAll("\\s+", " ");
        assertThat(text)
                .contains("21 de octubre de 2026")
                .contains("BANCOLOMBIA")
                .contains("Obligación: Tarjeta de crédito terminada en 1234")
                .contains("Ana Pérez Gómez")
                .contains("1020304050")
                .contains("PETICIONES")
                .contains("SÉPTIMO")
                .contains("Ley 2157 de 2021")
                .contains("tramites@radiografia.test")
                .contains("3001234567");
    }

    @Test
    void leavesBlanksWhenNotificationDataIsNotConfigured() throws Exception {
        PetitionService service = new PetitionService("", "");
        AnalysisResult.EntityAction action = new AnalysisResult.EntityAction("Banco X", "", List.of(), true, "");

        String text = allText(service.generate("Ana", "123456", action, LocalDate.of(2026, 1, 5)));

        assertThat(text).contains("correo electrónico: ____").doesNotContain("Obligación:");
    }

    private static String allText(byte[] pdf) throws Exception {
        PdfReader reader = new PdfReader(pdf);
        PdfTextExtractor extractor = new PdfTextExtractor(reader);
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= reader.getNumberOfPages(); i++) {
            sb.append(extractor.getTextFromPage(i)).append('\n');
        }
        return sb.toString();
    }
}
