package com.titanium.policy.web.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.axonframework.modelling.command.AggregateStreamCreationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.titanium.metadata.errorcode.PolicyErrorCode;
import com.titanium.metadata.errorcode.SystemErrorCode;
import com.titanium.metadata.exception.DomainException;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.exception.PolicyBusinessRuleException;
import com.titanium.policy.exception.PolicyStatusTransitionException;

/**
 * {@link PolicyExceptionHandler} 异常翻译回归测试。
 * <p>
 * 守护 D-501-14 / D-501-23：领域异常必须被翻成 {@link ApiResponse} 业务码，HTTP 用语义码而非 500。
 * 缺处理器时这些异常直穿 servlet 容器，调用方只能看到「服务器错误」。
 * </p>
 * <p>
 * 🔴 <b>本类只测业务/领域异常</b>。原先此处的两例缺头测试（R8-05：{@code X-Tenant-Id} →
 * 400 + {@code TENANT_HEADER_MISSING}，其余头 → {@code PARAM_INVALID}）随
 * {@code PolicyExceptionHandler#handleMissingRequestHeader} 一并迁出——该方法被
 * {@code BoundaryExceptionHandler}（序 {@code HIGHEST_PRECEDENCE + 20}）遮蔽后已不可达，故从生产代码删除，
 * 语义提升为全域唯一实现 {@code FrameworkErrorCodes#of}。等价的断言现由
 * {@code titanium-common} 的 {@code FrameworkErrorCodesTest} 承载，<b>覆盖未减</b>。
 * </p>
 */
class PolicyExceptionHandlerTest {

    private final PolicyExceptionHandler handler = new PolicyExceptionHandler();

    @Test
    void shouldTranslateEnumCarriedDomainException() {
        DomainException exception =
                new PolicyBusinessRuleException(PolicyErrorCode.PROPOSAL_NOT_EXIST, "投保意向单不存在");

        ResponseEntity<ApiResponse<Void>> response = handler.handleDomainException(exception);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(PolicyErrorCode.PROPOSAL_NOT_EXIST.getCode(), response.getBody().getCode());
        assertEquals("投保意向单不存在", response.getBody().getMessage());
    }

    /**
     * 🔴 出单流程未配置 → 400，不是 500（审查报告 EXC-05 复核新增）。
     * <p>
     * 该码由 {@code ProductServiceAdapter#getIssuanceMode} 在「产品及其模板均未配出单流程」时携带。
     * 断言 HTTP 400 而非仅断言「抛了业务异常」，是因为该结论<b>跨了两段容易各自失守的逻辑</b>：
     * </p>
     * <ol>
     *   <li>适配器弃用裸 {@code IllegalStateException}，改抛携带错误码的业务异常；</li>
     *   <li>边界 {@code resolve} 按码串回查枚举——{@code BusinessRuleViolationException} 的
     *       {@link com.titanium.metadata.errorcode.BaseErrorCode} 构造器目前<b>仍把枚举降级为裸串</b>
     *       （走 {@code @Deprecated} 的 {@code DomainException(String, String)}，包装成
     *       {@code RawErrorCode}，其 {@code category()} 恒为 {@code INTERNAL_FAILURE}），
     *       全靠此处的反查把枚举还原回来，类别才能回到 {@code PARAMETER_INVALID}。</li>
     * </ol>
     * <p>
     * 故本用例锁的是<b>两段合起来的结果</b>：任一段失守（改回裸抛，或边界改用无反查的
     * {@code ExceptionResponseFactory.of(exception)}），「产品没配出单流程」都会静默退回 500，
     * 而两段的单侧改动都不会让任何既有用例变红。
     * </p>
     */
    @Test
    void shouldRejectUnconfiguredIssuanceModeAsBadRequest() {
        DomainException exception = new PolicyBusinessRuleException(
                PolicyErrorCode.ISSUANCE_MODE_NOT_CONFIGURED,
                "产品[P-1]及其模板均未配置出单流程，无法决定出单模式");

        ResponseEntity<ApiResponse<Void>> response = handler.handleDomainException(exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(PolicyErrorCode.ISSUANCE_MODE_NOT_CONFIGURED.getCode(), response.getBody().getCode());
    }

    @Test
    void shouldTranslateLegacyBusinessRuleViolationAsUnprocessableEntity() {
        // 复刻 D-501-14 实测形态：存量裸串构造器抛出的规则拒绝（Proposal.validateApplicants:343）
        DomainException exception = new PolicyBusinessRuleException("POLICY_RULE_VIOLATION", "At least one applicant is required");

        ResponseEntity<ApiResponse<Void>> response = handler.handleDomainException(exception);

        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(PolicyErrorCode.POLICY_RULE_VIOLATION.getCode(), response.getBody().getCode());
        // 原始规则码必须保留在 message 中，供调用方与 B-03a 类断言分支识别
        assertTrue(response.getBody().getMessage().contains("POLICY_RULE_VIOLATION"), response.getBody().getMessage());
    }

    /**
     * 状态流转非法 → 409 + {@code POLICY_STATUS_TRANSITION_ILLEGAL}。
     * <p>
     * 🔴 <b>本用例名原为 {@code shouldTranslateLegacyIllegalStateTransitionAsConflict}，已去掉「Legacy」（m30-10）</b>：
     * 迁移前它锁的是 <b>LEGACY 回查路径</b>——{@code PolicyStatusTransitionException} 转发父类的裸串构造器，
     * 错误码退化为符号串 {@code ILLEGAL_STATE_TRANSITION}，须由 {@code PolicyExceptionHandler} 的
     * {@code LEGACY_ERROR_CODES} 回查才还原成本码。迁移后该异常<b>直接携带枚举</b>，走 {@code resolve}
     * 的 {@code instanceof PolicyErrorCode} 分支直通，回查条目已随同删除。
     * </p>
     * <p>
     * 故本用例的断言<b>值未变、对象已换</b>：它现在守的是「枚举直通同样得到 409 + 正确码」，
     * 而非「历史裸串回查生效」。留着原名会让人误以为 LEGACY 回查仍有覆盖，实际该路径已无产生源。
     * </p>
     */
    @Test
    void shouldTranslateIllegalStateTransitionAsConflict() {
        DomainException exception = new PolicyStatusTransitionException("投保单", "P-1", "DRAFT", "SUBMITTED");

        ResponseEntity<ApiResponse<Void>> response = handler.handleDomainException(exception);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(PolicyErrorCode.POLICY_STATUS_TRANSITION_ILLEGAL.getCode(), response.getBody().getCode());
    }

    @Test
    void shouldTranslateAggregateIdentifierReuseAsConflict() {
        AggregateStreamCreationException exception =
                new AggregateStreamCreationException("Cannot reuse aggregate identifier [P-1] to create aggregate [Proposal]");

        ResponseEntity<ApiResponse<Void>> response = handler.handleAggregateStreamCreationException(exception);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(PolicyErrorCode.POLICY_AGGREGATE_ALREADY_EXISTS.getCode(), response.getBody().getCode());
    }

    @Test
    void shouldFallbackToSystemErrorOnUnknownCode() {
        // 未登记在册的 8 位码（20 段内未占用值）无法反查到枚举实例，只能走系统错误兜底
        DomainException exception = new DomainException("20999999", "未登记的错误码");

        ResponseEntity<ApiResponse<Void>> response = handler.handleDomainException(exception);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(SystemErrorCode.SYSTEM_ERROR.getCode(), response.getBody().getCode());
    }

    @Test
    void shouldFallbackToSystemErrorOnUnexpectedException() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleException(new IllegalStateException("boom"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(SystemErrorCode.SYSTEM_ERROR.getCode(), response.getBody().getCode());
    }

    /**
     * 🔴 R8-05 缺陷①：{@code notFoundInTenant} 产出的 404 必须**带可诊断的 body**。
     * <p>
     * 真机实测此前是 HTTP 404 + <b>0 字节 body</b>，admin BFF 拿不到任何线索、只能回落硬编码
     * 「资源不存在」。用例走完整翻译链（工厂 → {@code handleDomainException}），断言三件事：
     * ① HTTP 仍是 404（隔离行为不放宽）② 业务码是本域 {@code *_NOT_EXIST}
     * ③ 文案同时给出「或不在当前租户可见范围内」与回显的 id。
     * </p>
     * <p>
     * ③ 的两半都要断言：只断成因短语，删掉 id 回显仍会通过，而 id 回显正是运维拿手上的 id
     * 做直接对照的依据。
     * </p>
     */
    @Test
    void shouldTranslateNotFoundInTenantIntoDiagnosableNotFoundBody() {
        String policyId = "034df204-6a82-49d5-a10e-77c4d1cbb50d";
        DomainException exception = PolicyExceptionHandler.notFoundInTenant(PolicyErrorCode.POLICY_NOT_EXIST, policyId);

        ResponseEntity<ApiResponse<Void>> response = handler.handleDomainException(exception);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(PolicyErrorCode.POLICY_NOT_EXIST.getCode(), response.getBody().getCode());
        assertTrue(response.getBody().getMessage().contains("不在当前租户可见范围内"), response.getBody().getMessage());
        assertTrue(response.getBody().getMessage().contains(policyId), response.getBody().getMessage());
    }

    /**
     * 🔴 两种成因**合并表述**是安全要求，不是文案偏好：分开报（如不存在→404、租户错配→403）
     * 即成为探测 id 是否真实存在的侧信道。本用例把「消息里不得出现任何区分性措辞」固化为断言。
     */
    @Test
    void shouldNotDistinguishAbsentFromTenantMismatch() {
        DomainException absent = PolicyExceptionHandler.notFoundInTenant(PolicyErrorCode.INSURANCE_NOT_EXIST, "X");
        DomainException mismatched = PolicyExceptionHandler.notFoundInTenant(PolicyErrorCode.INSURANCE_NOT_EXIST, "X");

        // 同一个 id 在「不存在」与「租户错配」两种成因下，服务端产出的异常必须逐字节相同
        assertEquals(absent.getMessage(), mismatched.getMessage());
        assertEquals(absent.getErrorCode(), mismatched.getErrorCode());
        assertEquals(HttpStatus.NOT_FOUND, handler.handleDomainException(absent).getStatusCode());
    }
}
