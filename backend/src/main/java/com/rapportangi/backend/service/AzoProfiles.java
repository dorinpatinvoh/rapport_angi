package com.rapportangi.backend.service;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class AzoProfiles {

  private AzoProfiles() {}

  public record Definition(String code, String label, String group) {}

  public static final List<Definition> ALL = List.of(
    new Definition("CLIENT", "Clients", "Clients"),
    new Definition(
      "SPEEDER_MOTO_ESSENCE",
      "Speeder moto à essence",
      "Speeders"
    ),
    new Definition(
      "SPEEDER_MOTO_ELECTRIQUE",
      "Speeder moto électrique",
      "Speeders"
    ),
    new Definition("SPEEDER_TRICYCLE", "Speeder tricycle", "Speeders"),
    new Definition(
      "SPEEDER_GAZELLE",
      "Gazelle - voiture entrée de gamme",
      "Speeders"
    ),
    new Definition("SPEEDER_KOALA", "Koala - voiture climatisée", "Speeders"),
    new Definition("SPEEDER_LEOPARD", "Léopard - berline premium", "Speeders"),
    new Definition(
      "LIVREUR_EXPRESS",
      "Livreur express",
      "Livraison et coursier"
    ),
    new Definition(
      "COURSIER_PERSONNEL",
      "Coursier personnel",
      "Livraison et coursier"
    ),
    new Definition("AGENCE_PRO", "Agence PRO", "Agences de flotte"),
    new Definition("AGENCE_SILVER", "Agence SILVER", "Agences de flotte"),
    new Definition("AGENCE_OR", "Agence OR", "Agences de flotte"),
    new Definition("AGENCE_DIAMANT", "Agence DIAMANT", "Agences de flotte"),
    new Definition("AGENCE_PLATINE", "Agence PLATINE", "Agences de flotte"),
    new Definition("AGENCE_ELITE", "Agence ELITE", "Agences de flotte"),
    new Definition("BOUTIQUE_PARTENAIRE", "Boutique partenaire", "Partenaires")
  );

  public static Map<String, Integer> validate(
    Map<String, Integer> counts,
    String team
  ) {
    if (counts == null || counts.isEmpty()) return Map.of();
    if (
      !"AZO".equals(team) || counts.size() > ALL.size()
    ) throw new ResponseStatusException(
      HttpStatus.BAD_REQUEST,
      "La répartition des profils est réservée à AZO."
    );
    var keys = ALL.stream()
      .map(Definition::code)
      .collect(java.util.stream.Collectors.toSet());
    counts.forEach((key, value) -> {
      if (
        !keys.contains(key) || value == null || value < 0 || value > 1000000
      ) throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "Profil ou nombre de profils AZO invalide."
      );
    });
    return new TreeMap<>(counts);
  }

  public static String label(String code) {
    return ALL.stream()
      .filter(d -> d.code().equals(code))
      .findFirst()
      .map(Definition::label)
      .orElse(code);
  }
}
