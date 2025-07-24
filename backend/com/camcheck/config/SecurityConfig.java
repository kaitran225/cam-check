package com.camcheck.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
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

    @Value("${LEGACY_USERNAME:legacy_user}")
    private String legacyUsername;
    
    @Value("${LEGACY_PASSWORD:legacy_password}")
    private String legacyPassword;
    
    @Value("${SECURITY_TRUSTED_IPS:127.0.0.1,::1}")
    private String trustedIps;
    
    @Value("${SUPERUSER_USERNAME:superuser}")
    private String superuserUsername;
    
    @Value("${SUPERUSER_PASSWORD:superuser_password}")
    private String superuserPassword;

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    
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
            // Disable CSRF for API endpoints and login
            .csrf(csrf -> csrf
                .ignoringRequestMatchers(
                    new AntPathRequestMatcher("/api/**"), 
                    new AntPathRequestMatcher("/api/v1/**"), 
                    new AntPathRequestMatcher("/api/v2/**"),
                    new AntPathRequestMatcher("/ws/**"), 
                    new AntPathRequestMatcher("/api-docs/**"), 
                    new AntPathRequestMatcher("/swagger-ui/**"), 
                    new AntPathRequestMatcher("/v3/api-docs/**"), 
                    new AntPathRequestMatcher("/receiver/**"), 
                    new AntPathRequestMatcher("/client-camera/**"),
                    new AntPathRequestMatcher("/login"),
                    new AntPathRequestMatcher("/logout"),
                    new AntPathRequestMatcher("/h2-console/**")
                )
            )
            .authorizeHttpRequests(authorize -> authorize
                // Login page and static resources must be accessible to all
                .requestMatchers(new AntPathRequestMatcher("/login")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/login/**")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/error")).permitAll()
                
                // Static resources
                .requestMatchers(new AntPathRequestMatcher("/resources/**"), 
                                new AntPathRequestMatcher("/static/**"), 
                                new AntPathRequestMatcher("/css/**"), 
                                new AntPathRequestMatcher("/js/**"), 
                                new AntPathRequestMatcher("/images/**"), 
                                new AntPathRequestMatcher("/webjars/**")).permitAll()
                // Swagger UI and API docs
                .requestMatchers(new AntPathRequestMatcher("/swagger-ui.html"), 
                                new AntPathRequestMatcher("/swagger-ui/**"), 
                                new AntPathRequestMatcher("/api-docs"), 
                                new AntPathRequestMatcher("/api-docs/**"), 
                                new AntPathRequestMatcher("/v3/api-docs/**")).permitAll()
                // JWT authentication endpoints
                .requestMatchers(new AntPathRequestMatcher("/api/v1/auth/login"),
                                new AntPathRequestMatcher("/api/v1/auth/refresh"),
                                new AntPathRequestMatcher("/api/v2/auth/login"),
                                new AntPathRequestMatcher("/api/v2/auth/refresh")).permitAll()
                // Allow receiver endpoint without authentication
                .requestMatchers(new AntPathRequestMatcher("/receiver"), 
                                new AntPathRequestMatcher("/receiver/**")).permitAll()
                // Allow client-camera endpoint without authentication
                .requestMatchers(new AntPathRequestMatcher("/client-camera"), 
                                new AntPathRequestMatcher("/client-camera/**")).permitAll()
                // WebSocket endpoints
                .requestMatchers(new AntPathRequestMatcher("/ws/**")).permitAll()
                // H2 Console
                .requestMatchers(new AntPathRequestMatcher("/h2-console/**")).permitAll()
                // Allow access from trusted IPs without authentication
                .requestMatchers(request -> trustedIpMatcher.matches(request)).permitAll()
                // Mobile API requires authentication
                .requestMatchers(new AntPathRequestMatcher("/api/v2/**")).authenticated()
                // Superuser pages require SUPERUSER role
                .requestMatchers(new AntPathRequestMatcher("/superuser/**")).hasRole("SUPERUSER")
                // Admin pages require ADMIN or SUPERUSER role
                .requestMatchers(new AntPathRequestMatcher("/admin/**")).hasAnyRole("ADMIN", "SUPERUSER")
                // All other requests need authentication
                .anyRequest().authenticated()
            )
            // Form login configuration
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .defaultSuccessUrl("/", true)
                .failureUrl("/login?error=true")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout=true")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .permitAll()
            )
            .headers(headers -> headers
                .frameOptions(frameOptions -> frameOptions.sameOrigin()) // Required for H2 Console
            )
            // Session management - use session for web UI
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            )
            // Add JWT authentication filter for API requests
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        
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
    
    /**
     * Authentication manager bean for JWT authentication
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }
} 