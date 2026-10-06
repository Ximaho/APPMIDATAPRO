package com.radiografiacrediticia.app.controller;

import com.radiografiacrediticia.app.dto.AnalysisResult;
import com.radiografiacrediticia.app.dto.Questionnaire;
import com.radiografiacrediticia.app.model.CreditAnalysis;
import com.radiografiacrediticia.app.model.User;
import com.radiografiacrediticia.app.repository.UserRepository;
import com.radiografiacrediticia.app.service.ClaudeAiService;
import com.radiografiacrediticia.app.service.ClaudeAnalysisException;
import com.radiografiacrediticia.app.service.CreditAnalysisService;
import com.radiografiacrediticia.app.service.MonthlyLimitException;
import com.radiografiacrediticia.app.service.PdfReportService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Área privada: inicio con el acceso a la radiografía del mes, formulario (capturas y/o preguntas),
 * resultado, historial y descarga del informe PDF.
 */
@Controller
public class DashboardController {

    private final UserRepository userRepository;
    private final CreditAnalysisService analysisService;
    private final PdfReportService pdfReportService;

    public DashboardController(UserRepository userRepository,
                               CreditAnalysisService analysisService,
                               PdfReportService pdfReportService) {
        this.userRepository = userRepository;
        this.analysisService = analysisService;
        this.pdfReportService = pdfReportService;
    }

    @GetMapping({"/", "/dashboard"})
    public String root() {
        return "redirect:/inicio";
    }

    @GetMapping("/inicio")
    public String home(Authentication authentication, Model model) {
        User user = currentUser(authentication);
        model.addAttribute("user", user);
        model.addAttribute("history", analysisService.history(user));
        LocalDate next = analysisService.nextAvailableDate(user).orElse(null);
        model.addAttribute("nextAvailable", next);
        model.addAttribute("nextAvailableText", next == null ? null : MonthlyLimitException.formatDate(next));
        return "inicio";
    }

    @GetMapping("/radiografia/nueva")
    public String newForm(Authentication authentication, Model model, RedirectAttributes redirectAttributes) {
        User user = currentUser(authentication);
        Optional<LocalDate> next = analysisService.nextAvailableDate(user);
        if (next.isPresent()) {
            redirectAttributes.addFlashAttribute("info", new MonthlyLimitException(next.get()).getMessage());
            return "redirect:/inicio";
        }
        model.addAttribute("user", user);
        model.addAttribute("questions", Questionnaire.QUESTIONS);
        model.addAttribute("maxImages", ClaudeAiService.MAX_IMAGES);
        model.addAttribute("maxDescriptionChars", ClaudeAiService.MAX_DESCRIPTION_CHARS);
        if (!model.containsAttribute("fullName")) {
            model.addAttribute("fullName", user.getFullName());
        }
        if (!model.containsAttribute("selected")) {
            model.addAttribute("selected", Map.of());
        }
        return "radiografia-nueva";
    }

    @PostMapping(value = "/radiografia", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String generate(@RequestParam(name = "images", required = false) List<MultipartFile> images,
                           @RequestParam(name = "fullName", required = false) String fullName,
                           @RequestParam(name = "identification", required = false) String identification,
                           @RequestParam(name = "description", required = false) String description,
                           @RequestParam Map<String, String> params,
                           Authentication authentication,
                           RedirectAttributes redirectAttributes) {
        User user = currentUser(authentication);
        Map<String, String> answers = Questionnaire.extractAnswers(params);
        try {
            CreditAnalysis analysis = analysisService.analyzeAndSave(user, fullName, identification,
                    images == null ? List.of() : images, description, answers);
            return "redirect:/radiografia/" + analysis.getId();
        } catch (MonthlyLimitException e) {
            redirectAttributes.addFlashAttribute("info", e.getMessage());
            return "redirect:/inicio";
        } catch (IllegalArgumentException | ClaudeAnalysisException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            redirectAttributes.addFlashAttribute("fullName", fullName);
            redirectAttributes.addFlashAttribute("identification", identification);
            redirectAttributes.addFlashAttribute("description", description);
            redirectAttributes.addFlashAttribute("selected", Questionnaire.selectedById(params));
            return "redirect:/radiografia/nueva";
        }
    }

    @GetMapping("/radiografia/{id}")
    public String result(@PathVariable Long id, Authentication authentication, Model model) {
        User user = currentUser(authentication);
        CreditAnalysis analysis = findOwned(id, user);
        model.addAttribute("user", user);
        model.addAttribute("analysis", analysis);
        model.addAttribute("result", analysisService.toResult(analysis));
        model.addAttribute("answers", analysisService.answersOf(analysis));
        return "radiografia";
    }

    @GetMapping("/radiografia/{id}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long id, Authentication authentication) {
        User user = currentUser(authentication);
        CreditAnalysis analysis = findOwned(id, user);

        AnalysisResult result = analysisService.toResult(analysis);
        byte[] pdf = pdfReportService.generateReport(analysis, result, user.getFullName(),
                user.getIdentification(), analysisService.answersOf(analysis));

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("radiografia-crediticia-" + analysis.getId() + ".pdf")
                        .build()
                        .toString())
                .body(pdf);
    }

    private CreditAnalysis findOwned(Long id, User user) {
        return analysisService.findForUser(id, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Radiografía no encontrada"));
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
}
