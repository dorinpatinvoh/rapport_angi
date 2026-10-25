package com.rapportangi.backend.controller;

import com.rapportangi.backend.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserRepository users;
    public AuthController(UserRepository users) { this.users = users; }

    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken csrf) { return new CsrfResponse(csrf.getToken()); }

    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        var user = users.findByEmail(authentication.getName()).orElseThrow();
        return new UserResponse(user.getId(), user.getFirstName() + " " + user.getLastName(), user.getEmail(),
                authentication.getAuthorities().stream().map(a -> a.getAuthority()).toList());
    }
    public record CsrfResponse(String token) {}
    public record UserResponse(Long id, String name, String email, List<String> roles) {}
}
