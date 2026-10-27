package com.rapportangi.backend.dto;

import jakarta.validation.constraints.*;

public record AzoRegistrationDto(
  @NotBlank @Size(max = 100) String firstName,
  @NotBlank @Size(max = 100) String lastName,
  @NotBlank @Size(max = 50) String profileCode
) {}
