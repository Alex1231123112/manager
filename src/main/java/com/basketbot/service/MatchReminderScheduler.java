package com.basketbot.service;

import com.basketbot.model.Match;
import com.basketbot.model.Team;
import com.basketbot.util.TelegramChatIdUtil;
import com.basketbot.model.EventAttendance;
import com.basketbot.model.IntegrationEvent;
import com.basketbot.model.Player;
import com.basketbot.repository.MatchRepository;
import com.basketbot.service.PlayerService;
import com.basketbot.service.TeamService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Напоминания о матчах: за 24 ч — сообщение с кнопками подтверждения (Буду/Опоздаю/Не смогу), за 3 ч — напоминание, после матча — запрос результата.
 */
@Component
@ConditionalOnProperty(name = "telegram.bot.token")
public class MatchReminderScheduler {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("dd.MM HH:mm").withZone(ZoneId.systemDefault());

    private final MatchRepository matchRepository;
    private final TelegramClient telegramClient;
    private final EventAttendanceService eventAttendanceService;
    private final TeamMemberService teamMemberService;
    private final TeamService teamService;
    private final PlayerService playerService;
    private final IntegrationMetricsService integrationMetricsService;

    public MatchReminderScheduler(MatchRepository matchRepository, TelegramClient telegramClient,
                                  EventAttendanceService eventAttendanceService,
                                  TeamMemberService teamMemberService,
                                  TeamService teamService,
                                  PlayerService playerService,
                                  IntegrationMetricsService integrationMetricsService) {
        this.matchRepository = matchRepository;
        this.telegramClient = telegramClient;
        this.eventAttendanceService = eventAttendanceService;
        this.teamMemberService = teamMemberService;
        this.teamService = teamService;
        this.playerService = playerService;
        this.integrationMetricsService = integrationMetricsService;
    }

    @Scheduled(cron = "${telegram.bot.reminder-cron:0 */15 * * * ?}")
    @Transactional
    public void runReminders() {
        Instant now = Instant.now();
        // Окно 24 ч: матчи через 23–25 ч
        Instant from24 = now.plusSeconds(23 * 3600);
        Instant to24 = now.plusSeconds(25 * 3600);
        List<Match> for24 = matchRepository.findFor24hReminder(from24, to24);
        for (Match m : for24) {
            send24hReminder(m);
            m.setReminder24hSent(true);
            m.setReminder24hSentAt(Instant.now());
            matchRepository.save(m);
        }

        // Статистика подтверждений через ~2 ч после 24h напоминания
        Instant twoHoursAgo = now.minusSeconds(2 * 3600);
        List<Match> forStats = matchRepository.findForAttendanceStats(twoHoursAgo);
        for (Match m : forStats) {
            sendAttendanceStats(m);
            m.setReminderStatsSent(true);
            matchRepository.save(m);
        }

        // Окно 3 ч: матчи через 2ч30 – 3ч30
        Instant from3 = now.plusSeconds((long) (2.5 * 3600));
        Instant to3 = now.plusSeconds((long) (3.5 * 3600));
        List<Match> for3 = matchRepository.findFor3hReminder(from3, to3);
        for (Match m : for3) {
            send3hReminder(m);
            m.setReminder3hSent(true);
            matchRepository.save(m);
        }

        // После матча: матч был 0.5–25 ч назад
        Instant afterFrom = now.minusSeconds(25 * 3600);
        Instant afterTo = now.minusSeconds((long) (0.5 * 3600));
        List<Match> forAfter = matchRepository.findForAfterMatchReminder(afterFrom, afterTo);
        for (Match m : forAfter) {
            sendAfterMatchReminder(m);
            m.setReminderAfterSent(true);
            matchRepository.save(m);
        }
    }

    /** Раз в неделю (понедельник 10:00): напоминание о долгах в чат команды. Отключить: telegram.bot.debt-reminder-cron=- */
    @Scheduled(cron = "${telegram.bot.debt-reminder-cron:0 0 10 ? * MON}")
    @Transactional(readOnly = true)
    public void sendWeeklyDebtReminders() {
        for (Team team : teamService.findAll()) {
            String chatId = (team.getGroupTelegramChatId() != null && !team.getGroupTelegramChatId().isBlank())
                    ? TelegramChatIdUtil.normalizeGroupChatId(team.getGroupTelegramChatId())
                    : team.getTelegramChatId();
            if (chatId == null || chatId.isBlank()) continue;
            List<Player> debtors = playerService.findWithDebt(team.getId());
            if (debtors.isEmpty()) continue;
            StringBuilder sb = new StringBuilder("💰 Напоминание: кто не оплатил взносы?\n\n");
            for (Player p : debtors) {
                sb.append("• ").append(p.getName() != null ? p.getName() : "—");
                if (p.getNumber() != null) sb.append(" №").append(p.getNumber());
                sb.append(" — ").append(p.getDebt() != null ? p.getDebt().stripTrailingZeros().toPlainString() : "0").append(" ₽\n");
            }
            String text = sb.toString();
            if (text.length() > 4000) text = text.substring(0, 4000);
            try {
                telegramClient.execute(SendMessage.builder().chatId(chatId).text(text).build());
                integrationMetricsService.record(IntegrationEvent.EventType.DEBT_REMINDER, chatId, true, null, team.getId(), null);
            } catch (Exception e) {
                integrationMetricsService.record(IntegrationEvent.EventType.DEBT_REMINDER, chatId, false, e.getMessage(), team.getId(), null);
            }
        }
    }

    private void send24hReminder(Match match) {
        Team team = match.getTeam();
        String chatId = (team.getGroupTelegramChatId() != null && !team.getGroupTelegramChatId().isBlank())
                ? TelegramChatIdUtil.normalizeGroupChatId(team.getGroupTelegramChatId())
                : team.getTelegramChatId();
        if (chatId == null || chatId.isBlank()) return;
        String timeStr = TIME_FMT.format(match.getDate());
        String location = (match.getLocation() != null && !match.getLocation().isBlank()) ? "\n🏟️ " + match.getLocation() : "";
        String text = "[НОВОЕ СОБЫТИЕ]\n🏀 Игра vs " + match.getOpponent() + "\n📅 " + timeStr + location + "\n\nПодтвердите участие:";
        InlineKeyboardRow row = new InlineKeyboardRow();
        row.add(InlineKeyboardButton.builder().text("🟢 Буду").callbackData("attend:" + match.getId() + ":COMING").build());
        row.add(InlineKeyboardButton.builder().text("🟡 Опоздаю").callbackData("attend:" + match.getId() + ":LATE").build());
        row.add(InlineKeyboardButton.builder().text("🔴 Не смогу").callbackData("attend:" + match.getId() + ":NOT_COMING").build());
        InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder().keyboard(List.of(row)).build();
        try {
            telegramClient.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(text)
                    .replyMarkup(keyboard)
                    .build());
            integrationMetricsService.record(IntegrationEvent.EventType.REMINDER_24H, chatId, true, null, team.getId(), match.getId());
        } catch (Exception e) {
            integrationMetricsService.record(IntegrationEvent.EventType.REMINDER_24H, chatId, false, e.getMessage(), team.getId(), match.getId());
        }
    }

    private void sendAttendanceStats(Match match) {
        Team team = match.getTeam();
        String chatId = (team.getGroupTelegramChatId() != null && !team.getGroupTelegramChatId().isBlank())
                ? TelegramChatIdUtil.normalizeGroupChatId(team.getGroupTelegramChatId())
                : team.getTelegramChatId();
        if (chatId == null || chatId.isBlank()) return;
        var counts = eventAttendanceService.getCountsByStatus(match.getId());
        long coming = counts.getOrDefault(EventAttendance.Status.COMING, 0L);
        long late = counts.getOrDefault(EventAttendance.Status.LATE, 0L);
        long notComing = counts.getOrDefault(EventAttendance.Status.NOT_COMING, 0L);
        int responded = eventAttendanceService.getRespondedCount(match.getId());
        int totalMembers = teamMemberService.findByTeamId(team.getId()).stream().filter(m -> m.isActive()).toList().size();
        int noResponse = Math.max(0, totalMembers - responded);
        String text = "[Статистика голосования]\n✅ Подтвердили: " + coming
                + "\n🟡 Опоздают: " + late
                + "\n❌ Отказались: " + notComing
                + "\n❓ Не ответили: " + noResponse + (noResponse > 0 ? " (разошлём напоминание)" : "");
        try {
            telegramClient.execute(SendMessage.builder().chatId(chatId).text(text).build());
            integrationMetricsService.record(IntegrationEvent.EventType.REMINDER_STATS, chatId, true, null, team.getId(), match.getId());
        } catch (Exception e) {
            integrationMetricsService.record(IntegrationEvent.EventType.REMINDER_STATS, chatId, false, e.getMessage(), team.getId(), match.getId());
        }
    }

    private void send3hReminder(Match match) {
        Team team = match.getTeam();
        String chatId = (team.getGroupTelegramChatId() != null && !team.getGroupTelegramChatId().isBlank())
                ? TelegramChatIdUtil.normalizeGroupChatId(team.getGroupTelegramChatId())
                : team.getTelegramChatId();
        if (chatId == null || chatId.isBlank()) return;
        String timeStr = TIME_FMT.format(match.getDate());
        String text = "⏰ Через ~3 часа матч с «" + match.getOpponent() + "» (" + timeStr + "). Удачи!";
        try {
            telegramClient.execute(SendMessage.builder().chatId(chatId).text(text).build());
            integrationMetricsService.record(IntegrationEvent.EventType.REMINDER_3H, chatId, true, null, team.getId(), match.getId());
        } catch (Exception e) {
            integrationMetricsService.record(IntegrationEvent.EventType.REMINDER_3H, chatId, false, e.getMessage(), team.getId(), match.getId());
        }
    }

    private void sendAfterMatchReminder(Match match) {
        Team team = match.getTeam();
        String chatId = (team.getGroupTelegramChatId() != null && !team.getGroupTelegramChatId().isBlank())
                ? TelegramChatIdUtil.normalizeGroupChatId(team.getGroupTelegramChatId())
                : team.getTelegramChatId();
        if (chatId == null || chatId.isBlank()) return;
        String text = "Матч с «" + match.getOpponent() + "» прошёл. Введите результат и статистику: /result";
        try {
            telegramClient.execute(SendMessage.builder().chatId(chatId).text(text).build());
            integrationMetricsService.record(IntegrationEvent.EventType.REMINDER_AFTER_MATCH, chatId, true, null, team.getId(), match.getId());
        } catch (Exception e) {
            integrationMetricsService.record(IntegrationEvent.EventType.REMINDER_AFTER_MATCH, chatId, false, e.getMessage(), team.getId(), match.getId());
        }
    }
}
