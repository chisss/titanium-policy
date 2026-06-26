package com.titanium.policy.infrastructure.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.titanium.policy.aggregate.Insurance;
import com.titanium.policy.infrastructure.entity.InsuranceEntity;
import com.titanium.policy.infrastructure.mapper.InsuranceMapper;
import com.titanium.policy.infrastructure.repository.jpa.JpaInsuranceRepository;
import com.titanium.policy.repository.InsuranceRepository;
import com.titanium.policy.valueobject.insurance.InsuranceStatus;

/**
 * 投保单仓库实现
 * <p>
 * 使用JPA repository来访问和操作投保单数据
 * </p>
 */
@Repository
public class InsuranceRepositoryImpl implements InsuranceRepository {
    private final JpaInsuranceRepository jpaInsuranceRepository;

    /**
     * 构造函数
     *
     * @param jpaInsuranceRepository JPA投保单仓库
     */
    public InsuranceRepositoryImpl(JpaInsuranceRepository jpaInsuranceRepository) {
        this.jpaInsuranceRepository = jpaInsuranceRepository;
    }

    @Override
    public Optional<Insurance> findById(String insuranceId, String tenantId) {
        Optional<InsuranceEntity> entityOpt = jpaInsuranceRepository.findById(insuranceId);
        return entityOpt.map(InsuranceMapper.INSTANCE::toAggregate);
    }

    @Override
    public Insurance save(Insurance insurance) {
        InsuranceEntity entity = InsuranceMapper.INSTANCE.toEntity(insurance);
        InsuranceEntity savedEntity = jpaInsuranceRepository.save(entity);
        return InsuranceMapper.INSTANCE.toAggregate(savedEntity);
    }

    @Override
    public void deleteById(String applicationId, String tenantId) {
        jpaInsuranceRepository.deleteById(applicationId);
    }

    @Override
    public Iterable<Insurance> findByStatus(String tenantId, InsuranceStatus.StatusCode statusCode) {
        Iterable<InsuranceEntity> entities = jpaInsuranceRepository.findByStatusCodeAndTenantId(statusCode,
                tenantId);
        List<Insurance> insurances = new ArrayList<>();
        for (InsuranceEntity entity : entities) {
            insurances.add(InsuranceMapper.INSTANCE.toAggregate(entity));
        }
        return insurances;
    }

    @Override
    public Optional<Insurance> findByInsuranceNo(String insuranceNo, String tenantId) {
        Optional<InsuranceEntity> entityOpt = jpaInsuranceRepository.findByInsuranceNoAndTenantId(insuranceNo,
                tenantId);
        return entityOpt.map(InsuranceMapper.INSTANCE::toAggregate);
    }

    @Override
    public Optional<Insurance> findByProposalId(String proposalId, String tenantId) {
        Optional<InsuranceEntity> entityOpt = jpaInsuranceRepository.findByProposalIdAndTenantId(proposalId, tenantId);
        return entityOpt.map(InsuranceMapper.INSTANCE::toAggregate);
    }

    @Override
    public Iterable<Insurance> findByApplicantId(String holderId, String tenantId) {
        Iterable<InsuranceEntity> entities = jpaInsuranceRepository.findByHolderIdAndTenantId(holderId, tenantId);
        List<Insurance> insurances = new ArrayList<>();
        for (InsuranceEntity entity : entities) {
            insurances.add(InsuranceMapper.INSTANCE.toAggregate(entity));
        }
        return insurances;
    }
}
