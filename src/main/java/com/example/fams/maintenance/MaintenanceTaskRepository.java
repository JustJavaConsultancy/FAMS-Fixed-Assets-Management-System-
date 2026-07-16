package com.example.fams.maintenance;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface MaintenanceTaskRepository extends JpaRepository<MaintenanceTask, Long> {

    boolean existsByScheduleAndDueDate(MaintenanceSchedule schedule, LocalDate dueDate);

    @EntityGraph(attributePaths = {"asset", "schedule"})
    List<MaintenanceTask> findTop8ByOrderByDueDateDescCreatedAtDesc();

    @EntityGraph(attributePaths = {"asset", "schedule"})
    List<MaintenanceTask> findTop5ByOrderByDueDateAscCreatedAtDesc();

    @EntityGraph(attributePaths = {"asset", "schedule"})
    List<MaintenanceTask> findTop5ByStatusNotOrderByDueDateAscCreatedAtDesc(MaintenanceStatus status);

    @EntityGraph(attributePaths = {"asset", "schedule"})
    List<MaintenanceTask> findTop8ByStatusOrderByDueDateDescCreatedAtDesc(MaintenanceStatus status);

    long countByStatus(MaintenanceStatus status);

    long countByAsset_DepartmentIgnoreCaseAndStatus(String department, MaintenanceStatus status);
}
