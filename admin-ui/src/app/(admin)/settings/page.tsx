"use client";

import { useEffect, useState } from "react";
import { apiGet, apiPost, apiPut } from "@/lib/api";
import { getUserFacingError } from "@/lib/errors";
import type { SettingsDto, SystemSettingsDto, ActionResult } from "@/lib/types";

export default function SettingsPage() {
  const [channelId, setChannelId] = useState("");
  const [groupChatId, setGroupChatId] = useState("");
  const [adminTelegramId, setAdminTelegramId] = useState("");
  const [adminTelegramUsername, setAdminTelegramUsername] = useState("");
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [message, setMessage] = useState<{ type: "ok" | "err"; text: string } | null>(null);
  const [systemMessage, setSystemMessage] = useState<{ type: "ok" | "err"; text: string } | null>(null);

  function load() {
    setLoadError(null);
    setLoading(true);
    Promise.all([
      apiGet<SettingsDto>("/api/admin/settings"),
      apiGet<SystemSettingsDto>("/api/admin/system-settings"),
    ]).then(([settingsRes, systemRes]) => {
      setLoading(false);
      const networkFailed =
        settingsRes.status === 0 ||
        settingsRes.networkError ||
        systemRes.status === 0 ||
        systemRes.networkError;
      if (networkFailed) {
        setLoadError(getUserFacingError(0));
        return;
      }
      if (settingsRes.ok && settingsRes.data) {
        setChannelId(settingsRes.data.channelId ?? "");
        setGroupChatId(settingsRes.data.groupChatId ?? "");
      }
      if (systemRes.ok && systemRes.data) {
        setAdminTelegramId(systemRes.data.adminTelegramId ?? "");
        const uname = systemRes.data.adminTelegramUsername ?? "";
        setAdminTelegramUsername(uname ? (uname.startsWith("@") ? uname : "@" + uname) : "");
      }
    });
  }

  useEffect(() => {
    load();
  }, []);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setMessage(null);
    const res = await apiPost<ActionResult>("/api/admin/settings", {
      channelId: channelId.trim(),
      groupChatId: groupChatId.trim(),
    });
    if (res.ok && res.data?.success) {
      setMessage({ type: "ok", text: res.data.message ?? "Настройки сохранены" });
    } else {
      setMessage({ type: "err", text: getUserFacingError(res.status, res.data?.data ?? res.error) });
    }
  }

  async function submitSystem(e: React.FormEvent) {
    e.preventDefault();
    setSystemMessage(null);
    const idVal = adminTelegramId.trim();
    const unameVal = adminTelegramUsername.trim().replace(/^@/, "");
    const res = await apiPut<ActionResult>("/api/admin/system-settings", {
      adminTelegramId: idVal,
      adminTelegramUsername: unameVal,
    });
    if (res.ok && res.data?.success) {
      setSystemMessage({ type: "ok", text: res.data.message ?? "Сохранено" });
      setAdminTelegramId(idVal);
      setAdminTelegramUsername(unameVal ? "@" + unameVal : "");
    } else {
      setSystemMessage({ type: "err", text: getUserFacingError(res.status, res.data?.data ?? res.error) });
    }
  }

  if (loading) return <div className="text-zinc-500">Загрузка…</div>;
  if (loadError) {
    return (
      <div>
        <h1 className="mb-6 text-2xl font-semibold text-zinc-800">Настройки</h1>
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
        <div className="mb-4">
          <label className="mb-1 block text-sm font-medium text-zinc-600">
            ID группового чата (для опросов и уведомлений)
          </label>
          <input
            type="text"
            value={groupChatId}
            onChange={(e) => setGroupChatId(e.target.value)}
            className="w-full rounded-lg border border-zinc-300 px-3 py-2"
            placeholder="-1001234567890"
          />
          <p className="mt-1 text-xs text-zinc-500">
            Если команда создана в личке, укажите сюда ID группы. Добавьте бота в группу и вставьте ID (например -100…).
          </p>
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
        <p className="mb-3 text-sm text-zinc-600">
          Только указанный пользователь может создавать новую команду в боте по /start без приглашения. Укажите Telegram ID и/или @username (достаточно одного).
        </p>
        <div className="mb-4">
          <label className="mb-1 block text-sm font-medium text-zinc-600">
            Telegram ID администратора
          </label>
          <input
            type="text"
            value={adminTelegramId}
            onChange={(e) => setAdminTelegramId(e.target.value)}
            className="w-full rounded-lg border border-zinc-300 px-3 py-2"
            placeholder="123456789"
          />
        </div>
        <div className="mb-4">
          <label className="mb-1 block text-sm font-medium text-zinc-600">
            Telegram @username администратора
          </label>
          <input
            type="text"
            value={adminTelegramUsername}
            onChange={(e) => setAdminTelegramUsername(e.target.value)}
            className="w-full rounded-lg border border-zinc-300 px-3 py-2"
            placeholder="@username"
          />
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
