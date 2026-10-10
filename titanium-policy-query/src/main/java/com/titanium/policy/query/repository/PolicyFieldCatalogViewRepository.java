package com.titanium.policy.query.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.titanium.policy.query.view.PolicyFieldCatalogView;

/**
 * 保单字段目录读模型仓储（按**目录版本**取行）。
 *
 * <p>🔴 <b>取数必须以版本为条件</b>（{@code g07-04}）：行表改为复合主键
 * {@code (catalog_version, field_code)} 后，同一字段的多版本行共存；
 * 若仍按「全表取回」取数，会把多个版本的字段混成一份目录——内容哈希当场不符（fail-closed），
 * 但错误信息会指向「哈希不一致」而非「取数漏了版本条件」，故此处方法名即自证其口径。
 * 排序下推到 SQL 而非在内存排序：目录的 {@code contentHash} 由字段顺序参与计算，
 * 顺序不稳定会让同一份数据算出不同哈希。</p>
 */
public interface PolicyFieldCatalogViewRepository extends JpaRepository<PolicyFieldCatalogView, String> {

    /** 按目录版本取该版本的全部字段行，按稳定字段码升序。 */
    List<PolicyFieldCatalogView> findByCatalogVersionOrderByFieldCodeAsc(String catalogVersion);
}
