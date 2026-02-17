"use client";

import { useEffect, useState } from "react";
import { apiGet, apiPost, apiPut } from "@/lib/api";
import type { SettingsDto, SystemSettingsDto, ActionResult } from "@/lib/types";

export default function SettingsPage() {
  const [channelId, setChannelId] = useState("");
  const [adminTelegramUsername, setAdminTelegramUsername] = useState("");
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState<{ type: "ok" | "err"; text: string } | null>(null);
  const [systemMessage, setSystemMessage] = useState<{ type: "ok" | "err"; text: string } | null>(null);

  useEffect(() => {
    Promise.all([
      apiGet<SettingsDto>("/api/admin/settings"),
      apiGet<SystemSettingsDto>("/api/admin/system-settings"),
    ]).then(([settingsRes, systemRes]) => {
      setLoading(false);
      if (settingsRes.ok && settingsRes.data) setChannelId(settingsRes.data.channelId ?? "");
      const uname = systemRes.ok && systemRes.data ? (systemRes.data.adminTelegramUsername ?? "") : "";
      setAdminTelegramUsername(uname ? (uname.startsWith("@") ? uname : "@" + uname) : "");
    });
  }, []);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setMessage(null);
    const res = await apiPost<ActionResult>("/api/admin/settings", {
      channelId: channelId.trim(),
    });
    if (res.ok && res.data?.success) {
      setMessage({ type: "ok", text: res.data.message ?? "Настройки сохранены" });
    } else {
      setMessage({ type: "err", text: res.data?.data ?? res.error ?? "Ошибка" });
    }
  }

  async function submitSystem(e: React.FormEvent) {
    e.preventDefault();
    setSystemMessage(null);
    const value = adminTelegramUsername.trim().replace(/^@/, "");
    const res = await apiPut<ActionResult>("/api/admin/system-settings", {
      adminTelegramUsername: value,
    });
    if (res.ok && res.data?.success) {
      setSystemMessage({ type: "ok", text: res.data.message ?? "Сохранено" });
      setAdminTelegramUsername(value ? "@" + value : "");
    } else {
      setSystemMessage({ type: "err", text: res.data?.data ?? res.error ?? "Ошибка" });
    }
  }

  if (loading) return <div className="text-zinc-500">Загрузка…</div>;

  return (
    <div>
      <h1 className="mb-6 text-2xl font-semibold text-zinc-800">Настройки</h1>
      {message && (
        <p
          className={`mb-4 rounded-lg px-3 py-2 text-sm ${message.type === "ok" ? "bg-green-100 text-green-800" : "bg-red-100 text-red-800"}`}
        >
          {message.text}
        </p>
      )}
      <form
        onSubmit={submit}
        className="mb-8 max-w-md rounded-xl border border-zinc-200 bg-white p-4 shadow-sm"
      >
        <h2 className="mb-3 text-lg font-medium text-zinc-700">Команда</h2>
        <div className="mb-4">
          <label className="mb-1 block text-sm font-medium text-zinc-600">
            ID канала Telegram (для публикации результатов)
          </label>
          <input
            type="text"
            value={channelId}
            onChange={(e) => setChannelId(e.target.value)}
            className="w-full rounded-lg border border-zinc-300 px-3 py-2"
            placeholder="@channel или -100..."
          />
        </div>
        <button
          type="submit"
          className="rounded-lg bg-blue-600 px-4 py-2 text-white hover:bg-blue-700"
        >
          Сохранить
        </button>
      </form>

      {systemMessage && (
        <p
          className={`mb-4 rounded-lg px-3 py-2 text-sm ${systemMessage.type === "ok" ? "bg-green-100 text-green-800" : "bg-red-100 text-red-800"}`}
        >
          {systemMessage.text}
        </p>
      )}
      <form
        onSubmit={submitSystem}
        className="max-w-md rounded-xl border border-zinc-200 bg-white p-4 shadow-sm"
      >
        <h2 className="mb-3 text-lg font-medium text-zinc-700">Доступ в бот</h2>
        <div className="mb-4">
          <label className="mb-1 block text-sm font-medium text-zinc-600">
            Telegram администратора
          </label>
          <input
            type="text"
            value={adminTelegramUsername}
            onChange={(e) => setAdminTelegramUsername(e.target.value)}
            className="w-full rounded-lg border border-zinc-300 px-3 py-2"
            placeholder="@username"
          />
          <p className="mt-1 text-xs text-zinc-500">
            Только этот пользователь может создавать новую команду в боте без приглашения. Укажите имя в Telegram в формате @username.
          </p>
        </div>
        <button
          type="submit"
          className="rounded-lg bg-blue-600 px-4 py-2 text-white hover:bg-blue-700"
        >
          Сохранить
        </button>
      </form>
    </div>
  );
}
