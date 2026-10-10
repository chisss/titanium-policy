package com.titanium.policy.application.saga;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * 结构守卫：本域<b>每个</b> Saga 的<b>每个</b>事件处理器都必须在租户身份作用域内执行
 * <p>
 * <b>成因（2026-09-27 g02-06 真机实测）</b>：Saga 的事件处理器运行在 Axon 的 tracking processor
 * 线程上，与创建 Saga 的 Web 请求线程不是同一个线程；而 {@code RequestContextHolder} 基于
 * {@code ThreadLocal}，上下文不跨线程传播。Saga 各步骤的出站调用（核保网关、段级保额档次取数、
 * 产品试算、计费开单）依赖线程上下文提供租户——出站拦截器是「上下文有才注入」策略，上下文为空时
 * <b>静默</b>不加 {@code X-Tenant-Id}，对端契约入口随即 400（{@code TENANT_HEADER_MISSING}）。
 * 真机表现为「出单链路在出站调用处中断 → Saga 进退避重试 → 阻塞整个 tracking segment」。
 * </p>
 * <p>
 * <b>为何用结构断言而非只测行为</b>：行为用例只覆盖被调用到的那个处理器；新增第 N 个
 * {@code @SagaEventHandler} 时若忘了包作用域，行为用例不会失败。此处直接扫源码，断言每个处理器的
 * <b>首条语句</b>即 {@code inTenantScope(...)}。
 * </p>
 * <p>
 * 🔴 <b>三重防假绿</b>：① 逐文件核对处理器数量与 {@link #EXPECTED_HANDLERS} 登记表——注解文本或
 * 目录路径漂移时扫描会匹配到 0 个处理器而「全部合规」恒真，数量核对把这种假绿变成显式红灯；
 * ② 断言各 Saga 源码确实包含 {@code RequestContextHolder.runAsTenant(}——防止 {@code inTenantScope}
 * 被改成不建立作用域的空壳后所有断言照绿；③ 登记表按「文件名 → 处理器数」双向核对，<b>新增 Saga
 * 未登记同样判红</b>（本类自身即因只覆盖 IssuanceSaga 而漏掉 ProposalIssuanceSaga 的第二处命中点，
 * 由真机发现——覆盖面本身也需要机械保证）。
 * </p>
 */
class SagaTenantScopeGuardTest {

    /** Saga 源码目录（surefire 以模块根为工作目录，与 policy-domain 既有结构守卫同法） */
    private static final Path SAGA_DIR = Path.of("src/main/java/com/titanium/policy/application/saga");

    /** 处理器注解：关联键因 Saga 而异（insuranceId / proposalId），故泛化匹配 */
    private static final Pattern HANDLER_ANNOTATION =
            Pattern.compile("@SagaEventHandler\\(associationProperty = \"[A-Za-z]+\"\\)");

    /** 处理器签名，用于从失败信息定位到具体事件（本域处理器一律命名 on） */
    private static final Pattern HANDLER_SIGNATURE = Pattern.compile("public void on\\(([A-Za-z]+) event\\)");

    /**
     * 处理器数量登记表（文件名 → 处理器数）。
     * <p>
     * 🔴 新增 / 删除 Saga 处理器或新增 Saga 类时必须同步改这里。数量不符即判红，须人工复核后
     * 再调整——不得为了让测试变绿而直接改数字。
     * </p>
     */
    private static final Map<String, Integer> EXPECTED_HANDLERS = Map.of(
            "IssuanceSaga.java", 4,
            "ProposalIssuanceSaga.java", 4);

    @Test
    void everySagaEventHandlerMustOpenTenantScope() throws IOException {
        Map<String, Integer> actualHandlers = new LinkedHashMap<>();
        List<String> inspected = new ArrayList<>();
        List<String> offenders = new ArrayList<>();

        for (Path file : sagaSourceFiles()) {
            String fileName = file.getFileName().toString();
            String source = Files.readString(file, StandardCharsets.UTF_8);
            String[] segments = HANDLER_ANNOTATION.split(source, -1);
            int handlers = segments.length - 1;
            if (handlers == 0) {
                continue;
            }
            actualHandlers.put(fileName, handlers);

            // 守护语句必须真的建立作用域，否则断言退化为空转（结构合规但行为不对）
            assertTrue(source.contains("RequestContextHolder.runAsTenant("),
                    fileName + " 的 inTenantScope 未调用 RequestContextHolder.runAsTenant(...)，"
                            + "作用域并未真正建立——本守卫会退化为恒真空转");

            for (int i = 1; i < segments.length; i++) {
                String segment = segments[i];
                int brace = segment.indexOf('{');
                assertTrue(brace > 0, fileName + " 第 " + i + " 个 @SagaEventHandler 段内未找到方法体起始 '{'");
                String eventType = eventType(segment.substring(0, brace));
                inspected.add(fileName + "#on(" + eventType + ")");
                String firstStatement = firstCodeLine(segment.substring(brace + 1));
                if (!firstStatement.startsWith("inTenantScope(")) {
                    offenders.add("  · " + fileName + "#on(" + eventType + ") → 首条语句「" + firstStatement + "」");
                }
            }
        }

        assertEquals(EXPECTED_HANDLERS, actualHandlers,
                "Saga 处理器登记表与实际扫描结果不符。差异意味着注解文本/源码路径漂移，或新增 Saga"
                        + "（/处理器）未登记——此时「全部合规」是假绿，须先复核 saga 包下各类的"
                        + "@SagaEventHandler 注解，再调整登记表。");

        int expectedTotal = EXPECTED_HANDLERS.values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(expectedTotal, inspected.size(), "已检查的处理器数量与登记表不符：" + inspected);

        assertTrue(offenders.isEmpty(),
                "以下 Saga 事件处理器的首条语句不是 inTenantScope(...)：\n" + String.join("\n", offenders)
                        + "\n⇒ 该步骤（及其同步触发的下游步骤）的 Feign 出站调用将在无租户上下文的 tracking "
                        + "线程上发起，对端契约入口会以 400 TENANT_HEADER_MISSING 静默拒绝。\n"
                        + "已检查：" + inspected);
    }

    /** 列出 saga 包下全部 Java 源文件（新增 Saga 自动纳入扫描）。 */
    private static List<Path> sagaSourceFiles() throws IOException {
        try (Stream<Path> files = Files.list(SAGA_DIR)) {
            return files.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }
    }

    /** 从注解与方法体之间的片段里取出处理器的事件参数类型（如 {@code ProposalConvertedEvent}）。 */
    private static String eventType(String header) {
        Matcher matcher = HANDLER_SIGNATURE.matcher(header);
        return matcher.find() ? matcher.group(1) : "<无法识别的方法签名>";
    }

    /**
     * 取方法体首条<b>代码</b>语句（跳过空行与注释行）。
     * <p>
     * 🔴 必须跳注释：若只取「首个非空行」，被注释掉的首行会被当成合规语句，守卫在
     * 「守护语句被注释掉」这一最危险的形态下恒绿。
     * </p>
     */
    private static String firstCodeLine(String body) {
        for (String raw : body.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("//") || line.startsWith("/*") || line.startsWith("*")) {
                continue;
            }
            return line;
        }
        return "<方法体为空>";
    }
}
