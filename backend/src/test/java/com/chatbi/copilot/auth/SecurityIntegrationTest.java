package com.chatbi.copilot.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:security-http;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "chatbi.demo-datasource.enabled=false",
        "chatbi.security.demo-auth-enabled=true",
        "server.port=0"
})
@AutoConfigureMockMvc
class SecurityIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void loginCookieCsrfAndRoleBoundariesAreEnforced() throws Exception {
        mvc.perform(get("/health/live")).andExpect(status().isOk());
        mvc.perform(get("/health/ready")).andExpect(status().isOk());
        mvc.perform(get("/health")).andExpect(status().isUnauthorized());
        mvc.perform(get("/datasources")).andExpect(status().isUnauthorized());

        Session analyst = login("analyst", "ChatBI!Analyst123");
        mvc.perform(get("/datasources").cookie(analyst.cookie()))
                .andExpect(status().isOk());
        mvc.perform(post("/auth/logout").cookie(analyst.cookie()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/admin/users").cookie(analyst.cookie()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/auth/logout").cookie(analyst.cookie())
                        .header("X-CSRF-Token", analyst.csrf()))
                .andExpect(status().isOk());
        mvc.perform(get("/datasources").cookie(analyst.cookie()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void administratorCanReachUserManagement() throws Exception {
        Session admin = login("admin", "ChatBI!Admin123");
        mvc.perform(get("/admin/users").cookie(admin.cookie()))
                .andExpect(status().isOk());
    }

    @Test
    void localFrontendOriginsAreAllowedButUnknownOriginsAreRejected() throws Exception {
        mvc.perform(options("/auth/login")
                        .header("Origin", "http://127.0.0.1:19031")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://127.0.0.1:19031"));
        mvc.perform(options("/auth/login")
                        .header("Origin", "https://attacker.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    private Session login(String username, String password) throws Exception {
        MvcResult result = mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = result.getResponse().getCookie("CHATBI_SESSION");
        Cookie csrfCookie = result.getResponse().getCookie("CHATBI_CSRF");
        assertThat(cookie).isNotNull();
        assertThat(csrfCookie).isNotNull();
        assertThat(csrfCookie.isHttpOnly()).isFalse();
        assertThat(result.getResponse().getHeaders("Set-Cookie"))
                .anySatisfy(header -> assertThat(header).contains("CHATBI_SESSION", "HttpOnly", "SameSite=Strict"))
                .anySatisfy(header -> assertThat(header).contains("CHATBI_CSRF", "SameSite=Strict"));
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(csrfCookie.getValue()).isEqualTo(body.path("data").path("csrfToken").asText());
        return new Session(cookie, body.path("data").path("csrfToken").asText());
    }

    private record Session(Cookie cookie, String csrf) {
    }
}
