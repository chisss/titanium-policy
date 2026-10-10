package com.titanium.policy.web.mapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import com.titanium.metadata.enums.CurrencyEnum;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.api.model.Amount;
import com.titanium.policy.api.request.issuance.CreatePolicyRequest;
import com.titanium.policy.api.request.member.AddInsuredMemberRequest;
import com.titanium.policy.api.response.fieldcatalog.PolicySnapshotFieldValueResponse;
import com.titanium.policy.api.response.maintenance.PolicyMaintenanceSnapshotResponse;
import com.titanium.policy.api.response.policy.PolicyBeneficiaryResponse;
import com.titanium.policy.api.response.policy.PolicyClauseResponse;
import com.titanium.policy.api.response.policy.PolicyResponse;
import com.titanium.policy.api.response.policy.PolicyStatusResponse;
import com.titanium.policy.command.AddInsuredMemberCommand;
import com.titanium.policy.command.ApplyPolicyEndorsementCommand;
import com.titanium.policy.command.CreatePolicyCommand;
import com.titanium.policy.command.CreatePolicyDirectlyCommand;
import com.titanium.policy.command.DistributeDividendCommand;
import com.titanium.policy.command.MaturePolicyCommand;
import com.titanium.policy.command.RemoveInsuredMemberCommand;
import com.titanium.policy.command.StartAnnuityPayoutCommand;
import com.titanium.policy.command.WaivePremiumCommand;
import com.titanium.policy.common.enums.FamilyRelation;
import com.titanium.policy.entity.insurance.InsuredPartyList;
import com.titanium.policy.query.result.PolicyBeneficiaryQueryResult;
import com.titanium.policy.query.result.PolicyClauseQueryResult;
import com.titanium.policy.query.result.PolicyMaintenanceSnapshotQueryResult;
import com.titanium.policy.query.result.PolicyMaintenanceSnapshotQueryResult.PolicySnapshotFieldValueQueryResult;
import com.titanium.policy.query.result.PolicyQueryResult;
import com.titanium.policy.valueobject.policy.PolicyPeriod;
import com.titanium.policy.web.dto.issuance.CreatePolicyDTO;
import com.titanium.policy.web.dto.maintenance.ApplyEndorsementDTO;
import com.titanium.policy.web.dto.maintenance.DistributeDividendDTO;
import com.titanium.policy.web.dto.maintenance.MaturePolicyDTO;
import com.titanium.policy.web.dto.maintenance.StartAnnuityPayoutDTO;
import com.titanium.policy.web.dto.maintenance.WaivePremiumDTO;
import com.titanium.policy.web.dto.member.AddInsuredMemberDTO;
import com.titanium.policy.web.response.policy.PolicyDetailVO;

/**
 * 保单 Web 层对象映射器（MapStruct）
 * <p>
 * 把边界输入翻译成 CQRS 命令/查询的转换枢纽：HTTP {@code Request} → 领域命令 {@code CreatePolicyCommand}
 * （Controller 用）、远程 {@code DTO} → 领域命令（Provider 用）、读模型结果 → 展示 {@code VO}（Controller 用）、
 * 读模型结果 → 对外 {@code DTO}（Provider 用）。application 门面入参即领域命令，本映射器在 web 层完成
 * Request/DTO → Command 的结构翻译（{@code BigDecimal}+币种 → {@code Money}）。业务默认值仅作兜底，
 * 命令的业务完整性由聚合根保证。
 * </p>
 */
@Mapper(componentModel = "spring")
public interface PolicyWebMapper {

    /**
     * HTTP Request → 领域命令（Controller 用）
     * <p>
     * 租户ID 以请求头透传值为准；金额+币种组装为 {@code Money}。DTO 未承载的领域字段留空由聚合根兜底。
     * </p>
     *
     * @param request 创建保单请求
     * @param tenantId 租户ID（请求头）
     * @return 创建保单命令
     */
    @Mapping(target = "sumInsured", expression = "java(toMoney(request.getSumInsured(), request.getCurrency()))")
    @Mapping(target = "premium", expression = "java(toMoney(request.getPremium(), request.getCurrency()))")
    @Mapping(target = "policyPeriod", expression = "java(toPolicyPeriod(request.getStartDate(), request.getEndDate()))")
    @Mapping(target = "tenantId", source = "tenantId")
    @Mapping(target = "policyProducts", ignore = true)
    @Mapping(target = "collectionInfo", ignore = true)
    @Mapping(target = "channelInfo", ignore = true)
    @Mapping(target = "premiumPlan", ignore = true)
    CreatePolicyCommand toCommand(CreatePolicyDTO request, String tenantId);

    /**
     * HTTP Request → 一步出单命令（Controller 的 direct 端点用）
     * <p>
     * 🔴 本映射产出的命令<b>不含险种段</b>（{@code policyProducts} 留空），仅用于兼容既有的裸建单端点。
     * 完整的一单多险出单请自 {@code PolicyIssuanceApi} 提交——出单编排器会依产品配置装配段、
     * 条款与责任快照。裸建单产出的保单在读侧查不到险种/责任明细。
     * </p>
     *
     * @param request 创建保单请求
     * @param tenantId 租户ID（请求头）
     * @return 一步出单命令
     */
    @Mapping(target = "totalPremium", expression = "java(toMoney(request.getPremium(), request.getCurrency()))")
    @Mapping(target = "sumInsured", expression = "java(toMoney(request.getSumInsured(), request.getCurrency()))")
    @Mapping(target = "policyPeriod", expression = "java(toPolicyPeriod(request.getStartDate(), request.getEndDate()))")
    @Mapping(target = "tenantId", source = "tenantId")
    @Mapping(target = "policyProducts", ignore = true)
    @Mapping(target = "insuredPartyList", ignore = true)
    @Mapping(target = "collectionInfo", ignore = true)
    @Mapping(target = "channelInfo", ignore = true)
    @Mapping(target = "premiumPlan", ignore = true)
    @Mapping(target = "marketPackageId", ignore = true)
    CreatePolicyDirectlyCommand toDirectCommand(CreatePolicyDTO request, String tenantId);

    /**
     * 远程 DTO → 领域命令（Provider 用）
     * <p>
     * DTO 的嵌套金额 {@code premium} 拆解为 {@code Money}；DTO 未承载的字段（投保单/形态/机构/被保险人/保额/渠道）留空。
     * </p>
     *
     * @param dto 创建保单 DTO
     * @param tenantId 租户ID（请求头）
     * @return 创建保单命令
     */
    @Mapping(target = "policyNo", source = "dto.policyNumber")
    @Mapping(target = "premium", expression = "java(toMoney(amountValue(dto.getPremium()), amountCurrency(dto.getPremium())))")
    @Mapping(target = "policyPeriod", expression = "java(toPolicyPeriod(dto.getEffectiveDate(), dto.getExpiryDate()))")
    @Mapping(target = "tenantId", source = "tenantId")
    @Mapping(target = "insuranceId", ignore = true)
    @Mapping(target = "proposalId", ignore = true)
    @Mapping(target = "underwritingId", ignore = true)
    @Mapping(target = "marketPackageId", ignore = true)
    @Mapping(target = "policyForm", ignore = true)
    @Mapping(target = "productId", ignore = true)
    @Mapping(target = "issueOrg", ignore = true)
    @Mapping(target = "insuredPartyList", ignore = true)
    @Mapping(target = "policyProducts", ignore = true)
    @Mapping(target = "sumInsured", ignore = true)
    @Mapping(target = "premiumPlan", ignore = true)
    @Mapping(target = "collectionInfo", ignore = true)
    @Mapping(target = "channelInfo", ignore = true)
    CreatePolicyCommand toCommand(CreatePolicyRequest dto, String tenantId);

    /**
     * 远程 DTO → 一步出单命令（Provider 的 direct 端点用）
     * <p>
     * 一步出单不经投保单，直接创建并具备签发条件；DTO 未承载的领域字段留空由聚合根兜底。
     * </p>
     *
     * @param dto 创建保单 DTO
     * @param tenantId 租户ID（请求头）
     * @return 一步出单命令
     */
    @Mapping(target = "policyNo", source = "dto.policyNumber")
    @Mapping(target = "totalPremium", expression = "java(toMoney(amountValue(dto.getPremium()), amountCurrency(dto.getPremium())))")
    @Mapping(target = "policyPeriod", expression = "java(toPolicyPeriod(dto.getEffectiveDate(), dto.getExpiryDate()))")
    @Mapping(target = "tenantId", source = "tenantId")
    @Mapping(target = "policyForm", ignore = true)
    @Mapping(target = "productId", ignore = true)
    @Mapping(target = "sumInsured", ignore = true)
    @Mapping(target = "policyProducts", ignore = true)
    @Mapping(target = "insuredPartyList", ignore = true)
    @Mapping(target = "premiumPlan", ignore = true)
    @Mapping(target = "collectionInfo", ignore = true)
    @Mapping(target = "channelInfo", ignore = true)
    @Mapping(target = "marketPackageId", ignore = true)
    CreatePolicyDirectlyCommand toDirectCommand(CreatePolicyRequest dto, String tenantId);

    /**
     * 读模型结果 → 展示 VO（Controller 用）
     *
     * @param result 读模型查询结果
     * @return 保单详情 VO
     */
    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.ERROR)
    PolicyDetailVO toVO(PolicyQueryResult result);

    /**
     * 读模型结果 → 对外 DTO（Provider 用）
     *
     * @param result 读模型查询结果
     * @return 保单 DTO
     */
    @Mapping(target = "customerId", source = "policyHolderId")
    @Mapping(target = "productId", source = "productId")
    @Mapping(target = "premium", expression = "java(toAmount(result.getPremium(), result.getCurrency() != null ? result.getCurrency().name() : null))")
    @Mapping(target = "createdAt", source = "createTime")
    @Mapping(target = "updatedAt", source = "updateTime")
    @Mapping(target = "policyItems", ignore = true)
    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.ERROR)
    PolicyResponse toResponse(PolicyQueryResult result);

    /** Policy 权威快照读模型 → 正式 API 响应。 */
    PolicyMaintenanceSnapshotResponse toMaintenanceSnapshotResponse(
            PolicyMaintenanceSnapshotQueryResult result);

    /** Policy 快照字段值 → 正式 API 字段值。 */
    PolicySnapshotFieldValueResponse toSnapshotFieldValueResponse(
            PolicySnapshotFieldValueQueryResult result);

    /**
     * 读模型结果 → 保单状态 DTO（Provider 用）
     *
     * @param result 读模型查询结果
     * @return 保单状态 DTO
     */
    @Mapping(target = "status", expression = "java(result.getStatus() != null ? result.getStatus().name() : null)")
    PolicyStatusResponse toStatusResponse(PolicyQueryResult result);

    /**
     * 条款快照读模型 → Feign 契约（Provider 用，字段同名自动映射）
     *
     * @param result 条款快照查询结果
     * @return 保单条款响应
     */
    PolicyClauseResponse toClauseResponse(PolicyClauseQueryResult result);

    /**
     * 受益人读模型 → Feign 契约（Provider 用，字段同名自动映射）
     *
     * @param result 受益人查询结果
     * @return 保单受益人响应
     */
    PolicyBeneficiaryResponse toBeneficiaryResponse(PolicyBeneficiaryQueryResult result);

    /**
     * HTTP Request → 保费豁免命令（Controller 用）
     * <p>
     * 保单ID 取路径变量，操作人/租户ID 取请求头，豁免原因取请求体。
     * </p>
     *
     * @param request 保费豁免请求
     * @param policyId 保单ID
     * @param operatorId 操作人ID
     * @param tenantId 租户ID
     * @return 保费豁免命令
     */
    @Mapping(target = "policyId", source = "policyId")
    @Mapping(target = "reason", source = "request.reason")
    @Mapping(target = "operatorId", source = "operatorId")
    @Mapping(target = "tenantId", source = "tenantId")
    WaivePremiumCommand toWaivePremiumCommand(WaivePremiumDTO request, String policyId, String operatorId,
                                              String tenantId);

    /**
     * HTTP Request → 红利派发命令（Controller 用）
     *
     * @param request 红利派发请求
     * @param policyId 保单ID
     * @param operatorId 操作人ID
     * @param tenantId 租户ID
     * @return 红利派发命令
     */
    @Mapping(target = "policyId", source = "policyId")
    @Mapping(target = "dividendAmount", source = "request.dividendAmount")
    @Mapping(target = "option", source = "request.option")
    @Mapping(target = "policyYear", source = "request.policyYear")
    @Mapping(target = "operatorId", source = "operatorId")
    @Mapping(target = "tenantId", source = "tenantId")
    DistributeDividendCommand toDistributeDividendCommand(DistributeDividendDTO request, String policyId,
                                                          String operatorId, String tenantId);

    /**
     * HTTP Request → 启动年金给付命令（Controller 用）
     * <p>
     * 每期给付金额+币种组装为 {@code Money}；保单ID 取路径变量，操作人/租户ID 取请求头。
     * </p>
     *
     * @param request 启动年金给付请求
     * @param policyId 保单ID
     * @param operatorId 操作人ID
     * @param tenantId 租户ID
     * @return 启动年金给付命令
     */
    @Mapping(target = "policyId", source = "policyId")
    @Mapping(target = "startDate", source = "request.startDate")
    @Mapping(target = "frequency", source = "request.frequency")
    @Mapping(target = "amountPerInstallment", expression = "java(toMoney(request.getAmountPerInstallment(), request.getCurrency()))")
    @Mapping(target = "totalInstallments", source = "request.totalInstallments")
    @Mapping(target = "operatorId", source = "operatorId")
    @Mapping(target = "tenantId", source = "tenantId")
    StartAnnuityPayoutCommand toStartAnnuityPayoutCommand(StartAnnuityPayoutDTO request, String policyId,
                                                          String operatorId, String tenantId);

    /**
     * HTTP Request → 满期给付命令（Controller 用）
     *
     * @param request 满期给付请求
     * @param policyId 保单ID
     * @param operatorId 操作人ID
     * @param tenantId 租户ID
     * @return 满期给付命令
     */
    @Mapping(target = "policyId", source = "policyId")
    @Mapping(target = "maturityBenefit", source = "request.maturityBenefit")
    @Mapping(target = "operatorId", source = "operatorId")
    @Mapping(target = "tenantId", source = "tenantId")
    MaturePolicyCommand toMaturePolicyCommand(MaturePolicyDTO request, String policyId, String operatorId,
                                              String tenantId);

    /**
     * HTTP Request → 应用保单批改命令（Controller 用）
     *
     * @param request 申请批改请求
     * @param policyId 保单ID
     * @param operatorId 操作人ID
     * @param tenantId 租户ID
     * @return 应用保单批改命令
     */
    @Mapping(target = "policyId", source = "policyId")
    @Mapping(target = "endorsementNo", source = "request.endorsementNo")
    @Mapping(target = "updateType", source = "request.updateType")
    @Mapping(target = "endorsementEffectiveDate", source = "request.endorsementEffectiveDate")
    @Mapping(target = "changeSummary", source = "request.changeSummary")
    @Mapping(target = "originalSnapshot", source = "request.originalSnapshot")
    @Mapping(target = "sourceMaintenanceId", source = "request.sourceMaintenanceId")
    @Mapping(target = "operatorId", source = "operatorId")
    @Mapping(target = "tenantId", source = "tenantId")
    ApplyPolicyEndorsementCommand toApplyEndorsementCommand(ApplyEndorsementDTO request, String policyId,
                                                            String operatorId, String tenantId);

    /**
     * HTTP DTO → 新增被保险人命令（Controller 用，团单加保 / 家庭险增员）
     * <p>
     * 家庭成员关系直接用域枚举（同 {@code ApplyEndorsementDTO.updateType} 直映射约定）。
     * </p>
     *
     * @param request 新增被保险人请求
     * @param policyId 保单ID
     * @param operatorId 操作人ID
     * @param tenantId 租户ID
     * @return 新增被保险人命令
     */
    @Mapping(target = "policyId", source = "policyId")
    @Mapping(target = "member", source = "request")
    @Mapping(target = "familyRelation", source = "request.familyRelation")
    @Mapping(target = "operatorId", source = "operatorId")
    @Mapping(target = "tenantId", source = "tenantId")
    AddInsuredMemberCommand toAddInsuredMemberCommand(AddInsuredMemberDTO request, String policyId,
                                                      String operatorId, String tenantId);

    /**
     * 远程 Request → 新增被保险人命令（Provider 用，团单加保 / 家庭险增员）
     * <p>
     * api 层零内部依赖：家庭成员关系以 code 字符串承载，在 web 层映射器翻译为域枚举。
     * </p>
     *
     * @param request 新增被保险人请求（Feign 契约）
     * @param policyId 保单ID
     * @param operatorId 操作人ID
     * @param tenantId 租户ID
     * @return 新增被保险人命令
     */
    @Mapping(target = "policyId", source = "policyId")
    @Mapping(target = "member", source = "request")
    @Mapping(target = "familyRelation", expression = "java(toFamilyRelation(request.familyRelation()))")
    @Mapping(target = "operatorId", source = "operatorId")
    @Mapping(target = "tenantId", source = "tenantId")
    AddInsuredMemberCommand toAddInsuredMemberCommand(AddInsuredMemberRequest request, String policyId,
                                                      String operatorId, String tenantId);

    /**
     * HTTP DTO → 被保险人快照（新增成员命令的成员段）
     *
     * @param request 新增被保险人请求
     * @return 被保险人快照（聚合内实体）
     */
    InsuredPartyList.InsuredInfo toInsuredInfo(AddInsuredMemberDTO request);

    /**
     * 远程 Request → 被保险人快照（新增成员命令的成员段）
     *
     * @param request 新增被保险人请求（Feign 契约）
     * @return 被保险人快照（聚合内实体）
     */
    @Mapping(target = "familyRelation", expression = "java(toFamilyRelation(request.familyRelation()))")
    InsuredPartyList.InsuredInfo toInsuredInfo(AddInsuredMemberRequest request);

    /**
     * 移除被保险人命令（Controller/Provider 共用，全标量入参，reason 可空）
     *
     * @param policyId 保单ID
     * @param insuredId 被移除的被保险人ID
     * @param reason 移除原因
     * @param operatorId 操作人ID
     * @param tenantId 租户ID
     * @return 移除被保险人命令
     */
    @Mapping(target = "policyId", source = "policyId")
    @Mapping(target = "insuredId", source = "insuredId")
    @Mapping(target = "reason", source = "reason")
    @Mapping(target = "operatorId", source = "operatorId")
    @Mapping(target = "tenantId", source = "tenantId")
    RemoveInsuredMemberCommand toRemoveInsuredMemberCommand(String policyId, String insuredId, String reason,
                                                            String operatorId, String tenantId);

    /**
     * 家庭成员关系 code → 域枚举（空安全）
     * <p>
     * api 层零内部依赖，家庭关系以 code 字符串承载，翻译点固定在 web 层映射器。
     * </p>
     */
    default FamilyRelation toFamilyRelation(String code) {
        return code == null || code.isBlank() ? null : FamilyRelation.fromCode(code);
    }

    /**
     * BigDecimal + 币种 → Money 值对象（空安全，缺省币种 CNY）
     */
    default Money toMoney(BigDecimal value, String currency) {
        return value != null ? Money.of(value, currency != null ? currency : CurrencyEnum.CNY.getCode()) : null;
    }

    /**
     * 起止期 → 保单期间值对象（空安全）
     * <p>
     * 保单期间仅承载保障起止期；等待期/犹豫期自 G11/g11-04（D2 裁决）<b>下沉段级</b>，随各险种段
     * {@code LineCoveragePeriod} 按段装配（产品 {@code InsureCondition} 配置经出单编排器写入段上）。
     * </p>
     */
    default PolicyPeriod toPolicyPeriod(LocalDateTime start, LocalDateTime end) {
        return start == null && end == null ? null : PolicyPeriod.of(start, end);
    }

    /**
     * 取金额 DTO 的数值（空安全）
     */
    default BigDecimal amountValue(Amount amount) {
        return amount != null ? amount.getValue() : null;
    }

    /**
     * 取金额 DTO 的币种（空安全）
     */
    default String amountCurrency(Amount amount) {
        return amount != null ? amount.getCurrency() : null;
    }

    /**
     * 数值 + 币种 → 金额 DTO（空安全）
     */
    default Amount toAmount(Double value, String currency) {
        if (value == null) {
            return null;
        }
        Amount amount = new Amount();
        amount.setValue(BigDecimal.valueOf(value));
        amount.setCurrency(currency);
        return amount;
    }
}
