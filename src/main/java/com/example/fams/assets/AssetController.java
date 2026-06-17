package com.example.fams.assets;

import com.example.fams.aau.keycloak.KeycloakAdminService;
import com.example.fams.organization.Branch;
import com.example.fams.organization.BranchRepository;
import com.example.fams.organization.Company;
import com.example.fams.organization.CompanyRepository;
import com.example.fams.organization.Department;
import com.example.fams.organization.DepartmentRepository;
import com.example.fams.settings.AdminSettingsService;
import com.example.fams.settings.AssetCategory;
import com.example.fams.lifecycle.ApprovalDecision;
import com.example.fams.lifecycle.AssetLifecycleService;
import com.example.fams.lifecycle.AssetLifecycleWorkflow;
import com.example.fams.lifecycle.LifecycleWorkflowForm;
import com.example.fams.lifecycle.LifecycleWorkflowType;
import jakarta.validation.Valid;
import org.flowable.task.api.Task;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class AssetController {

    private final AssetService assetService;
    private final DepartmentRepository departmentRepository;
    private final BranchRepository branchRepository;
    private final CompanyRepository companyRepository;
    private final KeycloakAdminService keycloakAdminService;
    private final AdminSettingsService adminSettingsService;
    private final AssetLifecycleService assetLifecycleService;

    @Value("${keycloak.realm:fams}")
    private String realmName;

    public AssetController(AssetService assetService,
                           DepartmentRepository departmentRepository,
                           BranchRepository branchRepository,
                           CompanyRepository companyRepository,
                           KeycloakAdminService keycloakAdminService,
                           AdminSettingsService adminSettingsService,
                           AssetLifecycleService assetLifecycleService) {
        this.assetService = assetService;
        this.departmentRepository = departmentRepository;
        this.branchRepository = branchRepository;
        this.companyRepository = companyRepository;
        this.keycloakAdminService = keycloakAdminService;
        this.adminSettingsService = adminSettingsService;
        this.assetLifecycleService = assetLifecycleService;
    }

    /**
     * Get the current active company or default to the first active company
     */
    private Company getCurrentCompany() {
        List<Company> activeCompanies = companyRepository.findByIsActiveTrueOrderByCreatedAtDesc();
        return activeCompanies.isEmpty() ? null : activeCompanies.get(0);
    }

    @ModelAttribute("departments")
    public List<Department> departments() {
        Company company = getCurrentCompany();
        if (company == null) {
            return new ArrayList<>();
        }
        return departmentRepository.findByCompanyIdAndIsActiveTrueOrderByCreatedAtDesc(company.getId());
    }

    @ModelAttribute("branches")
    public List<Branch> branches() {
        Company company = getCurrentCompany();
        if (company == null) {
            return new ArrayList<>();
        }
        return branchRepository.findByCompanyIdAndIsActiveTrueOrderByCreatedAtDesc(company.getId());
    }

    @ModelAttribute("custodians")
    public List<CustodianDTO> custodians() {
        try {
            // Get all users and find those in the 'employees' group
            List<UserRepresentation> allUsers = keycloakAdminService.listAllUsers(realmName);

            return allUsers.stream()
                    .filter(user -> {
                        try {
                            List<String> userGroups = keycloakAdminService.getUserGroups(realmName, user.getId());
                            return userGroups.stream()
                                    .anyMatch(group -> group.equalsIgnoreCase("employees"));
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .map(user -> new CustodianDTO(
                            user.getId(),
                            (user.getFirstName() != null ? user.getFirstName() : "") +
                            (user.getLastName() != null ? " " + user.getLastName() : ""),
                            user.getUsername()
                    ))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            // If Keycloak is unavailable, return empty list
            return new ArrayList<>();
        }
    }

    @ModelAttribute("assetCategories")
    public List<AssetCategory> assetCategories() {
        adminSettingsService.ensureDefaults();
        return adminSettingsService.findActiveCategories();
    }

    @GetMapping("/assets")
    public String assetsList(Model model) {
        model.addAttribute("assets", assetService.findAll());
        return "assets/assets-list";
    }

    @GetMapping("/assets/lifecycle/workflows")
    public String lifecycleWorkflows(Model model) {
        List<AssetLifecycleWorkflow> workflows = assetLifecycleService.findAllWorkflows();
        Map<Long, List<Task>> pendingTasks = new HashMap<>();
        for (AssetLifecycleWorkflow workflow : workflows) {
            pendingTasks.put(workflow.getId(), assetLifecycleService.pendingTasks(workflow.getId()));
        }
        model.addAttribute("workflows", workflows);
        model.addAttribute("pendingTasks", pendingTasks);
        return "assets/lifecycle-workflows";
    }

    @PostMapping("/assets/lifecycle/workflows/{workflowId}/tasks/{taskId}/decision")
    public String decideLifecycleTask(@PathVariable Long workflowId,
                                      @PathVariable String taskId,
                                      @RequestParam ApprovalDecision decision,
                                      @RequestParam(required = false) String comment,
                                      RedirectAttributes redirectAttributes) {
        try {
            AssetLifecycleWorkflow workflow = assetLifecycleService.decide(workflowId, taskId, decision, comment);
            redirectAttributes.addFlashAttribute("successMessage", "Workflow " + workflow.getStatus().name().toLowerCase().replace('_', ' ') + ".");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/assets/lifecycle/workflows";
    }

    @GetMapping("/assets/register")
    public String registerAsset(Model model) {
        if (!model.containsAttribute("asset")) {
            model.addAttribute("asset", new Asset());
        }
        return "assets/register-assets";
    }

    @GetMapping("/assets/{id}")
    public String assetDetails(@PathVariable Long id, Model model) {
        Asset asset = assetService.findById(id);
        model.addAttribute("asset", asset);
        model.addAttribute("timeline", assetLifecycleService.timeline(asset));
        model.addAttribute("workflows", assetLifecycleService.findWorkflowsForAsset(asset));
        return "assets/assets-details";
    }

    @GetMapping("/assets/{id}/assignment")
    public String assignmentForm(@PathVariable Long id, Model model) {
        return lifecycleForm(id, LifecycleWorkflowType.ASSIGNMENT, "assets/assets-assignment", model);
    }

    @GetMapping("/assets/{id}/transfer")
    public String transferForm(@PathVariable Long id, Model model) {
        return lifecycleForm(id, LifecycleWorkflowType.TRANSFER, "assets/assets-transfer", model);
    }

    @GetMapping("/assets/{id}/return")
    public String returnForm(@PathVariable Long id, Model model) {
        return lifecycleForm(id, LifecycleWorkflowType.RETURN, "assets/assets-transfer", model);
    }

    @GetMapping("/assets/{id}/disposal")
    public String disposalForm(@PathVariable Long id, Model model) {
        return lifecycleForm(id, LifecycleWorkflowType.DISPOSAL, "assets/assets-disposal", model);
    }

    @PostMapping("/assets/lifecycle/workflows")
    public String submitLifecycleWorkflow(@Valid @ModelAttribute("form") LifecycleWorkflowForm form,
                                          BindingResult bindingResult,
                                          RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", bindingResult.getAllErrors().getFirst().getDefaultMessage());
            return "redirect:/assets/" + form.getAssetId();
        }
        try {
            AssetLifecycleWorkflow workflow = assetLifecycleService.submit(form);
            redirectAttributes.addFlashAttribute("successMessage", workflow.getType().name().toLowerCase() + " workflow submitted for approval.");
            return "redirect:/assets/" + form.getAssetId();
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/assets/" + form.getAssetId();
        }
    }

    @PostMapping("/assets")
    public String createAsset(@ModelAttribute Asset asset,
                              @RequestParam(value = "image", required = false) MultipartFile image,
                              RedirectAttributes redirectAttributes) {
        try {
            Asset savedAsset = assetService.create(asset, image);
            redirectAttributes.addFlashAttribute("successMessage", savedAsset.getAssetCode() + " was registered successfully.");
            return "redirect:/assets";
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            redirectAttributes.addFlashAttribute("asset", asset);
            return "redirect:/assets/register";
        }
    }

    // REST API endpoint for getting all assets as JSON
    @GetMapping("/api/assets")
    @ResponseBody
    public List<Asset> getAssetsJson() {
        return assetService.findAll();
    }

    private String lifecycleForm(Long assetId, LifecycleWorkflowType type, String template, Model model) {
        Asset asset = assetService.findById(assetId);
        if (!model.containsAttribute("form")) {
            LifecycleWorkflowForm form = new LifecycleWorkflowForm();
            form.setAssetId(asset.getId());
            form.setType(type);
            form.setRequestedEffectiveDate(LocalDate.now().plusDays(1));
            form.setToEmployee(type == LifecycleWorkflowType.RETURN ? "Asset Store" : null);
            form.setToDepartment(type == LifecycleWorkflowType.RETURN ? asset.getDepartment() : null);
            form.setToBranch(type == LifecycleWorkflowType.RETURN ? asset.getBranch() : null);
            model.addAttribute("form", form);
        }
        model.addAttribute("asset", asset);
        model.addAttribute("workflowType", type);
        return template;
    }
}
