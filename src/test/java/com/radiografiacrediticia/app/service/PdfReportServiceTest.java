package com.radiografiacrediticia.app.service;

import com.radiografiacrediticia.app.dto.AnalysisResult;
import com.radiografiacrediticia.app.model.CreditAnalysis;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PdfReportServiceTest {

    @Test
    void generatesValidPdf() {
        CreditAnalysis analysis = new CreditAnalysis();
        analysis.setActivityDescription("Comerciante independiente con ingresos variables.");
        analysis.setImageFileNames("reporte-1.png, reporte-2.png");
        analysis.setImageCount(2);
        analysis.setQuestionnaireJson("{}");
        analysis.setScoreCalculable(true);
        analysis.setModelUsed("claude-sonnet-5-5");

        AnalysisResult result = com.radiografiacrediticia.app.dto.TestResults.withRating(75);

        byte[] pdf = new PdfReportService().generateReport(analysis, result, "Ana Pérez", "1020304050",
                java.util.Map.of("¿Has estado en mora en los últimos 12 meses?", "Sí, pero ya pagué"));

        assertThat(pdf.length).isGreaterThan(1000);
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }
}
