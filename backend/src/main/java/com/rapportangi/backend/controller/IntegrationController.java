package com.rapportangi.backend.controller;

import com.rapportangi.backend.repository.UserRepository;
import com.rapportangi.backend.service.MobileStatsService;
import java.time.*;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/integrations")
public class IntegrationController {

  private final MobileStatsService mobile;
  private final UserRepository users;

  public IntegrationController(
    MobileStatsService mobile,
    UserRepository users
  ) {
    this.mobile = mobile;
    this.users = users;
  }

  private com.rapportangi.backend.entity.User user(Authentication auth) {
    return users
      .findByEmail(auth.getName())
      .filter(com.rapportangi.backend.entity.User::isEnabled)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
  }

  @GetMapping("/status")
  public Map<String, Boolean> status(Authentication auth) {
    return Map.of("configured", mobile.configured(user(auth).getTeam()));
  }

  @GetMapping("/my-stats")
  public MobileStatsService.Stats stats(
    Authentication auth,
    @RequestParam LocalDate date
  ) {
    if (
      date.isAfter(LocalDate.now(ZoneId.of("Africa/Lagos")))
    ) throw new ResponseStatusException(
      HttpStatus.BAD_REQUEST,
      "Date future interdite"
    );
    return mobile.fetch(user(auth), date);
  }
}
