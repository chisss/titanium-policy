package com.titanium.policy.aggregate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.spring.stereotype.Aggregate;

import com.titanium.metadata.enums.policy.PolicyEnum;
import com.titanium.policy.command.CreatePolicyCommand;
import com.titanium.policy.entity.InsuranceProduct;
import com.titanium.policy.entity.PaymentRecord;
import com.titanium.policy.entity.Subject;
import com.titanium.policy.entity.insurance.InsuredPartyList;
import com.titanium.policy.event.PolicyCreatedEvent;
import com.titanium.policy.valueobject.DeductibleRule;
import com.titanium.policy.valueobject.PolicyBasicInfo;
import com.titanium.policy.valueobject.PolicyDocument;
import com.titanium.policy.valueobject.PolicyNo;
import com.titanium.policy.valueobject.PolicyRelation;
import com.titanium.policy.valueobject.PolicyStatus;
import com.titanium.policy.valueobject.PremiumPlan;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 正式保单聚合根
 * <p>
 * 作为保单域的最终聚合根，对外提供契约全生命周期服务，管理正式保单的生成、签发、状态更新等
 * </p>
 */
@Aggregate
@Getter
@Builder(builderMethodName = "builder")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Policy {
    /**
     * 聚合根唯一标识
     */
    @AggregateIdentifier
    private String                 policyId;
    /**
     * 保单号，对外唯一
     */
    private PolicyNo               policyNo;
    /**
     * 关联投保单ID
     */
    private String                 insuranceId;
    /**
     * 保单形态
     */
    private String                 policyForm;
    /**
     * 父保单ID
     */
    private String                 parentPolicyId;
    /**
     * 签发机构
     */
    private String                 issueOrg;
    /**
     * 创建时间
     */
    private LocalDateTime          createTime;
    /**
     * 签发时间
     */
    private LocalDateTime          issueTime;
    /**
     * 保单基本信息
     */
    private PolicyBasicInfo        basicInfo;
    /**
     * 保单关系
     */
    private PolicyRelation         policyRelation;
    /**
     * 投保险种列表
     */
    private List<InsuranceProduct> insuranceProducts;
    /**
     * 保险标的列表
     */
    private List<Subject>          subjects;
    /**
     * 保费计划
     */
    private PremiumPlan            premiumPlan;
    /**
     * 免赔规则列表
     */
    private List<DeductibleRule>   deductibleRules;
    /**
     * 投保参与方清单
     */
    private InsuredPartyList       insuredPartyList;
    /**
     * 缴费记录列表
     */
    private List<PaymentRecord>    paymentRecords;
    /**
     * 保单单证列表
     */
    private List<PolicyDocument>   policyDocuments;
    /**
     * 保单状态
     */
    private PolicyStatus           status;
    /**
     * 租户ID
     */
    private String                 tenantId;

    /**
     * 创建保单 - 命令处理器
     * <p>
     * 处理创建保单的命令
     * </p>
     *
     * @param command 创建保单命令
     */
    @CommandHandler
    public Policy(CreatePolicyCommand command) {
        this.policyId = command.policyId();
        this.policyNo = new PolicyNo(command.policyNo());
        this.insuranceId = command.insuranceId();
        this.policyForm = command.policyForm();
        this.issueOrg = command.issueOrg();
        this.tenantId = command.tenantId();
        this.createTime = LocalDateTime.now();
        this.insuranceProducts = new ArrayList<>();
        this.subjects = new ArrayList<>();
        this.deductibleRules = new ArrayList<>();
        this.paymentRecords = new ArrayList<>();
        this.policyDocuments = new ArrayList<>();
        this.status = new PolicyStatus(PolicyStatus.StatusCode.NOT_EFFECTIVE, LocalDateTime.now(), "创建保单", "SYSTEM");
        // 初始化保单关系
        this.policyRelation = new PolicyRelation(PolicyEnum.PolicyLevel.INDEPENDENT, null, 0, null);

        // 从命令中导入基本信息
        this.basicInfo = new PolicyBasicInfo(command.policyHolderId(), 0, command.premium(), command.startDate(),
                command.endDate(), 0, command.channel()

        );
        AggregateLifecycle.apply(new PolicyCreatedEvent(command.policyId(), new PolicyNo(command.policyNo()),
                command.startDate(), command.endDate(), command.premium(),
                new PolicyStatus(PolicyStatus.StatusCode.NOT_EFFECTIVE, LocalDateTime.now(), "创建保单", "SYSTEM"),
                new ArrayList<>(), command.tenantId()));
    }

    /**
     * 基于投保单+核保结果创建保单
     * <p>
     * 根据投保单和核保结果创建正式保单
     * </p>
     *
     * @param policyId 保单ID
     * @param policyNo 保单号
     * @param insuranceId 投保单ID
     * @param policyForm 保单形态
     * @param issueOrg 签发机构
     * @param basicInfo 保单基本信息
     * @param tenantId 租户ID
     * @return 保单聚合根实例
     */
    public static Policy createPolicy(String policyId, PolicyNo policyNo, String insuranceId, String policyForm,
                                      String issueOrg, PolicyBasicInfo basicInfo, String tenantId) {
        Policy policy = new Policy();
        policy.policyId = policyId;
        policy.policyNo = policyNo;
        policy.insuranceId = insuranceId;
        policy.policyForm = policyForm;
        policy.issueOrg = issueOrg;
        policy.basicInfo = basicInfo;
        policy.tenantId = tenantId;
        policy.createTime = LocalDateTime.now();
        policy.insuranceProducts = new ArrayList<>();
        policy.subjects = new ArrayList<>();
        policy.deductibleRules = new ArrayList<>();
        policy.paymentRecords = new ArrayList<>();
        policy.policyDocuments = new ArrayList<>();
        policy.status = new PolicyStatus(PolicyStatus.StatusCode.NOT_EFFECTIVE, LocalDateTime.now(), "创建保单", "SYSTEM");
        // 初始化保单关系
        policy.policyRelation = new PolicyRelation(PolicyEnum.PolicyLevel.INDEPENDENT, null, 0, null);
        return policy;
    }

    /**
     * 签发保单
     * <p>
     * 生成电子/纸质单证，更新保单状态为已签发
     * </p>
     *
     * @param operatorId 操作人ID
     */
    public void issuePolicy(String operatorId) {
        // 只有未生效状态的保单才能签发
        if (this.status.statusCode() != PolicyStatus.StatusCode.NOT_EFFECTIVE) {
            throw new IllegalArgumentException("Only not effective policies can be issued");
        }
        // 生成保单合同、电子凭证
        generateDocument();
        // 更新签发时间
        this.issueTime = LocalDateTime.now();
        // 更新保单状态
        this.status = this.status.transitionStatus(PolicyStatus.StatusCode.NOT_EFFECTIVE, "签发保单", operatorId);
    }

    /**
     * 更新保单状态
     * <p>
     * 联动保全/财务结果更新保单状态
     * </p>
     *
     * @param newStatusCode 新状态编码
     * @param changeReason 变更原因
     * @param operatorId 操作人ID
     */
    public void updatePolicyStatus(PolicyStatus.StatusCode newStatusCode, String changeReason, String operatorId) {
        // 更新保单状态
        this.status = this.status.transitionStatus(newStatusCode, changeReason, operatorId);
        // 如果是父保单状态变更，同步子保单状态
        if (this.policyRelation.policyLevel() == PolicyEnum.PolicyLevel.PARENT) {
            this.policyRelation.syncParentStatus(newStatusCode);
        }
    }

    /**
     * 关联子保单
     * <p>
     * 关联子保单，维护父子层级关系
     * </p>
     *
     * @param childPolicyId 子保单ID
     */
    public void linkSubPolicy(String childPolicyId) {
        // 只有父保单才能关联子保单
        if (this.policyRelation.policyLevel() != PolicyEnum.PolicyLevel.PARENT) {
            // 如果是独立保单，可以升级为父保单
            if (this.policyRelation.policyLevel() == PolicyEnum.PolicyLevel.INDEPENDENT) {
                this.policyRelation = new PolicyRelation(PolicyEnum.PolicyLevel.PARENT, null,
                        this.policyRelation.subPolicyCount() + 1, this.policyRelation.groupId());
            } else {
                throw new IllegalArgumentException("Only parent policies can link sub policies");
            }
        } else {
            // 父保单关联子保单，子保单数量递增
            this.policyRelation = new PolicyRelation(this.policyRelation.policyLevel(),
                    this.policyRelation.parentPolicyId(), this.policyRelation.subPolicyCount() + 1,
                    this.policyRelation.groupId());
        }
    }

    /**
     * 生成保单合同、电子凭证
     * <p>
     * 调用单证系统生成电子/纸质保单模板
     * </p>
     */
    private void generateDocument() {
        // 这里应该调用单证系统生成电子保单/纸质保单模板
        // 暂时创建一个简单的保单单证
        PolicyDocument document = new PolicyDocument("doc-" + LocalDateTime.now().toString(),
                "E-" + this.policyNo.value(), "P-" + this.policyNo.value(), LocalDateTime.now(),
                PolicyEnum.SignatureStatus.UNSIGNED, "http://docs.titanium.com/policies/" + this.policyNo.value());
        this.policyDocuments.add(document);
    }

    protected Policy() {

    }

    public void cancel() {
    }

    public void expire() {
    }

    public void suspend() {

    }

    public void activate() {
    }
}
