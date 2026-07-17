package com.example.fams.admin;

import com.example.fams.aau.keycloak.KeycloakAdminService;
import com.example.fams.aau.keycloak.SyncedUser;
import com.example.fams.aau.keycloak.SyncedUserRepository;
import com.example.fams.core.config.AuthenticationManager;
import com.example.fams.dashboard.DashboardModelService;
import com.example.fams.settings.AdminSettingsService;
import com.example.fams.settings.AssetCategory;
import jakarta.servlet.http.HttpServletRequest;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Controller
@RequestMapping("/admin")
public class AdminController {
    @Autowired
    KeycloakAdminService keycloakAdminService;

    @Autowired
    AdminSettingsService adminSettingsService;

    @Autowired
    AuthenticationManager authenticationManager;

    @Autowired
    DashboardModelService dashboardModelService;

    @Autowired
    SyncedUserRepository syncedUserRepository;

    /** Primary realm this admin panel operates on. */
    @Value("fams")
    private String realmName;

    private static final int DEFAULT_PAGE_SIZE = 10;


    @GetMapping("/dashboard")
    public String adminDashboard(Model model) {
        dashboardModelService.addDashboardModel(model);
        return "admin/dashboard";
    }

    @GetMapping("/settings")
    public String settings(Model model, RedirectAttributes ra) {
        if (!isAdmin()) {
            ra.addFlashAttribute("errorMessage", "Only administrators can manage system settings.");
            return "redirect:/dashboard";
        }
        adminSettingsService.ensureDefaults();
        model.addAttribute("categories", adminSettingsService.findAllCategories());
        model.addAttribute("systemParameters", adminSettingsService.findSystemParameters());
        model.addAttribute("newCategory", new AssetCategory());
        return "admin/settings";
    }

    @PostMapping("/settings/categories")
    public String createCategory(@ModelAttribute AssetCategory category, RedirectAttributes ra) {
        if (!isAdmin()) {
            ra.addFlashAttribute("errorMessage", "Only administrators can manage system settings.");
            return "redirect:/dashboard";
        }
        try {
            adminSettingsService.createCategory(category);
            ra.addFlashAttribute("successMessage", "Asset category \"" + category.getName() + "\" was created.");
        } catch (Exception ex) {
            ra.addFlashAttribute("errorMessage", sanitize(ex.getMessage()));
        }
        return "redirect:/admin/settings";
    }

    @PostMapping("/settings/categories/{id}")
    public String updateCategory(@PathVariable Long id, @ModelAttribute AssetCategory category, RedirectAttributes ra) {
        if (!isAdmin()) {
            ra.addFlashAttribute("errorMessage", "Only administrators can manage system settings.");
            return "redirect:/dashboard";
        }
        try {
            adminSettingsService.updateCategory(id, category);
            ra.addFlashAttribute("successMessage", "Asset category \"" + category.getName() + "\" was updated.");
        } catch (Exception ex) {
            ra.addFlashAttribute("errorMessage", sanitize(ex.getMessage()));
        }
        return "redirect:/admin/settings";
    }

    @PostMapping("/settings/categories/{id}/delete")
    public String deleteCategory(@PathVariable Long id, RedirectAttributes ra) {
        if (!isAdmin()) {
            ra.addFlashAttribute("errorMessage", "Only administrators can manage system settings.");
            return "redirect:/dashboard";
        }
        try {
            adminSettingsService.deleteCategory(id);
            ra.addFlashAttribute("successMessage", "Asset category was deleted.");
        } catch (Exception ex) {
            ra.addFlashAttribute("errorMessage", sanitize(ex.getMessage()));
        }
        return "redirect:/admin/settings";
    }

    @PostMapping("/settings/parameters")
    public String updateSystemParameters(@RequestParam Map<String, String> parameters, RedirectAttributes ra) {
        if (!isAdmin()) {
            ra.addFlashAttribute("errorMessage", "Only administrators can manage system settings.");
            return "redirect:/dashboard";
        }
        try {
            adminSettingsService.updateParameters(parameters);
            ra.addFlashAttribute("successMessage", "System settings were updated.");
        } catch (Exception ex) {
            ra.addFlashAttribute("errorMessage", sanitize(ex.getMessage()));
        }
        return "redirect:/admin/settings";
    }

    /* ══════════════════════════════════════════════════════════
       Dashboard — User List (search + pagination)
       ══════════════════════════════════════════════════════════ */

    @GetMapping("/users")
    public String dashboard(
            @RequestParam(defaultValue = "")  String search,
            @RequestParam(defaultValue = "0") int    page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size,
            @AuthenticationPrincipal OidcUser principal,
            Model model
    ) {
        // ── Fetch users from the locally-synced snapshot (fast, no live Keycloak call) ──
        List<SyncedUser> allUsers = syncedUserRepository.findAllByOrderByUsernameAsc();

        // In-memory search over the local snapshot (username / name / email).
        if (search != null && !search.isBlank()) {
            String q = search.toLowerCase().trim();
            allUsers = allUsers.stream()
                    .filter(u -> containsIgnoreCase(u.getUsername(), q)
                            || containsIgnoreCase(u.getFirstName(), q)
                            || containsIgnoreCase(u.getLastName(), q)
                            || containsIgnoreCase(u.getEmail(), q))
                    .collect(java.util.stream.Collectors.toList());
        }

        // ── Aggregate stats ────────────────────────────────────
        int  totalUsers  = allUsers.size();
        long totalActive = allUsers.stream()
                .filter(SyncedUser::isEnabled)
                .count();

        // ── Paginate ───────────────────────────────────────────
        int totalPages = totalUsers == 0 ? 1 : (int) Math.ceil((double) totalUsers / size);
        int safePage   = Math.max(0, Math.min(page, totalPages - 1));
        int from       = safePage * size;
        int to         = Math.min(from + size, totalUsers);

        List<SyncedUser> pageUsers =
                from < totalUsers ? allUsers.subList(from, to) : Collections.emptyList();

        // ── All realm groups (used for Access-tab dropdown) ────
        List<GroupRepresentation> groups = Collections.emptyList();
        try {
            groups = keycloakAdminService.getAllGroups(realmName);
        } catch (Exception ignored) { /* non-fatal */ }

        // ── Resolved display name ──────────────────────────────
        String userName = resolveUserName(principal);

        // ── Model ──────────────────────────────────────────────
        model.addAttribute("users",       pageUsers);
        model.addAttribute("totalUsers",  totalUsers);
        model.addAttribute("totalActive", totalActive);
        model.addAttribute("currentPage", safePage);
        model.addAttribute("totalPages",  totalPages);
        model.addAttribute("pageSize",    size);
        model.addAttribute("search",      search);
        model.addAttribute("fromIndex",   totalUsers == 0 ? 0 : from + 1);
        model.addAttribute("toIndex",     to);
        model.addAttribute("groups",      groups);
        model.addAttribute("userName",    userName);
        model.addAttribute("realmName",   realmName);

        return "admin/userManagement";
    }

    /* ══════════════════════════════════════════════════════════
       Create User
       ══════════════════════════════════════════════════════════ */

    @PostMapping("/create-user")
    public String createUser(
            @RequestParam String username,
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam String firstName,
            @RequestParam String lastName,
            @RequestParam(defaultValue = "") String realm,
            HttpServletRequest request,
            RedirectAttributes ra
    ) {
        String targetRealm = (realm == null || realm.isBlank()) ? realmName : realm;
        try {
            keycloakAdminService.createUser(
                    targetRealm, username, email, password,
                    firstName, lastName,
                    parseAttributes(request)
            );
            ra.addFlashAttribute("successMessage",
                    "User \"" + username + "\" created successfully.");
        } catch (Exception ex) {
            ra.addFlashAttribute("errorMessage",
                    "Could not create user: " + sanitize(ex.getMessage()));
        }
        return "redirect:/admin/users";
    }

    /* ══════════════════════════════════════════════════════════
       Enable / Disable
       ══════════════════════════════════════════════════════════ */

    @PostMapping("/enable-user")
    public String enableUser(@RequestParam String userId, RedirectAttributes ra) {
        try {
            keycloakAdminService.enableUser(realmName, userId);
            ra.addFlashAttribute("successMessage", "Account enabled.");
        } catch (Exception ex) {
            ra.addFlashAttribute("errorMessage", "Failed to enable: " + sanitize(ex.getMessage()));
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/disable-user")
    public String disableUser(@RequestParam String userId, RedirectAttributes ra) {
        try {
            keycloakAdminService.disableUser(realmName, userId);
            ra.addFlashAttribute("successMessage", "Account disabled.");
        } catch (Exception ex) {
            ra.addFlashAttribute("errorMessage", "Failed to disable: " + sanitize(ex.getMessage()));
        }
        return "redirect:/admin/users";
    }

    /* ══════════════════════════════════════════════════════════
       Delete
       ══════════════════════════════════════════════════════════ */

    @PostMapping("/delete-user")
    public String deleteUser(@RequestParam String userId, RedirectAttributes ra) {
        try {
            keycloakAdminService.deleteUser(realmName, userId);
            ra.addFlashAttribute("successMessage", "User deleted.");
        } catch (Exception ex) {
            ra.addFlashAttribute("errorMessage", "Delete failed: " + sanitize(ex.getMessage()));
        }
        return "redirect:/admin/users";
    }

    /* ══════════════════════════════════════════════════════════
       Password Actions
       ══════════════════════════════════════════════════════════ */

    @PostMapping("/reset-password-email")
    public String sendPasswordResetEmail(@RequestParam String userId, RedirectAttributes ra) {
        try {
            keycloakAdminService.sendPasswordResetEmail(realmName, userId);
            ra.addFlashAttribute("successMessage", "Password reset email sent.");
        } catch (Exception ex) {
            ra.addFlashAttribute("errorMessage", "Reset email failed: " + sanitize(ex.getMessage()));
        }
        return "redirect:/admin/users";
    }

    /* ══════════════════════════════════════════════════════════
       Group membership
       ══════════════════════════════════════════════════════════ */

    @PostMapping("/add/group")
    public String addUserToGroup(
            @RequestParam String userId,
            @RequestParam String groupName,
            RedirectAttributes ra
    ) {
        try {
            keycloakAdminService.addUserToGroup(realmName, userId, groupName);
            ra.addFlashAttribute("successMessage", "Added to group \"" + groupName + "\".");
        } catch (Exception ex) {
            ra.addFlashAttribute("errorMessage", "Failed to add to group: " + sanitize(ex.getMessage()));
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/remove/group")
    public String removeUserFromGroup(
            @RequestParam String userId,
            @RequestParam String groupName,
            RedirectAttributes ra
    ) {
        try {
            keycloakAdminService.removeUserFromGroup(realmName, userId, groupName);
            ra.addFlashAttribute("successMessage", "Removed from group \"" + groupName + "\".");
        } catch (Exception ex) {
            ra.addFlashAttribute("errorMessage", "Failed to remove from group: " + sanitize(ex.getMessage()));
        }
        return "redirect:/admin/users";
    }

    /* ══════════════════════════════════════════════════════════
       Group CRUD
       ══════════════════════════════════════════════════════════ */

    @PostMapping("/create-group")
    public String createGroup(
            @RequestParam String groupName,
            @RequestParam(required = false) String parentGroupId,
            RedirectAttributes ra
    ) {
        String parentId = (parentGroupId != null && parentGroupId.isBlank()) ? null : parentGroupId;
        try {
            keycloakAdminService.createGroup(realmName, groupName, parentId);
            ra.addFlashAttribute("successMessage", "Group \"" + groupName + "\" created.");
        } catch (Exception ex) {
            ra.addFlashAttribute("errorMessage", "Group creation failed: " + sanitize(ex.getMessage()));
        }
        return "redirect:/admin/users";
    }

    /* ══════════════════════════════════════════════════════════
       JSON — consumed by the Manage panel via fetch()
       ══════════════════════════════════════════════════════════ */

    @GetMapping("/user/{userId}/groups")
    @ResponseBody
    public ResponseEntity<List<String>> getUserGroups(@PathVariable String userId) {
        try {
            return ResponseEntity.ok(keycloakAdminService.getUserGroups(realmName, userId));
        } catch (Exception ex) {
            return ResponseEntity.ok(Collections.emptyList());
        }
    }

    /* ══════════════════════════════════════════════════════════
       Helpers
       ══════════════════════════════════════════════════════════ */

    /**
     * Parses {@code attribute[KEY][INDEX]} multipart params into
     * {@code Map<String, List<String>>} expected by the service.
     */
    private Map<String, List<String>> parseAttributes(HttpServletRequest request) {
        Map<String, List<String>> result  = new LinkedHashMap<>();
        Pattern pattern = Pattern.compile("^attribute\\[(.+?)]\\[\\d+]$");
        for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
            Matcher m = pattern.matcher(entry.getKey());
            if (m.matches()) {
                result.computeIfAbsent(m.group(1), k -> new ArrayList<>())
                        .addAll(Arrays.asList(entry.getValue()));
            }
        }
        return result;
    }

    /**
     * Best-effort display name from the OIDC principal.
     * Falls back gracefully if principal is null (e.g. form-login).
     */
    private String resolveUserName(OidcUser principal) {
        if (principal == null) return "Admin";
        String name = principal.getFullName();
        if (name != null && !name.isBlank()) return name;
        name = principal.getPreferredUsername();
        if (name != null && !name.isBlank()) return name;
        name = principal.getEmail();
        return (name != null && !name.isBlank()) ? name : "Admin";
    }

    /**
     * Strips HTML and truncates before writing to flash attributes.
     */
    private String sanitize(String msg) {
        if (msg == null) return "An unexpected error occurred.";
        String clean = msg.replaceAll("<[^>]+>", "");
        return clean.length() > 250 ? clean.substring(0, 247) + "…" : clean;
    }

    private boolean isAdmin() {
        return authenticationManager.isAdmin();
    }

    /** Null-safe case-insensitive substring check. */
    private boolean containsIgnoreCase(String value, String needle) {
        return value != null && value.toLowerCase().contains(needle);
    }
}
