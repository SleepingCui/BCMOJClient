package org.bcmoj.client;


import java.sql.*;
import java.util.*;

public class DatabaseService {

    public ProblemData getProblemFromDatabase(int problemId, DatabaseConfig config) throws SQLException {
        try (Connection conn = DriverManager.getConnection(
                config.getJdbcUrl(), config.getUsername(), config.getPassword())) {

            // 获取题目信息
            Map<String, Object> problem = getProblemInfo(conn, problemId);
            if (problem.isEmpty()) {
                throw new SQLException("题目ID " + problemId + " 不存在");
            }

            // 获取样例数据
            List<Map<String, String>> examples = getExamples(conn, problemId);
            if (examples.isEmpty()) {
                throw new SQLException("该题无测试用例");
            }

            return new ProblemData(problem, examples);
        }
    }

    private Map<String, Object> getProblemInfo(Connection conn, int problemId) throws SQLException {
        String sql = "SELECT * FROM problems WHERE problem_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, problemId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> problem = new HashMap<>();
                    ResultSetMetaData metaData = rs.getMetaData();
                    for (int i = 1; i <= metaData.getColumnCount(); i++) {
                        problem.put(metaData.getColumnName(i), rs.getObject(i));
                    }
                    return problem;
                }
            }
        }
        return new HashMap<>();
    }

    private List<Map<String, String>> getExamples(Connection conn, int problemId) throws SQLException {
        String sql = "SELECT input, output FROM examples WHERE problem_id = ? ORDER BY example_id";
        List<Map<String, String>> examples = new ArrayList<>();

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, problemId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, String> example = new HashMap<>();
                    example.put("input", rs.getString("input"));
                    example.put("output", rs.getString("output"));
                    examples.add(example);
                }
            }
        }
        return examples;
    }
}
