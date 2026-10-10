package com.titanium.policy.application.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldMaskingPolicy;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldObjectType;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldSensitivityLevel;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldValueType;
import com.titanium.metadata.fieldcatalog.FieldAnalysisCapability;
import com.titanium.policy.fieldcatalog.PolicyFieldCatalog;
import com.titanium.policy.fieldcatalog.PolicyFieldCatalogPublication;
import com.titanium.policy.fieldcatalog.PolicyFieldCatalogValidationException;
import com.titanium.policy.fieldcatalog.PolicyFieldDescriptor;
import com.titanium.policy.query.view.PolicyFieldCatalogView;

/**
 * {@link PolicyFieldCatalogAssembler} 的单元测试：目录表行 + 版本头 → 领域目录。
 *
 * <p>🔴 <b>本类读的是将要入库的那份种子 SQL 本身</b>（{@code liquibase/dml/...}，非副本）。
 * 这是「硬编码常量 → 目录表」<b>等价迁移</b>的最强判据：SQL 里任何一个属性写错
 * （掩码策略、能力位、对象标识字段……），装配出的 {@code contentHash} 就不再等于
 * 独立算出的锚，本类立刻报红。若改用测试内联夹具，SQL 写错而夹具正确时两侧都对不上——
 * 判据就断了。</p>
 *
 * <p>SQL 解析按<b>列名</b>而非下标取值：生成脚本若调整了列顺序，本类仍按名字正确取值；
 * 列名拼错则会因取不到值而显性失败（下标口径会静默错位）。</p>
 *
 * <p>🔴 <b>合成夹具不承担哈希判据</b>：形如「单行合成字段」「删掉一行后的 18 行」这类夹具的
 * 冻结哈希由领域 record 自身算出（只为满足读取侧的一致性校验），其上的断言只针对
 * <b>行为</b>（字段数、拒绝与否）——把这种自算值当哈希判据就是「期望值由被测代码自报」。
 * 真正的哈希判据只有一条：真实种子 SQL 对**独立 Python 锚**（{@link #CATALOG_HASH}）。</p>
 */
class PolicyFieldCatalogAssemblerTest {

    /** 行种子文件（相对模块目录；Maven surefire 的工作目录即模块根）。 */
    private static final String SEED_RELATIVE =
            "../titanium-policy-bootstrap/src/main/resources/liquibase/dml/"
                    + "policy_field_catalog_seed_202610011900_weisun_dml.sql";

    /** 版本头种子文件（G07/g07-04；版本号与冻结哈希的落库处）。 */
    private static final String VERSION_SEED_RELATIVE =
            "../titanium-policy-bootstrap/src/main/resources/liquibase/dml/"
                    + "policy_field_catalog_version_seed_202610021110_weisun_dml.sql";

    /**
     * 迁移后的内容哈希——**由独立实现的 Python 脚本算出**（{@code ~/.g07-snapshots/analysis-hash-g07-03a.py}，
     * 纯 Python 复刻 canonicalForm + SHA-256，不调用任何 Java 代码），不是从被测代码导出的。
     *
     * <p>该脚本自带复刻自检：用**旧的 15 分量**（不含分析位）复算，必须逐字复现 g07-02d 的既有锚
     * {@code 89ff1ece…}——复刻正确才输出本值。两侧独立（Java 走对象图、Python 走 SQL 字面量）
     * 而必须相等，任一方的字段口径、排序规则、连接符、编码漂移都会当场破裂。</p>
     *
     * <p>🔴 <b>为何不能把本值改成「跑一遍装配器看输出什么」</b>：那样判据的期望值由被测代码自报，
     * 算法改了它跟着变，断言恒真——正是「内联副本随权威源漂移」的形态。</p>
     */
    private static final String CATALOG_HASH =
            "541d557fa6be3df1a3a99aa0ae3cb0c94e1c92367808dc1730ff49fc06a6cfc2";

    private static final String VERSION = "2026.09.14.2";

    /** 版本头夹具：生效起始日取版本号日期段（与版本头种子同口径），截止日开放。 */
    private static PolicyFieldCatalogPublication publication(String catalogVersion, String contentHash) {
        return new PolicyFieldCatalogPublication(
                catalogVersion, contentHash, LocalDate.of(2026, 9, 14), null, "system",
                LocalDateTime.of(2026, 9, 14, 10, 0));
    }

    private static final PolicyFieldCatalogPublication PUBLICATION = publication(VERSION, CATALOG_HASH);

    // ------------------------------------------------------------ 判据

    @Test
    void shouldFailClosedWhenCatalogTableIsNotSeeded() {
        PolicyFieldCatalogAssembler assembler = new PolicyFieldCatalogAssembler();

        PolicyFieldCatalogValidationException empty = assertThrows(
                PolicyFieldCatalogValidationException.class,
                () -> assembler.assemble(PUBLICATION, List.of()));
        assertTrue(empty.getMessage().contains("未初始化"), "空表必须报「未初始化」而非「字段目录不能为空」");
        assertTrue(empty.getMessage().contains("t_policy_field_catalog"), "错误信息须点明是哪张表");
        assertTrue(empty.getMessage().contains(VERSION), "错误信息须点明是哪个版本无行");

        assertThrows(PolicyFieldCatalogValidationException.class, () -> assembler.assemble(PUBLICATION, null));
    }

    /**
     * 行与版本头必须同源：取数漏了版本条件（把多版本行混成一份）时，此处当场拒绝——
     * 否则错误会以「哈希不符」的形式出现在下游，把「取数口径错」伪装成「内容被改」。
     */
    @Test
    void shouldRejectRowsBelongingToAnotherVersion() {
        List<PolicyFieldCatalogView> rows = new ArrayList<>(readSeedRows());
        rows.get(0).setCatalogVersion("2026.09.14.1");

        PolicyFieldCatalogValidationException exception = assertThrows(
                PolicyFieldCatalogValidationException.class,
                () -> new PolicyFieldCatalogAssembler().assemble(PUBLICATION, rows));

        assertTrue(exception.getMessage().contains("不同源"), "须显式指出行与版本头不同源");
        assertTrue(exception.getMessage().contains("2026.09.14.1"), "须列出实际出现的版本号");
    }

    @Test
    void shouldRebuildCatalogFromSeedSqlMatchingIndependentHashAnchor() {
        List<PolicyFieldCatalogView> rows = readSeedRows();
        assertEquals(19, rows.size(), "种子行数须等于 19 个字段（G07/AC-04 下界）");

        PolicyFieldCatalog catalog = new PolicyFieldCatalogAssembler().assemble(PUBLICATION, rows);

        assertEquals(19, catalog.fields().size());
        assertEquals(VERSION, catalog.catalogVersion());
        assertEquals(CATALOG_HASH, catalog.contentHash(),
                "内容哈希必须等于独立 Python 实现算出的锚（不是被测代码自报）");
    }

    /**
     * 分析位分布锚（G07/g07-03a/AC-03）——哈希不匹配时无法定位错在哪一行，本用例补上可诊断信号，
     * 同时是「种子 SQL 的分析位口径 = 独立 Python 口径」的第二条独立判据。
     */
    @Test
    void shouldDeriveAnalysisBitDistributionMatchingTheIndependentAnchor() {
        PolicyFieldCatalog catalog = new PolicyFieldCatalogAssembler().assemble(PUBLICATION, readSeedRows());

        List<PolicyFieldDescriptor> fields = catalog.fields();
        assertEquals(19, fields.size());
        assertEquals(19, fields.stream().filter(field -> field.analysis().analyzable()).count(), "analyzable");
        assertEquals(9, fields.stream().filter(field -> field.analysis().aggregatable()).count(), "aggregatable");
        assertEquals(11, fields.stream().filter(field -> field.analysis().groupable()).count(), "groupable");
        assertEquals(19, fields.stream().filter(field -> field.analysis().filterable()).count(), "filterable");

        // 逐点位样板：TEXT 标量不可聚合、集合字段必可分组、DECIMAL 标量可聚合但不分组。
        PolicyFieldDescriptor address = catalog.requireField("policy.holder.address");
        assertEquals(FieldAnalysisCapability.of(true, false, false, true), address.analysis(),
                "TEXT 标量：可分析可筛选，不聚合不分组");
        PolicyFieldDescriptor insuredName = catalog.requireField("policy.insured.name");
        assertEquals(FieldAnalysisCapability.of(true, false, true, true), insuredName.analysis(),
                "TEXT 集合：必须可分组（否则聚合结果无法按业务主体归并）");
        PolicyFieldDescriptor loan = catalog.requireField("policy.loan.outstandingAmount");
        assertEquals(FieldAnalysisCapability.of(true, true, false, true), loan.analysis(),
                "DECIMAL 标量：可聚合（金额求和）但不作分组维度");
    }

    /**
     * 装配器必须**逐列直读**，不得按 {@code valueType}/{@code collection} 就地推导
     * （G07「目录可运营」的底线：表是这四位的唯一权威源）。
     *
     * <p>🔴 <b>为何其余读 SQL 的用例覆盖不到这一点</b>：种子 SQL 里 19 行的分析位
     * <b>本来就由推导规则生成</b>（那份 CASE 表达式即规则本身）——在真实数据上
     * 「直读」与「推导」恒等，故它们对「装配器悄悄改成推导」<b>完全免疫</b>。
     * 本用例手工构造一行**不可推导**的合法组合（非集合 TEXT 却 {@code groupable=true}，
     * 推导规则会给出 false）把两种实现区分开：装配器一旦改为推导，本用例当场红。</p>
     *
     * <p>该组合合法：非集合 ⇒ 规则 3 不适用；{@code groupable=true} 且 {@code filterable=true}
     * ⇒ 规则 4 满足；TEXT 且 {@code aggregatable=false} ⇒ 规则 2 满足；
     * {@code analyzable=true} ⇒ 规则 1 不适用。</p>
     *
     * <p>该行的冻结哈希由领域 record 自算（合成夹具，非判据），本用例只断言四个分析位。</p>
     */
    @Test
    void shouldReadAnalysisBitsFromColumnsRatherThanDerivingThem() {
        PolicyFieldCatalogView row = new PolicyFieldCatalogView();
        row.setCatalogVersion(VERSION);
        row.setFieldCode("policy.synthetic.groupableText");
        row.setObjectType(PolicyFieldObjectType.POLICY);
        row.setValueType(PolicyFieldValueType.TEXT);
        row.setLabelKey("policy.field.synthetic.groupableText");
        row.setCollection(false);
        row.setReadable(true);
        row.setSensitivity(PolicyFieldSensitivityLevel.INTERNAL);
        row.setMaskingPolicy(PolicyFieldMaskingPolicy.NONE);
        row.setAnalyzable(true);
        row.setAggregatable(false);
        row.setGroupable(true);
        row.setFilterable(true);

        List<PolicyFieldCatalogView> rows = List.of(row);
        PolicyFieldCatalog catalog = new PolicyFieldCatalogAssembler()
                .assemble(publication(VERSION, hashOf(VERSION, rows)), rows);

        FieldAnalysisCapability analysis = catalog.requireField("policy.synthetic.groupableText").analysis();
        assertEquals(FieldAnalysisCapability.of(true, false, true, true), analysis,
                "表里写什么就必须是什么：装配器不得按 valueType/collection 就地推导");
    }

    @Test
    void shouldMapAllCapabilityColumnsLosslessly() {
        PolicyFieldCatalog catalog = new PolicyFieldCatalogAssembler().assemble(PUBLICATION, readSeedRows());

        PolicyFieldDescriptor mobile = catalog.requireField("policy.holder.mobile");
        assertTrue(mobile.capability().proposable() && mobile.capability().executionSupported());
        assertTrue(mobile.capability().clearable() && !mobile.capability().requiresObjectId());
        assertEquals("POLICY_INFO_CHANGE", mobile.capability().changeTypeCode());

        PolicyFieldDescriptor email = catalog.requireField("policy.holder.email");
        assertTrue(email.capability().proposable() && !email.capability().executionSupported(),
                "email 仅可提案、尚无执行器（迁移前即为如此，不得被种子放大）");

        PolicyFieldDescriptor status = catalog.requireField("policy.status");
        assertTrue(status.capability().readable() && !status.capability().proposable());
        assertNull(status.capability().changeTypeCode());

        PolicyFieldDescriptor beneficiaryName = catalog.requireField("policy.beneficiary.name");
        assertTrue(beneficiaryName.collection() && beneficiaryName.capability().requiresObjectId());
        assertEquals("beneficiaryId", beneficiaryName.objectIdentityField());

        assertEquals(PolicyFieldSensitivityLevel.RESTRICTED,
                catalog.requireField("policy.holder.documentNumber").sensitivity());
        assertEquals(PolicyFieldMaskingPolicy.ID_NUMBER,
                catalog.requireField("policy.holder.documentNumber").maskingPolicy());
        assertEquals(PolicyFieldMaskingPolicy.PARTIAL_TEXT,
                catalog.requireField("policy.holder.gender").maskingPolicy());
        assertEquals(PolicyFieldObjectType.COVERAGE, catalog.requireField("policy.coverage.sumInsured").objectType());
        assertEquals(PolicyFieldValueType.DECIMAL, catalog.requireField("policy.beneficiary.share").valueType());
    }

    /**
     * 🔴 读取侧的版本一致性自证（{@code G07/AC-05} ② 的运行时关口）：把行内容改了却仍用**原版本的
     * 冻结哈希**读取 ⇒ 当场拒绝，而不是把一份「版本号说是 v1、内容其实是别的东西」的目录发给保全域。
     *
     * <p>本用例同时是 AC-04 反向判据（「读的是表不是常量」）的更强形态：装配器若拿常量糊弄，
     * 删掉一行后哈希仍会等于冻结值、本断言就不会红——它能红，恰恰证明内容确实来自表行。</p>
     */
    @Test
    void shouldRejectContentChangeWithoutVersionBump() {
        List<PolicyFieldCatalogView> rows = new ArrayList<>(readSeedRows());
        rows.removeIf(row -> row.getFieldCode().equals("policy.status"));

        PolicyFieldCatalogValidationException exception = assertThrows(
                PolicyFieldCatalogValidationException.class,
                () -> new PolicyFieldCatalogAssembler().assemble(PUBLICATION, rows));

        assertTrue(exception.getMessage().contains("哈希"), "须报内容哈希不一致（AC-05 ② 的读取侧表现）");
    }

    /**
     * 换版本发布后的正常路径：行内容变了、版本头随之换成**新版本**且冻结哈希与之一致 ⇒
     * 装配出 18 个字段，且与旧版本哈希不同。
     *
     * <p>冻结哈希由领域 record 对同一批行自算（合成夹具，非哈希判据）；本用例只断言
     * 「字段数由行集决定」「不同内容的哈希不同」两条行为。</p>
     */
    @Test
    void shouldRebuildReducedCatalogWhenNewVersionIsPublished() {
        List<PolicyFieldCatalogView> rows = new ArrayList<>(readSeedRows());
        rows.removeIf(row -> row.getFieldCode().equals("policy.status"));
        assertEquals(18, rows.size());

        // 新版本的行在行表里以新版本号存放（catalog_version 是复合主键的一半），
        // 故先改版本号再算哈希——哈希的规范形以版本号为前缀。
        String nextVersion = "2026.09.15.1";
        rows.forEach(row -> row.setCatalogVersion(nextVersion));
        PolicyFieldCatalog before = new PolicyFieldCatalogAssembler().assemble(PUBLICATION, readSeedRows());
        PolicyFieldCatalog after = new PolicyFieldCatalogAssembler()
                .assemble(publication(nextVersion, hashOf(nextVersion, rows)), rows);

        assertEquals(19, before.fields().size());
        assertEquals(18, after.fields().size(), "AC-04 反向：删一行 ⇒ 字段数减一（证明读的是表不是常量）");
        assertNotEquals(before.contentHash(), after.contentHash());
        assertEquals(nextVersion, after.catalogVersion(), "新版本号必须来自版本头，不是常量");
        assertThrows(PolicyFieldCatalogValidationException.class, () -> after.requireField("policy.status"));
    }

    /**
     * 🔴 版本头种子 SQL 与 Java 侧锚**必须同值**：种子里的 {@code content_hash} 是落库的冻结值
     * （真机读取时的校验基准），本类的 {@link #CATALOG_HASH} 是独立 Python 锚。二者漂移时
     * 单测仍会绿（Java 侧只比锚），直到真机验证才暴露——本用例把它提前到构建期。
     */
    @Test
    void shouldKeepVersionHeaderSeedAlignedWithIndependentAnchor() {
        String sql = readFile(VERSION_SEED_RELATIVE);
        Matcher values = Pattern.compile("VALUES\\s*\\(([^;]*)\\)", Pattern.DOTALL).matcher(sql);
        if (!values.find()) {
            fail("版本头种子 SQL 未找到 VALUES 元组：" + VERSION_SEED_RELATIVE);
        }
        List<String> literals = splitTopLevel(values.group(1));
        assertTrue(literals.size() >= 2, "版本头种子 VALUES 元组至少须含版本号与哈希两列");
        assertEquals("'" + VERSION + "'", literals.get(0).trim(),
                "版本头种子的 catalog_version 与 Java 侧 VERSION 不一致");
        assertEquals("'" + CATALOG_HASH + "'", literals.get(1).trim(),
                "版本头种子的 content_hash 与独立 Python 锚不一致");
    }

    // ------------------------------------------------------------ 夹具构造

    /**
     * 用装配器的**计算路径**对给定行自算哈希（合成夹具专用；真实哈希判据见类注释）。
     *
     * <p>版本号必须与随后构造的版本头**逐字一致**——内容哈希的规范形以版本号为前缀，
     * 版本号不同则同一批行算出的哈希也不同。</p>
     */
    private static String hashOf(String catalogVersion, List<PolicyFieldCatalogView> rows) {
        return new PolicyFieldCatalogAssembler().computeContent(catalogVersion, rows).contentHash();
    }

    // ------------------------------------------------------------ 种子 SQL 读取与解析

    private static List<PolicyFieldCatalogView> readSeedRows() {
        return parseInserts(readFile(SEED_RELATIVE));
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
            fail("找不到 SQL 文件：" + Paths.get(relativePath).toAbsolutePath().normalize()
                    + "（工作目录 " + Paths.get("").toAbsolutePath() + "）——"
                    + "本测试刻意读真实文件而非内联夹具，文件被移动即显性失败");
        }
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException("读取 SQL 失败: " + path, exception);
        }
    }

    /** 解析所有 {@code INSERT INTO t_policy_field_catalog} 语句为目录行。 */
    private static List<PolicyFieldCatalogView> parseInserts(String sql) {
        List<PolicyFieldCatalogView> rows = new ArrayList<>();
        int from = 0;
        while (true) {
            int start = sql.indexOf("INSERT INTO t_policy_field_catalog", from);
            if (start < 0) {
                break;
            }
            int end = sql.indexOf(';', start);
            if (end < 0) {
                throw new IllegalStateException("INSERT 语句缺少结束分号");
            }
            rows.add(toView(parseStatement(sql.substring(start, end))));
            from = end + 1;
        }
        assertFalse(rows.isEmpty(), "种子 SQL 中未解析出任何 INSERT 行");
        return rows;
    }

    /** 一条 INSERT → 列名到字面量的映射。 */
    private static Map<String, String> parseStatement(String statement) {
        int valuesAt = statement.indexOf("VALUES");
        if (valuesAt < 0) {
            throw new IllegalStateException("INSERT 语句缺少 VALUES: " + statement);
        }
        List<String> columns = splitTopLevel(extractTuple(statement, statement.indexOf('(')));
        List<String> values = splitTopLevel(extractTuple(statement, statement.indexOf('(', valuesAt)));
        if (columns.size() != values.size()) {
            throw new IllegalStateException("列数 %d 与值数 %d 不等".formatted(columns.size(), values.size()));
        }
        Map<String, String> row = new LinkedHashMap<>();
        for (int i = 0; i < columns.size(); i++) {
            row.put(columns.get(i), values.get(i));
        }
        return row;
    }

    /** 取 {@code openAt} 处左括号的配对内容（跳过字符串字面量内的括号）。 */
    private static String extractTuple(String text, int openAt) {
        if (openAt < 0 || text.charAt(openAt) != '(') {
            throw new IllegalStateException("未找到左括号: " + text);
        }
        int depth = 0;
        boolean inString = false;
        for (int i = openAt; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (inString) {
                if (ch == '\'') {
                    inString = false;
                }
            } else if (ch == '\'') {
                inString = true;
            } else if (ch == '(') {
                depth++;
            } else if (ch == ')' && --depth == 0) {
                return text.substring(openAt + 1, i);
            }
        }
        throw new IllegalStateException("括号未配对: " + text);
    }

    /** 按顶层逗号切分（跳过字符串字面量内的逗号）。 */
    private static List<String> splitTopLevel(String text) {
        List<String> parts = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        boolean inString = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (inString) {
                buffer.append(ch);
                if (ch == '\'') {
                    inString = false;
                }
            } else if (ch == '\'') {
                inString = true;
                buffer.append(ch);
            } else if (ch == ',') {
                parts.add(buffer.toString().trim());
                buffer.setLength(0);
            } else {
                buffer.append(ch);
            }
        }
        parts.add(buffer.toString().trim());
        return parts;
    }

    // ------------------------------------------------------------ 行 → View

    private static PolicyFieldCatalogView toView(Map<String, String> row) {
        PolicyFieldCatalogView view = new PolicyFieldCatalogView();
        // 行表 INSERT 不含 catalog_version（该列由 policy_field_catalog_row_version_* changeset
        // 从版本头表回填），此处按同一回填口径赋值——单版本读时它等于当前唯一版本。
        view.setCatalogVersion(VERSION);
        view.setFieldCode(text(row, "field_code"));
        view.setObjectType(PolicyFieldObjectType.valueOf(text(row, "object_type")));
        PolicyFieldValueType valueType = PolicyFieldValueType.valueOf(text(row, "value_type"));
        view.setValueType(valueType);
        view.setLabelKey(text(row, "label_key"));
        boolean collection = flag(row, "collection");
        view.setCollection(collection);
        view.setObjectIdentityField(text(row, "object_identity_field"));
        view.setReadable(flag(row, "readable"));
        view.setProposable(flag(row, "proposable"));
        view.setClearable(flag(row, "clearable"));
        view.setExecutionSupported(flag(row, "execution_supported"));
        view.setRequiresObjectId(flag(row, "requires_object_id"));
        view.setChangeTypeCode(text(row, "change_type_code"));
        view.setSensitivity(PolicyFieldSensitivityLevel.valueOf(text(row, "sensitivity")));
        view.setMaskingPolicy(PolicyFieldMaskingPolicy.valueOf(text(row, "masking_policy")));

        // 分析位不在 INSERT 种子里（它是后续 changeset 的 ALTER + UPDATE 补上的），
        // 故此处按同一口径推导后赋入 —— 推导出的值一旦与独立 Python 锚不符，
        // CATALOG_HASH 断言当场破裂。
        FieldAnalysisCapability analysis = analysisOf(valueType, collection);
        view.setAnalyzable(analysis.analyzable());
        view.setAggregatable(analysis.aggregatable());
        view.setGroupable(analysis.groupable());
        view.setFilterable(analysis.filterable());
        return view;
    }

    /**
     * 「可分析」四位推导——与 Liquibase 的
     * {@code policy_field_catalog_analysis_seed_*.dml.sql} 的 CASE 表达式**同规则、异实现**。
     *
     * <p>三份实现互为交叉验证：① SQL 的 CASE（真机执行）；② 本方法；③ 独立的 Python 脚本
     * （{@code analysis-hash-g07-03a.py}，产出 {@link #CATALOG_HASH}）。任一方口径漂移，
     * 要么本类哈希断言破裂，要么真机验证时「查表结果 vs Python 期望」不等。</p>
     *
     * <p>switch 不写 default：值类型枚举新增取值时本方法编译失败，强制复核聚合口径
     * （与 {@code PolicyFieldDescriptor.isNonAggregatableType} 同一手法）。</p>
     */
    private static FieldAnalysisCapability analysisOf(PolicyFieldValueType valueType, boolean collection) {
        boolean aggregatable = switch (valueType) {
            case TEXT, OBJECT, ARRAY -> false;
            case INTEGER, DECIMAL, BOOLEAN, DATE, DATETIME, ENUM -> true;
        };
        boolean groupable = collection
                || valueType == PolicyFieldValueType.ENUM
                || valueType == PolicyFieldValueType.DATE;
        return FieldAnalysisCapability.of(true, aggregatable, groupable, true);
    }

    private static String text(Map<String, String> row, String column) {
        String raw = require(row, column);
        if ("NULL".equals(raw)) {
            return null;
        }
        if (raw.length() >= 2 && raw.charAt(0) == '\'' && raw.charAt(raw.length() - 1) == '\'') {
            return raw.substring(1, raw.length() - 1).replace("''", "'");
        }
        throw new IllegalStateException("列 " + column + " 期望字符串或 NULL，实得: " + raw);
    }

    private static boolean flag(Map<String, String> row, String column) {
        String raw = require(row, column);
        return switch (raw) {
            case "0", "false" -> false;
            case "1", "true" -> true;
            default -> throw new IllegalStateException("列 " + column + " 期望 0/1，实得: " + raw);
        };
    }

    private static String require(Map<String, String> row, String column) {
        String value = row.get(column);
        if (value == null) {
            throw new IllegalStateException("种子 SQL 缺少列 " + column + "（现有列: " + row.keySet() + "）");
        }
        return value;
    }
}
