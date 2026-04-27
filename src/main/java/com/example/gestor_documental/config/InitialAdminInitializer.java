package com.example.gestor_documental.config;

import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.repository.UsuarioRepository;
import com.example.gestor_documental.enums.RolUsuario;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("prod")
@RequiredArgsConstructor
public class InitialAdminInitializer implements ApplicationRunner {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.initial-admin.email:}")
    private String adminEmail;

    @Value("${app.initial-admin.password:}")
    private String adminPassword;

    @Override
    public void run(ApplicationArguments args) {
        boolean existeAdmin = usuarioRepository.existsByRolUsuario(RolUsuario.ADMIN);

        if (existeAdmin) {
            return;
        }

        if (!StringUtils.hasText(adminEmail) || !StringUtils.hasText(adminPassword)) {
            throw new IllegalStateException(
                    "No existe ningún usuario ADMIN y no se han configurado APP_ADMIN_EMAIL y APP_ADMIN_PASSWORD");
        }

        Usuario admin = new Usuario();
        admin.setNombre("Administrador");
        admin.setApellidos("Sistema");
        admin.setEmail(adminEmail);
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setRolUsuario(RolUsuario.ADMIN);
        admin.setActivo(true);

        usuarioRepository.save(admin);
    }
}