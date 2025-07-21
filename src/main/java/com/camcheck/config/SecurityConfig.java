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
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Security configuration for CamCheck
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties
public class SecurityConfig {

    @Value("${camcheck.security.username}")
    private String legacyUsername;
    
    @Value("${camcheck.security.password}")
    private String legacyPassword;
    
    @Value("${camcheck.security.trusted-ips:127.0.0.1,::1}")
    private String trustedIps;
    
    @Value("${camcheck.security.superuser.username:${SUPERUSER_USERNAME:superuser}}")
    private String superuserUsername;
    
    @Value("${camcheck.security.superuser.password:${SUPERUSER_PASSWORD:changeme}}")
    private String superuserPassword;
    
    @ConfigurationProperties(prefix = "camcheck.security")
    @Configuration
    public static class SecurityUsers {
        private List<UserConfig> users = new ArrayList<>();
        
        public List<UserConfig> getUsers() {
            return users;
        }
        
        public void setUsers(List<UserConfig> users) {
            this.users = users;
        }
        
        public static class UserConfig {
            private String username;
            private String password;
            private String role;
            
            public String getUsername() {
                return username;
            }
            
            public void setUsername(String username) {
                this.username = username;
            }
            
            public String getPassword() {
                return password;
            }
            
            public void setPassword(String password) {
                this.password = password;
            }
            
            public String getRole() {
                return role;
            }
            
            public void setRole(String role) {
                this.role = role;
            }
        }
    }
    
    private final SecurityUsers securityUsers;
    
    public SecurityConfig(SecurityUsers securityUsers) {
        this.securityUsers = securityUsers;
    }
    
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
                // Allow client-camera endpoint without authentication
                .requestMatchers("/client-camera", "/client-camera/**").permitAll()
                // WebSocket endpoints
                .requestMatchers("/ws/**").permitAll()
                // Allow access from trusted IPs without authentication
                .requestMatchers(request -> trustedIpMatcher.matches(request)).permitAll()
                // Superuser pages require SUPERUSER role
                .requestMatchers("/superuser/**").hasRole("SUPERUSER")
                // Admin pages require ADMIN or SUPERUSER role
                .requestMatchers("/admin/**").hasAnyRole("ADMIN", "SUPERUSER")
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
                .ignoringRequestMatchers("/api/**", "/api/v1/**", "/ws/**", "/api-docs/**", "/swagger-ui/**", "/v3/api-docs/**", "/receiver/**", "/client-camera/**")
            );
        
        return http.build();
    }
    
    @Bean
    public InMemoryUserDetailsManager userDetailsService() {
        List<UserDetails> userDetailsList = new ArrayList<>();
        
        // Add configured users from application.yml
        for (SecurityUsers.UserConfig userConfig : securityUsers.getUsers()) {
            UserDetails user = User.builder()
                .username(userConfig.getUsername())
                .password(passwordEncoder().encode(userConfig.getPassword()))
                .roles(userConfig.getRole())
                .build();
            userDetailsList.add(user);
        }
        
        // Add legacy user for backward compatibility
        UserDetails legacyUser = User.builder()
            .username(legacyUsername)
            .password(passwordEncoder().encode(legacyPassword))
            .roles("USER", "ADMIN")
            .build();
        userDetailsList.add(legacyUser);
        
        // Add superuser with highest privileges
        UserDetails superuser = User.builder()
            .username(superuserUsername)
            .password(passwordEncoder().encode(superuserPassword))
            .roles("USER", "ADMIN", "SUPERUSER")
            .build();
        userDetailsList.add(superuser);
        
        return new InMemoryUserDetailsManager(userDetailsList);
    }   
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
} 