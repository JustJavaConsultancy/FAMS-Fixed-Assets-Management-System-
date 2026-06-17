package com.example.fams.assets;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssetRepository extends JpaRepository<Asset, Long> {

    List<Asset> findAllByOrderByCreatedAtDesc();

    List<Asset> findByStatusNotIgnoreCaseOrderByCreatedAtDesc(String status);

    long countByCategoryIgnoreCase(String category);
}
