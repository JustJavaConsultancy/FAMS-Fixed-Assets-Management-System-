package com.example.fams.lifecycle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "asset_lifecycle_approval_actions")
public class AssetLifecycleApprovalAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_id", nullable = false)
    private AssetLifecycleWorkflow workflow;

    @Column(nullable = false, length = 80)
    private String flowableTaskId;

    @Column(nullable = false, length = 120)
    private String taskName;

    @Column(nullable = false, length = 160)
    private String actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApprovalDecision decision;

    @Column(columnDefinition = "text")
    private String comment;

    @Column(nullable = false)
    private LocalDateTime actionAt;

    @PrePersist
    void beforeCreate() {
        actionAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public AssetLifecycleWorkflow getWorkflow() {
        return workflow;
    }

    public void setWorkflow(AssetLifecycleWorkflow workflow) {
        this.workflow = workflow;
    }

    public String getFlowableTaskId() {
        return flowableTaskId;
    }

    public void setFlowableTaskId(String flowableTaskId) {
        this.flowableTaskId = flowableTaskId;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public ApprovalDecision getDecision() {
        return decision;
    }

    public void setDecision(ApprovalDecision decision) {
        this.decision = decision;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public LocalDateTime getActionAt() {
        return actionAt;
    }
}
