package com.rapportangi.backend.config;

import com.rapportangi.backend.entity.Role;
import com.rapportangi.backend.entity.User;
import com.rapportangi.backend.repository.RoleRepository;
import com.rapportangi.backend.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;

@Configuration
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;

    public DataInitializer(RoleRepository roleRepository, UserRepository userRepository) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        // 1. Initialiser les Rôles s'ils n'existent pas
        Role userRole = roleRepository.findByName("ROLE_USER").orElseGet(() -> {
            Role role = new Role();
            role.setName("ROLE_USER");
            return roleRepository.save(role);
        });

        Role adminRole = roleRepository.findByName("ROLE_ADMIN").orElseGet(() -> {
            Role role = new Role();
            role.setName("ROLE_ADMIN");
            return roleRepository.save(role);
        });

        // 2. Créer un Utilisateur de test (si aucun n'existe avec cet email)
        String adminEmail = "admin@angi.com";
        if (!userRepository.existsByEmail(adminEmail)) {
            User admin = new User();
            admin.setFirstName("Super");
            admin.setLastName("Admin");
            admin.setEmail(adminEmail);
            admin.setPassword("password123"); // Plus tard on chiffrera avec BCrypt
            admin.setEnabled(true);
            admin.setCreatedAt(LocalDateTime.now());
            admin.setRoles(new HashSet<>(Collections.singletonList(adminRole)));

            userRepository.save(admin);
            System.out.println(">>> Utilisateur de test créé avec succès ! ID: " + admin.getId() + " (Email: " + adminEmail + ")");
        }
    }
}