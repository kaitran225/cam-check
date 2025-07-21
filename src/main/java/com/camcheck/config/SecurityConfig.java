package com.camcheck.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.Arrays;
import java.util.List;

/**
 * Security configuration for CamCheck
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${camcheck.security.username}")
    private String username;
    
    @Value("${camcheck.security.password}")
    private String password;
    
    @Value("${camcheck.security.trusted-ips:127.0.0.1,::1}")
    private String trustedIps;
    
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Create a request matcher for trusted IPs
        RequestMatcher trustedIpMatcher = request -> {
            List<String> ipList = Arrays.asList(trustedIps.split(","));
            return ipList.stream().anyMatch(ip -> new IpAddressMatcher(ip.trim()).matches(request));
        };
        
        http
            .authorizeHttpRequests(authorize -> authorize
                // Static resources
                .requestMatchers("/resources/**", "/static/**", "/css/**", "/js/**", "/images/**", "/webjars/**").permitAll()
                // Swagger UI and API docs
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/api-docs", "/api-docs/**", "/v3/api-docs/**").permitAll()
                // Allow receiver endpoint without authentication
                .requestMatchers("/receiver", "/receiver/**").permitAll()
                // WebSocket endpoints
                .requestMatchers("/ws/**").permitAll()
                // Allow access from trusted IPs without authentication
                .requestMatchers(request -> trustedIpMatcher.matches(request)).permitAll()
                // All other requests need authentication
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/")
                .permitAll()
            )
            .logout(logout -> logout
                .permitAll()
            )
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/**", "/api/v1/**", "/ws/**", "/api-docs/**", "/swagger-ui/**", "/v3/api-docs/**", "/receiver/**")
            );
        
        return http.build();
    }
    
    @Bean
    public InMemoryUserDetailsManager userDetailsService() {
        UserDetails user = User.builder()
            .username(username)
            .password(passwordEncoder().encode(password))
            .roles("USER", "ADMIN")
            .build();
        
        return new InMemoryUserDetailsManager(user);
    }   
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
} 