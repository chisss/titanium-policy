package com.titanium.policy.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import com.titanium.metadata.enums.PolicyEnum;
import com.titanium.policy.aggregate.Policy;
import com.titanium.policy.application.PolicyApplicationService;
import com.titanium.policy.command.CreatePolicyCommand;
import com.titanium.policy.event.PolicyCreatedEvent;
import com.titanium.policy.repository.PolicyRepository;
import com.titanium.policy.valueobject.Amount;
import com.titanium.policy.valueobject.PolicyNo;

class PolicyApplicationServiceTest {

    @Mock
    private CommandGateway           commandGateway;

    @Mock
    private PolicyRepository         policyRepository;

    @InjectMocks
    private PolicyApplicationService policyApplicationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testCreatePolicy() {
        // 准备测试数据
        String policyId = "test-policy-id";
        CreatePolicyCommand command = new CreatePolicyCommand(policyId, new PolicyNo("POL-2024-0001"), "customer-001",
                "product-001", LocalDateTime.now(), LocalDateTime.now().plusYears(1),
                new Amount(new BigDecimal("1000.0"), "CNY"), null, // policyItems
                "tenant-001");

        // 模拟依赖
        when(commandGateway.sendAndWait(any(CreatePolicyCommand.class))).thenReturn(policyId);

        // 执行测试
        String result = policyApplicationService.createPolicy(command);

        // 验证结果
        assertNotNull(result);
        assertEquals(policyId, result);
        verify(commandGateway, times(1)).sendAndWait(any(CreatePolicyCommand.class));
    }

    @Test
    void testGetPolicyById() {
        // 准备测试数据
        String policyId = "policy-001";
        String tenantId = "tenant-001";
        String policyNo = "POL-2024-0001";

        FixtureConfiguration<Policy> fixture = new AggregateTestFixture<>(Policy.class);

        fixture.given(new PolicyCreatedEvent(policyId, new PolicyNo(policyNo), "customer-001", "product-001",
                LocalDateTime.now(), LocalDateTime.now().plusYears(1), new Amount(new BigDecimal("1000.0"), "CNY"),
                PolicyEnum.PolicyStatus.PENDING, Collections.emptyList(),
                tenantId));

        Policy mockPolicy = Mockito.mock(Policy.class);
        when(mockPolicy.getPolicyId()).thenReturn(policyId);
        when(mockPolicy.getPolicyNo()).thenReturn(new PolicyNo(policyNo));

        when(policyRepository.findById(policyId, tenantId)).thenReturn(Optional.of(mockPolicy));

        // 执行测试
        Optional<Policy> result = policyApplicationService.getPolicyById(policyId, tenantId);

        // 验证结果
        assertTrue(result.isPresent());
        assertEquals(policyId, result.get().getPolicyId());
        verify(policyRepository, times(1)).findById(policyId, tenantId);
    }

    @Test
    void testActivatePolicy() {
        // 准备测试数据
        String policyId = "policy-001";
        String tenantId = "tenant-001";

        // 创建模拟的 Policy 对象
        Policy mockPolicy = Mockito.mock(Policy.class);
        when(mockPolicy.getStatus()).thenReturn(PolicyEnum.PolicyStatus.PENDING);

        // 模拟依赖
        when(policyRepository.findById(policyId, tenantId)).thenReturn(Optional.of(mockPolicy));

        // 执行测试
        policyApplicationService.activatePolicy(policyId, tenantId);

        // 验证结果
        verify(policyRepository, times(1)).findById(policyId, tenantId);
        verify(mockPolicy).activate(); // 验证激活方法被调用

    }

    @Test
    void testActivatePolicy_PolicyNotFound() {
        // 准备测试数据
        String policyId = "policy-001";
        String tenantId = "tenant-001";

        // 模拟依赖 - 保单不存在
        when(policyRepository.findById(policyId, tenantId)).thenReturn(Optional.empty());

        // 执行测试，预期抛出异常
        assertThrows(IllegalArgumentException.class, () -> policyApplicationService.activatePolicy(policyId, tenantId));

        // 验证结果
        verify(policyRepository, times(1)).findById(policyId, tenantId);
    }
}
