package com.tradingassistant.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void registerLoginRefreshAndProtectAdminApi() throws Exception {
        var credentials = """
                {"email":"user@example.com","displayName":"测试用户","password":"StrongPass123!"}
                """;
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());

        var loginResult = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"password\":\"StrongPass123!\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode login = objectMapper.readTree(loginResult.getResponse().getContentAsString());

        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RefreshRequest(login.get("refreshToken").asText()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());

        mvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + login.get("accessToken").asText()))
                .andExpect(status().isForbidden());
    }

    @Test
    void logoutAllRevokesEveryRefreshSession() throws Exception {
        var registerResult = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"logout-all@example.com","displayName":"会话用户","password":"StrongPass123!"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode session = objectMapper.readTree(registerResult.getResponse().getContentAsString());

        mvc.perform(post("/api/v1/me/logout-all")
                        .header("Authorization", "Bearer " + session.get("accessToken").asText()))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RefreshRequest(session.get("refreshToken").asText()))))
                .andExpect(status().isUnauthorized());
    }

    private record RefreshRequest(String refreshToken) {}
}
