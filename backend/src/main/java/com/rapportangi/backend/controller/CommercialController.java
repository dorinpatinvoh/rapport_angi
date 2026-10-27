package com.rapportangi.backend.controller;

import com.rapportangi.backend.dto.*;
import com.rapportangi.backend.service.AccountService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/commercials")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','DIRECTEUR_COMMERCIAL')")
public class CommercialController {

  private final AccountService accounts;

  public CommercialController(AccountService accounts) {
    this.accounts = accounts;
  }

  @GetMapping
  public List<CommercialResponse> list() {
    return accounts.list();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public CommercialResponse create(
    @Valid @RequestBody CommercialRequest request
  ) {
    return accounts.create(request, true);
  }

  @PutMapping("/{id}")
  public CommercialResponse update(
    @PathVariable Long id,
    @Valid @RequestBody Update request
  ) {
    return accounts.update(
      id,
      request.team(),
      request.referralCode(),
      request.enabled()
    );
  }

  public record Update(
    @NotNull @Pattern(regexp = "AZO|ZEHAMI") String team,
    @Size(max = 100) String referralCode,
    boolean enabled
  ) {}
}
