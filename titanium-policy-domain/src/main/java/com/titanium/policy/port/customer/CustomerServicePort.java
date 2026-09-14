package com.titanium.policy.port.customer;

import java.util.Optional;

import com.titanium.policy.valueobject.customer.CustomerIdentitySnapshot;

/**
 * 客户主数据端口。
 * <p>
 * 出单应用层只通过该端口解析或创建客户，customer API 的 Feign 协议由基础设施适配器隔离。
 * </p>
 * <p>
 * 🔴 <b>本端口即 policy 与 customer 的全部协作面，不订阅客户主题（2026-09-14 m6-908 判定）</b>：
 * 三个方法均为<b>出单时点的同步调用</b>（存在性校验 / 身份快照比对 / 幂等建档），无长期状态订阅语义。
 * 客户主数据变更<b>不应</b>经 Kafka 静默改写保单——保单当事人姓名/证件/年龄/性别是<b>合同要素</b>且参与费率，
 * 变更须走<b>保全批改</b>（{@code PolicyMaintenanceFieldExecutor} 体系，有留痕可审核）。
 * 故 {@code customer-created/updated/status-changed/relationship-added} 四主题<b>均不接</b>入 policy，
 * 判定依据见 {@code docs/技术文档/跨域事件目录-2026-09.md} §六.16。
 * </p>
 */
public interface CustomerServicePort {

    /**
     * 按租户确认客户主数据存在。
     *
     * @param customerId 客户ID
     * @param tenantId   租户ID
     * @return 当前租户下存在返回 {@code true}
     */
    boolean isCustomerExists(String customerId, String tenantId);

    /**
     * 按客户ID读取用于出单归属校验的身份快照。
     *
     * @param customerId 客户ID
     * @param tenantId   租户ID
     * @return 当前租户下的客户身份；不存在返回空
     */
    Optional<CustomerIdentitySnapshot> findCustomerIdentity(String customerId, String tenantId);

    /**
     * 按租户内自然身份解析或幂等创建客户。
     *
     * @param identity 身份与首次建档快照
     * @param tenantId 租户ID
     * @return 客户主数据ID
     */
    String resolveCustomer(CustomerIdentitySnapshot identity, String tenantId);
}
