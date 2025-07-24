package com.camcheck.config;

import com.camcheck.service.JwtTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Filter for JWT token validation and authentication
 */
@Component
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;
    private final JwtConfig jwtConfig;
    
    // Paths that should be excluded from JWT authentication
    private final List<String> excludedPaths = Arrays.asList(
        "/login", 
        "/logout", 
        "/static", 
        "/css", 
        "/js", 
        "/images", 
        "/webjars", 
        "/error"
    );

    @Autowired
    public JwtAuthenticationFilter(JwtTokenService jwtTokenService, JwtConfig jwtConfig) {
        this.jwtTokenService = jwtTokenService;
        this.jwtConfig = jwtConfig;
    }
    
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        
        // Skip JWT filter for web UI paths
        return excludedPaths.stream().anyMatch(path::startsWith) || 
               path.equals("/") || 
               path.equals("/index.html") || 
               path.equals("/dashboard") ||
               path.equals("/favicon.ico");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,@NonNull HttpServletResponse response,@NonNull FilterChain filterChain)
            throws ServletException, IOException {
        // Get authorization header
        String authorizationHeader = request.getHeader(jwtConfig.getHeader());

        // Skip if header not present or does not match expected format
        if (authorizationHeader == null || !authorizationHeader.startsWith(jwtConfig.getTokenPrefix())) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // Extract token
            String token = jwtTokenService.getTokenFromHeader(authorizationHeader);

            // Validate token
            if (token != null && jwtTokenService.validateToken(token)) {
                // Get authentication from token
                Authentication authentication = jwtTokenService.getAuthentication(token);

                // Set authentication in context
                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("Set authentication in security context for '{}'", authentication.getName());
            } else {
                log.debug("JWT token is invalid or expired");
            }
        } catch (Exception ex) {
            log.error("Could not authenticate user with JWT token", ex);
            SecurityContextHolder.clearContext();
        }

        // Continue filter chain
        filterChain.doFilter(request, response);
    }
} 