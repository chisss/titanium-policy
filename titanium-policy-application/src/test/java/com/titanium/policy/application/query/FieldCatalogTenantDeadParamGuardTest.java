package com.titanium.policy.application.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.policy.fieldcatalog.PolicyFieldCatalogCriteria;
import com.titanium.policy.fieldcatalog.PolicyFieldCatalogValidationException;
import com.titanium.policy.query.repository.PolicyFieldCatalogVersionViewRepository;
import com.titanium.policy.query.repository.PolicyFieldCatalogViewRepository;

/**
 * 字段目录「租户不过滤」口径的守卫（{@code G07/AC-10}）。
 *
 * <p>被守护的口径：字段目录是<b>跨租户共享的平台元数据</b>，查询条件里的 {@code tenantId} 是
 * <b>死参数</b>——只回显、不取数；租户差异落在<b>特征定义层</b>。</p>
 *
 * <p>四组判据各锁一面，缺一即口径可被静默推翻：</p>
 * <ol>
 *   <li><b>服务层不读租户</b>（结构）——读都不读，就无从过滤。</li>
 *   <li><b>仓储契约无租户条件方法</b>（反射）——即使服务不读，仓储也不该提供这条路径。</li>
 *   <li><b>两处文档记录该约定</b>（文本）——javadoc 与模块 README，使「死参数」不被后来者误当遗漏删掉。</li>
 *   <li><b>租户仍强制必填</b>（行为）——死参数 ≠ 可省略参数，缺租户必须拒绝。</li>
 * </ol>
 *
 * <p>🔴 <b>本类判据的射程要说清</b>：①③ 是文本判据，只能证明「该写法/该段文字<b>存在或不存在</b>」，
 * 不能证明其语义正确（例如把过滤写成 {@code rows.stream().filter(r -> false)} 时本类不红）——
 * 语义由 {@code PolicyFieldCatalogApplicationServiceTest} 的行为用例承担；
 * ② 是反射判据，只覆盖<b>本类直接声明的</b>方法（继承自 {@code JpaRepository} 的方法不在内），
 * 故配了方法数下界断言，防反射取不到东西时空转通过。</p>
 */
class FieldCatalogTenantDeadParamGuardTest {

    private static final String SERVICE_SOURCE =
            "src/main/java/com/titanium/policy/application/query/PolicyFieldCatalogApplicationService.java";
    private static final String CRITERIA_SOURCE =
            "../titanium-policy-domain/src/main/java/com/titanium/policy/fieldcatalog/PolicyFieldCatalogCriteria.java";
    private static final String README = "../README.md";

    /** README 中承载该口径的小节标题（缺失即失败——约定必须有落脚点）。 */
    private static final String README_SECTION_HEADING = "### 字段目录与业务时点";

    /**
     * 两处文档都必须出现的措辞。取的是**口径的判别性表述**而非泛词：
     * {@code 死参数}（该参数不参与取数）、{@code 跨租户共享}（目录的可见性）、
     * {@code 特征定义}（租户差异的正确落点）、{@code 不参与取数过滤}（过滤动作被明令禁止）。
     */
    private static final List<String> REQUIRED_PHRASES =
            List.of("死参数", "跨租户共享", "特征定义", "不参与取数过滤");

    /** README 小节长度下界：防止标题还在、内容被清空（空集合不产生条目）。 */
    private static final int README_SECTION_MIN_LENGTH = 300;

    // ------------------------------------------------------------ ① 服务层不读租户

    @Test
    @DisplayName("AC-10① 应用服务源码不得读取租户（含日志），代码里出现 tenantId 即失败")
    void serviceMustNotReadTenantId() {
        String code = stripCommentsAndStringLiterals(readFile(SERVICE_SOURCE));

        // 计数独立报出：0 之外还要证明「确实扫到了代码」——否则文件路径错、全被剥掉也会是 0
        assertTrue(code.contains("class PolicyFieldCatalogApplicationService"),
                "剥注释后未扫到类声明，说明解析或路径失效（本断言防 0 命中恒真）");
        assertTrue(code.length() > 500, "剥注释后的代码长度异常，疑解析失效");

        assertEquals(0, countOf(code, "tenantId"), "服务层不得出现 tenantId（读即可能被用于过滤）");
        assertEquals(0, countOf(code, "getTenantId"), "服务层不得读取行/版本头的租户列");
    }

    // ------------------------------------------------------------ ② 仓储契约无租户方法

    @Test
    @DisplayName("AC-10② 两个读侧仓储接口不得声明任何带租户条件的方法")
    void repositoriesMustNotDeclareTenantScopedFinders() {
        assertNoTenantMethod(PolicyFieldCatalogViewRepository.class, 1);
        assertNoTenantMethod(PolicyFieldCatalogVersionViewRepository.class, 1);
    }

    // ------------------------------------------------------------ ③ 两处文档

    @Test
    @DisplayName("AC-10③ PolicyFieldCatalogCriteria 的 javadoc 必须记录死参数口径")
    void criteriaJavadocMustDocumentDeadParam() {
        String source = readFile(CRITERIA_SOURCE);
        int declaration = source.indexOf("public record PolicyFieldCatalogCriteria");
        assertTrue(declaration > 0, "未找到条件 record 的声明：源码结构已变，本判据的取法须同步调整");

        String javadoc = source.substring(0, declaration);
        REQUIRED_PHRASES.forEach(phrase -> assertTrue(javadoc.contains(phrase),
                "PolicyFieldCatalogCriteria 的 javadoc 缺少口径措辞: " + phrase));
    }

    @Test
    @DisplayName("AC-10③ 模块 README 的「字段目录与业务时点」小节必须记录死参数口径")
    void readmeMustDocumentDeadParam() {
        String readme = readFile(README);
        int start = readme.indexOf(README_SECTION_HEADING);
        assertTrue(start >= 0, "README 缺少小节 " + README_SECTION_HEADING + "（约定必须有落脚点）");
        int end = readme.indexOf("\n### ", start + README_SECTION_HEADING.length());
        String section = end < 0 ? readme.substring(start) : readme.substring(start, end);

        assertTrue(section.length() >= README_SECTION_MIN_LENGTH,
                "小节内容长度 " + section.length() + " 低于下界 " + README_SECTION_MIN_LENGTH + "，疑被清空");
        REQUIRED_PHRASES.forEach(phrase -> assertTrue(section.contains(phrase),
                "README 小节缺少口径措辞: " + phrase));
    }

    // ------------------------------------------------------------ ④ 死参数仍必填

    @Test
    @DisplayName("AC-10④ 死参数不等于可省略：缺租户仍必须拒绝")
    void tenantIdRemainsRequiredEvenThoughUnused() {
        assertThrows(PolicyFieldCatalogValidationException.class,
                () -> new PolicyFieldCatalogCriteria(null, "LIFE", "INDIVIDUAL", LocalDate.of(2026, 9, 14)));
        assertThrows(PolicyFieldCatalogValidationException.class,
                () -> new PolicyFieldCatalogCriteria("  ", "LIFE", "INDIVIDUAL", LocalDate.of(2026, 9, 14)));
        assertThrows(PolicyFieldCatalogValidationException.class,
                () -> new PolicyFieldCatalogCriteria("tenant-1", "LIFE", "INDIVIDUAL", null));
        assertEquals("tenant-1", new PolicyFieldCatalogCriteria(
                " tenant-1 ", "LIFE", "INDIVIDUAL", LocalDate.of(2026, 9, 14)).tenantId());
    }

    // ------------------------------------------------------------ 工具

    private static void assertNoTenantMethod(Class<?> repositoryType, int minimumDeclaredMethods) {
        Method[] declared = repositoryType.getDeclaredMethods();
        assertTrue(declared.length >= minimumDeclaredMethods,
                repositoryType.getSimpleName() + " 反射取到的方法数 " + declared.length
                        + " 低于下界 " + minimumDeclaredMethods + "——反射失效会让本判据空转");
        List<String> offenders = Arrays.stream(declared)
                .map(Method::getName)
                .filter(name -> name.toLowerCase().contains("tenant"))
                .toList();
        assertEquals(List.of(), offenders,
                repositoryType.getSimpleName() + " 声明了带租户条件的方法——目录是跨租户共享元数据，取数不得按租户过滤");
    }

    private static int countOf(String text, String token) {
        int count = 0;
        int from = 0;
        while (true) {
            int at = text.indexOf(token, from);
            if (at < 0) {
                return count;
            }
            count++;
            from = at + token.length();
        }
    }

    /**
     * 单遍词法扫描剥离注释与字符串字面量内容（**不删空白**，保持字符位置语义简单）。
     *
     * <p>为何必须剥：本类要判「代码里不出现 tenantId」，而 javadoc 里恰恰**必须**出现该词——
     * 不剥注释则文档越合规、判据越失败（方向相反的假红）；不剥字符串则一行日志里的词形也会误伤。
     * 两遍 regex（先块注释再行注释）在 {@code /* // *}{@code /} 这类嵌套形态上会互相破坏，
     * 故按状态机一遍扫过。</p>
     */
    private static String stripCommentsAndStringLiterals(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int index = 0;
        while (index < source.length()) {
            char current = source.charAt(index);
            if (current == '/' && index + 1 < source.length() && source.charAt(index + 1) == '/') {
                while (index < source.length() && source.charAt(index) != '\n') {
                    index++;
                }
            } else if (current == '/' && index + 1 < source.length() && source.charAt(index + 1) == '*') {
                index += 2;
                while (index + 1 < source.length()
                        && !(source.charAt(index) == '*' && source.charAt(index + 1) == '/')) {
                    index++;
                }
                index = Math.min(index + 2, source.length());
            } else if (current == '"') {
                out.append("\"\"");
                index++;
                while (index < source.length() && source.charAt(index) != '"') {
                    index += source.charAt(index) == '\\' ? 2 : 1;
                }
                index = Math.min(index + 1, source.length());
            } else if (current == '\'') {
                out.append("''");
                index++;
                while (index < source.length() && source.charAt(index) != '\'') {
                    index += source.charAt(index) == '\\' ? 2 : 1;
                }
                index = Math.min(index + 1, source.length());
            } else {
                out.append(current);
                index++;
            }
        }
        return out.toString();
    }

    private static String readFile(String relativePath) {
        Path path = null;
        for (Path candidate : List.of(
                Paths.get(relativePath),
                Paths.get("titanium-policy", relativePath))) {
            if (Files.isRegularFile(candidate)) {
                path = candidate;
                break;
            }
        }
        if (path == null) {
            fail("找不到文件：" + Paths.get(relativePath).toAbsolutePath().normalize()
                    + "（工作目录 " + Paths.get("").toAbsolutePath() + "）——"
                    + "本守卫刻意读真实文件，文件被移动即显性失败");
        }
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException("读取文件失败: " + path, exception);
        }
    }

    @Test
    @DisplayName("自检：剥注释/字符串的扫描器确实会剥掉注释与字面量里的词、保留代码里的标识符")
    void stripperMustNotBeVacuous() {
        String stripped = stripCommentsAndStringLiterals(
                "class A { // tenantId\n /* tenantId */ String s = \"tenantId\"; String t = tenantId; }");

        assertFalse(stripped.contains("\"tenantId\""), "字符串字面量内容必须被剥掉");
        assertEquals(0, countOf(stripped, "// tenantId"), "行注释必须被剥掉");
        assertEquals(1, countOf(stripped, "tenantId"),
                "四处 tenantId 中只有代码里的标识符应保留（行注释 / 块注释 / 字符串字面量各一处须剥掉）");
    }
}
