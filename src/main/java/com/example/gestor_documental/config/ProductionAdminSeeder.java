package com.example.gestor_documental.config;

import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
@RequiredArgsConstructor
public class ProductionAdminSeeder implements CommandLineRunner {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${APP_ADMIN_EMAIL:}")
    private String adminEmail;

    @Value("${APP_ADMIN_PASSWORD:}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        if (adminEmail == null || adminEmail.isBlank() || adminPassword == null || adminPassword.isBlank()) {
            return;
        }

        if (usuarioRepository.existsByEmail(adminEmail)) {
            return;
        }

        Usuario admin = new Usuario();
        admin.setNombre("Admin");
        admin.setApellidos("Sistema");
        admin.setEmail(adminEmail.trim());
        admin.setPassword(passwordEncoder.encode(adminPassword.trim()));
        admin.setRolUsuario(RolUsuario.ADMIN);
        admin.setActivo(true);

        usuarioRepository.save(admin);
    }
}