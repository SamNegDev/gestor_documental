package com.example.gestor_documental.config;

import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
@Profile("dev")
public class TestUserSetter implements CommandLineRunner {
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.dev-admin.email:}")
    private String adminEmail;

    @Value("${app.dev-admin.password:}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        if (!StringUtils.hasText(adminEmail) || !StringUtils.hasText(adminPassword)) {
            return;
        }

        if (!usuarioRepository.findByEmail(adminEmail).isPresent()) {
            Usuario admin = new Usuario();
            admin.setNombre("Admin");
            admin.setApellidos("System");
            admin.setEmail(adminEmail);
            admin.setPassword(passwordEncoder.encode(adminPassword));
            admin.setRolUsuario(RolUsuario.ADMIN);
            admin.setActivo(true);
            usuarioRepository.save(admin);
        } else {
            Usuario admin = usuarioRepository.findByEmail(adminEmail).get();
            admin.setPassword(passwordEncoder.encode(adminPassword));
            admin.setRolUsuario(RolUsuario.ADMIN);
            admin.setActivo(true);
            usuarioRepository.save(admin);
        }
    }
}
