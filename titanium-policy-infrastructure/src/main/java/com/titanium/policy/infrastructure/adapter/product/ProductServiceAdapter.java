package com.titanium.policy.infrastructure.adapter.product;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.titanium.metadata.enums.insurance.InsuranceProductType;
import com.titanium.metadata.enums.insurance.SubjectType;
import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.enums.product.ProductEnum.IssuanceMode;
import com.titanium.metadata.enums.product.ProductEnum.PolicyFormType;
import com.titanium.metadata.errorcode.PolicyErrorCode;
import com.titanium.metadata.errorcode.SystemErrorCode;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.exception.PolicyBusinessRuleException;
import com.titanium.policy.exception.RemoteDependencyUnavailableException;
import com.titanium.policy.port.product.ProductServicePort;
import com.titanium.policy.valueobject.product.CoverageSumInsuredConfig;
import com.titanium.policy.valueobject.product.ProductBasicInfo;
import com.titanium.policy.valueobject.product.ProductClauseRef;
import com.titanium.policy.valueobject.product.ProductIssueRules;
import com.titanium.policy.valueobject.product.ProductSumInsuredTiers;
import com.titanium.product.api.ProductApi;
import com.titanium.product.api.ProductTemplateApi;
import com.titanium.product.api.response.clause.ProductClauseResponse;
import com.titanium.product.api.response.config.CoveragePeriodConfigResponse;
import com.titanium.product.api.response.config.CoverageSumInsuredConfigResponse;
import com.titanium.product.api.response.config.InsureConditionResponse;
import com.titanium.product.api.response.config.IssuanceProcessConfigResponse;
import com.titanium.product.api.response.config.PaymentConfigResponse;
import com.titanium.product.api.response.config.PolicyFormConfigResponse;
import com.titanium.product.api.response.config.UnderwritingConfigResponse;
import com.titanium.product.api.response.product.ProductResponse;
import com.titanium.product.api.response.product.ProductTemplateResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 产品服务适配器
 * <p>
 * {@link ProductServicePort} 的基础设施实现，调用产品域 {@link ProductApi}（Feign）并把产品域
 * Response 翻译为保单域防腐 record（六边形架构 Adapter）。
 * </p>
 * <p>
 * 🔴 <b>去 JSON 拆包</b>：改造前 Port 返回 {@code Object}，本适配器需 {@code JSON.parseObject}
 * 手工取 {@code issuanceMode} 字段。实测产品域 {@link ProductResponse} 各配置段<b>本就是强类型
 * record</b>，故改为直接字段映射，弱类型拆包与相应异常处理一并删除。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductServiceAdapter implements ProductServicePort {

    /**
     * JSON 解析器：**由 Spring 注入托管实例**（m22-03 / 审查报告 SPR-04）。
     * <p>原为字段初始化 {@code = new ObjectMapper()}——裸构造的实例不带 Spring 全局配置
     * （自定义 Module、命名策略、JavaTimeModule 等），与 REST 层/其它组件的序列化行为**不一致**：
     * 同一个时间字段在接口响应里是 ISO 字符串、在此处解析却可能失败或语义不同。</p>
     */
    private final ObjectMapper objectMapper;

    private final ProductApi productApi;
    private final ProductTemplateApi productTemplateApi;

    @Override
    public ProductBasicInfo getProductBasicInfo(String productId) {
        ProductResponse product = fetchProduct(productId);
        if (product == null) {
            return null;
        }
        return new ProductBasicInfo(product.getProductId(), product.getProductCode(), product.getProductName(),
                product.getVersion(), product.getInsuranceType(),
                product.getStatus() != null ? product.getStatus().getCode() : null,
                product.getInsuranceMandateType());
    }

    @Override
    public IssuanceMode getIssuanceMode(String productId) {
        ProductResponse product = fetchProduct(productId);
        IssuanceProcessConfigResponse config = product != null ? product.getIssuanceProcessConfig() : null;
        if (config != null && config.issuanceMode() != null) {
            log.info("产品出单模式: productId={}, mode={}, source=PRODUCT", productId, config.issuanceMode());
            return config.issuanceMode();
        }
        ProductTemplateResponse template = fetchTemplate(productId);
        if (template == null || template.getIssuanceMode() == null) {
            // 🔴 业务条件，非编程错误（审查报告 EXC-05）：productId 来自投保请求，调用方改选一个已配
            // 出单流程的产品即可继续，属可预期的业务拒绝。此前抛裸 IllegalStateException，而本方法的
            // 两个调用点（PolicyIssuanceApplicationService#issue、IssuanceOrchestrator#orchestrate）
            // 均无 try 包裹 ⇒ 异常逃逸至 web 兜底成为 500 + SYSTEM_ERROR，把「产品没配出单流程」
            // 这个可解释的业务事实，报成了「服务端出错了」。
            throw new PolicyBusinessRuleException(PolicyErrorCode.ISSUANCE_MODE_NOT_CONFIGURED,
                    String.format("产品[%s]及其模板均未配置出单流程，无法决定出单模式", productId));
        }
        log.info("产品出单模式: productId={}, mode={}, source=TEMPLATE", productId, template.getIssuanceMode());
        return template.getIssuanceMode();
    }

    @Override
    public ProductIssueRules getIssueRules(String productId) {
        ProductResponse product = fetchProduct(productId);
        if (product == null) {
            return null;
        }
        InsureConditionResponse condition = product.getInsureCondition();
        CoveragePeriodConfigResponse period = product.getCoveragePeriod();
        PaymentConfigResponse payment = product.getPaymentConfig();
        PolicyFormConfigResponse formConfig = product.getPolicyFormConfig();
        UnderwritingConfigResponse underwriting = product.getUnderwritingConfig();
        IssuanceProcessConfigResponse issuance = product.getIssuanceProcessConfig();
        ProductTemplateResponse template = fetchTemplate(productId);
        ProductTemplateResponse.PolicyStructureConfigDTO structure = template != null
                ? template.getPolicyStructure() : null;
        SubjectType subjectType = structure != null ? structure.getSubjectType() : inferSubjectType(product.getInsuranceType());

        return new ProductIssueRules(
                condition != null ? condition.minAge() : null,
                condition != null ? condition.maxAge() : null,
                condition != null ? condition.minInsuredAmount() : null,
                condition != null ? condition.maxInsuredAmount() : null,
                condition != null ? condition.maxInsuredCount() : null,
                condition != null ? condition.minGroupSize() : null,
                condition != null ? condition.forbiddenOccupations() : List.of(),
                condition != null ? condition.allowedRegions() : List.of(),
                condition != null ? condition.forbiddenRegions() : List.of(),
                condition != null ? condition.waitingPeriodDays() : null,
                condition != null ? condition.hesitationPeriodDays() : null,
                toPolicyForm(formConfig),
                formConfig != null && formConfig.beneficiaryRequired(),
                payment != null ? payment.allowedFrequencies() : List.of(),
                payment != null ? payment.allowedPaymentTerms() : List.of(),
                period != null ? period.periodUnit() : null,
                period != null ? period.fixedTermOptions() : List.of(),
                underwriting != null ? underwriting.underwritingMode() : null,
                underwriting != null ? underwriting.manualReviewAmountThreshold() : null,
                issuance != null && issuance.underwritingSkippable(),
                issuance != null && issuance.prepaymentRequired(), subjectType,
                requiredAttributes(structure != null ? structure.getSubjectFieldsSchema() : null),
                // G11/g11-06：多标的声明取自产品模板（structure 与前文 subjectType 同源）；
                // 无模板/无结构时传 null（三态「未声明 = 不限制」，见 ProductIssueRules.allowsMultipleSubjects）
                structure != null ? structure.isAllowMultipleSubjects() : null);
    }

    /** 从模板 JSON Schema 读取 required 数组；非法 Schema 按未配置约束处理并记录日志。 */
    private List<String> requiredAttributes(String schema) {
        if (schema == null || schema.isBlank() || "{}".equals(schema.trim())) {
            return List.of();
        }
        try {
            JsonNode required = objectMapper.readTree(schema).path("required");
            if (!required.isArray()) {
                return List.of();
            }
            List<String> attributes = new ArrayList<>();
            required.forEach(node -> {
                if (node.isTextual() && !node.asText().isBlank()) {
                    attributes.add(node.asText());
                }
            });
            return List.copyOf(attributes);
        } catch (Exception exception) {
            log.warn("产品标的 Schema 无法解析，按未配置必填属性处理: schema={}", schema, exception);
            return List.of();
        }
    }

    private SubjectType inferSubjectType(InsuranceProductType type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case AUTO -> SubjectType.VEHICLE;
            case ENTERPRISE_PROPERTY -> SubjectType.PROPERTY;
            case HOUSEHOLD_PROPERTY -> SubjectType.HOUSEHOLD;
            case MARINE_CARGO -> SubjectType.CARGO;
            default -> SubjectType.PERSON;
        };
    }

    @Override
    public ProductSumInsuredTiers getCoverageSumInsuredConfigs(String productId) {
        ApiResponse<List<CoverageSumInsuredConfigResponse>> response =
                productApi.getCoverageSumInsuredConfigs(productId);
        if (response == null) {
            throw new RemoteDependencyUnavailableException(SystemErrorCode.DEPENDENCY_SERVICE_ERROR,
                    "查询产品责任保额档次配置无响应: productId=" + productId);
        }
        if (!response.isSuccess()) {
            // 🔴 失败不折叠成空清单：折叠会把「服务不可用」静默变成「该产品没配档次」，
            // 使段级保额档次校验被整体绕过（G02/AC-09）。
            throw new RemoteDependencyUnavailableException(SystemErrorCode.DEPENDENCY_SERVICE_ERROR,
                    "获取产品责任保额档次配置失败: productId=" + productId + ", downstreamCode=" + response.getCode()
                            + ", message=" + response.getMessage());
        }
        // 查询成功但 data 为空 ⇒ 该产品未配置档次（对端契约：未配置返回空列表，非 404）
        List<CoverageSumInsuredConfigResponse> coverages =
                response.getData() != null ? response.getData() : List.of();
        return new ProductSumInsuredTiers(productId,
                coverages.stream().map(this::toCoverageSumInsuredConfig).toList());
    }

    @Override
    public List<ProductClauseRef> getClauseRefs(String productId, String tenantId) {
        ApiResponse<List<ProductClauseResponse>> response = productApi
                .getProductClauses(productId);
        if (response == null || !response.isSuccess() || response.getData() == null) {
            log.warn("产品未绑定条款或查询失败: productId={}, tenantId={}", productId, tenantId);
            return List.of();
        }
        return response.getData().stream()
                .map(rel -> new ProductClauseRef(rel.getClauseId(), rel.getClauseVersion(),
                        Boolean.TRUE.equals(rel.getMainClause())))
                .toList();
    }

    /**
     * 取产品详情并解包 {@link ApiResponse}（失败返回 null 由调用方按语义决定兜底）。
     */
    private ProductResponse fetchProduct(String productId) {
        ApiResponse<ProductResponse> response = productApi.getProductById(productId);
        if (response == null) {
            throw new RemoteDependencyUnavailableException(SystemErrorCode.DEPENDENCY_SERVICE_ERROR,
                    "查询产品详情无响应: productId=" + productId);
        }
        if (!response.isSuccess()) {
            // 🔴 m21-02：下游以失败码作答时不再折叠成 null。可能是「产品不存在」（业务）也可能是下游内部
            // 错误（技术），但二者对出单都意味着「拿不到权威投保规则」——统一按依赖不可用抛出，
            // 由编排层决定挂起或拒绝，绝不静默放行（改造前会把等待期/犹豫期置 0 继续出单）。
            throw new RemoteDependencyUnavailableException(SystemErrorCode.DEPENDENCY_SERVICE_ERROR,
                    "获取产品详情失败: productId=" + productId + ", downstreamCode=" + response.getCode()
                            + ", message=" + response.getMessage());
        }
        return response.getData();
    }

    /** 产品实例未覆盖行为时，读取其绑定模板的默认行为。 */
    private ProductTemplateResponse fetchTemplate(String productId) {
        ApiResponse<ProductTemplateResponse> response = productTemplateApi.getByProductId(productId);
        if (response == null) {
            throw new RemoteDependencyUnavailableException(SystemErrorCode.DEPENDENCY_SERVICE_ERROR,
                    "查询产品模板无响应: productId=" + productId);
        }
        if (!response.isSuccess()) {
            // 同上：失败不折叠。返回 null 仅用于「查询成功但没有模板」这一业务事实。
            throw new RemoteDependencyUnavailableException(SystemErrorCode.DEPENDENCY_SERVICE_ERROR,
                    "获取产品模板失败: productId=" + productId + ", downstreamCode=" + response.getCode()
                            + ", message=" + response.getMessage());
        }
        return response.getData();
    }

    /**
     * 产品保单形态类型 → 保单域保单形态枚举。
     * <p>
     * 二者是同一业务概念在两域的表述：产品侧 {@code PolicyFormType}（个单/团单/家庭单/联合保单）
     * 决定保单侧 {@code PolicyForm}。联合保单在保单域暂按个单处理（无独立形态语义）。
     * </p>
     */
    /**
     * 产品域责任档次 Response → 保单域防腐镜像。
     * <p>
     * 仅做结构翻译，不重复对端的不变量校验（档位单调、区间自洽等由产品域写入时守护）。
     * {@code allowCustomAmount} 用 {@code Boolean.TRUE.equals} 承接——对端为包装类型，
     * 缺失按「不开放自由填」处理（失败关闭）。
     * </p>
     */
    private CoverageSumInsuredConfig toCoverageSumInsuredConfig(CoverageSumInsuredConfigResponse response) {
        List<CoverageSumInsuredConfig.SumInsuredTier> tiers = response.tiers() == null ? List.of()
                : response.tiers().stream()
                        .map(tier -> new CoverageSumInsuredConfig.SumInsuredTier(tier.tierCode(), tier.sumInsured(),
                                tier.allowedTerms(), tier.sortNo()))
                        .toList();
        return new CoverageSumInsuredConfig(response.coverageCode(), tiers, response.minSumInsured(),
                response.maxSumInsured(), Boolean.TRUE.equals(response.allowCustomAmount()),
                response.defaultTierCode());
    }

    private PolicyForm toPolicyForm(PolicyFormConfigResponse formConfig) {
        if (formConfig == null || formConfig.policyFormType() == null) {
            return null;
        }
        PolicyFormType type = formConfig.policyFormType();
        return switch (type) {
            case INDIVIDUAL, JOINT -> PolicyForm.INDIVIDUAL;
            case GROUP -> PolicyForm.GROUP;
            case FAMILY -> PolicyForm.FAMILY;
        };
    }
}
