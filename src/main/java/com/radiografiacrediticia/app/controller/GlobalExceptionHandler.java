package com.radiografiacrediticia.app.controller;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Convierte errores de carga de archivos en mensajes amigables en el dashboard.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleMaxUpload(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("error", "Las capturas superan el tamaño permitido (máx. 5 MB cada una y 20 MB en total).");
        return "redirect:/dashboard";
    }
}
