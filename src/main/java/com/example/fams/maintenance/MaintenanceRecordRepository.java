package com.example.fams.maintenance;

import com.example.fams.assets.Asset;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface MaintenanceRecordRepository extends JpaRepository<MaintenanceRecord, Long> {

    @EntityGraph(attributePaths = "asset")
    List<MaintenanceRecord> findByAssetOrderByMaintenanceDateDescCreatedAtDesc(Asset asset);

    @EntityGraph(attributePaths = "asset")
    List<MaintenanceRecord> findByAssetInOrderByMaintenanceDateDescCreatedAtDesc(Collection<Asset> assets);

    @EntityGraph(attributePaths = "asset")
    List<MaintenanceRecord> findByMaintenanceDateBetweenOrderByMaintenanceDateDesc(LocalDate start, LocalDate end);

    @EntityGraph(attributePaths = "asset")
    List<MaintenanceRecord> findTop8ByTypeOrderByMaintenanceDateDescCreatedAtDesc(MaintenanceType type);

    @EntityGraph(attributePaths = "asset")
    List<MaintenanceRecord> findTop8ByStatusOrderByMaintenanceDateDescCreatedAtDesc(MaintenanceStatus status);

    long countByType(MaintenanceType type);

    long countByStatus(MaintenanceStatus status);

    long countByAssetInAndStatus(Collection<Asset> assets, MaintenanceStatus status);
}
