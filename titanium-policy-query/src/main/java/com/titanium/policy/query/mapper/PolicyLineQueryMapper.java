package com.titanium.policy.query.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.titanium.policy.query.result.PolicyBeneficiaryQueryResult;
import com.titanium.policy.query.result.PolicyClauseQueryResult;
import com.titanium.policy.query.result.PolicyCollectionQueryResult;
import com.titanium.policy.query.result.PolicyCoverageQueryResult;
import com.titanium.policy.query.result.PolicyInsuredQueryResult;
import com.titanium.policy.query.result.PolicyProductQueryResult;
import com.titanium.policy.query.result.PolicyQueryResult;
import com.titanium.policy.query.result.PolicySubjectQueryResult;
import com.titanium.policy.query.view.PolicyBeneficiaryView;
import com.titanium.policy.query.view.PolicyClauseView;
import com.titanium.policy.query.view.PolicyCollectionView;
import com.titanium.policy.query.view.PolicyCoverageView;
import com.titanium.policy.query.view.PolicyInsuredView;
import com.titanium.policy.query.view.PolicyProductView;
import com.titanium.policy.query.view.PolicySubjectView;
import com.titanium.policy.query.view.PolicyView;

/**
 * 保单险种段族读模型映射器（View → QueryResult）
 * <p>
 * 读模型实体（{@code XxxView}，映射数据表）与查询结果（{@code XxxQueryResult}，对外稳定契约）
 * 的转换。二者分立的意义：View 是「怎么存」（含乐观锁、租户列、JPA 注解），QueryResult 是
 * 「返回什么」——禁止直接返回 View 以免泄漏持久化细节（规约 §3.4.9 ③）。
 * </p>
 * <p>
 * 全部字段同名，MapStruct 自动映射；段内的条款/标的/责任三层明细由查询服务按段分组装配后
 * 回填，故在段映射中忽略。
 * </p>
 */
@Mapper(componentModel = "spring")
public interface PolicyLineQueryMapper {

    /**
     * 保单主体读模型 → 查询结果。
     *
     * @param view 保单读模型
     * @return 保单查询结果
     */
    @Mapping(target = "applicationId", source = "insuranceId")
    @Mapping(target = "effectiveDate", source = "startDate")
    @Mapping(target = "expiryDate", source = "endDate")
    @Mapping(target = "status", source = "policyStatus")
    @Mapping(target = "policyForm", ignore = true)
    @Mapping(target = "insuredId", ignore = true)
    @Mapping(target = "productName", ignore = true)
    @Mapping(target = "policyItems", ignore = true)
    PolicyQueryResult toPolicyResult(PolicyView view);

    /**
     * 险种段读模型 → 查询结果（L2）。
     * <p>
     * 段内三层明细（条款/标的/责任）由查询服务批量取出后按段分组回填，此处忽略以避免
     * MapStruct 生成无意义的空集合赋值。
     * </p>
     *
     * @param view 险种段读模型
     * @return 险种段查询结果
     */
    @Mapping(target = "clauses", ignore = true)
    @Mapping(target = "subjects", ignore = true)
    @Mapping(target = "coverages", ignore = true)
    PolicyProductQueryResult toLineResult(PolicyProductView view);

    /**
     * 条款快照读模型 → 查询结果（L2.5）。
     *
     * @param view 条款快照读模型
     * @return 条款查询结果
     */
    PolicyClauseQueryResult toClauseResult(PolicyClauseView view);

    /**
     * 标的读模型 → 查询结果（L3）。
     *
     * @param view 标的读模型
     * @return 标的查询结果
     */
    PolicySubjectQueryResult toSubjectResult(PolicySubjectView view);

    /**
     * 保险责任读模型 → 查询结果（L4）。
     *
     * @param view 责任读模型
     * @return 责任查询结果
     */
    PolicyCoverageQueryResult toCoverageResult(PolicyCoverageView view);

    /**
     * 收费信息读模型 → 查询结果。
     *
     * @param view 收费读模型
     * @return 收费查询结果
     */
    PolicyCollectionQueryResult toCollectionResult(PolicyCollectionView view);

    /**
     * 被保险人读模型 → 查询结果。
     *
     * @param view 被保险人读模型
     * @return 被保险人查询结果
     */
    @Mapping(target = "relationToHolder", source = "relation")
    PolicyInsuredQueryResult toInsuredResult(PolicyInsuredView view);

    /**
     * 受益人读模型 → 查询结果。
     *
     * @param view 受益人读模型
     * @return 受益人查询结果
     */
    PolicyBeneficiaryQueryResult toBeneficiaryResult(PolicyBeneficiaryView view);
}
