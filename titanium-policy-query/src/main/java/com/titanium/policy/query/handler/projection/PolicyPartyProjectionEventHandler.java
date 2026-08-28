package com.titanium.policy.query.handler.projection;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.policy.entity.insurance.InsuredPartyList;
import com.titanium.policy.event.PolicyCreatedEvent;
import com.titanium.policy.query.repository.PolicyBeneficiaryViewRepository;
import com.titanium.policy.query.repository.PolicyInsuredViewRepository;
import com.titanium.policy.query.view.PolicyBeneficiaryView;
import com.titanium.policy.query.view.PolicyInsuredView;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceObjectId;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 保单参与方读模型投影处理器（CQRS 读侧）
 * <p>
 * 订阅 {@link PolicyCreatedEvent}，将 insuredPartyList 中的被保险人和受益人清单
 * 拆解投影到 {@code t_policy_insured} 和 {@code t_policy_beneficiary}，实现参与方读写分离。
 * </p>
 * <p>
 * <b>幂等性</b>：先按 policyId+tenantId 删除存量，再批量插入，保证事件重放不累积脏数据。
 * </p>
 * <p>
 * <b>约束</b>：纯读侧投影，不发命令、不持有 CommandGateway，遵守 ArchUnit 读侧规约。
 * </p>
 */
@Slf4j
@Component
@ProcessingGroup("policy-query-group")
@RequiredArgsConstructor
public class PolicyPartyProjectionEventHandler {

    private final PolicyInsuredViewRepository   insuredViewRepository;
    private final PolicyBeneficiaryViewRepository beneficiaryViewRepository;

    /**
     * 投影保单创建事件：拆解参与方清单，写入被保险人/受益人读模型
     */
    @EventHandler
    @Transactional
    public void on(PolicyCreatedEvent event) {
        InsuredPartyList partyList = event.insuredPartyList();
        if (partyList == null) {
            // 存量事件无参与方清单，跳过（兼容旧事件流）
            return;
        }
        String policyId = event.policyId();
        String tenantId = event.tenantId();
        LocalDateTime now = LocalDateTime.now();

        // 幂等：先清理该保单的存量参与方投影，再重建
        insuredViewRepository.deleteByPolicyIdAndTenantId(policyId, tenantId);
        beneficiaryViewRepository.deleteByPolicyIdAndTenantId(policyId, tenantId);
        // 🔴 必须显式 flush：投影主键是 policyId 派生的确定性 ID，事件重放时新旧主键相同。
        // Hibernate 在事务提交时按「先 INSERT 后 DELETE」排序动作队列，若不强制先落 DELETE，
        // 重放即撞主键（Duplicate entry for key PRIMARY），导致参与方投影永久失败。
        insuredViewRepository.flush();
        beneficiaryViewRepository.flush();

        // 投影被保险人列表
        List<InsuredPartyList.InsuredInfo> insuredList = partyList.insuredList();
        if (insuredList != null) {
            for (int i = 0; i < insuredList.size(); i++) {
                InsuredPartyList.InsuredInfo insured = insuredList.get(i);
                PolicyInsuredView view = new PolicyInsuredView();
                // 确定性 ID：policyId 前20位 + 被保险人序号，去除横线后截取保证 32 字符以内
                view.setId(deterministicId(policyId, "I", i));
                view.setPolicyId(policyId);
                view.setCustomerId(insured.customerId());
                view.setInsuredName(insured.name());
                view.setIdType(insured.certType() != null ? insured.certType().getCode() : null);
                view.setIdNo(insured.certNo());
                view.setAge(insured.age());
                view.setGender(insured.gender() != null ? insured.gender().getCode() : null);
                view.setPhone(insured.phone());
                view.setRelation(insured.relationToHolder());
                view.setFamilyRelation(
                        insured.familyRelation() != null ? insured.familyRelation().getCode() : null);
                view.setTenantId(tenantId);
                view.setCreateTime(now);
                view.setUpdateTime(now);
                insuredViewRepository.save(view);
            }
        }
        log.info("[参与方投影] 被保险人: policyId={}, count={}", policyId,
                insuredList != null ? insuredList.size() : 0);

        // 投影受益人列表
        List<InsuredPartyList.BeneficiaryInfo> beneficiaryList = partyList.beneficiaryList();
        if (beneficiaryList != null) {
            for (int i = 0; i < beneficiaryList.size(); i++) {
                InsuredPartyList.BeneficiaryInfo beneficiary = beneficiaryList.get(i);
                PolicyBeneficiaryView view = new PolicyBeneficiaryView();
                view.setId(PolicyMaintenanceObjectId.beneficiary(policyId, beneficiary, i).value());
                view.setPolicyId(policyId);
                view.setCustomerId(beneficiary.customerId());
                view.setBeneficiaryName(beneficiary.name());
                view.setIdType(beneficiary.certType() != null ? beneficiary.certType().getCode() : null);
                view.setIdNo(beneficiary.certNo());
                view.setGender(beneficiary.gender() != null ? beneficiary.gender().getCode() : null);
                view.setPhone(beneficiary.phone());
                view.setBeneficiaryType(
                        beneficiary.beneficiaryType() != null ? beneficiary.beneficiaryType().getCode() : null);
                view.setOrderNo(beneficiary.order());
                // ratio 以 1.0=100% 存储，转百分比写入 DB
                view.setShareRatio(BigDecimal.valueOf(beneficiary.beneficiaryRatio() * 100));
                view.setTenantId(tenantId);
                view.setCreateTime(now);
                view.setUpdateTime(now);
                beneficiaryViewRepository.save(view);
            }
        }
        log.info("[参与方投影] 受益人: policyId={}, count={}", policyId,
                beneficiaryList != null ? beneficiaryList.size() : 0);
    }

    /**
     * 生成确定性投影主键：基于 policyId + 类型前缀 + 序号，保证 32 字符内、幂等可重入。
     *
     * @param policyId 保单ID
     * @param prefix   类型前缀（I=被保险人, B=受益人）
     * @param index    列表序号
     * @return 32 字符以内的唯一 ID
     */
    private String deterministicId(String policyId, String prefix, int index) {
        // 用 UUID5 语义：取 policyId+prefix+index 的 UUID，去横线
        String seed = policyId + ":" + prefix + ":" + index;
        return UUID.nameUUIDFromBytes(seed.getBytes())
                .toString().replace("-", "");
    }
}
