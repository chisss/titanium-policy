package com.titanium.policy.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.titanium.metadata.errorcode.PolicyErrorCode;
import com.titanium.metadata.exception.DomainException;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.application.command.insurance.InsuranceApplicationService;
import com.titanium.policy.application.command.policy.PolicyApplicationService;
import com.titanium.policy.application.command.proposal.ProposalApplicationService;
import com.titanium.policy.application.query.InsuranceAppQueryService;
import com.titanium.policy.application.query.PolicyAppQueryService;
import com.titanium.policy.application.query.PolicyLineAppQueryService;
import com.titanium.policy.application.query.ProposalAppQueryService;
import com.titanium.policy.query.query.FindPolicyByIdQuery;
import com.titanium.policy.web.controller.insurance.InsuranceController;
import com.titanium.policy.web.controller.policy.PolicyController;
import com.titanium.policy.web.controller.policy.PolicyDetailController;
import com.titanium.policy.web.controller.proposal.ProposalController;
import com.titanium.policy.web.handler.PolicyExceptionHandler;
import com.titanium.policy.web.mapper.InsuranceWebMapper;
import com.titanium.policy.web.mapper.PolicyStatisticsWebMapper;
import com.titanium.policy.web.mapper.PolicyWebMapper;
import com.titanium.policy.web.mapper.ProposalWebMapper;

/**
 * 「按 id 取详情」未命中的统一拒绝契约（🔴 R8-05）。
 * <p>
 * 四个详情端点此前都是「未命中 → 返回 404 + <b>0 字节 body</b>」，真机实测确认
 * （{@code GET /web/v1/policies/{id}} 错配租户 = 404 + 0B）。调用方（admin BFF）拿不到任何线索，
 * 只能回落到硬编码的「资源不存在」，运维据此无从分辨「id 拼错了」与「租户错配」——
 * 而这两者的处置完全不同。
 * </p>
 * <p>
 * 本类把「未命中必须抛 {@link DomainException}」固化为可回归的契约。它同时守护两个方向：
 * <b>既不能退回空 body</b>（{@code orElse(ResponseEntity.notFound().build())} 会让下面的
 * {@code assertThrows} 失败），<b>也不能改成放宽隔离的写法</b>（如未命中返回 200 + 空对象、
 * 或按成因分别返回 403/404——后者会成为探测 id 是否存在的侧信道）。
 * </p>
 * <p>
 * 🔴 四个端点必须逐个覆盖而非抽测其一：它们是四处独立的 {@code orElseThrow}，其中一处漏改
 * 只在那个页面上表现为「资源不存在」，其余三处照常通过。
 * </p>
 */
class DetailEndpointNotFoundContractTest {

    private static final String TENANT_ID = "1";
    private static final String POLICY_ID = "034df204-6a82-49d5-a10e-77c4d1cbb50d";

    private final PolicyExceptionHandler exceptionHandler = new PolicyExceptionHandler();

    @Test
    void policyDetailShouldRejectWithDiagnosableNotFound() {
        PolicyAppQueryService queryService = mock(PolicyAppQueryService.class);
        when(queryService.findById(new FindPolicyByIdQuery(POLICY_ID, TENANT_ID))).thenReturn(Optional.empty());
        PolicyController controller = new PolicyController(mock(PolicyApplicationService.class), queryService,
                mock(PolicyWebMapper.class), mock(PolicyStatisticsWebMapper.class));

        DomainException thrown = assertThrows(DomainException.class,
                () -> controller.getPolicy(POLICY_ID, TENANT_ID));

        assertDiagnosableNotFound(thrown, PolicyErrorCode.POLICY_NOT_EXIST, POLICY_ID);
    }

    @Test
    void policyFullDetailShouldRejectWithDiagnosableNotFound() {
        PolicyLineAppQueryService queryService = mock(PolicyLineAppQueryService.class);
        when(queryService.findFullDetail(POLICY_ID, TENANT_ID)).thenReturn(Optional.empty());
        PolicyDetailController controller = new PolicyDetailController(queryService);

        DomainException thrown = assertThrows(DomainException.class,
                () -> controller.getFullDetail(POLICY_ID, TENANT_ID));

        assertDiagnosableNotFound(thrown, PolicyErrorCode.POLICY_NOT_EXIST, POLICY_ID);
    }

    @Test
    void insuranceDetailShouldRejectWithDiagnosableNotFound() {
        InsuranceAppQueryService queryService = mock(InsuranceAppQueryService.class);
        when(queryService.findById(POLICY_ID, TENANT_ID)).thenReturn(Optional.empty());
        InsuranceController controller = new InsuranceController(mock(InsuranceApplicationService.class),
                queryService, mock(InsuranceWebMapper.class));

        DomainException thrown = assertThrows(DomainException.class,
                () -> controller.getInsurance(POLICY_ID, TENANT_ID));

        assertDiagnosableNotFound(thrown, PolicyErrorCode.INSURANCE_NOT_EXIST, POLICY_ID);
    }

    @Test
    void proposalDetailShouldRejectWithDiagnosableNotFound() {
        ProposalAppQueryService queryService = mock(ProposalAppQueryService.class);
        when(queryService.findById(POLICY_ID, TENANT_ID)).thenReturn(Optional.empty());
        ProposalController controller = new ProposalController(mock(ProposalApplicationService.class),
                queryService, mock(ProposalWebMapper.class));

        DomainException thrown = assertThrows(DomainException.class,
                () -> controller.getProposal(POLICY_ID, TENANT_ID));

        assertDiagnosableNotFound(thrown, PolicyErrorCode.PROPOSAL_NOT_EXIST, POLICY_ID);
    }

    /**
     * 断言「可诊断的 404」三要素：① 仍是 404（隔离行为不放宽，不放宽成 200/403）
     * ② 业务码是本域 {@code *_NOT_EXIST} ③ 文案给出成因与回显 id。
     * <p>走完整翻译链（控制器抛出的异常 → {@link PolicyExceptionHandler}），
     * 只断言异常本身会漏掉「处理器把它翻成 500」这类回归。</p>
     */
    private void assertDiagnosableNotFound(DomainException thrown, PolicyErrorCode expectedCode, String resourceId) {
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleDomainException(thrown);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(expectedCode.getCode(), response.getBody().getCode());
        assertTrue(response.getBody().getMessage().contains("不在当前租户可见范围内"), response.getBody().getMessage());
        assertTrue(response.getBody().getMessage().contains(resourceId), response.getBody().getMessage());
    }
}
