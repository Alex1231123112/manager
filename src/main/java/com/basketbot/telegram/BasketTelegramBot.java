package com.basketbot.telegram;

import com.basketbot.model.Invitation;
import com.basketbot.model.Match;
import com.basketbot.model.Player;
import com.basketbot.model.Team;
import com.basketbot.model.TeamMember;
import com.basketbot.model.EventAttendance;
import com.basketbot.model.IntegrationEvent;
import com.basketbot.service.EventAttendanceService;
import com.basketbot.service.IntegrationMetricsService;
import com.basketbot.service.InvitationService;
import com.basketbot.service.MatchService;
import com.basketbot.service.QrCodeService;
import com.basketbot.service.PlayerService;
import com.basketbot.service.TeamMemberService;
import com.basketbot.service.SystemSettingsService;
import com.basketbot.service.TeamService;
import com.basketbot.config.TelegramBotProperties;
import com.basketbot.util.TelegramChatIdUtil;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.polls.SendPoll;
import org.telegram.telegrambots.meta.api.objects.InputFile;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "telegram.bot.token")
public class BasketTelegramBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

    private static final Logger log = LoggerFactory.getLogger(BasketTelegramBot.class);
    private static final String BTN_SCHEDULE = "Расписание";
    private static final String BTN_PAST_MATCHES = "Прошедшие игры";
    private static final String BTN_ROSTER = "Состав";
    private static final String BTN_PROFILE = "Мой профиль";
    private static final String BTN_LEAVE = "Выйти из команды";
    private static final String BTN_POLL = "Опрос на игру";
    private static final String BTN_INVITE = "Приглашение";
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
    private final QrCodeService qrCodeService;
    private final EventAttendanceService eventAttendanceService;
    private final IntegrationMetricsService integrationMetricsService;

    /** Ожидание названия команды после /start (chatId -> true) */
    private final Map<Long, Boolean> pendingTeamName = new ConcurrentHashMap<>();
    /** Ожидание подтверждения выхода (chatId -> teamId) */
    private final Map<Long, Long> pendingLeaveTeamId = new ConcurrentHashMap<>();
    /** Создание опроса: chatId -> "" (ждём вопрос) или вопрос (ждём варианты ответа) */
    private final Map<Long, String> pendingPollQuestion = new ConcurrentHashMap<>();

    public BasketTelegramBot(TelegramBotProperties properties,
                            org.telegram.telegrambots.meta.generics.TelegramClient telegramClient,
                            TeamService teamService,
                            TeamMemberService teamMemberService,
                            MatchService matchService,
                            PlayerService playerService,
                            SystemSettingsService systemSettingsService,
                            InvitationService invitationService,
                            QrCodeService qrCodeService,
                            EventAttendanceService eventAttendanceService,
                            IntegrationMetricsService integrationMetricsService) {
        this.properties = properties;
        this.telegramClient = telegramClient;
        this.teamService = teamService;
        this.teamMemberService = teamMemberService;
        this.matchService = matchService;
        this.playerService = playerService;
        this.systemSettingsService = systemSettingsService;
        this.invitationService = invitationService;
        this.qrCodeService = qrCodeService;
        this.eventAttendanceService = eventAttendanceService;
        this.integrationMetricsService = integrationMetricsService;
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
                new BotCommand("profile", "Посмотреть свой профиль (имя, номер, долг)"),
                new BotCommand("leave", "Выйти из команды"),
                new BotCommand("poll", "Опрос на игру (вопрос и варианты по шагам)"),
                new BotCommand("invite", "Создать приглашение в команду (ссылка и QR)")
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
        if (update.hasMessage() && update.getMessage().hasText()) {
            log.info("Bot received: chatId={}, text={}", update.getMessage().getChatId(), update.getMessage().getText());
        }
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
                String firstName = from != null ? from.getFirstName() : null;
                String lastName = from != null ? from.getLastName() : null;
                handleStart(chatId, telegramUserIdStr, startPayload, telegramUsername, firstName, lastName);
                return;
            }

            Long teamIdForLeave = pendingLeaveTeamId.remove(chatId);
            if (teamIdForLeave != null) {
                var from = update.getMessage().getFrom();
                String telegramUserIdStr = from != null ? String.valueOf(from.getId()) : null;
                if (telegramUserIdStr != null && !teamMemberService.canUseBot(teamIdForLeave, telegramUserIdStr)) {
                    sendMessage(chatId, "Вы деактивированы. Обратитесь к администратору команды.");
                    return;
                }
                if ("ДА".equalsIgnoreCase(text.trim())) {
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

            String pollState = pendingPollQuestion.get(chatId);
            if (pollState != null) {
                var from = update.getMessage().getFrom();
                String telegramUserIdStr = from != null ? String.valueOf(from.getId()) : null;
                Optional<Team> teamOpt = resolveTeam(chatId, telegramUserIdStr);
                if (teamOpt.isEmpty()) {
                    pendingPollQuestion.remove(chatId);
                    sendMessage(chatId, "Сначала создай команду: отправь /start и затем название команды.");
                    return;
                }
                if (telegramUserIdStr != null && !teamMemberService.canUseBot(teamOpt.get().getId(), telegramUserIdStr)) {
                    pendingPollQuestion.remove(chatId);
                    sendMessage(chatId, "Вы деактивированы. Обратитесь к администратору команды.");
                    return;
                }
                if (pollState.isEmpty()) {
                    String question = text.length() > 255 ? text.substring(0, 255) : text;
                    if (question.isBlank()) {
                        sendMessage(chatId, "Вопрос не может быть пустым. Напиши вопрос для опроса.");
                        return;
                    }
                    pendingPollQuestion.put(chatId, question);
                    sendMessage(chatId, "Напиши варианты ответа через запятую. Например: Еду, Не еду, Опоздаю");
                } else {
                    List<String> options = parsePollOptions(text);
                    pendingPollQuestion.remove(chatId);
                    if (options.size() < 2) {
                        sendMessage(chatId, "Нужно минимум 2 варианта ответа. Нажми «Опрос на игру» и начни заново.");
                        return;
                    }
                    Team team = teamOpt.get();
                    // Чат для опроса: если задан групповой чат в настройках — туда, иначе чат команды, иначе личка пользователя
                    String pollChatIdStr = (team.getGroupTelegramChatId() != null && !team.getGroupTelegramChatId().isBlank())
                            ? TelegramChatIdUtil.normalizeGroupChatId(team.getGroupTelegramChatId())
                            : team.getTelegramChatId();
                    if (pollChatIdStr == null || pollChatIdStr.isBlank()) {
                        pollChatIdStr = String.valueOf(chatId);
                    }
                    log.info("Sending poll to chatId={} (groupChatId={}, teamChatId={})", pollChatIdStr, team.getGroupTelegramChatId(), team.getTelegramChatId());
                    try {
                        sendPoll(pollChatIdStr, pollState, options);
                        sendMessageWithReplyKeyboard(chatId, "Опрос отправлен в чат команды.", mainMenu());
                    } catch (Exception ex) {
                        String cause = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                        if (cause == null) cause = ex.getMessage();
                        if (cause == null) cause = ex.getClass().getSimpleName();
                        log.warn("SendPoll failed: targetChatId={}, cause={}", pollChatIdStr, cause, ex);
                        sendMessageWithReplyKeyboard(chatId,
                                "Не удалось отправить опрос в группу (ID чата: " + pollChatIdStr + ").\nПричина: " + cause
                                        + "\n\nУбедитесь: 1) бот добавлен в группу, 2) в админке → Настройки указан «ID группового чата» (как в группе: -100…).",
                                mainMenu());
                    }
                }
                return;
            }

            Optional<Team> teamOpt = resolveTeam(chatId, update.getMessage().getFrom() != null ? String.valueOf(update.getMessage().getFrom().getId()) : null);
            if (teamOpt.isEmpty()) {
                sendMessage(chatId, "Сначала создай команду: отправь /start и затем название команды.");
                return;
            }
            Team team = teamOpt.get();
            Long teamId = team.getId();
            var from = update.getMessage().getFrom();
            String telegramUserIdStr = from != null ? String.valueOf(from.getId()) : null;
            if (telegramUserIdStr != null && !teamMemberService.canUseBot(teamId, telegramUserIdStr)) {
                sendMessage(chatId, "Вы деактивированы. Обратитесь к администратору команды.");
                return;
            }
            if (from != null && from.getUserName() != null && telegramUserIdStr != null) {
                teamMemberService.ensureTelegramUsername(teamId, telegramUserIdStr, from.getUserName());
            }

            if ("/schedule".equals(text) || BTN_SCHEDULE.equals(text)) {
                sendSchedule(chatId, teamId);
                return;
            }
            if (BTN_PAST_MATCHES.equals(text)) {
                sendPastMatches(chatId, teamId);
                return;
            }
            if ("/roster".equals(text) || BTN_ROSTER.equals(text)) {
                sendRoster(chatId, teamId);
                return;
                    }
            if ("/leave".equals(text) || BTN_LEAVE.equals(text)) {
                pendingLeaveTeamId.put(chatId, teamId);
                sendMessage(chatId, "Выйти из команды? Напиши ДА для подтверждения.");
                return;
            }
            if ("/invite".equals(text) || BTN_INVITE.equals(text)) {
                handleInvite(chatId, teamId, telegramUserIdStr);
                return;
            }
            if ("/poll".equals(text) || BTN_POLL.equals(text)) {
                pendingPollQuestion.put(chatId, "");
                sendMessage(chatId, "Напиши вопрос для опроса одним сообщением.\nНапример: Кто едет на игру?");
                return;
            }
            if (text.startsWith("/poll ")) {
                String question = text.substring("/poll ".length()).trim();
                if (question.length() > 255) question = question.substring(0, 255);
                if (!question.isBlank()) {
                    pendingPollQuestion.put(chatId, question);
                    sendMessage(chatId, "Напиши варианты ответа через запятую. Например: Еду, Не еду, Опоздаю");
                } else {
                    pendingPollQuestion.put(chatId, "");
                    sendMessage(chatId, "Напиши вопрос для опроса одним сообщением.\nНапример: Кто едет на игру?");
                }
                return;
            }

            sendMessageWithReplyKeyboard(chatId, "Используй кнопки меню или команды: Расписание, Состав, Мой профиль, Выйти из команды, Опрос на игру, Приглашение.", mainMenu());
        } catch (Exception e) {
            sendMessage(chatId, "Ошибка: " + e.getMessage());
        }
    }

    private void handleStart(long chatId, String telegramUserIdStr, String startPayload, String telegramUsername,
                            String firstName, String lastName) {
        // Сначала обрабатываем код приглашения: иначе в личном чате, уже привязанном к команде, код игнорируется
        if (!startPayload.isEmpty()) {
            handleStartWithInviteCode(chatId, telegramUserIdStr, startPayload, telegramUsername, firstName, lastName);
            return;
        }
        Optional<Team> teamOpt = resolveTeam(chatId, telegramUserIdStr);
        if (teamOpt.isPresent()) {
            Team team = teamOpt.get();
            if (telegramUserIdStr != null && !teamMemberService.canUseBot(team.getId(), telegramUserIdStr)) {
                sendMessage(chatId, "Вы деактивированы. Обратитесь к администратору команды.");
                return;
            }
            if (telegramUsername != null && !telegramUsername.isBlank()) {
                teamMemberService.ensureTelegramUsername(team.getId(), telegramUserIdStr, telegramUsername);
            }
            String welcome = "Привет! Команда: «" + team.getName() + "». Расписание (будущие игры), прошедшие игры, состав, имя (Мой профиль), выход из команды, опрос — кнопки ниже.";
            if (chatId < 0) {
                welcome += "\n\n(ID этого чата для админки → Настройки: " + chatId + ")";
            }
            sendMessageWithReplyKeyboard(chatId, welcome, mainMenu());
        } else {
            if (!systemSettingsService.canCreateTeamWithoutInvite(telegramUserIdStr, telegramUsername)) {
                sendMessage(chatId, "Создать команду может только администратор. Попросите ссылку-приглашение у менеджера команды.");
                return;
            }
            pendingTeamName.put(chatId, true);
            sendMessage(chatId, "Привет! Отправь название команды одним сообщением.\nНапример: БК Метеор");
        }
    }

    private static String buildDisplayNameFromTelegram(String firstName, String lastName) {
        String first = firstName != null ? firstName.trim() : "";
        String last = lastName != null ? lastName.trim() : "";
        String combined = (first + " " + last).trim();
        return combined.isEmpty() ? null : combined;
    }

    /** Обработка /start CODE (приглашение). Вызывается из handleStart при непустом payload. */
    private void handleStartWithInviteCode(long chatId, String telegramUserIdStr, String code, String telegramUsername,
                                           String firstName, String lastName) {
        log.info("Invite: code={}, telegramUserId={}, username={}", code, telegramUserIdStr, telegramUsername);
        String displayName = buildDisplayNameFromTelegram(firstName, lastName);
        Optional<InvitationService.InvitationUseResult> result = invitationService.use(code, telegramUserIdStr, telegramUsername, displayName);
        if (result.isPresent()) {
            InvitationService.InvitationUseResult r = result.get();
            log.info("Invite success: user {} added to team {} as {}", telegramUserIdStr, r.teamName(), r.role());
            sendMessage(chatId, "Вы добавлены в команду «" + r.teamName() + "». Роль: " + r.role() + ".\nДальше: откройте чат команды в Telegram и напишите там /start, чтобы видеть расписание и состав.");
        } else {
            log.warn("Invite failed: code={}, telegramUserId={} (code not found or expired)", code, telegramUserIdStr);
            sendMessage(chatId, "Приглашение недействительно или уже использовано.");
        }
    }

    /** Создание приглашения в боте: только админ. Отправляет ссылку и QR в чат. */
    private void handleInvite(long chatId, Long teamId, String telegramUserIdStr) {
        if (teamMemberService.getRole(teamId, telegramUserIdStr).orElse(TeamMember.Role.PLAYER) != TeamMember.Role.ADMIN) {
            sendMessage(chatId, "Создавать приглашения может только администратор команды.");
            return;
        }
        try {
            Invitation inv = invitationService.create(teamId, TeamMember.Role.PLAYER, 7);
            String link = invitationService.buildInviteLink(inv.getCode());
            sendMessage(chatId, "Приглашение создано (роль: Игрок, срок: 7 дней).\nСсылка:\n" + link + "\n\nПерешлите ссылку или QR ниже новым участникам.");
            byte[] png = qrCodeService.generatePng(link, 256);
            SendPhoto photo = SendPhoto.builder()
                    .chatId(String.valueOf(chatId))
                    .photo(new InputFile(new ByteArrayInputStream(png), "invite-qr.png"))
                    .caption("QR-код приглашения в команду")
                    .build();
            telegramClient.execute(photo);
            integrationMetricsService.record(IntegrationEvent.EventType.INVITE_QR, String.valueOf(chatId), true, null, teamId, null);
        } catch (Exception e) {
            log.warn("Failed to create invite or send QR", e);
            integrationMetricsService.record(IntegrationEvent.EventType.INVITE_QR, String.valueOf(chatId), false, e.getMessage(), teamId, null);
            sendMessage(chatId, "Ошибка при создании приглашения: " + e.getMessage());
        }
    }

    /** Текст блока «Мой профиль»: имя, номер, долг (как в админке, раздел Участники). */
    private String buildProfileText(Long teamId, String telegramUserIdStr) {
        Optional<TeamMember> memberOpt = teamMemberService.findByTeamId(teamId).stream()
                .filter(m -> telegramUserIdStr != null && telegramUserIdStr.equals(m.getTelegramUserId()))
                .findFirst();
        String name = memberOpt
                .map(TeamMember::getDisplayName)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .orElse(null);
        Optional<Player> playerOpt = playerService.findByTeamIdAndTelegramId(teamId, telegramUserIdStr);
        if (name == null && playerOpt.isPresent()) {
            String n = playerOpt.get().getName();
            name = (n != null && !n.isBlank()) ? n.trim() : "—";
        }
        if (name == null) name = "—";
        String numberStr = playerOpt.map(Player::getNumber).map(String::valueOf).orElse("—");
        BigDecimal debt = playerOpt.map(Player::getDebt).orElse(BigDecimal.ZERO);
        String debtStr = (debt == null || debt.compareTo(BigDecimal.ZERO) <= 0)
                ? "нет"
                : debt.stripTrailingZeros().toPlainString() + " ₽";
        return "Мой профиль:\nИмя: " + name + "\nНомер: " + numberStr + "\nДолг: " + debtStr;
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
        String createdMsg = "Команда «" + team.getName() + "» создана. Участники видят расписание, состав, могут посмотреть свой профиль (кнопка «Мой профиль») и выйти из команды. Редактирование — в админке.";
        if (chatId < 0) {
            createdMsg += " Опросы будут уходить в этот чат (ID: " + chatId + ").";
        }
        sendMessageWithReplyKeyboard(chatId, createdMsg, mainMenu());
    }

    private void handleCallbackQuery(CallbackQuery callbackQuery) {
        String data = callbackQuery.getData();
        if (data != null && data.startsWith("attend:")) {
            String[] parts = data.split(":");
            if (parts.length == 3) {
                try {
                    long matchId = Long.parseLong(parts[1]);
                    EventAttendance.Status status = EventAttendance.Status.valueOf(parts[2]);
                    String telegramUserId = callbackQuery.getFrom() != null ? String.valueOf(callbackQuery.getFrom().getId()) : null;
                    if (telegramUserId != null) {
                        eventAttendanceService.setAttendance(matchId, telegramUserId, status);
                        String label = status == EventAttendance.Status.COMING ? "Буду" : status == EventAttendance.Status.LATE ? "Опоздаю" : "Не смогу";
                        answerCallback(callbackQuery.getId(), "Вы выбрали: " + label, false);
                        return;
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
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

    /** Команда: по chatId (чат команды или личка), при личке — по участию пользователя. */
    private Optional<Team> resolveTeam(long chatId, String telegramUserIdStr) {
        Optional<Team> byChat = teamService.findByTelegramChatId(String.valueOf(chatId));
        if (byChat.isPresent()) return byChat;
        // Личный чат (chatId > 0): команда могла быть создана в группе — ищем по участию
        if (chatId > 0 && telegramUserIdStr != null && !telegramUserIdStr.isBlank()) {
            return teamMemberService.findFirstTeamByTelegramUserId(telegramUserIdStr);
        }
        return Optional.empty();
    }

    /** Парсит варианты ответа: через запятую или с новой строки, до 10 вариантов, каждый до 100 символов. */
    private static List<String> parsePollOptions(String text) {
        if (text == null || text.isBlank()) return List.of();
        return Arrays.stream(text.split("[,;\n]+"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .limit(10)
                .map(s -> s.length() > 100 ? s.substring(0, 100) : s)
                .toList();
    }

    /** Отправляет опрос в чат. chatId — строка (личный или группа, например "-1001234567890"). */
    private void sendPoll(String chatId, String question, List<String> optionStrings) {
        String q = (question != null && !question.isBlank()) ? question : "Кто едет на игру?";
        if (q.length() > 255) q = q.substring(0, 255);
        List<InputPollOption> options = optionStrings.stream()
                .map(InputPollOption::new)
                .toList();
        if (options.size() < 2) {
            options = POLL_OPTIONS;
        }
        SendPoll poll = SendPoll.builder()
                .chatId(chatId)
                .question(q)
                .options(options)
                .isAnonymous(false)
                .build();
        try {
            telegramClient.execute(poll);
            integrationMetricsService.record(IntegrationEvent.EventType.POLL, chatId, true, null, null, null);
        } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
            integrationMetricsService.record(IntegrationEvent.EventType.POLL, chatId, false, e.getMessage(), null, null);
            throw new RuntimeException(e);
        }
    }

    private ReplyKeyboardMarkup mainMenu() {
        List<KeyboardRow> rows = new ArrayList<>();
        rows.add(new KeyboardRow(BTN_SCHEDULE, BTN_PAST_MATCHES));
        rows.add(new KeyboardRow(BTN_ROSTER));
        rows.add(new KeyboardRow(BTN_PROFILE, BTN_LEAVE, BTN_POLL));
        rows.add(new KeyboardRow(BTN_INVITE));
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup(rows);
        markup.setResizeKeyboard(true);
        return markup;
    }

    private void sendSchedule(long chatId, Long teamId) {
        List<Match> matches = matchService.findFutureByTeamId(teamId);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());
        if (matches.isEmpty()) {
            sendMessage(chatId, "Ближайших игр нет");
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

    private void sendPastMatches(long chatId, Long teamId) {
        List<Match> matches = matchService.findPastByTeamId(teamId);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());
        if (matches.isEmpty()) {
            sendMessage(chatId, "Прошедших игр пока нет.");
            return;
        }
        StringBuilder sb = new StringBuilder("Прошедшие игры:\n\n");
        for (Match m : matches) {
            String dateStr = fmt.format(m.getDate());
            String status = m.getStatus() == Match.Status.COMPLETED ? "✅" : (m.getStatus() == Match.Status.CANCELLED ? "❌" : "⏳");
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

    /** Состав: по одному игроку на активного участника (TeamMember), данные из Player при наличии. */
    private void sendRoster(long chatId, Long teamId) {
        List<TeamMember> members = teamMemberService.findByTeamId(teamId).stream()
                .filter(TeamMember::isActive)
                .toList();
        if (members.isEmpty()) {
            sendMessage(chatId, "Состав пуст. Участников добавляют через приглашения и админку.");
            return;
        }
        StringBuilder sb = new StringBuilder("Состав команды:\n\n");
        for (TeamMember m : members) {
            Optional<Player> playerOpt = playerService.findByTeamIdAndTelegramId(teamId, m.getTelegramUserId());
            String name = null;
            Integer number = null;
            Player.PlayerStatus status = Player.PlayerStatus.ACTIVE;
            boolean active = true;
            if (playerOpt.isPresent()) {
                Player p = playerOpt.get();
                if (!p.isActive()) continue;
                name = p.getName();
                number = p.getNumber();
                status = p.getPlayerStatus();
            }
            if (name == null || name.isBlank()) {
                name = m.getDisplayName() != null && !m.getDisplayName().isBlank() ? m.getDisplayName().trim() : "—";
            }
            sb.append("• ").append(name);
            if (number != null) sb.append(" №").append(number);
            sb.append(" — ").append(STATUS_LABEL.getOrDefault(status, status.name())).append("\n");
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
            integrationMetricsService.record(IntegrationEvent.EventType.BOT_MESSAGE, String.valueOf(chatId), true, null, null, null);
        } catch (Exception e) {
            integrationMetricsService.record(IntegrationEvent.EventType.BOT_MESSAGE, String.valueOf(chatId), false, e.getMessage(), null, null);
            throw new RuntimeException("Не удалось отправить сообщение в Telegram", e);
        }
    }

}
