package com.example.fams.superadmin;

import com.example.fams.core.ApiResponse;
import com.example.fams.organization.*;
import com.example.fams.organization.dto.*;
import com.example.fams.aau.keycloak.KeycloakAdminService;
import lombok.extern.slf4j.Slf4j;
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

@Controller
@RequestMapping("/superadmin")
@Slf4j
public class SuperAdminCompanyController {

    @Autowired
    private CompanyStructureService companyStructureService;

    @Autowired
    private KeycloakAdminService keycloakAdminService;

    @Value("fams")
    private String realmName;

    // ============== COMPANY STRUCTURE PAGE ==============

    @GetMapping("/company-structure")
    public String companyStructurePage(Model model, @AuthenticationPrincipal OidcUser principal) {
        try {
            List<CompanyDTO> companies = companyStructureService.getAllCompanies();
            List<LocationDTO> allLocations = new ArrayList<>();
            List<BranchDTO> allBranches = new ArrayList<>();

            for (CompanyDTO company : companies) {
                allLocations.addAll(companyStructureService.getLocationsByCompany(company.getId()));
                allBranches.addAll(companyStructureService.getBranchesByCompany(company.getId()));
            }

            model.addAttribute("companies", companies);
            model.addAttribute("locations", allLocations);
            model.addAttribute("branches", allBranches);
            model.addAttribute("userName", resolveUserName(principal));
            model.addAttribute("locationTypes", Location.LocationType.values());
            model.addAttribute("locationStatuses", Location.LocationStatus.values());
            model.addAttribute("branchStatuses", Branch.BranchStatus.values());
            model.addAttribute("companyStatuses", Company.CompanyStatus.values());

            return "admin/company-structure";
        } catch (Exception e) {
            log.error("Error loading company structure page: " + e.getMessage());
            model.addAttribute("errorMessage", "Failed to load company structure");
            return "admin/company-structure";
        }
    }

    // ============== COMPANY MANAGEMENT ==============

    @PostMapping("/company/create")
    public String createCompany(
            @RequestParam String name,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String registrationNumber,
            @RequestParam(required = false) String taxId,
            @RequestParam(required = false) String industry,
            RedirectAttributes ra
    ) {
        try {
            CompanyDTO dto = CompanyDTO.builder()
                    .name(name)
                    .description(description)
                    .registrationNumber(registrationNumber)
                    .taxId(taxId)
                    .industry(industry)
                    .status("ACTIVE")
                    .build();

            CompanyDTO created = companyStructureService.createCompany(dto);
            ra.addFlashAttribute("successMessage", "Company \"" + created.getName() + "\" created successfully");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Failed to create company: " + sanitize(e.getMessage()));
        }
        return "redirect:/superadmin/company-structure";
    }

    @PostMapping("/company/update/{id}")
    public String updateCompany(
            @PathVariable Long id,
            @RequestParam String name,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String registrationNumber,
            @RequestParam(required = false) String taxId,
            @RequestParam(required = false) String industry,
            @RequestParam String status,
            RedirectAttributes ra
    ) {
        try {
            CompanyDTO dto = CompanyDTO.builder()
                    .name(name)
                    .description(description)
                    .registrationNumber(registrationNumber)
                    .taxId(taxId)
                    .industry(industry)
                    .status(status)
                    .build();

            CompanyDTO updated = companyStructureService.updateCompany(id, dto);
            ra.addFlashAttribute("successMessage", "Company \"" + updated.getName() + "\" updated successfully");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Failed to update company: " + sanitize(e.getMessage()));
        }
        return "redirect:/superadmin/company-structure";
    }

    @PostMapping("/company/delete/{id}")
    public String deleteCompany(@PathVariable Long id, RedirectAttributes ra) {
        try {
            companyStructureService.deleteCompany(id);
            ra.addFlashAttribute("successMessage", "Company deleted successfully");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Failed to delete company: " + sanitize(e.getMessage()));
        }
        return "redirect:/superadmin/company-structure";
    }

    // ============== LOCATION MANAGEMENT ==============

    @PostMapping("/location/create")
    public String createLocation(
            @RequestParam Long companyId,
            @RequestParam String name,
            @RequestParam(required = false) String address,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String postalCode,
            @RequestParam(required = false) String phoneNumber,
            @RequestParam(required = false) String contactPerson,
            @RequestParam(defaultValue = "OFFICE") String locationType,
            RedirectAttributes ra
    ) {
        try {
            LocationDTO dto = LocationDTO.builder()
                    .companyId(companyId)
                    .name(name)
                    .address(address)
                    .city(city)
                    .state(state)
                    .country(country)
                    .postalCode(postalCode)
                    .phoneNumber(phoneNumber)
                    .contactPerson(contactPerson)
                    .locationType(locationType)
                    .status("ACTIVE")
                    .build();

            LocationDTO created = companyStructureService.createLocation(dto);
            ra.addFlashAttribute("successMessage", "Location \"" + created.getName() + "\" created successfully");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Failed to create location: " + sanitize(e.getMessage()));
        }
        return "redirect:/superadmin/company-structure";
    }

    @PostMapping("/location/update/{id}")
    public String updateLocation(
            @PathVariable Long id,
            @RequestParam String name,
            @RequestParam(required = false) String address,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String postalCode,
            @RequestParam(required = false) String phoneNumber,
            @RequestParam(required = false) String contactPerson,
            @RequestParam String locationType,
            @RequestParam String status,
            RedirectAttributes ra
    ) {
        try {
            LocationDTO dto = LocationDTO.builder()
                    .name(name)
                    .address(address)
                    .city(city)
                    .state(state)
                    .country(country)
                    .postalCode(postalCode)
                    .phoneNumber(phoneNumber)
                    .contactPerson(contactPerson)
                    .locationType(locationType)
                    .status(status)
                    .build();

            LocationDTO updated = companyStructureService.updateLocation(id, dto);
            ra.addFlashAttribute("successMessage", "Location \"" + updated.getName() + "\" updated successfully");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Failed to update location: " + sanitize(e.getMessage()));
        }
        return "redirect:/superadmin/company-structure";
    }

    @PostMapping("/location/delete/{id}")
    public String deleteLocation(@PathVariable Long id, RedirectAttributes ra) {
        try {
            companyStructureService.deleteLocation(id);
            ra.addFlashAttribute("successMessage", "Location deleted successfully");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Failed to delete location: " + sanitize(e.getMessage()));
        }
        return "redirect:/superadmin/company-structure";
    }

    // ============== BRANCH MANAGEMENT ==============

    @PostMapping("/branch/create")
    public String createBranch(
            @RequestParam Long companyId,
            @RequestParam Long locationId,
            @RequestParam String name,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String branchCode,
            @RequestParam(required = false) String managerName,
            @RequestParam(required = false) String managerPhone,
            @RequestParam(required = false) String managerEmail,
            RedirectAttributes ra
    ) {
        try {
            BranchDTO dto = BranchDTO.builder()
                    .companyId(companyId)
                    .locationId(locationId)
                    .name(name)
                    .description(description)
                    .branchCode(branchCode)
                    .managerName(managerName)
                    .managerPhone(managerPhone)
                    .managerEmail(managerEmail)
                    .status("ACTIVE")
                    .build();

            BranchDTO created = companyStructureService.createBranch(dto);
            ra.addFlashAttribute("successMessage", "Branch \"" + created.getName() + "\" created successfully");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Failed to create branch: " + sanitize(e.getMessage()));
        }
        return "redirect:/superadmin/company-structure";
    }

    @PostMapping("/branch/update/{id}")
    public String updateBranch(
            @PathVariable Long id,
            @RequestParam String name,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String branchCode,
            @RequestParam(required = false) String managerName,
            @RequestParam(required = false) String managerPhone,
            @RequestParam(required = false) String managerEmail,
            @RequestParam String status,
            RedirectAttributes ra
    ) {
        try {
            BranchDTO dto = BranchDTO.builder()
                    .name(name)
                    .description(description)
                    .branchCode(branchCode)
                    .managerName(managerName)
                    .managerPhone(managerPhone)
                    .managerEmail(managerEmail)
                    .status(status)
                    .build();

            BranchDTO updated = companyStructureService.updateBranch(id, dto);
            ra.addFlashAttribute("successMessage", "Branch \"" + updated.getName() + "\" updated successfully");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Failed to update branch: " + sanitize(e.getMessage()));
        }
        return "redirect:/superadmin/company-structure";
    }

    @PostMapping("/branch/delete/{id}")
    public String deleteBranch(@PathVariable Long id, RedirectAttributes ra) {
        try {
            companyStructureService.deleteBranch(id);
            ra.addFlashAttribute("successMessage", "Branch deleted successfully");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Failed to delete branch: " + sanitize(e.getMessage()));
        }
        return "redirect:/superadmin/company-structure";
    }

    // ============== MERGED DEPARTMENTS & HEADS PAGE ==============

    @GetMapping("/departments")
    public String departmentsAndHeadsPage(Model model, @AuthenticationPrincipal OidcUser principal) {
        try {
            // Load all companies (for department creation dropdown)
            List<CompanyDTO> companies = companyStructureService.getAllCompanies();

            // Load all departments (for listing and head assignment dropdown)
            List<DepartmentDTO> allDepartments = new ArrayList<>();
            for (CompanyDTO company : companies) {
                allDepartments.addAll(companyStructureService.getDepartmentsByCompany(company.getId()));
            }

            // Load all department heads (for listing)
            List<DepartmentHeadDTO> allHeads = companyStructureService.getAllDepartmentHeads();

            // Load users from Keycloak (for head assignment dropdown)
            List<UserRepresentation> users = new ArrayList<>();
            try {
                users = keycloakAdminService.listAllUsers(realmName);
            } catch (Exception e) {
                log.warn("Could not fetch users from Keycloak: " + e.getMessage());
            }

            model.addAttribute("companies", companies);
            model.addAttribute("departments", allDepartments);
            model.addAttribute("departmentHeads", allHeads);
            model.addAttribute("users", users);
            model.addAttribute("userName", resolveUserName(principal));
            model.addAttribute("departmentStatuses", Department.DepartmentStatus.values());
            model.addAttribute("headStatuses", DepartmentHead.HeadStatus.values());

            return "admin/departments-management"; // merged view
        } catch (Exception e) {
            log.error("Error loading departments & heads page: " + e.getMessage());
            model.addAttribute("errorMessage", "Failed to load departments and heads");
            return "admin/departments-management";
        }
    }

    // Redirect the old separate page to the merged one
    @GetMapping("/department-heads")
    public String redirectDepartmentHeads() {
        return "redirect:/superadmin/departments";
    }

    // ============== DEPARTMENT MANAGEMENT ==============

    @PostMapping("/department/create")
    public String createDepartment(
            @RequestParam Long companyId,
            @RequestParam Long branchId,
            @RequestParam String name,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String departmentCode,
            @RequestParam(required = false) String budget,
            RedirectAttributes ra
    ) {
        try {
            DepartmentDTO dto = DepartmentDTO.builder()
                    .companyId(companyId)
                    .branchId(branchId)
                    .name(name)
                    .description(description)
                    .departmentCode(departmentCode)
                    .budget(budget)
                    .status("ACTIVE")
                    .build();

            DepartmentDTO created = companyStructureService.createDepartment(dto);
            ra.addFlashAttribute("successMessage", "Department \"" + created.getName() + "\" created successfully");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Failed to create department: " + sanitize(e.getMessage()));
        }
        return "redirect:/superadmin/departments";
    }

    @PostMapping("/department/update/{id}")
    public String updateDepartment(
            @PathVariable Long id,
            @RequestParam String name,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String departmentCode,
            @RequestParam(required = false) String budget,
            @RequestParam String status,
            RedirectAttributes ra
    ) {
        try {
            DepartmentDTO dto = DepartmentDTO.builder()
                    .name(name)
                    .description(description)
                    .departmentCode(departmentCode)
                    .budget(budget)
                    .status(status)
                    .build();

            DepartmentDTO updated = companyStructureService.updateDepartment(id, dto);
            ra.addFlashAttribute("successMessage", "Department \"" + updated.getName() + "\" updated successfully");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Failed to update department: " + sanitize(e.getMessage()));
        }
        return "redirect:/superadmin/departments";
    }

    @PostMapping("/department/delete/{id}")
    public String deleteDepartment(@PathVariable Long id, RedirectAttributes ra) {
        try {
            companyStructureService.deleteDepartment(id);
            ra.addFlashAttribute("successMessage", "Department deleted successfully");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Failed to delete department: " + sanitize(e.getMessage()));
        }
        return "redirect:/superadmin/departments";
    }

    // ============== DEPARTMENT HEAD MANAGEMENT ==============

    @PostMapping("/department-head/assign")
    public String assignDepartmentHead(
            @RequestParam Long departmentId,
            @RequestParam String userId,
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) String userEmail,
            @RequestParam(required = false) String fullName,
            @RequestParam(defaultValue = "false") Boolean isPrimary,
            RedirectAttributes ra
    ) {
        try {
            DepartmentHeadDTO dto = DepartmentHeadDTO.builder()
                    .departmentId(departmentId)
                    .userId(userId)
                    .userName(userName != null ? userName : userId)
                    .userEmail(userEmail != null ? userEmail : "")
                    .fullName(fullName != null ? fullName : "")
                    .isPrimary(isPrimary)
                    .build();

            companyStructureService.assignDepartmentHead(dto);
            ra.addFlashAttribute("successMessage", "User assigned as department head successfully");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Failed to assign department head: " + sanitize(e.getMessage()));
        }
        return "redirect:/superadmin/departments";
    }

    @PostMapping("/department-head/remove/{id}")
    public String removeDepartmentHead(@PathVariable Long id, RedirectAttributes ra) {
        try {
            companyStructureService.removeDepartmentHead(id);
            ra.addFlashAttribute("successMessage", "Department head removed successfully");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Failed to remove department head: " + sanitize(e.getMessage()));
        }
        return "redirect:/superadmin/departments";
    }

    // ============== JSON API ENDPOINTS ==============

    @GetMapping("/api/branches/{companyId}")
    @ResponseBody
    public ResponseEntity<?> getBranchesForCompany(@PathVariable Long companyId) {
        try {
            List<BranchDTO> branches = companyStructureService.getBranchesByCompany(companyId);
            return ResponseEntity.ok(ApiResponse.success("Branches retrieved", branches));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error("Failed to retrieve branches", "ERROR", e.getMessage()));
        }
    }

    @GetMapping("/api/departments/{branchId}")
    @ResponseBody
    public ResponseEntity<?> getDepartmentsForBranch(@PathVariable Long branchId) {
        try {
            List<DepartmentDTO> departments = companyStructureService.getDepartmentsByBranch(branchId);
            return ResponseEntity.ok(ApiResponse.success("Departments retrieved", departments));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error("Failed to retrieve departments", "ERROR", e.getMessage()));
        }
    }

    @GetMapping("/api/locations/{companyId}")
    @ResponseBody
    public ResponseEntity<?> getLocationsForCompany(@PathVariable Long companyId) {
        try {
            List<LocationDTO> locations = companyStructureService.getLocationsByCompany(companyId);
            return ResponseEntity.ok(ApiResponse.success("Locations retrieved", locations));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error("Failed to retrieve locations", "ERROR", e.getMessage()));
        }
    }

    @GetMapping("/api/department-heads/{departmentId}")
    @ResponseBody
    public ResponseEntity<?> getDepartmentHeadsForDepartment(@PathVariable Long departmentId) {
        try {
            List<DepartmentHeadDTO> heads = companyStructureService.getDepartmentHeads(departmentId);
            return ResponseEntity.ok(ApiResponse.success("Department heads retrieved", heads));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error("Failed to retrieve department heads", "ERROR", e.getMessage()));
        }
    }

    // ============== HELPERS ==============

    private String sanitize(String msg) {
        if (msg == null) return "An unexpected error occurred.";
        String clean = msg.replaceAll("<[^>]+>", "");
        return clean.length() > 250 ? clean.substring(0, 247) + "…" : clean;
    }

    private String resolveUserName(OidcUser principal) {
        if (principal == null) return "Super Admin";
        String name = principal.getFullName();
        if (name != null && !name.isBlank()) return name;
        name = principal.getPreferredUsername();
        if (name != null && !name.isBlank()) return name;
        name = principal.getEmail();
        return (name != null && !name.isBlank()) ? name : "Super Admin";
    }
}