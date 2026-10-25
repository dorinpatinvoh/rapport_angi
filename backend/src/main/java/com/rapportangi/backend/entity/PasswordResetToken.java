package com.rapportangi.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "token_hash", nullable = false, unique = true, length = 64)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  public PasswordResetToken() {}

  public PasswordResetToken(User user, String hash, Instant expiry) {
    this.user = user;
    tokenHash = hash;
    expiresAt = expiry;
    createdAt = Instant.now();
  }

  public User getUser() {
    return user;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }
}
