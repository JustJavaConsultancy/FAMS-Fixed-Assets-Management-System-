package com.example.fams.maintenance;

import com.example.fams.assets.Asset;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface MaintenanceScheduleRepository extends JpaRepository<MaintenanceSchedule, Long> {

    @EntityGraph(attributePaths = "asset")
    List<MaintenanceSchedule> findAllByOrderByNextDueDateAsc();

    @EntityGraph(attributePaths = "asset")
    List<MaintenanceSchedule> findByAssetOrderByNextDueDateAsc(Asset asset);

    @EntityGraph(attributePaths = "asset")
    List<MaintenanceSchedule> findByNextDueDateLessThanEqualOrderByNextDueDateAsc(LocalDate date);
}
