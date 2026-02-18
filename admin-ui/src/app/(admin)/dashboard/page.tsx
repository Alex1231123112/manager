"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { apiGet, apiPost } from "@/lib/api";
import type { DashboardDto, ActionResult } from "@/lib/types";

function formatDate(s: string | null) {
  if (!s) return "—";
  try {
    const d = new Date(s);
    return d.toLocaleString("ru-RU", {
      day: "2-digit",
      month: "2-digit",
      year: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    });
  } catch {
    return s;
  }
}

export default function DashboardPage() {
  const [data, setData] = useState<DashboardDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [notifyText, setNotifyText] = useState("");
  const [notifySending, setNotifySending] = useState(false);
  const [notifyMessage, setNotifyMessage] = useState<{ type: "ok" | "err"; text: string } | null>(null);

  useEffect(() => {
    apiGet<DashboardDto>("/api/admin/dashboard").then((res) => {
      setLoading(false);
      if (res.ok && res.data) setData(res.data);
    });
  }, []);

  if (loading) return <div className="text-zinc-500">Загрузка…</div>;
  if (!data) return <div className="text-zinc-600">Нет данных</div>;

  const next = data.nextMatch;

  return (
    <div>
      <h1 className="mb-6 text-2xl font-semibold text-zinc-800">Дашборд</h1>
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <div className="rounded-xl border border-zinc-200 bg-white p-4 shadow-sm">
          <p className="text-sm text-zinc-500">Игроков в команде</p>
          <p className="text-2xl font-semibold text-zinc-800">
            {data.playerCount}
          </p>
        </div>
        <div className="rounded-xl border border-zinc-200 bg-white p-4 shadow-sm">
          <p className="text-sm text-zinc-500">Должников</p>
          <p className="text-2xl font-semibold text-zinc-800">
            {data.debtorCount}
          </p>
        </div>
        <div className="rounded-xl border border-zinc-200 bg-white p-4 shadow-sm">
          <p className="text-sm text-zinc-500">Сумма долгов</p>
          <p className="text-2xl font-semibold text-zinc-800">
            {data.totalDebt.toFixed(0)} ₽
          </p>
        </div>
      </div>
      {next && (
        <div className="mt-6 rounded-xl border border-zinc-200 bg-white p-4 shadow-sm">
          <h2 className="mb-2 text-lg font-medium text-zinc-800">
            Ближайший матч
          </h2>
          <p className="text-zinc-600">
            {next.opponent} — {formatDate(next.date)}
          </p>
          <p className="mt-1 text-sm text-zinc-500">{next.location ?? "—"}</p>
          <Link
            href="/matches"
            className="mt-2 inline-block text-blue-600 hover:underline"
          >
            К матчам →
          </Link>
        </div>
      )}

      <div className="mt-6 rounded-xl border border-zinc-200 bg-white p-4 shadow-sm">
        <h2 className="mb-2 text-lg font-medium text-zinc-800">
          Уведомление команде
        </h2>
        <p className="mb-3 text-sm text-zinc-500">
          Текст отправится в чат команды в Telegram (бот должен быть добавлен в чат и привязан по /start).
        </p>
        {notifyMessage && (
          <p
            className={`mb-3 rounded-lg px-3 py-2 text-sm ${notifyMessage.type === "ok" ? "bg-green-100 text-green-800" : "bg-red-100 text-red-800"}`}
          >
            {notifyMessage.text}
          </p>
        )}
        <textarea
          value={notifyText}
          onChange={(e) => setNotifyText(e.target.value)}
          placeholder="Текст уведомления…"
          rows={3}
          className="mb-3 w-full rounded-lg border border-zinc-300 px-3 py-2"
        />
        <button
          type="button"
          disabled={notifySending || !notifyText.trim()}
          onClick={async () => {
            setNotifyMessage(null);
            setNotifySending(true);
            const res = await apiPost<ActionResult>("/api/admin/notify", { message: notifyText.trim() });
            setNotifySending(false);
            if (res.ok && res.data?.success) {
              setNotifyMessage({ type: "ok", text: res.data.message ?? "Отправлено" });
              setNotifyText("");
            } else {
              setNotifyMessage({ type: "err", text: res.data?.data ?? res.error ?? "Ошибка" });
            }
          }}
          className="rounded-lg bg-blue-600 px-4 py-2 text-white hover:bg-blue-700 disabled:opacity-50"
        >
          {notifySending ? "Отправка…" : "Отправить в чат команды"}
        </button>
      </div>
    </div>
  );
}
