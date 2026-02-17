package com.basketbot.controller.admin;

import com.basketbot.model.Team;
import com.basketbot.service.TeamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminApiInvitationsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TeamService teamService;

    private Team team;

    @BeforeEach
    void setUp() {
        team = teamService.createTeam("Test Team", null);
    }

    @Test
    void invitations_withoutLogin_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/invitations"))
                .andExpect(status().isForbidden());
    }

    @Test
    void invitations_withLoginButNoTeam_returns403() throws Exception {
        MockHttpSession session = loginAndGetSession();
        mockMvc.perform(get("/api/admin/invitations").session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void invitations_withLoginAndTeam_returns200_andEmptyList() throws Exception {
        MockHttpSession session = loginAndSelectTeam();
        mockMvc.perform(get("/api/admin/invitations").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void createInvitation_withLoginAndTeam_returns201_withCodeAndLink() throws Exception {
        MockHttpSession session = loginAndSelectTeam();
        mockMvc.perform(post("/api/admin/invitations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"PLAYER\",\"expiresInDays\":7}")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.invitation.code").isNotEmpty())
                .andExpect(jsonPath("$.invitation.link").value(org.hamcrest.Matchers.containsString("start=")))
                .andExpect(jsonPath("$.invitation.role").value("PLAYER"));
    }

    @Test
    void createInvitation_thenList_returnsOneItem() throws Exception {
        MockHttpSession session = loginAndSelectTeam();
        mockMvc.perform(post("/api/admin/invitations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"CAPTAIN\",\"expiresInDays\":14}")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invitation.code").isNotEmpty());

        mockMvc.perform(get("/api/admin/invitations").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].role").value("CAPTAIN"));
    }

    @Test
    void deleteInvitation_afterCreate_returns200_andRemovesFromList() throws Exception {
        MockHttpSession session = loginAndSelectTeam();
        String body = mockMvc.perform(post("/api/admin/invitations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"PLAYER\",\"expiresInDays\":7}")
                        .session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String code = com.jayway.jsonpath.JsonPath.parse(body).read("$.invitation.code", String.class);

        mockMvc.perform(delete("/api/admin/invitations/" + code).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/admin/invitations").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void createInvitation_invalidRole_returns400() throws Exception {
        MockHttpSession session = loginAndSelectTeam();
        mockMvc.perform(post("/api/admin/invitations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"INVALID\",\"expiresInDays\":7}")
                        .session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    private MockHttpSession loginAndGetSession() throws Exception {
        ResultActions login = mockMvc.perform(post("/api/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin\"}"))
                .andExpect(status().isOk());
        return (MockHttpSession) login.andReturn().getRequest().getSession(false);
    }

    private MockHttpSession loginAndSelectTeam() throws Exception {
        MockHttpSession session = loginAndGetSession();
        mockMvc.perform(post("/api/admin/team-select")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teamId\":" + team.getId() + "}")
                        .session(session))
                .andExpect(status().isOk());
        return session;
    }
}
