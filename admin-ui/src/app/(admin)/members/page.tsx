"use client";

import { useEffect, useState } from "react";
import { apiGet, apiPatch } from "@/lib/api";
import type { MemberDto, ActionResult } from "@/lib/types";

const ROLE_LABELS: Record<string, string> = {
  ADMIN: "Админ",
  CAPTAIN: "Капитан",
  PLAYER: "Игрок",
};

function formatContact(m: MemberDto): string {
  if (m.telegramUsername && m.telegramUsername.trim()) {
    return m.telegramUsername.startsWith("@") ? m.telegramUsername : "@" + m.telegramUsername;
  }
  return "—";
}

export default function MembersPage() {
  const [list, setList] = useState<MemberDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState<{ type: "ok" | "err"; text: string } | null>(null);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editName, setEditName] = useState("");

  function load() {
    apiGet<MemberDto[]>("/api/admin/members").then((res) => {
      setLoading(false);
      if (res.ok && Array.isArray(res.data)) setList(res.data);
    });
  }

  useEffect(() => {
    load();
  }, []);

  async function changeRole(telegramUserId: string, role: string) {
    setMessage(null);
    const res = await apiPatch<ActionResult>(
      `/api/admin/members/${encodeURIComponent(telegramUserId)}/role`,
      { role }
    );
    if (res.ok && res.data?.success) {
      setMessage({ type: "ok", text: res.data.message ?? "Роль обновлена" });
      load();
    } else {
      setMessage({ type: "err", text: res.data?.data ?? res.error ?? "Ошибка" });
    }
  }

  function startEdit(m: MemberDto) {
    setEditingId(m.telegramUserId);
    setEditName(m.displayName ?? "");
  }

  async function saveDisplayName(telegramUserId: string) {
    setEditingId(null);
    setMessage(null);
    const res = await apiPatch<ActionResult>(
      `/api/admin/members/${encodeURIComponent(telegramUserId)}`,
      { displayName: editName.trim() || null }
    );
    if (res.ok && res.data?.success) {
      setMessage({ type: "ok", text: res.data.message ?? "Имя сохранено" });
      load();
    } else {
      setMessage({ type: "err", text: res.data?.data ?? res.error ?? "Ошибка" });
    }
  }

  if (loading) return <div className="text-zinc-500">Загрузка…</div>;

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-2xl font-semibold text-zinc-800">Участники команды</h1>
        <p className="mt-1 text-sm text-zinc-500">
          Роли в Telegram-боте: кто может вводить результат, выставлять долги и публиковать в канал. Имя можно редактировать; контакт (@username) подставляется из Telegram при взаимодействии с ботом.
        </p>
      </div>
      {message && (
        <p
          className={`mb-4 rounded-lg px-3 py-2 text-sm ${message.type === "ok" ? "bg-green-100 text-green-800" : "bg-red-100 text-red-800"}`}
        >
          {message.text}
        </p>
      )}
      <div className="overflow-hidden rounded-xl border border-zinc-200 bg-white shadow-sm">
        <table className="w-full">
          <thead className="bg-zinc-50">
            <tr>
              <th className="px-4 py-2 text-left text-sm font-medium text-zinc-600">
                Имя
              </th>
              <th className="px-4 py-2 text-left text-sm font-medium text-zinc-600">
                Контакт
              </th>
              <th className="px-4 py-2 text-left text-sm font-medium text-zinc-600">
                Роль
              </th>
              <th className="px-4 py-2 text-left text-sm font-medium text-zinc-600">
                Сменить роль
              </th>
            </tr>
          </thead>
          <tbody>
            {list.length === 0 ? (
              <tr>
                <td colSpan={4} className="px-4 py-6 text-center text-zinc-500">
                  Нет записей. Участники появляются после создания команды в боте и назначения ролей (/setrole в Telegram).
                </td>
              </tr>
            ) : (
              list.map((m) => (
                <tr key={m.telegramUserId} className="border-t border-zinc-100">
                  <td className="px-4 py-2">
                    {editingId === m.telegramUserId ? (
                      <span className="flex items-center gap-2">
                        <input
                          type="text"
                          value={editName}
                          onChange={(e) => setEditName(e.target.value)}
                          onKeyDown={(e) => {
                            if (e.key === "Enter") saveDisplayName(m.telegramUserId);
                            if (e.key === "Escape") setEditingId(null);
                          }}
                          className="w-48 rounded border border-zinc-300 px-2 py-1 text-sm"
                          autoFocus
                        />
                        <button
                          type="button"
                          onClick={() => saveDisplayName(m.telegramUserId)}
                          className="text-sm text-blue-600 hover:underline"
                        >
                          Сохранить
                        </button>
                        <button
                          type="button"
                          onClick={() => setEditingId(null)}
                          className="text-sm text-zinc-500 hover:underline"
                        >
                          Отмена
                        </button>
                      </span>
                    ) : (
                      <span
                        className="cursor-pointer rounded px-1 py-0.5 hover:bg-zinc-100"
                        onClick={() => startEdit(m)}
                        title="Нажмите, чтобы изменить имя"
                      >
                        {m.displayName?.trim() || "—"}
                      </span>
                    )}
                  </td>
                  <td className="px-4 py-2 text-zinc-600">{formatContact(m)}</td>
                  <td className="px-4 py-2">{ROLE_LABELS[m.role] ?? m.role}</td>
                  <td className="px-4 py-2">
                    <select
                      value={m.role}
                      onChange={(e) => changeRole(m.telegramUserId, e.target.value)}
                      className="rounded-lg border border-zinc-300 px-2 py-1 text-sm"
                    >
                      <option value="ADMIN">{ROLE_LABELS.ADMIN}</option>
                      <option value="CAPTAIN">{ROLE_LABELS.CAPTAIN}</option>
                      <option value="PLAYER">{ROLE_LABELS.PLAYER}</option>
                    </select>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
