package com.radiografiacrediticia.app.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Datos de prueba con el formato de la metodología por factores. */
public final class TestResults {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TestResults() {
    }

    /** Resultado con los siete factores valorados igual. */
    public static AnalysisResult withRating(int rating) {
        List<AnalysisResult.FactorInput> inputs = new ArrayList<>();
        for (ScoreFactor f : ScoreFactor.values()) {
            inputs.add(new AnalysisResult.FactorInput(f.key(), rating, true, "Justificación de " + f.label()));
        }
        return AnalysisResult.fromModel(true, "media", inputs, "Buen historial.", List.of("Pagos puntuales"),
                List.of("Uso alto de tarjetas"), List.of("Cupos de las tarjetas"), List.of("Pagar a tiempo"));
    }

    /** JSON tal como lo devolvería la IA, con las valoraciones dadas en el orden de ScoreFactor. */
    public static String modelJson(boolean calculable, int... ratings) {
        List<Map<String, Object>> factors = new ArrayList<>();
        ScoreFactor[] all = ScoreFactor.values();
        for (int i = 0; i < ratings.length; i++) {
            factors.add(Map.of("factor", all[i].key(), "rating", ratings[i], "verifiable", true,
                    "justification", "Justificación " + (i + 1)));
        }
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("score_calculable", calculable);
        json.put("confidence", "media");
        json.put("factors", factors);
        json.put("summary", "Historial aceptable");
        json.put("favorable_aspects", List.of("Pagos recientes al día"));
        json.put("problems", List.of("Mora de 30 días"));
        json.put("data_to_verify", List.of("Cupos de las tarjetas"));
        json.put("recommendations", List.of("Pagar a tiempo", "Reducir uso de tarjetas"));
        return write(json);
    }

    /** Respuesta completa de la API de mensajes que envuelve el JSON de la IA. */
    public static String apiResponse(String modelJson) {
        return write(Map.of("id", "msg_1", "type", "message", "role", "assistant", "stop_reason", "end_turn",
                "content", List.of(Map.of("type", "text", "text", modelJson))));
    }

    private static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
