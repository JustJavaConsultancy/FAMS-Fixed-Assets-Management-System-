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
        return taskRepository.findTop8ByOrderByDueDateDescCreatedAtDesc();
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
        MaintenanceSchedule schedule = new MaintenanceSchedule();
        if (assetId != null) {
            schedule.setAsset(assetService.findById(assetId));
        }
        schedule.setAssetCategory(clean(assetCategory));
        schedule.setServiceType(serviceType);
        schedule.setFrequency(frequency);
        schedule.setStartDate(startDate);
        schedule.setNextDueDate(startDate);
        schedule.setResponsibleParty(responsibleParty);
        schedule.setResponsibleRole(responsibleRole);
        return scheduleRepository.save(schedule);
    }

    @Transactional
    public MaintenanceRecord recordCorrective(Long assetId,
                                              String issueDescription,
                                              String serviceProvider,
                                              BigDecimal maintenanceCost,
                                              LocalDate resolutionDate) {
        MaintenanceRecord record = new MaintenanceRecord();
        record.setAsset(assetService.findById(assetId));
        record.setType(MaintenanceType.CORRECTIVE);
        record.setIssueDescription(issueDescription);
        record.setServiceProvider(serviceProvider);
        record.setMaintenanceCost(maintenanceCost);
        record.setMaintenanceDate(resolutionDate == null ? LocalDate.now() : resolutionDate);
        record.setResolutionDate(resolutionDate);
        record.setStatus(resolutionDate == null ? MaintenanceStatus.OPEN : MaintenanceStatus.COMPLETED);
        return recordRepository.save(record);
    }

    @Transactional
    public int generateDueTasks() {
        LocalDate today = LocalDate.now();
        int generated = 0;
        for (MaintenanceSchedule schedule : scheduleRepository.findByNextDueDateLessThanEqualOrderByNextDueDateAsc(today)) {
            LocalDate dueDate = schedule.getNextDueDate();
            if (!taskRepository.existsByScheduleAndDueDate(schedule, dueDate)) {
                MaintenanceTask task = createTask(schedule, dueDate);
                publishDueEvent(task);
                generated++;
            }
            schedule.setStatus(MaintenanceStatus.SCHEDULED);
            schedule.setNextDueDate(schedule.getFrequency().nextAfter(dueDate));
        }
        return generated;
    }

    @Scheduled(cron = "0 0 7 * * *")
    @Transactional
    public void generateDueTasksOnSchedule() {
        int generated = generateDueTasks();
        if (generated > 0) {
            log.info("Generated {} maintenance due task(s)", generated);
        }
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
        return taskRepository.save(task);
    }

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
            task.setEventPublished(true);
            task.setEventPublishedAt(LocalDateTime.now());
        } catch (AmqpException ex) {
            log.warn("Maintenance task {} was saved but RabbitMQ publish failed: {}", task.getId(), ex.getMessage());
        }
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
