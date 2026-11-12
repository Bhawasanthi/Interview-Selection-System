package com.iss.backend;

import com.iss.backend.security.CustomLoginSuccessHandler;
import com.iss.backend.security.CustomUserDetailsService;
import com.iss.backend.security.LoginFailureHandler;
import com.iss.backend.service.ActivityService;
import com.iss.backend.web.VisitTrackingFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@Configuration
public class SecurityConfig {

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private CustomLoginSuccessHandler successHandler;

    @Autowired
    private LoginFailureHandler failureHandler;

    @Autowired
    private ActivityService activityService;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Used only by the AuthenticationManager below (not a bean, so Spring Security does not warn about two login sources). */
    private DaoAuthenticationProvider authenticationProvider() {

        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(userDetailsService);

        provider.setPasswordEncoder(passwordEncoder());

        return provider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/about",
                                "/contact",
                                "/schools",
                                "/student-signup",
                                "/login",
                                "/forgot-password",
                                "/reset-password",
                                "/error",
                                "/css/**",
                                "/js/**",
                                "/images/**"
                        ).permitAll()

                        .requestMatchers("/admin/**")
                        .hasRole("ADMIN")

                        .requestMatchers("/sectionhead/**")
                        .hasRole("SECTION_HEAD")

                        .requestMatchers("/panel/**")
                        .hasRole("INTERVIEW_PANEL")

                        .requestMatchers("/student/**", "/register")
                        .hasRole("STUDENT")

                        .anyRequest()
                        .authenticated()
                )

                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .successHandler(successHandler)
                        .failureHandler(failureHandler)
                        .permitAll()
                )

                .logout(logout -> logout
                        .addLogoutHandler((request, response, auth) -> {
                            if (auth != null) {
                                activityService.recordLogin(ActivityService.LOGOUT, auth.getName(),
                                        VisitTrackingFilter.roleLabel(auth), request.getRemoteAddr(),
                                        request.getHeader("User-Agent"));
                            }
                        })
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessUrl("/?logout")
                        .permitAll()
                )

                // CSRF protection is on: Thymeleaf adds the token to every th:action form automatically.
                // The token is loaded at the start of each request (not when a form is first rendered),
                // otherwise a first-time visitor's page fails once the response has started streaming.
                .csrf(csrf -> {
                    CsrfTokenRequestAttributeHandler handler = new CsrfTokenRequestAttributeHandler();
                    handler.setCsrfRequestAttributeName(null);
                    csrf.csrfTokenRequestHandler(handler);
                })

                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(ref -> ref.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .permissionsPolicyHeader(p -> p.policy("camera=(), microphone=(), geolocation=()"))
                );

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager() {

        return new ProviderManager(
                authenticationProvider()
        );
    }
}