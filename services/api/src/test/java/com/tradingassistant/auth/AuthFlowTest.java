package com.tradingassistant.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import com.tradingassistant.audit.UserOperationAudit;
import com.tradingassistant.audit.UserOperationAuditRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserOperationAuditRepository operationAudits;

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

        mvc.perform(get("/api/v1/market-data/config")
                        .header("Authorization", "Bearer " + login.get("accessToken").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("AKSHARE"));

        mvc.perform(put("/api/v1/admin/market-data/config")
                        .header("Authorization", "Bearer " + login.get("accessToken").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"AKSHARE","mode":"SINGLE_STOCK",
                                 "snapshotSource":"EASTMONEY","singleSource":"XUEQIU",
                                 "refreshSeconds":10}
                                """))
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

    @Test
    void changePasswordValidatesInputRevokesRefreshTokenAndWritesSafeAudits() throws Exception {
        String email = "change-password@example.com";
        var registerResult = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"change-password@example.com","displayName":"改密用户",
                                 "password":"StrongPass123!"}
                                """))
                .andExpect(status().isCreated()).andReturn();
        JsonNode session = objectMapper.readTree(registerResult.getResponse().getContentAsString());
        String bearer = "Bearer " + session.get("accessToken").asText();

        changePassword(bearer, "WrongPass123!", "AnotherPass456!", "AnotherPass456!")
                .andExpect(status().isUnauthorized());
        changePassword(bearer, "StrongPass123!", "weak", "weak")
                .andExpect(status().isBadRequest());
        changePassword(bearer, "StrongPass123!", "StrongPass123!", "StrongPass123!")
                .andExpect(status().isBadRequest());
        changePassword(bearer, "StrongPass123!", "AnotherPass456!", "DifferentPass456!")
                .andExpect(status().isBadRequest());
        changePassword(bearer, "StrongPass123!", "AnotherPass456!", "AnotherPass456!")
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RefreshRequest(session.get("refreshToken").asText()))))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email
                                + "\",\"password\":\"AnotherPass456!\"}"))
                .andExpect(status().isOk());

        var records = operationAudits.findAll().stream()
                .filter(audit -> audit.getAction() == UserOperationAudit.Action.PASSWORD_CHANGED)
                .toList();
        org.assertj.core.api.Assertions.assertThat(records)
                .extracting(UserOperationAudit::getResult)
                .contains(UserOperationAudit.Result.FAILURE, UserOperationAudit.Result.SUCCESS);
    }

    @Test
    void performanceEndpointRequiresOwnerAndMarksEmptyHistoryAsAccumulating() throws Exception {
        var registerResult = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"performance@example.com","displayName":"收益用户",
                                 "password":"StrongPass123!"}
                                """))
                .andExpect(status().isCreated()).andReturn();
        JsonNode session = objectMapper.readTree(registerResult.getResponse().getContentAsString());

        mvc.perform(get("/api/v1/me/performance")
                        .header("Authorization", "Bearer " + session.get("accessToken").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyProfit").value(0))
                .andExpect(jsonPath("$.dailyReturnPercent").value(0))
                .andExpect(jsonPath("$.status").value("ACCUMULATING"))
                .andExpect(jsonPath("$.referenceNotice").isNotEmpty());
        mvc.perform(get("/api/v1/me/performance")).andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.ResultActions changePassword(String bearer,
            String currentPassword, String newPassword, String confirmPassword) throws Exception {
        return mvc.perform(post("/api/v1/me/change-password")
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ChangePasswordRequest(
                        currentPassword, newPassword, confirmPassword))));
    }

    private record RefreshRequest(String refreshToken) {}
    private record ChangePasswordRequest(String currentPassword, String newPassword,
                                         String confirmPassword) {}
}
