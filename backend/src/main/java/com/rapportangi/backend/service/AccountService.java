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
  private final SalesAccess access;
  private final SalesCountryRepository countries;
  private final CommunicationService comms;

  public AccountService(
    UserRepository users,
    RoleRepository roles,
    PasswordEncoder encoder,
    SalesAccess access,
    SalesCountryRepository countries,
    CommunicationService comms
  ) {
    this.users = users;
    this.roles = roles;
    this.encoder = encoder;
    this.access = access;
    this.countries = countries;
    this.comms = comms;
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
    String country = request.country() == null ? "BJ" : request.country();
    if (!countries.existsById(country)) throw SalesAccess.error(
      HttpStatus.BAD_REQUEST,
      "Pays inconnu."
    );
    u.setCountry(country);
    u.setPassword(encoder.encode(request.password()));
    u.setEnabled(approved);
    u.setRoles(Set.of(roles.findByName("COMMERCIAL").orElseThrow()));
    u.setReferralCode(approved ? clean(request.referralCode()) : null);
    return CommercialResponse.from(users.saveAndFlush(u));
  }

  public CommercialResponse createAuthorized(
    CommercialRequest request,
    org.springframework.security.core.Authentication auth
  ) {
    var actor = access.current(auth);
    String country =
      request.country() == null ? actor.getCountry() : request.country();
    if (
      !SalesAccess.director(actor) ||
      !SalesAccess.scope(actor, country, request.team())
    ) throw SalesAccess.error(
      HttpStatus.FORBIDDEN,
      "Création hors de votre pays ou de vos projets."
    );
    var created = create(
      new CommercialRequest(
        request.firstName(),
        request.lastName(),
        request.email(),
        request.password(),
        request.team(),
        request.referralCode(),
        country
      ),
      true
    );
    comms.record(
      actor,
      "ACCOUNT_CREATED",
      "Compte commercial créé : " +
        created.firstName() +
        " " +
        created.lastName(),
      country,
      request.team()
    );
    return created;
  }

  public List<CommercialResponse> list(
    org.springframework.security.core.Authentication auth
  ) {
    var actor = access.current(auth);
    return users
      .findAll()
      .stream()
      .filter(this::commercial)
      .filter(u -> SalesAccess.manages(actor, u))
      .map(CommercialResponse::from)
      .toList();
  }

  public CommercialResponse updateAuthorized(
    Long id,
    String team,
    String code,
    boolean enabled,
    org.springframework.security.core.Authentication auth
  ) {
    var actor = access.current(auth);
    var target = users
      .findLockedById(id)
      .orElseThrow(() ->
        SalesAccess.error(HttpStatus.NOT_FOUND, "Commercial introuvable")
      );
    if (
      !SalesAccess.manages(actor, target) ||
      !SalesAccess.scope(actor, target.getCountry(), team)
    ) throw SalesAccess.error(
      HttpStatus.FORBIDDEN,
      "Compte hors de votre périmètre."
    );
    String oldTeam = target.getTeam();
    var result = update(id, team, code, enabled);
    comms.record(
      actor,
      "ACCOUNT_UPDATED",
      "Compte commercial mis à jour : " +
        result.firstName() +
        " " +
        result.lastName() +
        " (" +
        (enabled ? "actif" : "inactif") +
        ")",
      target.getCountry(),
      team
    );
    if (!Objects.equals(oldTeam, team)) comms.record(
      actor,
      "ACCOUNT_UPDATED",
      "Un compte a quitté ce projet.",
      target.getCountry(),
      oldTeam
    );
    return result;
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
    if (u.isEnabled() != enabled) u.setSecurityVersion(
      u.getSecurityVersion() + 1
    );
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
