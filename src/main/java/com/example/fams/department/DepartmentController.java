package com.example.fams.department;

import com.example.fams.assets.Asset;
import com.example.fams.assets.AssetRepository;
import com.example.fams.lifecycle.ApprovalDecision;
import com.example.fams.lifecycle.AssetLifecycleService;
import com.example.fams.lifecycle.AssetLifecycleWorkflow;
import com.example.fams.lifecycle.AssetLifecycleWorkflowRepository;
import com.example.fams.maintenance.MaintenanceStatus;
import com.example.fams.maintenance.MaintenanceTaskRepository;
import com.example.fams.organization.DepartmentHead;
import com.example.fams.organization.DepartmentHeadRepository;
import org.flowable.task.api.Task;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
public class DepartmentController {

    private final AssetRepository assetRepository;
    private final DepartmentHeadRepository departmentHeadRepository;
    private final AssetLifecycleWorkflowRepository workflowRepository;
    private final AssetLifecycleService assetLifecycleService;
    private final MaintenanceTaskRepository maintenanceTaskRepository;

    public DepartmentController(AssetRepository assetRepository,
                                DepartmentHeadRepository departmentHeadRepository,
                                AssetLifecycleWorkflowRepository workflowRepository,
                                AssetLifecycleService assetLifecycleService,
                                MaintenanceTaskRepository maintenanceTaskRepository) {
        this.assetRepository = assetRepository;
        this.departmentHeadRepository = departmentHeadRepository;
        this.workflowRepository = workflowRepository;
        this.assetLifecycleService = assetLifecycleService;
        this.maintenanceTaskRepository = maintenanceTaskRepository;
    }

    @GetMapping("/department-head/dashboard")
    public String departmentHeadDashboard(Model model) {
        DepartmentHeadContext context = context();
        List<Asset> assets = departmentAssets(context.departments());
        List<AssetLifecycleWorkflow> approvals = pendingDepartmentTransfers(context.departmentKeys());
        Map<Long, List<Task>> pendingTasks = pendingTasks(approvals);

        model.addAttribute("departmentNames", context.departments());
        model.addAttribute("departmentLabel", departmentLabel(context.departments()));
        model.addAttribute("departmentAssets", assets);
        model.addAttribute("pendingApprovals", approvals);
        model.addAttribute("pendingTasks", pendingTasks);
        model.addAttribute("totalDepartmentValue", totalValue(assets));
        model.addAttribute("categoryStats", categoryStats(assets));
        model.addAttribute("recentWorkflows", recentDepartmentWorkflows(context.departmentKeys()));
        model.addAttribute("maintenanceDueCount", maintenanceDueCount(context.departments()));
        return "department-head/dashboard";
    }

    @GetMapping("/department-head/assets")
    public String departmentAssets(Model model) {
        DepartmentHeadContext context = context();
        model.addAttribute("departmentNames", context.departments());
        model.addAttribute("departmentLabel", departmentLabel(context.departments()));
        model.addAttribute("departmentAssets", departmentAssets(context.departments()));
        return "department-head/assets";
    }

    @GetMapping("/department-head/approvals")
    public String departmentApprovals(Model model) {
        DepartmentHeadContext context = context();
        List<AssetLifecycleWorkflow> approvals = pendingDepartmentTransfers(context.departmentKeys());
        model.addAttribute("departmentNames", context.departments());
        model.addAttribute("departmentLabel", departmentLabel(context.departments()));
        model.addAttribute("pendingApprovals", approvals);
        model.addAttribute("pendingTasks", pendingTasks(approvals));
        return "department-head/approvals";
    }

    @PostMapping("/department-head/approvals/{workflowId}/tasks/{taskId}/decision")
    public String decideDepartmentTransfer(@PathVariable Long workflowId,
                                           @PathVariable String taskId,
                                           @RequestParam(name = "decision", required = false) ApprovalDecision decision,
                                           @RequestParam(required = false) String comment,
                                           RedirectAttributes redirectAttributes) {
        if (decision == null) {
            // Missing or invalid decision parameter — return friendly message instead of letting Spring return 400
            redirectAttributes.addFlashAttribute("errorMessage", "A decision (approve/decline) is required to proceed.");
            return "redirect:/department-head/approvals";
        }

        try {
            AssetLifecycleWorkflow workflow = requireEligibleWorkflow(workflowId);
            assetLifecycleService.decide(workflow.getId(), taskId, decision, comment);
            redirectAttributes.addFlashAttribute("successMessage", "Transfer request " + decision.name().toLowerCase() + ".");
        } catch (IllegalArgumentException | NoSuchElementException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/department-head/approvals";
    }

    private AssetLifecycleWorkflow requireEligibleWorkflow(Long workflowId) {
        DepartmentHeadContext context = context();
        AssetLifecycleWorkflow workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new NoSuchElementException("Workflow not found."));
        String fromDepartment = clean(workflow.getFromDepartment());
        boolean fromOwned = fromDepartment != null && context.departmentKeys().contains(fromDepartment.toLowerCase());
        if (workflow.getType().name().equals("TRANSFER") && fromOwned) {
            return workflow;
        }
        throw new IllegalArgumentException("Only transfers from your department can be approved here.");
    }

    private List<Asset> departmentAssets(List<String> departments) {
        if (departments.isEmpty()) {
            return List.of();
        }
        return assetRepository.findByDepartmentInOrderByCreatedAtDesc(departments);
    }

    private List<AssetLifecycleWorkflow> pendingDepartmentTransfers(List<String> departmentKeys) {
        if (departmentKeys.isEmpty()) {
            return List.of();
        }
        return workflowRepository.findPendingTransfersFromDepartments(departmentKeys);
    }

    private List<AssetLifecycleWorkflow> recentDepartmentWorkflows(List<String> departmentKeys) {
        if (departmentKeys.isEmpty()) {
            return List.of();
        }
        return workflowRepository.findByAssetDepartmentInIgnoreCase(departmentKeys)
                .stream()
                .limit(5)
                .toList();
    }

    private Map<Long, List<Task>> pendingTasks(List<AssetLifecycleWorkflow> workflows) {
        Map<Long, List<Task>> tasks = new LinkedHashMap<>();
        for (AssetLifecycleWorkflow workflow : workflows) {
            tasks.put(workflow.getId(), assetLifecycleService.pendingTasks(workflow.getId()));
        }
        return tasks;
    }

    private BigDecimal totalValue(List<Asset> assets) {
        return assets.stream()
                .map(Asset::getPurchaseCost)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Map<String, Long> categoryStats(List<Asset> assets) {
        return assets.stream()
                .collect(Collectors.groupingBy(Asset::getCategory, LinkedHashMap::new, Collectors.counting()))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(4)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (left, right) -> left, LinkedHashMap::new));
    }

    private long maintenanceDueCount(List<String> departments) {
        long count = 0;
        for (String department : departments) {
            count += maintenanceTaskRepository.countByAsset_DepartmentIgnoreCaseAndStatus(department, MaintenanceStatus.DUE);
        }
        return count;
    }

    private DepartmentHeadContext context() {
        List<DepartmentHead> heads = currentDepartmentHeads();
        List<String> departments = heads.stream()
                .map(head -> head.getDepartment().getName())
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .toList();
        List<String> departmentKeys = departments.stream()
                .map(value -> value.toLowerCase().trim())
                .toList();
        return new DepartmentHeadContext(departments, departmentKeys);
    }

    private List<DepartmentHead> currentDepartmentHeads() {
        Set<Long> ids = new LinkedHashSet<>();
        List<DepartmentHead> heads = new ArrayList<>();
        for (String candidate : userLookupCandidates()) {
            for (DepartmentHead head : departmentHeadRepository.findByUserIdAndIsActiveTrueOrderByAssignedAtDesc(candidate)) {
                if (ids.add(head.getId())) {
                    heads.add(head);
                }
            }
            for (DepartmentHead head : departmentHeadRepository.findByUserNameIgnoreCaseAndIsActiveTrueOrderByAssignedAtDesc(candidate)) {
                if (ids.add(head.getId())) {
                    heads.add(head);
                }
            }
            for (DepartmentHead head : departmentHeadRepository.findByUserEmailIgnoreCaseAndIsActiveTrueOrderByAssignedAtDesc(candidate)) {
                if (ids.add(head.getId())) {
                    heads.add(head);
                }
            }
        }
        return heads;
    }

    private List<String> userLookupCandidates() {
        Set<String> candidates = new LinkedHashSet<>();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null) {
            candidates.add(auth.getName());
        }
        if (auth != null && auth.getPrincipal() instanceof DefaultOidcUser oidc) {
            addClaim(candidates, oidc.getSubject());
            addClaim(candidates, oidc.getClaims().get("preferred_username"));
            addClaim(candidates, oidc.getClaims().get("email"));
            addClaim(candidates, oidc.getClaims().get("name"));
        }
        return candidates.stream().filter(value -> value != null && !value.isBlank()).toList();
    }

    private void addClaim(Set<String> candidates, Object value) {
        if (value instanceof String text && !text.isBlank()) {
            candidates.add(text);
        }
    }

    private String departmentLabel(List<String> departments) {
        if (departments.isEmpty()) {
            return "No assigned department";
        }
        if (departments.size() == 1) {
            return departments.getFirst();
        }
        return departments.size() + " departments";
    }

    private String clean(String value) {
        return value == null ? null : value.trim();
    }

    private record DepartmentHeadContext(List<String> departments, List<String> departmentKeys) {
    }
}
