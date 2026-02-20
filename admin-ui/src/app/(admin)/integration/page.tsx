"use client";

import { useEffect, useState } from "react";
import { apiGet } from "@/lib/api";
import { getUserFacingError } from "@/lib/errors";
import type { IntegrationStatsDto, IntegrationEventDto } from "@/lib/types";

const EVENT_TYPE_LABELS: Record<string, string> = {
  BOT_MESSAGE: "Ответ бота",
  REMINDER_24H: "Напоминание за 24 ч",
  REMINDER_3H: "Напоминание за 3 ч",
  REMINDER_STATS: "Статистика подтверждений",
  REMINDER_AFTER_MATCH: "После матча (результат)",
  DEBT_REMINDER: "Напоминание о долгах",
  INVITE_QR: "QR приглашения",
  POLL: "Опрос",
  CHANNEL_POST: "Публикация в канал",
};

export default function IntegrationPage() {
  const [stats, setStats] = useState<IntegrationStatsDto | null>(null);
  const [events, setEvents] = useState<IntegrationEventDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [periodDays, setPeriodDays] = useState(7);

  function load() {
    setLoadError(null);
    setLoading(true);
    const to = new Date();
    const from = new Date();
    from.setDate(from.getDate() - periodDays);
    const fromStr = from.toISOString();
    const toStr = to.toISOString();
    Promise.all([
      apiGet<IntegrationStatsDto>(`/api/admin/integration/stats?from=${encodeURIComponent(fromStr)}&to=${encodeURIComponent(toStr)}`),
      apiGet<IntegrationEventDto[]>("/api/admin/integration/events?limit=100"),
    ]).then(([statsRes, eventsRes]) => {
      setLoading(false);
      if (statsRes.ok && statsRes.data) setStats(statsRes.data);
      else setLoadError(getUserFacingError(statsRes.status));
      if (eventsRes.ok && Array.isArray(eventsRes.data)) setEvents(eventsRes.data);
    });
  }

  useEffect(() => {
    load();
  }, [periodDays]);

  if (loading && !stats) {
    return (
      <div>
        <h1 className="mb-6 text-2xl font-semibold text-zinc-800">Интеграция с Telegram</h1>
        <p className="text-zinc-500">Загрузка метрик…</p>
      </div>
    );
  }

  if (loadError) {
    return (
      <div>
        <h1 className="mb-6 text-2xl font-semibold text-zinc-800">Интеграция с Telegram</h1>
        <p className="mb-4 rounded-lg bg-amber-100 px-3 py-2 text-zinc-800">{loadError}</p>
        <button
          type="button"
          onClick={() => load()}
          className="rounded-lg bg-blue-600 px-4 py-2 text-white hover:bg-blue-700"
        >
          Повторить
        </button>
      </div>
    );
  }

  return (
    <div>
      <div className="mb-6 flex flex-wrap items-center justify-between gap-4">
        <h1 className="text-2xl font-semibold text-zinc-800">Интеграция с Telegram</h1>
        <div className="flex items-center gap-2">
          <label className="text-sm text-zinc-600">Период:</label>
          <select
            value={periodDays}
            onChange={(e) => setPeriodDays(Number(e.target.value))}
            className="rounded-lg border border-zinc-300 px-3 py-2 text-sm"
          >
            <option value={1}>1 день</option>
            <option value={7}>7 дней</option>
            <option value={14}>14 дней</option>
            <option value={30}>30 дней</option>
          </select>
          <button
            type="button"
            onClick={() => load()}
            className="rounded-lg border border-zinc-300 px-3 py-2 text-sm hover:bg-zinc-50"
          >
            Обновить
          </button>
        </div>
      </div>

      <p className="mb-6 text-sm text-zinc-600">
        Отслеживание отправки сообщений в Telegram: напоминания о матчах, ответы бота, приглашения, опросы. Успешная доставка и ошибки фиксируются для анализа.
      </p>

      {stats && (
        <>
          <div className="mb-8 grid grid-cols-2 gap-4 sm:grid-cols-4">
            <div className="rounded-xl border border-zinc-200 bg-white p-4 shadow-sm">
              <div className="text-sm font-medium text-zinc-500">Всего отправок</div>
              <div className="mt-1 text-2xl font-semibold text-zinc-800">{stats.total}</div>
            </div>
            <div className="rounded-xl border border-zinc-200 bg-white p-4 shadow-sm">
              <div className="text-sm font-medium text-zinc-500">Доставлено</div>
              <div className="mt-1 text-2xl font-semibold text-green-600">{stats.success}</div>
            </div>
            <div className="rounded-xl border border-zinc-200 bg-white p-4 shadow-sm">
              <div className="text-sm font-medium text-zinc-500">Ошибки</div>
              <div className="mt-1 text-2xl font-semibold text-red-600">{stats.failed}</div>
            </div>
            <div className="rounded-xl border border-zinc-200 bg-white p-4 shadow-sm">
              <div className="text-sm font-medium text-zinc-500">% доставки</div>
              <div className="mt-1 text-2xl font-semibold text-zinc-800">
                {stats.total > 0 ? Math.round((stats.success / stats.total) * 100) : 0}%
              </div>
            </div>
          </div>

          {stats.byType && stats.byType.length > 0 && (
            <div className="mb-8 overflow-hidden rounded-xl border border-zinc-200 bg-white shadow-sm">
              <h2 className="border-b border-zinc-100 px-4 py-3 text-lg font-medium text-zinc-800">По типу события</h2>
              <table className="w-full">
                <thead className="bg-zinc-50">
                  <tr>
                    <th className="px-4 py-2 text-left text-sm font-medium text-zinc-600">Тип</th>
                    <th className="px-4 py-2 text-right text-sm font-medium text-zinc-600">Доставлено</th>
                    <th className="px-4 py-2 text-right text-sm font-medium text-zinc-600">Ошибки</th>
                  </tr>
                </thead>
                <tbody>
                  {stats.byType.map((row) => (
                    <tr key={row.eventType} className="border-t border-zinc-100">
                      <td className="px-4 py-2">{row.label ?? row.eventType}</td>
                      <td className="px-4 py-2 text-right text-green-600">{row.success}</td>
                      <td className="px-4 py-2 text-right text-red-600">{row.failed}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}

      <div className="overflow-hidden rounded-xl border border-zinc-200 bg-white shadow-sm">
        <h2 className="border-b border-zinc-100 px-4 py-3 text-lg font-medium text-zinc-800">Последние события (лог)</h2>
        {events.length === 0 ? (
          <p className="px-4 py-6 text-zinc-500">Пока нет записей</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-zinc-50">
                <tr>
                  <th className="px-4 py-2 text-left font-medium text-zinc-600">Время</th>
                  <th className="px-4 py-2 text-left font-medium text-zinc-600">Тип</th>
                  <th className="px-4 py-2 text-left font-medium text-zinc-600">Чат</th>
                  <th className="px-4 py-2 text-center font-medium text-zinc-600">Статус</th>
                  <th className="px-4 py-2 text-left font-medium text-zinc-600">Ошибка</th>
                </tr>
              </thead>
              <tbody>
                {events.map((e) => (
                  <tr
                    key={e.id}
                    className={`border-t border-zinc-100 ${!e.success ? "bg-red-50/50" : ""}`}
                  >
                    <td className="whitespace-nowrap px-4 py-2 text-zinc-600">
                      {e.createdAt ? new Date(e.createdAt).toLocaleString("ru-RU") : "—"}
                    </td>
                    <td className="px-4 py-2">{EVENT_TYPE_LABELS[e.eventType] ?? e.eventType}</td>
                    <td className="max-w-[120px] truncate px-4 py-2 font-mono text-zinc-500">{e.targetChatId ?? "—"}</td>
                    <td className="px-4 py-2 text-center">
                      {e.success ? (
                        <span className="text-green-600">✓</span>
                      ) : (
                        <span className="text-red-600" title={e.errorMessage ?? ""}>✗</span>
                      )}
                    </td>
                    <td className="max-w-[280px] truncate px-4 py-2 text-red-600" title={e.errorMessage ?? ""}>
                      {e.success ? "—" : (e.errorMessage ?? "Ошибка")}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
