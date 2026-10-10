package com.titanium.policy.fieldcatalog;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Policy 已发布的字段目录**版本头**（G07/g07-04）：一个版本的哈希、生效区间与发布证据。
 *
 * <p>对应表 {@code t_policy_field_catalog_version} 一行；与
 * {@link PolicyFieldCatalog}（版本**内容**：版本号 + 哈希 + 字段清单）的分工是
 * 「版本级事实」与「字段级内容」——后者是 1:N 里的 N，前者是本 record 承载的 1。</p>
 *
 * <p>发布证据刻意**不新造审计模型**，沿用保全域已验证的形态（{@code MaintenancePublicationEvidence}
 * 记录版本/哈希/时间）：版本号 + 内容哈希 + 发布人 + 发布时间四件套即可回答
 * 「这一版是谁在什么时候、以什么内容发出去的」，不再另建一张审计表。</p>
 *
 * <p>🔴 <b>生效区间是半开区间 {@code [effectiveFrom, effectiveTo)}</b>：
 * {@code effectiveFrom} 含当日、{@code effectiveTo} **不含**当日，{@code effectiveTo} 为
 * {@code null} 表示开放区间。半开区间让相邻两版首尾相接（前一版的 {@code effectiveTo} 等于
 * 后一版的 {@code effectiveFrom}）而**无重叠、无空档**，业务时点的归属因此唯一。</p>
 *
 * <p>🔴 <b>版本号按「点分数字段」比较，不用字符串比较</b>（{@link #compareVersion}）：
 * 本仓版本号形如 {@code 2026.09.14.2}，字符串比较会把 {@code 2026.09.14.10} 判成小于
 * {@code 2026.09.14.9}——版本递增守卫若用字符串比较，等于在最需要它的那一天失效。</p>
 */
public record PolicyFieldCatalogPublication(
        String catalogVersion,
        String contentHash,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String publishedBy,
        LocalDateTime publishedAt) {

    private static final Pattern SHA_256_PATTERN = Pattern.compile("[a-f0-9]{64}");

    /** 版本号比较器（按 {@link #compareVersion} 语义），供排序与取最大版本使用。 */
    private static final Comparator<String> VERSION_ORDER = PolicyFieldCatalogPublication::compareVersion;

    public PolicyFieldCatalogPublication {
        catalogVersion = requireText(catalogVersion, "目录版本");
        publishedBy = requireText(publishedBy, "发布人");
        if (contentHash == null) {
            throw invalid("目录内容哈希不能为空");
        }
        contentHash = contentHash.trim().toLowerCase();
        if (!SHA_256_PATTERN.matcher(contentHash).matches()) {
            throw invalid("目录内容哈希必须为 SHA-256（64 位小写十六进制）: " + contentHash);
        }
        if (effectiveFrom == null) {
            throw invalid("目录版本生效起始日不能为空");
        }
        if (effectiveTo != null && !effectiveTo.isAfter(effectiveFrom)) {
            throw invalid("目录版本生效截止日必须晚于起始日（半开区间 [from, to)）: "
                    + effectiveFrom + " → " + effectiveTo);
        }
        if (publishedAt == null) {
            throw invalid("目录版本发布时间不能为空");
        }
    }

    /** 业务日期是否落在本版本生效区间内（半开区间 {@code [effectiveFrom, effectiveTo)}）。 */
    public boolean covers(LocalDate businessDate) {
        if (businessDate == null) {
            throw invalid("业务日期不能为空");
        }
        return !businessDate.isBefore(effectiveFrom)
                && (effectiveTo == null || businessDate.isBefore(effectiveTo));
    }

    /**
     * 按业务时点解析出应生效的目录版本（{@code G07/AC-05} ③）。
     *
     * <p>三条语义，缺一不可：</p>
     * <ol>
     *   <li><b>命中即返回</b>：恰好一版覆盖 {@code businessDate} ⇒ 返回它，{@code fallback=false}。</li>
     *   <li><b>早于最早版本 ⇒ 降级</b>：返回**最早**版本并标记 {@code fallback=true}，
     *       <b>不是报错</b>——否则历史保单的读侧会因目录缺失而全线失败（需求 §g07-04 第 4 点）。
     *       跨域同语义要求见《特征取数与规则引擎架构方案》§5.1 第 4 条。</li>
     *   <li><b>落在版本空档 ⇒ 拒绝</b>：既非命中、又不早于最早版本，说明已发布版本之间存在
     *       <b>时间空档</b>（前版已止、后版未起）。这是数据缺陷，**不得**降级为「取某一版」——
     *       降级会把「目录没发布全」静默伪装成「内容就是这样」。</li>
     * </ol>
     *
     * <p>另外在解析前校验整条版本链自洽（相邻版本不得重叠、版本号须随生效日严格递增）。
     * 这两条与 {@link #publish} 的发布守卫是**同一不变量的两个关口**：发布守卫在其被调用时挡住不合规的
     * 新版本，链校验在**每次读取**时兜住「绕过守卫直接改表」的情形（规则落在读取路径上才算活着）。</p>
     *
     * @throws PolicyFieldCatalogValidationException 版本表为空、版本链不自洽、或时点落在空档
     */
    public static Resolved resolveAt(List<PolicyFieldCatalogPublication> published, LocalDate businessDate) {
        if (businessDate == null) {
            throw invalid("业务日期不能为空");
        }
        List<PolicyFieldCatalogPublication> chain = sortedChain(published);
        Optional<PolicyFieldCatalogPublication> matched = chain.stream()
                .filter(publication -> publication.covers(businessDate))
                .findFirst();
        if (matched.isPresent()) {
            return new Resolved(matched.get(), false);
        }
        PolicyFieldCatalogPublication earliest = chain.get(0);
        if (businessDate.isBefore(earliest.effectiveFrom())) {
            return new Resolved(earliest, true);
        }
        throw invalid("业务日期 " + businessDate + " 落在已发布目录版本的时间空档中（最早版本自 "
                + earliest.effectiveFrom() + " 起生效），须补发覆盖该时点的目录版本");
    }

    /**
     * 发布守卫（{@code G07/AC-05} ①②）：返回**应当留存的版本头**，不合规即拒绝。
     *
     * <ul>
     *   <li>① <b>内容不变重复发布 ⇒ 幂等</b>：同一版本号 + 同一内容哈希 ⇒ 返回既有版本，
     *       不新增版本、哈希不变、版本号不递增。</li>
     *   <li>② <b>同版本号换了内容 ⇒ 拒绝</b>：版本号是内容的身份，同号不同内容会让
     *       「消费方按版本号缓存/审计」全线失真。</li>
     *   <li>③ <b>内容变了而版本号未递增 ⇒ 拒绝</b>：新版本号必须**严格大于**既有最大版本号
     *       （按点分数字段比较）。内容相同但版本号递增**允许**——那是「同一份内容换生效期」的
     *       正常运营动作，本条只约束内容变更。</li>
     * </ul>
     *
     * <p>🔴 <b>本方法的运行时保证在读取侧同样成立</b>：运营侧的发布当前以 SQL 完成（本域无发布入口），
     * 故「改了内容而没升版本」这条缺陷由读取路径兜住——装配器把版本头冻结的哈希传进
     * {@link PolicyFieldCatalog} 构造器，行内容一旦被改动，算出的哈希与冻结值不符即当场拒绝。
     * 本方法把同一条规则前移到发布动作的门口，两者不是重复实现，而是同一不变量的两个关口。</p>
     */
    public static PolicyFieldCatalogPublication publish(
            List<PolicyFieldCatalogPublication> published, PolicyFieldCatalogPublication candidate) {
        if (candidate == null) {
            throw invalid("待发布的目录版本不能为空");
        }
        List<PolicyFieldCatalogPublication> existing = published == null ? List.of() : published;
        Optional<PolicyFieldCatalogPublication> sameVersion = existing.stream()
                .filter(publication -> publication.catalogVersion().equals(candidate.catalogVersion()))
                .findFirst();
        if (sameVersion.isPresent()) {
            PolicyFieldCatalogPublication current = sameVersion.get();
            if (current.contentHash().equals(candidate.contentHash())) {
                return current;
            }
            throw invalid("目录版本 " + candidate.catalogVersion()
                    + " 已发布且内容哈希不同（已发布 " + current.contentHash()
                    + "，待发布 " + candidate.contentHash() + "）——同版本号不得换内容，须递增版本号");
        }
        existing.stream()
                .max(Comparator.comparing(PolicyFieldCatalogPublication::catalogVersion, VERSION_ORDER))
                .ifPresent(latest -> {
                    if (compareVersion(candidate.catalogVersion(), latest.catalogVersion()) <= 0) {
                        throw invalid("目录内容已变更但版本号未递增（当前最大版本 " + latest.catalogVersion()
                                + "，待发布 " + candidate.catalogVersion() + "）——拒绝发布");
                    }
                });
        return candidate;
    }

    /** 解析结果：业务时点应生效的版本 + 是否由「早于最早版本」降级而来。 */
    public record Resolved(PolicyFieldCatalogPublication publication, boolean fallback) {

        public Resolved {
            if (publication == null) {
                throw invalid("解析结果中的目录版本不能为空");
            }
        }
    }

    /**
     * 版本链规整与自洽校验：按生效日升序（同日起始按版本号升序，保证结果确定），
     * 并断言相邻版本**不重叠**、版本号随生效日**严格递增**。
     */
    private static List<PolicyFieldCatalogPublication> sortedChain(
            List<PolicyFieldCatalogPublication> published) {
        if (published == null || published.isEmpty()) {
            throw invalid("字段目录版本未初始化：t_policy_field_catalog_version 无任何行，"
                    + "须由 Liquibase 版本头种子导入");
        }
        if (published.stream().anyMatch(Objects::isNull)) {
            throw invalid("字段目录版本链不能包含空版本");
        }
        List<PolicyFieldCatalogPublication> chain = published.stream()
                .sorted(Comparator.comparing(PolicyFieldCatalogPublication::effectiveFrom)
                        .thenComparing(PolicyFieldCatalogPublication::catalogVersion, VERSION_ORDER))
                .toList();
        for (int index = 0; index < chain.size() - 1; index++) {
            PolicyFieldCatalogPublication current = chain.get(index);
            PolicyFieldCatalogPublication next = chain.get(index + 1);
            if (current.effectiveTo() == null || current.effectiveTo().isAfter(next.effectiveFrom())) {
                throw invalid("目录版本生效区间重叠：" + current.catalogVersion() + "（起始 "
                        + current.effectiveFrom() + "，截止 "
                        + (current.effectiveTo() == null ? "开放" : current.effectiveTo())
                        + "）与 " + next.catalogVersion() + "（起始 " + next.effectiveFrom() + "）");
            }
            if (compareVersion(next.catalogVersion(), current.catalogVersion()) <= 0) {
                throw invalid("目录版本号未随生效日递增：" + current.catalogVersion()
                        + "（生效 " + current.effectiveFrom() + "）之后的 " + next.catalogVersion()
                        + "（生效 " + next.effectiveFrom() + "）不更大");
            }
        }
        return chain;
    }

    /**
     * 点分数字段比较：数值段按数值比（{@code 10 > 9}），数值段大于非数值段，其余按字典序；
     * 段数不同时缺失段视为最小（{@code 1.2 < 1.2.0}）。
     */
    private static int compareVersion(String left, String right) {
        String[] leftSegments = left.split("\\.", -1);
        String[] rightSegments = right.split("\\.", -1);
        int length = Math.max(leftSegments.length, rightSegments.length);
        for (int index = 0; index < length; index++) {
            int compared = compareSegment(
                    index < leftSegments.length ? leftSegments[index] : "",
                    index < rightSegments.length ? rightSegments[index] : "");
            if (compared != 0) {
                return compared;
            }
        }
        return 0;
    }

    private static int compareSegment(String left, String right) {
        boolean leftNumeric = isDigits(left);
        boolean rightNumeric = isDigits(right);
        if (leftNumeric && rightNumeric) {
            return Long.compare(Long.parseLong(left), Long.parseLong(right));
        }
        if (leftNumeric != rightNumeric) {
            return leftNumeric ? 1 : -1;
        }
        return left.compareTo(right);
    }

    private static boolean isDigits(String value) {
        return !value.isEmpty() && value.chars().allMatch(Character::isDigit);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw invalid(fieldName + "不能为空");
        }
        return value.trim();
    }

    private static PolicyFieldCatalogValidationException invalid(String message) {
        return new PolicyFieldCatalogValidationException(message);
    }
}
