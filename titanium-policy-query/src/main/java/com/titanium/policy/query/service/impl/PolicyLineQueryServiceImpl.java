package com.titanium.policy.query.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.metadata.enums.customer.CustomerEnum.InsuranceRole;
import com.titanium.policy.query.mapper.PolicyLineQueryMapper;
import com.titanium.policy.query.repository.PolicyBeneficiaryViewRepository;
import com.titanium.policy.query.repository.PolicyClauseViewRepository;
import com.titanium.policy.query.repository.PolicyCollectionViewRepository;
import com.titanium.policy.query.repository.PolicyCoverageViewRepository;
import com.titanium.policy.query.repository.PolicyInsuredViewRepository;
import com.titanium.policy.query.repository.PolicyProductViewRepository;
import com.titanium.policy.query.repository.PolicySubjectViewRepository;
import com.titanium.policy.query.repository.PolicyViewRepository;
import com.titanium.policy.query.result.PolicyClauseQueryResult;
import com.titanium.policy.query.result.PolicyCollectionQueryResult;
import com.titanium.policy.query.result.PolicyCoverageQueryResult;
import com.titanium.policy.query.result.PolicyFullDetailQueryResult;
import com.titanium.policy.query.result.PolicyProductQueryResult;
import com.titanium.policy.query.result.PolicyQueryResult;
import com.titanium.policy.query.result.PolicySubjectQueryResult;
import com.titanium.policy.query.service.PolicyLineQueryService;
import com.titanium.policy.query.view.PolicyClauseView;
import com.titanium.policy.query.view.PolicyCoverageView;
import com.titanium.policy.query.view.PolicyProductView;
import com.titanium.policy.query.view.PolicySubjectView;
import com.titanium.policy.query.view.PolicyView;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 保单险种段族读模型查询服务实现
 * <p>
 * 一单多险的读侧装配中心。全景查询聚合七张读模型表，<b>按段分组一次性装配</b>——先按 policyId
 * 批量取出全部条款/标的/责任行，再在内存中按 policyProductId 分组挂到各段上，避免「N 个段
 * 各查三次」的 N+1 查询。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PolicyLineQueryServiceImpl implements PolicyLineQueryService {

    private static final String MAIN_PRODUCT_CATEGORY = "MAIN";

    /**
     * 默认排序：创建时间倒序 + 主键第二排序键（同一秒创建的多行顺序仍然确定，分页窗口落在确定顺序上）
     */
    private static final Comparator<PolicyView> CREATE_TIME_DESC_THEN_POLICY_ID = Comparator
            .comparing(PolicyView::getCreateTime, Comparator.nullsLast(Comparator.<LocalDateTime>reverseOrder()))
            .thenComparing(PolicyView::getPolicyId);

    private final PolicyViewRepository            policyViewRepository;
    private final PolicyProductViewRepository     policyProductViewRepository;
    private final PolicyClauseViewRepository      policyClauseViewRepository;
    private final PolicySubjectViewRepository     policySubjectViewRepository;
    private final PolicyCoverageViewRepository    policyCoverageViewRepository;
    private final PolicyCollectionViewRepository  policyCollectionViewRepository;
    private final PolicyInsuredViewRepository     policyInsuredViewRepository;
    private final PolicyBeneficiaryViewRepository policyBeneficiaryViewRepository;
    private final PolicyLineQueryMapper           mapper;

    @Override
    public Optional<PolicyFullDetailQueryResult> findFullDetail(String policyId, String tenantId) {
        Optional<PolicyView> policyView = policyViewRepository.findByPolicyIdAndTenantId(policyId, tenantId);
        if (policyView.isEmpty()) {
            log.debug("保单不存在，全景查询返回空: policyId={}, tenantId={}", policyId, tenantId);
            return Optional.empty();
        }
        PolicyFullDetailQueryResult result = new PolicyFullDetailQueryResult();
        result.setPolicy(mapper.toPolicyResult(policyView.get()));
        result.setLines(findLines(policyId, true, tenantId));
        result.setInsuredList(policyInsuredViewRepository.findByPolicyIdAndTenantId(policyId, tenantId).stream()
                .map(mapper::toInsuredResult).toList());
        result.setBeneficiaryList(policyBeneficiaryViewRepository.findByPolicyIdAndTenantId(policyId, tenantId).stream()
                .map(mapper::toBeneficiaryResult).toList());
        result.setCollection(findCollection(policyId, tenantId).orElse(null));
        log.info("保单全景查询完成: policyId={}, 险种段数={}, 责任数={}", policyId, result.lineCount(),
                result.allCoverages().size());
        return Optional.of(result);
    }

    @Override
    public List<PolicyProductQueryResult> findLines(String policyId, boolean withDetails, String tenantId) {
        List<PolicyProductView> lineViews = policyProductViewRepository
                .findByPolicyIdAndTenantIdOrderByLineNoAsc(policyId, tenantId);
        if (lineViews.isEmpty()) {
            return List.of();
        }
        List<PolicyProductQueryResult> lines = lineViews.stream().map(mapper::toLineResult).toList();
        if (!withDetails) {
            return lines;
        }
        // 批量取三类明细后按段分组挂载，避免 N 个段各查三次（N+1 查询）
        Map<String, List<PolicyClauseView>> clausesByLine = groupByLine(
                policyClauseViewRepository.findByPolicyIdAndTenantId(policyId, tenantId),
                PolicyClauseView::getPolicyProductId);
        Map<String, List<PolicySubjectView>> subjectsByLine = groupByLine(
                policySubjectViewRepository.findByPolicyIdAndTenantId(policyId, tenantId),
                PolicySubjectView::getPolicyProductId);
        Map<String, List<PolicyCoverageView>> coveragesByLine = groupByLine(
                policyCoverageViewRepository.findByPolicyIdAndTenantId(policyId, tenantId),
                PolicyCoverageView::getPolicyProductId);

        for (PolicyProductQueryResult line : lines) {
            String lineId = line.getPolicyProductId();
            line.setClauses(clausesByLine.getOrDefault(lineId, List.of()).stream()
                    .map(mapper::toClauseResult).toList());
            line.setSubjects(subjectsByLine.getOrDefault(lineId, List.of()).stream()
                    .map(mapper::toSubjectResult).toList());
            line.setCoverages(coveragesByLine.getOrDefault(lineId, List.of()).stream()
                    .map(mapper::toCoverageResult).toList());
        }
        return lines;
    }

    @Override
    public List<PolicyCoverageQueryResult> findCoverages(String policyId, String tenantId) {
        return policyCoverageViewRepository.findByPolicyIdAndTenantId(policyId, tenantId).stream()
                .map(mapper::toCoverageResult)
                .toList();
    }

    @Override
    public List<PolicyClauseQueryResult> findClauses(String policyId, String tenantId) {
        return policyClauseViewRepository.findByPolicyIdAndTenantId(policyId, tenantId).stream()
                .map(mapper::toClauseResult)
                .toList();
    }

    @Override
    public List<PolicySubjectQueryResult> findSubjects(String policyId, String tenantId) {
        return policySubjectViewRepository.findByPolicyIdAndTenantId(policyId, tenantId).stream()
                .map(mapper::toSubjectResult)
                .toList();
    }

    @Override
    public Optional<PolicyCollectionQueryResult> findCollection(String policyId, String tenantId) {
        return policyCollectionViewRepository.findByPolicyIdAndTenantId(policyId, tenantId)
                .map(mapper::toCollectionResult);
    }

    @Override
    public List<PolicyQueryResult> findByCustomerRole(String customerId, InsuranceRole role, String tenantId, int page,
                                                      int size) {
        Set<String> policyIds = collectPolicyIdsByRole(customerId, role, tenantId);
        if (policyIds.isEmpty()) {
            return List.of();
        }
        // 内存分页：单客户名下保单量级有限（个人客户通常 < 100 张），避免三源 union 的复杂分页 SQL。
        // 🔴 三个角色的数据源各自无序，故先取回全部候选保单并按「创建时间倒序 + 主键第二排序键」显式排序，
        // 再做 skip/limit —— 否则分页窗口落在无确定顺序的集合上，跨页会重复/漏行（正确性缺陷）。
        List<PolicyView> orderedViews = policyIds.stream()
                .map(policyId -> policyViewRepository.findByPolicyIdAndTenantId(policyId, tenantId))
                .flatMap(Optional::stream)
                .sorted(CREATE_TIME_DESC_THEN_POLICY_ID)
                .toList();
        List<PolicyQueryResult> results = orderedViews.stream()
                .skip((long) page * size)
                .limit(size)
                .map(view -> assembleCustomerPolicy(view, tenantId))
                .collect(Collectors.toCollection(ArrayList::new));
        log.info("按客户角色查保单: customerId={}, 角色={}, 命中={}, 返回={}", customerId, role, policyIds.size(),
                results.size());
        return results;
    }

    /**
     * 装配客户关联保单摘要，并从出单快照补充主险展示字段。
     */
    private PolicyQueryResult assembleCustomerPolicy(PolicyView view, String tenantId) {
        PolicyQueryResult result = mapper.toPolicyResult(view);
        List<PolicyProductView> productViews = policyProductViewRepository
                .findByPolicyIdAndTenantIdOrderByLineNoAsc(view.getPolicyId(), tenantId);
        if (productViews.isEmpty()) {
            return result;
        }

        PolicyProductView mainProduct = productViews.stream()
                .filter(product -> MAIN_PRODUCT_CATEGORY.equals(product.getProductCategory()))
                .findFirst()
                .orElse(productViews.getFirst());
        result.setProductName(mainProduct.getProductName());
        if (result.getProductCode() == null) {
            result.setProductCode(mainProduct.getProductCode());
        }
        return result;
    }

    /**
     * 按角色收集该客户关联的保单ID（角色为 null 时取三种角色并集）。
     * <p>
     * 三个角色的数据源不同：投保人在保单主表的 {@code policyHolderId}，被保险人与受益人各在
     * 自己的明细表。用 {@link LinkedHashSet} 保序去重——同一客户可能既是投保人又是被保险人。
     * </p>
     */
    private Set<String> collectPolicyIdsByRole(String customerId, InsuranceRole role, String tenantId) {
        Set<String> policyIds = new LinkedHashSet<>();
        if (role == null || role == InsuranceRole.POLICY_HOLDER) {
            policyViewRepository
                    .findByPolicyHolderIdAndTenantId(customerId, tenantId, Pageable.unpaged())
                    .forEach(view -> policyIds.add(view.getPolicyId()));
        }
        if (role == null || role == InsuranceRole.INSURED) {
            policyInsuredViewRepository.findByCustomerIdAndTenantId(customerId, tenantId)
                    .forEach(view -> policyIds.add(view.getPolicyId()));
        }
        if (role == null || role == InsuranceRole.BENEFICIARY) {
            policyBeneficiaryViewRepository.findByCustomerIdAndTenantId(customerId, tenantId)
                    .forEach(view -> policyIds.add(view.getPolicyId()));
        }
        return policyIds;
    }

    /**
     * 按险种段ID分组（空安全，段ID为空的行归入空键并在挂载时被忽略）。
     */
    private <T> Map<String, List<T>> groupByLine(List<T> rows, Function<T, String> lineIdExtractor) {
        return rows.stream()
                .filter(row -> lineIdExtractor.apply(row) != null)
                .collect(Collectors.groupingBy(lineIdExtractor));
    }
}
