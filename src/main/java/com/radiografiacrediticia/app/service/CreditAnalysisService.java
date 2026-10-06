package com.radiografiacrediticia.app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.radiografiacrediticia.app.dto.AnalysisResult;
import com.radiografiacrediticia.app.dto.Questionnaire;
import com.radiografiacrediticia.app.model.CreditAnalysis;
import com.radiografiacrediticia.app.model.User;
import com.radiografiacrediticia.app.repository.CreditAnalysisRepository;
import com.radiografiacrediticia.app.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private static final TypeReference<LinkedHashMap<String, String>> STRING_MAP = new TypeReference<>() {
    };

    private final ClaudeAiService claudeAiService;
    private final CreditAnalysisRepository analysisRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final boolean monthlyLimitEnabled;

    public CreditAnalysisService(ClaudeAiService claudeAiService,
                                 CreditAnalysisRepository analysisRepository,
                                 UserRepository userRepository,
                                 ObjectMapper objectMapper,
                                 @Value("${radiografia.limite-mensual:true}") boolean monthlyLimitEnabled) {
        this.claudeAiService = claudeAiService;
        this.analysisRepository = analysisRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.monthlyLimitEnabled = monthlyLimitEnabled;
    }

    /**
     * Fecha desde la que el usuario puede generar su próxima radiografía, o vacío si puede hacerlo ya.
     * El límite es una radiografía por mes calendario.
     */
    @Transactional(readOnly = true)
    public Optional<LocalDate> nextAvailableDate(User user) {
        if (!monthlyLimitEnabled) {
            return Optional.empty();
        }
        YearMonth thisMonth = YearMonth.now();
        boolean usedThisMonth = analysisRepository.existsByUserAndCreatedAtGreaterThanEqual(
                user, thisMonth.atDay(1).atStartOfDay());
        return usedThisMonth ? Optional.of(thisMonth.plusMonths(1).atDay(1)) : Optional.empty();
    }

    /**
     * Genera la radiografía con Claude y la guarda en el historial del usuario.
     * La identificación queda ligada a la cuenta solo cuando la radiografía se genera con éxito,
     * para que un error de digitación en un intento fallido no la deje fija.
     * La llamada a la API se hace fuera de una transacción para no retener conexiones de BD.
     */
    public CreditAnalysis analyzeAndSave(User user, String fullName, String identification,
                                         List<MultipartFile> images, String description,
                                         Map<String, String> answers) {
        nextAvailableDate(user).ifPresent(date -> {
            throw new MonthlyLimitException(date);
        });

        String cleanName = fullName == null ? "" : fullName.trim();
        if (cleanName.isEmpty() || cleanName.length() > 120) {
            throw new IllegalArgumentException("Escribe tu nombre completo (máximo 120 caracteres).");
        }
        String cleanId = resolveIdentification(user, identification);

        List<MultipartFile> files = images == null ? List.of()
                : images.stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (files.isEmpty() && !Questionnaire.isComplete(answers)) {
            throw new IllegalArgumentException(
                    "Sube al menos una captura de tu reporte o, si no tienes acceso a ellas, responde todas las preguntas.");
        }

        AnalysisResult result = claudeAiService.analyze(files, description, Questionnaire.format(answers));

        user.setFullName(cleanName);
        user.setIdentification(cleanId);
        userRepository.save(user);

        CreditAnalysis analysis = new CreditAnalysis();
        analysis.setUser(user);
        analysis.setActivityDescription(description.trim());
        analysis.setImageFileNames(truncate(files.stream()
                .map(f -> sanitizeFileName(f.getOriginalFilename()))
                .collect(Collectors.joining(", ")), MAX_FILE_NAMES_COLUMN));
        analysis.setImageCount(files.size());
        analysis.setQuestionnaireJson(answers.isEmpty() ? null : toJson(answers));
        analysis.setEstimatedScore(result.estimatedScore());
        analysis.setSummary(truncate(result.summary(), MAX_TEXT_COLUMN));
        analysis.setProblemsJson(toJson(result.problems()));
        analysis.setRecommendationsJson(toJson(result.recommendations()));
        analysis.setModelUsed(claudeAiService.getModel());
        return analysisRepository.save(analysis);
    }

    /** Usa la identificación ya ligada a la cuenta o valida la nueva (normalizada, sin puntos ni espacios). */
    private String resolveIdentification(User user, String identification) {
        if (user.getIdentification() != null) {
            return user.getIdentification();
        }
        String clean = normalizeIdentification(identification);
        if (!clean.matches("[A-Z0-9]{5,20}")) {
            throw new IllegalArgumentException(
                    "Escribe un número de identificación válido (entre 5 y 20 letras o números).");
        }
        if (userRepository.existsByIdentificationAndIdNot(clean, user.getId())) {
            throw new IllegalArgumentException("Esta identificación ya está ligada a otra cuenta.");
        }
        return clean;
    }

    static String normalizeIdentification(String identification) {
        return identification == null ? ""
                : identification.replaceAll("[\\s.\\-]", "").toUpperCase(Locale.ROOT);
    }

    /** Respuestas del cuestionario guardadas con el análisis (pregunta → respuesta). */
    public Map<String, String> answersOf(CreditAnalysis analysis) {
        String json = analysis.getQuestionnaireJson();
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, STRING_MAP);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Historial de análisis corrupto", e);
        }
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

    private String toJson(Object items) {
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
