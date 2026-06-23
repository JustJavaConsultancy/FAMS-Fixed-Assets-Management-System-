package com.example.fams.assets;

import com.example.fams.organization.Company;
import com.example.fams.organization.CompanyRepository;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/assets/checkout")
public class AssetCheckoutController {

    private final AssetCheckoutService checkoutService;
    private final AssetService assetService;
    private final CompanyRepository companyRepository;

    public AssetCheckoutController(AssetCheckoutService checkoutService,
                                   AssetService assetService,
                                   CompanyRepository companyRepository) {
        this.checkoutService = checkoutService;
        this.assetService = assetService;
        this.companyRepository = companyRepository;
    }

    /**
     * Get the current active company or default to the first active company
     */
    private Company getCurrentCompany() {
        List<Company> activeCompanies = companyRepository.findByIsActiveTrueOrderByCreatedAtDesc();
        return activeCompanies.isEmpty() ? null : activeCompanies.get(0);
    }

    /**
     * Display all checkouts/check-ins
     */
    @GetMapping
    public String checkoutsDashboard(Model model) {
        List<AssetCheckout> allCheckouts = checkoutService.getAllCheckouts();
        List<AssetCheckout> activeCheckouts = checkoutService.getActiveCheckouts();
        List<AssetCheckout> overdueCheckouts = checkoutService.getOverdueCheckouts();

        // Filter pending verifications (status == "Returned") in the controller to avoid complex SpEL lambdas in templates
        List<AssetCheckout> pendingVerifications = allCheckouts.stream()
                .filter(c -> "Returned".equalsIgnoreCase(c.getStatus()))
                .toList();

        model.addAttribute("allCheckouts", allCheckouts);
        model.addAttribute("activeCheckouts", activeCheckouts);
        model.addAttribute("overdueCheckouts", overdueCheckouts);
        model.addAttribute("pendingVerifications", pendingVerifications);
        model.addAttribute("activeCount", activeCheckouts.size());
        model.addAttribute("overdueCount", overdueCheckouts.size());
        model.addAttribute("totalCount", allCheckouts.size());
        model.addAttribute("pendingVerificationCount", pendingVerifications.size());

        return "assets/asset-checkout";
    }

    /**
     * Display checkout form for a specific asset
     */
    @GetMapping("/{assetId}/form")
    public String checkoutForm(@PathVariable Long assetId, Model model) {
        Asset asset = assetService.findById(assetId);
        List<AssetCheckout> previousCheckouts = checkoutService.getCheckoutsForAsset(assetId);

        model.addAttribute("asset", asset);
        model.addAttribute("previousCheckouts", previousCheckouts);
        model.addAttribute("checkoutDate", LocalDate.now());
        model.addAttribute("dueReturnDate", LocalDate.now().plusDays(1));

        return "assets/fragments/checkout-form";
    }

    /**
     * Process checkout
     */
    @PostMapping("/{assetId}/create")
    public String createCheckout(@PathVariable Long assetId,
                                 @RequestParam String checkedOutBy,
                                 @RequestParam LocalDate checkoutDate,
                                 @RequestParam LocalDate dueReturnDate,
                                 @RequestParam(required = false) String purpose,
                                 @RequestParam(required = false) String conditionBeforeCheckout,
                                 RedirectAttributes redirectAttributes) {
        try {
            AssetCheckout checkout = checkoutService.checkout(assetId, checkedOutBy, checkoutDate,
                    dueReturnDate, purpose, conditionBeforeCheckout);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Asset checked out successfully. Checkout ID: " + checkout.getId());
            return "redirect:/assets/checkout";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/assets/" + assetId;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "An error occurred while creating checkout: " + e.getMessage());
            return "redirect:/assets/" + assetId;
        }
    }

    /**
     * Display return form for a specific checkout
     */
    @GetMapping("/{checkoutId}/return-form")
    public String returnForm(@PathVariable Long checkoutId, Model model) {
        AssetCheckout checkout = checkoutService.getCheckout(checkoutId);

        if (!checkout.getStatus().equals("Checked Out")) {
            model.addAttribute("errorMessage", "This asset is not currently checked out.");
            return "assets/asset-checkout";
        }

        model.addAttribute("checkout", checkout);
        model.addAttribute("asset", checkout.getAsset());

        // The return and verify fragments are defined inside the
        // `assets/fragments/checkout-form.html` file as named fragments
        // (return-form and verify-form). Return the fragment using the
        // Thymeleaf fragment syntax so the template resolver can locate it.
        return "assets/fragments/checkout-form :: return-form";
    }

    /**
     * Process return
     */
    @PostMapping("/{checkoutId}/return")
    public String processReturn(@PathVariable Long checkoutId,
                               @RequestParam(required = false) String conditionAfterReturn,
                               @RequestParam(required = false) String returnNotes,
                               RedirectAttributes redirectAttributes) {
        try {
            AssetCheckout checkout = checkoutService.returnCheckout(checkoutId, conditionAfterReturn, returnNotes);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Asset returned successfully. Status: Pending Verification");
            return "redirect:/assets/checkout";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/assets/checkout";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "An error occurred while processing return: " + e.getMessage());
            return "redirect:/assets/checkout";
        }
    }

    /**
     * Display verification form for a returned asset
     */
    @GetMapping("/{checkoutId}/verify-form")
    public String verifyForm(@PathVariable Long checkoutId, Model model) {
        AssetCheckout checkout = checkoutService.getCheckout(checkoutId);

        if (!checkout.getStatus().equals("Returned")) {
            model.addAttribute("errorMessage", "This checkout has not been returned yet.");
            return "assets/asset-checkout";
        }

        model.addAttribute("checkout", checkout);
        model.addAttribute("asset", checkout.getAsset());

        // Verify fragment lives in the same file as the return fragment.
        return "assets/fragments/checkout-form :: verify-form";
    }

    /**
     * Process verification
     */
    @PostMapping("/{checkoutId}/verify")
    public String processVerify(@PathVariable Long checkoutId,
                               @RequestParam String verifiedBy,
                               @RequestParam(required = false) String notes,
                               RedirectAttributes redirectAttributes) {
        try {
            AssetCheckout checkout = checkoutService.verifyReturn(checkoutId, verifiedBy, notes);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Asset checkout verified successfully. Asset is now available.");
            return "redirect:/assets/checkout";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/assets/checkout";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "An error occurred while verifying checkout: " + e.getMessage());
            return "redirect:/assets/checkout";
        }
    }

    /**
     * View checkout details
     */
    @GetMapping("/{checkoutId}/details")
    public String viewDetails(@PathVariable Long checkoutId, Model model) {
        AssetCheckout checkout = checkoutService.getCheckout(checkoutId);
        model.addAttribute("checkout", checkout);
        model.addAttribute("asset", checkout.getAsset());
        return "assets/checkout-details";
    }

    /**
     * API endpoint to get checkout by ID (for AJAX calls)
     */
    @GetMapping("/api/{checkoutId}")
    @ResponseBody
    public ResponseEntity<AssetCheckout> getCheckoutApi(@PathVariable Long checkoutId) {
        try {
            AssetCheckout checkout = checkoutService.getCheckout(checkoutId);
            return ResponseEntity.ok(checkout);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    /**
     * API endpoint to get all checkouts for an asset
     */
    @GetMapping("/api/asset/{assetId}")
    @ResponseBody
    public ResponseEntity<List<AssetCheckout>> getAssetCheckoutsApi(@PathVariable Long assetId) {
        try {
            List<AssetCheckout> checkouts = checkoutService.getCheckoutsForAsset(assetId);
            return ResponseEntity.ok(checkouts);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    /**
     * API endpoint to get overdue checkouts
     */
    @GetMapping("/api/overdue")
    @ResponseBody
    public ResponseEntity<List<AssetCheckout>> getOverdueCheckoutsApi() {
        try {
            List<AssetCheckout> overdue = checkoutService.getOverdueCheckouts();
            return ResponseEntity.ok(overdue);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }
}

