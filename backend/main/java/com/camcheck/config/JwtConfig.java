package com.camcheck.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Data
@Component
public class JwtConfig {

    @Value("${camcheck.jwt.secret}")
    private String secret;

    @Value("${camcheck.jwt.expiration-ms:86400000}")
    private long expirationMs;

    @Value("${camcheck.jwt.token-prefix:Bearer }")
    private String tokenPrefix;

    @Value("${camcheck.jwt.header:Authorization}")
    private String header;

    @Value("${camcheck.jwt.issuer:camcheck}")
    private String issuer;
} 