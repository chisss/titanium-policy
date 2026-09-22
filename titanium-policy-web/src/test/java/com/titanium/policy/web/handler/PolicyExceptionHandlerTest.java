package com.titanium.policy.web.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.axonframework.modelling.command.AggregateStreamCreationException;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;

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

    @Test
    void shouldTranslateLegacyIllegalStateTransitionAsConflict() {
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
     * 🔴 R8-05 缺陷②：缺 {@code X-Tenant-Id} 必须是 400 + TENANT_HEADER_MISSING。
     * <p>
     * 此前 {@code /web/v1/**} 上没有任何租户头准入门（{@code TenantApiInterceptor} 只挂
     * {@code /api/v1/**}），缺头落 {@code handleException} 兜底成 500 + SYSTEM_ERROR
     * （真机实测响应体 {@code {"code":"10000000","message":"Required request header 'X-Tenant-Id' ..."}}）。
     * 用例同时锁死「码」与「HTTP 状态」两件事：只锁其一，另一条回归仍会通过。
     * </p>
     */
    @Test
    void shouldTranslateMissingTenantHeaderAsBadRequestWithTenantCode() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleMissingRequestHeader(missingHeader("X-Tenant-Id"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(SystemErrorCode.TENANT_HEADER_MISSING.getCode(), response.getBody().getCode());
        assertTrue(response.getBody().getMessage().contains("X-Tenant-Id"), response.getBody().getMessage());
    }

    /**
     * 缺的若不是租户头（如 {@code X-Operator-Id}），仍按普通参数错误 400 + PARAM_INVALID——
     * 只有租户头有专属码，不得把一切缺头都升级成租户错误。
     * <p>大小写不敏感：本域三个拦截器写的是 {@code X-Tenant-Id}，而 admin 侧
     * {@code ProxyTenantAuthorizationManager} 写的是 {@code X-Tenant-ID}，两种都要认。</p>
     */
    @Test
    void shouldTranslateMissingNonTenantHeaderAsParamInvalid() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleMissingRequestHeader(missingHeader("X-Tenant-ID-Other"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(SystemErrorCode.PARAM_INVALID.getCode(), response.getBody().getCode());
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

    /** 构造缺头异常（构造器要求 {@link MethodParameter} 占位，内容不参与断言）。 */
    private static MissingRequestHeaderException missingHeader(String headerName) {
        try {
            MethodParameter parameter = new MethodParameter(
                    PolicyExceptionHandlerTest.class.getDeclaredMethod("missingHeader", String.class), 0);
            return new MissingRequestHeaderException(headerName, parameter);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }
}
