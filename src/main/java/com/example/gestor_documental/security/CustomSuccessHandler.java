package com.example.gestor_documental.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collection;

@Component
public class CustomSuccessHandler implements AuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                            HttpServletResponse response,
                                            Authentication authentication) throws IOException {

        String targetUrl = resolveTargetUrl(authentication.getAuthorities());

        if (request.getRequestURI().startsWith("/api/")) {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType("application/json");
            response.getWriter().write("{\"redirect\":\"" + targetUrl + "\"}");
            response.getWriter().flush();
            return;
        }

        response.sendRedirect(targetUrl);
    }

    private String resolveTargetUrl(Collection<? extends GrantedAuthority> authorities) {
        for (GrantedAuthority auth : authorities) {
            if (auth.getAuthority().equals("ROLE_ADMIN")) {
                return "/admin/dashboard";
            }
            if (auth.getAuthority().equals("ROLE_CLIENTE")) {
                return "/cliente/dashboard";
            }
        }

        return "/login?error";
    }
}

