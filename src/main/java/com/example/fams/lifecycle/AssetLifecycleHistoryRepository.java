package com.example.fams.lifecycle;

import com.example.fams.assets.Asset;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssetLifecycleHistoryRepository extends JpaRepository<AssetLifecycleHistory, Long> {

    @EntityGraph(attributePaths = "asset")
    List<AssetLifecycleHistory> findByAssetOrderByEventAtDesc(Asset asset);
}
