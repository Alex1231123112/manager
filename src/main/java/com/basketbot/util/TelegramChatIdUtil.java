package com.basketbot.util;

/**
 * Утилита для работы с ID чатов Telegram.
 * Для супергрупп API ожидает формат -100xxxxxxxxxx; если в настройках указано число без минуса (3841841896), нужен префикс -100.
 */
public final class TelegramChatIdUtil {

    private TelegramChatIdUtil() {
    }

    /**
     * Нормализует ID группового чата для Telegram API.
     * Если передано число из 9–15 цифр без минуса (например 3841841896), возвращает "-100" + число.
     */
    public static String normalizeGroupChatId(String raw) {
        if (raw == null || raw.isBlank()) return raw;
        String s = raw.trim();
        if (s.startsWith("-")) return s;
        if (s.matches("\\d{9,15}")) return "-100" + s;
        return s;
    }
}
