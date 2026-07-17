package com.example.fams.employee;

import com.example.fams.assets.Asset;
import com.example.fams.assets.AssetRepository;
import com.example.fams.assets.AssetService;
import com.example.fams.assets.AssetCheckout;
import com.example.fams.assets.AssetCheckoutRepository;
import com.example.fams.assets.AssetCheckoutService;
import com.example.fams.lifecycle.AssetLifecycleService;
import com.example.fams.lifecycle.AssetLifecycleWorkflowRepository;
import com.example.fams.lifecycle.LifecycleWorkflowForm;
import com.example.fams.lifecycle.LifecycleWorkflowStatus;
import com.example.fams.lifecycle.LifecycleWorkflowType;
import com.example.fams.lifecycle.AssetLifecycleWorkflow;
import com.example.fams.maintenance.MaintenanceRecord;
import com.example.fams.maintenance.MaintenanceRecordRepository;
import com.example.fams.maintenance.MaintenanceService;
import com.example.fams.maintenance.MaintenanceStatus;
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

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

@Controller
public class EmployeeController {

    private final AssetRepository assetRepository;
    private final AssetService assetService;
    private final MaintenanceService maintenanceService;
    private final MaintenanceRecordRepository maintenanceRecordRepository;
    private final AssetLifecycleService assetLifecycleService;
    private final AssetLifecycleWorkflowRepository workflowRepository;
    private final AssetCheckoutService checkoutService;
    private final AssetCheckoutRepository checkoutRepository;

    public EmployeeController(AssetRepository assetRepository,
                              AssetService assetService,
                              MaintenanceService maintenanceService,
                              MaintenanceRecordRepository maintenanceRecordRepository,
                              AssetLifecycleService assetLifecycleService,
                              AssetLifecycleWorkflowRepository workflowRepository,
                              AssetCheckoutService checkoutService,
                              AssetCheckoutRepository checkoutRepository) {
        this.assetRepository = assetRepository;
        this.assetService = assetService;
        this.maintenanceService = maintenanceService;
        this.maintenanceRecordRepository = maintenanceRecordRepository;
        this.assetLifecycleService = assetLifecycleService;
        this.workflowRepository = workflowRepository;
        this.checkoutService = checkoutService;
        this.checkoutRepository = checkoutRepository;
    }

    @GetMapping("/employee/dashboard")
    public String employeeDashboard(Model model) {
        List<Asset> assets = assignedAssets();
        model.addAttribute("assignedAssets", assets);
        model.addAttribute("maintenanceRequests", maintenanceFor(assets));
        model.addAttribute("returnRequests", workflowsFor(assets));
        model.addAttribute("openMaintenanceCount", openMaintenanceCount(assets));
        model.addAttribute("pendingWorkflowCount", pendingWorkflowCount(assets));
        return "employee/dashboard";
    }

    @GetMapping("/employee/assets")
    public String employeeAssets(Model model) {
        model.addAttribute("assignedAssets", assignedAssets());
        return "employee/assets";
    }

    @GetMapping("/employee/assets/{id}")
    public String employeeAssetDetails(@PathVariable Long id, Model model) {
        Asset asset = requireAssignedAsset(id);
        model.addAttribute("asset", asset);
        model.addAttribute("maintenanceRequests", maintenanceRecordRepository.findByAssetOrderByMaintenanceDateDescCreatedAtDesc(asset));
        model.addAttribute("returnRequests", workflowRepository.findByAssetOrderByRequestedAtDesc(asset));
        model.addAttribute("timeline", assetLifecycleService.timeline(asset));
        List<AssetCheckout> activeCheckouts = checkoutRepository
                .findByAssetIdAndStatusOrderByCheckoutDateDesc(asset.getId(), "Checked Out");
        List<AssetCheckout> pendingReturns = checkoutRepository
                .findByAssetIdAndStatusOrderByCheckoutDateDesc(asset.getId(), "Pending Return");
        List<AssetCheckout> openCheckouts = new java.util.ArrayList<>(activeCheckouts);
        openCheckouts.addAll(pendingReturns);
        model.addAttribute("activeCheckout", openCheckouts.isEmpty() ? null : openCheckouts.get(0));
        List<AssetCheckout> pendingCheckouts = checkoutRepository
                .findByAssetIdAndStatusOrderByCheckoutDateDesc(asset.getId(), "Pending Approval");
        model.addAttribute("pendingCheckout", pendingCheckouts.isEmpty() ? null : pendingCheckouts.get(0));
        return "employee/asset-details";
    }

    @GetMapping("/employee/maintenance-requests")
    public String maintenanceRequests(Model model) {
        List<Asset> assets = assignedAssets();
        model.addAttribute("assignedAssets", assets);
        model.addAttribute("maintenanceRequests", maintenanceFor(assets));
        return "employee/maintenance-requests";
    }

    @PostMapping("/employee/maintenance-requests")
    public String submitMaintenanceRequest(@RequestParam Long assetId,
                                           @RequestParam String issueDescription,
                                           @RequestParam(required = false) String serviceProvider,
                                           RedirectAttributes redirectAttributes) {
        try {
            Asset asset = requireAssignedAsset(assetId);
            maintenanceService.recordCorrective(asset.getId(), issueDescription, serviceProvider, null, null, currentDisplayName());
            redirectAttributes.addFlashAttribute("successMessage", "Maintenance request submitted and routed to asset management.");
        } catch (IllegalArgumentException | NoSuchElementException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/employee/maintenance-requests";
    }

    @GetMapping("/employee/return-requests")
    public String returnRequests(Model model) {
        List<Asset> assets = assignedAssets();
        model.addAttribute("assignedAssets", assets);
        model.addAttribute("returnRequests", workflowsFor(assets));
        model.addAttribute("tomorrow", LocalDate.now().plusDays(1));
        return "employee/return-requests";
    }

    @PostMapping("/employee/return-requests")
    public String submitReturnRequest(@RequestParam Long assetId,
                                      @RequestParam LocalDate requestedEffectiveDate,
                                      @RequestParam(required = false) String reason,
                                      RedirectAttributes redirectAttributes) {
        try {
            Asset asset = requireAssignedAsset(assetId);
            LifecycleWorkflowForm form = new LifecycleWorkflowForm();
            form.setAssetId(asset.getId());
            form.setType(LifecycleWorkflowType.TRANSFER);
            form.setRequestedEffectiveDate(requestedEffectiveDate);
            form.setToEmployee("Asset Store");
            form.setToDepartment(asset.getDepartment());
            form.setToBranch(asset.getBranch());
            form.setReason(reason);
            assetLifecycleService.submit(form);
            redirectAttributes.addFlashAttribute("successMessage", "Return request submitted for approval.");
        } catch (IllegalArgumentException | NoSuchElementException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/employee/return-requests";
    }

    @GetMapping("/employee/checkout-requests")
    public String checkoutRequests(Model model) {
        String current = currentUsername();
        List<AssetCheckout> myRequests = checkoutRepository
                .findByStatusAndRequestedByOrderByRequestedAtDesc("Pending Approval", current);
        List<AssetCheckout> myHistory = checkoutRepository
                .findByRequestedByOrderByRequestedAtDesc(current);
        model.addAttribute("myRequests", myRequests);
        model.addAttribute("myHistory", myHistory);
        model.addAttribute("assignedAssets", assignedAssets());
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("tomorrow", LocalDate.now().plusDays(1));
        return "employee/checkout-requests";
    }

    @PostMapping("/employee/checkout-requests")
    public String submitCheckoutRequest(@RequestParam Long assetId,
                                        @RequestParam LocalDate checkoutDate,
                                        @RequestParam LocalDate dueReturnDate,
                                        @RequestParam(required = false) String purpose,
                                        @RequestParam(required = false) String conditionBeforeCheckout,
                                        RedirectAttributes redirectAttributes) {
        try {
            Asset asset = requireAssignedAsset(assetId);
            checkoutService.requestCheckout(asset.getId(), currentUsername(),
                    checkoutDate, dueReturnDate, purpose, conditionBeforeCheckout);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Checkout request submitted and is awaiting asset manager approval.");
        } catch (IllegalArgumentException | NoSuchElementException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/employee/checkout-requests";
    }

    /**
     * Employee submits a return request for an asset they currently have checked out. The request
     * goes into "Pending Return" and is approved by the asset manager before the asset is returned.
     */
    @PostMapping("/employee/checkout/{checkoutId}/request-return")
    public String requestReturn(@PathVariable Long checkoutId,
                                @RequestParam(required = false) String conditionAfterReturn,
                                @RequestParam(required = false) String returnNotes,
                                RedirectAttributes redirectAttributes) {
        try {
            AssetCheckout checkout = checkoutRepository.findById(checkoutId)
                    .orElseThrow(() -> new NoSuchElementException("Checkout not found with id: " + checkoutId));
            String current = currentUsername();
            if (checkout.getRequestedBy() != null && !checkout.getRequestedBy().equalsIgnoreCase(current)
                    && checkout.getCheckedOutBy() != null && !checkout.getCheckedOutBy().equalsIgnoreCase(current)) {
                throw new NoSuchElementException("This checkout does not belong to the current employee.");
            }
            checkoutService.requestReturn(checkout.getId(), conditionAfterReturn, returnNotes);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Return request submitted. Awaiting asset manager approval.");
        } catch (IllegalArgumentException | NoSuchElementException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/employee/assets";
    }

    /**
     * Display the employee's return-request form for a checkout they own.
     */
    @GetMapping("/employee/checkout/{checkoutId}/request-return-form")
    public String requestReturnForm(@PathVariable Long checkoutId, Model model) {
        AssetCheckout checkout = checkoutRepository.findById(checkoutId)
                .orElseThrow(() -> new NoSuchElementException("Checkout not found with id: " + checkoutId));
        String current = currentUsername();
        boolean owns = (checkout.getRequestedBy() != null && checkout.getRequestedBy().equalsIgnoreCase(current))
                || (checkout.getCheckedOutBy() != null && checkout.getCheckedOutBy().equalsIgnoreCase(current));
        if (!owns) {
            throw new NoSuchElementException("This checkout does not belong to the current employee.");
        }
        model.addAttribute("checkout", checkout);
        model.addAttribute("asset", checkout.getAsset());
        return "assets/fragments/checkout-form :: request-return-form";
    }

    private List<Asset> assignedAssets() {
        List<String> candidates = userLookupCandidates();
        if (candidates.isEmpty()) return List.of();
        // normalize to lower-case for the repository query
        List<String> lowered = candidates.stream().map(String::toLowerCase).toList();
        return assetRepository.findByCustodianInIgnoreCaseOrderByCreatedAtDesc(lowered);
    }

    private Asset requireAssignedAsset(Long id) {
        Asset asset = assetService.findById(id);
        String custodian = asset.getCustodian();
        if (custodian != null) {
            for (String candidate : userLookupCandidates()) {
                if (custodian.equalsIgnoreCase(candidate)) {
                    return asset;
                }
            }
        }
        throw new NoSuchElementException("Asset is not assigned to the current employee.");
    }

    private List<MaintenanceRecord> maintenanceFor(List<Asset> assets) {
        if (assets.isEmpty()) {
            return List.of();
        }
        return maintenanceRecordRepository.findByAssetInOrderByMaintenanceDateDescCreatedAtDesc(assets);
    }

    private List<AssetLifecycleWorkflow> workflowsFor(List<Asset> assets) {
        if (assets.isEmpty()) {
            return List.of();
        }
        return workflowRepository.findByAssetInOrderByRequestedAtDesc(assets);
    }

    private long openMaintenanceCount(List<Asset> assets) {
        if (assets.isEmpty()) {
            return 0;
        }
        return maintenanceRecordRepository.countByAssetInAndStatus(assets, MaintenanceStatus.OPEN);
    }

    private long pendingWorkflowCount(List<Asset> assets) {
        if (assets.isEmpty()) {
            return 0;
        }
        return workflowRepository.countByAssetInAndStatus(assets, LifecycleWorkflowStatus.PENDING_APPROVAL);
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            return "guest";
        }
        return auth.getName();
    }

    private String currentDisplayName() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return "Guest";
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof DefaultOidcUser oidc) {
            Object name = oidc.getClaims().get("name");
            if (name instanceof String value && !value.isBlank()) {
                return value;
            }
            Object username = oidc.getClaims().get("preferred_username");
            if (username instanceof String value && !value.isBlank()) {
                return value;
            }
        }
        return currentUsername();
    }

    /**
     * Build a list of possible user identifiers to match against stored custodian values.
     * This mirrors the logic used elsewhere so custodian matches work whether stored as
     * subject id, preferred_username, email or display name.
     */
    private List<String> userLookupCandidates() {
        java.util.Set<String> candidates = new java.util.LinkedHashSet<>();
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

    private void addClaim(java.util.Set<String> candidates, Object value) {
        if (value instanceof String text && !text.isBlank()) {
            candidates.add(text);
        }
    }
}
