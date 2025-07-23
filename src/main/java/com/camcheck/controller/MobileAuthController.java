package com.camcheck.controller;

import com.camcheck.config.JwtConfig;
import com.camcheck.model.ApiResponse;
import com.camcheck.model.AuthRequest;
import com.camcheck.model.AuthResponse;
import com.camcheck.model.RefreshTokenRequest;
import com.camcheck.service.JwtTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller for mobile authentication (JWT-based)
 */
@RestController
@RequestMapping("/api/v2/auth")
@Tag(name = "Mobile Authentication", description = "Mobile API endpoints for authentication")
@Slf4j
public class MobileAuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenService jwtTokenService;
    private final JwtConfig jwtConfig;

    @Value("${camcheck.mobile.default-quality:medium}")
    private String defaultQuality;

    @Value("${camcheck.mobile.background-mode-enabled:false}")
    private boolean backgroundModeEnabled;

    @Autowired
    public MobileAuthController(
            AuthenticationManager authenticationManager,
            JwtTokenService jwtTokenService,
            JwtConfig jwtConfig) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenService = jwtTokenService;
        this.jwtConfig = jwtConfig;
    }

    /**
     * Authenticate user and generate JWT token
     *
     * @param request Login request with username and password
     * @return JWT token response
     */
    @PostMapping("/login")
    @Operation(summary = "Authenticate user", description = "Authenticate user with username and password, return JWT token")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Authentication successful", content = @Content(mediaType = "application/json")),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication failed", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<ApiResponse> login(@RequestBody AuthRequest request, HttpServletRequest httpRequest) {
        log.info("Mobile authentication request for user: {}", request.getUsername());
        
        try {
            // Authenticate user with Spring Security
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()
                    )
            );

            // Set authentication in security context
            SecurityContextHolder.getContext().setAuthentication(authentication);
            
            // Get user roles
            List<String> roles = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toList());

            // Generate JWT token
            String accessToken = jwtTokenService.generateToken(authentication.getName(), roles);
            
            // Generate refresh token
            String refreshToken = jwtTokenService.generateRefreshToken(authentication.getName());
            
            // Create app configuration for mobile client
            Map<String, Object> appConfig = new HashMap<>();
            appConfig.put("videoQuality", defaultQuality);
            appConfig.put("backgroundModeEnabled", backgroundModeEnabled);
            appConfig.put("streamingEnabled", true);
            appConfig.put("recordingEnabled", true);
            appConfig.put("pushNotificationsEnabled", true);
            
            // Build authentication response
            AuthResponse authResponse = AuthResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .tokenType(jwtConfig.getTokenPrefix().trim())
                    .expiresIn(jwtConfig.getExpirationMs() / 1000) // Convert to seconds
                    .username(authentication.getName())
                    .roles(roles)
                    .userData(new HashMap<>()) // Placeholder for user data
                    .requiresPasswordChange(false) // No password change required by default
                    .appConfig(appConfig)
                    .build();
            
            // TODO: Store device information if provided

            log.info("Mobile authentication successful for user: {}", request.getUsername());
            
            return ResponseEntity.ok(ApiResponse.success("Authentication successful", Map.of("auth", authResponse)));
        } catch (BadCredentialsException e) {
            log.warn("Mobile authentication failed for user: {}", request.getUsername());
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Invalid username or password"));
        } catch (Exception e) {
            log.error("Mobile authentication error", e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Authentication error: " + e.getMessage()));
        }
    }

    /**
     * Refresh JWT token
     *
     * @param request Refresh token request
     * @return New JWT token
     */
    @PostMapping("/refresh")
    @Operation(summary = "Refresh token", description = "Refresh JWT token using refresh token")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Token refreshed successfully", content = @Content(mediaType = "application/json")),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid refresh token", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<ApiResponse> refreshToken(@RequestBody RefreshTokenRequest request) {
        log.debug("Token refresh request received");
        
        try {
            String refreshToken = request.getRefreshToken();
            
            // Validate refresh token
            if (!jwtTokenService.validateToken(refreshToken)) {
                log.warn("Invalid refresh token");
                return ResponseEntity
                        .status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("Invalid refresh token"));
            }
            
            // Extract username from refresh token
            String username = jwtTokenService.getUsernameFromToken(refreshToken);
            
            // For a proper implementation, you would:
            // 1. Verify the refresh token in a persistent store
            // 2. Check if the refresh token has been revoked
            // 3. Check if the user still has access

            // Here we'll just re-authenticate the user
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            
            // Get user roles
            List<String> roles = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toList());

            // Generate new access token
            String newAccessToken = jwtTokenService.generateToken(username, roles);
            
            // Generate new refresh token
            String newRefreshToken = jwtTokenService.generateRefreshToken(username);
            
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("accessToken", newAccessToken);
            responseData.put("refreshToken", newRefreshToken);
            responseData.put("tokenType", jwtConfig.getTokenPrefix().trim());
            responseData.put("expiresIn", jwtConfig.getExpirationMs() / 1000); // Convert to seconds
            
            log.debug("Token refreshed successfully for user: {}", username);
            
            return ResponseEntity.ok(ApiResponse.success("Token refreshed successfully", responseData));
        } catch (Exception e) {
            log.error("Token refresh error", e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Token refresh error: " + e.getMessage()));
        }
    }

    /**
     * Logout user
     *
     * @return Success message
     */
    @PostMapping("/logout")
    @Operation(summary = "Logout", description = "Invalidate user's tokens")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Logout successful", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<ApiResponse> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.debug("Logout request received");
        
        // In a full implementation, you would:
        // 1. Extract the token
        // 2. Add it to a blocklist or token revocation list
        // 3. Remove any device registrations if needed
        
        // Clear security context
        SecurityContextHolder.clearContext();
        
        return ResponseEntity.ok(ApiResponse.success("Logout successful"));
    }

    /**
     * Validate token
     *
     * @return Token validation result
     */
    @GetMapping("/validate")
    @Operation(summary = "Validate token", description = "Validate JWT token")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Token is valid", content = @Content(mediaType = "application/json")),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Token is invalid", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<ApiResponse> validateToken(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.debug("Token validation request received");
        
        if (authHeader == null || !authHeader.startsWith(jwtConfig.getTokenPrefix())) {
            log.warn("Token validation failed: Missing or invalid Authorization header");
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Missing or invalid Authorization header"));
        }
        
        String token = jwtTokenService.getTokenFromHeader(authHeader);
        
        if (jwtTokenService.validateToken(token)) {
            String username = jwtTokenService.getUsernameFromToken(token);
            List<String> roles = jwtTokenService.getAuthoritiesFromToken(token);
            
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("username", username);
            responseData.put("roles", roles);
            responseData.put("valid", true);
            
            log.debug("Token validation successful for user: {}", username);
            
            return ResponseEntity.ok(ApiResponse.success("Token is valid", responseData));
        } else {
            log.warn("Token validation failed: Invalid token");
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Invalid token"));
        }
    }
} 