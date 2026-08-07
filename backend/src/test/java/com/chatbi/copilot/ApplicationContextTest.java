package com.chatbi.copilot;

import com.chatbi.copilot.auth.entity.AppUser;
import com.chatbi.copilot.auth.mapper.AppUserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:chatbi-context;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "chatbi.demo-datasource.enabled=false",
        "chatbi.security.demo-auth-enabled=true",
        "server.port=0"
})
class ApplicationContextTest {

    @Autowired
    private AppUserMapper userMapper;

    @Test
    void migrationsAndDemoUsersInitialize() {
        assertThat(userMapper.selectList(null))
                .extracting(AppUser::getUsername)
                .containsExactlyInAnyOrder("admin", "analyst", "viewer");
    }
}
