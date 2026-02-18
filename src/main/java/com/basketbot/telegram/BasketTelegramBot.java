package com.basketbot.telegram;

import com.basketbot.model.Match;
import com.basketbot.model.Player;
import com.basketbot.model.Team;
import com.basketbot.service.InvitationService;
import com.basketbot.service.MatchService;
import com.basketbot.service.PlayerService;
import com.basketbot.service.TeamMemberService;
import com.basketbot.service.SystemSettingsService;
import com.basketbot.service.TeamService;
import com.basketbot.config.TelegramBotProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.polls.SendPoll;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.polls.input.InputPollOption;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "telegram.bot.token")
public class BasketTelegramBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

    private static final String BTN_SCHEDULE = "Расписание";
    private static final String BTN_ROSTER = "Состав";
    private static final String BTN_PROFILE = "Мой профиль";
    private static final String BTN_LEAVE = "Выйти из команды";
    private static final String BTN_POLL = "Опрос на игру";
    private static final List<String> POLL_OPTION_STRINGS = List.of("Еду", "Не еду", "Опоздаю");
    private static final Map<Player.PlayerStatus, String> STATUS_LABEL = Map.of(
            Player.PlayerStatus.ACTIVE, "активен",
            Player.PlayerStatus.INJURY, "травма",
            Player.PlayerStatus.VACATION, "отпуск",
            Player.PlayerStatus.NOT_PAID, "не оплатил"
    );
    private static final List<InputPollOption> POLL_OPTIONS = POLL_OPTION_STRINGS.stream()
            .map(InputPollOption::new)
            .toList();

    private final TelegramBotProperties properties;
    private final TelegramClient telegramClient;
    private final TeamService teamService;
    private final TeamMemberService teamMemberService;
    private final MatchService matchService;
    private final PlayerService playerService;
    private final SystemSettingsService systemSettingsService;
    private final InvitationService invitationService;

    /** Ожидание названия команды после /start (chatId -> true) */
    private final Map<Long, Boolean> pendingTeamName = new ConcurrentHashMap<>();
    /** Ожидание нового имени для профиля (chatId -> teamId) */
    private final Map<Long, Long> pendingProfileTeamId = new ConcurrentHashMap<>();
    /** Ожидание подтверждения выхода (chatId -> teamId) */
    private final Map<Long, Long> pendingLeaveTeamId = new ConcurrentHashMap<>();

    public BasketTelegramBot(TelegramBotProperties properties,
                            org.telegram.telegrambots.meta.generics.TelegramClient telegramClient,
                            TeamService teamService,
                            TeamMemberService teamMemberService,
                            MatchService matchService,
                            PlayerService playerService,
                            SystemSettingsService systemSettingsService,
                            InvitationService invitationService) {
        this.properties = properties;
        this.telegramClient = telegramClient;
        this.teamService = teamService;
        this.teamMemberService = teamMemberService;
        this.matchService = matchService;
        this.playerService = playerService;
        this.systemSettingsService = systemSettingsService;
        this.invitationService = invitationService;
    }

    @Override
    public String getBotToken() {
        return properties.getToken();
    }

    @Override
    public LongPollingSingleThreadUpdateConsumer getUpdatesConsumer() {
        return this;
    }

    @PostConstruct
    public void registerBotCommands() {
        List<BotCommand> commands = List.of(
                new BotCommand("start", "Создать команду или войти по приглашению"),
                new BotCommand("schedule", "Расписание игр"),
                new BotCommand("roster", "Состав команды"),
                new BotCommand("profile", "Редактировать своё имя"),
                new BotCommand("leave", "Выйти из команды"),
                new BotCommand("poll", "Опрос на игру: poll Текст вопроса")
        );
        try {
            telegramClient.execute(SetMyCommands.builder()
                    .scope(BotCommandScopeDefault.builder().build())
                    .commands(commands)
                    .build());
        } catch (Exception ignored) {
            // токен или сеть недоступны при старте
        }
    }

    @Override
    public void consume(Update update) {
        if (update.hasCallbackQuery()) {
            try {
                handleCallbackQuery(update.getCallbackQuery());
            } catch (Exception e) {
                sendMessage(update.getCallbackQuery().getMessage().getChatId(), "Ошибка: " + e.getMessage());
            }
            return;
        }

        if (!update.hasMessage() || !update.getMessage().hasText()) return;

        String text = update.getMessage().getText().trim();
        long chatId = update.getMessage().getChatId();

        try {
            if (pendingTeamName.remove(chatId) != null) {
                var from = update.getMessage().getFrom();
                long creatorId = from != null ? from.getId() : 0;
                String creatorUsername = from != null ? from.getUserName() : null;
                createTeamAndSendMenu(chatId, text, creatorId, creatorUsername);
                return;
            }

            if ("/start".equals(text) || text.startsWith("/start ")) {
                var from = update.getMessage().getFrom();
                long telegramUserId = from != null ? from.getId() : 0;
                String telegramUserIdStr = String.valueOf(telegramUserId);
                String startPayload = text.length() > 7 ? text.substring(7).trim() : "";
                String telegramUsername = from != null ? from.getUserName() : null;
                handleStart(chatId, telegramUserIdStr, startPayload, telegramUsername);
                return;
            }

            Long teamIdForProfile = pendingProfileTeamId.remove(chatId);
            if (teamIdForProfile != null) {
                var from = update.getMessage().getFrom();
                String telegramUserIdStr = from != null ? String.valueOf(from.getId()) : null;
                if (telegramUserIdStr != null && !text.isBlank()) {
                    teamMemberService.updateDisplayName(teamIdForProfile, telegramUserIdStr, text);
                    sendMessageWithReplyKeyboard(chatId, "Имя обновлено: " + text.trim(), mainMenu());
                } else {
                    sendMessage(chatId, "Имя не может быть пустым. Нажми «Мой профиль» и отправь новое имя.");
                }
                return;
            }

            Long teamIdForLeave = pendingLeaveTeamId.remove(chatId);
            if (teamIdForLeave != null) {
                if ("ДА".equalsIgnoreCase(text.trim())) {
                    var from = update.getMessage().getFrom();
                    String telegramUserIdStr = from != null ? String.valueOf(from.getId()) : null;
                    if (telegramUserIdStr != null) {
                        teamMemberService.leaveTeam(teamIdForLeave, telegramUserIdStr);
                        sendMessage(chatId, "Вы вышли из команды. Чтобы снова вступить, нужна новая ссылка-приглашение от админа.");
                    }
                } else {
                    pendingLeaveTeamId.put(chatId, teamIdForLeave);
                    sendMessage(chatId, "Выход отменён. Напиши ДА, если точно хочешь выйти из команды.");
                }
                return;
            }

            Optional<Team> teamOpt = teamService.findByTelegramChatId(String.valueOf(chatId));
            if (teamOpt.isEmpty()) {
                sendMessage(chatId, "Сначала создай команду: отправь /start и затем название команды.");
                return;
            }
            Team team = teamOpt.get();
            Long teamId = team.getId();
            var from = update.getMessage().getFrom();
            String telegramUserIdStr = from != null ? String.valueOf(from.getId()) : null;
            if (from != null && from.getUserName() != null && telegramUserIdStr != null) {
                teamMemberService.ensureTelegramUsername(teamId, telegramUserIdStr, from.getUserName());
            }

            if ("/schedule".equals(text) || BTN_SCHEDULE.equals(text)) {
                sendSchedule(chatId, teamId);
                return;
            }
            if ("/roster".equals(text) || BTN_ROSTER.equals(text)) {
                sendRoster(chatId, teamId);
                return;
            }
            if ("/profile".equals(text) || BTN_PROFILE.equals(text)) {
                pendingProfileTeamId.put(chatId, teamId);
                sendMessage(chatId, "Напиши новое имя (как отображать в составе):");
                return;
            }
            if ("/leave".equals(text) || BTN_LEAVE.equals(text)) {
                pendingLeaveTeamId.put(chatId, teamId);
                sendMessage(chatId, "Выйти из команды? Напиши ДА для подтверждения.");
                return;
            }
            if ("/poll".equals(text) || BTN_POLL.equals(text)) {
                sendMessage(chatId, "Опрос на игру. Напиши: /poll Текст вопроса\nНапример: /poll Кто едет в субботу?");
                return;
            }
            if (text.startsWith("/poll ")) {
                sendPoll(chatId, text.substring("/poll ".length()).trim());
                return;
            }

            sendMessageWithReplyKeyboard(chatId, "Используй кнопки меню или команды: /schedule, /roster, /profile, /leave, /poll Текст.", mainMenu());
        } catch (Exception e) {
            sendMessage(chatId, "Ошибка: " + e.getMessage());
        }
    }

    private void handleStart(long chatId, String telegramUserIdStr, String startPayload, String telegramUsername) {
        Optional<Team> teamOpt = teamService.findByTelegramChatId(String.valueOf(chatId));
        if (teamOpt.isPresent()) {
            Team team = teamOpt.get();
            if (telegramUsername != null && !telegramUsername.isBlank()) {
                teamMemberService.ensureTelegramUsername(team.getId(), telegramUserIdStr, telegramUsername);
            }
            sendMessageWithReplyKeyboard(chatId, "Привет! Команда: «" + team.getName() + "». Расписание, состав, мой профиль, выход из команды, опрос — кнопки ниже.", mainMenu());
        } else if (!startPayload.isEmpty()) {
            handleStartWithInviteCode(chatId, telegramUserIdStr, startPayload, telegramUsername);
        } else {
            if (!systemSettingsService.canCreateTeamWithoutInvite(telegramUserIdStr, telegramUsername)) {
                sendMessage(chatId, "Создать команду может только администратор. Попросите ссылку-приглашение у менеджера команды.");
                return;
            }
            pendingTeamName.put(chatId, true);
            sendMessage(chatId, "Привет! Отправь название команды одним сообщением.\nНапример: БК Метеор");
        }
    }

    /** Обработка /start CODE (приглашение). Вызывается из handleStart при непустом payload. */
    private void handleStartWithInviteCode(long chatId, String telegramUserIdStr, String code, String telegramUsername) {
        Optional<InvitationService.InvitationUseResult> result = invitationService.use(code, telegramUserIdStr, telegramUsername);
        if (result.isPresent()) {
            InvitationService.InvitationUseResult r = result.get();
            sendMessage(chatId, "Вы добавлены в команду «" + r.teamName() + "». Роль: " + r.role() + ".\nЕсли бот в чате команды, напиши там /start.");
        } else {
            sendMessage(chatId, "Приглашение недействительно или уже использовано.");
        }
    }

    private void createTeamAndSendMenu(long chatId, String teamName, long creatorTelegramUserId, String creatorUsername) {
        if (teamName.isBlank()) {
            sendMessage(chatId, "Название не может быть пустым. Отправь название команды.");
            pendingTeamName.put(chatId, true);
            return;
        }
        Team team = teamService.createTeam(teamName, String.valueOf(chatId));
        if (creatorTelegramUserId != 0) {
            teamMemberService.addAsAdmin(team.getId(), String.valueOf(creatorTelegramUserId));
            if (creatorUsername != null && !creatorUsername.isBlank()) {
                teamMemberService.ensureTelegramUsername(team.getId(), String.valueOf(creatorTelegramUserId), creatorUsername);
            }
        }
        sendMessageWithReplyKeyboard(chatId, "Команда «" + team.getName() + "» создана. Участники видят расписание, состав, могут редактировать профиль и выйти из команды. Админка — для управления.", mainMenu());
    }

    private void handleCallbackQuery(CallbackQuery callbackQuery) {
        answerCallback(callbackQuery.getId(), "Команда недоступна. Управление — в админке.", true);
    }

    private void answerCallback(String callbackQueryId, String text, boolean showAlert) {
        try {
            AnswerCallbackQuery.AnswerCallbackQueryBuilder builder = AnswerCallbackQuery.builder()
                    .callbackQueryId(callbackQueryId)
                    .showAlert(showAlert);
            if (text != null && !text.isEmpty()) builder.text(text);
            telegramClient.execute(builder.build());
        } catch (Exception e) {
            throw new RuntimeException("Не удалось ответить на callback", e);
        }
    }

    private void sendPoll(long chatId, String question) {
        String q = (question != null && !question.isBlank()) ? question : "Кто едет на игру?";
        if (q.length() > 255) q = q.substring(0, 255);
        try {
            SendPoll poll = SendPoll.builder()
                    .chatId(String.valueOf(chatId))
                    .question(q)
                    .options(POLL_OPTIONS)
                    .build();
            telegramClient.execute(poll);
        } catch (Exception e) {
            throw new RuntimeException("Не удалось отправить опрос", e);
        }
    }

    private ReplyKeyboardMarkup mainMenu() {
        List<KeyboardRow> rows = new ArrayList<>();
        rows.add(new KeyboardRow(BTN_SCHEDULE, BTN_ROSTER));
        rows.add(new KeyboardRow(BTN_PROFILE, BTN_LEAVE, BTN_POLL));
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup(rows);
        markup.setResizeKeyboard(true);
        return markup;
    }

    private void sendSchedule(long chatId, Long teamId) {
        List<Match> matches = matchService.findByTeamIdOrderByDateDesc(teamId);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());
        if (matches.isEmpty()) {
            sendMessage(chatId, "Расписание пусто. Ближайшие игры добавят в админке.");
            return;
        }
        StringBuilder sb = new StringBuilder("Расписание игр:\n\n");
        for (Match m : matches) {
            String dateStr = fmt.format(m.getDate());
            String status = m.getStatus() == Match.Status.SCHEDULED ? "⏳" : (m.getStatus() == Match.Status.COMPLETED ? "✅" : "❌");
            sb.append(status).append(" ").append(dateStr).append(" — ").append(m.getOpponent());
            if (m.getStatus() == Match.Status.COMPLETED && m.getOurScore() != null && m.getOpponentScore() != null) {
                sb.append(" (").append(m.getOurScore()).append(":").append(m.getOpponentScore()).append(")");
            }
            if (m.getLocation() != null && !m.getLocation().isBlank()) {
                sb.append(", ").append(m.getLocation());
            }
            sb.append("\n");
        }
        sendMessage(chatId, sb.toString());
    }

    private void sendRoster(long chatId, Long teamId) {
        List<Player> players = playerService.findByTeamId(teamId);
        if (players.isEmpty()) {
            sendMessage(chatId, "Состав пуст. Участников добавляют через приглашения и админку.");
            return;
        }
        StringBuilder sb = new StringBuilder("Состав команды:\n\n");
        for (Player p : players) {
            if (!p.isActive()) continue;
            sb.append("• ").append(p.getName());
            if (p.getNumber() != null) sb.append(" №").append(p.getNumber());
            sb.append(" — ").append(STATUS_LABEL.getOrDefault(p.getPlayerStatus(), p.getPlayerStatus().name())).append("\n");
        }
        sendMessage(chatId, sb.toString());
    }

    private void sendMessage(long chatId, String messageText) {
        sendMessageWithReplyKeyboard(chatId, messageText, null);
    }

    private void sendMessageWithReplyKeyboard(long chatId, String messageText, ReplyKeyboardMarkup replyMarkup) {
        try {
            SendMessage.SendMessageBuilder builder = SendMessage.builder()
                    .chatId(String.valueOf(chatId))
                    .text(messageText);
            if (replyMarkup != null) {
                builder.replyMarkup(replyMarkup);
            }
            telegramClient.execute(builder.build());
        } catch (Exception e) {
            throw new RuntimeException("Не удалось отправить сообщение в Telegram", e);
        }
    }

}
