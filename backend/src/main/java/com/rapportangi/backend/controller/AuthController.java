package com.rapportangi.backend.controller;

import com.rapportangi.backend.repository.UserRepository;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final UserRepository users;

  public AuthController(UserRepository users) {
    this.users = users;
  }

  @org.springframework.beans.factory.annotation.Autowired
  private com.rapportangi.backend.service.AccountService accounts;

  @PostMapping("/register")
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public java.util.Map<String, String> register(
    @jakarta.validation.Valid @RequestBody com.rapportangi.backend.dto.CommercialRequest request
  ) {
    accounts.create(request, false);
    return java.util.Map.of(
      "message",
      "Inscription reçue. Votre compte doit être activé par le super administrateur."
    );
  }

  @GetMapping("/csrf")
  public CsrfResponse csrf(CsrfToken csrf) {
    return new CsrfResponse(csrf.getToken());
  }

  @GetMapping("/me")
  public UserResponse me(Authentication authentication) {
    var user = users
      .findByEmail(authentication.getName())
      .filter(com.rapportangi.backend.entity.User::isEnabled)
      .orElseThrow(() ->
        new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.UNAUTHORIZED
        )
      );
    return new UserResponse(
      user.getId(),
      user.getFirstName() + " " + user.getLastName(),
      user.getEmail(),
      authentication
        .getAuthorities()
        .stream()
        .map(a -> a.getAuthority())
        .toList(),
      user.getTeam(),
      user.getReferralCode()
    );
  }

  public record CsrfResponse(String token) {}

  public record UserResponse(
    Long id,
    String name,
    String email,
    List<String> roles,
    String team,
    String referralCode
  ) {}
}
