package com.countrydelight.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "environment")
public class EnvironmentConfig {
    
    private String env = "QA";
    private QaConfig qa = new QaConfig();
    private UatConfig uat = new UatConfig();
    private DatabaseConfig database = new DatabaseConfig();

    public static class QaConfig {
        public String cmsUrl = "https://qa-cms.countrydelight.in";
        public String adminUrl = "https://qa-crm.countrydelight.in/admin/";
        public String apiKey = "uZ8Q7mKp2xVdL4tN9sFjR1cW6yH3bG0aT5qX8eM2nP7rL9kD4vB1zC6hJ0wY3fU_cd_membership";
        public String authToken = "eyJhbGciOiJIUzI1NiJ9.eyJqdGkiOiIyNDA0IiwiaXNzIjoiQ291bnRyeURlbGlnaHQiLCJzdWIiOiJVc2VyIEF1dGhlbnRpY2F0aW9uIiwiaWF0IjoxNzc1NTU4NjgyLCJleHAiOjE4MDcwOTQ2ODJ9.5DdqflV9u6vbo7oYC-CKpNxptS76LGrlCMieixsb1c0";

        public String getCmsUrl() { return cmsUrl; }
        public void setCmsUrl(String cmsUrl) { this.cmsUrl = cmsUrl; }
        public String getAdminUrl() { return adminUrl; }
        public void setAdminUrl(String adminUrl) { this.adminUrl = adminUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getAuthToken() { return authToken; }
        public void setAuthToken(String authToken) { this.authToken = authToken; }
    }

    public static class UatConfig {
        public String cmsUrl = "https://uat-cms.countrydelight.in";
        public String adminUrl = "https://uat-crm.countrydelight.in/admin/";
        public String apiKey = "uZ8Q7mKp2xVdL4tN9sFjR1cW6yH3bG0aT5qX8eM2nP7rL9kD4vB1zC6hJ0wY3fU_cd_membership";
        public String authToken = "eyJhbGciOiJIUzI1NiJ9.eyJqdGkiOiIyNDA0IiwiaXNzIjoiQ291bnRyeURlbGlnaHQiLCJzdWIiOiJVc2VyIEF1dGhlbnRpY2F0aW9uIiwiaWF0IjoxNzc1NTU4NjgyLCJleHAiOjE4MDcwOTQ2ODJ9.5DdqflV9u6vbo7oYC-CKpNxptS76LGrlCMieixsb1c0";

        public String getCmsUrl() { return cmsUrl; }
        public void setCmsUrl(String cmsUrl) { this.cmsUrl = cmsUrl; }
        public String getAdminUrl() { return adminUrl; }
        public void setAdminUrl(String adminUrl) { this.adminUrl = adminUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getAuthToken() { return authToken; }
        public void setAuthToken(String authToken) { this.authToken = authToken; }
    }

    public static class DatabaseConfig {
        public String host = "${DB_HOST}";
        public String port = "${DB_PORT}";
        public String username = "${DB_USER}";
        public String password = "${DB_PASSWORD}";
        public String database = "${DB_NAME}";

        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public String getPort() { return port; }
        public void setPort(String port) { this.port = port; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getDatabase() { return database; }
        public void setDatabase(String database) { this.database = database; }
    }

    public String getEnv() { return env; }
    public void setEnv(String env) { this.env = env; }
    public QaConfig getQa() { return qa; }
    public void setQa(QaConfig qa) { this.qa = qa; }
    public UatConfig getUat() { return uat; }
    public void setUat(UatConfig uat) { this.uat = uat; }
    public DatabaseConfig getDatabase() { return database; }
    public void setDatabase(DatabaseConfig database) { this.database = database; }

    public String getApiBaseUrl() {
        return env.equalsIgnoreCase("UAT") ? uat.getCmsUrl() : qa.getCmsUrl();
    }

    public String getAdminUrl() {
        return env.equalsIgnoreCase("UAT") ? uat.getAdminUrl() : qa.getAdminUrl();
    }

    public String getApiKey() {
        return env.equalsIgnoreCase("UAT") ? uat.getApiKey() : qa.getApiKey();
    }

    public String getAuthToken() {
        return env.equalsIgnoreCase("UAT") ? uat.getAuthToken() : qa.getAuthToken();
    }
}
