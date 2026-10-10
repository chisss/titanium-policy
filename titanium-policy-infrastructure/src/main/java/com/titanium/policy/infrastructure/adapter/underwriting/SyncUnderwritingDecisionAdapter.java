package com.titanium.policy.infrastructure.adapter.underwriting;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import com.titanium.common.context.RequestContextHolder;
import com.titanium.common.exception.BusinessException;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum.ConclusionType;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.port.underwriting.UnderwritingDecisionGateway;
import com.titanium.policy.valueobject.insurance.UnderwritingDecisionRequest;
import com.titanium.policy.valueobject.insurance.UnderwritingResult;
import com.titanium.policy.valueobject.issuance.HealthDeclarationInput;
import com.titanium.underwriting.api.UnderwritingApi;
import com.titanium.underwriting.api.request.underwriting.AutoDecideUnderwritingRequest;
import com.titanium.underwriting.api.response.underwriting.UnderwritingResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 核保决策网关同步适配器（粗粒度一次调用）
 * <p>
 * {@link UnderwritingDecisionGateway} 的当前阶段实现：经 {@link UnderwritingApi}（Feign）同步调用核保域
 * 的**粗粒度自动决策端点**，一次远程调用完成「创建/幂等复用核保单 → 提交风险要素 → 出具决策」，
 * 再将核保域 {@link UnderwritingResponse} 翻译为保单域 {@link UnderwritingResult}，构成防腐层（ACL）。
 * </p>
 * <p>
 * <b>g02-04 收敛（AC-05）</b>：此前本适配器自行拼装**四步**远程写（{@code getUnderwritingByPolicyId} 查重 →
 * {@code createUnderwriting} → {@code submitInput} → {@code decide}），并因此产生两处跨域语义错配：
 * <ol>
 *   <li><b>标识错配</b>：把投保单号写进核保域的 {@code policyId}（「保单号」）字段，而保全核保路径写入的
 *       {@code policyId} 是**真实保单号**——同一字段两种语义，按它做幂等键会错配；</li>
 *   <li><b>幂等靠查重</b>：幂等依赖「先查后建」，而查询读的是**最终一致**的读模型，四次远程写之间任一环节
 *       重试都会多建孤儿核保单。</li>
 * </ol>
 * 现改为一次 {@code autoDecide}：幂等键（投保单号）随请求进入核保域，由其<b>同事务内</b>判定
 * 「复用终态 / 续跑在办 / 新建」，判据是写侧权威状态而非异步投影。本适配器不再持有任何编排职责，
 * 只做「组装请求 → 调一次 → 翻译结论」。
 * </p>
 * <p>
 * <b>G02/AC-01 四要素透传（保留）</b>：年龄/性别/职业类别/BMI 四项要素仍在同一请求内透传。
 * 🔴 <b>未提供的要素一律不写入</b>——不得用 {@code 0}／空串占位，核保域据此区分
 * 「未提供」与「提供了 0 岁/0 类职业」；写假值会让核保域把「无输入」误判成「低风险输入」。
 * </p>
 * <p>
 * <b>G12/g12-01 AC-01 健康告知透传</b>：{@code healthDeclaration} 由出单 Saga 从投保险种段的段级
 * {@code extendData} 提取后随本请求抵达，本适配器翻译为核保契约的嵌套对象；未提供（{@code null}）时
 * 一律不写入——口径与四要素一致，不得以「不吸烟/默认身高体重」代填。
 * </p>
 * <p>
 * <b>UW-3 加费回传（保留）</b>：从决策响应读取结构化加费率 {@code extraPremiumRatio} 填入
 * {@link UnderwritingResult}，供出单 Saga 并入保费。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SyncUnderwritingDecisionAdapter implements UnderwritingDecisionGateway {

    /**
     * 系统主体标识：核保决策由本域自动发起，操作人是**系统**而非投保人。
     * <p>m21-03（审查报告 RPC-02）：改造前三处审计字段一律填 {@code holderId}，
     * 使核保记录显示「投保人本人提交了核保，并且本人做出了核保决策」——审计留痕失真
     * （NIST SP 800-53 AU-3/AU-10：审计记录须写明真实主体）。</p>
     */
    private static final String SYSTEM_OPERATOR = "SYSTEM_AUTO_UNDERWRITING";

    private final UnderwritingApi underwritingApi;

    @Override
    public UnderwritingResult requestDecision(UnderwritingDecisionRequest request) {
        log.info("[核保网关] 同步请求粗粒度自动决策, insuranceId={}, holderId={}, tenantId={}", request.insuranceId(),
                request.holderId(), request.tenantId());

        // 一次远程调用完成「创建/幂等复用 + 提交输入 + 出具决策」：
        // 幂等键为投保单号，由核保域在写侧权威状态下判定复用/续跑/新建（本域不再查读模型做幂等前置）
        ApiResponse<UnderwritingResponse> decisionResponse = underwritingApi
                .autoDecide(buildAutoDecideRequest(request));
        UnderwritingResponse decided = requireSuccessfulBody(decisionResponse, "自动决策", request);
        String decidedUnderwritingId = requireUnderwritingId(decided, "自动决策", request);

        // 核保域结果翻译为保单域核保结果（防腐层），携带结构化加费率
        ConclusionType resultCode = resolveConclusion(decided, request);
        BigDecimal extraPremiumRatio = decided.getExtraPremiumRatio();
        log.info("[核保网关] 自动决策完成, insuranceId={}, underwritingId={}, 结论={}, 加费率={}", request.insuranceId(),
                decidedUnderwritingId, resultCode, extraPremiumRatio);

        return new UnderwritingResult(decidedUnderwritingId, resultCode, decided.getReviewComments(),
                decided.getUnderwriterId(), LocalDateTime.now(), decided.getSurchargeReason(), extraPremiumRatio);
    }

    /**
     * 构建粗粒度自动决策请求（G02/AC-01 四要素 + 幂等键）
     * <p>
     * 🔴 <b>null 一律不写入</b>：{@code setAge(null)} 与「不调用 setAge」在 JSON 序列化上等价，
     * 但显式判空表达了「该要素未提供」的业务语义，且防止后续维护者顺手补默认值（如 {@code 0}）
     * 把「无输入」变成「低风险输入」。
     * </p>
     * <p>
     * 🔴 <b>不再写 {@code policyId}</b>：投保单号只进 {@code insuranceId}（核保域 g02-04 新增的
     * 幂等键字段），核保域按「加新字段不动旧字段」口径自行双填兼容既有分区键——把本域的内部标识
     * 塞进对端字段的旧做法至此终止。
     * </p>
     */
    private AutoDecideUnderwritingRequest buildAutoDecideRequest(UnderwritingDecisionRequest request) {
        AutoDecideUnderwritingRequest autoRequest = new AutoDecideUnderwritingRequest();
        // 幂等键：同一投保单在重试/重入下必然映射到同一张核保单
        autoRequest.setInsuranceId(request.insuranceId());
        autoRequest.setCustomerId(request.holderId());
        autoRequest.setAmount(request.sumInsured());
        autoRequest.setCurrency(request.currency());
        autoRequest.setUnderwritingType(UnderwritingEnum.UnderwritingType.NEW_BUSINESS);
        // 核保决策由系统自动作出，主体是系统而非投保人
        autoRequest.setOperatorId(resolveOperatorId());
        // 险种编码取投保险种列表首个（承保主险载体），供核保域按产品查核保配置
        autoRequest.setProductCode(resolveProductCode(request));

        // 年龄：投保单被保人参与方清单直接提供（承保前置链路可得）
        if (request.primaryInsuredAge() != null) {
            autoRequest.setAge(request.primaryInsuredAge());
        }

        // 性别：同上；核保域只承载不评分（评级因子 vs 风险因子，见 InsuredRiskFactors javadoc）
        if (request.primaryInsuredGender() != null) {
            autoRequest.setGender(request.primaryInsuredGender());
        }

        // 职业类别：柜面/APP 完整录入才有，出单主链路通常取不到（意外险/定期寿险风险要素）
        if (request.primaryInsuredOccupationCategory() != null) {
            autoRequest.setOccupationCategory(request.primaryInsuredOccupationCategory());
        }

        // BMI：同上（寿险/重疾健康风险要素）
        if (request.primaryInsuredBmi() != null) {
            autoRequest.setBmi(request.primaryInsuredBmi());
        }

        // 健康告知（G12/g12-01 AC-01）：段级 extendData 提供，未提供时不写入（核保域按无告知处理）
        if (request.healthDeclaration() != null) {
            autoRequest.setHealthDeclaration(toAutoDecideHealth(request.healthDeclaration()));
        }

        return autoRequest;
    }

    /**
     * 健康告知翻译（保单域值对象 → 核保域契约，防腐层）
     * <p>
     * 保单域 {@link HealthDeclarationInput} 与核保契约嵌套对象字段逐项对齐（G12/g12-01 实测形态：
     * 病史两项为字符串列表、吸烟为布尔、身高体重为十进制）。
     * 保单域侧已保证「三个必答项齐全」才产出非 null 值，核保域 {@code HealthDeclaration} 紧凑构造器
     * 仍会对其强校验（身高/体重必须为正，非法即拒收）——两域各自守住自己的边界。
     * </p>
     */
    private AutoDecideUnderwritingRequest.HealthDeclarationInput toAutoDecideHealth(
            HealthDeclarationInput declaration) {
        AutoDecideUnderwritingRequest.HealthDeclarationInput input =
                new AutoDecideUnderwritingRequest.HealthDeclarationInput();
        input.setMedicalHistory(declaration.medicalHistory());
        input.setFamilyHistory(declaration.familyHistory());
        input.setSmoking(declaration.smoking());
        input.setHeightCm(declaration.heightCm());
        input.setWeightKg(declaration.weightKg());
        return input;
    }

    /**
     * 解包统一信封（m24-01a 起核保契约统一返回 {@code ApiResponse}）。
     * <p>
     * 🔴 错误信封（2xx + 错误码）必须显式失败，不得并入「结论缺失」——否则契约失败会被读成业务事实。
     * </p>
     */
    private UnderwritingResponse requireSuccessfulBody(ApiResponse<UnderwritingResponse> response, String stage,
                                                        UnderwritingDecisionRequest request) {
        if (response == null) {
            throw new BusinessException(stage + "失败: insuranceId=" + request.insuranceId() + ", 无响应");
        }
        if (!response.isSuccess()) {
            throw new BusinessException(stage + "失败: insuranceId=" + request.insuranceId() + ", 错误码="
                    + response.getCode() + ", " + response.getMessage());
        }
        if (response.getData() == null) {
            throw new BusinessException(stage + "响应体为空: insuranceId=" + request.insuranceId());
        }
        return response.getData();
    }

    /**
     * 提取核保单ID并拒绝无效响应。
     */
    private String requireUnderwritingId(UnderwritingResponse response, String stage,
                                         UnderwritingDecisionRequest request) {
        if (response.getUnderwritingId() == null || response.getUnderwritingId().isBlank()) {
            throw new BusinessException(stage + "核保单ID为空: insuranceId=" + request.insuranceId());
        }
        return response.getUnderwritingId();
    }

    /**
     * 新契约直接采用业务结论；兼容旧版仅返回流程状态的核保响应。
     */
    private ConclusionType resolveConclusion(UnderwritingResponse response, UnderwritingDecisionRequest request) {
        if (response.getConclusionType() != null) {
            return response.getConclusionType();
        }
        if (response.getStatus() != null) {
            return mapToResultCode(response.getStatus());
        }
        throw new BusinessException("核保结论为空: insuranceId=" + request.insuranceId() + ", underwritingId="
                + response.getUnderwritingId());
    }

    /**
     * 取真实操作人；上下文无操作人时回落系统主体。
     * <p>🔴 绝不回落 {@code holderId}：把投保人记为操作人正是本次修复要消除的审计失真。
     * 用「回落系统主体」而非 {@code requireOperatorId()} 硬失败，是为兼容事件驱动的自动出单
     * （该场景确无人工操作人，业务上应由系统主体承担）。</p>
     */
    private String resolveOperatorId() {
        String operatorId = RequestContextHolder.getOperatorId();
        return operatorId != null && !operatorId.isBlank() ? operatorId : SYSTEM_OPERATOR;
    }

    /**
     * 解析险种编码（取投保险种编码列表首个作为承保主险载体）
     */
    private String resolveProductCode(UnderwritingDecisionRequest request) {
        return request.productCodes() != null && !request.productCodes().isEmpty()
                ? request.productCodes().get(0)
                : null;
    }

    /**
     * 核保域状态 → 保单域核保结论映射（防腐层翻译）
     * <p>
     * 标准/通过承保视为接受(ACCEPT)；加费承保视为修改条件承保(MODIFY)；
     * 拒保/拒绝视为拒绝(REJECT)；其余（待核保/人工审核/延期等）视为延期(POSTPONE)。
     * </p>
     */
    private ConclusionType mapToResultCode(UnderwritingEnum.UnderwritingStatus status) {
        return switch (status) {
            case APPROVED, STANDARD -> ConclusionType.ACCEPT;
            case RATED, EXCLUDED -> ConclusionType.MODIFY;
            case REJECTED, DECLINED -> ConclusionType.REJECT;
            default -> ConclusionType.POSTPONE;
        };
    }
}
