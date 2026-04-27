package com.example.gestor_documental.config;

import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.security.crypto.password.PasswordEncoder;

@Component
@RequiredArgsConstructor
@Profile("dev")
public class TestUserSetter implements CommandLineRunner {
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (!usuarioRepository.findByEmail("admin@gestoria.com").isPresent()) {
            Usuario admin = new Usuario();
            admin.setNombre("Admin");
            admin.setApellidos("System");
            admin.setEmail("admin@gestoria.com");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setRolUsuario(RolUsuario.ADMIN);
            admin.setActivo(true);
            usuarioRepository.save(admin);
            System.out.println("Admin user created: admin@gestoria.com / admin123");
        } else {
            Usuario admin = usuarioRepository.findByEmail("admin@gestoria.com").get();
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setRolUsuario(RolUsuario.ADMIN);
            admin.setActivo(true);
            usuarioRepository.save(admin);
            System.out.println("Admin user updated: admin@gestoria.com / admin123");
        }
    }
}
