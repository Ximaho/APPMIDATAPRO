package com.radiografiacrediticia.app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.radiografiacrediticia.app.dto.AnalysisResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Integración con la API de Mensajes de Anthropic (https://api.anthropic.com/v1/messages).
 * El cliente HTTP se configura en {@link com.radiografiacrediticia.app.config.AnthropicClientConfig}.
 * <p>
 * Envía un mensaje multimodal con:
 * <ol>
 *   <li>Por cada captura, un bloque {@code text} que la numera y un bloque {@code image} con la
 *   captura de MiDataCrédito codificada en Base64.</li>
 *   <li>Un bloque {@code text} final con las instrucciones y la descripción de actividad económica.</li>
 * </ol>
 * La respuesta se restringe con <em>structured outputs</em> ({@code output_config.format})
 * a un esquema JSON con las llaves {@code estimated_score}, {@code summary},
 * {@code problems} y {@code recommendations}, de modo que siempre es parseable.
 */
@Service
public class ClaudeAiService {

    private static final Logger log = LoggerFactory.getLogger(ClaudeAiService.class);

    /** Límite de tamaño por imagen aceptado por la API de Anthropic. */
    public static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    /** Dimensión máxima (ancho o alto) que acepta la API por imagen. */
    public static final int MAX_IMAGE_DIMENSION = 8000;
    /** Máximo de capturas por análisis. */
    public static final int MAX_IMAGES = 10;
    /**
     * Tamaño máximo combinado de las capturas. En Base64 ocupan ~33 % más, lo que deja
     * la solicitud por debajo del límite de 32 MB de la API.
     */
    public static final long MAX_TOTAL_IMAGE_BYTES = 20L * 1024 * 1024;
    public static final int MAX_DESCRIPTION_CHARS = 4000;

    /** Habilita el reintento automático en un modelo alterno si el modelo principal declina la solicitud. */
    private static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";

    /** Esquema JSON exigido a la respuesta (structured outputs). */
    private static final Map<String, Object> RESPONSE_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "estimated_score", Map.of(
                            "type", "integer",
                            "description", "Puntaje crediticio estimado entre 150 y 950"),
                    "summary", Map.of(
                            "type", "string",
                            "description", "Diagnóstico financiero general"),
                    "problems", Map.of(
                            "type", "array",
                            "items", Map.of("type", "string"),
                            "description", "Problemas detectados en el historial"),
                    "recommendations", Map.of(
                            "type", "array",
                            "items", Map.of("type", "string"),
                            "description", "Consejos concretos para mejorar el puntaje")),
            "required", List.of("estimated_score", "summary", "problems", "recommendations"),
            "additionalProperties", false);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final int maxTokens;
    private final String systemPrompt;

    public ClaudeAiService(RestClient anthropicRestClient,
                           ObjectMapper objectMapper,
                           @Value("${anthropic.api-key:}") String apiKey,
                           @Value("${anthropic.model}") String model,
                           @Value("${anthropic.max-tokens}") int maxTokens,
                           PromptLibrary prompts) {
        this.restClient = anthropicRestClient;
        this.objectMapper = objectMapper;
        this.apiKey = cleanApiKey(apiKey);
        this.model = model;
        this.maxTokens = maxTokens;
        this.systemPrompt = prompts.systemPrompt();
        if (this.apiKey.isEmpty()) {
            log.warn("ANTHROPIC_API_KEY no está definida: los análisis fallarán hasta configurarla.");
        } else {
            log.info("ANTHROPIC_API_KEY cargada: {} ({} caracteres). Modelo: {}",
                    maskApiKey(this.apiKey), this.apiKey.length(), model);
        }
    }

    /** Quita espacios y comillas que a veces quedan al definir la variable en CMD/PowerShell. */
    static String cleanApiKey(String apiKey) {
        if (apiKey == null) {
            return "";
        }
        String key = apiKey.trim();
        while (key.length() >= 2 && (key.startsWith("\"") || key.startsWith("'"))
                && (key.endsWith("\"") || key.endsWith("'"))) {
            key = key.substring(1, key.length() - 1).trim();
        }
        return key;
    }

    /** Muestra solo el inicio y los últimos 4 caracteres, para verificar qué key se cargó sin exponerla. */
    static String maskApiKey(String apiKey) {
        if (apiKey.length() <= 16) {
            return "****";
        }
        return apiKey.substring(0, 10) + "…" + apiKey.substring(apiKey.length() - 4);
    }

    public String getModel() {
        return model;
    }

    /**
     * Genera la radiografía a partir de capturas del reporte, respuestas del cuestionario o ambas,
     * más la descripción en palabras de la persona.
     *
     * @param images        capturas PNG o JPEG del reporte (0 a {@value #MAX_IMAGES})
     * @param description   lo que ha pasado con la vida crediticia de la persona
     * @param questionnaire respuestas del cuestionario en texto plano (puede estar vacío)
     * @return resultado estructurado del análisis
     * @throws IllegalArgumentException si la entrada no es válida
     * @throws ClaudeAnalysisException  si la API falla o la respuesta no es utilizable
     */
    public AnalysisResult analyze(List<MultipartFile> images, String description, String questionnaire) {
        List<MultipartFile> files = validateImages(images);
        String cleanQuestionnaire = questionnaire == null ? "" : questionnaire.trim();
        if (files.isEmpty() && cleanQuestionnaire.isEmpty()) {
            throw new IllegalArgumentException("Sube al menos una captura de tu reporte o responde las preguntas.");
        }
        List<String> mediaTypes = files.stream().map(this::validateImage).toList();
        String cleanDescription = validateDescription(description);

        if (!StringUtils.hasText(apiKey)) {
            throw new ClaudeAnalysisException(
                    "La API de Claude no está configurada. Define la variable de entorno ANTHROPIC_API_KEY.");
        }

        List<EncodedImage> encoded = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            String mediaType = mediaTypes.get(i);
            try {
                encoded.add(new EncodedImage(mediaType, Base64.getEncoder().encodeToString(file.getBytes())));
            } catch (IOException e) {
                throw new IllegalArgumentException("No fue posible leer la imagen " + file.getOriginalFilename() + ".", e);
            }
        }

        Map<String, Object> payload = buildPayload(encoded, cleanDescription, cleanQuestionnaire);
        JsonNode response = callApi(payload);
        return parseResponse(response);
    }

    /** Imagen lista para enviarse a la API. */
    record EncodedImage(String mediaType, String base64Data) {
    }

    /** Construye el cuerpo JSON de la solicitud a /v1/messages. */
    Map<String, Object> buildPayload(List<EncodedImage> images, String description, String questionnaire) {
        List<Map<String, Object>> content = new ArrayList<>();
        int total = images.size();
        for (int i = 0; i < total; i++) {
            EncodedImage image = images.get(i);
            content.add(Map.of("type", "text", "text", "Captura " + (i + 1) + " de " + total + ":"));
            content.add(Map.of(
                    "type", "image",
                    "source", Map.of(
                            "type", "base64",
                            "media_type", image.mediaType(),
                            "data", image.base64Data())));
        }

        StringBuilder userText = new StringBuilder();
        userText.append(total > 0
                ? "Adjunto " + total + " captura(s) de pantalla de mi reporte crediticio."
                : "No tengo acceso a las capturas de mi reporte crediticio.");
        userText.append(" Con esta información entrega mi radiografía crediticia.\n\n");
        if (!questionnaire.isEmpty()) {
            userText.append("<cuestionario>\n").append(questionnaire).append("\n</cuestionario>\n\n");
        }
        userText.append("<lo_que_ha_pasado_con_mi_vida_crediticia>\n")
                .append(description)
                .append("\n</lo_que_ha_pasado_con_mi_vida_crediticia>\n");
        content.add(Map.of("type", "text", "text", userText.toString()));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("max_tokens", maxTokens);
        // El prompt de sistema es igual en todas las consultas: se marca para caché y así
        // la base de conocimiento no se cobra completa en cada radiografía.
        payload.put("system", List.of(Map.of(
                "type", "text",
                "text", systemPrompt,
                "cache_control", Map.of("type", "ephemeral"))));
        payload.put("messages", List.of(Map.of(
                "role", "user",
                "content", content)));
        payload.put("output_config", Map.of(
                "format", Map.of(
                        "type", "json_schema",
                        "schema", RESPONSE_SCHEMA)));
        payload.put("fallbacks", "default");
        return payload;
    }

    private JsonNode callApi(Map<String, Object> payload) {
        try {
            JsonNode body = restClient.post()
                    .uri("/v1/messages")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("x-api-key", apiKey)
                    .header("anthropic-beta", FALLBACK_BETA)
                    .body(payload)
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null) {
                throw new ClaudeAnalysisException("La API de Claude devolvió una respuesta vacía.");
            }
            return body;
        } catch (RestClientResponseException e) {
            log.warn("Error de la API de Anthropic: HTTP {} - {}", e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new ClaudeAnalysisException(
                    describeHttpError(e.getStatusCode().value(), apiErrorMessage(e.getResponseBodyAsString())), e);
        } catch (ResourceAccessException e) {
            log.warn("No se pudo conectar con la API de Anthropic", e);
            throw new ClaudeAnalysisException(
                    "No fue posible conectar con el servicio de análisis. Verifica tu conexión e inténtalo de nuevo.", e);
        }
    }

    /** Extrae y valida el JSON estructurado de la respuesta de la API. */
    AnalysisResult parseResponse(JsonNode response) {
        String stopReason = response.path("stop_reason").asText("");
        switch (stopReason) {
            case "refusal" -> throw new ClaudeAnalysisException(
                    "El modelo no pudo procesar esta solicitud. Verifica que la imagen sea un reporte crediticio e inténtalo de nuevo.");
            case "max_tokens" -> throw new ClaudeAnalysisException(
                    "La respuesta del análisis quedó incompleta. Intenta con una descripción más breve.");
            default -> {
                // end_turn u otros: continuar
            }
        }

        StringBuilder text = new StringBuilder();
        for (JsonNode block : response.path("content")) {
            if ("text".equals(block.path("type").asText())) {
                text.append(block.path("text").asText());
            }
        }
        if (text.isEmpty()) {
            throw new ClaudeAnalysisException("La respuesta de Claude no contiene texto para analizar.");
        }

        try {
            return objectMapper.readValue(text.toString(), AnalysisResult.class);
        } catch (JsonProcessingException e) {
            log.warn("Respuesta de Claude no parseable: {}", text);
            throw new ClaudeAnalysisException("No fue posible interpretar la respuesta del análisis.", e);
        }
    }

    /** Descarta entradas vacías y valida cantidad y tamaño total de las capturas (puede no haber ninguna). */
    List<MultipartFile> validateImages(List<MultipartFile> images) {
        List<MultipartFile> files = images == null ? List.of()
                : images.stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (files.size() > MAX_IMAGES) {
            throw new IllegalArgumentException("Puedes cargar máximo " + MAX_IMAGES + " capturas por análisis.");
        }
        long totalBytes = files.stream().mapToLong(MultipartFile::getSize).sum();
        if (totalBytes > MAX_TOTAL_IMAGE_BYTES) {
            throw new IllegalArgumentException("Las capturas suman más de 20 MB. Reduce su tamaño o cantidad.");
        }
        return files;
    }

    /**
     * Valida tamaño y tipo real del archivo (por su firma binaria, no solo por la extensión).
     *
     * @return media type aceptado por la API ({@code image/png} o {@code image/jpeg})
     */
    String validateImage(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new IllegalArgumentException("Debes cargar una captura de pantalla de tu reporte.");
        }
        if (image.getSize() > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException(
                    "La imagen " + image.getOriginalFilename() + " supera el tamaño máximo permitido de 5 MB.");
        }
        byte[] header = new byte[8];
        String mediaType = null;
        try (var in = image.getInputStream()) {
            int read = in.readNBytes(header, 0, header.length);
            if (read >= 8 && (header[0] & 0xFF) == 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G') {
                mediaType = MediaType.IMAGE_PNG_VALUE;
            } else if (read >= 3 && (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
                mediaType = MediaType.IMAGE_JPEG_VALUE;
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("No fue posible leer la imagen cargada.", e);
        }
        if (mediaType == null) {
            throw new IllegalArgumentException(
                    "Formato no soportado en " + image.getOriginalFilename() + ". Carga imágenes PNG o JPEG.");
        }
        validateDimensions(image);
        return mediaType;
    }

    /**
     * La API rechaza imágenes de más de {@value #MAX_IMAGE_DIMENSION} px por lado (típico en capturas
     * de "página completa"). Solo lee el encabezado; si no puede leerlo, deja la decisión a la API.
     */
    private void validateDimensions(MultipartFile image) {
        try (ImageInputStream in = ImageIO.createImageInputStream(image.getInputStream())) {
            if (in == null) {
                return;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                return;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width > MAX_IMAGE_DIMENSION || height > MAX_IMAGE_DIMENSION) {
                    throw new IllegalArgumentException("La imagen " + image.getOriginalFilename() + " mide "
                            + width + "×" + height + " px; el máximo es " + MAX_IMAGE_DIMENSION
                            + " px por lado. Divídela en varias capturas más cortas.");
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            log.debug("No se pudieron leer las dimensiones de {}", image.getOriginalFilename(), e);
        }
    }

    String validateDescription(String description) {
        if (!StringUtils.hasText(description)) {
            throw new IllegalArgumentException("Cuéntanos qué ha pasado con tu vida crediticia.");
        }
        String trimmed = description.trim();
        if (trimmed.length() > MAX_DESCRIPTION_CHARS) {
            throw new IllegalArgumentException(
                    "La descripción no puede superar " + MAX_DESCRIPTION_CHARS + " caracteres.");
        }
        return trimmed;
    }

    /** Extrae {@code error.message} del cuerpo de error de la API, o cadena vacía si no existe. */
    private String apiErrorMessage(String body) {
        try {
            return objectMapper.readTree(body).path("error").path("message").asText("");
        } catch (JsonProcessingException | IllegalArgumentException e) {
            return "";
        }
    }

    private static String describeHttpError(int status, String apiMessage) {
        if (apiMessage.toLowerCase(java.util.Locale.ROOT).contains("credit balance")) {
            return "Tu cuenta de Anthropic no tiene saldo suficiente. Compra créditos en "
                    + "console.anthropic.com → Plans & Billing e inténtalo de nuevo.";
        }
        return switch (status) {
            case 400 -> "La API de Claude rechazó la solicitud"
                    + (apiMessage.isBlank() ? "." : ": " + apiMessage);
            case 401, 403 -> "La API key de Anthropic es inválida o no tiene permisos. Revisa ANTHROPIC_API_KEY.";
            case 404 -> "El modelo configurado no está disponible. Revisa la propiedad anthropic.model.";
            case 413 -> "La imagen es demasiado grande para el servicio de análisis.";
            case 429 -> "Se alcanzó el límite de solicitudes a la API. Espera un momento e inténtalo de nuevo.";
            case 529 -> "El servicio de análisis está saturado. Inténtalo de nuevo en unos minutos.";
            default -> status >= 500
                    ? "El servicio de análisis no está disponible temporalmente. Inténtalo más tarde."
                    : "Error inesperado del servicio de análisis (HTTP " + status + ").";
        };
    }
}
