package com.treelab;

import java.util.*;
import jakarta.servlet.http.*;

import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.*;
import org.springframework.security.core.context.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record Credentials(String name, String email, String password) {}

    private final UserService users;
    private final SecurityContextRepository repository;

    public AuthController(UserService users, SecurityContextRepository repository) {
        this.users = users;
        this.repository = repository;
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of(
            "token", token.getToken(),
            "headerName", token.getHeaderName()
        );
    }

    @GetMapping("/me")
    public Map<String, Object> me(Authentication auth) {
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("user", auth == null ? null : users.find(auth.getName()));

        return result;
    }

    @PostMapping("/register")
    public Map<String, Object> register(
            @RequestBody Credentials data,
            HttpServletRequest req,
            HttpServletResponse res) {

        return authenticate(
            users.register(data.name(), data.email(), data.password()),
            req,
            res
        );
    }

    @PostMapping("/login")
    public Map<String, Object> login(
            @RequestBody Credentials data,
            HttpServletRequest req,
            HttpServletResponse res) {

        return authenticate(
            users.login(data.email(), data.password()),
            req,
            res
        );
    }

    private Map<String, Object> authenticate(
            UserService.User user,
            HttpServletRequest req,
            HttpServletResponse res) {

        Authentication auth = UsernamePasswordAuthenticationToken.authenticated(
            user.email(),
            null,
            List.of()
        );

        new ChangeSessionIdAuthenticationStrategy()
            .onAuthentication(auth, req, res);

        new CsrfAuthenticationStrategy(
            new HttpSessionCsrfTokenRepository()
        ).onAuthentication(auth, req, res);

        SecurityContext context = SecurityContextHolder.createEmptyContext();

        context.setAuthentication(auth);

        SecurityContextHolder.setContext(context);
        repository.saveContext(context, req, res);

        return Map.of("user", user);
    }
}