package com.example.fams.aau.keycloak;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Local, periodically-refreshed snapshot of Keycloak users.
 *
 * Pages read from this table instead of calling Keycloak on every request.
 * The {@link UserSyncService} keeps it up to date in the background
 * (on startup + on a schedule).
 *
 * Getter names intentionally mirror the fields the templates already read
 * on Keycloak's {@code UserRepresentation} (id, username, firstName,
 * lastName, email, enabled) so the three consumer pages need no template
 * changes.
 */
@Entity
@Table(name = "synced_users", uniqueConstraints = {
        @UniqueConstraint(columnNames = "keycloak_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncedUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "keycloak_id", nullable = false, length = 100)
    private String keycloakId;

    @Column(length = 255)
    private String username;

    @Column(length = 255)
    private String email;

    @Column(name = "first_name", length = 255)
    private String firstName;

    @Column(name = "last_name", length = 255)
    private String lastName;

    @Column(nullable = false)
    private boolean enabled;

    /** Comma-separated group names, e.g. "employees,admins". */
    @Column(name = "groups", length = 1024)
    private String groups;

    @Column(name = "synced_at", nullable = false)
    private LocalDateTime syncedAt;

    /* ── Accessors that mirror UserRepresentation field names ── */

    public String getId() {
        return keycloakId;
    }

    public List<String> getGroupList() {
        if (groups == null || groups.isBlank()) return List.of();
        return Arrays.stream(groups.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    public boolean isInGroup(String groupName) {
        return getGroupList().stream().anyMatch(g -> g.equalsIgnoreCase(groupName));
    }
}
