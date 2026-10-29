package com.rapportangi.backend.service;

import static com.rapportangi.backend.service.SalesAccess.*;

import com.rapportangi.backend.entity.*;
import com.rapportangi.backend.repository.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.text.Normalizer;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ZoneGoalsService {

  private final GoalZoneRepository zones;
  private final UserRepository users;
  private final SalesCountryRepository countries;
  private final SalesAccess access;
  private final CommunicationService comms;

  public ZoneGoalsService(
    GoalZoneRepository z,
    UserRepository u,
    SalesCountryRepository c,
    SalesAccess a,
    CommunicationService n
  ) {
    zones = z;
    users = u;
    countries = c;
    access = a;
    comms = n;
  }

  public record Metric(
    @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,49}") String code,
    @NotBlank @Size(max = 120) String label,
    @Min(0) @Max(1000000) int target
  ) {}

  public record Input(
    @NotBlank @Pattern(regexp = "[A-Z]{2}") String country,
    @NotNull @Pattern(regexp = "AZO|ZEHAMI") String project,
    @NotNull @Pattern(regexp = "DEPARTMENT|COMMUNE") String level,
    @NotBlank @Size(max = 150) String name,
    @Positive Long parentId,
    @Min(0) @Max(10000) int plannedStaff,
    boolean inheritGoals,
    @NotNull @Size(max = 30) List<@Valid @NotNull Metric> goals,
    @PositiveOrZero Long version
  ) {}

  public record Policy(
    Long zoneId,
    String zoneName,
    String source,
    List<SalesGoals.Definition> goals
  ) {}

  public record View(
    Long id,
    String country,
    String project,
    String level,
    String name,
    Long parentId,
    int plannedStaff,
    long assignedStaff,
    boolean inheritGoals,
    List<Metric> goals,
    List<SalesGoals.Definition> effectiveGoals,
    long version
  ) {}

  public static String key(String name) {
    return Normalizer.normalize(name.strip(), Normalizer.Form.NFD)
      .replaceAll("\\p{M}", "")
      .toLowerCase(Locale.ROOT)
      .replaceAll("[^a-z0-9]+", " ")
      .strip();
  }

  private List<SalesGoals.Definition> definitions(GoalZone z) {
    if (z.inheritGoals) {
      var parent = zones.findById(z.parentId).orElseThrow();
      return definitions(parent);
    }
    return z.goals
      .stream()
      .map(g -> new SalesGoals.Definition(g.code, g.label, g.target))
      .toList();
  }

  public GoalZone resolve(User user) {
    var u = users.findById(user.getId()).orElseThrow();
    var owner = commercial(u) ? u.getSupervisor() : u;
    if (owner == null) return null;
    if (owner.getZoneConfigId() != null) {
      var z = zones.findById(owner.getZoneConfigId()).orElse(null);
      if (
        z != null &&
        Objects.equals(z.country, u.getCountry()) &&
        Objects.equals(z.project, u.getTeam())
      ) return z;
    }
    if (owner.getZone() == null) return null;
    var matches = zones.findByCountryAndProjectAndZoneKey(
      u.getCountry(),
      u.getTeam(),
      key(owner.getZone())
    );
    if (owner.getZoneLevel() != null) matches = matches
      .stream()
      .filter(z -> z.level.equals(owner.getZoneLevel()))
      .toList();
    return matches.size() == 1 ? matches.get(0) : null;
  }

  public Policy policy(User u) {
    var z = resolve(u);
    if (z == null) return new Policy(
      null,
      null,
      "Grille standard (zone non configurée)",
      SalesGoals.definitions(u.getTeam())
    );
    var source = z.inheritGoals
      ? zones.findById(z.parentId).orElseThrow().name
      : z.name;
    return new Policy(
      z.id,
      z.name,
      z.inheritGoals ? "Grille héritée de " + source : "Grille de " + source,
      definitions(z)
    );
  }

  public List<SalesGoals.Definition> goals(String team, Authentication auth) {
    var u = access.current(auth);
    return Objects.equals(u.getTeam(), team)
      ? policy(u).goals()
      : SalesGoals.definitions(team);
  }

  public GoalZone binding(Long id, String country, String project) {
    var z = zones
      .findById(id)
      .orElseThrow(() -> error(HttpStatus.BAD_REQUEST, "Zone introuvable."));
    if (
      !Objects.equals(z.country, country) || !Objects.equals(z.project, project)
    ) throw error(
      HttpStatus.BAD_REQUEST,
      "La zone doit appartenir au même pays et au même projet."
    );
    return z;
  }

  private View view(GoalZone z) {
    long assigned = users
      .findAll()
      .stream()
      .filter(
        u ->
          u.isEnabled() &&
          commercial(u) &&
          Objects.equals(u.getCountry(), z.country) &&
          Objects.equals(u.getTeam(), z.project)
      )
      .filter(u -> {
        var own = resolve(u);
        return (
          own != null &&
          (own.id.equals(z.id) ||
            ("DEPARTMENT".equals(z.level) && z.id.equals(own.parentId)))
        );
      })
      .count();
    return new View(
      z.id,
      z.country,
      z.project,
      z.level,
      z.name,
      z.parentId,
      z.plannedStaff,
      assigned,
      z.inheritGoals,
      z.goals
        .stream()
        .map(g -> new Metric(g.code, g.label, g.target))
        .toList(),
      definitions(z),
      z.version
    );
  }

  public List<View> list(Authentication auth) {
    var u = access.current(auth);
    if (!director(u)) throw error(
      HttpStatus.FORBIDDEN,
      "Gestion des grilles réservée à la direction."
    );
    return zones
      .findAll()
      .stream()
      .filter(z -> scope(u, z.country, z.project))
      .sorted(Comparator.comparing(z -> z.country + z.project + z.name))
      .map(this::view)
      .toList();
  }

  public View save(Long id, Input input, Authentication auth) {
    var actor = access.current(auth);
    if (
      !director(actor) || !scope(actor, input.country(), input.project())
    ) throw error(HttpStatus.FORBIDDEN, "Grille hors de votre périmètre.");
    if (!countries.existsById(input.country())) throw error(
      HttpStatus.BAD_REQUEST,
      "Pays inconnu."
    );
    GoalZone z;
    if (id == null) {
      z = new GoalZone();
      z.country = input.country();
      z.project = input.project();
      z.level = input.level();
      z.name = input.name().strip();
      z.zoneKey = key(input.name());
      if (
        zones
          .findByCountryAndProjectAndLevelAndZoneKey(
            z.country,
            z.project,
            z.level,
            z.zoneKey
          )
          .isPresent()
      ) throw error(
        HttpStatus.CONFLICT,
        "Une grille existe déjà pour cette zone. Modifiez la grille existante."
      );
      if (z.zoneKey.isBlank()) throw error(
        HttpStatus.BAD_REQUEST,
        "Nom de zone invalide."
      );
    } else {
      z = zones
        .findById(id)
        .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Zone introuvable."));
      if (!scope(actor, z.country, z.project)) throw error(
        HttpStatus.FORBIDDEN,
        "Zone hors de votre périmètre."
      );
      if (input.version() == null || input.version() != z.version) throw error(
        HttpStatus.CONFLICT,
        "Grille modifiée entre-temps. Rechargez-la."
      );
      if (
        !z.country.equals(input.country()) ||
        !z.project.equals(input.project()) ||
        !z.level.equals(input.level()) ||
        !z.name.equals(input.name().strip())
      ) throw error(
        HttpStatus.BAD_REQUEST,
        "Le pays, le projet, le niveau et le nom sont fixes. Créez une autre zone puis réaffectez les équipes."
      );
    }
    if (
      "DEPARTMENT".equals(z.level) &&
      (input.parentId() != null || input.inheritGoals())
    ) throw error(
      HttpStatus.BAD_REQUEST,
      "Un département définit sa propre grille."
    );
    if (input.parentId() != null) {
      var parent = binding(input.parentId(), z.country, z.project);
      if (
        !"DEPARTMENT".equals(parent.level) || Objects.equals(parent.id, z.id)
      ) throw error(
        HttpStatus.BAD_REQUEST,
        "Choisissez un département du même périmètre."
      );
    }
    if (input.inheritGoals() && input.parentId() == null) throw error(
      HttpStatus.BAD_REQUEST,
      "Choisissez le département dont hériter."
    );
    if (!input.inheritGoals() && input.goals().isEmpty()) throw error(
      HttpStatus.BAD_REQUEST,
      "Ajoutez au moins un critère."
    );
    if (
      input.goals().stream().map(Metric::code).distinct().count() !=
      input.goals().size()
    ) throw error(
      HttpStatus.BAD_REQUEST,
      "Chaque critère doit avoir un identifiant distinct."
    );
    z.parentId = input.parentId();
    z.inheritGoals = input.inheritGoals();
    z.plannedStaff = input.plannedStaff();
    z.goals.clear();
    if (!z.inheritGoals) input
      .goals()
      .forEach(g ->
        z.goals.add(new ZoneGoal(g.code(), g.label().strip(), g.target()))
      );
    zones.saveAndFlush(z);
    // Bind matching legacy supervisors without changing their account identity or their report history.
    for (var u : users.findAll())
      if (
        has(u, "SUPERVISEUR") &&
        u.getZoneConfigId() == null &&
        Objects.equals(u.getCountry(), z.country) &&
        Objects.equals(u.getTeam(), z.project) &&
        u.getZone() != null &&
        key(u.getZone()).equals(z.zoneKey) &&
        (u.getZoneLevel() == null || u.getZoneLevel().equals(z.level)) &&
        zones
          .findByCountryAndProjectAndZoneKey(z.country, z.project, z.zoneKey)
          .size() == 1
      ) {
        u.setZoneConfigId(z.id);
        u.setZoneLevel(z.level);
      }
    comms.record(
      actor,
      "GOAL_GRID_UPDATED",
      "Grille d’objectifs enregistrée : " + z.name,
      z.country,
      z.project
    );
    return view(z);
  }

  public Map<String, Integer> validate(
    Map<String, Integer> counts,
    User u,
    Report report
  ) {
    if (counts == null) return Map.of();
    var allowed = new HashSet<String>();
    SalesGoals.definitions(
      report == null ? u.getTeam() : report.getTeam()
    ).forEach(g -> allowed.add(g.code()));
    policy(u)
      .goals()
      .forEach(g -> allowed.add(g.code()));
    if (report != null) {
      allowed.addAll(report.getGoalCounts().keySet());
      allowed.addAll(report.getGoalLabels().keySet());
    }
    counts.forEach((k, v) -> {
      if (
        !allowed.contains(k) || v == null || v < 0 || v > 1000000
      ) throw error(
        HttpStatus.BAD_REQUEST,
        "Critère ou réalisation invalide pour cette grille."
      );
    });
    return new TreeMap<>(counts);
  }

  public void snapshotLabels(Report r, User u) {
    policy(u)
      .goals()
      .forEach(g -> r.getGoalLabels().putIfAbsent(g.code(), g.label()));
    SalesGoals.definitions(r.getTeam()).forEach(g ->
      r.getGoalLabels().putIfAbsent(g.code(), g.label())
    );
  }
}
