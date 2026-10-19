package com.newsagent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
public class DataSourceConfig {

    @Bean
    public DataSource dataSource(AppProperties properties) throws IOException {
        Path databasePath = Path.of(properties.dbPath()).toAbsolutePath().normalize();
        Path parent = databasePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + databasePath.toString().replace('\\', '/'));
        return dataSource;
    }
}
