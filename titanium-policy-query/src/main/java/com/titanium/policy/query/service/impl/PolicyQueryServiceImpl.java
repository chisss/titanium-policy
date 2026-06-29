package com.titanium.policy.query.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.metadata.enums.policy.PolicyEnum;
import com.titanium.policy.query.entity.PolicyQueryResult;
import com.titanium.policy.query.entity.PolicyView;
import com.titanium.policy.query.repository.PolicyViewRepository;
import com.titanium.policy.query.service.PolicyQueryService;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 保单查询服务实现（CQRS 读侧）
 * <p>
 * 改造说明：原实现返回 mock 空数据，现改为查询由事件投影维护的读模型表 {@code t_policy_view}， 实现真正的读写分离。所有查询强制携带
 * {@code tenantId} 保证多租户隔离。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyQueryServiceImpl implements PolicyQueryService {

    private final PolicyViewRepository policyViewRepository;

    @Override
    @Transactional(readOnly = true)
    public PolicyQueryResult findPolicyById(String policyId, String tenantId) {
        return policyViewRepository.findByPolicyIdAndTenantId(policyId, tenantId)
                .map(this::toQueryResult)
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PolicyQueryResult> findPoliciesByCustomerId(String customerId, String tenantId, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), normalizeSize(size));
        return policyViewRepository.findByPolicyHolderIdAndTenantId(customerId, tenantId, pageable)
                .stream()
                .map(this::toQueryResult)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PolicyQueryResult> findPoliciesByMultipleConditions(String policyNo, String policyHolderName,
                                                                    String insuredName, String productCode,
                                                                    String status, LocalDateTime effectiveDateStart,
                                                                    LocalDateTime effectiveDateEnd,
                                                                    LocalDateTime expiryDateStart,
                                                                    LocalDateTime expiryDateEnd, String tenantId,
                                                                    int page, int size) {
        Specification<PolicyView> spec = buildSpecification(policyNo, policyHolderName, insuredName, productCode,
                status, effectiveDateStart, effectiveDateEnd, expiryDateStart, expiryDateEnd, tenantId);
        Pageable pageable = PageRequest.of(Math.max(page, 0), normalizeSize(size));
        return policyViewRepository.findAll(spec, pageable)
                .stream()
                .map(this::toQueryResult)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PolicyQueryResult> findPoliciesByStatus(String status, String tenantId, int page, int size) {
        PolicyEnum.PolicyStatus statusEnum = PolicyEnum.PolicyStatus.fromCode(status);
        if (statusEnum == null) {
            return List.of();
        }
        Pageable pageable = PageRequest.of(Math.max(page, 0), normalizeSize(size));
        return policyViewRepository.findByPolicyStatusAndTenantId(statusEnum, tenantId, pageable)
                .stream()
                .map(this::toQueryResult)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PolicyQueryResult> findPoliciesByDateRange(LocalDateTime startDate, LocalDateTime endDate,
                                                           String dateType, String tenantId, int page, int size) {
        boolean byExpiry = "expiryDate".equalsIgnoreCase(dateType);
        Specification<PolicyView> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            String field = byExpiry ? "endDate" : "startDate";
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get(field), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get(field), endDate));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Pageable pageable = PageRequest.of(Math.max(page, 0), normalizeSize(size));
        return policyViewRepository.findAll(spec, pageable)
                .stream()
                .map(this::toQueryResult)
                .toList();
    }

    /**
     * 构建多条件动态查询规约（仅对非空条件追加谓词）
     */
    private Specification<PolicyView> buildSpecification(String policyNo, String policyHolderName, String insuredName,
                                                         String productCode, String status,
                                                         LocalDateTime effectiveDateStart,
                                                         LocalDateTime effectiveDateEnd,
                                                         LocalDateTime expiryDateStart, LocalDateTime expiryDateEnd,
                                                         String tenantId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            // 多租户隔离：强制条件
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            if (isNotBlank(policyNo)) {
                predicates.add(cb.like(root.get("policyNo"), "%" + policyNo + "%"));
            }
            if (isNotBlank(policyHolderName)) {
                predicates.add(cb.like(root.get("policyHolderName"), "%" + policyHolderName + "%"));
            }
            if (isNotBlank(insuredName)) {
                predicates.add(cb.like(root.get("insuredName"), "%" + insuredName + "%"));
            }
            if (isNotBlank(productCode)) {
                predicates.add(cb.equal(root.get("productCode"), productCode));
            }
            if (isNotBlank(status)) {
                PolicyEnum.PolicyStatus statusEnum = PolicyEnum.PolicyStatus.fromCode(status);
                if (statusEnum != null) {
                    predicates.add(cb.equal(root.get("policyStatus"), statusEnum));
                }
            }
            if (effectiveDateStart != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("startDate"), effectiveDateStart));
            }
            if (effectiveDateEnd != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("startDate"), effectiveDateEnd));
            }
            if (expiryDateStart != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("endDate"), expiryDateStart));
            }
            if (expiryDateEnd != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("endDate"), expiryDateEnd));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 读模型实体 → 查询结果 DTO
     */
    private PolicyQueryResult toQueryResult(PolicyView view) {
        PolicyQueryResult result = new PolicyQueryResult();
        result.setPolicyId(view.getPolicyId());
        result.setPolicyNo(view.getPolicyNo());
        result.setApplicationId(view.getInsuranceId());
        result.setPolicyHolderId(view.getPolicyHolderId());
        result.setPolicyHolderName(view.getPolicyHolderName());
        result.setInsuredName(view.getInsuredName());
        result.setProductCode(view.getProductCode());
        if (view.getPremium() != null) {
            result.setPremium(view.getPremium().doubleValue());
        }
        result.setCurrency(view.getCurrency());
        result.setEffectiveDate(view.getStartDate());
        result.setExpiryDate(view.getEndDate());
        result.setStatus(view.getPolicyStatus());
        result.setCreateTime(view.getCreateTime());
        result.setUpdateTime(view.getUpdateTime());
        result.setTenantId(view.getTenantId());
        return result;
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }

    private int normalizeSize(int size) {
        return size <= 0 ? 20 : size;
    }
}
