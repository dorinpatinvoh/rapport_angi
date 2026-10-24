package com.rapportangi.backend.repository;

import com.rapportangi.backend.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {
    // Permet de retrouver un rôle par son nom (ex: "ROLE_ADMIN")
    Optional<Role> findByName(String name);
}