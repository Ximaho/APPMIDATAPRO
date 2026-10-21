package com.radiografiacrediticia.app.config;

import com.radiografiacrediticia.app.model.User;
import com.radiografiacrediticia.app.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * Crea o marca la cuenta de administrador al arrancar, a partir de las variables de entorno
 * {@code ADMIN_EMAIL} y {@code ADMIN_PASSWORD} (las credenciales nunca se escriben en el código).
 * <ul>
 *   <li>Si la cuenta no existe y hay contraseña, se crea con esa contraseña.</li>
 *   <li>Si ya existe (por ejemplo, registrada desde la página), solo se marca como administrador
 *   y conserva su contraseña.</li>
 * </ul>
 * El administrador no tiene límite mensual y su identificación no queda ligada a la cuenta.
 */
@Component
public class AdminAccountInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    public AdminAccountInitializer(UserRepository userRepository,
                                   PasswordEncoder passwordEncoder,
                                   @Value("${radiografia.admin.email:}") String email,
                                   @Value("${radiografia.admin.password:}") String password) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.email = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        this.password = password == null ? "" : password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(email)) {
            return;
        }
        userRepository.findByEmailIgnoreCase(email).ifPresentOrElse(user -> {
            if (!user.isAdmin()) {
                user.setAdmin(true);
                userRepository.save(user);
            }
            log.info("Cuenta de administrador activa: {}", email);
        }, () -> {
            if (password.length() < 8) {
                log.warn("ADMIN_EMAIL={} no existe y ADMIN_PASSWORD no está definida o tiene menos de 8 caracteres: "
                        + "no se creó la cuenta de administrador.", email);
                return;
            }
            User admin = new User("Administrador", email, passwordEncoder.encode(password));
            admin.setAdmin(true);
            userRepository.save(admin);
            log.info("Cuenta de administrador creada: {}", email);
        });
    }
}
