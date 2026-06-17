package com.example.fams.lifecycle;

import com.example.fams.assets.Asset;
import com.example.fams.assets.AssetRepository;
import com.example.fams.maintenance.MaintenanceRecord;
import com.example.fams.maintenance.MaintenanceRecordRepository;
import com.example.fams.settings.AdminSettingsService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
public class AssetLifecycleService {

    private static final String PROCESS_KEY = "assetLifecycleWorkflow";

    private final AssetRepository assetRepository;
    private final AssetLifecycleWorkflowRepository workflowRepository;
    private final AssetLifecycleApprovalActionRepository approvalActionRepository;
    private final AssetLifecycleHistoryRepository historyRepository;
    private final MaintenanceRecordRepository maintenanceRecordRepository;
    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final AdminSettingsService adminSettingsService;

    public AssetLifecycleService(AssetRepository assetRepository,
                                 AssetLifecycleWorkflowRepository workflowRepository,
                                 AssetLifecycleApprovalActionRepository approvalActionRepository,
                                 AssetLifecycleHistoryRepository historyRepository,
                                 MaintenanceRecordRepository maintenanceRecordRepository,
                                 RuntimeService runtimeService,
                                 TaskService taskService,
                                 AdminSettingsService adminSettingsService) {
        this.assetRepository = assetRepository;
        this.workflowRepository = workflowRepository;
        this.approvalActionRepository = approvalActionRepository;
        this.historyRepository = historyRepository;
        this.maintenanceRecordRepository = maintenanceRecordRepository;
        this.runtimeService = runtimeService;
        this.taskService = taskService;
        this.adminSettingsService = adminSettingsService;
    }

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
        Task task = taskService.createTaskQuery()
                .taskId(taskId)
                .processInstanceId(workflow.getProcessInstanceId())
                .singleResult();
        if (task == null) {
            throw new IllegalArgumentException("The approval task is no longer available.");
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

        boolean processEnded = runtimeService.createProcessInstanceQuery()
                .processInstanceId(workflow.getProcessInstanceId())
                .singleResult() == null;
        if (processEnded) {
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
        if (form.getRequestedEffectiveDate() == null || form.getRequestedEffectiveDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Requested effective date cannot be in the past.");
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
