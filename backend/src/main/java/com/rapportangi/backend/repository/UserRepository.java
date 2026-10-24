package com.rapportangi.backend.repository;

import com.rapportangi.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    // Très utile pour la connexion/authentification future
    Optional<User> findByEmail(String email);

    // Permet de vérifier facilement si un email existe déjà à l'inscription
    boolean existsByEmail(String email);
}