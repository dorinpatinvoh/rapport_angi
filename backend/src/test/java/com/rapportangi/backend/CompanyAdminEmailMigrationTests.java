package com.rapportangi.backend;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class CompanyAdminEmailMigrationTests {

  Connection database() throws Exception {
    String url =
      "jdbc:h2:mem:email-" +
      UUID.randomUUID() +
      ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
    Flyway.configure().dataSource(url, "sa", "").target("6").load().migrate();
    return DriverManager.getConnection(url, "sa", "");
  }

  void account(Connection c, long id, String email, String role)
    throws Exception {
    try (
      var q = c.prepareStatement(
        "INSERT INTO users(id,first_name,last_name,email,password,enabled) VALUES (?, 'Compte', 'Test', ?, 'existing-hash', TRUE)"
      )
    ) {
      q.setLong(1, id);
      q.setString(2, email);
      q.executeUpdate();
    }
    try (
      var q = c.prepareStatement(
        "INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE name=?"
      )
    ) {
      q.setLong(1, id);
      q.setString(2, role);
      q.executeUpdate();
    }
  }

  @Test
  void migrationRenamesSingleRootPreservingIdentityAndPassword()
    throws Exception {
    try (var c = database()) {
      account(c, 1, "old-root@example.test", "SUPER_ADMIN");
      try (
        var q = c.prepareStatement(
          "INSERT INTO password_reset_tokens(user_id,token_hash,expires_at,created_at) VALUES (1,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)"
        )
      ) {
        q.setString(1, "a".repeat(64));
        q.executeUpdate();
      }
      Flyway.configure()
        .dataSource(c.getMetaData().getURL(), "sa", "")
        .load()
        .migrate();
      try (
        var rs = c
          .createStatement()
          .executeQuery(
            "SELECT email,password,security_version FROM users WHERE id=1"
          )
      ) {
        assertTrue(rs.next());
        assertEquals("angidsnpro@gmail.com", rs.getString(1));
        assertEquals("existing-hash", rs.getString(2));
        assertEquals(1, rs.getLong(3));
      }
      try (
        var rs = c
          .createStatement()
          .executeQuery(
            "SELECT COUNT(*) FROM password_reset_tokens WHERE user_id=1"
          )
      ) {
        rs.next();
        assertEquals(0, rs.getInt(1));
      }
      // Running the operation again does not invalidate sessions again.
      db.migration.V7__Company_admin_email.update(c);
      try (
        var rs = c
          .createStatement()
          .executeQuery("SELECT security_version FROM users WHERE id=1")
      ) {
        rs.next();
        assertEquals(1, rs.getLong(1));
      }
    }
  }

  @Test
  void migrationDoesNotTakeOverAnotherAccountOrChooseAmongRoots()
    throws Exception {
    try (var c = database()) {
      account(c, 1, "original@example.test", "SUPER_ADMIN");
      account(c, 2, "angidsnpro@gmail.com", "COMMERCIAL");
      db.migration.V7__Company_admin_email.update(c);
      try (
        var rs = c
          .createStatement()
          .executeQuery("SELECT email,security_version FROM users WHERE id=1")
      ) {
        rs.next();
        assertEquals("original@example.test", rs.getString(1));
        assertEquals(0, rs.getLong(2));
      }
    }
    try (var c = database()) {
      account(c, 1, "root-one@example.test", "SUPER_ADMIN");
      account(c, 2, "root-two@example.test", "SUPER_ADMIN");
      db.migration.V7__Company_admin_email.update(c);
      try (
        var rs = c
          .createStatement()
          .executeQuery(
            "SELECT COUNT(*) FROM users WHERE email='angidsnpro@gmail.com'"
          )
      ) {
        rs.next();
        assertEquals(0, rs.getInt(1));
      }
    }
  }
}
