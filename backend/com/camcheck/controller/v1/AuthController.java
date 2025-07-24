package com.camcheck.controller.v1;

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
 * Unified authentication controller for all clients
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "API endpoints for authentication")
@Slf4j
public class AuthController extends BaseController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenService jwtTokenService;
    private final JwtConfig jwtConfig;

    @Value("${camcheck.default-quality:medium}")
    private String defaultQuality;

    @Value("${camcheck.background-mode-enabled:false}")
    private boolean backgroundModeEnabled;

    @Autowired
    public AuthController(
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
        log.info("Authentication request for user: {}", request.getUsername());
        
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
            
            // Create app configuration
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
                    .expiresIn(jwtConfig.getExpirationMs() / 1000)
                    .username(authentication.getName())
                    .roles(roles)
                    .userData(new HashMap<>())
                    .requiresPasswordChange(false)
                    .appConfig(appConfig)
                    .build();
            
            log.info("Authentication successful for user: {}", request.getUsername());
            
            return success("Authentication successful", Map.of("auth", authResponse));
        } catch (BadCredentialsException e) {
            log.warn("Authentication failed for user: {}", request.getUsername());
            return error("Invalid username or password", HttpStatus.UNAUTHORIZED.value());
        } catch (Exception e) {
            log.error("Authentication error", e);
            return error("Authentication error: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
    }

    /**
     * Refresh JWT token
     *
     * @param request Refresh token request
     * @return New JWT token
     */
    @PostMapping("/refresh")
    @Operation(summary = "Refresh token", description = "Get new access token using refresh token")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Token refreshed successfully", content = @Content(mediaType = "application/json")),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid refresh token", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<ApiResponse> refreshToken(@RequestBody RefreshTokenRequest request) {
        log.debug("Token refresh request received");
        
        try {
            // Validate refresh token
            if (!jwtTokenService.validateRefreshToken(request.getRefreshToken())) {
                return error("Invalid refresh token", HttpStatus.UNAUTHORIZED.value());
            }
            
            // Get username from refresh token
            String username = jwtTokenService.getUsernameFromRefreshToken(request.getRefreshToken());
            
            // Get user roles (you might want to load these from a user service)
            List<String> roles = jwtTokenService.getRolesFromRefreshToken(request.getRefreshToken());
            
            // Generate new access token
            String newAccessToken = jwtTokenService.generateToken(username, roles);
            
            Map<String, Object> tokenData = new HashMap<>();
            tokenData.put("accessToken", newAccessToken);
            tokenData.put("tokenType", jwtConfig.getTokenPrefix().trim());
            tokenData.put("expiresIn", jwtConfig.getExpirationMs() / 1000);
            
            return success("Token refreshed successfully", tokenData);
        } catch (Exception e) {
            log.error("Token refresh error", e);
            return error("Token refresh error: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
    }

    /**
     * Validate JWT token
     *
     * @param authHeader Authorization header
     * @return Validation result
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
            return error("Missing or invalid Authorization header", HttpStatus.UNAUTHORIZED.value());
        }
        
        String token = jwtTokenService.getTokenFromHeader(authHeader);
        
        try {
            if (jwtTokenService.validateToken(token)) {
                String username = jwtTokenService.getUsernameFromToken(token);
                List<String> roles = jwtTokenService.getRolesFromToken(token);
                
                Map<String, Object> validationData = new HashMap<>();
                validationData.put("valid", true);
                validationData.put("username", username);
                validationData.put("roles", roles);
                
                return success("Token is valid", validationData);
            } else {
                return error("Token is invalid", HttpStatus.UNAUTHORIZED.value());
            }
        } catch (Exception e) {
            log.error("Token validation error", e);
            return error("Token validation error: " + e.getMessage(), HttpStatus.UNAUTHORIZED.value());
        }
    }

    /**
     * Logout user
     *
     * @param authentication Authentication object
     * @return Logout result
     */
    @PostMapping("/logout")
    @Operation(summary = "Logout user", description = "Invalidate user session and tokens")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Logout successful", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<ApiResponse> logout(Authentication authentication) {
        if (authentication != null) {
            String username = authentication.getName();
            log.info("Logout request for user: {}", username);
            
            // Clear security context
            SecurityContextHolder.clearContext();
            
            // Here you might want to add the token to a blacklist or invalidate it
            // depending on your token management strategy
            
            return success("Logout successful");
        }
        return success("No active session");
    }
} 