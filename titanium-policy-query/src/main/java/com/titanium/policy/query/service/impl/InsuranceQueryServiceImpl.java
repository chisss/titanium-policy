package com.titanium.policy.query.service.impl;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.policy.common.enums.InsuranceStatusCode;
import com.titanium.policy.query.repository.InsuranceViewRepository;
import com.titanium.policy.query.result.InsuranceQueryResult;
import com.titanium.policy.query.service.InsuranceQueryService;
import com.titanium.policy.query.view.InsuranceView;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 投保单查询服务实现（CQRS 读侧）
 * <p>
 * 查询由事件投影维护的读模型表 {@code t_insurance_view}，实现真正的读写分离。 所有查询强制携带
 * {@code tenantId} 保证多租户隔离。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InsuranceQueryServiceImpl implements InsuranceQueryService {

    private final InsuranceViewRepository insuranceViewRepository;

    @Override
    @Transactional(readOnly = true)
    public InsuranceQueryResult findInsuranceById(String insuranceId, String tenantId) {
        return insuranceViewRepository.findByInsuranceIdAndTenantId(insuranceId, tenantId)
                .map(this::toQueryResult)
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public InsuranceQueryResult findInsuranceByNo(String insuranceNo, String tenantId) {
        return insuranceViewRepository.findByInsuranceNoAndTenantId(insuranceNo, tenantId)
                .map(this::toQueryResult)
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InsuranceQueryResult> findInsurancesByConditions(String insuranceNo, String holderId,
                                                                 String productId, String status, String tenantId,
                                                                 int page, int size) {
        return findInsurancesPageByConditions(insuranceNo, holderId, productId, status, tenantId, page, size)
                .getContent();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<InsuranceQueryResult> findInsurancesPageByConditions(String insuranceNo, String holderId,
                                                                     String productId, String status, String tenantId,
                                                                     int page, int size) {
        Specification<InsuranceView> spec = buildSpec(insuranceNo, holderId, productId, status, tenantId);
        // 默认排序：创建时间倒序 + 主键第二排序键（同一秒创建的多行顺序仍然确定，分页不重不漏）
        Sort sort = Sort.by(Sort.Direction.DESC, "createTime").and(Sort.by("insuranceId"));
        Pageable pageable = PageRequest.of(Math.max(page, 0), size <= 0 ? 20 : size, sort);
        return insuranceViewRepository.findAll(spec, pageable).map(this::toQueryResult);
    }

    /**
     * 构建多条件动态查询规约（仅对非空条件追加谓词）
     */
    private Specification<InsuranceView> buildSpec(String insuranceNo, String holderId, String productId,
                                                    String status, String tenantId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            // 多租户隔离：强制条件
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            if (isNotBlank(insuranceNo)) {
                predicates.add(cb.like(root.get("insuranceNo"), "%" + insuranceNo + "%"));
            }
            if (isNotBlank(holderId)) {
                predicates.add(cb.equal(root.get("holderId"), holderId));
            }
            if (isNotBlank(productId)) {
                predicates.add(cb.equal(root.get("productId"), productId));
            }
            if (isNotBlank(status)) {
                try {
                    InsuranceStatusCode statusEnum = InsuranceStatusCode.valueOf(status);
                    predicates.add(cb.equal(root.get("status"), statusEnum));
                } catch (IllegalArgumentException e) {
                    // 🔴 未知码 ⇒ 空集（D-501-66）：静默丢弃条件会返回全表，
                    // 界面表现为「按某状态筛选却列出全部」，比报错更隐蔽
                    log.warn("[投保单查询] 状态码未识别，按空集返回: status={}", status);
                    predicates.add(cb.disjunction());
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 读模型实体 → 查询结果
     */
    private InsuranceQueryResult toQueryResult(InsuranceView view) {
        InsuranceQueryResult result = new InsuranceQueryResult();
        result.setInsuranceId(view.getInsuranceId());
        result.setInsuranceNo(view.getInsuranceNo());
        result.setProposalId(view.getProposalId());
        result.setPolicyForm(view.getPolicyForm());
        result.setInsuranceType(view.getInsuranceType());
        result.setProductId(view.getProductId());
        result.setSumInsured(view.getSumInsured());
        result.setPaymentFrequency(view.getPaymentFrequency());
        result.setPremiumPaymentYears(view.getPremiumPaymentYears());
        result.setCollectionMode(view.getCollectionMode());
        result.setChannelId(view.getChannelId());
        result.setBizNo(view.getBizNo());
        result.setMarketPackageId(view.getMarketPackageId());
        result.setLineCount(view.getLineCount());
        result.setHolderId(view.getHolderId());
        result.setInsuredCount(view.getInsuredCount());
        result.setExactPremium(view.getExactPremium());
        result.setCurrency(view.getCurrency());
        result.setInsurancePeriodStart(view.getInsurancePeriodStart());
        result.setInsurancePeriodEnd(view.getInsurancePeriodEnd());
        result.setStatus(view.getStatus());
        result.setUnderwritingResultCode(view.getUnderwritingResultCode());
        result.setUnderwritingId(view.getUnderwritingId());
        result.setIssuedTime(view.getIssuedTime());
        result.setCreateTime(view.getCreateTime());
        result.setUpdateTime(view.getUpdateTime());
        result.setTenantId(view.getTenantId());
        return result;
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
