package com.example.fams.assets;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssetRepository extends JpaRepository<Asset, Long> {

    List<Asset> findAllByOrderByCreatedAtDesc();

    List<Asset> findByStatusNotIgnoreCaseOrderByCreatedAtDesc(String status);

    long countByCategoryIgnoreCase(String category);

    Optional<Asset> findByAssetCodeIgnoreCaseOrBarcodeValueIgnoreCase(String assetCode, String barcodeValue);
}
