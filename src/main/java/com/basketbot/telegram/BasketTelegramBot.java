package com.basketbot.telegram;

import com.basketbot.model.Match;
import com.basketbot.model.Player;
import com.basketbot.model.Team;
import com.basketbot.model.Invitation;
import com.basketbot.model.TeamMember;
import com.basketbot.service.InvitationService;
import com.basketbot.service.MatchImageService;
import com.basketbot.service.MatchPostService;
import com.basketbot.service.QrCodeService;
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
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.polls.SendPoll;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.polls.input.InputPollOption;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.ByteArrayInputStream;
import java.time.Instant;
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

    private static final String BTN_ROSTER = "Состав";
    private static final String BTN_ADD_PLAYER = "Добавить игрока";
    private static final String BTN_NEW_MATCH = "Новый матч";
    private static final String BTN_RESULT = "Ввести результат";
    private static final String BTN_POLL = "Опрос на игру";
    private static final String BTN_DEBT = "Долги";
    private static final String BTN_COMMANDS = "Команды";
    private static final String BTN_INVITE = "Приглашение";
    private static final List<String> POLL_OPTION_STRINGS = List.of("Еду", "Не еду", "Опоздаю");
    private static final List<InputPollOption> POLL_OPTIONS = POLL_OPTION_STRINGS.stream()
            .map(InputPollOption::new)
            .toList();

    private final TelegramBotProperties properties;
    private final TelegramClient telegramClient;
    private final TeamService teamService;
    private final TeamMemberService teamMemberService;
    private final PlayerService playerService;
    private final MatchService matchService;
    private final MatchPostService matchPostService;
    private final MatchImageService matchImageService;
    private final SystemSettingsService systemSettingsService;
    private final InvitationService invitationService;
    private final QrCodeService qrCodeService;

    /** Ожидание названия команды после /start (chatId -> true) */
    private final Map<Long, Boolean> pendingTeamName = new ConcurrentHashMap<>();

    public BasketTelegramBot(TelegramBotProperties properties,
                            org.telegram.telegrambots.meta.generics.TelegramClient telegramClient,
                            TeamService teamService,
                            TeamMemberService teamMemberService,
                            PlayerService playerService,
                            MatchService matchService,
                            MatchPostService matchPostService,
                            MatchImageService matchImageService,
                            SystemSettingsService systemSettingsService,
                            InvitationService invitationService,
                            QrCodeService qrCodeService) {
        this.properties = properties;
        this.telegramClient = telegramClient;
        this.teamService = teamService;
        this.teamMemberService = teamMemberService;
        this.playerService = playerService;
        this.matchService = matchService;
        this.matchPostService = matchPostService;
        this.matchImageService = matchImageService;
        this.systemSettingsService = systemSettingsService;
        this.invitationService = invitationService;
        this.qrCodeService = qrCodeService;
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
                new BotCommand("start", "Создать команду или главное меню"),
                new BotCommand("roster", "Состав (или: roster активные|травма|отпуск|не оплатил)"),
                new BotCommand("addplayer", "Добавить игрока: addplayer Имя Номер"),
                new BotCommand("newmatch", "Новый матч: newmatch Соперник"),
                new BotCommand("result", "Ввести результат: result наши их"),
                new BotCommand("poll", "Опрос на игру: poll Текст вопроса"),
                new BotCommand("debt", "Список долгов"),
                new BotCommand("setdebt", "Выставить долг: setdebt Имя Сумма"),
                new BotCommand("paid", "Отметить оплату: paid Имя"),
                new BotCommand("setstatus", "Статус игрока: setstatus Имя статус"),
                new BotCommand("setchannel", "Канал для постов: setchannel ID_канала"),
                new BotCommand("setrole", "Роль участника: setrole TelegramID ADMIN|CAPTAIN|PLAYER (только админ)"),
                new BotCommand("invite", "Создать приглашение в команду (капитан и выше)")
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

            Optional<Team> teamOpt = teamService.findByTelegramChatId(String.valueOf(chatId));
            if (teamOpt.isEmpty()) {
                sendMessage(chatId, "Сначала создай команду: отправь /start и затем название команды.");
                return;
            }
            Team team = teamOpt.get();
            Long teamId = team.getId();
            var from = update.getMessage().getFrom();
            long telegramUserId = from != null ? from.getId() : 0;
            String telegramUserIdStr = String.valueOf(telegramUserId);
            if (from != null && from.getUserName() != null) {
                teamMemberService.ensureTelegramUsername(teamId, telegramUserIdStr, from.getUserName());
            }

            if ("/addplayer".equals(text) || BTN_ADD_PLAYER.equals(text)) {
                sendMessage(chatId, "Добавление игрока. Напиши: Имя Номер\nНапример: Иван Петров 23");
                return;
            }
            if ("/roster".equals(text) || BTN_ROSTER.equals(text)) {
                sendRoster(chatId, teamId, null);
                return;
            }
            if (text.startsWith("/roster ")) {
                sendRoster(chatId, teamId, text.substring("/roster ".length()).trim());
                return;
            }
            if ("/newmatch".equals(text) || BTN_NEW_MATCH.equals(text)) {
                sendMessage(chatId, "Создание матча. Напиши: /newmatch Соперник\nНапример: /newmatch ЦСКА");
                return;
            }
            if ("/result".equals(text) || BTN_RESULT.equals(text)) {
                sendResultHint(chatId, teamId);
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
            if ("/debt".equals(text) || BTN_DEBT.equals(text)) {
                sendDebtList(chatId, teamId);
                return;
            }
            if ("/commands".equals(text) || BTN_COMMANDS.equals(text)) {
                sendCommandsMenu(chatId, teamId, telegramUserIdStr);
                return;
            }
            if ("/invite".equals(text) || text.startsWith("/invite ") || BTN_INVITE.equals(text)) {
                if (!requireCaptain(chatId, teamId, telegramUserIdStr)) return;
                createInvite(chatId, teamId, text.startsWith("/invite ") ? text.substring(8).trim() : null);
                return;
            }
            if (text.startsWith("/setrole ")) {
                setRole(chatId, teamId, telegramUserIdStr, text.substring("/setrole ".length()).trim());
                return;
            }
            if (text.startsWith("/setdebt ")) {
                if (!requireCaptain(chatId, teamId, telegramUserIdStr)) return;
                setDebt(chatId, teamId, text.substring("/setdebt ".length()).trim());
                return;
            }
            if (text.startsWith("/paid ")) {
                if (!requireCaptain(chatId, teamId, telegramUserIdStr)) return;
                markPaid(chatId, teamId, text.substring("/paid ".length()).trim());
                return;
            }
            if (text.startsWith("/setchannel ")) {
                if (!requireCaptain(chatId, teamId, telegramUserIdStr)) return;
                setChannel(chatId, teamId, text.substring("/setchannel ".length()).trim());
                return;
            }

            if (text.startsWith("/addplayer ")) {
                if (!requireCaptain(chatId, teamId, telegramUserIdStr)) return;
                addPlayer(chatId, teamId, text.substring("/addplayer ".length()).trim());
                return;
            }
            if (text.startsWith("/newmatch ")) {
                if (!requireCaptain(chatId, teamId, telegramUserIdStr)) return;
                newMatch(chatId, teamId, text.substring("/newmatch ".length()).trim());
                return;
            }
            if (text.startsWith("/result ")) {
                if (!requireCaptain(chatId, teamId, telegramUserIdStr)) return;
                setResult(chatId, teamId, text.substring("/result ".length()).trim());
                return;
            }
            if (text.startsWith("/setstatus ")) {
                if (!requireCaptain(chatId, teamId, telegramUserIdStr)) return;
                setPlayerStatus(chatId, teamId, text.substring("/setstatus ".length()).trim());
                return;
            }

            sendMessage(chatId, "Неизвестная команда. Используй кнопки меню или /start.");
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
            sendMessageWithReplyKeyboard(chatId, "Привет! Команда: «" + team.getName() + "». Что делаем?", mainMenu(team.getId(), telegramUserIdStr));
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
        sendMessageWithReplyKeyboard(chatId, "Команда «" + team.getName() + "» создана. Добавляй игроков и матчи.", mainMenu(team.getId(), String.valueOf(creatorTelegramUserId)));
    }

    private void createInvite(long chatId, Long teamId, String roleStr) {
        TeamMember.Role role = TeamMember.Role.PLAYER;
        if (roleStr != null && !roleStr.isBlank()) {
            try {
                role = TeamMember.Role.valueOf(roleStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                sendMessage(chatId, "Роль должна быть: PLAYER, CAPTAIN или ADMIN. Используй по умолчанию PLAYER.");
                return;
            }
        }
        Invitation inv = invitationService.create(teamId, role, 7);
        String link = invitationService.buildInviteLink(inv.getCode());
        byte[] png = qrCodeService.generatePng(link, 256);
        sendPhoto(chatId, png, "invite.png");
        sendMessage(chatId, "Ссылка для приглашения (действует 7 дней):\n" + link);
    }

    /** Проверка прав: минимум капитан. При отказе отправляет сообщение и возвращает false. */
    private boolean requireCaptain(long chatId, Long teamId, String telegramUserIdStr) {
        try {
            teamMemberService.requireAtLeast(teamId, telegramUserIdStr, TeamMember.Role.CAPTAIN);
            return true;
        } catch (SecurityException e) {
            sendMessage(chatId, e.getMessage());
            return false;
        }
    }

    private void setRole(long chatId, Long teamId, String callerTelegramUserId, String args) {
        String[] parts = args.trim().split("\\s+");
        if (parts.length < 2) {
            sendMessage(chatId, "Формат: /setrole TelegramID роль\nРоли: ADMIN, CAPTAIN, PLAYER\nПример: /setrole 123456789 CAPTAIN");
            return;
        }
        String targetIdStr = parts[0];
        TeamMember.Role role;
        try {
            role = TeamMember.Role.valueOf(parts[1].toUpperCase());
        } catch (IllegalArgumentException e) {
            sendMessage(chatId, "Роль должна быть: ADMIN, CAPTAIN или PLAYER.");
            return;
        }
        try {
            teamMemberService.setRole(teamId, callerTelegramUserId, targetIdStr, role);
            sendMessage(chatId, "Роль назначена: " + targetIdStr + " — " + role);
        } catch (SecurityException e) {
            sendMessage(chatId, e.getMessage());
        } catch (IllegalArgumentException e) {
            sendMessage(chatId, e.getMessage());
        }
    }

    private void addPlayer(long chatId, Long teamId, String args) {
        String[] parts = args.split("\\s+");
        if (parts.length < 2) {
            sendMessage(chatId, "Формат: Имя Номер. Например: Иван Петров 23");
            return;
        }
        int number;
        try {
            number = Integer.parseInt(parts[parts.length - 1]);
        } catch (NumberFormatException e) {
            sendMessage(chatId, "Номер должен быть числом. Например: Иван Петров 23");
            return;
        }
        String name = String.join(" ", java.util.Arrays.copyOf(parts, parts.length - 1));
        playerService.addPlayer(teamId, name, number);
        sendMessage(chatId, "Игрок " + name + " №" + number + " добавлен.");
    }

    private static final Map<Player.PlayerStatus, String> STATUS_LABEL = Map.of(
            Player.PlayerStatus.ACTIVE, "активен",
            Player.PlayerStatus.INJURY, "травма",
            Player.PlayerStatus.VACATION, "отпуск",
            Player.PlayerStatus.NOT_PAID, "не оплатил"
    );

    private void sendRoster(long chatId, Long teamId, String filterArg) {
        Player.PlayerStatus filter = parseStatusFilter(filterArg);
        List<Player> players = filter != null
                ? playerService.findByTeamIdByStatus(teamId, filter)
                : playerService.findByTeamId(teamId);
        if (players.isEmpty()) {
            String hint = filter != null
                    ? "Нет игроков со статусом «" + STATUS_LABEL.get(filter) + "»."
                    : "Состав пуст. Добавь игроков: кнопка «Добавить игрока» или /addplayer Имя Номер.";
            sendMessage(chatId, hint);
            return;
        }
        StringBuilder sb = new StringBuilder(filter != null ? "Состав (" + STATUS_LABEL.get(filter) + "):\n" : "Состав:\n");
        for (Player p : players) {
            sb.append("• ").append(p.getName());
            if (p.getNumber() != null) sb.append(" №").append(p.getNumber());
            sb.append(" — ").append(STATUS_LABEL.get(p.getPlayerStatus())).append("\n");
        }
        sb.append("\nФильтр: кнопка «Команды» или /roster активные | травма | отпуск | не оплатил\nИзменить статус: /setstatus Имя статус");
        sendMessage(chatId, sb.toString());
    }

    private Player.PlayerStatus parseStatusFilter(String arg) {
        if (arg == null || arg.isBlank()) return null;
        String s = arg.trim().toLowerCase();
        return switch (s) {
            case "активен", "активные", "активный" -> Player.PlayerStatus.ACTIVE;
            case "травма" -> Player.PlayerStatus.INJURY;
            case "отпуск" -> Player.PlayerStatus.VACATION;
            case "не оплатил", "неоплатил" -> Player.PlayerStatus.NOT_PAID;
            default -> null;
        };
    }

    private void setPlayerStatus(long chatId, Long teamId, String args) {
        String[] parts = args.trim().split("\\s+");
        if (parts.length < 2) {
            sendMessage(chatId, "Формат: /setstatus Имя статус\nСтатусы: активен, травма, отпуск, не оплатил\nПример: /setstatus Иван Петров травма");
            return;
        }
        // Статус «не оплатил» из двух слов
        Player.PlayerStatus status = null;
        int nameEnd = parts.length;
        if (parts.length >= 2 && "не".equalsIgnoreCase(parts[parts.length - 2]) && "оплатил".equalsIgnoreCase(parts[parts.length - 1])) {
            status = Player.PlayerStatus.NOT_PAID;
            nameEnd = parts.length - 2;
        } else {
            status = parseStatusFilter(parts[parts.length - 1]);
            nameEnd = parts.length - 1;
        }
        if (status == null) {
            sendMessage(chatId, "Неизвестный статус. Укажи: активен, травма, отпуск или не оплатил.");
            return;
        }
        String name = String.join(" ", java.util.Arrays.copyOf(parts, nameEnd));
        if (name.isBlank()) {
            sendMessage(chatId, "Укажи имя игрока.");
            return;
        }
        try {
            playerService.setPlayerStatus(teamId, name, status);
            sendMessage(chatId, "Статус обновлён: " + name + " — " + STATUS_LABEL.get(status));
        } catch (IllegalArgumentException e) {
            sendMessage(chatId, e.getMessage());
        }
    }

    private void newMatch(long chatId, Long teamId, String opponent) {
        if (opponent.isBlank()) {
            sendMessage(chatId, "Укажи соперника: /newmatch Соперник");
            return;
        }
        Match match = matchService.createMatch(teamId, opponent, Instant.now(), null);
        String dateStr = DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.systemDefault()).format(match.getDate());
        sendMessage(chatId, "Матч создан: " + dateStr + " против " + match.getOpponent() + ".\nВвести счёт: /result наши их");
    }

    private void sendResultHint(long chatId, Long teamId) {
        Optional<Match> last = matchService.findLastScheduled(teamId);
        if (last.isEmpty()) {
            sendMessage(chatId, "Нет запланированных матчей. Создай матч: /newmatch Соперник");
            return;
        }
        Match m = last.get();
        sendMessage(chatId, "Последний матч: против " + m.getOpponent() + ".\nВведи счёт: /result наши их\nНапример: /result 85 82");
    }

    private void setResult(long chatId, Long teamId, String args) {
        String[] parts = args.trim().split("\\s+");
        if (parts.length != 2) {
            sendMessage(chatId, "Формат: /result наши очки очки соперника. Например: /result 85 82");
            return;
        }
        int ourScore;
        int opponentScore;
        try {
            ourScore = Integer.parseInt(parts[0]);
            opponentScore = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            sendMessage(chatId, "Оба значения должны быть числами. Например: /result 85 82");
            return;
        }
        Optional<Match> last = matchService.findLastScheduled(teamId);
        if (last.isEmpty()) {
            sendMessage(chatId, "Нет запланированного матча для ввода результата.");
            return;
        }
        Match match = matchService.setResult(last.get().getId(), teamId, ourScore, opponentScore);
        sendMessage(chatId, "Результат сохранён: " + match.getOurScore() + " : " + match.getOpponentScore() + " (против " + match.getOpponent() + ")");

        Team team = teamService.findById(teamId).orElseThrow();
        String postText = matchPostService.buildPostText(team, match);
        if (!postText.isEmpty()) {
            sendMessage(chatId, "Пост для соцсетей:\n\n" + postText);
        }

        byte[] imagePng = matchImageService.generateScoreCard(team, match);
        if (imagePng.length > 0) {
            sendPhoto(chatId, imagePng, "result.png");
            sendMessageWithPublishButton(chatId, match.getId());
        }
    }

    private void handleCallbackQuery(CallbackQuery callbackQuery) {
        String data = callbackQuery.getData();
        if (data == null || data.isEmpty()) return;

        long chatId = callbackQuery.getMessage().getChatId();
        String callbackId = callbackQuery.getId();

        if (data.startsWith("roster:")) {
            handleRosterCallback(chatId, callbackId, data.substring("roster:".length()));
            return;
        }
        if (data.startsWith("cmd:")) {
            handleCmdCallback(chatId, callbackId, data.substring("cmd:".length()));
            return;
        }
        if (data.startsWith("paid:")) {
            handlePaidCallback(chatId, callbackId, data.substring("paid:".length()), callbackQuery.getFrom() != null ? String.valueOf(callbackQuery.getFrom().getId()) : null);
            return;
        }
        if (!data.startsWith("publish:")) return;

        Optional<Team> teamOpt = teamService.findByTelegramChatId(String.valueOf(chatId));
        if (teamOpt.isEmpty()) {
            answerCallback(callbackId, "Команда не найдена.", true);
            return;
        }
        Team team = teamOpt.get();
        String callerUserIdStr = callbackQuery.getFrom() != null ? String.valueOf(callbackQuery.getFrom().getId()) : null;
        if (callerUserIdStr != null) {
            try {
                teamMemberService.requireAtLeast(team.getId(), callerUserIdStr, TeamMember.Role.CAPTAIN);
            } catch (SecurityException e) {
                answerCallback(callbackId, e.getMessage(), true);
                return;
            }
        }
        if (team.getChannelTelegramChatId() == null || team.getChannelTelegramChatId().isBlank()) {
            answerCallback(callbackId, "Сначала укажи канал: /setchannel ID_канала", true);
            return;
        }

        long matchId;
        try {
            matchId = Long.parseLong(data.substring("publish:".length()));
        } catch (NumberFormatException e) {
            answerCallback(callbackId, "Ошибка данных.", true);
            return;
        }

        Optional<Match> matchOpt = matchService.findByIdAndTeamId(matchId, team.getId());
        if (matchOpt.isEmpty()) {
            answerCallback(callbackId, "Матч не найден.", true);
            return;
        }
        Match match = matchOpt.get();
        byte[] imagePng = matchImageService.generateScoreCard(team, match);
        if (imagePng.length == 0) {
            answerCallback(callbackId, "Не удалось сгенерировать картинку.", true);
            return;
        }
        sendPhoto(Long.parseLong(team.getChannelTelegramChatId()), imagePng, "result.png");
        answerCallback(callbackId, "Опубликовано в канал.");
    }

    private void handleRosterCallback(long chatId, String callbackId, String statusKey) {
        Optional<Team> teamOpt = teamService.findByTelegramChatId(String.valueOf(chatId));
        if (teamOpt.isEmpty()) {
            answerCallback(callbackId, "Команда не найдена.", true);
            return;
        }
        answerCallback(callbackId, null);
        Player.PlayerStatus filter = switch (statusKey.toUpperCase()) {
            case "ACTIVE" -> Player.PlayerStatus.ACTIVE;
            case "INJURY" -> Player.PlayerStatus.INJURY;
            case "VACATION" -> Player.PlayerStatus.VACATION;
            case "NOT_PAID" -> Player.PlayerStatus.NOT_PAID;
            default -> null; // ALL
        };
        sendRoster(chatId, teamOpt.get().getId(), filter != null ? STATUS_LABEL.get(filter) : null);
    }

    private void handleCmdCallback(long chatId, String callbackId, String cmd) {
        answerCallback(callbackId, null);
        String text = switch (cmd) {
            case "setstatus" -> "Изменить статус игрока. Отправь:\n/setstatus Имя статус\nСтатусы: активен, травма, отпуск, не оплатил\nПример: /setstatus Иван Петров травма";
            case "setchannel" -> "Настроить канал для публикации. Отправь:\n/setchannel ID_канала\nID канала обычно вида -1001234567890. Добавь бота в канал как админа.";
            case "setdebt" -> "Выставить долг. Отправь:\n/setdebt Имя Сумма\nПример: /setdebt Иван Петров 500";
            default -> "Неизвестная команда. Нажми «Команды» для списка.";
        };
        sendMessage(chatId, text);
    }

    /** Список команд по роли: капитан и выше видят setstatus, setchannel, setdebt; игрок — только состав и фильтры. */
    private void sendCommandsMenu(long chatId, Long teamId, String telegramUserIdStr) {
        TeamMember.Role role = teamMemberService.getRole(teamId, telegramUserIdStr).orElse(TeamMember.Role.PLAYER);
        boolean captainOrAdmin = TeamMember.roleLevel(role) >= TeamMember.roleLevel(TeamMember.Role.CAPTAIN);
        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder().text("Состав (все)").callbackData("roster:ALL").build(),
                InlineKeyboardButton.builder().text("Активные").callbackData("roster:ACTIVE").build(),
                InlineKeyboardButton.builder().text("Травма").callbackData("roster:INJURY").build()
        ));
        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder().text("Отпуск").callbackData("roster:VACATION").build(),
                InlineKeyboardButton.builder().text("Не оплатил").callbackData("roster:NOT_PAID").build()
        ));
        if (captainOrAdmin) {
            rows.add(new InlineKeyboardRow(
                    InlineKeyboardButton.builder().text("Изменить статус").callbackData("cmd:setstatus").build(),
                    InlineKeyboardButton.builder().text("Настроить канал").callbackData("cmd:setchannel").build(),
                    InlineKeyboardButton.builder().text("Выставить долг").callbackData("cmd:setdebt").build()
            ));
        }
        InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder().keyboard(rows).build();
        sendMessage(chatId, "Выбери команду (доступно по твоей роли):", markup);
    }

    private void sendMessageWithPublishButton(long chatId, long matchId) {
        InlineKeyboardButton button = InlineKeyboardButton.builder()
                .text("Опубликовать в канал")
                .callbackData("publish:" + matchId)
                .build();
        InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder()
                .keyboard(List.of(new InlineKeyboardRow(button)))
                .build();
        sendMessage(chatId, "Карточка готова. Опубликовать в канал?", markup);
    }

    private void setChannel(long chatId, Long teamId, String channelId) {
        String id = channelId != null ? channelId.trim() : "";
        if (id.isEmpty()) {
            sendMessage(chatId, "Формат: /setchannel ID_канала\nID канала обычно вида -1001234567890. Добавь бота в канал как админа.");
            return;
        }
        teamService.setChannelChatId(teamId, id);
        sendMessage(chatId, "Канал для публикации задан. После ввода результата можно нажать «Опубликовать в канал».");
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

    private void answerCallback(String callbackQueryId, String text) {
        answerCallback(callbackQueryId, text, false);
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

    private void sendDebtList(long chatId, Long teamId) {
        List<Player> debtors = playerService.findWithDebt(teamId);
        if (debtors.isEmpty()) {
            sendMessage(chatId, "Долгов нет.");
            return;
        }
        StringBuilder sb = new StringBuilder("Долги:\n");
        for (Player p : debtors) {
            sb.append("• ").append(p.getName()).append(" — ").append(p.getDebt()).append(" ₽\n");
        }
        sb.append("\nОтметить оплату: /paid Имя или кнопка ниже.\nИзменить долг: /setdebt Имя Сумма");
        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (Player p : debtors) {
            rows.add(new InlineKeyboardRow(
                    InlineKeyboardButton.builder().text("Оплатил: " + p.getName()).callbackData("paid:" + p.getId()).build()
            ));
        }
        InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder().keyboard(rows).build();
        sendMessage(chatId, sb.toString(), markup);
    }

    private void handlePaidCallback(long chatId, String callbackId, String playerIdStr, String callerUserIdStr) {
        Optional<Team> teamOpt = teamService.findByTelegramChatId(String.valueOf(chatId));
        if (teamOpt.isEmpty()) {
            answerCallback(callbackId, "Команда не найдена.", true);
            return;
        }
        Team team = teamOpt.get();
        if (callerUserIdStr != null) {
            try {
                teamMemberService.requireAtLeast(team.getId(), callerUserIdStr, TeamMember.Role.CAPTAIN);
            } catch (SecurityException e) {
                answerCallback(callbackId, e.getMessage(), true);
                return;
            }
        }
        long playerId;
        try {
            playerId = Long.parseLong(playerIdStr);
        } catch (NumberFormatException e) {
            answerCallback(callbackId, "Ошибка данных.", true);
            return;
        }
        try {
            Player p = playerService.clearDebtByPlayerId(team.getId(), playerId);
            answerCallback(callbackId, "Долг снят: " + p.getName());
            sendDebtList(chatId, team.getId());
        } catch (IllegalArgumentException e) {
            answerCallback(callbackId, e.getMessage(), true);
        }
    }

    private void markPaid(long chatId, Long teamId, String name) {
        if (name == null || name.isBlank()) {
            sendMessage(chatId, "Формат: /paid Имя\nНапример: /paid Иван Петров");
            return;
        }
        try {
            Player p = playerService.clearDebt(teamId, name.trim());
            sendMessage(chatId, "Оплата отмечена: " + p.getName());
        } catch (IllegalArgumentException e) {
            sendMessage(chatId, e.getMessage());
        }
    }

    private void setDebt(long chatId, Long teamId, String args) {
        String[] parts = args.trim().split("\\s+");
        if (parts.length < 2) {
            sendMessage(chatId, "Формат: /setdebt Имя Сумма\nНапример: /setdebt Иван Петров 500");
            return;
        }
        java.math.BigDecimal amount;
        try {
            amount = new java.math.BigDecimal(parts[parts.length - 1].replace(",", "."));
        } catch (NumberFormatException e) {
            sendMessage(chatId, "Сумма должна быть числом. Например: /setdebt Иван 500");
            return;
        }
        String name = String.join(" ", java.util.Arrays.copyOf(parts, parts.length - 1));
        try {
            playerService.setDebt(teamId, name, amount);
            sendMessage(chatId, "Долг для " + name + ": " + amount + " ₽");
        } catch (IllegalArgumentException e) {
            sendMessage(chatId, e.getMessage());
        }
    }

    /** Меню кнопок по роли: капитан и выше видят добавление игрока, матчи, результат, приглашение; игрок — только состав, опрос, долги, команды. */
    private ReplyKeyboardMarkup mainMenu(Long teamId, String telegramUserIdStr) {
        TeamMember.Role role = teamMemberService.getRole(teamId, telegramUserIdStr).orElse(TeamMember.Role.PLAYER);
        boolean captainOrAdmin = TeamMember.roleLevel(role) >= TeamMember.roleLevel(TeamMember.Role.CAPTAIN);
        List<KeyboardRow> rows = new ArrayList<>();
        if (captainOrAdmin) {
            rows.add(new KeyboardRow(BTN_ROSTER, BTN_ADD_PLAYER));
            rows.add(new KeyboardRow(BTN_NEW_MATCH, BTN_RESULT));
        } else {
            rows.add(new KeyboardRow(BTN_ROSTER));
        }
        rows.add(new KeyboardRow(BTN_POLL, BTN_DEBT));
        if (captainOrAdmin) {
            rows.add(new KeyboardRow(BTN_COMMANDS, BTN_INVITE));
        } else {
            rows.add(new KeyboardRow(BTN_COMMANDS));
        }
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup(rows);
        markup.setResizeKeyboard(true);
        return markup;
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

    private void sendMessage(long chatId, String messageText, InlineKeyboardMarkup inlineMarkup) {
        try {
            SendMessage.SendMessageBuilder builder = SendMessage.builder()
                    .chatId(String.valueOf(chatId))
                    .text(messageText);
            if (inlineMarkup != null) {
                builder.replyMarkup(inlineMarkup);
            }
            telegramClient.execute(builder.build());
        } catch (Exception e) {
            throw new RuntimeException("Не удалось отправить сообщение в Telegram", e);
        }
    }

    private void sendPhoto(long chatId, byte[] imagePng, String fileName) {
        try {
            InputFile photo = new InputFile(new ByteArrayInputStream(imagePng), fileName);
            SendPhoto sendPhoto = SendPhoto.builder()
                    .chatId(String.valueOf(chatId))
                    .photo(photo)
                    .build();
            telegramClient.execute(sendPhoto);
        } catch (Exception e) {
            throw new RuntimeException("Не удалось отправить картинку в Telegram", e);
        }
    }
}
