package com.basketbot.service;

import com.basketbot.model.Match;
import com.basketbot.repository.MatchRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.polls.SendPoll;
import org.telegram.telegrambots.meta.api.objects.polls.input.InputPollOption;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Stream;

/**
 * Напоминания о матчах: за 24 ч — опрос, за 3 ч — напоминание, после матча — запрос результата.
 */
@Component
@ConditionalOnProperty(name = "telegram.bot.token")
public class MatchReminderScheduler {

    private static final List<InputPollOption> POLL_OPTIONS = Stream.of("Еду", "Не еду", "Опоздаю")
            .map(InputPollOption::new)
            .toList();
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("dd.MM HH:mm").withZone(ZoneId.systemDefault());

    private final MatchRepository matchRepository;
    private final TelegramClient telegramClient;

    public MatchReminderScheduler(MatchRepository matchRepository, TelegramClient telegramClient) {
        this.matchRepository = matchRepository;
        this.telegramClient = telegramClient;
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

    private void send24hReminder(Match match) {
        String chatId = match.getTeam().getTelegramChatId();
        if (chatId == null || chatId.isBlank()) return;
        String timeStr = TIME_FMT.format(match.getDate());
        String question = "Через сутки матч с «" + match.getOpponent() + "» (" + timeStr + "). Кто едет?";
        if (question.length() > 255) question = question.substring(0, 255);
        try {
            SendPoll poll = SendPoll.builder()
                    .chatId(chatId)
                    .question(question)
                    .options(POLL_OPTIONS)
                    .build();
            telegramClient.execute(poll);
        } catch (Exception ignored) {
            // логируем при необходимости
        }
    }

    private void send3hReminder(Match match) {
        String chatId = match.getTeam().getTelegramChatId();
        if (chatId == null || chatId.isBlank()) return;
        String timeStr = TIME_FMT.format(match.getDate());
        String text = "⏰ Через ~3 часа матч с «" + match.getOpponent() + "» (" + timeStr + "). Удачи!";
        try {
            telegramClient.execute(SendMessage.builder().chatId(chatId).text(text).build());
        } catch (Exception ignored) {
        }
    }

    private void sendAfterMatchReminder(Match match) {
        String chatId = match.getTeam().getTelegramChatId();
        if (chatId == null || chatId.isBlank()) return;
        String text = "Матч с «" + match.getOpponent() + "» прошёл. Введите результат и статистику: /result";
        try {
            telegramClient.execute(SendMessage.builder().chatId(chatId).text(text).build());
        } catch (Exception ignored) {
        }
    }
}
