package com.radiografiacrediticia.app;

import com.radiografiacrediticia.app.dto.AnalysisResult;
import com.radiografiacrediticia.app.dto.Questionnaire;
import com.radiografiacrediticia.app.model.CreditAnalysis;
import com.radiografiacrediticia.app.model.User;
import com.radiografiacrediticia.app.repository.CreditAnalysisRepository;
import com.radiografiacrediticia.app.repository.UserRepository;
import com.radiografiacrediticia.app.service.ClaudeAiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "anthropic.api-key="
})
@AutoConfigureMockMvc
class RadiografiaCrediticiaApplicationTests {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CreditAnalysisRepository analysisRepository;

    @MockitoBean
    private ClaudeAiService claudeAiService;

    @BeforeEach
    void cleanDatabase() {
        analysisRepository.deleteAll();
        userRepository.deleteAll();
        when(claudeAiService.getModel()).thenReturn("claude-sonnet-5-5");
        when(claudeAiService.analyze(anyList(), anyString(), anyString())).thenReturn(
                com.radiografiacrediticia.app.dto.TestResults.withRating(80));
    }

    @Test
    void privatePagesRequireAuthentication() throws Exception {
        mvc.perform(get("/inicio"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void loginPageShowsBrand() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ingrese a su cuenta")))
                .andExpect(content().string(containsString("/img/logo-icon.png")));
    }

    @Test
    void registerLoginAndOpenHome() throws Exception {
        mvc.perform(post("/register").with(csrf())
                        .param("fullName", "Ana Pérez")
                        .param("email", "Ana@Example.com")
                        .param("password", "secreta123")
                        .param("confirmPassword", "secreta123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        assertThat(userRepository.findByEmailIgnoreCase("ana@example.com"))
                .get()
                .satisfies(u -> assertThat(u.getPassword()).startsWith("$2"));

        mvc.perform(formLogin("/login").user("email", "ana@example.com").password("secreta123"))
                .andExpect(authenticated())
                .andExpect(redirectedUrl("/inicio"));
    }

    @Test
    void registerRejectsMismatchedPasswords() throws Exception {
        mvc.perform(post("/register").with(csrf())
                        .param("fullName", "Luis")
                        .param("email", "luis@example.com")
                        .param("password", "secreta123")
                        .param("confirmPassword", "otra12345"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Las contraseñas no coinciden")));
    }

    @Test
    @WithMockUser(username = "maria@example.com")
    void homeShowsMonthlyCardWhenAvailable() throws Exception {
        userRepository.save(new User("María Gómez", "maria@example.com", "$2a$10$hash"));
        mvc.perform(get("/inicio"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("De su información a un plan de acción.")))
                .andExpect(content().string(containsString("/radiografia/nueva")));
    }

    @Test
    @WithMockUser(username = "maria@example.com")
    void generatesWithCapturesBindsIdentificationAndShowsResult() throws Exception {
        userRepository.save(new User("María Gómez", "maria@example.com", "$2a$10$hash"));

        String location = mvc.perform(radiografia()
                        .file(new MockMultipartFile("images", "r1.png", "image/png", PNG))
                        .param("identification", "1.020.304.050"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/radiografia/*"))
                .andReturn().getResponse().getRedirectedUrl();

        User user = userRepository.findByEmailIgnoreCase("maria@example.com").orElseThrow();
        assertThat(user.getIdentification()).isEqualTo("1020304050");
        assertThat(user.getFullName()).isEqualTo("María Gómez Ruiz");

        mvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Perfil favorable")))
                .andExpect(content().string(not(containsString("Desglose de factores"))))
                .andExpect(content().string(containsString("/950")))
                .andExpect(content().string(containsString("No corresponde al puntaje oficial de Datacrédito")))
                .andExpect(content().string(containsString("Acciones recomendadas")))
                .andExpect(content().string(containsString("Uso alto de tarjetas")));

        mvc.perform(get(location + "/pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"));
    }

    @Test
    @WithMockUser(username = "pedro@example.com")
    void secondRadiografiaInSameMonthIsBlocked() throws Exception {
        userRepository.save(new User("Pedro", "pedro@example.com", "$2a$10$hash"));
        mvc.perform(radiografia().file(new MockMultipartFile("images", "r1.png", "image/png", PNG))
                        .param("identification", "123456789"))
                .andExpect(redirectedUrlPattern("/radiografia/*"));

        mvc.perform(radiografia().file(new MockMultipartFile("images", "r1.png", "image/png", PNG)))
                .andExpect(redirectedUrl("/inicio"))
                .andExpect(flash().attribute("info", containsString("Ya generó su radiografía de este mes")));

        mvc.perform(get("/radiografia/nueva")).andExpect(redirectedUrl("/inicio"));
        mvc.perform(get("/inicio"))
                .andExpect(content().string(containsString("Ya tiene su radiografía de este mes")))
                .andExpect(content().string(not(containsString("href=\"/radiografia/nueva\""))));
    }

    @Test
    @WithMockUser(username = "juan@example.com")
    void identificationCannotBeChangedOnceBound() throws Exception {
        User user = new User("Juan", "juan@example.com", "$2a$10$hash");
        user.setIdentification("11111111");
        userRepository.save(user);

        mvc.perform(get("/radiografia/nueva"))
                .andExpect(content().string(containsString("no se puede cambiar")))
                .andExpect(content().string(containsString("readonly")));

        mvc.perform(radiografia().file(new MockMultipartFile("images", "r1.png", "image/png", PNG))
                        .param("identification", "99999999"))
                .andExpect(redirectedUrlPattern("/radiografia/*"));

        assertThat(userRepository.findByEmailIgnoreCase("juan@example.com").orElseThrow().getIdentification())
                .isEqualTo("11111111");
    }

    @Test
    @WithMockUser(username = "sofia@example.com")
    void identificationAlreadyUsedByAnotherAccountIsRejected() throws Exception {
        User other = new User("Otra", "otra@example.com", "$2a$10$hash");
        other.setIdentification("55555555");
        userRepository.save(other);
        userRepository.save(new User("Sofía", "sofia@example.com", "$2a$10$hash"));

        mvc.perform(radiografia().file(new MockMultipartFile("images", "r1.png", "image/png", PNG))
                        .param("identification", "55.555.555"))
                .andExpect(redirectedUrl("/radiografia/nueva"))
                .andExpect(flash().attribute("error", containsString("ligada a otra cuenta")));
        verify(claudeAiService, never()).analyze(anyList(), anyString(), anyString());
    }

    @Test
    @WithMockUser(username = "lina@example.com")
    void withoutCapturesAllQuestionsAreRequired() throws Exception {
        userRepository.save(new User("Lina", "lina@example.com", "$2a$10$hash"));

        mvc.perform(radiografia().param("identification", "22222222")
                        .param("q_situacion", "Aparecen obligaciones que no reconozco."))
                .andExpect(redirectedUrl("/radiografia/nueva"))
                .andExpect(flash().attribute("error", containsString("responda todas las preguntas")));
        verify(claudeAiService, never()).analyze(anyList(), anyString(), anyString());

        // Las preguntas cerradas son obligatorias; la abierta no. Las múltiples aceptan varias opciones.
        MockMultipartHttpServletRequestBuilder complete = radiografia();
        complete.param("identification", "22222222");
        Questionnaire.QUESTIONS.stream().filter(Questionnaire.Question::isRequired)
                .forEach(q -> complete.param(q.paramName(), q.options().get(0)));
        complete.param("q_revisar", "Obligaciones castigadas.", "Opción inventada");
        mvc.perform(complete).andExpect(redirectedUrlPattern("/radiografia/*"));

        CreditAnalysis saved = analysisRepository.findAll().get(0);
        assertThat(saved.getImageCount()).isZero();
        assertThat(saved.getQuestionnaireJson())
                .contains("¿Qué situaciones desea que revisemos?")
                .contains("Reportes negativos por mora.; Obligaciones castigadas.")
                .doesNotContain("Opción inventada");
    }

    @Test
    @WithMockUser(username = "nora@example.com")
    void formShowsQuestionnaireFromDocument() throws Exception {
        userRepository.save(new User("Nora", "nora@example.com", "$2a$10$hash"));
        mvc.perform(get("/radiografia/nueva"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Cuéntenos brevemente"))))
                .andExpect(content().string(containsString("de la forma más explícita posible")))
                .andExpect(content().string(containsString("Pregunta 7.")))
                .andExpect(content().string(containsString("type=\"checkbox\" name=\"q_revisar\"")))
                .andExpect(content().string(containsString("type=\"checkbox\" name=\"q_situacion\"")))
                .andExpect(content().string(containsString("type=\"radio\" name=\"q_pagos\"")));
    }

    @Test
    @WithMockUser(username = "pablo@example.com")
    void otherUsersCannotSeeForeignReports() throws Exception {
        userRepository.save(new User("Pablo", "pablo@example.com", "$2a$10$hash"));
        mvc.perform(get("/radiografia/{id}", 99999L)).andExpect(status().isNotFound());
        mvc.perform(get("/radiografia/{id}/pdf", 99999L)).andExpect(status().isNotFound());
    }

    private static MockMultipartHttpServletRequestBuilder radiografia() {
        MockMultipartHttpServletRequestBuilder builder = multipart("/radiografia");
        builder.param("fullName", "María Gómez Ruiz")
                .param("description", "Estuve en mora pero ya pagué.")
                .with(csrf());
        return builder;
    }

    @Test
    @WithMockUser(username = "rosa@example.com")
    void radiografiasSavedWithoutScoreAreRecalculatedFromStoredRatings() throws Exception {
        User user = userRepository.save(new User("Rosa", "rosa@example.com", "$2a$10$hash"));
        CreditAnalysis old = new CreditAnalysis();
        old.setUser(user);
        old.setActivityDescription("Prueba");
        old.setImageCount(0);
        old.setEstimatedScore(0);
        old.setScoreCalculable(false);
        old.setConfidence("Baja");
        old.setSummary("Resumen");
        old.setProblemsJson("[]");
        old.setRecommendationsJson("[]");
        old.setFactorsJson("[{\"factor\":\"comportamiento_historico_pago\",\"rating\":80,\"verifiable\":true,\"justification\":\"j\"}]");
        old = analysisRepository.save(old);

        // 285×0,8 + (950 − 285)×0,5 = 228 + 332,5 = 560,5 → 561
        mvc.perform(get("/radiografia/{id}", old.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(">561<")))
                .andExpect(content().string(containsString("información limitada")));
        mvc.perform(get("/inicio"))
                .andExpect(content().string(containsString(">561<")));
    }

    @Test
    @WithMockUser(username = "pia@example.com")
    void resultShowsEntityPlanAndPetitionCanBeDownloaded() throws Exception {
        when(claudeAiService.analyze(anyList(), anyString(), anyString()))
                .thenReturn(com.radiografiacrediticia.app.dto.TestResults.withPetition());
        userRepository.save(new User("Pía", "pia@example.com", "$2a$10$hash"));

        String location = mvc.perform(radiografia().file(new MockMultipartFile("images", "r1.png", "image/png", PNG))
                        .param("identification", "44444444"))
                .andExpect(redirectedUrlPattern("/radiografia/*"))
                .andReturn().getResponse().getRedirectedUrl();

        mvc.perform(get(location))
                .andExpect(content().string(containsString("Pasos a seguir con cada entidad")))
                .andExpect(content().string(containsString("Bancolombia")))
                .andExpect(content().string(containsString(location + "/peticion/1")))
                .andExpect(content().string(not(containsString(location + "/peticion/2"))));

        mvc.perform(get(location + "/peticion/1"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", containsString("derecho-de-peticion-bancolombia.pdf")));
        mvc.perform(get(location + "/peticion/2")).andExpect(status().isNotFound());
    }
}
