package com.chatbi.copilot.config;

import com.chatbi.copilot.common.PasswordCipher;
import com.chatbi.copilot.datasource.entity.DataSourceConfig;
import com.chatbi.copilot.datasource.mapper.DataSourceConfigMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Auto-registers a demo datasource on first startup (when enabled) so the app is queryable
 * out of the box. The connection itself is created lazily, so this never blocks startup even
 * if the demo database is not up yet.
 */
@Slf4j
@Component
public class DemoDataSourceInitializer implements ApplicationRunner {

    private final DemoDataSourceProperties demo;
    private final DataSourceConfigMapper mapper;
    private final PasswordCipher cipher;

    public DemoDataSourceInitializer(DemoDataSourceProperties demo,
                                     DataSourceConfigMapper mapper,
                                     PasswordCipher cipher) {
        this.demo = demo;
        this.mapper = mapper;
        this.cipher = cipher;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!demo.isEnabled()) {
            return;
        }
        try {
            Long count = mapper.selectCount(null);
            if (count != null && count > 0) {
                log.info("Datasources already exist ({}), skipping demo seed", count);
                return;
            }
            DataSourceConfig config = new DataSourceConfig();
            config.setName(demo.getName());
            config.setDbType(demo.getDbType());
            config.setHost(demo.getHost());
            config.setPort(demo.getPort());
            config.setDatabaseName(demo.getDatabaseName());
            config.setUsername(demo.getUsername());
            config.setPassword(cipher.encrypt(demo.getPassword()));
            config.setRemark("Auto-registered demo datasource");
            mapper.insert(config);
            log.info("Registered demo datasource '{}' -> {}:{}/{}",
                    demo.getName(), demo.getHost(), demo.getPort(), demo.getDatabaseName());
        } catch (Exception e) {
            log.warn("Failed to seed demo datasource: {}", e.getMessage());
        }
    }
}
