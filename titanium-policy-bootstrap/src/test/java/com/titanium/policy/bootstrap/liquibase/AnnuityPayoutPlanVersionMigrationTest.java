package com.titanium.policy.bootstrap.liquibase;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/**
 * 年金给付计划乐观锁列迁移契约测试。
 */
class AnnuityPayoutPlanVersionMigrationTest {

    private static final String MIGRATION = "liquibase/ddl/annuity_payout_plan_version_202608140110_weisun_ddl.sql";

    @Test
    void masterChangelogIncludesVersionMigration() throws IOException {
        String master = resourceText("liquibase/changelog-master.xml");

        assertTrue(master.contains(MIGRATION));
    }

    @Test
    void migrationAddsRequiredOptimisticLockColumn() throws IOException {
        String migration = resourceText(MIGRATION);

        assertTrue(migration.contains("ALTER TABLE t_annuity_payout_plan"));
        assertTrue(migration.contains("ADD COLUMN version BIGINT NOT NULL DEFAULT 0"));
    }

    private String resourceText(String path) throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertTrue(input != null, "缺少资源: " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
