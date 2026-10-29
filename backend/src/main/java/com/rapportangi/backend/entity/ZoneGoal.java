package com.rapportangi.backend.entity;

import jakarta.persistence.*;

@Embeddable
public class ZoneGoal {

  @Column(nullable = false, length = 50)
  public String code;

  @Column(nullable = false, length = 120)
  public String label;

  @Column(nullable = false)
  public int target;

  public ZoneGoal() {}

  public ZoneGoal(String code, String label, int target) {
    this.code = code;
    this.label = label;
    this.target = target;
  }
}
