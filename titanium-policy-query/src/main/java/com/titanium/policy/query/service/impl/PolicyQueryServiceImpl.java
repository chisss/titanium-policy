package com.titanium.policy.query.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.common.exception.BusinessException;
import com.titanium.metadata.enums.BaseEnum;
import com.titanium.metadata.enums.insurance.InsuranceCategory;
import com.titanium.metadata.enums.insurance.InsuranceProductType;
import com.titanium.metadata.enums.policy.PolicyEnum;
import com.titanium.metadata.enums.product.ProductEnum.PaymentFrequency;
import com.titanium.policy.query.repository.InsuranceViewRepository;
import com.titanium.policy.query.repository.PolicyBeneficiaryViewRepository;
import com.titanium.policy.query.repository.PolicyInsuredViewRepository;
import com.titanium.policy.query.repository.PolicyProductViewRepository;
import com.titanium.policy.query.repository.PolicyViewRepository;
import com.titanium.policy.query.result.PolicyMaintenanceSnapshotQueryResult;
import com.titanium.policy.query.result.PolicyMaintenanceSnapshotQueryResult.PolicySnapshotFieldValueQueryResult;
import com.titanium.policy.query.result.PolicyQueryResult;
import com.titanium.policy.query.result.PolicyStatisticsResult;
import com.titanium.policy.query.service.PolicyQueryService;
import com.titanium.policy.query.view.InsuranceView;
import com.titanium.policy.query.view.PolicyBeneficiaryView;
import com.titanium.policy.query.view.PolicyProductView;
import com.titanium.policy.query.view.PolicyView;
import com.titanium.policy.service.maintenance.PolicyMaintenanceHashing;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceSnapshotFieldValue;

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

    private static final ZoneOffset BUSINESS_OFFSET = ZoneOffset.ofHours(8);

    private final PolicyViewRepository policyViewRepository;
    private final PolicyProductViewRepository policyProductViewRepository;
    private final PolicyInsuredViewRepository policyInsuredViewRepository;
    private final InsuranceViewRepository insuranceViewRepository;
    private final PolicyBeneficiaryViewRepository policyBeneficiaryViewRepository;

    @Override
    @Transactional(readOnly = true)
    public PolicyQueryResult findPolicyById(String policyId, String tenantId) {
        return policyViewRepository.findByPolicyIdAndTenantId(policyId, tenantId)
                .map(view -> enrichDetail(toQueryResult(view), view, tenantId))
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public PolicyMaintenanceSnapshotQueryResult findMaintenanceSnapshot(String policyId, String tenantId) {
        PolicyView policy = policyViewRepository.findByPolicyIdAndTenantId(policyId, tenantId).orElse(null);
        if (policy == null) {
            return null;
        }
        PolicyProductView mainProduct = requireMainProduct(policyId, tenantId);
        String customerId = requireSnapshotText(policy.getPolicyHolderId(), "Policy缺少投保人客户标识");
        String productId = requireSnapshotText(mainProduct.getProductId(), "Policy主险缺少产品标识");
        String productVersion = requireSnapshotText(mainProduct.getProductVersion(), "Policy主险缺少产品版本");
        String planVersion = requireSnapshotText(
                mainProduct.getPricingPlanVersion(), "Policy主险缺少确认定价计划版本");
        if (policy.getCurrentVersion() == null || policy.getCurrentVersion() < 0) {
            throw snapshotFailure("Policy缺少有效业务基准版本", "POLICY_MAINTENANCE_SNAPSHOT_VERSION_MISSING");
        }
        if (policy.getPolicyStatus() == null || policy.getStartDate() == null) {
            throw snapshotFailure("Policy缺少状态或业务生效时点", "POLICY_MAINTENANCE_SNAPSHOT_CONTRACT_INVALID");
        }

        long policyVersion = policy.getCurrentVersion().longValue();
        OffsetDateTime capturedAt = OffsetDateTime.now(BUSINESS_OFFSET);
        OffsetDateTime businessEffectiveAt = policy.getStartDate().atOffset(BUSINESS_OFFSET);
        OffsetDateTime nextBillingDateAt = nextBillingDate(policy, mainProduct, capturedAt.toLocalDateTime());
        OffsetDateTime nextPolicyAnniversaryAt = nextPolicyAnniversary(policy, capturedAt.toLocalDateTime());
        List<PolicyBeneficiaryView> beneficiaries = policyBeneficiaryViewRepository
                .findByPolicyIdAndTenantId(policyId, tenantId);
        Map<String, PolicySnapshotFieldValueQueryResult> fieldValues =
                buildSnapshotFields(policy, mainProduct, beneficiaries);
        String storageKey = "axon-event://policy/" + tenantId + "/" + policyId + "?version=" + policyVersion;
        String contentHash = snapshotHash(policy, mainProduct, policyVersion, fieldValues);
        return new PolicyMaintenanceSnapshotQueryResult(
                tenantId, policyId, requireSnapshotText(policy.getPolicyNo(), "Policy缺少保单号"), customerId,
                productId, productVersion, planVersion, policy.getPolicyStatus(), policyVersion,
                businessEffectiveAt, nextBillingDateAt, nextPolicyAnniversaryAt,
                storageKey, contentHash, capturedAt, fieldValues);
    }

    private OffsetDateTime nextBillingDate(
            PolicyView policy,
            PolicyProductView mainProduct,
            LocalDateTime now) {
        PaymentFrequency frequency = BaseEnum.fromCode(
                PaymentFrequency.class, mainProduct.getPaymentFrequency());
        if (frequency == null || frequency == PaymentFrequency.LUMP_SUM) {
            return null;
        }
        LocalDateTime anchor = mainProduct.getPeriodStart() != null
                ? mainProduct.getPeriodStart() : policy.getStartDate();
        int stepMonths = switch (frequency) {
            case ANNUAL -> 12;
            case SEMI_ANNUAL -> 6;
            case QUARTERLY -> 3;
            case MONTHLY -> 1;
            case LUMP_SUM -> 0;
        };
        LocalDateTime candidate = anchor;
        while (!candidate.isAfter(now)) {
            candidate = candidate.plusMonths(stepMonths);
        }
        Integer paymentYears = mainProduct.getPremiumPaymentYears();
        LocalDateTime paymentEndExclusive = paymentYears == null || paymentYears <= 0
                ? null : anchor.plusYears(paymentYears);
        LocalDateTime coverageEnd = mainProduct.getPeriodEnd() != null
                ? mainProduct.getPeriodEnd() : policy.getEndDate();
        if (paymentEndExclusive != null && !candidate.isBefore(paymentEndExclusive)
                || coverageEnd != null && !candidate.isBefore(coverageEnd)) {
            return null;
        }
        return candidate.atOffset(BUSINESS_OFFSET);
    }

    private OffsetDateTime nextPolicyAnniversary(PolicyView policy, LocalDateTime now) {
        LocalDateTime candidate = policy.getStartDate();
        int years = Math.max(1, now.getYear() - candidate.getYear());
        candidate = candidate.plusYears(years);
        if (!candidate.isAfter(now)) {
            candidate = candidate.plusYears(1);
        }
        if (policy.getEndDate() != null && !candidate.isBefore(policy.getEndDate())) {
            return null;
        }
        return candidate.atOffset(BUSINESS_OFFSET);
    }

    private PolicyProductView requireMainProduct(String policyId, String tenantId) {
        List<PolicyProductView> mainProducts = policyProductViewRepository
                .findByPolicyIdAndTenantIdOrderByLineNoAsc(policyId, tenantId).stream()
                .filter(product -> "MAIN".equals(product.getProductCategory()))
                .toList();
        if (mainProducts.size() != 1) {
            throw snapshotFailure("Policy必须且只能存在一个主险段", "POLICY_MAINTENANCE_SNAPSHOT_CONTRACT_INVALID");
        }
        return mainProducts.getFirst();
    }

    private Map<String, PolicySnapshotFieldValueQueryResult> buildSnapshotFields(
            PolicyView policy,
            PolicyProductView product,
            List<PolicyBeneficiaryView> beneficiaries) {
        TreeMap<String, PolicySnapshotFieldValueQueryResult> fields = new TreeMap<>();
        fields.put("policy.collection.mode", enumField(policy.getCollectionMode()));
        fields.put("policy.coverage.sumInsured",
                decimalField(product.getSumInsured(), product.getPolicyProductId()));
        fields.put("policy.currency", enumField(product.getCurrency()));
        fields.put("policy.holder.id", textField(policy.getPolicyHolderId()));
        fields.put("policy.holder.mobile", textField(policy.getPolicyHolderPhone()));
        fields.put("policy.holder.name", textField(policy.getPolicyHolderName()));
        fields.put("policy.number", textField(policy.getPolicyNo()));
        fields.put("policy.period.end", dateTimeField(policy.getEndDate()));
        fields.put("policy.period.start", dateTimeField(policy.getStartDate()));
        fields.put("policy.premium.total", decimalField(policy.getTotalPremium()));
        fields.put("policy.product.id", textField(product.getProductId()));
        fields.put("policy.product.planVersion", textField(product.getPricingPlanVersion()));
        fields.put("policy.product.version", textField(product.getProductVersion()));
        fields.put("policy.status", enumField(policy.getPolicyStatus().getCode()));
        beneficiaries.forEach(beneficiary -> {
            String objectId = beneficiary.getId();
            fields.put(collectionKey(objectId, "policy.beneficiary.name"),
                    textField(beneficiary.getBeneficiaryName(), objectId));
            fields.put(collectionKey(objectId, "policy.beneficiary.relationship"),
                    enumField(beneficiary.getBeneficiaryType(), objectId));
            fields.put(collectionKey(objectId, "policy.beneficiary.share"),
                    decimalField(beneficiary.getShareRatio(), objectId));
        });
        return Map.copyOf(fields);
    }

    private PolicySnapshotFieldValueQueryResult textField(String value) {
        return new PolicySnapshotFieldValueQueryResult("TEXT", value);
    }

    private PolicySnapshotFieldValueQueryResult textField(String value, String objectId) {
        return new PolicySnapshotFieldValueQueryResult("TEXT", value, objectId);
    }

    private PolicySnapshotFieldValueQueryResult enumField(String value) {
        return new PolicySnapshotFieldValueQueryResult("ENUM", value);
    }

    private PolicySnapshotFieldValueQueryResult enumField(String value, String objectId) {
        return new PolicySnapshotFieldValueQueryResult("ENUM", value, objectId);
    }

    private String collectionKey(String objectId, String fieldCode) {
        return objectId + ":" + fieldCode;
    }

    private PolicySnapshotFieldValueQueryResult decimalField(BigDecimal value) {
        return decimalField(value, null);
    }

    private PolicySnapshotFieldValueQueryResult decimalField(BigDecimal value, String objectId) {
        String canonical = value == null ? null : value.stripTrailingZeros().toPlainString();
        return new PolicySnapshotFieldValueQueryResult("DECIMAL", canonical, objectId);
    }

    private PolicySnapshotFieldValueQueryResult dateTimeField(LocalDateTime value) {
        String canonical = value == null ? null : value.atOffset(ZoneOffset.ofHours(8)).toString();
        return new PolicySnapshotFieldValueQueryResult("DATETIME", canonical);
    }

    private String snapshotHash(
            PolicyView policy,
            PolicyProductView product,
            long policyVersion,
            Map<String, PolicySnapshotFieldValueQueryResult> fields) {
        Map<String, PolicyMaintenanceSnapshotFieldValue> domainFields = fields.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> new PolicyMaintenanceSnapshotFieldValue(
                                entry.getValue().dataType(), entry.getValue().canonicalValue())));
        return PolicyMaintenanceHashing.snapshotHash(
                policy.getTenantId(), policy.getPolicyId(), policyVersion,
                product.getProductId(), product.getProductVersion(), product.getPricingPlanVersion(), domainFields);
    }

    private String requireSnapshotText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw snapshotFailure(message, "POLICY_MAINTENANCE_SNAPSHOT_VERSION_MISSING");
        }
        return value.trim();
    }

    private BusinessException snapshotFailure(String message, String code) {
        return new BusinessException(message, code);
    }

    /**
     * 装配详情页需要的快照字段。主视图只保存保单级事实，产品名、被保人ID和保单形态分别来自
     * 同一出单事件拆出的险种段、参与方和投保单视图；仅详情查询执行，避免列表查询产生 N+1。
     */
    private PolicyQueryResult enrichDetail(PolicyQueryResult result, PolicyView view, String tenantId) {
        List<PolicyProductView> products = policyProductViewRepository
                .findByPolicyIdAndTenantIdOrderByLineNoAsc(view.getPolicyId(), tenantId);
        products.stream()
                .filter(product -> "MAIN".equals(product.getProductCategory()))
                .findFirst()
                .or(() -> products.stream().findFirst())
                .map(PolicyProductView::getProductName)
                .filter(this::isNotBlank)
                .ifPresent(result::setProductName);

        policyInsuredViewRepository.findByPolicyIdAndTenantId(view.getPolicyId(), tenantId).stream()
                .map(insured -> insured.getCustomerId())
                .filter(this::isNotBlank)
                .findFirst()
                .ifPresent(result::setInsuredId);

        if (view.getInsuranceId() != null) {
            insuranceViewRepository.findByInsuranceIdAndTenantId(view.getInsuranceId(), tenantId)
                    .map(InsuranceView::getPolicyForm)
                    .ifPresent(result::setPolicyForm);
        }
        return result;
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
        return findPoliciesPageByMultipleConditions(policyNo, policyHolderName, insuredName, productCode, status,
                effectiveDateStart, effectiveDateEnd, expiryDateStart, expiryDateEnd, tenantId, page, size)
                .getContent();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PolicyQueryResult> findPoliciesPageByMultipleConditions(String policyNo, String policyHolderName,
                                                                        String insuredName, String productCode,
                                                                        String status,
                                                                        LocalDateTime effectiveDateStart,
                                                                        LocalDateTime effectiveDateEnd,
                                                                        LocalDateTime expiryDateStart,
                                                                        LocalDateTime expiryDateEnd, String tenantId,
                                                                        int page, int size) {
        Specification<PolicyView> spec = buildSpecification(policyNo, policyHolderName, insuredName, productCode,
                status, effectiveDateStart, effectiveDateEnd, expiryDateStart, expiryDateEnd, tenantId);
        Pageable pageable = PageRequest.of(Math.max(page, 0), normalizeSize(size));
        return policyViewRepository.findAll(spec, pageable).map(this::toQueryResult);
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
        // 列表保持保单主视图字段；详情由 enrichDetail 从保单产品快照补齐产品名称。
        if (view.getPremium() != null) {
            result.setPremium(view.getPremium().doubleValue());
        }
        if (view.getSumInsured() != null) {
            result.setSumInsured(view.getSumInsured().doubleValue());
        }
        result.setCurrency(view.getCurrency());
        result.setEffectiveDate(view.getStartDate());
        result.setExpiryDate(view.getEndDate());
        result.setStatus(view.getPolicyStatus());
        result.setCreateTime(view.getCreateTime());
        result.setUpdateTime(view.getUpdateTime());
        result.setTenantId(view.getTenantId());
        result.setProposalId(view.getProposalId());
        result.setUnderwritingId(view.getUnderwritingId());
        result.setMarketPackageId(view.getMarketPackageId());
        result.setProductId(view.getProductId());
        result.setTotalPremium(view.getTotalPremium());
        result.setLineCount(view.getLineCount());
        result.setWaitingPeriodEndDate(view.getWaitingPeriodEndDate());
        result.setHesitationPeriodEndDate(view.getHesitationPeriodEndDate());
        result.setCollectionMode(view.getCollectionMode());
        result.setCollectionStatus(view.getCollectionStatus());
        result.setCollectedAmount(view.getCollectedAmount());
        result.setChannelId(view.getChannelId());
        result.setSalesChannel(view.getSalesChannel());
        result.setAgentId(view.getAgentId());
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public PolicyStatisticsResult getStatistics(String tenantId) {
        // 有效保单数：状态为 EFFECTIVE（保单状态枚举无 ACTIVE，生效态即有效）
        long activeCount = policyViewRepository.countByPolicyStatusAndTenantId(PolicyEnum.PolicyStatus.EFFECTIVE,
                tenantId);
        // 今日新增：create_time 落在 [今日 00:00, 次日 00:00) 半开区间
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime tomorrowStart = todayStart.plusDays(1);
        long todayCount = policyViewRepository
                .countByTenantIdAndCreateTimeGreaterThanEqualAndCreateTimeLessThan(tenantId, todayStart, tomorrowStart);
        // 保单总数
        long totalCount = policyViewRepository.countByTenantId(tenantId);
        // 险种一级分类分布：三级险种分组计数后按一级分类归并
        List<PolicyStatisticsResult.CategoryDistribution> distribution = buildCategoryDistribution(tenantId);
        return new PolicyStatisticsResult(activeCount, todayCount, totalCount, distribution);
    }

    /**
     * 按险种一级分类归并保单数分布。
     * <p>
     * 仓储按三级险种（{@link InsuranceProductType}）分组计数，此处上溯到一级分类（{@link InsuranceCategory}）
     * 累加，输出看板 {@code {name, value}} 结构，保持分类枚举声明顺序稳定。
     * </p>
     */
    private List<PolicyStatisticsResult.CategoryDistribution> buildCategoryDistribution(String tenantId) {
        // 用 EnumMap 保持一级分类声明顺序，避免看板展示顺序抖动
        Map<InsuranceCategory, Long> categoryCount = new EnumMap<>(InsuranceCategory.class);
        for (Object[] row : policyViewRepository.countGroupByInsuranceType(tenantId)) {
            InsuranceProductType type = (InsuranceProductType) row[0];
            long count = ((Number) row[1]).longValue();
            categoryCount.merge(type.getCategory(), count, Long::sum);
        }
        return categoryCount.entrySet().stream()
                .map(entry -> new PolicyStatisticsResult.CategoryDistribution(entry.getKey().getName(),
                        entry.getValue()))
                .toList();
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }

    private int normalizeSize(int size) {
        return size <= 0 ? 20 : size;
    }
}
