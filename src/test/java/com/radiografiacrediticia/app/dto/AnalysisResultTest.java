package com.radiografiacrediticia.app.dto;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisResultTest {

    @Test
    void factorWeightsAddUpTo950AndTo100Percent() {
        assertThat(Arrays.stream(ScoreFactor.values()).mapToDouble(ScoreFactor::maxPoints).sum()).isEqualTo(950.0);
        assertThat(Arrays.stream(ScoreFactor.values()).mapToInt(ScoreFactor::weightPercent).sum()).isEqualTo(100);
    }

    @Test
    void scoreIsTheSumOfFactorPoints() {
        assertThat(TestResults.withRating(100).estimatedScore()).isEqualTo(950);
        assertThat(TestResults.withRating(0).estimatedScore()).isZero();

        // Puntos por factor (redondeados a un decimal): 228 + 118,8 + 85,5 + 66,5 + 30,4 + 59,9 + 23,8 = 612,9
        AnalysisResult r = AnalysisResult.fromModel(true, "alta", List.of(
                in("comportamiento_historico_pago", 80), in("estado_gravedad_obligaciones", 50),
                in("endeudamiento_utilizacion_cupos", 60), in("evolucion_reciente", 70),
                in("antiguedad_historia", 40), in("estado_cumplimiento_productos", 90),
                in("composicion_productos", 50)), "x", null, null, null, null);
        assertThat(r.estimatedScore()).isEqualTo(613);
        assertThat(r.classification()).isEqualTo("Perfil con factores por mejorar");
        assertThat(r.confidence()).isEqualTo("Alta");
        assertThat(r.factors().get(1).pointsText()).isEqualTo("118,8");
        assertThat(r.factors().get(0).maxPointsText()).isEqualTo("285");
    }

    @Test
    void ratingsAreClampedAndUnknownOrRepeatedFactorsIgnored() {
        AnalysisResult r = AnalysisResult.fromModel(true, "baja", List.of(
                in("comportamiento_historico_pago", 150), in("comportamiento_historico_pago", 0),
                in("factor_inventado", 100), in("estado_gravedad_obligaciones", -20),
                in("endeudamiento_utilizacion_cupos", 100), in("evolucion_reciente", 100),
                in("antiguedad_historia", 100), in("estado_cumplimiento_productos", 100),
                in("composicion_productos", 100)), "x", null, null, null, null);
        assertThat(r.factors()).hasSize(7);
        assertThat(r.factors().get(0).rating()).isEqualTo(100);
        assertThat(r.factors().get(1).rating()).isZero();
        // 950 − 237,5 (factor en 0) = 712,5 → 713
        assertThat(r.estimatedScore()).isEqualTo(713);
    }

    @Test
    void missingFactorsOrModelRefusalMeanNoScore() {
        AnalysisResult incomplete = AnalysisResult.fromModel(true, "media",
                List.of(in("comportamiento_historico_pago", 90)), "x", null, null, null, null);
        assertThat(incomplete.scoreCalculable()).isFalse();
        assertThat(incomplete.estimatedScore()).isZero();
        assertThat(incomplete.classification()).isEqualTo("Puntaje no calculable");
        assertThat(incomplete.dataToVerify()).anyMatch(s -> s.contains("no evaluó todos los factores"));

        AnalysisResult refused = AnalysisResult.fromModel(false, "baja", List.of(), "x", null, null,
                List.of("Capturas legibles del reporte"), null);
        assertThat(refused.scoreCalculable()).isFalse();
        assertThat(refused.dataToVerify()).containsExactly("Capturas legibles del reporte");
    }

    @Test
    void classificationFollowsMethodologyRanges() {
        assertThat(AnalysisResult.classificationFor(399)).isEqualTo("Perfil con alertas críticas");
        assertThat(AnalysisResult.classificationFor(400)).isEqualTo("Perfil con alertas importantes");
        assertThat(AnalysisResult.classificationFor(550)).isEqualTo("Perfil con factores por mejorar");
        assertThat(AnalysisResult.classificationFor(650)).isEqualTo("Perfil en evolución favorable");
        assertThat(AnalysisResult.classificationFor(750)).isEqualTo("Perfil favorable");
        assertThat(AnalysisResult.classificationFor(850)).isEqualTo("Perfil con indicadores favorables");
    }

    private static AnalysisResult.FactorInput in(String key, int rating) {
        return new AnalysisResult.FactorInput(key, rating, true, "j");
    }
}
