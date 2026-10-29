package com.rapportangi.backend.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "sales_countries")
public class SalesCountry {

  @Id
  @Column(length = 2)
  public String code;

  @Column(nullable = false, length = 100)
  public String name;

  public SalesCountry() {}

  public SalesCountry(String code, String name) {
    this.code = code;
    this.name = name;
  }
}
