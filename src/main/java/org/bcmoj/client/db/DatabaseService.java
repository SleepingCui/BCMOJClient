package org.bcmoj.client.db;


import org.bcmoj.client.ProblemData;

import java.sql.*;
import java.util.*;
import org.bcmoj.client.CodingClient;

public class DatabaseService {

    public ProblemData getProblemFromDatabase(int problemId, DatabaseConfig config) throws SQLException {
        try (Connection conn = DriverManager.getConnection(config.getJdbcUrl(), config.getUsername(), config.getPassword())) {
            Map<String, Object> problem = getProblemInfo(conn, problemId);
            if (problem.isEmpty()) {
                throw new SQLException("ProblemID " + problemId + " not exist");
            }
            List<Map<String, String>> examples = getExamples(conn, problemId);
            if (examples.isEmpty()) {
                throw new SQLException("NO Examples");
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
                    CodingClient.log("Fetched problem: " + problemId);
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
    public void testConnection(DatabaseConfig config) throws SQLException {
        long startTime = System.currentTimeMillis();
        try (Connection conn = DriverManager.getConnection(config.getJdbcUrl(), config.getUsername(), config.getPassword())) {
            DatabaseMetaData metaData = conn.getMetaData();
            long elapsed = System.currentTimeMillis() - startTime;
            CodingClient.log("Successfully connected to database '" + ", elapsed " + elapsed + "ms");
            CodingClient.log("Database Product: " + metaData.getDatabaseProductName());
            CodingClient.log("Database Version: " + metaData.getDatabaseProductVersion());
            CodingClient.log("Driver Name: " + metaData.getDriverName());
            CodingClient.log("Driver Version: " + metaData.getDriverVersion());
        } catch (SQLException e) {
            long elapsed = System.currentTimeMillis() - startTime;
            CodingClient.log("Failed to connect to database '" + ", elapsed " + elapsed + "ms");
            CodingClient.log("Error: " + e.getMessage());
            throw new SQLException(e);
        }
    }
}
