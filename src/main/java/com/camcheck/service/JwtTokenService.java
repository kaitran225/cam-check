package com.camcheck.service;

import com.camcheck.config.JwtConfig;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for JWT token generation and validation
 */
@Service
@Slf4j
public class JwtTokenService {

    private final JwtConfig jwtConfig;
    private final SecretKey secretKey;

    @Autowired
    public JwtTokenService(JwtConfig jwtConfig, SecretKey secretKey) {
        this.jwtConfig = jwtConfig;
        this.secretKey = secretKey;
    }

    /**
     * Generate JWT token
     *
     * @param username User identifier
     * @param authorities User roles
     * @return JWT token
     */
    public String generateToken(String username, List<String> authorities) {
        long now = System.currentTimeMillis();
        Date issuedAt = new Date(now);
        Date expiryDate = new Date(now + jwtConfig.getExpirationMs());

        return Jwts.builder()
                .setSubject(username)
                .claim("authorities", authorities)
                .setIssuedAt(issuedAt)
                .setExpiration(expiryDate)
                .setIssuer(jwtConfig.getIssuer())
                .setId(UUID.randomUUID().toString())
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Generate refresh token with extended validity
     *
     * @param username User identifier
     * @return Refresh token
     */
    public String generateRefreshToken(String username) {
        long now = System.currentTimeMillis();
        Date issuedAt = new Date(now);
        Date expiryDate = new Date(now + jwtConfig.getRefreshExpirationMs());

        return Jwts.builder()
                .setSubject(username)
                .claim("type", "refresh")
                .setIssuedAt(issuedAt)
                .setExpiration(expiryDate)
                .setIssuer(jwtConfig.getIssuer())
                .setId(UUID.randomUUID().toString())
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Validate JWT token
     *
     * @param token JWT token
     * @return true if token is valid
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (SignatureException e) {
            log.error("Invalid JWT signature: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.error("Invalid JWT token: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.error("JWT token is expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.error("JWT token is unsupported: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("JWT claims string is empty: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Extract username from JWT token
     *
     * @param token JWT token
     * @return Username
     */
    public String getUsernameFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();

        return claims.getSubject();
    }
    
    /**
     * Extract authorities (roles) from JWT token
     *
     * @param token JWT token
     * @return List of authorities
     */
    @SuppressWarnings("unchecked")
    public List<String> getAuthoritiesFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();

        return (List<String>) claims.get("authorities");
    }

    /**
     * Get authentication from token
     *
     * @param token JWT token
     * @return Authentication object
     */
    public Authentication getAuthentication(String token) {
        String username = getUsernameFromToken(token);
        List<String> authorityStrings = getAuthoritiesFromToken(token);
        
        List<GrantedAuthority> authorities = authorityStrings.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
        
        UserDetails userDetails = new User(username, "", authorities);
        
        return new UsernamePasswordAuthenticationToken(userDetails, token, authorities);
    }

    /**
     * Get token from Authorization header
     *
     * @param authorizationHeader Authorization header value
     * @return JWT token without prefix
     */
    public String getTokenFromHeader(String authorizationHeader) {
        if (authorizationHeader != null && authorizationHeader.startsWith(jwtConfig.getTokenPrefix())) {
            return authorizationHeader.replace(jwtConfig.getTokenPrefix(), "");
        }
        return null;
    }
} 