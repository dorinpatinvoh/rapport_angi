package com.rapportangi.backend.service;

import com.rapportangi.backend.dto.*;
import com.rapportangi.backend.entity.User;
import com.rapportangi.backend.repository.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class AccountService {

  private final UserRepository users;
  private final RoleRepository roles;
  private final PasswordEncoder encoder;

  public AccountService(
    UserRepository users,
    RoleRepository roles,
    PasswordEncoder encoder
  ) {
    this.users = users;
    this.roles = roles;
    this.encoder = encoder;
  }

  public CommercialResponse create(
    CommercialRequest request,
    boolean approved
  ) {
    String email = request.email().strip().toLowerCase(Locale.ROOT);
    if (
      users.findByEmailIgnoreCase(email).isPresent()
    ) throw new ResponseStatusException(
      HttpStatus.CONFLICT,
      "Cet email est déjà utilisé."
    );
    if (
      request.password().getBytes(StandardCharsets.UTF_8).length > 72
    ) throw new ResponseStatusException(
      HttpStatus.BAD_REQUEST,
      "Mot de passe limité à 72 octets UTF-8."
    );
    var u = new User();
    u.setFirstName(request.firstName().strip());
    u.setLastName(request.lastName().strip());
    u.setEmail(email);
    u.setTeam(request.team());
    u.setPassword(encoder.encode(request.password()));
    u.setEnabled(approved);
    u.setRoles(Set.of(roles.findByName("COMMERCIAL").orElseThrow()));
    u.setReferralCode(approved ? clean(request.referralCode()) : null);
    return CommercialResponse.from(users.saveAndFlush(u));
  }

  public List<CommercialResponse> list() {
    return users
      .findAll()
      .stream()
      .filter(this::commercial)
      .map(CommercialResponse::from)
      .toList();
  }

  public CommercialResponse update(
    Long id,
    String team,
    String referralCode,
    boolean enabled
  ) {
    var u = users
      .findLockedById(id)
      .filter(this::commercial)
      .orElseThrow(() ->
        new ResponseStatusException(
          HttpStatus.NOT_FOUND,
          "Commercial introuvable"
        )
      );
    if (
      u.getSupervisor() != null &&
      !java.util.Objects.equals(team, u.getSupervisor().getTeam())
    ) throw new ResponseStatusException(
      HttpStatus.CONFLICT,
      "Réaffectez ce commercial à un superviseur de la nouvelle équipe depuis Organisation."
    );
    u.setTeam(team);
    u.setReferralCode(clean(referralCode));
    u.setEnabled(enabled);
    return CommercialResponse.from(users.saveAndFlush(u));
  }

  private boolean commercial(User u) {
    if (!SalesAccess.commercial(u)) return false;
    return (
      u
        .getRoles()
        .stream()
        .anyMatch(
          r ->
            r.getName().equals("COMMERCIAL") ||
            r.getName().equals("ROLE_USER") ||
            r.getName().equals("ROLE_COMMERCIAL")
        ) &&
      u
        .getRoles()
        .stream()
        .noneMatch(r -> r.getName().contains("ADMIN"))
    );
  }

  private String clean(String code) {
    return code == null || code.isBlank() ? null : code.strip();
  }
}
