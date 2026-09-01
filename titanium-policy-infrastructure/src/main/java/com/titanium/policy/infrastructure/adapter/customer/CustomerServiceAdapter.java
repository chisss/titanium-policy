package com.titanium.policy.infrastructure.adapter;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.titanium.customer.api.CustomerApi;
import com.titanium.customer.api.request.ResolveCustomerRequest;
import com.titanium.customer.api.response.CustomerResponse;
import com.titanium.policy.port.CustomerServicePort;
import com.titanium.policy.valueobject.customer.CustomerIdentitySnapshot;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 客户服务防腐适配器。
 * <p>
 * 仅在基础设施层依赖 customer API；上层接收不到远程 DTO，也不会在客户服务失败时伪造客户ID。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerServiceAdapter implements CustomerServicePort {

    private final CustomerApi customerApi;

    @Override
    public boolean isCustomerExists(String customerId, String tenantId) {
        try {
            return customerApi.isCustomerExists(customerId, tenantId);
        } catch (FeignException exception) {
            log.warn("客户存在性查询失败: customerId={}, tenantId={}, status={}", customerId, tenantId,
                    exception.status(), exception);
            throw new IllegalStateException("客户服务调用失败", exception);
        }
    }

    @Override
    public Optional<CustomerIdentitySnapshot> findCustomerIdentity(String customerId, String tenantId) {
        try {
            CustomerResponse customer = customerApi.getCustomer(customerId, tenantId);
            if (customer == null) {
                return Optional.empty();
            }
            return Optional.of(new CustomerIdentitySnapshot(customer.getFullName(), customer.getIdType(),
                    customer.getIdNo(), customer.getGender(), customer.getPhoneNumber(), null));
        } catch (FeignException exception) {
            log.warn("客户身份查询失败: customerId={}, tenantId={}, status={}", customerId, tenantId,
                    exception.status(), exception);
            throw new IllegalStateException("客户服务调用失败", exception);
        }
    }

    @Override
    public String resolveCustomer(CustomerIdentitySnapshot identity, String tenantId) {
        try {
            ResolveCustomerRequest request = ResolveCustomerRequest.builder()
                    .fullName(identity.fullName())
                    .idType(identity.idType())
                    .idNo(identity.idNo())
                    .gender(identity.gender())
                    .phoneNumber(identity.phoneNumber())
                    .operatorId(identity.operatorId())
                    .build();
            return customerApi.resolveCustomer(request, tenantId);
        } catch (FeignException exception) {
            log.warn("客户服务调用失败: tenantId={}, status={}", tenantId, exception.status(), exception);
            throw new IllegalStateException("客户服务调用失败", exception);
        }
    }
}
