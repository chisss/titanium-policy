package com.titanium.policy.query.handler.projection;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.common.jpa.BasePersistable;
import com.titanium.policy.entity.insurance.InsuredPartyList;
import com.titanium.policy.entity.policy.PolicyProduct;
import com.titanium.policy.event.PolicyEndorsedEvent;
import com.titanium.policy.event.PolicyMaintenanceAppliedEvent;
import com.titanium.policy.event.PolicyMaintenanceStateAppliedEvent;
import com.titanium.policy.query.mapper.PolicyViewMapper;
import com.titanium.policy.query.repository.PolicyBeneficiaryViewRepository;
import com.titanium.policy.query.repository.PolicyEndorsementViewRepository;
import com.titanium.policy.query.repository.PolicyInsuredViewRepository;
import com.titanium.policy.query.repository.PolicyProductViewRepository;
import com.titanium.policy.query.repository.PolicyViewRepository;
import com.titanium.policy.query.view.PolicyBeneficiaryView;
import com.titanium.policy.query.view.PolicyEndorsementView;
import com.titanium.policy.query.view.PolicyInsuredView;
import com.titanium.policy.query.view.PolicyProductView;
import com.titanium.policy.query.view.PolicyView;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceExecutionState;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceObjectId;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 批单读模型投影
 * <p>
 * 订阅 PolicyEndorsedEvent，写入批单流水读模型 t_policy_endorsement_view，并同步刷新
 * 保单读模型 t_policy_view 的当前版本号与更新时间。与现有 PolicyProjectionEventHandler
 * 共用 policy-query-group 处理组（复用 DLQ/tracking 配置）。
 * </p>
 */
@Slf4j
@Component
@ProcessingGroup("policy-query-group")
@RequiredArgsConstructor
public class EndorsementProjectionEventHandler {

    private final PolicyEndorsementViewRepository endorsementViewRepository;
    private final PolicyViewRepository            policyViewRepository;
    private final PolicyProductViewRepository     policyProductViewRepository;
    private final PolicyViewMapper                policyViewMapper;
    private final PolicyBeneficiaryViewRepository beneficiaryViewRepository;
    private final PolicyInsuredViewRepository     insuredViewRepository;

    /**
     * 投影批改事件：写批单流水 + 刷新保单读模型版本
     */
    @EventHandler
    @Transactional
    public void on(PolicyEndorsedEvent event) {
        log.info("[读模型投影] 保单批改: policyId={}, endorsementNo={}, type={}", event.policyId(),
                event.endorsementNo(), event.updateType().getCode());

        PolicyEndorsementView view = endorsementViewRepository.findById(event.endorsementNo())
                .orElseGet(PolicyEndorsementView::new);

        // 事件字段 → 批单读模型的结构映射收敛到 MapStruct（类型/大类枚举取 code、版本号与生效日改名），消除逐字段 set
        policyViewMapper.applyEndorsed(view, event);
        // 批改落地时间含 now() 兜底，属处理器职责，不下沉映射器
        view.setEndorsedAt(event.endorsedAt() != null ? event.endorsedAt() : LocalDateTime.now());
        stampAuditTime(view);
        endorsementViewRepository.save(view);

        // 同步保单读模型当前版本号 + 更新时间（增量更新既有 View，非新建型，保留逐字段 set）
        policyViewRepository.findByPolicyIdAndTenantId(event.policyId(), event.tenantId()).ifPresent(policy -> {
            policy.setCurrentVersion(event.versionAfter());
            policy.setUpdateTime(LocalDateTime.now());
            policyViewRepository.save(policy);
        });
    }

    /** 投影正式保全应用事件：批单、业务版本和实际字段在同一事务刷新。 */
    @EventHandler
    @Transactional
    public void on(PolicyMaintenanceAppliedEvent event) {
        log.info("[读模型投影] Policy 保全应用: policyId={}, endorsementNo={}, requestId={}",
                event.policyId(), event.endorsementNo(), event.requestId());
        PolicyEndorsementView view = endorsementViewRepository.findById(event.endorsementNo())
                .orElseGet(PolicyEndorsementView::new);
        view.setEndorsementNo(event.endorsementNo());
        view.setPolicyId(event.policyId());
        view.setUpdateType(event.updateType().getCode());
        view.setCategory(event.category().getCode());
        view.setPolicyVersion(Math.toIntExact(event.actualPolicyVersion()));
        view.setEffectiveDate(event.effectiveAt());
        view.setChangeSummary(event.changeSummary());
        view.setRequiresPremiumRecalc(event.updateType().needsPremiumRecalc());
        view.setSourceMaintenanceId(event.sourceMaintenanceId());
        view.setOperatorId(event.operatorId());
        view.setEndorsedAt(event.appliedAt());
        view.setTenantId(event.tenantId());
        stampAuditTime(view);
        endorsementViewRepository.save(view);

        policyViewRepository.findByPolicyIdAndTenantId(event.policyId(), event.tenantId()).ifPresent(policy -> {
            policy.setCurrentVersion(Math.toIntExact(event.actualPolicyVersion()));
            applyExecutionState(event.policyId(), event.tenantId(), policy, event.executionStateAfter());
            policy.setUpdateTime(LocalDateTime.now());
            policyViewRepository.save(policy);
        });
    }

    /** 投影状态类保全的统一批单、版本和字段实际值。 */
    @EventHandler
    @Transactional
    public void on(PolicyMaintenanceStateAppliedEvent event) {
        log.info("[读模型投影] Policy 状态保全应用: policyId={}, endorsementNo={}, action={}",
                event.policyId(), event.endorsementNo(), event.stateAction());
        PolicyEndorsementView view = endorsementViewRepository.findById(event.endorsementNo())
                .orElseGet(PolicyEndorsementView::new);
        view.setEndorsementNo(event.endorsementNo());
        view.setPolicyId(event.policyId());
        view.setUpdateType(event.applicationType());
        view.setCategory(event.category().getCode());
        view.setPolicyVersion(Math.toIntExact(event.actualPolicyVersion()));
        view.setEffectiveDate(event.effectiveAt());
        view.setChangeSummary(event.changeSummary());
        view.setRequiresPremiumRecalc(false);
        view.setSourceMaintenanceId(event.sourceMaintenanceId());
        view.setOperatorId(event.operatorId());
        view.setEndorsedAt(event.appliedAt());
        view.setTenantId(event.tenantId());
        stampAuditTime(view);
        endorsementViewRepository.save(view);

        policyViewRepository.findByPolicyIdAndTenantId(event.policyId(), event.tenantId()).ifPresent(policy -> {
            policy.setCurrentVersion(Math.toIntExact(event.actualPolicyVersion()));
            applyExecutionState(event.policyId(), event.tenantId(), policy, event.executionStateAfter());
            policy.setUpdateTime(LocalDateTime.now());
            policyViewRepository.save(policy);
        });
    }

    private void applyExecutionState(
            String policyId,
            String tenantId,
            PolicyView policy,
            PolicyMaintenanceExecutionState executionState) {
        if (executionState == null) {
            return;
        }
        if (executionState.insuredPartyList() != null
                && executionState.insuredPartyList().holderInfo() != null) {
            // 投保人合同要素（姓名/证件/性别/出生日期/联系方式）：执行状态快照是保全生效后的权威合同快照，
            // 六项一次性回写，避免只回写其中一项造成读模型半新半旧。
            // 🔴 本分支必须留在下方 policyProducts 早返回之前——「仅改投保人字段」的保全不带险种段变更。
            InsuredPartyList.HolderInfo holder = executionState.insuredPartyList().holderInfo();
            policy.setPolicyHolderName(holder.name());
            policy.setPolicyHolderIdType(holder.certType() == null ? null : holder.certType().getCode());
            policy.setPolicyHolderIdNo(holder.certNo());
            policy.setPolicyHolderPhone(holder.phone());
            policy.setPolicyHolderGender(holder.gender() == null ? null : holder.gender().getCode());
            policy.setPolicyHolderBirthDate(holder.birthDate());
        }
        if (executionState.insuredPartyList() != null) {
            // 被保险人身份要素（姓名/证件号码）：执行状态快照是保全生效后的权威参与方快照，
            // 整表重建该保单的被保险人读模型行。🔴 同 holder 分支，本块必须留在下方 policyProducts
            // 早返回之前——「仅改被保险人字段」的保全不带险种段变更。
            replaceInsureds(policyId, tenantId, policy, executionState.insuredPartyList().insuredList());
            replaceBeneficiaries(policyId, tenantId, executionState.insuredPartyList().beneficiaryList());
        }
        // 缴费方式（趸缴/期缴）：字段执行器改写 PremiumPlan 后经执行状态快照回投影。
        // 🔴 必须置于下方 policyProducts 的早返回之前——仅改缴费方式的保全不带险种段变更，
        // 放在早返回之后会被整体跳过，读模型永不更新（缺口登记 G7 的成因）。
        if (executionState.premiumPlan() != null
                && executionState.premiumPlan().paymentMethod() != null) {
            policy.setPaymentMethod(executionState.premiumPlan().paymentMethod().getCode());
        }
        if (executionState.policyProducts() == null) {
            return;
        }
        List<PolicyProductView> productViews = policyProductViewRepository
                .findByPolicyIdAndTenantIdOrderByLineNoAsc(policyId, tenantId);
        for (PolicyProduct product : executionState.policyProducts()) {
            productViews.stream()
                    .filter(view -> product.policyProductId().equals(view.getPolicyProductId()))
                    .findFirst()
                    .ifPresent(view -> {
                        view.setSumInsured(product.sumInsured() == null ? null : product.sumInsured().value());
                        view.setUpdateTime(LocalDateTime.now());
                        policyProductViewRepository.save(view);
                    });
            if (product.isMain()) {
                policy.setSumInsured(product.sumInsured() == null ? null : product.sumInsured().value());
            }
        }
    }

    /**
     * 用执行状态快照整表重建该保单的被保险人读模型行。
     * <p>
     * 主键即保全集合字段的对象标识（{@link PolicyMaintenanceObjectId#insured}），与出单投影
     * {@code PolicyPartyProjectionEventHandler} 同源，保证读侧发布的对象标识写侧执行器可解析。
     * 先删后插并显式 flush：事件重放时新旧主键相同，不强制先落 DELETE 会撞主键。
     * </p>
     */
    private void replaceInsureds(
            String policyId,
            String tenantId,
            PolicyView policy,
            List<InsuredPartyList.InsuredInfo> insureds) {
        insuredViewRepository.deleteByPolicyIdAndTenantId(policyId, tenantId);
        insuredViewRepository.flush();
        List<InsuredPartyList.InsuredInfo> resolved = insureds == null ? List.of() : insureds;
        LocalDateTime now = LocalDateTime.now();
        for (int index = 0; index < resolved.size(); index++) {
            InsuredPartyList.InsuredInfo insured = resolved.get(index);
            PolicyInsuredView view = new PolicyInsuredView();
            view.setId(PolicyMaintenanceObjectId.insured(policyId, insured, index).value());
            view.setPolicyId(policyId);
            view.setCustomerId(insured.customerId());
            view.setInsuredName(insured.name());
            view.setIdType(insured.certType() == null ? null : insured.certType().getCode());
            view.setIdNo(insured.certNo());
            view.setAge(insured.age());
            view.setGender(insured.gender() == null ? null : insured.gender().getCode());
            view.setPhone(insured.phone());
            view.setRelation(insured.relationToHolder());
            view.setFamilyRelation(insured.familyRelation() == null ? null : insured.familyRelation().getCode());
            view.setTenantId(tenantId);
            view.setCreateTime(now);
            view.setUpdateTime(now);
            insuredViewRepository.save(view);
        }
        // 保单主视图的被保险人姓名是列表检索用的冗余列，与参与方投影保持同源（取首位被保险人）
        policy.setInsuredName(resolved.isEmpty() ? null : resolved.getFirst().name());
    }

    private void replaceBeneficiaries(
            String policyId,
            String tenantId,
            List<InsuredPartyList.BeneficiaryInfo> beneficiaries) {
        beneficiaryViewRepository.deleteByPolicyIdAndTenantId(policyId, tenantId);
        beneficiaryViewRepository.flush();
        List<InsuredPartyList.BeneficiaryInfo> resolved = beneficiaries == null ? List.of() : beneficiaries;
        LocalDateTime now = LocalDateTime.now();
        for (int index = 0; index < resolved.size(); index++) {
            InsuredPartyList.BeneficiaryInfo beneficiary = resolved.get(index);
            PolicyBeneficiaryView view = new PolicyBeneficiaryView();
            view.setId(PolicyMaintenanceObjectId.beneficiary(policyId, beneficiary, index).value());
            view.setPolicyId(policyId);
            view.setCustomerId(beneficiary.customerId());
            view.setBeneficiaryName(beneficiary.name());
            view.setIdType(beneficiary.certType() == null ? null : beneficiary.certType().getCode());
            view.setIdNo(beneficiary.certNo());
            view.setGender(beneficiary.gender() == null ? null : beneficiary.gender().getCode());
            view.setPhone(beneficiary.phone());
            view.setBeneficiaryType(
                    beneficiary.beneficiaryType() == null ? null : beneficiary.beneficiaryType().getCode());
            view.setOrderNo(beneficiary.order());
            view.setShareRatio(BigDecimal.valueOf(beneficiary.beneficiaryRatio()).movePointRight(2));
            view.setTenantId(tenantId);
            view.setCreateTime(now);
            view.setUpdateTime(now);
            beneficiaryViewRepository.save(view);
        }
    }

    /**
     * 统一填充读模型审计时间戳：createTime 仅首次创建时写入、updateTime 每次投影刷新。
     * <p>
     * 该逻辑含 {@code now()} 运行时副作用与"仅首次设置"语义，属投影处理器职责，不下沉 MapStruct 映射器。
     * </p>
     *
     * @param view 读模型（继承 {@link BasePersistable}）
     */
    private void stampAuditTime(BasePersistable view) {
        LocalDateTime now = LocalDateTime.now();
        if (view.getCreateTime() == null) {
            view.setCreateTime(now);
        }
        view.setUpdateTime(now);
    }
}
