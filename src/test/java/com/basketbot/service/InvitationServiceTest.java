package com.basketbot.service;

import com.basketbot.model.Invitation;
import com.basketbot.model.Team;
import com.basketbot.model.TeamMember;
import com.basketbot.repository.InvitationRepository;
import com.basketbot.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Автотесты по сценариям smoke: приглашения (2.1–2.5).
 * Покрывают создание приглашения, поиск по коду, использование (успех / неверный код / истёкший).
 */
@SpringBootTest
@ActiveProfiles("test")
class InvitationServiceTest {

    @Autowired
    private InvitationService invitationService;

    @Autowired
    private InvitationRepository invitationRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private TeamMemberService teamMemberService;

    private Team team;

    @BeforeEach
    void setUp() {
        team = new Team();
        team.setName("Test Team");
        team = teamRepository.save(team);
    }

    @Test
    void create_returnsInvitationWithCodeAndLinkContainsStart() {
        Invitation inv = invitationService.create(team.getId(), TeamMember.Role.PLAYER, 7);

        assertThat(inv.getCode()).isNotBlank();
        assertThat(inv.getRole()).isEqualTo(TeamMember.Role.PLAYER);
        assertThat(inv.getExpiresAt()).isAfter(Instant.now());

        String link = invitationService.buildInviteLink(inv.getCode());
        assertThat(link).contains("t.me/");
        assertThat(link).contains("start=");
        assertThat(link).contains(inv.getCode());
    }

    @Test
    void create_asAdminRole_savesRoleAdmin() {
        Invitation inv = invitationService.create(team.getId(), TeamMember.Role.ADMIN, 14);
        assertThat(inv.getRole()).isEqualTo(TeamMember.Role.ADMIN);
    }

    @Test
    void findByCode_validCode_returnsPresent() {
        Invitation inv = invitationService.create(team.getId(), TeamMember.Role.PLAYER, 7);
        assertThat(invitationService.findByCode(inv.getCode())).isPresent();
    }

    @Test
    void findByCode_expired_returnsEmpty() {
        Invitation inv = invitationService.create(team.getId(), TeamMember.Role.PLAYER, 7);
        inv = invitationRepository.findByCode(inv.getCode()).orElseThrow();
        inv.setExpiresAt(Instant.now().minusSeconds(3600));
        invitationRepository.save(inv);

        assertThat(invitationService.findByCode(inv.getCode())).isEmpty();
    }

    @Test
    void findByCode_nonexistent_returnsEmpty() {
        assertThat(invitationService.findByCode("nonexistent")).isEmpty();
    }

    @Test
    void findByCode_nullOrBlank_returnsEmpty() {
        assertThat(invitationService.findByCode(null)).isEmpty();
        assertThat(invitationService.findByCode("")).isEmpty();
        assertThat(invitationService.findByCode("   ")).isEmpty();
    }

    @Test
    void use_validCode_addsMemberAndReturnsTeamNameAndRole() {
        Invitation inv = invitationService.create(team.getId(), TeamMember.Role.PLAYER, 7);
        String code = inv.getCode();

        var result = invitationService.use(code, "12345", "testuser", "Test User");

        assertThat(result).isPresent();
        assertThat(result.get().teamName()).isEqualTo("Test Team");
        assertThat(result.get().role()).isEqualTo(TeamMember.Role.PLAYER);

        List<TeamMember> members = teamMemberService.findByTeamId(team.getId());
        assertThat(members).hasSize(1);
        assertThat(members.get(0).getTelegramUserId()).isEqualTo("12345");
        assertThat(members.get(0).getRole()).isEqualTo(TeamMember.Role.PLAYER);
    }

    @Test
    void use_emptyTelegramUserId_returnsEmpty() {
        Invitation inv = invitationService.create(team.getId(), TeamMember.Role.PLAYER, 7);
        assertThat(invitationService.use(inv.getCode(), null, "u", "N")).isEmpty();
        assertThat(invitationService.use(inv.getCode(), "", "u", "N")).isEmpty();
        assertThat(invitationService.use(inv.getCode(), "   ", "u", "N")).isEmpty();
    }

    @Test
    void use_nonexistentCode_returnsEmpty() {
        assertThat(invitationService.use("nonexistent", "12345", null, null)).isEmpty();
    }

    @Test
    void use_expiredCode_returnsEmpty() {
        Invitation inv = invitationService.create(team.getId(), TeamMember.Role.PLAYER, 7);
        inv = invitationRepository.findByCode(inv.getCode()).orElseThrow();
        inv.setExpiresAt(Instant.now().minusSeconds(3600));
        invitationRepository.save(inv);

        assertThat(invitationService.use(inv.getCode(), "12345", null, null)).isEmpty();
    }

    @Test
    void use_sameCodeTwice_bothSucceed() {
        Invitation inv = invitationService.create(team.getId(), TeamMember.Role.PLAYER, 7);
        String code = inv.getCode();

        assertThat(invitationService.use(code, "user1", null, null)).isPresent();
        assertThat(invitationService.use(code, "user2", null, null)).isPresent();

        List<TeamMember> members = teamMemberService.findByTeamId(team.getId());
        assertThat(members).hasSize(2);
    }

    @Test
    void findByTeamId_returnsInvitationsOrderedByCreatedAtDesc() {
        invitationService.create(team.getId(), TeamMember.Role.PLAYER, 7);
        invitationService.create(team.getId(), TeamMember.Role.ADMIN, 1);

        List<Invitation> list = invitationService.findByTeamId(team.getId());
        assertThat(list).hasSize(2);
        assertThat(list.get(0).getCreatedAt()).isAfterOrEqualTo(list.get(1).getCreatedAt());
    }

    @Test
    void deleteByCode_existingCode_removesInvitation() {
        Invitation inv = invitationService.create(team.getId(), TeamMember.Role.PLAYER, 7);
        boolean deleted = invitationService.deleteByCode(team.getId(), inv.getCode());

        assertThat(deleted).isTrue();
        assertThat(invitationRepository.findByCode(inv.getCode())).isEmpty();
    }

    @Test
    void deleteByCode_wrongTeamId_doesNotDelete() {
        Team other = new Team();
        other.setName("Other");
        other = teamRepository.save(other);
        Invitation inv = invitationService.create(team.getId(), TeamMember.Role.PLAYER, 7);
        boolean deleted = invitationService.deleteByCode(other.getId(), inv.getCode());

        assertThat(deleted).isFalse();
        assertThat(invitationRepository.findByCode(inv.getCode())).isPresent();
    }
}
