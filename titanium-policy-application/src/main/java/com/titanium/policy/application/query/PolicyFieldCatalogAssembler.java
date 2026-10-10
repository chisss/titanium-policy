package com.titanium.policy.application.query;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.titanium.metadata.fieldcatalog.FieldAnalysisCapability;
import com.titanium.policy.fieldcatalog.PolicyFieldCapability;
import com.titanium.policy.fieldcatalog.PolicyFieldCatalog;
import com.titanium.policy.fieldcatalog.PolicyFieldCatalogPublication;
import com.titanium.policy.fieldcatalog.PolicyFieldCatalogValidationException;
import com.titanium.policy.fieldcatalog.PolicyFieldDescriptor;
import com.titanium.policy.query.view.PolicyFieldCatalogVersionView;
import com.titanium.policy.query.view.PolicyFieldCatalogView;

/**
 * 保单字段目录装配器：目录表行 / 版本头行 → 领域对象。
 *
 * <p>单独成类（而非内联进应用服务）有两条依据：① 单次组装 10 个组件，属项目红线
 * 「&gt;5 字段的对象组装必须抽象 Assembler/Builder」；② 装配本身是<strong>可脱离容器 new 直测</strong>
 * 的纯逻辑，独立后无需 Spring 上下文即可验证边界。</p>
 *
 * <p>🔴 <b>空表即拒（fail-closed）</b>：{@code t_policy_field_catalog} 无行意味着
 * 目录<strong>尚未初始化</strong>（既无 Liquibase 种子也未跑 starter 的 {@code seed-mode=init}，
 * 见 {@code g08-06}），此时返回空目录会让消费方把「没有字段可分析」误读为
 * 「这个域没有可分析字段」——保全域三处生产调用点（配置发布校验 / 字段草稿受理 / 字段执行）
 * 会因此静默放行非法字段。领域目录 record 自身也拒绝空清单，此处提前给出可定位的错误信息。</p>
 */
@Component
public class PolicyFieldCatalogAssembler {

    /**
     * 版本头行 → 领域版本头。
     *
     * <p>逐列直读，不做任何兜底（列语义与 {@code PolicyFieldCatalogPublication} 一一对应）；
     * 列被改坏时（哈希格式、区间方向、必填为空）由该 record 的紧凑构造器当场拒绝。</p>
     */
    public PolicyFieldCatalogPublication toPublication(PolicyFieldCatalogVersionView view) {
        if (view == null) {
            throw new PolicyFieldCatalogValidationException("字段目录版本头行不能为空");
        }
        return new PolicyFieldCatalogPublication(
                view.getCatalogVersion(),
                view.getContentHash(),
                view.getEffectiveFrom(),
                view.getEffectiveTo(),
                view.getPublishedBy(),
                view.getPublishedAt());
    }

    /**
     * 某版本的字段行 → 该版本的领域目录内容。
     *
     * <p>🔴 <b>内容哈希取自版本头冻结值，而不是"算完就丢"</b>：{@link PolicyFieldCatalog} 的紧凑构造器
     * 会用行内容重算哈希并与传入值比对，不符即拒绝。这使
     * 「改了行内容而没升版本」（{@code G07/AC-05} ② 禁止的动作）在<strong>每次读取</strong>时即被拦下，
     * 而不是把一份「版本号说是 v2、内容其实是 v3」的目录静默发给保全域。</p>
     *
     * <p>本方法不含的语义：版本归属（按业务日期解析）与降级标记——那属应用服务的编排职责，
     * 其结果由 {@code PolicyFieldCatalogSnapshot} 组合并自校验。</p>
     *
     * @param publication 已解析出的版本头（提供版本号与冻结哈希）
     * @param rows        该版本的字段行，须已按字段码稳定排序
     * @return 校验通过、内容哈希与版本头一致的领域目录
     * @throws PolicyFieldCatalogValidationException 行集为空、行归属他版本、或内容哈希不符
     */
    public PolicyFieldCatalog assemble(PolicyFieldCatalogPublication publication, List<PolicyFieldCatalogView> rows) {
        if (publication == null) {
            throw new PolicyFieldCatalogValidationException("字段目录版本头不能为空");
        }
        List<PolicyFieldDescriptor> descriptors = toDescriptors(publication.catalogVersion(), rows);
        return new PolicyFieldCatalog(publication.catalogVersion(), publication.contentHash(), descriptors);
    }

    /**
     * 行集 → 该内容的领域目录，**哈希由行内容算出**而非取自版本头。
     *
     * <p>用途只有一个：取得「这批行应当冻结的哈希」。写版本头之前必须先把哈希算出来
     * （{@code PolicyFieldCatalogPublication.publish} 的候选版本头就要求携带它），因此发布/导入
     * 路径（如 {@code g08-06} 的 {@code seed-mode=init}）需要本方法；装配路径的单测亦以
     * 「先算后冻结」的方式构造与行集自洽的版本头。</p>
     *
     * <p>🔴 <b>读取路径不得使用本方法</b>——它不校验冻结哈希，用它读目录等于把
     * 「行内容被改动而版本没升」（{@code G07/AC-05} ② 禁止的动作）放行。</p>
     */
    public PolicyFieldCatalog computeContent(String catalogVersion, List<PolicyFieldCatalogView> rows) {
        if (catalogVersion == null || catalogVersion.isBlank()) {
            throw new PolicyFieldCatalogValidationException("字段目录版本不能为空");
        }
        return new PolicyFieldCatalog(catalogVersion.trim(), null, toDescriptors(catalogVersion, rows));
    }

    /** 行集 → 描述符清单（含空行、跨版本行的守卫），两条装配路径共用。 */
    private List<PolicyFieldDescriptor> toDescriptors(String catalogVersion, List<PolicyFieldCatalogView> rows) {
        if (rows == null || rows.isEmpty()) {
            throw new PolicyFieldCatalogValidationException("字段目录版本 " + catalogVersion
                    + " 无任何字段行：t_policy_field_catalog 未初始化，或该版本尚未导入行"
                    + "（须由 Liquibase 种子或 starter 在 seed-mode=init 导入）");
        }
        if (rows.stream().anyMatch(row -> row == null || !catalogVersion.equals(row.getCatalogVersion()))) {
            throw new PolicyFieldCatalogValidationException("字段目录行与版本头不同源：版本头 "
                    + catalogVersion + "，行内版本 "
                    + rows.stream().filter(Objects::nonNull)
                            .map(PolicyFieldCatalogView::getCatalogVersion).distinct().toList()
                    + "（含空行时为空）");
        }
        return rows.stream().map(this::toDescriptor).toList();
    }

    private PolicyFieldDescriptor toDescriptor(PolicyFieldCatalogView row) {
        return new PolicyFieldDescriptor(
                row.getFieldCode(),
                row.getObjectType(),
                row.getValueType(),
                row.getLabelKey(),
                row.isCollection(),
                row.getObjectIdentityField(),
                toCapability(row),
                row.getSensitivity(),
                row.getMaskingPolicy(),
                row.getDeprecatedAt(),
                toAnalysis(row));
    }

    private PolicyFieldCapability toCapability(PolicyFieldCatalogView row) {
        return new PolicyFieldCapability(
                row.isReadable(),
                row.isProposable(),
                row.isClearable(),
                row.isExecutionSupported(),
                row.isRequiresObjectId(),
                row.getChangeTypeCode());
    }

    /**
     * 读 4 个分析位（G07/g07-03）。
     *
     * <p>🔴 <b>逐列直读，不做任何兜底或推断</b>：表列是这四位唯一的权威来源，若此处按
     * {@code valueType} 就地推导，则运营在表里显式声明的口径会被代码覆盖，且「表写了什么」
     * 与「系统认什么」从此分叉。表里写了非法组合（如 TEXT 却 aggregatable=1）时，
     * 由 {@code PolicyFieldDescriptor} 的紧凑构造器当场拒绝——fail-closed，
     * 而不是替运营"修正"成一个看起来合理的值。</p>
     */
    private FieldAnalysisCapability toAnalysis(PolicyFieldCatalogView row) {
        return new FieldAnalysisCapability(
                row.isAnalyzable(),
                row.isAggregatable(),
                row.isGroupable(),
                row.isFilterable());
    }
}
