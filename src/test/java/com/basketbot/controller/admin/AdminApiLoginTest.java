package com.basketbot.controller.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.servlet.http.HttpSession;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminApiLoginTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void login_withValidCredentials_returnsSuccess() throws Exception {
        mockMvc.perform(post("/api/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.error").isEmpty());
    }

    @Test
    void login_withValidCredentials_thenMe_returns200_withSession() throws Exception {
        var loginResult = mockMvc.perform(post("/api/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        HttpSession session = loginResult.getRequest().getSession(false);
        mockMvc.perform(get("/api/admin/me").session((MockHttpSession) session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teams").isArray())
                .andExpect(jsonPath("$.currentTeam").isEmpty());
    }

    @Test
    void me_withoutLogin_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/me"))
                .andExpect(status().isForbidden());
    }

    @Test
    void login_withInvalidPassword_returns401() throws Exception {
        mockMvc.perform(post("/api/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void login_withInvalidUsername_returns401() throws Exception {
        mockMvc.perform(post("/api/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"unknown\",\"password\":\"admin\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }
}
