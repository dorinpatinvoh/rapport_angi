package com.rapportangi.backend.dto;

import jakarta.validation.constraints.*;

public record CommercialRequest(
  @NotBlank @Size(max = 100) String firstName,
  @NotBlank @Size(max = 100) String lastName,
  @NotBlank @Email @Size(max = 150) String email,
  @NotNull @Size(min = 12, max = 72) String password,
  @NotNull @Pattern(regexp = "AZO|ZEHAMI") String team,
  @Size(max = 100) String referralCode
) {}
