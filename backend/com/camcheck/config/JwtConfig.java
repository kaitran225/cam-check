package com.camcheck.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Data
@Component
public class JwtConfig {

    @Value("${JWT_SECRET:6u9h4HzKqBXbpnhLXktYoVm7sR2jDfGc}")
    private String secret;

    @Value("${JWT_EXPIRATION_MS:86400000}")
    private long expirationMs;

    @Value("${JWT_TOKEN_PREFIX:Bearer }")
    private String tokenPrefix;

    @Value("${JWT_HEADER:Authorization}")
    private String header;

    @Value("${JWT_ISSUER:camcheck}")
    private String issuer;
} 