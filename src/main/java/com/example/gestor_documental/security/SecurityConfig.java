package com.example.gestor_documental.security;

import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

        private final UserDetailsService customUserDetailsService;
        private final CustomSuccessHandler customSuccessHandler;

        @Bean
        public PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }

        @Bean
        public DaoAuthenticationProvider authenticationProvider() {
                DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(customUserDetailsService);

                authProvider.setPasswordEncoder(passwordEncoder());
                return authProvider;
        }

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

                http
                                .authenticationProvider(authenticationProvider())
                                .httpBasic(AbstractHttpConfigurer::disable)
                                .csrf(csrf -> csrf.ignoringRequestMatchers(request -> {
                                        String uri = request.getRequestURI();
                                        return uri.startsWith("/api/");
                                }))
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers("/api/login", "/assets/**", "/favicon.svg", "/icons.svg", "/actuator/health")
                                                .permitAll()
                                                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                                                .requestMatchers("/api/cliente/**").hasRole("CLIENTE")
                                                .requestMatchers("/api/**", "/documentos/**").authenticated()
                                                .requestMatchers("/actuator/**").hasRole("ADMIN")
                                                .anyRequest().permitAll())
                                .formLogin(form -> form
                                                .loginPage("/login")
                                                .loginProcessingUrl("/api/login")
                                                .successHandler(customSuccessHandler)
                                                .failureHandler((request, response, exception) -> writeJsonError(
                                                                response,
                                                                HttpServletResponse.SC_UNAUTHORIZED,
                                                                "UNAUTHORIZED",
                                                                "Email o contrasena incorrectos."))
                                                .permitAll())
                                .logout(logout -> logout
                                                .logoutUrl("/api/logout")
                                                .logoutSuccessHandler((request, response, authentication) -> response.setStatus(HttpServletResponse.SC_NO_CONTENT))
                                                .permitAll())
                                .exceptionHandling(exception -> exception
                                                .defaultAuthenticationEntryPointFor(apiAuthenticationEntryPoint(), request -> request.getRequestURI().startsWith("/api/"))
                                                .defaultAccessDeniedHandlerFor(apiAccessDeniedHandler(), request -> request.getRequestURI().startsWith("/api/"))
                                                .accessDeniedPage("/acceso-denegado"))
                                .headers(headers -> headers
                                                .frameOptions(frameOptions -> frameOptions.sameOrigin()));

                return http.build();
        }

        private AuthenticationEntryPoint apiAuthenticationEntryPoint() {
                return (request, response, authException) -> writeJsonError(
                                response,
                                HttpServletResponse.SC_UNAUTHORIZED,
                                "UNAUTHORIZED",
                                "Sesion no iniciada");
        }

        private AccessDeniedHandler apiAccessDeniedHandler() {
                return (request, response, accessDeniedException) -> writeJsonError(
                                response,
                                HttpServletResponse.SC_FORBIDDEN,
                                "FORBIDDEN",
                                "No tienes permiso para acceder a este recurso");
        }

        private void writeJsonError(HttpServletResponse response, int status, String error, String message) throws java.io.IOException {
                response.setStatus(status);
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"" + error + "\",\"message\":\"" + message + "\"}");
                response.getWriter().flush();
        }
}
