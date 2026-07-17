package com.example.fams.aau.keycloak;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SyncedUserRepository extends JpaRepository<SyncedUser, Long> {

    Optional<SyncedUser> findByKeycloakId(String keycloakId);

    /**
     * Users that belong to the given group. Group names are stored
     * comma-delimited WITH leading/trailing commas (e.g. ",employees,admins,"),
     * so the caller passes the delimited token ",groupname," to match
     * exactly and avoid partial-word collisions (e.g. "admin" vs "administrators").
     */
    @Query("""
           select u from SyncedUser u
           where u.groups like %:delimited% and u.enabled = true
           """)
    List<SyncedUser> findByGroupName(@Param("delimited") String delimited);

    List<SyncedUser> findAllByOrderByUsernameAsc();

    long countByEnabledTrue();
}
