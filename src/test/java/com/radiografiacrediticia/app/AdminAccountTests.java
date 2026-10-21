package com.radiografiacrediticia.app;

import com.radiografiacrediticia.app.dto.TestResults;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:admintest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "anthropic.api-key=",
        "radiografia.admin.email=Admin@Radiografia.test",
        "radiografia.admin.password=clave-admin-123"
})
@AutoConfigureMockMvc
class AdminAccountTests {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CreditAnalysisRepository analysisRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private ClaudeAiService claudeAiService;

    @BeforeEach
    void setUp() {
        analysisRepository.deleteAll();
        when(claudeAiService.getModel()).thenReturn("claude-sonnet-5-5");
        when(claudeAiService.analyze(anyList(), anyString(), anyString())).thenReturn(TestResults.withRating(70));
    }

    @Test
    void adminAccountIsCreatedAtStartupAndCanLogIn() throws Exception {
        User admin = userRepository.findByEmailIgnoreCase("admin@radiografia.test").orElseThrow();
        assertThat(admin.isAdmin()).isTrue();
        assertThat(passwordEncoder.matches("clave-admin-123", admin.getPassword())).isTrue();

        mvc.perform(formLogin("/login").user("email", "admin@radiografia.test").password("clave-admin-123"))
                .andExpect(authenticated().withRoles("USER", "ADMIN"));
    }

    @Test
    @WithMockUser(username = "admin@radiografia.test")
    void adminHasNoMonthlyLimitAndIdentificationIsNotBound() throws Exception {
        User other = new User("Otra persona", "otra@radiografia.test", "$2a$10$hash");
        other.setIdentification("77777777");
        userRepository.save(other);

        String[][] pruebas = {{"Cliente Uno", "11111111"}, {"Cliente Dos", "22222222"}, {"Cliente Tres", "77777777"}};
        for (String[] prueba : pruebas) {
            String location = mvc.perform(multipart("/radiografia")
                            .file(new MockMultipartFile("images", "r.png", "image/png", PNG))
                            .param("fullName", prueba[0])
                            .param("identification", prueba[1])
                            .param("phone", "3105556677")
                            .param("description", "Prueba de administrador")
                            .with(csrf()))
                    .andExpect(redirectedUrlPattern("/radiografia/*"))
                    .andReturn().getResponse().getRedirectedUrl();
            mvc.perform(get(location)).andExpect(content().string(containsString(prueba[0])));
        }

        assertThat(analysisRepository.count()).isEqualTo(3);
        assertThat(analysisRepository.findAll()).extracting("holderEmail")
                .containsOnly("admin@radiografia.test");
        User admin = userRepository.findByEmailIgnoreCase("admin@radiografia.test").orElseThrow();
        assertThat(admin.getIdentification()).isNull();
        assertThat(admin.getFullName()).isEqualTo("Administrador");

        mvc.perform(get("/radiografia/nueva"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Administrador · sin límite")))
                .andExpect(content().string(containsString("la identificación no queda ligada a la cuenta")));
        mvc.perform(get("/inicio"))
                .andExpect(content().string(containsString("href=\"/radiografia/nueva\"")))
                .andExpect(content().string(containsString("Cliente Dos")));
    }
}
