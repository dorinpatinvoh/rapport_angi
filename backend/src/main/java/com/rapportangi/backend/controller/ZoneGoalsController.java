package com.rapportangi.backend.controller;

import com.rapportangi.backend.service.ZoneGoalsService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sales/zones")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','DIRECTEUR_COMMERCIAL')")
public class ZoneGoalsController {

  private final ZoneGoalsService zones;

  public ZoneGoalsController(ZoneGoalsService zones) {
    this.zones = zones;
  }

  @GetMapping
  public java.util.List<ZoneGoalsService.View> list(Authentication a) {
    return zones.list(a);
  }

  @PostMapping
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public ZoneGoalsService.View create(
    @Valid @RequestBody ZoneGoalsService.Input r,
    Authentication a
  ) {
    return zones.save(null, r, a);
  }

  @PutMapping("/{id}")
  public ZoneGoalsService.View update(
    @PathVariable Long id,
    @Valid @RequestBody ZoneGoalsService.Input r,
    Authentication a
  ) {
    return zones.save(id, r, a);
  }
}
