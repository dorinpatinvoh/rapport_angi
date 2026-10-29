package com.rapportangi.backend.dto;

import com.rapportangi.backend.entity.User;

public record CommercialResponse(
  Long id,
  String firstName,
  String lastName,
  String email,
  String team,
  String referralCode,
  boolean enabled,
  String country
) {
  public static CommercialResponse from(User u) {
    return new CommercialResponse(
      u.getId(),
      u.getFirstName(),
      u.getLastName(),
      u.getEmail(),
      u.getTeam(),
      u.getReferralCode(),
      u.isEnabled(),
      u.getCountry()
    );
  }
}
