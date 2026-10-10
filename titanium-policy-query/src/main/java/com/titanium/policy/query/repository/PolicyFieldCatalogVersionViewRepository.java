package com.titanium.policy.query.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.titanium.policy.query.view.PolicyFieldCatalogVersionView;

/**
 * 保单字段目录版本头读模型仓储。
 *
 * <p>取全量版本行交由领域解析器（{@code PolicyFieldCatalogPublication#resolveAt}）按业务日期归属——
 * 版本链的排序、重叠校验与降级判定都是**领域规则**，不在此处用 SQL 表达：若在此按
 * {@code effective_from <= ? AND (effective_to IS NULL OR effective_to > ?)} 直接筛出一行，
 * 规则就分裂成「读侧 SQL 一份、领域一份」，而 SQL 那侧对「时间空档」只能返回空、
 * 无法与「尚未发布任何版本」区分，必然被降级成同一种静默行为。
 * 版本表行数是「发布次数」级别（每年个位数），全量取回无性能问题。</p>
 */
public interface PolicyFieldCatalogVersionViewRepository
        extends JpaRepository<PolicyFieldCatalogVersionView, String> {

    /** 取全量版本行，按生效起始日升序（同日起始按版本号升序，保证结果稳定）。 */
    List<PolicyFieldCatalogVersionView> findAllByOrderByEffectiveFromAscCatalogVersionAsc();
}
