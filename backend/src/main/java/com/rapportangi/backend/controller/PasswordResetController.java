package com.rapportangi.backend.controller;

import com.rapportangi.backend.service.PasswordResetService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
public class PasswordResetController {

  private final PasswordResetService reset;

  public PasswordResetController(PasswordResetService reset) {
    this.reset = reset;
  }

  @PostMapping("/api/auth/forgot-password")
  public Map<String, String> forgot(@Valid @RequestBody EmailRequest request) {
    reset.request(request.email());
    return Map.of(
      "message",
      "Si un compte actif correspond à cet email, vous recevrez un lien de réinitialisation. Vérifiez aussi les courriers indésirables."
    );
  }

  @PostMapping("/api/auth/reset-password")
  public Map<String, String> reset(@Valid @RequestBody ResetRequest request) {
    reset.reset(request.token(), request.password());
    return Map.of(
      "message",
      "Mot de passe réinitialisé. Reconnectez-vous avec le nouveau mot de passe."
    );
  }

  @PostMapping("/api/commercials/{id}/password")
  @PreAuthorize("hasRole('SUPER_ADMIN')")
  public Map<String, String> commercial(
    @PathVariable Long id,
    @Valid @RequestBody PasswordRequest request
  ) {
    reset.resetCommercial(id, request.password());
    return Map.of(
      "message",
      "Mot de passe du commercial réinitialisé. Ses sessions précédentes sont invalidées."
    );
  }

  public record EmailRequest(@NotBlank @Email @Size(max = 150) String email) {}

  public record ResetRequest(
    @NotBlank @Size(max = 100) String token,
    @NotNull @Size(min = 12, max = 72) String password
  ) {}

  public record PasswordRequest(
    @NotNull @Size(min = 12, max = 72) String password
  ) {}
}
