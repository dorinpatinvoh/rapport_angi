package com.rapportangi.backend.service;

import java.util.*;
import org.springframework.http.HttpStatus;

public final class SalesGoals {

  private SalesGoals() {}

  public record Definition(String code, String label, int target) {}

  public static List<Definition> definitions(String team) {
    return "AZO".equals(team)
      ? List.of(
          new Definition("AGENCES_FLOTTE", "Agences de flotte", 10),
          new Definition("SPEEDERS_INDEPENDANTS", "Speeders indépendants", 45),
          new Definition("CLIENTS", "Clients", 200)
        )
      : "ZEHAMI".equals(team)
        ? List.of(
            new Definition("PROFESSIONNELS", "Professionnels", 25),
            new Definition("UTILISATEURS", "Utilisateurs", 200)
          )
        : List.of();
  }

  public static Map<String, Integer> validate(
    Map<String, Integer> counts,
    String team
  ) {
    if (counts == null) return Map.of();
    var keys = definitions(team).stream().map(Definition::code).toList();
    counts.forEach((k, v) -> {
      if (
        !keys.contains(k) || v == null || v < 0 || v > 1000000
      ) throw SalesAccess.error(
        HttpStatus.BAD_REQUEST,
        "Objectif ou nombre de réalisations invalide pour cette équipe."
      );
    });
    return new TreeMap<>(counts);
  }

  public static String label(String code) {
    return java.util.stream.Stream.concat(
      definitions("AZO").stream(),
      definitions("ZEHAMI").stream()
    )
      .filter(d -> d.code().equals(code))
      .map(Definition::label)
      .findFirst()
      .orElse(code);
  }
}
