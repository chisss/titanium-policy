package com.titanium.policy.infrastructure.projection;

import org.axonframework.queryhandling.QueryHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.titanium.policy.infrastructure.entity.InsuranceEntity;
import com.titanium.policy.infrastructure.mapper.InsuranceMapper;
import com.titanium.policy.infrastructure.repository.jpa.JpaInsuranceRepository;
import com.titanium.policy.query.InsuranceQuery;

/**
 * 投保单投影类，用于处理投保单领域事件并更新数据库
 */
@Component
public class InsuranceProjection {

    @Autowired
    private JpaInsuranceRepository insuranceRepository;

    @Autowired
    private InsuranceMapper        insuranceMapper;

    /**
     * 处理投保单查询
     */
    @QueryHandler
    public InsuranceEntity handle(InsuranceQuery query) {
        return insuranceRepository.findByInsuranceNoAndTenantId(query.applicationId(), query.tenantId()).orElse(null);
    }
}
