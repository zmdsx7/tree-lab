package com.treelab;

import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.*;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean SecurityContextRepository contextRepository() { return new HttpSessionSecurityContextRepository(); }
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository repository) throws Exception {
        return http
            .securityContext(c -> c.securityContextRepository(repository))
            // Session-based CSRF protection remains enabled, including login/register.
            .authorizeHttpRequests(a -> a
                .requestMatchers("/", "/index.html", "/style.css", "/app.js", "/auth.js", "/favicon.ico", "/error").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/auth/me", "/api/auth/csrf").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/calculate", "/api/auth/login", "/api/auth/register").permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req,res,ex) -> {
                    res.setStatus(401);res.setContentType("application/json;charset=UTF-8");
                    res.getWriter().write("{\"message\":\"กรุณาเข้าสู่ระบบก่อน\"}");
                })
                .accessDeniedHandler((req,res,ex) -> {
                    res.setStatus(403);res.setContentType("application/json;charset=UTF-8");
                    res.getWriter().write("{\"message\":\"สิทธิ์หรือเซสชันไม่ถูกต้อง กรุณาโหลดหน้าเว็บใหม่\"}");
                }))
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(l -> l.logoutUrl("/api/auth/logout")
                .invalidateHttpSession(true).clearAuthentication(true).deleteCookies("JSESSIONID")
                .logoutSuccessHandler((req,res,auth) -> {
                    res.setContentType("application/json");res.getWriter().write("{\"ok\":true}");
                }))
            .build();
    }
}
