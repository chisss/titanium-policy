package com.titanium.policy.application.query;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.titanium.policy.aggregate.Insurance;
import com.titanium.policy.repository.InsuranceRepository;
import com.titanium.policy.valueobject.insurance.InsuranceStatus;

import jakarta.annotation.Resource;

/**
 * 投保单查询服务
 * <p>
 * 处理投保单相关的查询，协调领域层和基础设施层
 * </p>
 */
@Service
public class InsuranceAppQueryService {
    @Resource
    private InsuranceRepository insuranceRepository;

    /**
     * 根据ID查询投保单
     *
     * @param applicationId 投保单ID
     * @param tenantId 租户ID
     * @return 投保单
     */
    public Optional<Insurance> findById(String applicationId, String tenantId) {
        return insuranceRepository.findById(applicationId, tenantId);
    }

    /**
     * 根据状态查询投保单
     *
     * @param tenantId 租户ID
     * @param statusCode 状态编码
     * @return 投保单列表
     */
    public Iterable<Insurance> findByStatus(String tenantId, InsuranceStatus.StatusCode statusCode) {
        return insuranceRepository.findByStatus(tenantId, statusCode);
    }

    /**
     * 根据投保单编号查询投保单
     *
     * @param insuranceNo 投保单编号
     * @param tenantId 租户ID
     * @return 投保单
     */
    public Optional<Insurance> findByInsuranceNo(String insuranceNo, String tenantId) {
        return insuranceRepository.findByInsuranceNo(insuranceNo, tenantId);
    }

    /**
     * 根据关联意向单ID查询投保单
     *
     * @param proposalId 意向单ID
     * @param tenantId 租户ID
     * @return 投保单
     */
    public Optional<Insurance> findByProposalId(String proposalId, String tenantId) {
        return insuranceRepository.findByProposalId(proposalId, tenantId);
    }

    /**
     * 根据投保人ID查询投保单
     *
     * @param applicantId 投保人ID
     * @param tenantId 租户ID
     * @return 投保单列表
     */
    public Iterable<Insurance> findByApplicantId(String applicantId, String tenantId) {
        return insuranceRepository.findByApplicantId(applicantId, tenantId);
    }
}
