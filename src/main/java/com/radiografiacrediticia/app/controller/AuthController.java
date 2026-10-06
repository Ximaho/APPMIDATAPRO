package com.radiografiacrediticia.app.controller;

import com.radiografiacrediticia.app.dto.RegistrationForm;
import com.radiografiacrediticia.app.model.User;
import com.radiografiacrediticia.app.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Locale;

/**
 * Rutas públicas de autenticación: formulario de login (procesado por Spring Security)
 * y registro de nuevos usuarios.
 */
@Controller
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/login")
    public String login(Authentication authentication) {
        if (isAuthenticated(authentication)) {
            return "redirect:/dashboard";
        }
        return "login";
    }

    @GetMapping("/register")
    public String registerForm(Model model, Authentication authentication) {
        if (isAuthenticated(authentication)) {
            return "redirect:/dashboard";
        }
        model.addAttribute("form", new RegistrationForm());
        return "register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("form") RegistrationForm form,
                           BindingResult bindingResult,
                           RedirectAttributes redirectAttributes) {
        String email = form.getEmail() == null ? "" : form.getEmail().trim().toLowerCase(Locale.ROOT);

        if (form.getPassword() != null && !form.getPassword().equals(form.getConfirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "mismatch", "Las contraseñas no coinciden");
        }
        if (!email.isEmpty() && userRepository.existsByEmailIgnoreCase(email)) {
            bindingResult.rejectValue("email", "duplicate", "Ya existe una cuenta con este correo");
        }
        if (bindingResult.hasErrors()) {
            return "register";
        }

        User user = new User(form.getFullName().trim(), email, passwordEncoder.encode(form.getPassword()));
        userRepository.save(user);

        redirectAttributes.addFlashAttribute("success", "Cuenta creada correctamente. Ya puedes iniciar sesión.");
        return "redirect:/login";
    }

    private static boolean isAuthenticated(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }
}
