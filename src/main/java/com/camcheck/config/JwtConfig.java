package com.camcheck.config;

import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Configuration for JWT authentication
 */
@Configuration
@Data
public class JwtConfig {

    @Value("${camcheck.jwt.secret-key}")
    private String secretKey;
    
    @Value("${camcheck.jwt.expiration-ms}")
    private long expirationMs;
    
    @Value("${camcheck.jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;
    
    @Value("${camcheck.jwt.issuer}")
    private String issuer;
    
    @Value("${camcheck.jwt.token-prefix}")
    private String tokenPrefix;
    
    @Value("${camcheck.jwt.header}")
    private String header;
    
    /**
     * Get the secret key for JWT signature
     * @return SecretKey for JWT
     */
    @Bean
    public SecretKey jwtSecretKey() {
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
    
    /**
     * Password encoder for user authentication
     * @return BCrypt password encoder
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
} 