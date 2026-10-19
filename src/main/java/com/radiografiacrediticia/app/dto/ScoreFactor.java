package com.radiografiacrediticia.app.dto;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Los siete factores del puntaje interno de Radiografía Crediticia, con sus puntos máximos
 * (metodología en prompts/conocimiento/02-metodologia-puntaje.md). La suma de los máximos es 950.
 */
public enum ScoreFactor {

    COMPORTAMIENTO_PAGO("comportamiento_historico_pago", "Comportamiento histórico de pago", 285.0),
    ESTADO_OBLIGACIONES("estado_gravedad_obligaciones", "Estado y gravedad de las obligaciones", 237.5),
    ENDEUDAMIENTO("endeudamiento_utilizacion_cupos", "Endeudamiento y utilización de cupos", 142.5),
    EVOLUCION_RECIENTE("evolucion_reciente", "Evolución reciente del comportamiento", 95.0),
    ANTIGUEDAD("antiguedad_historia", "Antigüedad de la historia crediticia", 76.0),
    CUMPLIMIENTO_PRODUCTOS("estado_cumplimiento_productos", "Estado y cumplimiento de productos", 66.5),
    COMPOSICION_PRODUCTOS("composicion_productos", "Composición de productos crediticios", 47.5);

    private final String key;
    private final String label;
    private final double maxPoints;

    ScoreFactor(String key, String label, double maxPoints) {
        this.key = key;
        this.label = label;
        this.maxPoints = maxPoints;
    }

    /** Identificador que usa la IA en la respuesta JSON. */
    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    public double maxPoints() {
        return maxPoints;
    }

    /** Porcentaje del total (p. ej. 30 para 285 de 950). */
    public int weightPercent() {
        return (int) Math.round(maxPoints * 100 / AnalysisResult.MAX_SCORE);
    }

    public static List<String> keys() {
        return Arrays.stream(values()).map(ScoreFactor::key).toList();
    }

    public static Optional<ScoreFactor> fromKey(String key) {
        return Arrays.stream(values()).filter(f -> f.key.equals(key)).findFirst();
    }
}
