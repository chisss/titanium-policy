package com.titanium.policy.query.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.common.exception.BusinessException;
import com.titanium.metadata.enums.BaseEnum;
import com.titanium.metadata.enums.insurance.InsuranceCategory;
import com.titanium.metadata.enums.insurance.InsuranceProductType;
import com.titanium.metadata.enums.policy.PolicyEnum;
import com.titanium.metadata.enums.policy.PolicyServiceRestrictionType;
import com.titanium.metadata.enums.product.ProductEnum.PaymentFrequency;
import com.titanium.metadata.valueobject.Money;
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
import com.titanium.policy.query.view.PolicyInsuredView;
import com.titanium.policy.query.view.PolicyProductView;
import com.titanium.policy.query.view.PolicyView;
import com.titanium.policy.query.view.PolicyView_;
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
        List<PolicyProductView> mainSegments = requireMainSegments(policyId, tenantId);
        String customerId = requireSnapshotText(policy.getPolicyHolderId(), "Policy缺少投保人客户标识");
        // 🔴 合同级要素 = 多主险段逐段值以逗号连接（G11/g11-04 D3）：单主险时即该段原值（逐字不变），
        //    多主险时全部主险段都在值内（不用「取首段」，D1 纪律）；口径与写侧指纹及下方发布字段同源。
        String productId = PolicyMaintenanceHashing.joinMainProductField(
                mainSegments.stream().map(PolicyProductView::getProductId).toList());
        String productVersion = PolicyMaintenanceHashing.joinMainProductField(
                mainSegments.stream().map(PolicyProductView::getProductVersion).toList());
        String planVersion = PolicyMaintenanceHashing.joinMainProductField(
                mainSegments.stream().map(PolicyProductView::getPricingPlanVersion).toList());
        if (policy.getCurrentVersion() == null || policy.getCurrentVersion() < 0) {
            throw snapshotFailure("Policy缺少有效业务基准版本", "POLICY_MAINTENANCE_SNAPSHOT_VERSION_MISSING");
        }
        if (policy.getPolicyStatus() == null || policy.getStartDate() == null) {
            throw snapshotFailure("Policy缺少状态或业务生效时点", "POLICY_MAINTENANCE_SNAPSHOT_CONTRACT_INVALID");
        }

        long policyVersion = policy.getCurrentVersion().longValue();
        OffsetDateTime capturedAt = OffsetDateTime.now(BUSINESS_OFFSET);
        OffsetDateTime businessEffectiveAt = policy.getStartDate().atOffset(BUSINESS_OFFSET);
        OffsetDateTime nextBillingDateAt = nextBillingDate(policy, mainSegments, capturedAt.toLocalDateTime());
        OffsetDateTime nextPolicyAnniversaryAt = nextPolicyAnniversary(policy, capturedAt.toLocalDateTime());
        List<PolicyBeneficiaryView> beneficiaries = policyBeneficiaryViewRepository
                .findByPolicyIdAndTenantId(policyId, tenantId);
        List<PolicyInsuredView> insureds = policyInsuredViewRepository
                .findByPolicyIdAndTenantId(policyId, tenantId);
        Map<String, PolicySnapshotFieldValueQueryResult> fieldValues =
                buildSnapshotFields(policy, mainSegments, insureds, beneficiaries);
        String storageKey = "axon-event://policy/" + tenantId + "/" + policyId + "?version=" + policyVersion;
        String contentHash = snapshotHash(
                policy, mainSegments, productId, productVersion, planVersion, policyVersion, fieldValues);
        return new PolicyMaintenanceSnapshotQueryResult(
                tenantId, policyId, requireSnapshotText(policy.getPolicyNo(), "Policy缺少保单号"), customerId,
                productId, productVersion, planVersion, policy.getPolicyStatus(),
                activeRestrictionTypes(policy),
                policyVersion,
                businessEffectiveAt, nextBillingDateAt, nextPolicyAnniversaryAt,
                storageKey, contentHash, capturedAt, fieldValues);
    }

    /**
     * 解析读模型的服务受限列为受限种类清单。
     * <p>
     * 列值是逗号连接的种类码（{@code t_policy_view.service_restriction_types}），空/空白即无受限。
     * 存量行该列为 {@code null}，与「无受限」同解——这正是列语义（空即无受限），无需回填。
     * </p>
     */
    private List<PolicyServiceRestrictionType> activeRestrictionTypes(PolicyView policy) {
        String raw = policy.getServiceRestrictionTypes();
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(code -> !code.isEmpty())
                .map(PolicyServiceRestrictionType::fromCode)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 保单级「下一缴费日」= 各主险段候选中的最早非空者（G11/g11-04 D3）。
     * <p>
     * 交强与商业各自持有缴费计划（年缴/月缴…），保单层面的「下一缴费日」取最早到期者——确定性派生，
     * 不随段顺序变化。单主险时即该段原值（逐字不变）；各段均无可用缴费日时为 {@code null}，
     * 维护域据此拒绝依赖该字段的生效方式（NEXT_BILLING_DATE 建案时按「未提供可用的下一缴费日」拒绝）。
     * </p>
     */
    private OffsetDateTime nextBillingDate(
            PolicyView policy,
            List<PolicyProductView> mainSegments,
            LocalDateTime now) {
        return mainSegments.stream()
                .map(mainSegment -> nextBillingDate(policy, mainSegment, now))
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);
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

    /**
     * 校验并返回本保单的<b>全部主险段</b>（G11/g11-04 缺口①：与写侧
     * {@code Policy.requireMainProductsForMaintenance} 同判据）。
     * <p>
     * 原判据「{@code size() != 1} 即拒」会让交强 + 商业同单（两个主险段）保单的<b>任何保全</b>
     * 在建案取快照时被拒（{@code POLICY_MAINTENANCE_SNAPSHOT_CONTRACT_INVALID}）。按 D3 下沉后：
     * 无主险段 ⇒ 拒；<b>任一</b>主险段缺 productId / productVersion / pricingPlanVersion ⇒ 拒。
     * 单主险时与旧行为<b>完全等价</b>（旧判据 = size==1 且该段三要素齐备），无「取首段」语义。
     * </p>
     */
    private List<PolicyProductView> requireMainSegments(String policyId, String tenantId) {
        List<PolicyProductView> mainProducts = policyProductViewRepository
                .findByPolicyIdAndTenantIdOrderByLineNoAsc(policyId, tenantId).stream()
                .filter(product -> "MAIN".equals(product.getProductCategory()))
                .toList();
        if (mainProducts.isEmpty()) {
            throw snapshotFailure("Policy须含至少一个主险段", "POLICY_MAINTENANCE_SNAPSHOT_CONTRACT_INVALID");
        }
        for (PolicyProductView mainProduct : mainProducts) {
            requireSnapshotText(mainProduct.getProductId(), "Policy主险缺少产品标识");
            requireSnapshotText(mainProduct.getProductVersion(), "Policy主险缺少产品版本");
            requireSnapshotText(mainProduct.getPricingPlanVersion(), "Policy主险缺少确认定价计划版本");
        }
        return mainProducts;
    }

    private Map<String, PolicySnapshotFieldValueQueryResult> buildSnapshotFields(
            PolicyView policy,
            List<PolicyProductView> mainSegments,
            List<PolicyInsuredView> insureds,
            List<PolicyBeneficiaryView> beneficiaries) {
        TreeMap<String, PolicySnapshotFieldValueQueryResult> fields = new TreeMap<>();
        fields.put("policy.collection.mode", enumField(policy.getCollectionMode()));
        // 🔴 保额逐主险段对象化发布（G11/g11-04 缺口②）：字段目录声明本字段为集合字段
        //    （object_identity_field=policyProductId、requires_object_id=1），与受益人/被保人集合字段同构。
        //    单主险时仅多一个段前缀（维护域 Key 规范化后对外形态逐字不变）；多主险时各段各自成键，
        //    不再互相覆盖（原单键写法下后段保额会静默覆盖前段）。
        //    ⚠️ 合同快照指纹不含段级键：snapshotHash 折叠回裸码合计（与写侧同口径），见该处注释。
        for (PolicyProductView mainSegment : mainSegments) {
            String objectId = mainSegment.getPolicyProductId();
            fields.put(collectionKey(objectId, "policy.coverage.sumInsured"),
                    decimalField(mainSegment.getSumInsured(), objectId));
        }
        String mainCurrencies = PolicyMaintenanceHashing.distinctCurrencies(
                mainSegments.stream().map(PolicyProductView::getCurrency).toList());
        fields.put("policy.currency", enumField(mainCurrencies.isEmpty() ? null : mainCurrencies));
        fields.put("policy.holder.birthDate", dateField(policy.getPolicyHolderBirthDate()));
        fields.put("policy.holder.documentNumber", textField(policy.getPolicyHolderIdNo()));
        fields.put("policy.holder.documentType", enumField(policy.getPolicyHolderIdType()));
        fields.put("policy.holder.gender", enumField(policy.getPolicyHolderGender()));
        fields.put("policy.holder.id", textField(policy.getPolicyHolderId()));
        fields.put("policy.holder.mobile", textField(policy.getPolicyHolderPhone()));
        fields.put("policy.holder.name", textField(policy.getPolicyHolderName()));
        fields.put("policy.number", textField(policy.getPolicyNo()));
        fields.put("policy.period.end", dateTimeField(policy.getEndDate()));
        fields.put("policy.period.start", dateTimeField(policy.getStartDate()));
        fields.put("policy.premium.total", decimalField(policy.getTotalPremium()));
        fields.put("policy.product.id", textField(PolicyMaintenanceHashing.joinMainProductField(
                mainSegments.stream().map(PolicyProductView::getProductId).toList())));
        fields.put("policy.product.planVersion", textField(PolicyMaintenanceHashing.joinMainProductField(
                mainSegments.stream().map(PolicyProductView::getPricingPlanVersion).toList())));
        fields.put("policy.product.version", textField(PolicyMaintenanceHashing.joinMainProductField(
                mainSegments.stream().map(PolicyProductView::getProductVersion).toList())));
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
        // 被保险人身份要素（m15-1804 起可批改）：行主键即保全对象标识，必须发布 objectId，
        // 否则维护侧枚举不到被保险人集合元素、无从提交以 insuredId 定位的字段变更。
        insureds.forEach(insured -> {
            String objectId = insured.getId();
            fields.put(collectionKey(objectId, "policy.insured.name"),
                    textField(insured.getInsuredName(), objectId));
            fields.put(collectionKey(objectId, "policy.insured.documentNumber"),
                    textField(insured.getIdNo(), objectId));
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

    /** 纯日期字段（如出生日期）：按 ISO-8601 日期（yyyy-MM-dd）发布，与字段目录 DATE 类型对应。 */
    private PolicySnapshotFieldValueQueryResult dateField(LocalDate value) {
        return new PolicySnapshotFieldValueQueryResult("DATE", value == null ? null : value.toString());
    }

    /**
     * 合同快照内容指纹（与写侧 {@code Policy.maintenanceSnapshotHash} 同算法、同口径）。
     * <p>
     * 🔴 发布形态 ≠ 指纹形态：{@code policy.coverage.sumInsured} 对外逐主险段对象化发布（键含段前缀），
     * 但写侧指纹用<b>保单级合计裸码</b>（单主险时两者数值相同）。故此处把段级保额键从指纹中排除、
     * 折叠回裸码合计——否则同一快照在读（受理采集）与写（生效回写）两侧产出不同指纹，且存量单主险
     * 在途案件的冲突比对会被误判（部署前受理、部署后生效前再采集）。
     * 其余字段的发布形态与指纹形态本就同构（含受益人/被保人集合键，两侧同为
     * {@code {objectId}:{fieldCode}}）。
     * </p>
     */
    private String snapshotHash(
            PolicyView policy,
            List<PolicyProductView> mainSegments,
            String productId,
            String productVersion,
            String planVersion,
            long policyVersion,
            Map<String, PolicySnapshotFieldValueQueryResult> fields) {
        Set<String> segmentCoverageKeys = mainSegments.stream()
                .map(mainSegment -> collectionKey(
                        mainSegment.getPolicyProductId(), "policy.coverage.sumInsured"))
                .collect(Collectors.toSet());
        TreeMap<String, PolicyMaintenanceSnapshotFieldValue> domainFields = new TreeMap<>();
        fields.forEach((key, value) -> {
            if (!segmentCoverageKeys.contains(key)) {
                domainFields.put(key, new PolicyMaintenanceSnapshotFieldValue(
                        value.dataType(), value.canonicalValue()));
            }
        });
        domainFields.put("policy.coverage.sumInsured", new PolicyMaintenanceSnapshotFieldValue(
                "DECIMAL", PolicyMaintenanceHashing.totalMainSumInsuredCanonical(
                        mainSegments.stream().map(this::mainSumInsuredMoney).toList())));
        return PolicyMaintenanceHashing.snapshotHash(
                policy.getTenantId(), policy.getPolicyId(), policyVersion,
                productId, productVersion, planVersion, domainFields);
    }

    /**
     * 主险段保额（金额态），段保额为 null 时返回 null（合计时跳过）。
     * <p>
     * 经 {@link Money} 构造与求和：币种缺失/非法、多段币种不一致时在此抛异常（fail-closed，
     * 与写侧 {@code Money.add} 同判据）——受理采集即拒，避免「受理放行、生效必败」。
     * </p>
     */
    private Money mainSumInsuredMoney(PolicyProductView mainSegment) {
        return mainSegment.getSumInsured() == null ? null
                : Money.of(mainSegment.getSumInsured(), mainSegment.getCurrency());
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
        // 默认排序：创建时间倒序 + 主键第二排序键（同一秒创建的多行顺序仍然确定，分页不重不漏）
        Sort sort = Sort.by(Sort.Direction.DESC, "createTime").and(Sort.by("policyId"));
        Pageable pageable = PageRequest.of(Math.max(page, 0), normalizeSize(size), sort);
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
            predicates.add(cb.equal(root.get(PolicyView_.tenantId), tenantId));
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
            predicates.add(cb.equal(root.get(PolicyView_.tenantId), tenantId));
            if (isNotBlank(policyNo)) {
                predicates.add(cb.like(root.get(PolicyView_.policyNo), "%" + policyNo + "%"));
            }
            if (isNotBlank(policyHolderName)) {
                predicates.add(cb.like(root.get(PolicyView_.policyHolderName), "%" + policyHolderName + "%"));
            }
            if (isNotBlank(insuredName)) {
                predicates.add(cb.like(root.get(PolicyView_.insuredName), "%" + insuredName + "%"));
            }
            if (isNotBlank(productCode)) {
                predicates.add(cb.equal(root.get(PolicyView_.productCode), productCode));
            }
            if (isNotBlank(status)) {
                PolicyEnum.PolicyStatus statusEnum = PolicyEnum.PolicyStatus.fromCode(status);
                if (statusEnum != null) {
                    predicates.add(cb.equal(root.get(PolicyView_.policyStatus), statusEnum));
                } else {
                    // 🔴 未知码 ⇒ 空集（D-501-66）：静默丢弃条件会返回全表，
                    // 界面表现为「按某状态筛选却列出全部」，比报错更隐蔽
                    log.warn("[保单查询] 状态码未识别，按空集返回: status={}", status);
                    predicates.add(cb.disjunction());
                }
            }
            if (effectiveDateStart != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get(PolicyView_.startDate), effectiveDateStart));
            }
            if (effectiveDateEnd != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get(PolicyView_.startDate), effectiveDateEnd));
            }
            if (expiryDateStart != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get(PolicyView_.endDate), expiryDateStart));
            }
            if (expiryDateEnd != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get(PolicyView_.endDate), expiryDateEnd));
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
        result.setPolicyHolderIdType(view.getPolicyHolderIdType());
        result.setPolicyHolderIdNo(view.getPolicyHolderIdNo());
        result.setPolicyHolderGender(view.getPolicyHolderGender());
        result.setPolicyHolderBirthDate(view.getPolicyHolderBirthDate());
        result.setInsuredName(view.getInsuredName());
        result.setProductCode(view.getProductCode());
        // 列表保持保单主视图字段；详情由 enrichDetail 从保单产品快照补齐产品名称。
        // 🔴 DSN-07（m25-09）：金额直传 BigDecimal，不再经 doubleValue() 降精度为 double
        result.setPremium(view.getPremium());
        result.setSumInsured(view.getSumInsured());
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
        result.setCollectionMode(view.getCollectionMode());
        result.setPaymentMethod(view.getPaymentMethod());
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
