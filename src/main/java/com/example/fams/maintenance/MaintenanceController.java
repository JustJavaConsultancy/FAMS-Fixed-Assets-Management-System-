package com.example.fams.maintenance;

import com.example.fams.assets.Asset;
import com.example.fams.assets.AssetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Controller
public class MaintenanceController {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceController.class);

    private final AssetService assetService;
    private final MaintenanceService maintenanceService;

    public MaintenanceController(AssetService assetService, MaintenanceService maintenanceService) {
        this.assetService = assetService;
        this.maintenanceService = maintenanceService;
    }

    @GetMapping("/maintenance")
    public String maintenanceManagement(@RequestParam(value = "start", required = false) LocalDate start,
                                        @RequestParam(value = "end", required = false) LocalDate end,
                                        Model model) {
        LocalDate reportEnd = end == null ? LocalDate.now() : end;
        LocalDate reportStart = start == null ? reportEnd.minusMonths(1) : start;
        List<Asset> assets = assetService.findAll();
        model.addAttribute("assets", assets);
        model.addAttribute("schedules", maintenanceService.schedules());
        model.addAttribute("recentTasks", maintenanceService.recentTasks());
        model.addAttribute("correctiveRecords", maintenanceService.recentCorrectiveRecords());
        model.addAttribute("reportRows", maintenanceService.report(reportStart, reportEnd));
        model.addAttribute("reportTotal", maintenanceService.reportTotal(reportStart, reportEnd));
        model.addAttribute("reportStart", reportStart);
        model.addAttribute("reportEnd", reportEnd);
        model.addAttribute("frequencies", MaintenanceFrequency.values());
        model.addAttribute("correctiveCount", maintenanceService.correctiveCount());
        model.addAttribute("dueTaskCount", maintenanceService.dueTaskCount());
        return "assets/maintainance-management";
    }

    @PostMapping("/assets/maintenance/schedules")
    public String createSchedule(@RequestParam(value = "assetId", required = false) Long assetId,
                                 @RequestParam(value = "assetCategory", required = false) String assetCategory,
                                 @RequestParam String serviceType,
                                 @RequestParam MaintenanceFrequency frequency,
                                 @RequestParam LocalDate startDate,
                                 @RequestParam String responsibleParty,
                                 @RequestParam String responsibleRole,
                                 RedirectAttributes redirectAttributes) {
        try {
            maintenanceService.createSchedule(assetId, assetCategory, serviceType, frequency, startDate, responsibleParty, responsibleRole);
            redirectAttributes.addFlashAttribute("successMessage", "Preventive maintenance schedule stored successfully.");
        } catch (Exception ex) {
            log.warn("Failed to create maintenance schedule: {}", ex.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", sanitize(ex.getMessage()));
        }
        return "redirect:/maintenance";
    }

    @PostMapping("/assets/maintenance/corrective")
    public String recordCorrective(@RequestParam Long assetId,
                                   @RequestParam String issueDescription,
                                   @RequestParam String serviceProvider,
                                   @RequestParam(required = false) BigDecimal maintenanceCost,
                                   @RequestParam(required = false) LocalDate resolutionDate,
                                   RedirectAttributes redirectAttributes) {
        try {
            maintenanceService.recordCorrective(assetId, issueDescription, serviceProvider, maintenanceCost, resolutionDate);
            redirectAttributes.addFlashAttribute("successMessage", "Corrective maintenance event saved to asset history.");
        } catch (Exception ex) {
            log.warn("Failed to record corrective maintenance: {}", ex.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", sanitize(ex.getMessage()));
        }
        return "redirect:/maintenance";
    }

    @PostMapping("/assets/maintenance/due-check")
    public String generateDueTasks(RedirectAttributes redirectAttributes) {
        try {
            int generated = maintenanceService.generateDueTasks();
            redirectAttributes.addFlashAttribute("successMessage", generated + " due maintenance task(s) generated.");
        } catch (Exception ex) {
            log.warn("Failed to generate due tasks: {}", ex.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", sanitize(ex.getMessage()));
        }
        return "redirect:/maintenance";
    }

    private String sanitize(String msg) {
        if (msg == null) {
            return "An unexpected error occurred. Please try again.";
        }
        return msg.length() > 240 ? msg.substring(0, 240) : msg;
    }

    @GetMapping("/maintenance/{assetId}")
    public String assetMaintenanceHistory(@PathVariable Long assetId, Model model) {
        Asset asset = assetService.findById(assetId);
        model.addAttribute("asset", asset);
        model.addAttribute("records", maintenanceService.historyForAsset(assetId));
        return "assets/maintenance-history";
    }
}
