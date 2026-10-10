package com.titanium.policy.query.handler.projection;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.policy.entity.insurance.InsuredPartyList;
import com.titanium.policy.event.InsuredMemberAddedEvent;
import com.titanium.policy.event.InsuredMemberRemovedEvent;
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
 * 拆解投影到 {@code t_policy_insured} 和 {@code t_policy_beneficiary}，实现参与方读写分离；
 * 并订阅成员增减事件（{@link InsuredMemberAddedEvent}/{@link InsuredMemberRemovedEvent}，
 * G12/g12-06 团单加保/家庭险增减员入口），维护被保险人清单的最终一致。
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
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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
                insuredViewRepository.save(toInsuredView(policyId, insuredList.get(i), i, tenantId, now));
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
     * 投影被保险人新增事件（团单加保 / 家庭险增员，G12/g12-06）
     * <p>
     * 家庭险场景以事件携带的家庭关系覆盖成员关系，与聚合 {@code Policy#on(InsuredMemberAddedEvent)}
     * 同法，保证读模型与聚合状态一致。幂等：对象标识由
     * {@link PolicyMaintenanceObjectId#insured} 派生，合规成员标识下同一事件重放标识相同，已存在即跳过。
     * </p>
     */
    @EventHandler
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(InsuredMemberAddedEvent event) {
        InsuredPartyList.InsuredInfo member = event.member();
        if (member == null) {
            // 事件载荷缺成员信息：记警告跳过，不阻断事件流（投影只做最终一致维护）
            log.warn("[参与方投影] 新增被保险人事件缺少成员信息，跳过: policyId={}", event.policyId());
            return;
        }
        if (event.familyRelation() != null) {
            member = new InsuredPartyList.InsuredInfo(member.customerId(), member.insuredId(), member.name(),
                    member.certType(), member.certNo(), member.age(), member.gender(), member.phone(),
                    member.relationToHolder(), event.familyRelation());
        }
        // 追加位置 = 当前投影行数；合规 insuredId 下对象标识与顺序无关（优先复用聚合内标识）
        int index = insuredViewRepository.findByPolicyIdAndTenantId(event.policyId(), event.tenantId()).size();
        PolicyInsuredView view = toInsuredView(event.policyId(), member, index, event.tenantId(), LocalDateTime.now());
        if (insuredViewRepository.findById(view.getId()).isPresent()) {
            return;
        }
        insuredViewRepository.save(view);
        log.info("[参与方投影] 新增被保险人: policyId={}, insuredId={}", event.policyId(), member.insuredId());
    }

    /**
     * 投影被保险人移除事件（团单减保 / 家庭险减员，G12/g12-06）
     * <p>
     * 读模型行标识（{@code PolicyInsuredView.id}）在合规成员标识下即聚合内 {@code insuredId}，
     * 直接命中删除；未命中（成员标识为存量长标识的派生值、投影缺失）时记警告跳过、<b>不抛出</b>——
     * 投影只做最终一致维护，不得因单行缺失阻断事件流。租户不符同样跳过（防跨租户误删）。
     * </p>
     */
    @EventHandler
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(InsuredMemberRemovedEvent event) {
        Optional<PolicyInsuredView> found = insuredViewRepository.findById(event.insuredId());
        if (found.isEmpty()) {
            log.warn("[参与方投影] 移除被保险人未命中读模型行，跳过: policyId={}, insuredId={}",
                    event.policyId(), event.insuredId());
            return;
        }
        PolicyInsuredView view = found.get();
        if (!Objects.equals(view.getTenantId(), event.tenantId())) {
            log.warn("[参与方投影] 移除被保险人租户不匹配，跳过: policyId={}, insuredId={}",
                    event.policyId(), event.insuredId());
            return;
        }
        insuredViewRepository.delete(view);
        log.info("[参与方投影] 移除被保险人: policyId={}, insuredId={}", event.policyId(), event.insuredId());
    }

    /**
     * 被保险人快照 → 读模型行（创建投影与新增成员投影共用，保证对象标识派生同源）
     */
    private PolicyInsuredView toInsuredView(String policyId, InsuredPartyList.InsuredInfo insured, int index,
                                            String tenantId, LocalDateTime now) {
        PolicyInsuredView view = new PolicyInsuredView();
        // 主键即保全集合字段的对象标识：读侧发布的对象标识必须与写侧执行器的解析函数同源，
        // 否则受理通过的被保险人类保全项在生效环节解析不到目标（详见 PolicyMaintenanceObjectId#insured）
        view.setId(PolicyMaintenanceObjectId.insured(policyId, insured, index).value());
        view.setPolicyId(policyId);
        view.setCustomerId(insured.customerId());
        view.setInsuredName(insured.name());
        view.setIdType(insured.certType() != null ? insured.certType().getCode() : null);
        view.setIdNo(insured.certNo());
        view.setAge(insured.age());
        view.setGender(insured.gender() != null ? insured.gender().getCode() : null);
        view.setPhone(insured.phone());
        view.setRelation(insured.relationToHolder());
        view.setFamilyRelation(insured.familyRelation() != null ? insured.familyRelation().getCode() : null);
        view.setTenantId(tenantId);
        view.setCreateTime(now);
        view.setUpdateTime(now);
        return view;
    }

}
