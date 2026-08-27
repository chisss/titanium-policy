package com.titanium.policy.infrastructure.generator;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * MySQL 业务编号流水存储。
 * <p>
 * 使用 InnoDB 唯一键与 {@code LAST_INSERT_ID(expr)} 完成单条原子 upsert。返回值来自当前
 * JDBC 连接，因此不会因并发实例在查询流水时插入下一号而错配。
 * </p>
 */
@Repository
@RequiredArgsConstructor
class MySqlPolicyNoSequenceStore implements PolicyNoSequenceStore {

    private static final String UPSERT_SQL = """
            INSERT INTO t_policy_number_sequence
                (tenant_id, document_type, business_date, last_sequence)
            VALUES (?, ?, ?, LAST_INSERT_ID(1))
            ON DUPLICATE KEY UPDATE
                last_sequence = LAST_INSERT_ID(last_sequence + 1),
                update_time = CURRENT_TIMESTAMP
            """;

    private static final String LAST_INSERT_ID_SQL = "SELECT LAST_INSERT_ID()";

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public long next(String tenantId, String documentType, LocalDate businessDate) {
        Long sequence = jdbcTemplate.execute((ConnectionCallback<Long>) connection -> executeAtomically(connection,
                tenantId, documentType, businessDate));
        if (sequence == null || sequence < 1) {
            throw new IllegalStateException("MySQL 业务编号流水预占失败");
        }
        return sequence;
    }

    /**
     * 在同一 JDBC 连接中完成 upsert 与 LAST_INSERT_ID 读取，避免事务管理器未暴露连接时发生会话漂移。
     */
    private long executeAtomically(Connection connection, String tenantId, String documentType,
                                   LocalDate businessDate) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPSERT_SQL)) {
            statement.setString(1, tenantId);
            statement.setString(2, documentType);
            statement.setObject(3, businessDate);
            statement.executeUpdate();
        }
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(LAST_INSERT_ID_SQL)) {
            if (!resultSet.next()) {
                return 0;
            }
            return resultSet.getLong(1);
        }
    }
}
