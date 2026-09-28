package com.midatacredito.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.midatacredito.app.dto.AnalysisResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

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
                .andExpect(jsonPath("$.messages[0].content[0].type").value("image"))
                .andExpect(jsonPath("$.messages[0].content[0].source.type").value("base64"))
                .andExpect(jsonPath("$.messages[0].content[0].source.media_type").value("image/png"))
                .andExpect(jsonPath("$.messages[0].content[1].type").value("text"))
                .andExpect(jsonPath("$.output_config.format.type").value("json_schema"))
                .andExpect(jsonPath("$.output_config.format.schema.required.length()").value(4))
                .andRespond(withSuccess(apiResponse, MediaType.APPLICATION_JSON));

        MockMultipartFile image = new MockMultipartFile("image", "reporte.png", "image/png", PNG_BYTES);
        AnalysisResult result = service.analyze(image, "Trabajo independiente, ingresos estables.");

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
        assertThatThrownBy(() -> service.analyze(pdf, "descripcion"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PNG o JPEG");
    }

    @Test
    void rejectsEmptyDescription() {
        MockMultipartFile image = new MockMultipartFile("image", "r.png", "image/png", PNG_BYTES);
        assertThatThrownBy(() -> service.analyze(image, "   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mapsAuthenticationErrorToFriendlyMessage() {
        server.expect(requestTo("https://api.anthropic.com/v1/messages"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"invalid x-api-key\"}}"));

        MockMultipartFile image = new MockMultipartFile("image", "r.png", "image/png", PNG_BYTES);
        assertThatThrownBy(() -> service.analyze(image, "descripcion"))
                .isInstanceOf(ClaudeAnalysisException.class)
                .hasMessageContaining("ANTHROPIC_API_KEY");
    }

    @Test
    void handlesRefusalStopReason() {
        server.expect(requestTo("https://api.anthropic.com/v1/messages"))
                .andRespond(withSuccess("{\"stop_reason\":\"refusal\",\"content\":[]}", MediaType.APPLICATION_JSON));

        MockMultipartFile image = new MockMultipartFile("image", "r.png", "image/png", PNG_BYTES);
        assertThatThrownBy(() -> service.analyze(image, "descripcion"))
                .isInstanceOf(ClaudeAnalysisException.class);
    }

    @Test
    void failsFastWithoutApiKey() {
        ClaudeAiService noKey = new ClaudeAiService(RestClient.create(), new ObjectMapper(), "", "m", 100);
        MockMultipartFile image = new MockMultipartFile("image", "r.png", "image/png", PNG_BYTES);
        assertThatThrownBy(() -> noKey.analyze(image, "descripcion"))
                .isInstanceOf(ClaudeAnalysisException.class)
                .hasMessageContaining("ANTHROPIC_API_KEY");
    }
}
