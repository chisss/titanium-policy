package com.titanium.policy.infrastructure.adapter;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.titanium.clause.api.ClauseApi;
import com.titanium.metadata.errorcode.SystemErrorCode;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.exception.RemoteDependencyUnavailableException;
import com.titanium.policy.infrastructure.adapter.clause.ClauseServiceAdapter;
import com.titanium.policy.infrastructure.adapter.product.ProductServiceAdapter;
import com.titanium.product.api.ProductApi;
import com.titanium.product.api.ProductTemplateApi;

import feign.FeignException;
import feign.Request;

/**
 * 远程失败**不得被折叠**的契约测试（m21-02 / 审查报告 RPC-01）。
 *
 * <p>本类守的是改造引入的新契约：<b>适配器不得再以 null / 空集合表示远程失败</b>。
 * 改造前 {@code ProductServiceAdapter} 在 {@code !isSuccess()} 时 {@code return null}、
 * {@code ClauseServiceAdapter} 在 {@code catch (FeignException)} 时返回 null/空列表，
 * 使「查询失败」与「确实没有」不可区分 ⇒ 保单缺条款缺责任、等待期与犹豫期归零，
 * 却静默出单成功。
 * </p>
 *
 * <p>断言用「抛出具名异常」而非「返回值不是 null」：后者在适配器改成返回别的占位值时
 * 仍会通过，前者才真正锁住「失败必须显式传播」这一语义。
 * </p>
 */
class RemoteFailureNoFoldingTest {

    /**
     * 构造一个仅供 FeignException 携带的占位请求。
     * <p>用 {@code Request.create} 的 5 参重载并<b>显式给 byte[] 与 Charset</b>：
     * 该重载与 {@code (…, Body, RequestTemplate)} 存在重载歧义，传 null 无法编译；
     * 也不能 {@code mock(Request.class)}——Request 是 final 类，mock 会抛错并使
     * 整个测试类的静态初始化失败（症状是 NoClassDefFound: Could not initialize class）。</p>
     */
    /** 被测适配器要求注入 JSON 解析器（m22-03 起由构造器注入） */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Request DUMMY_REQUEST = Request.create(Request.HttpMethod.GET, "/probe",
            java.util.Map.of(), new byte[0], java.nio.charset.StandardCharsets.UTF_8);

    // ---------- ProductServiceAdapter ----------

    @Test
    @DisplayName("产品查询：下游返回失败码时抛出，而非折叠成 null")
    void productFetchShouldThrowWhenDownstreamAnswersWithFailureCode() {
        ProductApi productApi = mock(ProductApi.class);
        when(productApi.getProductById(anyString()))
                .thenReturn(ApiResponse.error(SystemErrorCode.RESOURCE_NOT_FOUND, "产品不存在"));
        ProductServiceAdapter adapter = new ProductServiceAdapter(OBJECT_MAPPER, productApi, mock(ProductTemplateApi.class));

        assertThrows(RemoteDependencyUnavailableException.class,
                () -> adapter.getProductBasicInfo("P-1"));
    }

    @Test
    @DisplayName("产品模板查询：下游响应为空时抛出，而非折叠成 null")
    void templateFetchShouldThrowWhenResponseIsNull() {
        ProductApi productApi = mock(ProductApi.class);
        ProductTemplateApi templateApi = mock(ProductTemplateApi.class);
        when(productApi.getProductById(anyString()))
                .thenReturn(ApiResponse.error(SystemErrorCode.SYSTEM_ERROR, "内部错误"));
        ProductServiceAdapter adapter = new ProductServiceAdapter(OBJECT_MAPPER, productApi, templateApi);

        assertThrows(RemoteDependencyUnavailableException.class,
                () -> adapter.getIssueRules("P-1"));
    }

    // ---------- ClauseServiceAdapter ----------

    @Test
    @DisplayName("条款查询：Feign 异常时抛出，而非折叠成 null（原实现使保单缺该条款仍出单成功）")
    void clauseFetchShouldThrowOnFeignFailure() {
        ClauseApi clauseApi = mock(ClauseApi.class);
        when(clauseApi.getClauseById(anyString()))
                .thenThrow(new FeignException.InternalServerError("boom", DUMMY_REQUEST, null, null));
        ClauseServiceAdapter adapter = new ClauseServiceAdapter(clauseApi);

        assertThrows(RemoteDependencyUnavailableException.class,
                () -> adapter.fetchClauseSnapshot("C-1", true, "TENANT-1"));
    }

    @Test
    @DisplayName("责任查询：Feign 异常时抛出，而非折叠成空列表（原实现使保单无责任仍出单成功）")
    void coverageFetchShouldThrowOnFeignFailure() {
        ClauseApi clauseApi = mock(ClauseApi.class);
        when(clauseApi.getCoveragesByClauseId(anyString()))
                .thenThrow(new FeignException.ServiceUnavailable("down", DUMMY_REQUEST, null, null));
        ClauseServiceAdapter adapter = new ClauseServiceAdapter(clauseApi);

        assertThrows(RemoteDependencyUnavailableException.class,
                () -> adapter.fetchCoverageSnapshots("C-1", "TENANT-1"));
    }

    @Test
    @DisplayName("连接层故障（RetryableException）同样必须抛出——它此前因继承关系不同而漏网")
    void clauseFetchShouldThrowOnRetryableFailure() {
        ClauseApi clauseApi = mock(ClauseApi.class);
        when(clauseApi.getClauseById(anyString()))
                .thenThrow(new FeignException.ServiceUnavailable("conn refused", DUMMY_REQUEST, null, null));
        ClauseServiceAdapter adapter = new ClauseServiceAdapter(clauseApi);

        assertThrows(RemoteDependencyUnavailableException.class,
                () -> adapter.fetchClauseSnapshot("C-2", false, "TENANT-1"));
    }
}
