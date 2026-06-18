package com.example.fams.assets;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssetRepository extends JpaRepository<Asset, Long> {

    List<Asset> findAllByOrderByCreatedAtDesc();

    List<Asset> findByStatusNotIgnoreCaseOrderByCreatedAtDesc(String status);

    List<Asset> findByCustodianIgnoreCaseOrCustodianIgnoreCaseOrderByCreatedAtDesc(String username, String displayName);

    @Query("select a from Asset a where lower(a.custodian) in :candidates order by a.createdAt desc")
    List<Asset> findByCustodianInIgnoreCaseOrderByCreatedAtDesc(@Param("candidates") Collection<String> candidates);

    List<Asset> findByDepartmentIgnoreCaseOrderByCreatedAtDesc(String department);

    List<Asset> findByDepartmentInOrderByCreatedAtDesc(Collection<String> departments);

    long countByCategoryIgnoreCase(String category);

    Optional<Asset> findByAssetCodeIgnoreCaseOrBarcodeValueIgnoreCase(String assetCode, String barcodeValue);
}
