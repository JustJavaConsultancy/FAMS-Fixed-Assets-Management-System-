package com.example.fams.aau.keycloak;

import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Keeps a local snapshot of Keycloak users in sync so that pages can read
 * from the database instead of calling Keycloak on every request.
 *
 * Sync runs:
 *   - once on application startup (via the {@link ApplicationRunner} bean), and
 *   - on a schedule (every 5 minutes by default).
 *
 * The sync is resilient: if Keycloak is unreachable or slow, the failure is
 * logged and the previously-synced snapshot is left intact, so pages keep
 * serving the last good data.
 */
@Service
public class UserSyncService {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(UserSyncService.class);

    private final KeycloakAdminService keycloakAdminService;
    private final SyncedUserRepository syncedUserRepository;
    private final String realmName;

    private volatile boolean syncedAtLeastOnce = false;
    private volatile LocalDateTime lastSuccessfulSync;

    public UserSyncService(
            KeycloakAdminService keycloakAdminService,
            SyncedUserRepository syncedUserRepository,
            @Value("${keycloak.realm}") String realmName
    ) {
        this.keycloakAdminService = keycloakAdminService;
        this.syncedUserRepository = syncedUserRepository;
        this.realmName = realmName;
    }

    /** Runs once when the application has started. */
    @Bean
    public ApplicationRunner userSyncOnStartup() {
        return args -> {
            log.info("Triggering initial Keycloak user sync...");
            syncNow();
        };
    }

    /** Scheduled background refresh. */
    @Scheduled(fixedDelayString = "${fams.user-sync.interval-ms:300000}")
    public void scheduledSync() {
        syncNow();
    }

    @Transactional
    public synchronized void syncNow() {
        try {
            List<UserRepresentation> users = keycloakAdminService.listAllUsers(realmName);

            // Pull groups for each user (cheap per-user call; fine in a background job).
            Map<String, String> groupsByUser = new HashMap<>();
            for (UserRepresentation user : users) {
                try {
                    List<String> grp = keycloakAdminService.getUserGroups(realmName, user.getId());
                    groupsByUser.put(user.getId(), toDelimited(grp));
                } catch (Exception ex) {
                    groupsByUser.put(user.getId(), ",");
                }
            }

            LocalDateTime now = LocalDateTime.now();
            Set<String> seenIds = new HashSet<>();

            for (UserRepresentation user : users) {
                if (user.getId() == null) continue;
                seenIds.add(user.getId());

                SyncedUser entity = syncedUserRepository
                        .findByKeycloakId(user.getId())
                        .orElseGet(SyncedUser::new);

                entity.setKeycloakId(user.getId());
                entity.setUsername(user.getUsername());
                entity.setEmail(user.getEmail());
                entity.setFirstName(user.getFirstName());
                entity.setLastName(user.getLastName());
                entity.setEnabled(Boolean.TRUE.equals(user.isEnabled()));
                entity.setGroups(groupsByUser.getOrDefault(user.getId(), ","));
                entity.setSyncedAt(now);

                syncedUserRepository.save(entity);
            }

            // Remove local rows for users that no longer exist in Keycloak.
            List<SyncedUser> local = syncedUserRepository.findAll();
            List<SyncedUser> toDelete = local.stream()
                    .filter(s -> !seenIds.contains(s.getKeycloakId()))
                    .toList();
            if (!toDelete.isEmpty()) {
                syncedUserRepository.deleteAll(toDelete);
            }

            syncedAtLeastOnce = true;
            lastSuccessfulSync = now;
            log.info("Keycloak user sync complete: {} user(s) synced, {} removed.",
                    users.size(), toDelete.size());
        } catch (Exception ex) {
            log.warn("Keycloak user sync failed (serving last snapshot): {}",
                    ex.getMessage());
        }
    }

    public boolean isSyncedAtLeastOnce() {
        return syncedAtLeastOnce;
    }

    public LocalDateTime getLastSuccessfulSync() {
        return lastSuccessfulSync;
    }

    private static String toDelimited(List<String> groups) {
        if (groups == null || groups.isEmpty()) return ",";
        return groups.stream()
                .filter(Objects::nonNull)
                .map(g -> "," + g.trim())
                .collect(Collectors.joining()) + ",";
    }
}
