package com.titanium.policy.port;

import java.util.Optional;

import com.titanium.policy.valueobject.customer.CustomerIdentitySnapshot;

/**
 * 客户主数据端口。
 * <p>
 * 出单应用层只通过该端口解析或创建客户，customer API 的 Feign 协议由基础设施适配器隔离。
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
