package com.titanium.policy.fieldcatalog;

import java.time.LocalDate;

/**
 * Policy 字段目录的**业务时点快照**（G07/g07-04）：某业务日期上应当生效的目录内容。
 *
 * <p>这是读侧的唯一返回形态——应用层的目录应用服务（{@code PolicyFieldCatalogApplicationService}）
 * 不再返回裸的 {@link PolicyFieldCatalog}，因为「目录内容」本身不足以回答调用方真正要问的问题：
 * <b>「在我这笔业务发生的那一天，目录长什么样？」</b>。业务日期与生效版本必须成对交出，
 * 否则调用方拿到的是一份不知道属于哪个时点的内容，只能默认它是最新版——正是缺口本身。</p>
 *
 * <p>🔴 <b>紧凑构造器断言「内容」与「版本」同源</b>：{@code catalog.catalogVersion()} 必须等于
 * {@code publication.catalogVersion()}。这条断言让本 record 成为一个**可核验的快照**——
 * 版本头（生效区间/发布证据）与版本内容（字段清单）一旦被拼错（例如按版本解析却按最新版取数），
 * 快照在构造期即失败，而不是把一份「日期 A 的版本号 + 日期 B 的字段清单」静默发出去。</p>
 *
 * @param catalog      解析出的版本内容（版本号 + 内容哈希 + 字段清单）
 * @param businessDate 查询的业务日期
 * @param publication  该版本对应的版本头（生效区间 + 发布证据）
 * @param fallback     是否由「业务日期早于最早版本」降级而来；为 {@code true} 表示 {@code catalog} 并非
 *                     该时点的真实版本，调用方须显式可见——降级不得静默
 */
public record PolicyFieldCatalogSnapshot(
        PolicyFieldCatalog catalog,
        LocalDate businessDate,
        PolicyFieldCatalogPublication publication,
        boolean fallback) {

    public PolicyFieldCatalogSnapshot {
        if (catalog == null) {
            throw new PolicyFieldCatalogValidationException("字段目录快照的内容不能为空");
        }
        if (businessDate == null) {
            throw new PolicyFieldCatalogValidationException("字段目录快照的业务日期不能为空");
        }
        if (publication == null) {
            throw new PolicyFieldCatalogValidationException("字段目录快照的版本头不能为空");
        }
        if (!catalog.catalogVersion().equals(publication.catalogVersion())) {
            throw new PolicyFieldCatalogValidationException("字段目录快照的内容与版本头不同源：内容版本 "
                    + catalog.catalogVersion() + "，版本头 " + publication.catalogVersion());
        }
        if (fallback && !businessDate.isBefore(publication.effectiveFrom())) {
            throw new PolicyFieldCatalogValidationException("字段目录快照标记为降级，但业务日期 "
                    + businessDate + " 不早于版本生效起始日 " + publication.effectiveFrom());
        }
    }

    /** 目录版本号（转发自内容，便于读侧直接取用）。 */
    public String catalogVersion() {
        return catalog.catalogVersion();
    }

    /** 目录内容哈希（转发自内容）。 */
    public String contentHash() {
        return catalog.contentHash();
    }
}
