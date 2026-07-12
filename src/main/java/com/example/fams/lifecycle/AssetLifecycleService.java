package com.example.fams.lifecycle;

import com.example.fams.assets.Asset;
import com.example.fams.assets.AssetRepository;
import com.example.fams.core.config.AuthenticationManager;
import com.example.fams.maintenance.MaintenanceRecord;
import com.example.fams.maintenance.MaintenanceRecordRepository;
import com.example.fams.organization.DepartmentHead;
import com.example.fams.organization.DepartmentHeadRepository;
import com.example.fams.settings.AdminSettingsService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

@Service
public class AssetLifecycleService {

    private static final String PROCESS_KEY = "assetLifecycleWorkflow";

    private final AssetRepository assetRepository;
    private final AssetLifecycleWorkflowRepository workflowRepository;
    private final AssetLifecycleApprovalActionRepository approvalActionRepository;
    private final AssetLifecycleHistoryRepository historyRepository;
    private final MaintenanceRecordRepository maintenanceRecordRepository;
    private final DepartmentHeadRepository departmentHeadRepository;
    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final AdminSettingsService adminSettingsService;
    private final AuthenticationManager authenticationManager;

    public AssetLifecycleService(AssetRepository assetRepository,
                                 AssetLifecycleWorkflowRepository workflowRepository,
                                 AssetLifecycleApprovalActionRepository approvalActionRepository,
                                 AssetLifecycleHistoryRepository historyRepository,
                                 MaintenanceRecordRepository maintenanceRecordRepository,
                                 DepartmentHeadRepository departmentHeadRepository,
                                 RuntimeService runtimeService,
                                 TaskService taskService,
                                 AdminSettingsService adminSettingsService,
                                 AuthenticationManager authenticationManager) {
        this.assetRepository = assetRepository;
        this.workflowRepository = workflowRepository;
        this.approvalActionRepository = approvalActionRepository;
        this.historyRepository = historyRepository;
        this.maintenanceRecordRepository = maintenanceRecordRepository;
        this.departmentHeadRepository = departmentHeadRepository;
        this.runtimeService = runtimeService;
        this.taskService = taskService;
        this.adminSettingsService = adminSettingsService;
        this.authenticationManager = authenticationManager;
    }

    private static final Logger log = LoggerFactory.getLogger(AssetLifecycleService.class);

    @Transactional
    public AssetLifecycleWorkflow submit(LifecycleWorkflowForm form) {
        Asset asset = assetRepository.findById(form.getAssetId())
                .orElseThrow(() -> new NoSuchElementException("Asset not found."));
        validate(form, asset);

        AssetLifecycleWorkflow workflow = new AssetLifecycleWorkflow();
        workflow.setAsset(asset);
        workflow.setType(form.getType());
        workflow.setStatus(LifecycleWorkflowStatus.PENDING_APPROVAL);
        workflow.setRequestedBy(currentActor());
        workflow.setRequestedEffectiveDate(form.getRequestedEffectiveDate());
        workflow.setFromEmployee(asset.getCustodian());
        workflow.setToEmployee(clean(form.getToEmployee()));
        workflow.setFromDepartment(asset.getDepartment());
        workflow.setToDepartment(clean(form.getToDepartment()));
        workflow.setFromBranch(asset.getBranch());
        workflow.setToBranch(clean(form.getToBranch()));
        workflow.setFromLocation(asset.getBranch());
        workflow.setToLocation(clean(form.getToLocation()));
        workflow.setDisposalMethod(clean(form.getDisposalMethod()));
        workflow.setDisposalProceeds(defaultMoney(form.getDisposalProceeds()));
        workflow.setAccumulatedDepreciation(defaultMoney(form.getAccumulatedDepreciation()));
        workflow.setNetBookValue(defaultMoney(form.getNetBookValue()));
        workflow.setFinancialImpact(defaultMoney(form.getDisposalProceeds()).subtract(defaultMoney(form.getNetBookValue())));
        workflow.setReason(clean(form.getReason()));
        workflow = workflowRepository.save(workflow);

        Map<String, Object> variables = new HashMap<>();
        variables.put("workflowId", workflow.getId());
        variables.put("workflowType", workflow.getType().name());
        variables.put("requester", workflow.getRequestedBy());
        variables.put("firstApprovalGroup", adminSettingsService.getParameterValue("asset.lifecycle.approval.firstGroup", "departmentHead"));
        variables.put("finalApprovalGroup", adminSettingsService.getParameterValue("asset.lifecycle.approval.finalGroup", "admin"));
        ProcessInstance instance = runtimeService.startProcessInstanceByKey(PROCESS_KEY, "asset-lifecycle-" + workflow.getId(), variables);
        workflow.setProcessInstanceId(instance.getProcessInstanceId());

        addHistory(asset, eventFor(workflow.getType()), workflow.getRequestedBy(),
                workflow.getType().name().replace('_', ' ') + " request submitted",
                describeSubmission(workflow), null, targetSummary(workflow));
        return workflowRepository.save(workflow);
    }

    @Transactional(readOnly = true)
    public List<AssetLifecycleWorkflow> findAllWorkflows() {
        return workflowRepository.findAllByOrderByRequestedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<AssetLifecycleWorkflow> findWorkflowsForAsset(Asset asset) {
        return workflowRepository.findByAssetOrderByRequestedAtDesc(asset);
    }

    @Transactional(readOnly = true)
    public List<AssetLifecycleApprovalAction> findApprovals(AssetLifecycleWorkflow workflow) {
        return approvalActionRepository.findByWorkflowOrderByActionAtAsc(workflow);
    }

    @Transactional(readOnly = true)
    public List<Task> pendingTasks(Long workflowId) {
        AssetLifecycleWorkflow workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new NoSuchElementException("Workflow not found."));
        if (workflow.getProcessInstanceId() == null) {
            return List.of();
        }
        return taskService.createTaskQuery()
                .processInstanceId(workflow.getProcessInstanceId())
                .active()
                .orderByTaskCreateTime()
                .asc()
                .list();
    }

    @Transactional
    public AssetLifecycleWorkflow decide(Long workflowId, String taskId, ApprovalDecision decision, String comment) {
        AssetLifecycleWorkflow workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new NoSuchElementException("Workflow not found."));
        log.debug("Decide called for workflow id={}, taskId={}, decision={}, comment={}", workflowId, taskId, decision, comment);
        log.info("Approval action by user='{}'. Workflow fromDept='{}', type='{}'", currentActor(), workflow.getFromDepartment(), workflow.getType());
        Task task = taskService.createTaskQuery()
                .taskId(taskId)
                .processInstanceId(workflow.getProcessInstanceId())
                .singleResult();
        log.debug("Found task: {} for processInstanceId={}. Task info={}", task == null ? null : task.getId(), workflow.getProcessInstanceId(), task == null ? null : task.toString());
        if (task == null) {
            throw new IllegalArgumentException("The approval task is no longer available.");
        }

        // Enforce role-based checks according to the task being completed.
        // Department-level approvals on a TRANSFER workflow must be performed by a department head for the asset's current department.
        // Disposals, returns, and assignments do not move assets between departments, so the department-head restriction does not apply.
        String taskName = task.getName() == null ? "" : task.getName();
        if (taskName.toLowerCase().contains("department")
                && workflow.getType() == LifecycleWorkflowType.TRANSFER) {
            if (!authenticationManager.isDepartmentHead() || !currentUserHeadsDepartment(workflow.getFromDepartment())) {
                throw new IllegalArgumentException("Only the department head of the asset's current department can approve this transfer.");
            }
        }

        AssetLifecycleApprovalAction action = new AssetLifecycleApprovalAction();
        action.setWorkflow(workflow);
        action.setFlowableTaskId(task.getId());
        action.setTaskName(task.getName());
        action.setActor(currentActor());
        action.setDecision(decision);
        action.setComment(clean(comment));
        approvalActionRepository.save(action);

        boolean approved = decision == ApprovalDecision.APPROVED;
        taskService.complete(taskId, Map.of("approved", approved));

        if (!approved) {
            workflow.setStatus(LifecycleWorkflowStatus.REJECTED);
            addHistory(workflow.getAsset(), LifecycleEventType.REJECTION, action.getActor(),
                    workflow.getType() + " rejected", defaultText(comment, task.getName()), null, null);
            return workflowRepository.save(workflow);
        }

        addHistory(workflow.getAsset(), LifecycleEventType.APPROVAL, action.getActor(),
                task.getName() + " approved", defaultText(comment, "Approval recorded."), null, null);
        // After completing the Flowable task, determine if the process instance has ended.
        // In some Flowable configurations the process may continue asynchronously and the
        // runtimeService query may still return a live instance for a short period. As a
        // pragmatic approach, treat the workflow as complete when either the process
        // instance no longer exists OR there are no more active user tasks for the
        // process instance (meaning no further approvals are pending).
        boolean processEnded = runtimeService.createProcessInstanceQuery()
                .processInstanceId(workflow.getProcessInstanceId())
                .singleResult() == null;

        long remainingUserTasks = taskService.createTaskQuery()
                .processInstanceId(workflow.getProcessInstanceId())
                .active()
                .count();
        log.debug("processEnded={}, remainingUserTasks={} for workflowId={}", processEnded, remainingUserTasks, workflowId);

        if (processEnded || remainingUserTasks == 0) {
            // No further user tasks — treat as completed and apply final changes
            log.info("Completing workflow id={} after approval by {}", workflowId, action.getActor());
            completeWorkflow(workflow, action.getActor());
        } else {
            workflow.setStatus(LifecycleWorkflowStatus.PENDING_APPROVAL);
        }
        return workflowRepository.save(workflow);
    }

    @Transactional
    public void recordRegistration(Asset asset, String actor) {
        addHistory(asset, LifecycleEventType.REGISTRATION, actor,
                "Asset registered", "Asset was added to the fixed asset register.", null, asset.getStatus());
    }

    /**
     * Record a bulk assignment action in the lifecycle history for each affected asset.
     */
    @Transactional
    public void recordBulkAssignment(java.util.List<com.example.fams.assets.Asset> assets, String actor, String details) {
        for (com.example.fams.assets.Asset asset : assets) {
            addHistory(asset, LifecycleEventType.ASSIGNMENT, actor,
                    "Bulk assignment", details, null, asset.getDepartment());
        }
    }

    /**
     * Record a bulk transfer action in the lifecycle history for each affected asset.
     */
    @Transactional
    public void recordBulkTransfer(java.util.List<com.example.fams.assets.Asset> assets, String actor, String details) {
        for (com.example.fams.assets.Asset asset : assets) {
            addHistory(asset, LifecycleEventType.TRANSFER, actor,
                    "Bulk transfer", details, null, asset.getBranch());
        }
    }

    /**
     * Record a bulk disposal/retirement action in the lifecycle history for each affected asset.
     */
    @Transactional
    public void recordBulkDisposal(java.util.List<com.example.fams.assets.Asset> assets, String actor, String details) {
        for (com.example.fams.assets.Asset asset : assets) {
            addHistory(asset, LifecycleEventType.DISPOSAL, actor,
                    "Bulk disposal", details, null, "Disposed");
        }
    }

    @Transactional(readOnly = true)
    public List<LifecycleTimelineItem> timeline(Asset asset) {
        List<LifecycleTimelineItem> items = new ArrayList<>();
        for (AssetLifecycleHistory history : historyRepository.findByAssetOrderByEventAtDesc(asset)) {
            items.add(new LifecycleTimelineItem(history.getEventType(), history.getTitle(), history.getDetails(), history.getActor(), history.getEventAt()));
        }
        for (MaintenanceRecord record : maintenanceRecordRepository.findByAssetOrderByMaintenanceDateDescCreatedAtDesc(asset)) {
            items.add(new LifecycleTimelineItem(
                    LifecycleEventType.MAINTENANCE,
                    "Maintenance " + record.getStatus().name().toLowerCase().replace('_', ' '),
                    record.getIssueDescription(),
                    defaultText(record.getServiceProvider(), "Maintenance team"),
                    record.getCreatedAt()));
        }
        items.sort((left, right) -> right.eventAt().compareTo(left.eventAt()));
        return items;
    }

    private void completeWorkflow(AssetLifecycleWorkflow workflow, String actor) {
        Asset asset = workflow.getAsset();
        switch (workflow.getType()) {
            case ASSIGNMENT -> {
                asset.setCustodian(workflow.getToEmployee());
                if (hasText(workflow.getToDepartment())) {
                    asset.setDepartment(workflow.getToDepartment());
                }
                if (hasText(workflow.getToBranch())) {
                    asset.setBranch(workflow.getToBranch());
                }
                asset.setStatus("Assigned");
            }
            case TRANSFER -> {
                if (hasText(workflow.getToEmployee())) {
                    asset.setCustodian(workflow.getToEmployee());
                }
                if (hasText(workflow.getToDepartment())) {
                    asset.setDepartment(workflow.getToDepartment());
                }
                if (hasText(workflow.getToBranch())) {
                    asset.setBranch(workflow.getToBranch());
                }
                asset.setStatus("Assigned");
            }
            case RETURN -> asset.setStatus("In Stock");
            case DISPOSAL -> asset.setStatus("Disposed");
        }
        assetRepository.save(asset);
        workflow.setStatus(LifecycleWorkflowStatus.COMPLETED);
        addHistory(asset, eventFor(workflow.getType()), actor,
                workflow.getType().name().replace('_', ' ') + " completed",
                describeCompletion(workflow), workflow.getFromEmployee(), targetSummary(workflow));
    }

    private void validate(LifecycleWorkflowForm form, Asset asset) {
        if ("Disposed".equalsIgnoreCase(asset.getStatus())) {
            throw new IllegalArgumentException("Disposed assets cannot start a new lifecycle workflow.");
        }
        if (form.getType() == null) {
            throw new IllegalArgumentException("Workflow type is required.");
        }
        if (form.getRequestedEffectiveDate() == null || form.getRequestedEffectiveDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Requested effective date cannot be in the past.");
        }
        if (form.getType() == LifecycleWorkflowType.TRANSFER && !currentUserCanRequestTransfer(asset)) {
            throw new IllegalArgumentException("Only an asset manager or the employee assigned to this asset can request a transfer.");
        }
        if (form.getType() == LifecycleWorkflowType.DISPOSAL) {
            if (!hasText(form.getDisposalMethod())) {
                throw new IllegalArgumentException("Disposal method is required.");
            }
            return;
        }
        if (!hasText(form.getToEmployee()) && !hasText(form.getToDepartment()) && !hasText(form.getToBranch()) && !hasText(form.getToLocation())) {
            throw new IllegalArgumentException("Provide at least one destination employee, department, branch or location.");
        }
    }

    private void addHistory(Asset asset, LifecycleEventType eventType, String actor, String title, String details, String from, String to) {
        AssetLifecycleHistory history = new AssetLifecycleHistory();
        history.setAsset(asset);
        history.setEventType(eventType);
        history.setActor(defaultText(actor, "System"));
        history.setTitle(title);
        history.setDetails(details);
        history.setFromValue(from);
        history.setToValue(to);
        historyRepository.save(history);
    }

    private String describeSubmission(AssetLifecycleWorkflow workflow) {
        return "Effective " + workflow.getRequestedEffectiveDate() + ". " + defaultText(workflow.getReason(), "No additional notes.");
    }

    private String describeCompletion(AssetLifecycleWorkflow workflow) {
        if (workflow.getType() == LifecycleWorkflowType.DISPOSAL) {
            return "Method: " + workflow.getDisposalMethod()
                    + ", proceeds: " + workflow.getDisposalProceeds()
                    + ", net book value: " + workflow.getNetBookValue()
                    + ", impact: " + workflow.getFinancialImpact() + ".";
        }
        return "Custodian/location updated after full approval.";
    }

    private String targetSummary(AssetLifecycleWorkflow workflow) {
        List<String> values = new ArrayList<>();
        if (hasText(workflow.getToEmployee())) values.add(workflow.getToEmployee());
        if (hasText(workflow.getToDepartment())) values.add(workflow.getToDepartment());
        if (hasText(workflow.getToBranch())) values.add(workflow.getToBranch());
        if (hasText(workflow.getToLocation())) values.add(workflow.getToLocation());
        if (hasText(workflow.getDisposalMethod())) values.add(workflow.getDisposalMethod());
        return String.join(" / ", values);
    }

    private LifecycleEventType eventFor(LifecycleWorkflowType type) {
        return switch (type) {
            case ASSIGNMENT -> LifecycleEventType.ASSIGNMENT;
            case TRANSFER -> LifecycleEventType.TRANSFER;
            case RETURN -> LifecycleEventType.RETURN;
            case DISPOSAL -> LifecycleEventType.DISPOSAL;
        };
    }

    private String currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return "System";
        }
        return authentication.getName();
    }

    private boolean currentUserCanRequestTransfer(Asset asset) {
        if (authenticationManager.isAssetManager()) {
            return true;
        }
        String custodian = clean(asset.getCustodian());
        return custodian != null && userLookupCandidates().stream()
                .anyMatch(candidate -> custodian.equalsIgnoreCase(candidate));
    }

    private boolean currentUserHeadsDepartment(String department) {
        String departmentKey = clean(department);
        if (departmentKey == null) {
            return false;
        }
        Set<Long> matchedHeadIds = new LinkedHashSet<>();
        for (String candidate : userLookupCandidates()) {
            for (DepartmentHead head : departmentHeadRepository.findByUserIdAndIsActiveTrueOrderByAssignedAtDesc(candidate)) {
                if (matchedHeadIds.add(head.getId()) && departmentMatches(head, departmentKey)) {
                    return true;
                }
            }
            for (DepartmentHead head : departmentHeadRepository.findByUserNameIgnoreCaseAndIsActiveTrueOrderByAssignedAtDesc(candidate)) {
                if (matchedHeadIds.add(head.getId()) && departmentMatches(head, departmentKey)) {
                    return true;
                }
            }
            for (DepartmentHead head : departmentHeadRepository.findByUserEmailIgnoreCaseAndIsActiveTrueOrderByAssignedAtDesc(candidate)) {
                if (matchedHeadIds.add(head.getId()) && departmentMatches(head, departmentKey)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean departmentMatches(DepartmentHead head, String departmentKey) {
        return head.getDepartment() != null
                && head.getDepartment().getName() != null
                && head.getDepartment().getName().trim().equalsIgnoreCase(departmentKey);
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

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String clean(String value) {
        return value == null ? null : value.trim();
    }

    private BigDecimal defaultMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String defaultText(String value, String fallback) {
        return hasText(value) ? value : fallback;
    }
}
