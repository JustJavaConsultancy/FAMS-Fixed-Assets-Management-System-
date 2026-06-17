package com.example.fams.lifecycle;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssetLifecycleApprovalActionRepository extends JpaRepository<AssetLifecycleApprovalAction, Long> {

    List<AssetLifecycleApprovalAction> findByWorkflowOrderByActionAtAsc(AssetLifecycleWorkflow workflow);
}
