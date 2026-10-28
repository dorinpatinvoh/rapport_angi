package com.rapportangi.backend.controller;

import com.rapportangi.backend.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sales")
public class SalesController {

  private final SalesManagementService sales;

  public SalesController(SalesManagementService sales) {
    this.sales = sales;
  }

  @GetMapping("/members")
  public List<SalesManagementService.Member> members(Authentication a) {
    return sales.members(a);
  }

  public record CreateMember(
    @Valid @NotNull com.rapportangi.backend.dto.CommercialRequest account,
    @NotNull @Pattern(regexp = "DIRECTEUR_COMMERCIAL|SUPERVISEUR") String role,
    @Size(max = 150) String zone
  ) {}

  @PostMapping("/members")
  @org.springframework.security.access.prepost.PreAuthorize(
    "hasAnyRole('SUPER_ADMIN','DIRECTEUR_COMMERCIAL')"
  )
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public SalesManagementService.Member create(
    @Valid @RequestBody CreateMember r,
    Authentication a
  ) {
    return sales.create(r.account(), r.role(), r.zone(), a);
  }

  public record Assignment(
    @NotNull
    @Pattern(regexp = "COMMERCIAL|SUPERVISEUR|DIRECTEUR_COMMERCIAL")
    String role,
    @NotNull @Pattern(regexp = "AZO|ZEHAMI") String team,
    Long supervisorId,
    @Size(max = 150) String zone
  ) {}

  @PutMapping("/members/{id}")
  public SalesManagementService.Member assign(
    @PathVariable Long id,
    @Valid @RequestBody Assignment r,
    Authentication a
  ) {
    return sales.assign(id, r.role(), r.team(), r.supervisorId(), r.zone(), a);
  }

  @GetMapping("/goals")
  public List<SalesGoals.Definition> goals(@RequestParam String team) {
    return SalesGoals.definitions(team);
  }

  @GetMapping("/objectives")
  public List<SalesManagementService.Monthly> objectives(
    @RequestParam String month,
    Authentication a
  ) {
    try {
      return sales.objectives(YearMonth.parse(month), a);
    } catch (java.time.format.DateTimeParseException e) {
      throw SalesAccess.error(
        org.springframework.http.HttpStatus.BAD_REQUEST,
        "Mois invalide (AAAA-MM)."
      );
    }
  }

  @GetMapping("/summaries")
  public List<SalesManagementService.Summary> summaries(
    @RequestParam LocalDate date,
    Authentication a
  ) {
    return sales.summaries(date, a);
  }

  public record SendSummary(
    @NotNull LocalDate date,
    @NotBlank @Size(max = 30000) String content
  ) {}

  @PostMapping("/summaries")
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public SalesManagementService.Summary send(
    @Valid @RequestBody SendSummary r,
    Authentication a
  ) {
    return sales.sendSummary(r.date(), r.content(), a);
  }
}
