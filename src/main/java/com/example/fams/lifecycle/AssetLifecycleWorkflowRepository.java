package com.example.fams.lifecycle;

import com.example.fams.assets.Asset;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AssetLifecycleWorkflowRepository extends JpaRepository<AssetLifecycleWorkflow, Long> {

    @EntityGraph(attributePaths = "asset")
    List<AssetLifecycleWorkflow> findByAssetOrderByRequestedAtDesc(Asset asset);

    @EntityGraph(attributePaths = "asset")
    List<AssetLifecycleWorkflow> findByAssetInOrderByRequestedAtDesc(List<Asset> assets);

    @Query("select w from AssetLifecycleWorkflow w join fetch w.asset a where lower(a.department) in :departments order by w.requestedAt desc")
    List<AssetLifecycleWorkflow> findByAssetDepartmentInIgnoreCase(@Param("departments") List<String> departments);

    @Query("""
            select w from AssetLifecycleWorkflow w join fetch w.asset a
            where w.type = com.example.fams.lifecycle.LifecycleWorkflowType.TRANSFER
              and w.status = com.example.fams.lifecycle.LifecycleWorkflowStatus.PENDING_APPROVAL
              and lower(w.fromDepartment) in :departments
            order by w.requestedAt desc
            """)
    List<AssetLifecycleWorkflow> findPendingTransfersFromDepartments(@Param("departments") List<String> departments);

    @EntityGraph(attributePaths = "asset")
    List<AssetLifecycleWorkflow> findAllByOrderByRequestedAtDesc();

    long countByAssetInAndStatus(List<Asset> assets, LifecycleWorkflowStatus status);

    Optional<AssetLifecycleWorkflow> findByProcessInstanceId(String processInstanceId);
}
