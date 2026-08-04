package com.example.gestor_documental.config;

import com.example.gestor_documental.security.AuditoriaDocumentoInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class AuditoriaDocumentoWebConfig implements WebMvcConfigurer {

    private final AuditoriaDocumentoInterceptor auditoriaDocumentoInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(auditoriaDocumentoInterceptor)
                .addPathPatterns(
                        "/documentos/ver/**",
                        "/documentos/descargar/**",
                        "/api/documentos/**",
                        "/api/expedientes/*/historial/exportar",
                        "/api/cliente/expedientes/*/historial/exportar",
                        "/api/admin/ia/extraccion-ga/revisiones/exportar",
                        "/api/admin/usuarios/**",
                        "/api/admin/clientes/*/administradores/**");
    }
}
