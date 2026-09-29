package com.midatacredito.app.controller;

import com.midatacredito.app.dto.AnalysisResult;
import com.midatacredito.app.model.CreditAnalysis;
import com.midatacredito.app.model.User;
import com.midatacredito.app.repository.UserRepository;
import com.midatacredito.app.service.ClaudeAiService;
import com.midatacredito.app.service.ClaudeAnalysisException;
import com.midatacredito.app.service.CreditAnalysisService;
import com.midatacredito.app.service.PdfReportService;
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

import java.util.List;

/**
 * Panel principal: carga de la captura + descripción, ejecución del análisis con Claude,
 * historial de consultas y descarga del informe PDF.
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

    @GetMapping("/")
    public String root() {
        return "redirect:/dashboard";
    }

    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(name = "analysisId", required = false) Long analysisId,
                            Authentication authentication,
                            Model model) {
        User user = currentUser(authentication);
        model.addAttribute("user", user);
        model.addAttribute("history", analysisService.history(user));
        model.addAttribute("maxDescriptionChars", ClaudeAiService.MAX_DESCRIPTION_CHARS);
        model.addAttribute("maxImages", ClaudeAiService.MAX_IMAGES);

        if (analysisId != null) {
            analysisService.findForUser(analysisId, user).ifPresent(analysis -> {
                model.addAttribute("analysis", analysis);
                model.addAttribute("result", analysisService.toResult(analysis));
            });
        }
        return "dashboard";
    }

    @PostMapping(value = "/dashboard/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String analyze(@RequestParam(name = "images", required = false) List<MultipartFile> images,
                          @RequestParam("description") String description,
                          Authentication authentication,
                          RedirectAttributes redirectAttributes) {
        User user = currentUser(authentication);
        try {
            CreditAnalysis analysis = analysisService.analyzeAndSave(user, images == null ? List.of() : images, description);
            redirectAttributes.addFlashAttribute("success", "Análisis completado correctamente.");
            return "redirect:/dashboard?analysisId=" + analysis.getId();
        } catch (IllegalArgumentException | ClaudeAnalysisException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            redirectAttributes.addFlashAttribute("description", description);
            return "redirect:/dashboard";
        }
    }

    @GetMapping("/dashboard/analysis/{id}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long id, Authentication authentication) {
        User user = currentUser(authentication);
        CreditAnalysis analysis = analysisService.findForUser(id, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Análisis no encontrado"));

        AnalysisResult result = analysisService.toResult(analysis);
        byte[] pdf = pdfReportService.generateReport(analysis, result, user.getFullName());

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("informe-crediticio-" + analysis.getId() + ".pdf")
                        .build()
                        .toString())
                .body(pdf);
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
}
