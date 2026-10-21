package com.radiografiacrediticia.app.dto;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Resultado de una radiografía. El puntaje no lo escribe la IA: se calcula aquí a partir de la
 * valoración (0 a 100) que la IA asigna a cada uno de los siete factores, para que la suma, la
 * clasificación y los puntos por factor siempre sean coherentes.
 *
 * @param estimatedScore   puntaje de 0 a 950
 * @param confidence       "Alta", "Media" o "Baja" (null en radiografías antiguas)
 * @param factors          desglose por factor (vacío en radiografías antiguas)
 * @param legacy           radiografía generada con la metodología anterior (sin desglose)
 */
public record AnalysisResult(
        int estimatedScore,
        String confidence,
        List<FactorScore> factors,
        String summary,
        List<String> favorableAspects,
        List<String> problems,
        List<String> dataToVerify,
        List<String> recommendations,
        boolean legacy) {

    public static final int MIN_SCORE = 0;
    public static final int MAX_SCORE = 950;

    /** Aviso obligatorio de la metodología (sección 9). */
    public static final String DISCLAIMER = "El puntaje presentado es una estimación interna elaborada por "
            + "Radiografía Crediticia con fines orientativos, a partir de la información disponible y de una "
            + "metodología propia. No corresponde al puntaje oficial de Datacrédito, TransUnion u otra central de "
            + "riesgo, ni reproduce sus algoritmos. No constituye una probabilidad validada de incumplimiento ni "
            + "garantiza la aprobación de productos financieros. Su precisión depende de la integridad, actualidad "
            + "y calidad de la información analizada.";

    public AnalysisResult {
        estimatedScore = Math.max(MIN_SCORE, Math.min(MAX_SCORE, estimatedScore));
        summary = summary == null ? "" : summary.trim();
        factors = factors == null ? List.of() : List.copyOf(factors);
        favorableAspects = clean(favorableAspects);
        problems = clean(problems);
        dataToVerify = clean(dataToVerify);
        recommendations = clean(recommendations);
    }

    /** Valoración de un factor y sus puntos (máximo del factor × valoración ÷ 100). */
    public record FactorScore(ScoreFactor factor, int rating, boolean verifiable, String justification) {
        public FactorScore {
            rating = Math.max(0, Math.min(100, rating));
            justification = justification == null ? "" : justification.trim();
        }

        public double points() {
            return Math.round(factor.maxPoints() * rating / 10.0) / 10.0;
        }

        /** Puntos con coma decimal, como en la metodología (p. ej. "213,8"). */
        public String pointsText() {
            return formatPoints(points());
        }

        public String maxPointsText() {
            return formatPoints(factor.maxPoints());
        }
    }

    /** Respuesta de la IA por factor, antes del cálculo. */
    public record FactorInput(String factor, Integer rating, Boolean verifiable, String justification) {
    }

    /** Valoración neutra para un factor que la IA no evaluó: no se penaliza la falta de datos. */
    public static final int NEUTRAL_RATING = 50;

    /**
     * Construye el resultado a partir de la respuesta de la IA, sumando los puntos de los factores.
     * El puntaje siempre se calcula: si la IA no valoró algún factor, se le asigna una valoración neutra
     * marcada como no verificable (la metodología pide no penalizar la ausencia de información), y la
     * incertidumbre se expresa en el nivel de confianza y en los datos por verificar.
     */
    public static AnalysisResult fromModel(String confidence, List<FactorInput> inputs,
                                           String summary, List<String> favorable, List<String> problems,
                                           List<String> toVerify, List<String> recommendations) {
        Map<ScoreFactor, FactorScore> byFactor = new EnumMap<>(ScoreFactor.class);
        if (inputs != null) {
            for (FactorInput in : inputs) {
                if (in == null || in.rating() == null) {
                    continue;
                }
                ScoreFactor.fromKey(in.factor()).ifPresent(f -> byFactor.putIfAbsent(f,
                        new FactorScore(f, in.rating(), !Boolean.FALSE.equals(in.verifiable()), in.justification())));
            }
        }
        for (ScoreFactor f : ScoreFactor.values()) {
            byFactor.computeIfAbsent(f, k -> new FactorScore(k, NEUTRAL_RATING, false,
                    "No evaluado por falta de información; se asigna una valoración neutra."));
        }
        List<FactorScore> factors = new ArrayList<>(byFactor.values());
        factors.sort(Comparator.comparing(FactorScore::factor));

        int score = (int) Math.round(factors.stream().mapToDouble(FactorScore::points).sum());
        return new AnalysisResult(score, normalizeConfidence(confidence), factors, summary,
                favorable, problems, toVerify, recommendations, false);
    }

    /** Radiografías generadas antes de la metodología por factores (solo puntaje y textos). */
    public static AnalysisResult legacy(int score, String summary, List<String> problems, List<String> recommendations) {
        return new AnalysisResult(score, null, List.of(), summary, List.of(), problems, List.of(),
                recommendations, true);
    }

    /** Clasificación interna según el rango del puntaje (sección 2 de la metodología). */
    public String classification() {
        return classificationFor(estimatedScore);
    }

    /** La estimación se hizo con información limitada (confianza baja). */
    public boolean lowConfidence() {
        return "Baja".equals(confidence);
    }

    public static String classificationFor(int score) {
        if (score >= 850) {
            return "Perfil con indicadores favorables";
        } else if (score >= 750) {
            return "Perfil favorable";
        } else if (score >= 650) {
            return "Perfil en evolución favorable";
        } else if (score >= 550) {
            return "Perfil con factores por mejorar";
        } else if (score >= 400) {
            return "Perfil con alertas importantes";
        }
        return "Perfil con alertas críticas";
    }

    /** Nivel visual para la interfaz: "bueno", "medio" o "bajo". */
    public String tone() {
        return estimatedScore >= 650 ? "bueno" : estimatedScore >= 550 ? "medio" : "bajo";
    }

    public int scorePercent() {
        return estimatedScore * 100 / MAX_SCORE;
    }

    private static String normalizeConfidence(String confidence) {
        if (confidence == null || confidence.isBlank()) {
            return null;
        }
        String c = confidence.trim().toLowerCase(Locale.ROOT);
        return switch (c) {
            case "alta" -> "Alta";
            case "media" -> "Media";
            case "baja" -> "Baja";
            default -> null;
        };
    }

    private static List<String> clean(List<String> items) {
        return items == null ? List.of()
                : items.stream().filter(s -> s != null && !s.isBlank()).map(String::trim).toList();
    }

    static String formatPoints(double points) {
        return points == Math.rint(points)
                ? String.valueOf((long) points)
                : String.format(new Locale("es", "CO"), "%.1f", points);
    }
}
