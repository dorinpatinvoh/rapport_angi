package com.rapportangi.backend;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class CountryUpgradeTests {

  void upgrade(int directors) throws Exception {
    String url =
      "jdbc:h2:mem:country-upgrade-" +
      UUID.randomUUID() +
      ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
    Flyway.configure().dataSource(url, "sa", "").target("7").load().migrate();
    try (
      var c = DriverManager.getConnection(url, "sa", "");
      var q = c.createStatement()
    ) {
      for (int i = 1; i <= directors; i++) {
        q.executeUpdate(
          "INSERT INTO users(id,first_name,last_name,email,password) VALUES (" +
            i +
            ",'Legacy','Director','legacy" +
            i +
            "@test.invalid','existing-hash')"
        );
        q.executeUpdate(
          "INSERT INTO user_roles(user_id,role_id) SELECT " +
            i +
            ",id FROM roles WHERE name='DIRECTEUR_COMMERCIAL'"
        );
      }
    }
    Flyway.configure().dataSource(url, "sa", "").load().migrate();
    try (
      var c = DriverManager.getConnection(url, "sa", "");
      var q = c.createStatement()
    ) {
      try (var rows = q.executeQuery("SELECT COUNT(*) FROM users")) {
        rows.next();
        assertEquals(directors, rows.getInt(1));
      }
      try (
        var rows = q.executeQuery(
          "SELECT COUNT(*) FROM users WHERE director_country='BJ'"
        )
      ) {
        rows.next();
        assertEquals(directors == 1 ? 1 : 0, rows.getInt(1));
      }
      try (
        var rows = q.executeQuery("SELECT COUNT(*) FROM director_projects")
      ) {
        rows.next();
        assertEquals(directors * 2, rows.getInt(1));
      }
      try (
        var rows = q.executeQuery(
          "SELECT COUNT(*) FROM users WHERE password='existing-hash'"
        )
      ) {
        rows.next();
        assertEquals(directors, rows.getInt(1));
      }
    }
  }

  @Test
  void singleLegacyDirectorGetsBeninAndBothProjects() throws Exception {
    upgrade(1);
  }

  @Test
  void multipleLegacyDirectorsArePreservedForExplicitAssignment()
    throws Exception {
    upgrade(2);
  }
}
