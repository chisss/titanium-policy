package com.titanium.policy.fieldcatalog;

import java.time.LocalDate;
import java.util.regex.Pattern;

import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldMaskingPolicy;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldObjectType;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldSensitivityLevel;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldValueType;
import com.titanium.metadata.errorcode.PolicyErrorCode;
import com.titanium.metadata.fieldcatalog.FieldAnalysisCapability;
import com.titanium.policy.valueobject.RuleDecision;

/** Policy 对外发布的稳定字段描述。 */
public record PolicyFieldDescriptor(
        String fieldCode,
        PolicyFieldObjectType objectType,
        PolicyFieldValueType valueType,
        String labelKey,
        boolean collection,
        String objectIdentityField,
        PolicyFieldCapability capability,
        PolicyFieldSensitivityLevel sensitivity,
        PolicyFieldMaskingPolicy maskingPolicy,
        LocalDate deprecatedAt,
        FieldAnalysisCapability analysis) {

    private static final Pattern FIELD_CODE_PATTERN =
            Pattern.compile("[a-z][a-zA-Z0-9]*(\\.[a-z][a-zA-Z0-9]*)+");

    public PolicyFieldDescriptor {
        fieldCode = requireText(fieldCode, "字段编码");
        labelKey = requireText(labelKey, "标签键");
        objectIdentityField = normalize(objectIdentityField);
        if (!FIELD_CODE_PATTERN.matcher(fieldCode).matches()) {
            throw new PolicyFieldCatalogValidationException("字段编码格式非法: " + fieldCode);
        }
        if (objectType == null || valueType == null || capability == null
                || sensitivity == null || maskingPolicy == null || analysis == null) {
            throw new PolicyFieldCatalogValidationException("字段类型、能力、分析位和敏感策略不能为空");
        }
        if (collection && objectIdentityField == null) {
            throw new PolicyFieldCatalogValidationException("集合字段必须配置稳定业务对象标识");
        }
        if (!collection && objectIdentityField != null) {
            throw new PolicyFieldCatalogValidationException("非集合字段不能配置业务对象标识");
        }
        if (capability.requiresObjectId() && !collection) {
            throw new PolicyFieldCatalogValidationException("仅集合字段可以要求业务对象标识");
        }
        if (sensitivity.requiresMasking() && maskingPolicy == PolicyFieldMaskingPolicy.NONE) {
            throw new PolicyFieldCatalogValidationException("敏感字段必须配置掩码策略");
        }
        RuleDecision analysisDecision = validateAnalysis(fieldCode, analysis, valueType, collection);
        if (!analysisDecision.passed()) {
            throw new PolicyFieldCatalogValidationException(
                    analysisDecision.errorCode(), analysisDecision.defaultMessage());
        }
    }

    /**
     * 校验「可分析」能力位的四条不变量（G07/g07-03），返回可判定的裁决而非直接抛出。
     *
     * <p>四条规则（rule → 错误码）：</p>
     * <ol>
     *   <li>{@code analyzable=false} ⇒ 其余三位必须全 false —— 总开关关掉却声称能聚合，自相矛盾。</li>
     *   <li>{@code valueType ∈ {TEXT, OBJECT, ARRAY}} ⇒ {@code aggregatable} 不得为 true
     *       —— 文本/对象/数组没有可求和的度量语义。</li>
     *   <li>{@code collection=true} ⇒ {@code groupable=true}
     *       —— 否则聚合结果无法按业务主体归并（归并键就是 {@code objectIdentityField}）。</li>
     *   <li>{@code groupable=true} ⇒ {@code filterable=true} —— 能分组必能过滤。</li>
     * </ol>
     *
     * <p>🔴 <b>为何四条都落在这里、而不像需求文档 §g07-03 第 3 点所说「内聚为
     * {@code FieldAnalysisCapability} 的紧凑构造器不变量」</b>：规则 2 需要 {@code valueType}、
     * 规则 3 需要 {@code collection}，metadata 侧的 {@code FieldAnalysisCapability} 看不到这两个事实；
     * 而本方法的返回类型 {@link RuleDecision} 定义在 policy-domain，metadata 反向依赖它会破坏
     * 「metadata 是纯契约 jar」的定位。规则只能内聚在**唯一能同时看到三个事实的地方**——
     * 即本类。已同批勘误文档措辞。</p>
     *
     * <p>🔴 <b>为何返回 {@link RuleDecision} 而不只抛异常</b>：调用方有两类诉求——构造期要
     * 「非法即失败」（紧凑构造器据本方法结果抛 {@link PolicyFieldCatalogValidationException}），
     * 而目录维护端/发布校验要在**不构造对象**的前提下拿到「哪条规则、哪个字段、什么参数」，
     * 以便一次性列出全部待修项。异常只能给第一处。</p>
     *
     * @param fieldCode  字段编码（进错误码参数 {0}，供运维在一份多字段目录里定位）
     * @param analysis   分析能力位；调用方须先行判空（紧凑构造器已在统一判空处拦截）
     * @param valueType  字段值类型
     * @param collection 是否集合对象
     * @return 通过返回 {@link RuleDecision#accepted()}；违规返回携带对应 {@link PolicyErrorCode} 的拒绝裁决
     */
    public static RuleDecision validateAnalysis(
            String fieldCode,
            FieldAnalysisCapability analysis,
            PolicyFieldValueType valueType,
            boolean collection) {
        if (!analysis.analyzable()
                && (analysis.aggregatable() || analysis.groupable() || analysis.filterable())) {
            return RuleDecision.rejected(PolicyErrorCode.POLICY_FIELD_ANALYSIS_INCONSISTENT, fieldCode);
        }
        if (isNonAggregatableType(valueType) && analysis.aggregatable()) {
            return RuleDecision.rejected(
                    PolicyErrorCode.POLICY_FIELD_ANALYSIS_TYPE_UNSUPPORTED, fieldCode, valueType.getCode());
        }
        if (collection && !analysis.groupable()) {
            return RuleDecision.rejected(
                    PolicyErrorCode.POLICY_FIELD_ANALYSIS_COLLECTION_NOT_GROUPABLE, fieldCode);
        }
        if (analysis.groupable() && !analysis.filterable()) {
            return RuleDecision.rejected(
                    PolicyErrorCode.POLICY_FIELD_ANALYSIS_GROUPABLE_NOT_FILTERABLE, fieldCode);
        }
        return RuleDecision.accepted();
    }

    /**
     * 是否为「不具备聚合语义」的值类型。
     *
     * <p>switch <b>不写 default</b>：{@code PolicyFieldValueType} 新增取值时本方法编译失败，
     * 把「新类型能不能聚合」变成构建期强制项（与 {@code PolicyErrorCode.category()} 同一手法）。
     * 否则新类型会静默落进 {@code default} 分支，得到一份无人复核的聚合口径。</p>
     */
    private static boolean isNonAggregatableType(PolicyFieldValueType valueType) {
        return switch (valueType) {
            case TEXT, OBJECT, ARRAY -> true;
            case INTEGER, DECIMAL, BOOLEAN, DATE, DATETIME, ENUM -> false;
        };
    }

    public static PolicyFieldDescriptor scalar(
            String fieldCode,
            PolicyFieldObjectType objectType,
            PolicyFieldValueType valueType,
            String labelKey,
            PolicyFieldCapability capability,
            PolicyFieldSensitivityLevel sensitivity,
            PolicyFieldMaskingPolicy maskingPolicy,
            FieldAnalysisCapability analysis) {
        return new PolicyFieldDescriptor(fieldCode, objectType, valueType, labelKey, false, null,
                capability, sensitivity, maskingPolicy, null, analysis);
    }

    public static PolicyFieldDescriptor collectionField(
            String fieldCode,
            PolicyFieldObjectType objectType,
            PolicyFieldValueType valueType,
            String labelKey,
            String objectIdentityField,
            PolicyFieldCapability capability,
            PolicyFieldSensitivityLevel sensitivity,
            PolicyFieldMaskingPolicy maskingPolicy,
            FieldAnalysisCapability analysis) {
        return new PolicyFieldDescriptor(fieldCode, objectType, valueType, labelKey, true,
                objectIdentityField, capability, sensitivity, maskingPolicy, null, analysis);
    }

    String canonicalForm() {
        return String.join("\u001f",
                fieldCode,
                objectType.getCode(),
                valueType.getCode(),
                labelKey,
                Boolean.toString(collection),
                nullToEmpty(objectIdentityField),
                Boolean.toString(capability.readable()),
                Boolean.toString(capability.proposable()),
                Boolean.toString(capability.clearable()),
                Boolean.toString(capability.executionSupported()),
                Boolean.toString(capability.requiresObjectId()),
                nullToEmpty(capability.changeTypeCode()),
                sensitivity.getCode(),
                maskingPolicy.getCode(),
                deprecatedAt == null ? "" : deprecatedAt.toString(),
                Boolean.toString(analysis.analyzable()),
                Boolean.toString(analysis.aggregatable()),
                Boolean.toString(analysis.groupable()),
                Boolean.toString(analysis.filterable()));
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new PolicyFieldCatalogValidationException(label + "不能为空");
        }
        return value.trim();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
