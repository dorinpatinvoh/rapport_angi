package com.rapportangi.backend.entity;

import jakarta.persistence.*;

@Embeddable
public class AzoRegistration {

  @Column(name = "first_name", nullable = false, length = 100)
  private String firstName;

  @Column(name = "last_name", nullable = false, length = 100)
  private String lastName;

  @Column(name = "profile_code", nullable = false, length = 50)
  private String profileCode;

  protected AzoRegistration() {}

  public AzoRegistration(
    String firstName,
    String lastName,
    String profileCode
  ) {
    this.firstName = firstName;
    this.lastName = lastName;
    this.profileCode = profileCode;
  }

  public String getFirstName() {
    return firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public String getProfileCode() {
    return profileCode;
  }
}
