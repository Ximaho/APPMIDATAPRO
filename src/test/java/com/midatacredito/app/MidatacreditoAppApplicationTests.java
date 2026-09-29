package com.midatacredito.app;

import com.midatacredito.app.model.CreditAnalysis;
import com.midatacredito.app.model.User;
import com.midatacredito.app.repository.CreditAnalysisRepository;
import com.midatacredito.app.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "anthropic.api-key="
})
@AutoConfigureMockMvc
class MidatacreditoAppApplicationTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CreditAnalysisRepository analysisRepository;

    @Test
    void dashboardRequiresAuthentication() throws Exception {
        mvc.perform(get("/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void registerLoginAndOpenDashboard() throws Exception {
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
                .andExpect(redirectedUrl("/dashboard"));
    }

    @Test
    void registerRejectsMismatchedPasswords() throws Exception {
        mvc.perform(post("/register").with(csrf())
                        .param("fullName", "Luis")
                        .param("email", "luis@example.com")
                        .param("password", "secreta123")
                        .param("confirmPassword", "otra12345"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Las contraseñas no coinciden")));
    }

    @Test
    void loginPageIsPublic() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "maria@example.com")
    void dashboardShowsSavedAnalysisAndDownloadsPdf() throws Exception {
        User user = userRepository.save(new User("María Gómez", "maria@example.com", "$2a$10$hash"));
        CreditAnalysis analysis = new CreditAnalysis();
        analysis.setUser(user);
        analysis.setActivityDescription("Empleada con contrato indefinido.");
        analysis.setImageFileNames("reporte-1.png, reporte-2.png");
        analysis.setImageCount(2);
        analysis.setEstimatedScore(780);
        analysis.setSummary("Buen historial crediticio.");
        analysis.setProblemsJson("[\"Uso alto de la tarjeta\"]");
        analysis.setRecommendationsJson("[\"Mantener pagos al día\"]");
        analysis.setModelUsed("claude-sonnet-5-5");
        analysis = analysisRepository.save(analysis);

        mvc.perform(get("/dashboard").param("analysisId", analysis.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Riesgo bajo")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Uso alto de la tarjeta")));

        mvc.perform(get("/dashboard/analysis/{id}/pdf", analysis.getId()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("informe-crediticio-" + analysis.getId() + ".pdf")));
    }

    @Test
    @WithMockUser(username = "pedro@example.com")
    void otherUsersCannotDownloadForeignReports() throws Exception {
        userRepository.save(new User("Pedro", "pedro@example.com", "$2a$10$hash"));
        mvc.perform(get("/dashboard/analysis/{id}/pdf", 99999L)).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "sofia@example.com")
    void analyzeWithoutApiKeyShowsFriendlyError() throws Exception {
        userRepository.save(new User("Sofía", "sofia@example.com", "$2a$10$hash"));
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        mvc.perform(multipart("/dashboard/analyze")
                        .file(new MockMultipartFile("images", "r1.png", "image/png", png))
                        .file(new MockMultipartFile("images", "r2.png", "image/png", png))
                        .param("description", "Independiente").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"))
                .andExpect(flash().attribute("error", org.hamcrest.Matchers.containsString("ANTHROPIC_API_KEY")));
    }

    @Test
    @WithMockUser(username = "juan@example.com")
    void analyzeWithoutImagesShowsFriendlyError() throws Exception {
        userRepository.save(new User("Juan", "juan@example.com", "$2a$10$hash"));
        mvc.perform(multipart("/dashboard/analyze").param("description", "Independiente").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("error", org.hamcrest.Matchers.containsString("al menos una captura")));
    }
}
