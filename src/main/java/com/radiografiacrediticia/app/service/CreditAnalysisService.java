package com.radiografiacrediticia.app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.radiografiacrediticia.app.dto.AnalysisResult;
import com.radiografiacrediticia.app.model.CreditAnalysis;
import com.radiografiacrediticia.app.model.User;
import com.radiografiacrediticia.app.repository.CreditAnalysisRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Orquesta el flujo de análisis: llamada a Claude, persistencia del historial
 * y conversión entre la entidad y el resultado estructurado.
 */
@Service
public class CreditAnalysisService {

    /** Longitud de las columnas de texto largo definidas en {@link CreditAnalysis}. */
    private static final int MAX_TEXT_COLUMN = 20000;
    private static final int MAX_FILE_NAMES_COLUMN = 3000;

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final ClaudeAiService claudeAiService;
    private final CreditAnalysisRepository analysisRepository;
    private final ObjectMapper objectMapper;

    public CreditAnalysisService(ClaudeAiService claudeAiService,
                                 CreditAnalysisRepository analysisRepository,
                                 ObjectMapper objectMapper) {
        this.claudeAiService = claudeAiService;
        this.analysisRepository = analysisRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Ejecuta el análisis con Claude y guarda el resultado en el historial del usuario.
     * La llamada a la API se hace fuera de la transacción para no retener conexiones de BD.
     */
    public CreditAnalysis analyzeAndSave(User user, List<MultipartFile> images, String description) {
        AnalysisResult result = claudeAiService.analyze(images, description);
        List<MultipartFile> files = images.stream().filter(f -> f != null && !f.isEmpty()).toList();

        CreditAnalysis analysis = new CreditAnalysis();
        analysis.setUser(user);
        analysis.setActivityDescription(description.trim());
        analysis.setImageFileNames(truncate(files.stream()
                .map(f -> sanitizeFileName(f.getOriginalFilename()))
                .collect(Collectors.joining(", ")), MAX_FILE_NAMES_COLUMN));
        analysis.setImageCount(files.size());
        analysis.setEstimatedScore(result.estimatedScore());
        analysis.setSummary(truncate(result.summary(), MAX_TEXT_COLUMN));
        analysis.setProblemsJson(toJson(result.problems()));
        analysis.setRecommendationsJson(toJson(result.recommendations()));
        analysis.setModelUsed(claudeAiService.getModel());
        return analysisRepository.save(analysis);
    }

    @Transactional(readOnly = true)
    public List<CreditAnalysis> history(User user) {
        return analysisRepository.findByUserOrderByCreatedAtDesc(user);
    }

    @Transactional(readOnly = true)
    public Optional<CreditAnalysis> findForUser(Long id, User user) {
        return analysisRepository.findByIdAndUser(id, user);
    }

    /** Reconstruye el resultado estructurado a partir de la entidad persistida. */
    public AnalysisResult toResult(CreditAnalysis analysis) {
        return new AnalysisResult(
                analysis.getEstimatedScore(),
                analysis.getSummary(),
                fromJson(analysis.getProblemsJson()),
                fromJson(analysis.getRecommendationsJson()));
    }

    private String toJson(List<String> items) {
        try {
            return objectMapper.writeValueAsString(items);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No fue posible serializar el análisis", e);
        }
    }

    private List<String> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Historial de análisis corrupto", e);
        }
    }

    private static String truncate(String value, int max) {
        return value.length() > max ? value.substring(0, max) : value;
    }

    private static String sanitizeFileName(String name) {
        if (name == null || name.isBlank()) {
            return "captura";
        }
        String base = name.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);
        return base.length() > 255 ? base.substring(0, 255) : base;
    }
}
