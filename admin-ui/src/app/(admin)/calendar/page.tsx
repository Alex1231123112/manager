"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { apiGet, apiPost, apiDelete } from "@/lib/api";
import { getUserFacingError } from "@/lib/errors";
import type { MatchDto, EventDto, ActionResult } from "@/lib/types";

type CalendarItem = {
  type: "match" | "event";
  id: number;
  date: string;
  title: string;
  subtitle?: string;
  location?: string | null;
  match?: MatchDto;
  event?: EventDto;
};

function parseDate(s: string): number {
  return new Date(s).getTime();
}

export default function CalendarPage() {
  const [matches, setMatches] = useState<MatchDto[]>([]);
  const [events, setEvents] = useState<EventDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [message, setMessage] = useState<{ type: "ok" | "err"; text: string } | null>(null);
  const [form, setForm] = useState({ title: "", eventType: "TRAINING", eventDate: "", eventTime: "18:00", location: "", description: "" });

  function load() {
    setLoadError(null);
    setLoading(true);
    Promise.all([
      apiGet<MatchDto[]>("/api/admin/matches"),
      apiGet<EventDto[]>("/api/admin/events"),
    ]).then(([matchesRes, eventsRes]) => {
      setLoading(false);
      if (matchesRes.status === 0 || eventsRes.status === 0 || matchesRes.networkError || eventsRes.networkError) {
        setLoadError(getUserFacingError(0));
        return;
      }
      if (matchesRes.ok && Array.isArray(matchesRes.data)) setMatches(matchesRes.data);
      if (eventsRes.ok && Array.isArray(eventsRes.data)) setEvents(eventsRes.data);
    });
  }

  useEffect(() => {
    load();
    const today = new Date().toISOString().slice(0, 10);
    setForm((f) => ({ ...f, eventDate: today }));
  }, []);

  const items: CalendarItem[] = [
    ...matches.filter((m) => m.date).map((m) => ({
      type: "match" as const,
      id: m.id,
      date: m.date!,
      title: "Матч vs " + m.opponent,
      subtitle: m.location ?? undefined,
      location: m.location ?? null,
      match: m,
    })),
    ...events.map((e) => ({
      type: "event" as const,
      id: e.id,
      date: e.eventDate,
      title: e.title,
      subtitle: e.eventType === "TRAINING" ? "Тренировка" : "Событие",
      location: e.location ?? null,
      event: e,
    })),
  ].sort((a, b) => parseDate(a.date) - parseDate(b.date));

  const now = Date.now();
  const upcoming = items.filter((i) => parseDate(i.date) >= now);
  const past = items.filter((i) => parseDate(i.date) < now).reverse();

  async function submitEvent(e: React.FormEvent) {
    e.preventDefault();
    setMessage(null);
    const dateStr = form.eventDate + "T" + (form.eventTime || "18:00") + ":00";
    const iso = new Date(dateStr).toISOString();
    const res = await apiPost<ActionResult>("/api/admin/events", {
      title: form.title.trim(),
      eventType: form.eventType,
      eventDate: iso,
      location: form.location.trim() || null,
      description: form.description.trim() || null,
    });
    if (res.ok && res.data?.success) {
      setMessage({ type: "ok", text: res.data.message ?? "Событие создано" });
      setForm((f) => ({ ...f, title: "", description: "" }));
      load();
    } else {
      setMessage({ type: "err", text: getUserFacingError(res.status, res.data?.data ?? res.error) });
    }
  }

  async function deleteEvent(id: number) {
    if (!window.confirm("Удалить событие?")) return;
    setMessage(null);
    const res = await apiDelete<ActionResult>(`/api/admin/events/${id}`);
    if (res.ok && res.data?.success) {
      setMessage({ type: "ok", text: "Событие удалено" });
      load();
    } else {
      setMessage({ type: "err", text: getUserFacingError(res.status, res.data?.data ?? res.error) });
    }
  }

  function formatDate(d: string) {
    const date = new Date(d);
    return date.toLocaleDateString("ru-RU", { day: "numeric", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" });
  }

  if (loading) return <div className="text-zinc-500">Загрузка…</div>;
  if (loadError) {
    return (
      <div>
        <h1 className="mb-6 text-2xl font-semibold text-zinc-800">Календарь</h1>
        <p className="mb-4 rounded-lg bg-amber-100 px-3 py-2 text-zinc-800">{loadError}</p>
        <button type="button" onClick={() => load()} className="rounded-lg bg-blue-600 px-4 py-2 text-white hover:bg-blue-700">Повторить</button>
      </div>
    );
  }

  return (
    <div>
      <h1 className="mb-6 text-2xl font-semibold text-zinc-800">Календарь событий</h1>
      {message && (
        <p className={`mb-4 rounded-lg px-3 py-2 text-sm ${message.type === "ok" ? "bg-green-100 text-green-800" : "bg-red-100 text-red-800"}`}>
          {message.text}
        </p>
      )}

      <form onSubmit={submitEvent} className="mb-8 rounded-xl border border-zinc-200 bg-white p-4 shadow-sm">
        <h2 className="mb-4 text-lg font-medium text-zinc-800">Создать событие (тренировка/игра)</h2>
        <p className="mb-3 text-sm text-zinc-500">После создания уведомление автоматически отправится в чат команды в Telegram.</p>
        <div className="flex flex-wrap gap-4">
          <div className="min-w-[200px]">
            <label className="mb-1 block text-sm text-zinc-600">Название</label>
            <input
              type="text"
              value={form.title}
              onChange={(e) => setForm((f) => ({ ...f, title: e.target.value }))}
              className="w-full rounded-lg border border-zinc-300 px-3 py-2"
              placeholder="Тренировка / Игра с..."
              required
            />
          </div>
          <div>
            <label className="mb-1 block text-sm text-zinc-600">Тип</label>
            <select
              value={form.eventType}
              onChange={(e) => setForm((f) => ({ ...f, eventType: e.target.value }))}
              className="rounded-lg border border-zinc-300 px-3 py-2"
            >
              <option value="TRAINING">Тренировка</option>
              <option value="GAME">Игра</option>
            </select>
          </div>
          <div>
            <label className="mb-1 block text-sm text-zinc-600">Дата</label>
            <input
              type="date"
              value={form.eventDate}
              onChange={(e) => setForm((f) => ({ ...f, eventDate: e.target.value }))}
              className="rounded-lg border border-zinc-300 px-3 py-2"
              required
            />
          </div>
          <div>
            <label className="mb-1 block text-sm text-zinc-600">Время</label>
            <input
              type="time"
              value={form.eventTime}
              onChange={(e) => setForm((f) => ({ ...f, eventTime: e.target.value }))}
              className="rounded-lg border border-zinc-300 px-3 py-2"
            />
          </div>
          <div className="min-w-[180px]">
            <label className="mb-1 block text-sm text-zinc-600">Место</label>
            <input
              type="text"
              value={form.location}
              onChange={(e) => setForm((f) => ({ ...f, location: e.target.value }))}
              className="w-full rounded-lg border border-zinc-300 px-3 py-2"
              placeholder="Зал «Олимпиец»"
            />
          </div>
          <div className="flex w-full items-end gap-2">
            <input
              type="text"
              value={form.description}
              onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
              className="flex-1 rounded-lg border border-zinc-300 px-3 py-2"
              placeholder="Описание (необязательно)"
            />
            <button type="submit" className="rounded-lg bg-blue-600 px-4 py-2 text-white hover:bg-blue-700">Создать</button>
          </div>
        </div>
      </form>

      <div className="space-y-6">
        {upcoming.length > 0 && (
          <div>
            <h2 className="mb-3 text-lg font-medium text-zinc-800">Ближайшие</h2>
            <ul className="space-y-2">
              {upcoming.map((i) => (
                <li key={`${i.type}-${i.id}`} className="flex items-center justify-between rounded-lg border border-zinc-200 bg-white px-4 py-3 shadow-sm">
                  <div>
                    <span className="font-medium">{i.title}</span>
                    {i.subtitle && <span className="ml-2 text-sm text-zinc-500">{i.subtitle}</span>}
                    <p className="text-sm text-zinc-600">{formatDate(i.date)}</p>
                    {i.location && <p className="text-sm text-zinc-500">🏟️ {i.location}</p>}
                  </div>
                  <div>
                    {i.type === "match" && <Link href="/matches" className="text-blue-600 hover:underline">К матчу</Link>}
                    {i.type === "event" && (
                      <button type="button" onClick={() => deleteEvent(i.id)} className="text-red-600 hover:underline">Удалить</button>
                    )}
                  </div>
                </li>
              ))}
            </ul>
          </div>
        )}
        {past.length > 0 && (
          <div>
            <h2 className="mb-3 text-lg font-medium text-zinc-600">Прошедшие</h2>
            <ul className="space-y-2">
              {past.map((i) => (
                <li key={`${i.type}-${i.id}`} className="flex items-center justify-between rounded-lg border border-zinc-100 bg-zinc-50 px-4 py-2 text-zinc-600">
                  <div>
                    <span>{i.title}</span>
                    {i.subtitle && <span className="ml-2 text-sm">{i.subtitle}</span>}
                    <p className="text-sm">{formatDate(i.date)}</p>
                  </div>
                  {i.type === "match" && <Link href="/matches" className="text-blue-600 hover:underline">К матчу</Link>}
                  {i.type === "event" && (
                    <button type="button" onClick={() => deleteEvent(i.id)} className="text-red-500 hover:underline">Удалить</button>
                  )}
                </li>
              ))}
            </ul>
          </div>
        )}
        {items.length === 0 && <p className="text-zinc-500">Нет матчей и событий. Добавьте матч на странице «Матчи» или создайте событие выше.</p>}
      </div>
    </div>
  );
}
