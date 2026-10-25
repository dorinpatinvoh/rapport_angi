package com.rapportangi.backend.repository;

import com.rapportangi.backend.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
  // Très utile pour la connexion/authentification future
  Optional<User> findByEmail(String email);
  Optional<User> findByEmailIgnoreCase(String email);

  // Permet de vérifier facilement si un email existe déjà à l'inscription
  boolean existsByEmail(String email);
}
