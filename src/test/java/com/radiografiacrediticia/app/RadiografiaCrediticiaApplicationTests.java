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
                new AnalysisResult(720, "Buen historial.", List.of("Uso alto de tarjetas"), List.of("Pagar a tiempo")));
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
                .andExpect(content().string(containsString("Entra a tu cuenta")))
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
                .andExpect(content().string(containsString("De tu información a un plan de acción.")))
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
                .andExpect(content().string(containsString("Riesgo bajo")))
                .andExpect(content().string(containsString("Tu plan de acción")))
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
                .andExpect(flash().attribute("info", containsString("Ya generaste tu radiografía de este mes")));

        mvc.perform(get("/radiografia/nueva")).andExpect(redirectedUrl("/inicio"));
        mvc.perform(get("/inicio"))
                .andExpect(content().string(containsString("Ya tienes tu radiografía de este mes")))
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

        mvc.perform(radiografia().param("identification", "22222222").param("q_moras", "No"))
                .andExpect(redirectedUrl("/radiografia/nueva"))
                .andExpect(flash().attribute("error", containsString("responde todas las preguntas")));
        verify(claudeAiService, never()).analyze(anyList(), anyString(), anyString());

        MockMultipartHttpServletRequestBuilder complete = radiografia();
        complete.param("identification", "22222222");
        Questionnaire.QUESTIONS.forEach(q -> complete.param(q.paramName(), q.options().get(0)));
        mvc.perform(complete).andExpect(redirectedUrlPattern("/radiografia/*"));

        CreditAnalysis saved = analysisRepository.findAll().get(0);
        assertThat(saved.getImageCount()).isZero();
        assertThat(saved.getQuestionnaireJson()).contains("¿Has estado en mora");
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
}
