package com.basketbot.controller.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminApiSystemSettingsTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void systemSettings_withoutLogin_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/system-settings"))
                .andExpect(status().isForbidden());
    }

    @Test
    void systemSettings_withLogin_returns200_andAdminTelegramUsername() throws Exception {
        MockHttpSession session = loginAndGetSession();
        mockMvc.perform(get("/api/admin/system-settings").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adminTelegramUsername").exists());
    }

    @Test
    void putSystemSettings_withLogin_savesAndReturns200() throws Exception {
        MockHttpSession session = loginAndGetSession();
        mockMvc.perform(put("/api/admin/system-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adminTelegramUsername\":\"adminuser\"}")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/admin/system-settings").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adminTelegramUsername").value("adminuser"));
    }

    @Test
    void putSystemSettings_withoutLogin_returns403() throws Exception {
        mockMvc.perform(put("/api/admin/system-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adminTelegramUsername\":\"@user\"}"))
                .andExpect(status().isForbidden());
    }

    private MockHttpSession loginAndGetSession() throws Exception {
        ResultActions login = mockMvc.perform(post("/api/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin\"}"))
                .andExpect(status().isOk());
        return (MockHttpSession) login.andReturn().getRequest().getSession(false);
    }
}
