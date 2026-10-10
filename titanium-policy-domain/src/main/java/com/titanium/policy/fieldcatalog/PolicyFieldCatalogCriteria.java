package com.titanium.policy.fieldcatalog;

import java.time.LocalDate;

/**
 * Policy 字段目录的**业务时点**查询条件。
 *
 * <p>{@code businessDate} 是唯一的取数维度——目录按业务时点解析应生效的版本
 * （{@code G07/AC-05}③）；早于最早版本时降级取最早版本，并在快照上标记
 * {@code fallback=true}（不报错），落在已发布版本的时间空档则拒绝。</p>
 *
 * <p>🔴 <b>{@code tenantId} 是死参数（{@code G07/AC-10}）：只做回显与审计，<u>不参与取数过滤</u>。</b>
 * 字段目录是<b>跨租户共享的平台元数据</b>——同一份目录对全部租户生效，故取数不得按租户过滤，
 * 仓储接口里也不存在带租户条件的查找方法。<b>租户差异不落在目录层</b>：某租户/产品/险种能用哪些字段，
 * 由<b>特征定义层</b>（feature-center 的 {@code FeatureDefinition}）勾选决定，目录只回答
 * 「系统里存在哪些字段、口径与能力是什么」。</p>
 *
 * <p>🔴 <b>为何保留这个死参数而不是删掉</b>：① 租户在<b>入口</b>处必须强制校验（缺租户即拒），
 * 「取数用不到」不等于「可以不传」；② 它随跨域契约回显给调用方，使响应自带租户上下文，
 * 便于跨租户串包的排查；③ 删除会改变契约形状。三者的前提都是「它不被用于过滤」。</p>
 *
 * <p>{@code productType}/{@code policyType} 同为**不参与取数的保留参数**：二者的差异表达方式
 * 同样是「特征定义层按产品/险种勾选可用字段」，不是「目录按产品/险种分叉」——
 * 目录一旦分叉，任一字段的口径就有 N 份，跨域一致性无从保证。保留它们只为固定契约形状。</p>
 *
 * <p>上述口径由 {@code FieldCatalogTenantDeadParamGuardTest} 机械守护：应用服务源码里一旦出现
 * 按租户过滤（读 {@code tenantId()} 或读列的 {@code getTenantId()}）、或仓储契约新增带租户条件的方法，
 * 守卫即失败；本 javadoc 与模块 README 中的约定文字也被同一条守卫核验。</p>
 */
public record PolicyFieldCatalogCriteria(
        String tenantId, String productType, String policyType, LocalDate businessDate) {

    public PolicyFieldCatalogCriteria {
        if (tenantId == null || tenantId.isBlank()) {
            throw new PolicyFieldCatalogValidationException("租户ID不能为空");
        }
        if (businessDate == null) {
            throw new PolicyFieldCatalogValidationException("业务日期不能为空");
        }
        tenantId = tenantId.trim();
        productType = normalize(productType);
        policyType = normalize(policyType);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
