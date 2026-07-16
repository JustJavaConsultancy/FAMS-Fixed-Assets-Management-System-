package com.example.fams.maintenance;

import com.example.fams.assets.Asset;
import com.example.fams.assets.AssetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class MaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceService.class);

    /**
     * Upper bound on how many missed intervals a single schedule will generate in one pass.
     * Guarantees the backlog catch-up loop always terminates even for schedules whose
     * nextDueDate is far in the past. 24 covers roughly WEEKLY maintenance for ~6 months.
     */
    private static final int MAX_BACKLOG = 24;

    /**
     * Reject start dates older than this to avoid spawning an enormous backlog of tasks.
     */
    private static final LocalDate MIN_START_DATE = LocalDate.now().minusYears(5);

    private final AssetService assetService;
    private final MaintenanceScheduleRepository scheduleRepository;
    private final MaintenanceRecordRepository recordRepository;
    private final MaintenanceTaskRepository taskRepository;
    private final RabbitTemplate rabbitTemplate;

    public MaintenanceService(AssetService assetService,
                              MaintenanceScheduleRepository scheduleRepository,
                              MaintenanceRecordRepository recordRepository,
                              MaintenanceTaskRepository taskRepository,
                              RabbitTemplate rabbitTemplate) {
        this.assetService = assetService;
        this.scheduleRepository = scheduleRepository;
        this.recordRepository = recordRepository;
        this.taskRepository = taskRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Transactional(readOnly = true)
    public List<MaintenanceSchedule> schedules() {
        return scheduleRepository.findAllByOrderByNextDueDateAsc();
    }

    @Transactional(readOnly = true)
    public List<MaintenanceRecord> historyForAsset(Long assetId) {
        Asset asset = assetService.findById(assetId);
        return recordRepository.findByAssetOrderByMaintenanceDateDescCreatedAtDesc(asset);
    }

    @Transactional(readOnly = true)
    public List<MaintenanceTask> recentTasks() {
        return taskRepository.findTop8ByStatusOrderByDueDateDescCreatedAtDesc(MaintenanceStatus.DUE);
    }

    @Transactional(readOnly = true)
    public List<MaintenanceTask> recentResolvedTasks() {
        return taskRepository.findTop8ByStatusOrderByDueDateDescCreatedAtDesc(MaintenanceStatus.COMPLETED);
    }

    @Transactional(readOnly = true)
    public List<MaintenanceRecord> recentCorrectiveRecords() {
        return recordRepository.findTop8ByTypeOrderByMaintenanceDateDescCreatedAtDesc(MaintenanceType.CORRECTIVE);
    }

    @Transactional(readOnly = true)
    public List<MaintenanceReportRow> report(LocalDate start, LocalDate end) {
        return recordRepository.findByMaintenanceDateBetweenOrderByMaintenanceDateDesc(start, end)
                .stream()
                .map(record -> new MaintenanceReportRow(
                        record.getAsset().getId(),
                        record.getAsset().getAssetCode(),
                        record.getAsset().getName(),
                        record.getAsset().getCategory(),
                        record.getType(),
                        record.getServiceProvider(),
                        record.getMaintenanceDate(),
                        record.getResolutionDate(),
                        record.getMaintenanceCost(),
                        record.getStatus()))
                .toList();
    }

    @Transactional(readOnly = true)
    public BigDecimal reportTotal(LocalDate start, LocalDate end) {
        return recordRepository.findByMaintenanceDateBetweenOrderByMaintenanceDateDesc(start, end)
                .stream()
                .map(MaintenanceRecord::getMaintenanceCost)
                .filter(cost -> cost != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional(readOnly = true)
    public long correctiveCount() {
        return recordRepository.countByType(MaintenanceType.CORRECTIVE);
    }

    @Transactional(readOnly = true)
    public long dueTaskCount() {
        return taskRepository.countByStatus(MaintenanceStatus.DUE);
    }

    @Transactional
    public MaintenanceSchedule createSchedule(Long assetId,
                                              String assetCategory,
                                              String serviceType,
                                              MaintenanceFrequency frequency,
                                              LocalDate startDate,
                                              String responsibleParty,
                                              String responsibleRole) {
        if (frequency == null) {
            throw new IllegalArgumentException("Maintenance frequency is required.");
        }
        if (startDate == null) {
            throw new IllegalArgumentException("Maintenance start date is required.");
        }
        if (startDate.isBefore(MIN_START_DATE)) {
            throw new IllegalArgumentException(
                    "Start date is too far in the past (must be after " + MIN_START_DATE + ").");
        }
        if (assetId == null && (assetCategory == null || assetCategory.isBlank())) {
            throw new IllegalArgumentException("Either an asset or an asset category is required.");
        }
        if (serviceType == null || serviceType.isBlank()) {
            throw new IllegalArgumentException("Service type is required.");
        }
        if (responsibleParty == null || responsibleParty.isBlank()) {
            throw new IllegalArgumentException("Responsible party is required.");
        }
        if (responsibleRole == null || responsibleRole.isBlank()) {
            throw new IllegalArgumentException("Responsible role is required.");
        }

        MaintenanceSchedule schedule = new MaintenanceSchedule();
        if (assetId != null) {
            schedule.setAsset(assetService.findById(assetId));
        }
        schedule.setAssetCategory(clean(assetCategory));
        schedule.setServiceType(serviceType.trim());
        schedule.setFrequency(frequency);
        schedule.setStartDate(startDate);
        schedule.setNextDueDate(startDate);
        schedule.setResponsibleParty(responsibleParty.trim());
        schedule.setResponsibleRole(responsibleRole.trim());
        return scheduleRepository.save(schedule);
    }

    @Transactional
    public MaintenanceRecord recordCorrective(Long assetId,
                                              String issueDescription,
                                              String serviceProvider,
                                              BigDecimal maintenanceCost,
                                              LocalDate resolutionDate) {
        return recordCorrective(assetId, issueDescription, serviceProvider, maintenanceCost, resolutionDate, null);
    }

    @Transactional
    public MaintenanceRecord recordCorrective(Long assetId,
                                              String issueDescription,
                                              String serviceProvider,
                                              BigDecimal maintenanceCost,
                                              LocalDate resolutionDate,
                                              String requestedBy) {
        MaintenanceRecord record = new MaintenanceRecord();
        record.setAsset(assetService.findById(assetId));
        record.setType(MaintenanceType.CORRECTIVE);
        record.setIssueDescription(issueDescription);
        record.setServiceProvider(clean(serviceProvider));
        record.setRequestedBy(hasText(requestedBy) ? requestedBy.trim() : "Asset Manager");
        record.setMaintenanceCost(maintenanceCost);
        record.setMaintenanceDate(resolutionDate == null ? LocalDate.now() : resolutionDate);
        record.setResolutionDate(resolutionDate);
        record.setStatus(resolutionDate == null ? MaintenanceStatus.OPEN : MaintenanceStatus.COMPLETED);
        return recordRepository.save(record);
    }

    /**
     * Generates due tasks for every schedule whose nextDueDate is at or before today.
     * Catches up the full backlog of missed intervals (capped per schedule) so a schedule
     * that is weeks behind produces every missed task rather than just one.
     *
     * Each schedule is processed in its own try/catch so a single bad row cannot abort the
     * whole batch or roll back tasks already created for other schedules. Event publishing
     * to RabbitMQ is best-effort and fully decoupled from task persistence.
     *
     * @return total number of due tasks generated across all schedules
     */
    @Transactional
    public int generateDueTasks() {
        LocalDate today = LocalDate.now();
        int generated = 0;
        for (MaintenanceSchedule schedule : scheduleRepository.findByNextDueDateLessThanEqualOrderByNextDueDateAsc(today)) {
            try {
                generated += generateDueTasksForSchedule(schedule, today);
            } catch (Exception ex) {
                log.error("Failed to generate due tasks for schedule {}: {}", schedule.getId(), ex.getMessage(), ex);
            }
        }
        return generated;
    }

    private int generateDueTasksForSchedule(MaintenanceSchedule schedule, LocalDate today) {
        MaintenanceFrequency frequency = schedule.getFrequency();
        if (frequency == null) {
            log.warn("Schedule {} has no frequency; skipping due-task generation", schedule.getId());
            return 0;
        }
        LocalDate nextDue = schedule.getNextDueDate();
        if (nextDue == null) {
            log.warn("Schedule {} has null nextDueDate; skipping due-task generation", schedule.getId());
            return 0;
        }

        int generated = 0;
        int intervals = 0;
        while (!nextDue.isAfter(today)) {
            if (!taskRepository.existsByScheduleAndDueDate(schedule, nextDue)) {
                MaintenanceTask task = createTask(schedule, nextDue);
                generated++;
                publishDueEvent(task); // best-effort, never blocks persistence
            }
            nextDue = frequency.nextAfter(nextDue);
            if (++intervals >= MAX_BACKLOG) {
                log.warn("Schedule {} hit the backlog cap ({}); advancing nextDueDate to {} and stopping catch-up.",
                        schedule.getId(), MAX_BACKLOG, nextDue);
                break;
            }
        }

        schedule.setNextDueDate(nextDue);
        schedule.setStatus(generated > 0 ? MaintenanceStatus.DUE : MaintenanceStatus.SCHEDULED);
        scheduleRepository.save(schedule);
        return generated;
    }

    @Scheduled(cron = "0 0 7 * * *")
    public void generateDueTasksOnSchedule() {
        int generated = generateDueTasks();
        if (generated > 0) {
            log.info("Generated {} maintenance due task(s)", generated);
        }
    }

    /**
     * Resolves a DUE preventive maintenance task: records the completed work as a PREVENTIVE
     * MaintenanceRecord (linked to the schedule and asset for full traceability) and marks the
     * task COMPLETED. Idempotent-safe: a task that is already resolved cannot be resolved again.
     *
     * @throws IllegalStateException if the task does not exist or is not in DUE status
     */
    @Transactional
    public void resolveTask(Long taskId,
                            String serviceProvider,
                            BigDecimal maintenanceCost,
                            LocalDate resolutionDate,
                            String notes) {
        MaintenanceTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalStateException("Maintenance task not found: " + taskId));
        if (task.getStatus() != MaintenanceStatus.DUE) {
            throw new IllegalStateException(
                    "Only DUE tasks can be resolved (task " + taskId + " is " + task.getStatus() + ").");
        }

        MaintenanceSchedule schedule = task.getSchedule();
        LocalDate resolvedOn = resolutionDate == null ? LocalDate.now() : resolutionDate;

        MaintenanceRecord record = new MaintenanceRecord();
        record.setAsset(task.getAsset());
        record.setSchedule(schedule);
        record.setType(MaintenanceType.PREVENTIVE);
        record.setIssueDescription(clean(notes) == null ? "Preventive maintenance completed" : clean(notes));
        record.setServiceProvider(clean(serviceProvider));
        record.setRequestedBy(schedule == null ? "System" : schedule.getResponsibleParty());
        record.setMaintenanceCost(maintenanceCost);
        record.setMaintenanceDate(resolvedOn);
        record.setResolutionDate(resolutionDate);
        record.setStatus(MaintenanceStatus.COMPLETED);
        recordRepository.save(record);

        task.setStatus(MaintenanceStatus.COMPLETED);
        task.setResolutionCost(maintenanceCost);
        task.setResolutionDate(resolutionDate);
        taskRepository.save(task);

        log.info("Resolved maintenance task {} as PREVENTIVE record (schedule {})",
                taskId, schedule == null ? "n/a" : schedule.getId());
    }

    private MaintenanceTask createTask(MaintenanceSchedule schedule, LocalDate dueDate) {
        MaintenanceTask task = new MaintenanceTask();
        task.setSchedule(schedule);
        task.setAsset(schedule.getAsset());
        task.setAssetCategory(schedule.getAssetCategory());
        task.setServiceType(schedule.getServiceType());
        task.setDueDate(dueDate);
        task.setResponsibleParty(schedule.getResponsibleParty());
        task.setResponsibleRole(schedule.getResponsibleRole());
        task.setStatus(MaintenanceStatus.DUE);
        task.setEventPublished(false);
        return taskRepository.save(task);
    }

    /**
     * Best-effort publish of a due-task notification. Must never throw: a missing or
     * unavailable RabbitMQ broker must not prevent the task from being created or persisted.
     * The eventPublished flag is updated separately and best-effort only.
     */
    private void publishDueEvent(MaintenanceTask task) {
        try {
            Asset asset = task.getAsset();
            MaintenanceDueEvent event = new MaintenanceDueEvent(
                    task.getId(),
                    task.getSchedule().getId(),
                    asset == null ? null : asset.getId(),
                    asset == null ? null : asset.getAssetCode(),
                    asset == null ? null : asset.getName(),
                    task.getAssetCategory(),
                    task.getServiceType(),
                    task.getDueDate(),
                    task.getResponsibleParty(),
                    task.getResponsibleRole());
            rabbitTemplate.convertAndSend(
                    MaintenanceMessagingConfig.EXCHANGE,
                    MaintenanceMessagingConfig.DUE_ROUTING_KEY,
                    event);
            markEventPublished(task.getId());
        } catch (Exception ex) {
            // Messaging is optional; the due task already exists and is what the user sees.
            log.warn("Maintenance due event for task {} was not published (broker unavailable?): {}",
                    task.getId(), ex.getMessage());
        }
    }

    @Transactional
    public void markEventPublished(Long taskId) {
        taskRepository.findById(taskId).ifPresent(task -> {
            task.setEventPublished(true);
            task.setEventPublishedAt(LocalDateTime.now());
            taskRepository.save(task);
        });
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
