package com.titanium.policy.valueobject;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import com.titanium.metadata.errorcode.BaseErrorCode;

/**
 * 规则裁决结果值对象（可国际化）
 * <p>
 * 领域层各类规则校验（投保要素、保单构成、承保准入…）的统一裁决载体。取代原先各自定义
 * {@code XxxDecision} 且<b>把中文提示拼成句子</b>的做法——按红线 15，领域层只携带
 * 「错误码 + 参数」，文案渲染推迟到边界层按 {@code Accept-Language} 进行。
 * </p>
 * <p>
 * 🔴 <b>为何不在领域层拼句子</b>：系统按多国业务设计，硬编码中文在做其它语言业务时无法翻译。
 * 错误码是稳定契约，参数是纯数据，二者组合可渲染任意语言：
 * </p>
 * <pre>
 * // 领域层
 * RuleDecision.rejectedAtLine(ELIGIBILITY_AGE_EXCEEDS_MAX, lineNo, age, maxAge)
 * // messages_zh_CN.properties: 20006011=被保险人年龄 {0} 超过产品最大投保年龄 {1}
 * // messages_en_US.properties: 20006011=Insured age {0} exceeds product maximum {1}
 * </pre>
 *
 * @param passed    是否通过
 * @param errorCode 违反的规则错误码（通过时为 null）
 * @param args      文案渲染参数（按错误码 message 中 {0}{1} 的顺序）
 * @param lineNo    违反所在的险种段序号（单据级规则违反时为 null）
 */
public record RuleDecision(boolean passed, BaseErrorCode errorCode, List<Object> args, Integer lineNo) {

    /** 通过的单例决策（无错误码、无参数，复用避免重复分配） */
    private static final RuleDecision PASSED = new RuleDecision(true, null, List.of(), null);

    /**
     * 构造通过决策。
     * <p>
     * 工厂名为 {@code accepted} 而非 {@code passed}——record 已隐式生成同名组件访问器。
     * </p>
     *
     * @return 通过决策
     */
    public static RuleDecision accepted() {
        return PASSED;
    }

    /**
     * 构造单据级不通过决策（投保人/受益人/收费方式等跨段规则）。
     *
     * @param errorCode 违反的规则错误码
     * @param args      文案渲染参数
     * @return 不通过决策
     */
    public static RuleDecision rejected(BaseErrorCode errorCode, Object... args) {
        return new RuleDecision(false, errorCode, toArgList(args), null);
    }

    /**
     * 构造段级不通过决策（年龄/保额/期间/缴费等段内规则）。
     * <p>
     * 一单多险时调用方需知道是哪一段不合格（如「附加重疾超龄」而主险合格），故携带段序号。
     * </p>
     *
     * @param errorCode 违反的规则错误码
     * @param lineNo    险种段序号
     * @param args      文案渲染参数
     * @return 不通过决策
     */
    public static RuleDecision rejectedAtLine(BaseErrorCode errorCode, int lineNo, Object... args) {
        return new RuleDecision(false, errorCode, toArgList(args), lineNo);
    }

    /**
     * 业务错误码字符串（供 API 响应的 rejectCode 字段使用）。
     *
     * @return 错误码；通过时返回 null
     */
    public String code() {
        return errorCode != null ? errorCode.getCode() : null;
    }

    /**
     * 以错误码内置的默认文案（中文兜底）渲染消息。
     * <p>
     * 🔴 仅用于日志与无 {@code MessageSource} 的兜底场景。<b>对外响应必须经边界层的
     * {@code MessageSource} 按请求语言渲染</b>，不得直接用本方法的返回值作为 API 输出。
     * </p>
     *
     * @return 渲染后的默认文案；通过时返回 null
     */
    public String defaultMessage() {
        if (errorCode == null) {
            return null;
        }
        String template = errorCode.getDescription() != null ? errorCode.getDescription() : errorCode.getMessage();
        return args.isEmpty() ? template : MessageFormat.format(template, args.toArray());
    }

    /**
     * 是否为段级违反（携带险种段序号）。
     *
     * @return 段级违反返回 {@code true}
     */
    @JsonIgnore
    public boolean isLineLevel() {
        return lineNo != null;
    }

    /**
     * 参数数组 → 不可变列表（null 安全，元素允许为 null 故不用 List.of）。
     */
    private static List<Object> toArgList(Object... args) {
        return args == null || args.length == 0 ? List.of() : Arrays.asList(args);
    }
}
