package com.rapportangi.backend.service;

import com.rapportangi.backend.entity.*;
import com.rapportangi.backend.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SalesAccess {

  private final UserRepository users;

  public SalesAccess(UserRepository users) {
    this.users = users;
  }

  public User current(Authentication auth) {
    return users
      .findByEmailIgnoreCase(auth.getName())
      .filter(User::isEnabled)
      .orElseThrow(() -> error(HttpStatus.UNAUTHORIZED, "Compte indisponible"));
  }

  public static boolean has(User u, String role) {
    return u
      .getRoles()
      .stream()
      .anyMatch(
        r -> r.getName().equals(role) || r.getName().equals("ROLE_" + role)
      );
  }

  public static boolean director(User u) {
    return has(u, "SUPER_ADMIN") || has(u, "DIRECTEUR_COMMERCIAL");
  }

  public static boolean commercial(User u) {
    return (
      !director(u) &&
      !has(u, "SUPERVISEUR") &&
      !has(u, "ADMIN") &&
      (has(u, "COMMERCIAL") || has(u, "USER"))
    );
  }

  public static boolean scope(User actor, String country, String project) {
    if (has(actor, "SUPER_ADMIN")) return true;
    if (has(actor, "DIRECTEUR_COMMERCIAL")) return (
      actor.getDirectorCountry() != null &&
      java.util.Objects.equals(actor.getDirectorCountry(), country) &&
      (project == null || actor.getProjects().contains(project))
    );
    return (
      java.util.Objects.equals(actor.getCountry(), country) &&
      (project == null || java.util.Objects.equals(actor.getTeam(), project))
    );
  }

  public static boolean manages(User actor, User target) {
    return (
      has(actor, "SUPER_ADMIN") ||
      (has(actor, "DIRECTEUR_COMMERCIAL") &&
        !director(target) &&
        !has(target, "ADMIN") &&
        scope(actor, target.getCountry(), target.getTeam()))
    );
  }

  public static boolean reads(User u, Report r) {
    if (r.getAuthor().getId().equals(u.getId())) return true;
    if ("DRAFT".equals(r.getStatus())) return (
      has(u, "SUPER_ADMIN") || has(u, "ADMIN")
    );
    return (
      (director(u) && scope(u, r.getCountry(), r.getTeam())) ||
      has(u, "ADMIN") ||
      (has(u, "SUPERVISEUR") &&
        r.getSupervisor() != null &&
        r.getSupervisor().getId().equals(u.getId()))
    );
  }

  public static ResponseStatusException error(
    HttpStatus status,
    String message
  ) {
    return new ResponseStatusException(status, message);
  }
}
