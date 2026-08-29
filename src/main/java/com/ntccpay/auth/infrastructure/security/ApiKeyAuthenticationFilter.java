package com.ntccpay.auth.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * X-API-Key authentication: a valid key authenticates the request as its merchant.
 * An absent/invalid key simply leaves the context empty; protected routes then
 * answer 401 through the entry point.
 */
public final class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-API-Key";

    private final ApiKeyProperties properties;

    public ApiKeyAuthenticationFilter(ApiKeyProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String apiKey = request.getHeader(HEADER);
        if (apiKey != null && !apiKey.isBlank()) {
            String merchant = properties.merchantFor(apiKey);
            if (merchant != null) {
                var authentication = new PreAuthenticatedAuthenticationToken(
                        merchant, apiKey, List.of(new SimpleGrantedAuthority("ROLE_MERCHANT")));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        chain.doFilter(request, response);
    }
}
