package db.migration;

import java.sql.Connection;
import java.util.ArrayList;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** One-time change requested for the company account; never grants a role. */
public class V7__Company_admin_email extends BaseJavaMigration {

  private static final String EMAIL = "angidsnpro@gmail.com";

  @Override
  public void migrate(Context context) throws Exception {
    update(context.getConnection());
  }

  public static void update(Connection connection) throws Exception {
    var ids = new ArrayList<Long>();
    try (
      var query = connection.prepareStatement(
        "SELECT u.id FROM users u WHERE EXISTS (SELECT 1 FROM user_roles ur JOIN roles r ON r.id = ur.role_id WHERE ur.user_id = u.id AND r.name IN ('SUPER_ADMIN', 'ROLE_SUPER_ADMIN'))"
      )
    ) {
      try (var rows = query.executeQuery()) {
        while (rows.next()) ids.add(rows.getLong(1));
      }
    }
    if (ids.isEmpty()) return; // Fresh installations use the normal administrator bootstrap.
    if (ids.size() != 1) {
      System.err.println(
        "Email entreprise inchangé : plusieurs SUPER_ADMIN, choix du compte nécessaire."
      );
      return;
    }
    long id = ids.get(0);
    try (
      var query = connection.prepareStatement(
        "SELECT id FROM users WHERE LOWER(email) = ?"
      )
    ) {
      query.setString(1, EMAIL);
      try (var rows = query.executeQuery()) {
        if (rows.next()) {
          if (rows.getLong(1) != id) System.err.println(
            "Email entreprise inchangé : adresse déjà utilisée par un autre compte."
          );
          return;
        }
      }
    }
    try (
      var delete = connection.prepareStatement(
        "DELETE FROM password_reset_tokens WHERE user_id = ?"
      )
    ) {
      delete.setLong(1, id);
      delete.executeUpdate();
    }
    try (
      var update = connection.prepareStatement(
        "UPDATE users SET email = ?, security_version = security_version + 1 WHERE id = ?"
      )
    ) {
      update.setString(1, EMAIL);
      update.setLong(2, id);
      update.executeUpdate();
    }
    System.out.println(
      "Email du SUPER_ADMIN remplacé par l'adresse de l'entreprise ; reconnectez-vous."
    );
  }
}
