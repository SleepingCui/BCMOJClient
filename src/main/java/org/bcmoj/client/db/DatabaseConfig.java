package org.bcmoj.client.db;

public class DatabaseConfig {
    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String database;

    public DatabaseConfig(String host, int port, String username, String password, String database) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.database = database;
    }

    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String getJdbcUrl() {
        return String.format("jdbc:mysql://%s:%d/%s", host, port, database);
    }
}
