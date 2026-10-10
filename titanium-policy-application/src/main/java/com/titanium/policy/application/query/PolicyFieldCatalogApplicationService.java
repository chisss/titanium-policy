package com.titanium.policy.application.query;

import java.util.List;

import org.springframework.stereotype.Service;

import com.titanium.policy.fieldcatalog.PolicyFieldCatalog;
import com.titanium.policy.fieldcatalog.PolicyFieldCatalogCriteria;
import com.titanium.policy.fieldcatalog.PolicyFieldCatalogPublication;
import com.titanium.policy.fieldcatalog.PolicyFieldCatalogSnapshot;
import com.titanium.policy.fieldcatalog.PolicyFieldCatalogValidationException;
import com.titanium.policy.query.repository.PolicyFieldCatalogVersionViewRepository;
import com.titanium.policy.query.repository.PolicyFieldCatalogViewRepository;
import com.titanium.policy.query.view.PolicyFieldCatalogView;

import lombok.RequiredArgsConstructor;

/**
 * 保单字段目录应用查询服务（读用例入口门面）。
 *
 * <p>只做编排：解析业务时点应生效的版本 → 取该版本行 → 交装配器 → 组合成快照。
 * 业务规则（区间归属、版本链自洽、内容哈希、字段不变量）全在领域 record 与装配器中，
 * 本类不含任何判断分支。</p>
 *
 * <p>🔴 <b>查询条件中的 {@code tenantId} 是死参数</b>（{@code G07/AC-10}）：目录是跨租户共享的
 * 平台元数据，不按租户过滤是<strong>有意设计</strong>，故本类<strong>不</strong>把租户下推到仓储。
 * 租户差异落在特征定义层（feature-center 的 {@code FeatureDefinition}）。</p>
 *
 * <p>🔴 <b>{@code productType}/{@code policyType} 同为不参与取数的保留参数</b>：二者的差异
 * 表达方式是「特征定义层按产品/险种勾选可用字段」，不是「目录按产品/险种分叉」——
 * 目录一旦按产品分叉，任一字段的口径就有 N 份，跨域一致性无从保证。查询条件保留这两个字段
 * 是为了固定契约形状（回显给调用方），不是预留过滤开关。</p>
 *
 * <p>🔴 <b>业务时点解析（{@code G07/AC-05} ③）</b>：按 {@code businessDate} 命中当时生效的版本；
 * 早于最早版本 ⇒ 返回最早版本并标记 {@code fallback=true}（不报错）；落在版本空档 ⇒ 拒绝。
 * 降级与空档的区分理由见 {@link PolicyFieldCatalogPublication#resolveAt}。</p>
 */
@Service
@RequiredArgsConstructor
public class PolicyFieldCatalogApplicationService {

    private final PolicyFieldCatalogVersionViewRepository versionRepository;
    private final PolicyFieldCatalogViewRepository viewRepository;
    private final PolicyFieldCatalogAssembler assembler;

    /**
     * 查询指定业务时点生效的权威字段目录。
     *
     * @throws PolicyFieldCatalogValidationException 条件为空、版本表为空、版本链不自洽、
     *                                               时点落在版本空档、或目录内容与版本头哈希不符
     */
    public PolicyFieldCatalogSnapshot getCatalogAt(PolicyFieldCatalogCriteria criteria) {
        if (criteria == null) {
            throw new PolicyFieldCatalogValidationException("字段目录查询条件不能为空");
        }
        List<PolicyFieldCatalogPublication> published = versionRepository
                .findAllByOrderByEffectiveFromAscCatalogVersionAsc()
                .stream()
                .map(assembler::toPublication)
                .toList();
        PolicyFieldCatalogPublication.Resolved resolved =
                PolicyFieldCatalogPublication.resolveAt(published, criteria.businessDate());
        PolicyFieldCatalogPublication publication = resolved.publication();
        List<PolicyFieldCatalogView> rows =
                viewRepository.findByCatalogVersionOrderByFieldCodeAsc(publication.catalogVersion());
        PolicyFieldCatalog catalog = assembler.assemble(publication, rows);
        return new PolicyFieldCatalogSnapshot(catalog, criteria.businessDate(), publication, resolved.fallback());
    }
}
