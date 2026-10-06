package com.radiografiacrediticia.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.radiografiacrediticia.app.dto.AnalysisResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ClaudeAiServiceTest {

    private static final byte[] PNG_BYTES = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
    private static final byte[] JPEG_BYTES = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10};

    private MockRestServiceServer server;
    private ClaudeAiService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.anthropic.com");
        server = MockRestServiceServer.bindTo(builder).build();
        service = new ClaudeAiService(builder.build(), new ObjectMapper(), "test-key", "claude-sonnet-5-5", 16000);
    }

    @Test
    void sendsMultimodalRequestAndParsesStructuredJson() {
        String apiResponse = """
                {
                  "id": "msg_1",
                  "type": "message",
                  "role": "assistant",
                  "stop_reason": "end_turn",
                  "content": [
                    {"type": "text", "text": "{\\"estimated_score\\": 612, \\"summary\\": \\"Historial aceptable\\", \\"problems\\": [\\"Mora de 30 días\\"], \\"recommendations\\": [\\"Pagar a tiempo\\", \\"Reducir uso de tarjetas\\"]}"}
                  ]
                }
                """;

        server.expect(requestTo("https://api.anthropic.com/v1/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-api-key", "test-key"))
                .andExpect(jsonPath("$.model").value("claude-sonnet-5-5"))
                .andExpect(jsonPath("$.messages[0].content.length()").value(5))
                .andExpect(jsonPath("$.messages[0].content[0].text").value("Captura 1 de 2:"))
                .andExpect(jsonPath("$.messages[0].content[1].type").value("image"))
                .andExpect(jsonPath("$.messages[0].content[1].source.type").value("base64"))
                .andExpect(jsonPath("$.messages[0].content[1].source.media_type").value("image/png"))
                .andExpect(jsonPath("$.messages[0].content[2].text").value("Captura 2 de 2:"))
                .andExpect(jsonPath("$.messages[0].content[3].source.media_type").value("image/jpeg"))
                .andExpect(jsonPath("$.messages[0].content[4].type").value("text"))
                .andExpect(jsonPath("$.output_config.format.type").value("json_schema"))
                .andExpect(jsonPath("$.output_config.format.schema.required.length()").value(4))
                .andRespond(withSuccess(apiResponse, MediaType.APPLICATION_JSON));

        MockMultipartFile page1 = new MockMultipartFile("images", "reporte-1.png", "image/png", PNG_BYTES);
        MockMultipartFile page2 = new MockMultipartFile("images", "reporte-2.jpg", "image/jpeg", JPEG_BYTES);
        AnalysisResult result = service.analyze(List.of(page1, page2), "Trabajo independiente, ingresos estables.", "");

        server.verify();
        assertThat(result.estimatedScore()).isEqualTo(612);
        assertThat(result.summary()).isEqualTo("Historial aceptable");
        assertThat(result.problems()).containsExactly("Mora de 30 días");
        assertThat(result.recommendations()).hasSize(2);
        assertThat(result.riskLevel()).isEqualTo("Riesgo medio");
    }

    @Test
    void clampsScoreToValidRange() {
        AnalysisResult high = new AnalysisResult(2000, "x", null, null);
        AnalysisResult low = new AnalysisResult(10, "x", null, null);
        assertThat(high.estimatedScore()).isEqualTo(950);
        assertThat(low.estimatedScore()).isEqualTo(150);
        assertThat(high.problems()).isEmpty();
    }

    @Test
    void rejectsNonImageFiles() {
        MockMultipartFile pdf = new MockMultipartFile("image", "doc.png", "image/png", "%PDF-1.4".getBytes());
        assertThatThrownBy(() -> service.analyze(List.of(pdf), "descripcion", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PNG o JPEG");
    }

    @Test
    void rejectsEmptyDescription() {
        MockMultipartFile image = new MockMultipartFile("image", "r.png", "image/png", PNG_BYTES);
        assertThatThrownBy(() -> service.analyze(List.of(image), "   ", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mapsAuthenticationErrorToFriendlyMessage() {
        server.expect(requestTo("https://api.anthropic.com/v1/messages"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"invalid x-api-key\"}}"));

        MockMultipartFile image = new MockMultipartFile("image", "r.png", "image/png", PNG_BYTES);
        assertThatThrownBy(() -> service.analyze(List.of(image), "descripcion", ""))
                .isInstanceOf(ClaudeAnalysisException.class)
                .hasMessageContaining("ANTHROPIC_API_KEY");
    }

    @Test
    void handlesRefusalStopReason() {
        server.expect(requestTo("https://api.anthropic.com/v1/messages"))
                .andRespond(withSuccess("{\"stop_reason\":\"refusal\",\"content\":[]}", MediaType.APPLICATION_JSON));

        MockMultipartFile image = new MockMultipartFile("image", "r.png", "image/png", PNG_BYTES);
        assertThatThrownBy(() -> service.analyze(List.of(image), "descripcion", ""))
                .isInstanceOf(ClaudeAnalysisException.class);
    }

    @Test
    void failsFastWithoutApiKey() {
        ClaudeAiService noKey = new ClaudeAiService(RestClient.create(), new ObjectMapper(), "", "m", 100);
        MockMultipartFile image = new MockMultipartFile("image", "r.png", "image/png", PNG_BYTES);
        assertThatThrownBy(() -> noKey.analyze(List.of(image), "descripcion", ""))
                .isInstanceOf(ClaudeAnalysisException.class)
                .hasMessageContaining("ANTHROPIC_API_KEY");
    }

    @Test
    void rejectsMoreThanMaxImages() {
        MockMultipartFile image = new MockMultipartFile("images", "r.png", "image/png", PNG_BYTES);
        List<MockMultipartFile> tooMany = Collections.nCopies(ClaudeAiService.MAX_IMAGES + 1, image);
        assertThatThrownBy(() -> service.analyze(List.copyOf(tooMany), "descripcion", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("máximo " + ClaudeAiService.MAX_IMAGES);
    }

    @Test
    void rejectsWhenTotalSizeExceedsLimit() {
        byte[] big = new byte[(int) ClaudeAiService.MAX_IMAGE_BYTES - 10];
        System.arraycopy(PNG_BYTES, 0, big, 0, PNG_BYTES.length);
        MockMultipartFile image = new MockMultipartFile("images", "big.png", "image/png", big);
        assertThatThrownBy(() -> service.analyze(List.of(image, image, image, image, image), "descripcion", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("20 MB");
    }

    @Test
    void rejectsEmptySelection() {
        MockMultipartFile empty = new MockMultipartFile("images", "", "application/octet-stream", new byte[0]);
        assertThatThrownBy(() -> service.analyze(List.of(empty), "descripcion", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("al menos una captura");
    }

    @Test
    void reportsInvalidFileAmongSeveralBeforeCheckingApiKey() {
        ClaudeAiService noKey = new ClaudeAiService(RestClient.create(), new ObjectMapper(), "", "m", 100);
        MockMultipartFile good = new MockMultipartFile("images", "ok.png", "image/png", PNG_BYTES);
        MockMultipartFile fake = new MockMultipartFile("images", "falso.png", "image/png", "%PDF-1.4".getBytes());
        assertThatThrownBy(() -> noKey.analyze(List.of(good, fake), "descripcion", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("falso.png");
    }

    @Test
    void showsApiErrorMessageOnBadRequest() {
        server.expect(requestTo("https://api.anthropic.com/v1/messages"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"type\":\"error\",\"error\":{\"type\":\"invalid_request_error\",\"message\":\"detalle de prueba\"}}"));

        MockMultipartFile image = new MockMultipartFile("image", "r.png", "image/png", PNG_BYTES);
        assertThatThrownBy(() -> service.analyze(List.of(image), "descripcion", ""))
                .isInstanceOf(ClaudeAnalysisException.class)
                .hasMessageContaining("detalle de prueba");
    }

    @Test
    void rejectsImagesLargerThanApiDimensionLimit() throws Exception {
        java.awt.image.BufferedImage tall = new java.awt.image.BufferedImage(10, 8100,
                java.awt.image.BufferedImage.TYPE_BYTE_GRAY);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(tall, "png", out);
        MockMultipartFile image = new MockMultipartFile("images", "pagina-completa.png", "image/png", out.toByteArray());

        assertThatThrownBy(() -> service.analyze(List.of(image), "descripcion", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("8100");
    }

    @Test
    void explainsLowCreditBalanceInSpanish() {
        server.expect(requestTo("https://api.anthropic.com/v1/messages"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"type\":\"error\",\"error\":{\"type\":\"invalid_request_error\",\"message\":\"Your credit balance is too low to access the Anthropic API. Please go to Plans & Billing to upgrade or purchase credits.\"}}"));

        MockMultipartFile image = new MockMultipartFile("image", "r.png", "image/png", PNG_BYTES);
        assertThatThrownBy(() -> service.analyze(List.of(image), "descripcion", ""))
                .isInstanceOf(ClaudeAnalysisException.class)
                .hasMessageContaining("no tiene saldo suficiente");
    }

    @Test
    void sendsQuestionnaireOnlyRequestWithoutImages() {
        String apiResponse = """
                {"stop_reason": "end_turn", "content": [{"type": "text", "text": "{\\"estimated_score\\": 540, \\"summary\\": \\"Estimación sin reporte\\", \\"problems\\": [], \\"recommendations\\": [\\"Ponerse al día\\"]}"}]}
                """;
        server.expect(requestTo("https://api.anthropic.com/v1/messages"))
                .andExpect(jsonPath("$.messages[0].content.length()").value(1))
                .andExpect(jsonPath("$.messages[0].content[0].text",
                        org.hamcrest.Matchers.containsString("<cuestionario>")))
                .andExpect(jsonPath("$.messages[0].content[0].text",
                        org.hamcrest.Matchers.containsString("No tengo acceso a las capturas")))
                .andRespond(withSuccess(apiResponse, MediaType.APPLICATION_JSON));

        AnalysisResult result = service.analyze(List.of(), "Estuve en mora pero ya pagué",
                "- ¿Has estado en mora en los últimos 12 meses? Sí, pero ya pagué");

        server.verify();
        assertThat(result.estimatedScore()).isEqualTo(540);
    }

    @Test
    void rejectsRequestWithoutImagesNorQuestionnaire() {
        assertThatThrownBy(() -> service.analyze(List.of(), "descripcion", "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("responde las preguntas");
    }

    @Test
    void cleansQuotesAndMasksApiKey() {
        assertThat(ClaudeAiService.cleanApiKey("  \"sk-ant-api03-abc\"  ")).isEqualTo("sk-ant-api03-abc");
        assertThat(ClaudeAiService.cleanApiKey("'sk-ant-xyz'")).isEqualTo("sk-ant-xyz");
        assertThat(ClaudeAiService.cleanApiKey(null)).isEmpty();
        assertThat(ClaudeAiService.maskApiKey("sk-ant-api03-ABCDEFGHIJKLMNOP-1234"))
                .isEqualTo("sk-ant-api…1234")
                .doesNotContain("ABCDEFGH");
    }
}
