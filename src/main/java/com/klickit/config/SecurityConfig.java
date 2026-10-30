package com.klickit.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.klickit.common.dto.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Collections;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final JwtAuthenticationEntryPoint jwtEntryPoint;
    private final UserDetailsService userDetailsService;

    @Value("${klickit.cors.allowed-origins:}")
    private String allowedOrigins;

    private static final List<String> TRUSTED_PRODUCTION_ORIGINS = List.of(
            "https://klickit-iycniu6bm-ajha10804-3583.vercel.app",
            "https://klickit-ajha10804-3583.vercel.app",
            "https://klickit.vercel.app",
            "https://klickit-gar0xgcu7-ajha10804-3583.vercel.app"
    );


    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthFilter,
            JwtAuthenticationEntryPoint jwtEntryPoint,
            @Lazy UserDetailsService userDetailsService) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.jwtEntryPoint = jwtEntryPoint;
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(jwtEntryPoint)
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            OBJECT_MAPPER.writeValue(
                                    response.getOutputStream(),
                                    ApiResponse.error("Access denied"));
                        }))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Permit CORS preflight requests across all endpoints
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Public authentication endpoints
                        .requestMatchers("/auth/**").permitAll()

                        // Actuator health and liveness/readiness probes (minimal exposure)
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**"
                        ).permitAll()

                        // OpenAPI / Swagger UI
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/swagger-resources/**",
                                "/webjars/**"
                        ).permitAll()

                        // Static frontend resources
                        .requestMatchers(
                                "/",
                                "/index.html",
                                "/static/**",
                                "/favicon.ico"
                        ).permitAll()

                        // Public customer product browsing endpoints
                        .requestMatchers(HttpMethod.GET, "/products/**").permitAll()

                        // Public customer cart endpoints
                        .requestMatchers(HttpMethod.GET, "/cart/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/cart/add").permitAll()
                        .requestMatchers(HttpMethod.POST, "/cart/remove").permitAll()

                        // Authenticated customer checkout endpoint
                        .requestMatchers(HttpMethod.POST, "/orders/checkout").hasRole("CUSTOMER")

                        // Admin endpoints
                        .requestMatchers("/admin/**").hasRole("ADMIN")

                        // Product management (POST/PUT/DELETE) - Admin only
                        .requestMatchers(HttpMethod.POST, "/products/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/products/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/products/**").hasRole("ADMIN")

                        // Delivery partner endpoints
                        .requestMatchers(HttpMethod.POST, "/delivery/partners").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/delivery/partners").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/delivery/orders").hasAnyRole("DELIVERY_PARTNER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/delivery/orders/*").hasAnyRole("DELIVERY_PARTNER", "ADMIN")
                        .requestMatchers("/delivery/**").hasRole("DELIVERY_PARTNER")

                        // Live delivery tracking endpoints
                        .requestMatchers(HttpMethod.POST, "/tracking/*/location").hasRole("DELIVERY_PARTNER")
                        .requestMatchers(HttpMethod.GET, "/tracking/*").authenticated()

                        // User profile endpoints
                        .requestMatchers("/user/**").authenticated()

                        // Everything else requires authentication
                        .anyRequest().authenticated()
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        Set<String> origins = new LinkedHashSet<>(TRUSTED_PRODUCTION_ORIGINS);
        if (allowedOrigins != null && !allowedOrigins.isBlank()) {
            Arrays.stream(allowedOrigins.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(s -> s.replaceAll("/+$", ""))
                    .forEach(origins::add);
        }

        configuration.setAllowedOrigins(new ArrayList<>(origins));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With", "Accept", "Origin"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
