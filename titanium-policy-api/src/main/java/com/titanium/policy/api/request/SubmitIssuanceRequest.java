package com.titanium.policy.api.request;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 提交出单请求（Feign 契约）
 * <p>
 * 统一出单入口的入参。字段按<b>单据级 vs 段级</b>分层：投保人、受益人、收费方式、保单主期间是
 * 单据级（跨段共用）；保额、保障期间、缴费条件、标的是段级（各段独立，见 {@link PlanLine}）。
 * </p>
 * <p>
 * 出单模式（一步/两步/三步）<b>由主险产品配置决定</b>，调用方不指定——这是产品驱动出单的核心。
 * </p>
 */
@Schema(description = "提交出单请求")
@Data
public class SubmitIssuanceRequest {

    @Schema(description = "调用方业务流水号（同租户内唯一，重复提交返回首次结果）", example = "BIZ20260807001",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String              bizNo;

    @Schema(description = "营销包ID（弱引用，可空）", example = "PKG-FAMILY-2026")
    private String              marketPackageId;

    @Schema(description = "出单策略：MERGE_ONE_POLICY 合并为一张多险种保单（缺省）/ SPLIT_MULTI_POLICY 拆分为多张保单",
            example = "MERGE_ONE_POLICY")
    private String              issuanceStrategy;

    @Schema(description = "发起投保的注册用户ID（后台代录场景可空）", example = "USER-001")
    private String              userId;

    @Schema(description = "渠道ID（指向 channel 域）", example = "CH-ONLINE-001")
    private String              channelId;

    @Schema(description = "销售渠道大类码（AGENT/BANCASSURANCE/ONLINE/BROKER/TELEMARKETING/GROUP_SALES）",
            example = "ONLINE")
    private String              salesChannel;

    @Schema(description = "代理人/业务员ID", example = "AGENT-001")
    private String              agentId;

    @Schema(description = "投保人（单据级）", requiredMode = Schema.RequiredMode.REQUIRED)
    private PartyInput          holder;

    @Schema(description = "被保险人列表（单据级，至少一人）", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<PartyInput>    insuredList;

    @Schema(description = "受益人列表（单据级；空则视为法定继承，非空则同顺位份额和须为 100）")
    private List<PartyInput>    beneficiaryList;

    @Schema(description = "保单主保障起期", example = "2026-09-01T00:00:00")
    private LocalDateTime       periodStart;

    @Schema(description = "保单主保障止期", example = "2027-08-31T23:59:59")
    private LocalDateTime       periodEnd;

    @Schema(description = "收费方式码（OFFLINE 线下 / ONLINE 线上 / FREE 免支付 / PAY_AFTER_USE 先享后付 / WITHHOLD 代扣）",
            example = "ONLINE", requiredMode = Schema.RequiredMode.REQUIRED)
    private String              collectionMode;

    @Schema(description = "保单形态码（缺省由主险产品配置推导）", example = "INDIVIDUAL")
    private String              policyForm;

    @Schema(description = "上游报价（仅用于与系统试算比对，不作为保费真相）")
    private BigDecimal          quotedPremium;

    @Schema(description = "币种（缺省 CNY）", example = "CNY")
    private String              currency;

    @Schema(description = "出单方案行列表（险种段级要素，至少一个 productCategory=MAIN）",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private List<PlanLine>      planLines;

    @Schema(description = "单据级扩展要素（本期透传存档）")
    private Map<String, Object> extendData;

    /**
     * 参与方输入（投保人 / 被保险人 / 受益人共用）
     * <p>
     * 有 {@code customerId} 时直接复用客户主数据；否则由 customer 域按「租户 + 证件类型 + 证件号」
     * 幂等 upsert 后回填。
     * </p>
     */
    @Schema(description = "参与方输入")
    @Data
    public static class PartyInput {

        @Schema(description = "客户主数据ID（已有客户时传此项，其余字段可省）", example = "CUST-001")
        private String     customerId;

        @Schema(description = "姓名", example = "张三")
        private String     name;

        @Schema(description = "证件类型码（CHINA_ID_CARD 等）", example = "CHINA_ID_CARD")
        private String     certType;

        @Schema(description = "证件号", example = "310101199001011234")
        private String     certNo;

        @Schema(description = "性别码（MALE/FEMALE/UNKNOWN）", example = "MALE")
        private String     gender;

        @Schema(description = "出生日期（用于推算投保年龄）", example = "1990-01-01T00:00:00")
        private LocalDateTime birthDate;

        @Schema(description = "年龄（直接传入时优先于出生日期推算）", example = "35")
        private Integer    age;

        @Schema(description = "手机号", example = "13800138000")
        private String     mobile;

        @Schema(description = "与投保人关系码（被保险人/受益人用，可保利益校验依据）", example = "SELF")
        private String     relationToHolder;

        @Schema(description = "家庭成员关系码（家庭险专属）", example = "SPOUSE")
        private String     familyRelation;

        @Schema(description = "受益人类型码（DEATH 身故受益人 / SURVIVAL 生存受益人，受益人专属）", example = "DEATH")
        private String     beneficiaryType;

        @Schema(description = "受益顺位（1=第一顺位，受益人专属）", example = "1")
        private Integer    beneficiaryOrder;

        @Schema(description = "受益份额百分比（同顺位内合计须为 100，受益人专属）", example = "100")
        private BigDecimal shareRatio;
    }

    /**
     * 出单方案行（一个险种段的投保意图）
     * <p>
     * 一单多险时保额、保障期间、缴费条件、标的全部是「按险种段」而非「按保单」的量——
     * 「主险 20 年缴 50 万 + 附加重疾 1 年期 30 万」无法用扁平字段表达。
     * </p>
     */
    @Schema(description = "出单方案行（险种段）")
    @Data
    public static class PlanLine {

        @Schema(description = "段序号（保单内唯一，附加险以此关联主险）", example = "1",
                requiredMode = Schema.RequiredMode.REQUIRED)
        private Integer             lineNo;

        @Schema(description = "产品ID", example = "PROD-MED-001", requiredMode = Schema.RequiredMode.REQUIRED)
        private String              productId;

        @Schema(description = "产品类别码（MAIN 主险 / RIDER 附加险）", example = "MAIN",
                requiredMode = Schema.RequiredMode.REQUIRED)
        private String              productCategory;

        @Schema(description = "依附的主险段序号（附加险必填）", example = "1")
        private Integer             parentLineNo;

        @Schema(description = "本险种投保保额", example = "4000000", requiredMode = Schema.RequiredMode.REQUIRED)
        private BigDecimal          sumInsured;

        @Schema(description = "保障期限数值（如 20 表示 20 年；与保单主期间二选一）", example = "1")
        private Integer             coveragePeriodValue;

        @Schema(description = "保障期限单位码（YEAR/MONTH/DAY）", example = "YEAR")
        private String              coveragePeriodUnit;

        @Schema(description = "缴费频率码（LUMP_SUM 趸缴/ANNUAL 年缴/SEMI_ANNUAL/QUARTERLY/MONTHLY）",
                example = "ANNUAL", requiredMode = Schema.RequiredMode.REQUIRED)
        private String              paymentFrequency;

        @Schema(description = "缴费年数（缴费期 ≠ 保障期）", example = "1")
        private Integer             premiumPaymentYears;

        @Schema(description = "本险种承保标的（人身类可省，缺省取被保险人清单）")
        private List<SubjectInput>  subjects;

        @Schema(description = "险种段扩展要素（健康告知答案、验车结论等，本期透传存档）")
        private Map<String, Object> extendData;
    }

    /**
     * 标的输入
     * <p>
     * 人身类标的（寿险/医疗/意外）传 {@code customerId}；物类标的（车辆/财产/货物）传
     * {@code attributes}，其字段结构由产品 {@code subjectFieldsSchema} 校验。
     * </p>
     */
    @Schema(description = "标的输入")
    @Data
    public static class SubjectInput {

        @Schema(description = "标的类型码（PERSON/VEHICLE/PROPERTY/CARGO/VESSEL/AIRCRAFT/AGRICULTURAL 等）",
                example = "PERSON", requiredMode = Schema.RequiredMode.REQUIRED)
        private String              subjectType;

        @Schema(description = "客户主数据ID（人身类标的必填）", example = "CUST-002")
        private String              customerId;

        @Schema(description = "标的名称（车牌号 / 厂房名称；人身类可省）", example = "沪A12345")
        private String              subjectName;

        @Schema(description = "本标的保额（多车/多分项时各不同；单标的可省，取段保额）", example = "500000")
        private BigDecimal          subjectSumInsured;

        @Schema(description = "与投保人关系码（可保利益校验用）", example = "SELF")
        private String              relationToHolder;

        @Schema(description = "标的属性包（物类标的必填：车险传 VIN/初登日期/NCD，企财险传建筑结构/消防等级）")
        private Map<String, Object> attributes;
    }
}
