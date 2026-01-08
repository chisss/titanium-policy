package com.titanium.policy.infrastructure.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "t_policy_document")
@Getter
@Setter
public class PolicyDocumentEntity {
    @Id
    @Column(name = "id", nullable = false, length = 32)
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 32)
    private String tenantId;

    @Column(name = "policy_id", nullable = false, length = 32)
    private String policyId;

    @Column(name = "document_type", nullable = false, length = 32)
    private String documentType;

    @Column(name = "document_name", nullable = false, length = 128)
    private String documentName;

    @Lob
    @Column(name = "document_content", columnDefinition = "TEXT")
    private String documentContent;

    @Column(name = "file_path", length = 256)
    private String filePath;

    @Column(name = "create_time", nullable = false, columnDefinition = "DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createTime;

    @Column(name = "update_time", nullable = false, columnDefinition = "DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP")
    private LocalDateTime updateTime;

    @Column(name = "created_by", nullable = false, length = 32)
    private String createdBy;

    @Column(name = "updated_by", nullable = false, length = 32)
    private String updatedBy;

    @Column(name = "is_deleted", nullable = false, columnDefinition = "TINYINT NOT NULL DEFAULT 0")
    private Integer isDeleted;
}