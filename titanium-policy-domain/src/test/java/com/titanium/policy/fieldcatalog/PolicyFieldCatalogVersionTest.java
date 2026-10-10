package com.titanium.policy.fieldcatalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.policy.fieldcatalog.PolicyFieldCatalogPublication.Resolved;

/**
 * 字段目录版本头 {@link PolicyFieldCatalogPublication} 的单元测试（{@code G07/AC-05}）。
 *
 * <p>三组正反配对：</p>
 * <ol>
 *   <li><b>①</b> 内容不变重复发布 ⇒ 幂等（哈希不变、版本号不递增、不新增版本）；
 *       反例：同版本号换内容 ⇒ 拒绝。</li>
 *   <li><b>②</b> 内容变更但版本号未递增 ⇒ 拒绝发布；正例：版本号递增 ⇒ 放行。</li>
 *   <li><b>③</b> 按业务日期解析出当时生效的版本；反例：落在版本空档 ⇒ 拒绝（不得降级）。</li>
 * </ol>
 *
 * <p>本类只测领域 record 本身，不经过任何仓储或容器——这正是版本规则能被「直测」的意义：
 * 规则若只落在读路径上，改数据（绕过发布守卫）时就没有第二道关口。</p>
 */
class PolicyFieldCatalogVersionTest {

    private static final String V1 = "2026.09.14.2";
    private static final String V2 = "2026.10.01.1";
    private static final String HASH_A = "a".repeat(64);
    private static final String HASH_B = "b".repeat(64);

    private static final LocalDate SEPT_14 = LocalDate.of(2026, 9, 14);
    private static final LocalDate OCT_01 = LocalDate.of(2026, 10, 1);

    // ------------------------------------------------------------ AC-05 ③ 业务时点解析

    @Test
    @DisplayName("AC-05③ 按业务日期命中当时生效版本，半开区间边界各归一侧")
    void shouldResolveVersionCoveringBusinessDate() {
        List<PolicyFieldCatalogPublication> published = List.of(
                publication(V1, HASH_A, SEPT_14, OCT_01),
                publication(V2, HASH_B, OCT_01, null));

        // 区间内
        assertEquals(V1, resolve(published, LocalDate.of(2026, 9, 20)).publication().catalogVersion());
        assertEquals(V2, resolve(published, LocalDate.of(2026, 12, 31)).publication().catalogVersion());
        assertFalse(resolve(published, LocalDate.of(2026, 9, 20)).fallback());
        assertFalse(resolve(published, LocalDate.of(2026, 12, 31)).fallback());

        // 🔴 半开区间 [from, to)：起始日归本版、截止日归下一版，两版首尾相接而无重叠
        assertEquals(V1, resolve(published, SEPT_14).publication().catalogVersion(), "起始日含当日 ⇒ 归前一版");
        assertEquals(V1, resolve(published, OCT_01.minusDays(1)).publication().catalogVersion(), "截止日前一天仍归前一版");
        assertEquals(V2, resolve(published, OCT_01).publication().catalogVersion(), "截止日不含当日 ⇒ 归后一版");
    }

    @Test
    @DisplayName("AC-05③ 业务日期早于最早版本 ⇒ 返回最早版本并标记降级（不报错）")
    void shouldFallBackToEarliestVersionWhenBusinessDatePrecedesIt() {
        List<PolicyFieldCatalogPublication> published = List.of(
                publication(V1, HASH_A, SEPT_14, OCT_01),
                publication(V2, HASH_B, OCT_01, null));

        Resolved resolved = resolve(published, LocalDate.of(2020, 1, 1));

        assertEquals(V1, resolved.publication().catalogVersion(), "降级取的是最早版本，不是最新版");
        assertTrue(resolved.fallback(), "降级必须显式标记，调用方据此可见「内容不是该时点的」");
    }

    @Test
    @DisplayName("AC-05③ 反例：业务日期落在版本空档 ⇒ 拒绝，不得降级伪装")
    void shouldRejectBusinessDateFallingIntoGap() {
        // 前一版止于 09-20，后一版 10-01 才生效 ⇒ [09-20, 10-01) 是空档
        List<PolicyFieldCatalogPublication> published = List.of(
                publication(V1, HASH_A, SEPT_14, LocalDate.of(2026, 9, 20)),
                publication(V2, HASH_B, OCT_01, null));

        PolicyFieldCatalogValidationException exception = assertThrows(
                PolicyFieldCatalogValidationException.class,
                () -> resolve(published, LocalDate.of(2026, 9, 25)));

        assertTrue(exception.getMessage().contains("空档"), "须点明是时间空档而非普通缺失");
        assertTrue(exception.getMessage().contains("须补发"), "须给出处置方向");
    }

    @Test
    @DisplayName("AC-05③ 反例：版本表为空 ⇒ 拒绝（未初始化不得读成空目录）")
    void shouldRejectEmptyVersionTable() {
        assertTrue(assertThrows(PolicyFieldCatalogValidationException.class,
                () -> resolve(List.of(), SEPT_14)).getMessage().contains("未初始化"));
        assertThrows(PolicyFieldCatalogValidationException.class, () -> resolve(null, SEPT_14));
        assertThrows(PolicyFieldCatalogValidationException.class, () -> resolve(List.of(publication(V1, HASH_A, SEPT_14, null)), null));
    }

    @Test
    @DisplayName("AC-05③ 读取侧链校验：区间重叠、版本号未随生效日递增均拒绝")
    void shouldRejectInconsistentVersionChainOnRead() {
        // 前一版开放区间（effectiveTo=null）却又有后继版本 ⇒ 区间重叠
        assertTrue(assertThrows(PolicyFieldCatalogValidationException.class,
                () -> resolve(List.of(
                        publication(V1, HASH_A, SEPT_14, null),
                        publication(V2, HASH_B, OCT_01, null)), OCT_01))
                .getMessage().contains("重叠"));

        // 生效日靠后的版本号反而更小 ⇒ 链不自洽
        assertTrue(assertThrows(PolicyFieldCatalogValidationException.class,
                () -> resolve(List.of(
                        publication(V2, HASH_A, SEPT_14, OCT_01),
                        publication(V1, HASH_B, OCT_01, null)), OCT_01))
                .getMessage().contains("未随生效日递增"));
    }

    // ------------------------------------------------------------ AC-05 ①② 发布守卫

    @Test
    @DisplayName("AC-05① 内容不变重复发布 ⇒ 幂等：返回既有版本，哈希不变、版本号不递增")
    void shouldBeIdempotentWhenSameContentRepublished() {
        PolicyFieldCatalogPublication existing = publication(V1, HASH_A, SEPT_14, null);
        PolicyFieldCatalogPublication sameContent = publication(V1, HASH_A, SEPT_14, null);

        PolicyFieldCatalogPublication result =
                PolicyFieldCatalogPublication.publish(List.of(existing), sameContent);

        assertSame(existing, result, "重复发布必须原样返回既有版本，不得产生第二个版本");
        assertEquals(HASH_A, result.contentHash(), "哈希不得因重复发布而变化");
        assertEquals(V1, result.catalogVersion(), "版本号不得因重复发布而递增");
    }

    @Test
    @DisplayName("AC-05① 反例：同版本号换了内容 ⇒ 拒绝（版本号是内容的身份）")
    void shouldRejectSameVersionWithDifferentContent() {
        PolicyFieldCatalogPublication existing = publication(V1, HASH_A, SEPT_14, null);

        PolicyFieldCatalogValidationException exception = assertThrows(
                PolicyFieldCatalogValidationException.class,
                () -> PolicyFieldCatalogPublication.publish(
                        List.of(existing), publication(V1, HASH_B, SEPT_14, null)));

        assertTrue(exception.getMessage().contains("同版本号不得换内容"));
    }

    @Test
    @DisplayName("AC-05② 内容变更但版本号未递增 ⇒ 拒绝发布；递增则放行")
    void shouldRejectContentChangeWithoutVersionBump() {
        PolicyFieldCatalogPublication existing = publication(V1, HASH_A, SEPT_14, null);
        List<PolicyFieldCatalogPublication> published = List.of(existing);

        // 反例一：版本号原地不动（同版本号换内容已被上一条挡住，此处走的是「更小的新版本号」）
        assertTrue(assertThrows(PolicyFieldCatalogValidationException.class,
                () -> PolicyFieldCatalogPublication.publish(
                        published, publication("2026.09.01.1", HASH_B, SEPT_14, null)))
                .getMessage().contains("拒绝发布"));

        // 正例：版本号递增 ⇒ 放行
        PolicyFieldCatalogPublication next = publication(V2, HASH_B, OCT_01, null);
        assertSame(next, PolicyFieldCatalogPublication.publish(published, next));
    }

    @Test
    @DisplayName("🔴 版本号按点分数字段比较：2026.09.14.10 > 2026.09.14.9（字符串比较会判反）")
    void shouldCompareVersionSegmentsNumerically() {
        PolicyFieldCatalogPublication nine = publication("2026.09.14.9", HASH_A, SEPT_14, null);
        PolicyFieldCatalogPublication ten = publication("2026.09.14.10", HASH_B, OCT_01, null);

        // 若实现用 String.compareTo："2026.09.14.10" < "2026.09.14.9" ⇒ 正常发布会被拒
        assertSame(ten, PolicyFieldCatalogPublication.publish(List.of(nine), ten),
                "数值上更大的版本号必须被接受（字符串比较会在此拒绝合法发布）");
        // 反向必须被拒——同一个比较器的另一侧
        assertThrows(PolicyFieldCatalogValidationException.class,
                () -> PolicyFieldCatalogPublication.publish(List.of(ten), nine));
    }

    // ------------------------------------------------------------ 版本头自身不变量

    @Test
    @DisplayName("版本头自身不变量：哈希格式、区间方向、发布证据必填")
    void shouldRejectInvalidVersionHeaderFacts() {
        assertTrue(assertThrows(PolicyFieldCatalogValidationException.class,
                () -> publication(V1, "a".repeat(63), SEPT_14, null)).getMessage().contains("SHA-256"));
        assertTrue(assertThrows(PolicyFieldCatalogValidationException.class,
                () -> publication(V1, "z".repeat(64), SEPT_14, null)).getMessage().contains("SHA-256"));
        assertTrue(assertThrows(PolicyFieldCatalogValidationException.class,
                () -> publication(V1, null, SEPT_14, null)).getMessage().contains("不能为空"));
        assertTrue(assertThrows(PolicyFieldCatalogValidationException.class,
                () -> new PolicyFieldCatalogPublication(V1, HASH_A, null, null, "system",
                        LocalDateTime.of(2026, 9, 14, 10, 0))).getMessage().contains("生效起始日"));
        assertTrue(assertThrows(PolicyFieldCatalogValidationException.class,
                () -> new PolicyFieldCatalogPublication(V1, HASH_A, SEPT_14, SEPT_14, "system",
                        LocalDateTime.of(2026, 9, 14, 10, 0))).getMessage().contains("必须晚于起始日"));
        assertTrue(assertThrows(PolicyFieldCatalogValidationException.class,
                () -> new PolicyFieldCatalogPublication(V1, HASH_A, SEPT_14, null, " ",
                        LocalDateTime.of(2026, 9, 14, 10, 0))).getMessage().contains("发布人"));
        assertTrue(assertThrows(PolicyFieldCatalogValidationException.class,
                () -> new PolicyFieldCatalogPublication(V1, HASH_A, SEPT_14, null, "system", null))
                .getMessage().contains("发布时间"));
    }

    /** 开放区间（effectiveTo=null）覆盖其起始日之后的一切日期。 */
    @Test
    @DisplayName("开放区间：effectiveTo 为 null 时覆盖起始日之后的一切日期")
    void shouldCoverEverythingAfterStartForOpenRange() {
        PolicyFieldCatalogPublication open = publication(V1, HASH_A, SEPT_14, null);

        assertFalse(open.covers(SEPT_14.minusDays(1)));
        assertTrue(open.covers(SEPT_14));
        assertTrue(open.covers(LocalDate.of(2099, 1, 1)));
    }

    // ------------------------------------------------------------ 夹具

    private static Resolved resolve(List<PolicyFieldCatalogPublication> published, LocalDate businessDate) {
        return PolicyFieldCatalogPublication.resolveAt(published, businessDate);
    }

    private static PolicyFieldCatalogPublication publication(
            String catalogVersion, String contentHash, LocalDate effectiveFrom, LocalDate effectiveTo) {
        return new PolicyFieldCatalogPublication(catalogVersion, contentHash, effectiveFrom, effectiveTo,
                "system", LocalDateTime.of(2026, 9, 14, 10, 0));
    }
}
