package com.webevaluator.auth.security;

import com.webevaluator.auth.service.ApiKeyService;
import com.webevaluator.core.domain.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final ApiKeyService apiKeyService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String apiKeyHeader = request.getHeader("X-API-Key");

        if (apiKeyHeader != null && !apiKeyHeader.trim().isEmpty()) {
            // ВАЖНО: trim() убирает случайные пробелы или переносы строк из терминала
            User user = apiKeyService.validateAndGetUser(apiKeyHeader.trim());

            if (user != null) {
                // УЛУЧШЕНИЕ 1: Кладем реального пользователя в контекст, а не "api-key-user"
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        user.getUsername(),
                        null,
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
                );
                SecurityContextHolder.getContext().setAuthentication(auth);
            } else {
                // ВАЖНО: выбрасываем исключение, а не делаем sendError.
                // Это позволит Spring Security корректно отдать 401 с правильным сообщением.
                throw new BadCredentialsException("Invalid or revoked API Key");
            }
        }

        filterChain.doFilter(request, response);
    }
}