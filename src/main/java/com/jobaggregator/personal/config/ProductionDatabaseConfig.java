package com.jobaggregator.personal.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;
import java.net.URI;

@Configuration
@Profile("prod")
@Slf4j
public class ProductionDatabaseConfig {

    @Value("${DATABASE_URL:#{null}}")
    private String databaseUrl;

    @Value("${spring.datasource.username:postgres}")
    private String defaultUsername;

    @Value("${spring.datasource.password:postgres}")
    private String defaultPassword;

    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setDriverClassName("org.postgresql.Driver");

        if (databaseUrl != null && !databaseUrl.isBlank()) {
            log.info("Configuring PostgreSQL DataSource from DATABASE_URL...");
            try {
                // Render often provides DATABASE_URL in the format: postgres://user:password@host:port/database
                // or postgresql://user:password@host:port/database
                if (databaseUrl.startsWith("postgres://") || databaseUrl.startsWith("postgresql://")) {
                    URI uri = new URI(databaseUrl.replace("postgres://", "http://").replace("postgresql://", "http://"));
                    String host = uri.getHost();
                    int port = uri.getPort() == -1 ? 5432 : uri.getPort();
                    String path = uri.getPath();
                    String dbName = (path != null && path.length() > 1) ? path.substring(1) : "jobdb";

                    String jdbcUrl = String.format("jdbc:postgresql://%s:%d/%s", host, port, dbName);
                    config.setJdbcUrl(jdbcUrl);

                    String userInfo = uri.getUserInfo();
                    if (userInfo != null && userInfo.contains(":")) {
                        String[] userPass = userInfo.split(":", 2);
                        config.setUsername(userPass[0]);
                        config.setPassword(userPass[1]);
                    }
                } else if (databaseUrl.startsWith("jdbc:")) {
                    config.setJdbcUrl(databaseUrl);
                    config.setUsername(defaultUsername);
                    config.setPassword(defaultPassword);
                } else {
                    config.setJdbcUrl("jdbc:postgresql://" + databaseUrl);
                    config.setUsername(defaultUsername);
                    config.setPassword(defaultPassword);
                }
            } catch (Exception e) {
                log.warn("Could not parse DATABASE_URL as URI, using as raw JDBC URL: {}", e.getMessage());
                config.setJdbcUrl(databaseUrl.startsWith("jdbc:") ? databaseUrl : "jdbc:" + databaseUrl);
                config.setUsername(defaultUsername);
                config.setPassword(defaultPassword);
            }
        } else {
            log.info("Using default PostgreSQL configuration for prod profile");
            config.setJdbcUrl("jdbc:postgresql://localhost:5432/jobdb");
            config.setUsername(defaultUsername);
            config.setPassword(defaultPassword);
        }

        // Production pool tuning for Render 512MB RAM tier
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setIdleTimeout(30000);
        config.setConnectionTimeout(20000);
        config.setMaxLifetime(1800000);

        return new HikariDataSource(config);
    }
}
