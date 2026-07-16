package com.example.fams.depreciation;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DepreciationPostingRepository extends JpaRepository<DepreciationPosting, Long> {

    // Find all postings for a specific asset
    List<DepreciationPosting> findByAssetIdOrderByDepreciationPeriodDesc(Long assetId);

    // Find postings for a specific period
    List<DepreciationPosting> findByDepreciationPeriodOrderByAssetCode(String depreciationPeriod);

    // Find postings for a category
    List<DepreciationPosting> findByCategoryOrderByDepreciationPeriodDescAssetCode(String category);

    // Find postings for a department
    List<DepreciationPosting> findByDepartmentOrderByDepreciationPeriodDescAssetCode(String department);

    // Find paginated postings for a period
    Page<DepreciationPosting> findByDepreciationPeriod(String depreciationPeriod, Pageable pageable);

    // Find latest posting for an asset
    DepreciationPosting findFirstByAssetIdOrderByDepreciationPeriodDesc(Long assetId);

    // Find postings for a specific asset + period (used to replace on re-run)
    List<DepreciationPosting> findByAssetIdAndDepreciationPeriod(Long assetId, String depreciationPeriod);

    // Find postings by fiscal year
    List<DepreciationPosting> findByFiscalYearOrderByDepreciationPeriodDescAssetCode(Integer fiscalYear);
}

