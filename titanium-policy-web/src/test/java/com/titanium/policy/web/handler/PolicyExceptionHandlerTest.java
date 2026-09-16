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
}
