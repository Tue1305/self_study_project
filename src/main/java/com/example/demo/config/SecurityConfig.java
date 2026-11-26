package com.example.demo.config;

import com.example.demo.security.CustomUserDetailsService;
import com.example.demo.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Cache-Control", "*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .requiresChannel(channel -> channel.anyRequest().requiresSecure())
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            // Return 401 Unauthorized for API requests, redirect HTML pages to login
                            if (request.getRequestURI().startsWith("/api/")) {
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized: Token is missing or invalid");
                            } else {
                                response.sendRedirect("/login");
                            }
                        })
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/index.html",
                                "/book",
                                "/book/**",
                                "/booking",
                                "/booking/**",
                                "/booking.html",
                                "/manage-booking",
                                "/manage-reservation",
                                "/admin/dashboard",
                                "/admin/calendar",
                                "/admin/customers",
                                "/admin/nail-techs",
                                "/admin",
                                "/staff",
                                "/staff/portal",
                                "/tech-portal",
                                "/staff-portal.html",
                                "/admin-calendar.html",
                                "/admin-dashboard.html",
                                "/admin-customers.html",
                                "/nail-techs.html",
                                "/services",
                                "/services.html",
                                "/login",
                                "/login.html",
                                "/signup",
                                "/signup.html",
                                "/forgot-password",
                                "/forgot-password.html",
                                "/profile",
                                "/profile.html",
                                "/gift-cards",
                                "/gift-cards/**",
                                "/gift-cards.html",
                                "/giftcard",
                                "/giftcards",
                                "/dashboard",
                                "/dashboard.html",
                                "/favicon.ico",
                                "/*.css",
                                "/*.js",
                                "/js/**",
                                "/css/**",
                                "/error",
                                "/nail_collection/**",
                                "/static/**",
                                "/*.png",
                                "/*.jpg",
                                "/*.jpeg",
                                "/api/public/**",
                                "/api/tenant/config"
                        ).permitAll()
                        // Appointments & Bookings: public booking creation, availability lookup, and token-gated self-service portal
                        .requestMatchers(HttpMethod.POST, "/api/appointments", "/api/bookings").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/appointments/availability", "/api/appointments/available-slots",
                                "/api/bookings/availability", "/api/bookings/available-slots").permitAll()
                        .requestMatchers("/api/appointments/by-token/**", "/api/bookings/by-token/**").permitAll()
                        // All other appointment management endpoints require Admin or Staff authentication
                        .requestMatchers("/api/appointments", "/api/appointments/**", "/api/bookings", "/api/bookings/**")
                                .hasAnyAuthority("ROLE_ADMIN", "ADMIN", "ROLE_STAFF", "STAFF", "ROLE_NAIL_TECH")
                        // Services rules (public GET, admin-only POST/PUT/DELETE)
                        .requestMatchers(HttpMethod.GET, "/api/services", "/api/services/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/services", "/api/services/**").hasAnyAuthority("ROLE_ADMIN", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/services", "/api/services/**").hasAnyAuthority("ROLE_ADMIN", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/services", "/api/services/**").hasAnyAuthority("ROLE_ADMIN", "ADMIN")
                        // Nail-tech rules for connection (covering both exact and subpaths)
                        .requestMatchers(HttpMethod.GET, "/api/nail-techs", "/api/nail-techs/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/nail-techs", "/api/nail-techs/**").hasAnyAuthority("ROLE_ADMIN", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/nail-techs", "/api/nail-techs/**").hasAnyAuthority("ROLE_ADMIN", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/nail-techs", "/api/nail-techs/**").hasAnyAuthority("ROLE_ADMIN", "ADMIN")
                        // Showcase gallery rules (public GET, admin-only POST/PUT/DELETE)
                        .requestMatchers(HttpMethod.GET, "/api/showcase", "/api/showcase/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/showcase", "/api/showcase/**").hasAnyAuthority("ROLE_ADMIN", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/showcase", "/api/showcase/**").hasAnyAuthority("ROLE_ADMIN", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/showcase", "/api/showcase/**").hasAnyAuthority("ROLE_ADMIN", "ADMIN")
                        // Customers admin API rules
                        .requestMatchers("/api/customers", "/api/customers/**").hasAnyAuthority("ROLE_ADMIN", "ADMIN")
                        .requestMatchers("/api/pos", "/api/pos/**").hasAnyAuthority("ROLE_ADMIN", "ADMIN", "ROLE_STAFF", "STAFF", "ROLE_NAIL_TECH")
                        .requestMatchers("/api/staff-portal", "/api/staff-portal/**").hasAnyAuthority("ROLE_NAIL_TECH", "NAIL_TECH", "ROLE_STAFF", "STAFF", "ROLE_ADMIN", "ADMIN")
                        .requestMatchers("/api/gift-cards/verify", "/api/gift-cards/purchase").permitAll()
                        .requestMatchers("/api/gift-cards", "/api/gift-cards/**").hasAnyAuthority("ROLE_ADMIN", "ADMIN", "ROLE_STAFF", "STAFF", "ROLE_NAIL_TECH")
                        .requestMatchers("/api/admin/**").hasAnyAuthority("ROLE_ADMIN", "ADMIN")
                        .requestMatchers("/api/auth/**").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(CustomUserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder);
        return authProvider;
    }
}
