package com.midatacredito.app.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Resultado estructurado devuelto por Claude.
 * Los nombres JSON coinciden con el esquema solicitado a la API.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AnalysisResult(
        @JsonProperty("estimated_score") int estimatedScore,
        @JsonProperty("summary") String summary,
        @JsonProperty("problems") List<String> problems,
        @JsonProperty("recommendations") List<String> recommendations) {

    public static final int MIN_SCORE = 150;
    public static final int MAX_SCORE = 950;

    public AnalysisResult {
        estimatedScore = Math.max(MIN_SCORE, Math.min(MAX_SCORE, estimatedScore));
        summary = summary == null ? "" : summary.trim();
        problems = problems == null ? List.of() : List.copyOf(problems);
        recommendations = recommendations == null ? List.of() : List.copyOf(recommendations);
    }

    /** Categoría de riesgo aproximada según el rango del puntaje. */
    public String riskLevel() {
        return riskLevelFor(estimatedScore);
    }

    public static String riskLevelFor(int score) {
        if (score >= 800) {
            return "Riesgo muy bajo";
        } else if (score >= 700) {
            return "Riesgo bajo";
        } else if (score >= 550) {
            return "Riesgo medio";
        } else if (score >= 400) {
            return "Riesgo alto";
        }
        return "Riesgo muy alto";
    }
}
