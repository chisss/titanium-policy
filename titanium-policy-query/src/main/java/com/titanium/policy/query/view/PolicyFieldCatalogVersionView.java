package com.titanium.policy.query.view;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.titanium.common.jpa.BaseView;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 保单字段目录**版本头**（一行 = 一个已发布版本），映射 {@code t_policy_field_catalog_version}。
 *
 * <p>与 {@link PolicyFieldCatalogView} 的关系是 1:N：本表承载**版本级**事实
 * （内容哈希 / 生效区间 / 发布证据），行表承载该版本的字段清单。故版本头单独成表——
 * 摊到每行上会让「同一版本 N 行必须逐字相同」成为一条无人守护的不变量。</p>
 *
 * <p>与行表同属平台标准元数据表（非事件投影的产物，无 {@code XxxProjectionEventHandler}），
 * 内容由 Liquibase 种子灌入；发布新版本当前亦以 SQL 完成（本域无发布入口，见
 * {@code PolicyFieldCatalogPublication#publish} 的说明）。</p>
 *
 * <p>🔴 <b>生效区间是半开区间 {@code [effectiveFrom, effectiveTo)}</b>（{@code effectiveTo} 为 null
 * 表示开放区间）：相邻两版首尾相接而无重叠，业务时点的归属唯一。列语义与
 * {@code PolicyFieldCatalogPublication} 一一对应，且该 record 的紧凑构造器会重新校验
 * （哈希格式、区间方向），故本视图列被改坏时在读取路径即被拒。</p>
 *
 * <p>{@code tenantId} 由 {@link BaseView} 承载且不参与过滤（跨租户共享平台元数据，
 * {@code G07/AC-10}）。</p>
 */
@Entity
@Table(name = "t_policy_field_catalog_version")
@Getter
@Setter
public class PolicyFieldCatalogVersionView extends BaseView {

    /** 目录版本（主键，业务唯一键；如 2026.09.14.2） */
    @Id
    @Column(name = "catalog_version", nullable = false, length = 32)
    private String catalogVersion;

    /** 该版本发布时冻结的内容 SHA-256 */
    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    /** 生效起始日（含当日） */
    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    /** 生效截止日（不含当日；null = 开放区间） */
    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    /** 发布人 */
    @Column(name = "published_by", nullable = false, length = 32)
    private String publishedBy;

    /** 发布时间 */
    @Column(name = "published_at", nullable = false)
    private LocalDateTime publishedAt;
}
