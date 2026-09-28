package com.midatacredito.app.controller;

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
        redirectAttributes.addFlashAttribute("error", "La imagen supera el tamaño máximo permitido de 5 MB.");
        return "redirect:/dashboard";
    }
}
