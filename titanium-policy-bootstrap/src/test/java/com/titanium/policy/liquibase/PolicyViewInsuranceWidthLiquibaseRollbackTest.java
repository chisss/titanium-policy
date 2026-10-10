package com.titanium.policy.liquibase;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;

/**
 * 保单域险种三级分类列宽收敛变更集测试（G10/g10-02）。
 * <p>
 * 该变更集把 4 张表的 {@code insurance_type} 由 VARCHAR(64) 收窄至 VARCHAR(32)
 * （t_proposal_view / t_insurance_view / t_policy_view / t_policy_product）。
 * 4 条正向语句与 4 条 rollback 语句均为「单行多语句分号分隔」——拆分执行若失败
 * 只会在变更运行时暴露，故在 H2 上验证「4 表全部收窄、存量值不丢、回滚 4 表全部还原」。
 * </p>
 */
class PolicyViewInsuranceWidthLiquibaseRollbackTest {

    private static final String CHANGELOG =
            "liquibase/ddl/policy_view_insurance_width_202610091849_weisun_ddl.sql";

    private static final List<String> TABLES =
            List.of("t_proposal_view", "t_insurance_view", "t_policy_view", "t_policy_product");

    @Test
    void shouldNarrowAllFourTablesAndRollback() throws Exception {
        String databaseName = "policy_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:h2:mem:" + databaseName + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            try (Statement statement = connection.createStatement()) {
                for (String table : TABLES) {
                    // 变更前形态替身：4 张表列宽均为 64，各插一行验证收窄与回滚均不丢值
                    statement.execute("CREATE TABLE " + table + " ("
                            + "id VARCHAR(64) PRIMARY KEY,"
                            + "insurance_type VARCHAR(64) NULL)");
                    statement.execute("INSERT INTO " + table
                            + " VALUES ('ROW-001', 'TERM_LIFE')");
                }
            }
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            Liquibase liquibase = new Liquibase(
                    CHANGELOG, new ClassLoaderResourceAccessor(), database);

            liquibase.update(new Contexts(), new LabelExpression());
            for (String table : TABLES) {
                assertEquals(32, columnWidth(connection, table), table + " 应收窄至 32");
                assertEquals("TERM_LIFE", valueOf(connection, table), table + " 收窄不得丢值");
            }

            liquibase.rollback(1, new Contexts(), new LabelExpression());
            for (String table : TABLES) {
                assertEquals(64, columnWidth(connection, table), table + " 回滚应还原 64");
                assertEquals("TERM_LIFE", valueOf(connection, table), table + " 回滚不得丢值");
            }
        }
    }

    private int columnWidth(Connection connection, String tableName) throws Exception {
        String sql = "SELECT CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS"
                + " WHERE LOWER(TABLE_NAME) = LOWER(?) AND LOWER(COLUMN_NAME) = 'insurance_type'";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    private String valueOf(Connection connection, String tableName) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT insurance_type FROM " + tableName + " WHERE id = 'ROW-001'")) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }
}
