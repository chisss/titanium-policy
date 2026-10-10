package com.titanium.policy.fieldcatalog;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Policy 对外发布的不可变字段目录。
 *
 * <p>🔴 <b>本 record 不含任何字段清单字面量</b>——清单的权威存储是目录表
 * {@code t_policy_field_catalog}（{@code G07/AC-04}），读取侧由
 * {@code PolicyFieldCatalogAssembler} 从表行重建。
 * 表驱动化之前此处曾硬编码整份 19 项字段清单，那使
 * 「运营加字段」必须改代码发版，与目录可运营（G07 O-4）相悖。</p>
 */
public record PolicyFieldCatalog(String catalogVersion, String contentHash, List<PolicyFieldDescriptor> fields) {

    public PolicyFieldCatalog {
        if (catalogVersion == null || catalogVersion.isBlank()) {
            throw new PolicyFieldCatalogValidationException("字段目录版本不能为空");
        }
        if (fields == null || fields.isEmpty()) {
            throw new PolicyFieldCatalogValidationException("字段目录不能为空");
        }
        catalogVersion = catalogVersion.trim();
        if (fields.stream().anyMatch(Objects::isNull)) {
            throw new PolicyFieldCatalogValidationException("字段目录不能包含空字段");
        }
        fields = fields.stream().sorted(Comparator.comparing(PolicyFieldDescriptor::fieldCode)).toList();
        validateUniqueFieldCodes(fields);
        String calculatedHash = calculateHash(catalogVersion, fields);
        if (contentHash != null && !contentHash.isBlank() && !calculatedHash.equals(contentHash)) {
            throw new PolicyFieldCatalogValidationException("字段目录内容哈希不一致");
        }
        contentHash = calculatedHash;
    }

    /** 按稳定字段码查找字段。 */
    public PolicyFieldDescriptor requireField(String fieldCode) {
        return fields.stream()
                .filter(field -> field.fieldCode().equals(fieldCode))
                .findFirst()
                .orElseThrow(() -> new PolicyFieldCatalogValidationException("字段目录不存在字段: " + fieldCode));
    }

    private static void validateUniqueFieldCodes(List<PolicyFieldDescriptor> fields) {
        Set<String> fieldCodes = new HashSet<>();
        for (PolicyFieldDescriptor field : fields) {
            if (field == null) {
                throw new PolicyFieldCatalogValidationException("字段目录不能包含空字段");
            }
            if (!fieldCodes.add(field.fieldCode())) {
                throw new PolicyFieldCatalogValidationException("字段目录编码重复: " + field.fieldCode());
            }
        }
    }

    private static String calculateHash(String version, List<PolicyFieldDescriptor> fields) {
        String canonical = version + "\n"
                + fields.stream().map(PolicyFieldDescriptor::canonicalForm).collect(Collectors.joining("\n"));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持 SHA-256", exception);
        }
    }
}
