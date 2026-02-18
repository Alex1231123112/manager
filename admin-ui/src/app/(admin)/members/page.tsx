"use client";

import { useEffect, useState } from "react";
import { apiGet, apiPatch } from "@/lib/api";
import type { MemberDto, ActionResult } from "@/lib/types";

const ROLE_LABELS: Record<string, string> = {
  ADMIN: "Админ",
  PLAYER: "Игрок",
};

const STATUS_LABELS: Record<string, string> = {
  ACTIVE: "Активен",
  INJURY: "Травма",
  VACATION: "Отпуск",
  NOT_PAID: "Не оплатил",
};

function formatContact(m: MemberDto): string {
  if (m.telegramUsername && m.telegramUsername.trim()) {
    return m.telegramUsername.startsWith("@") ? m.telegramUsername : "@" + m.telegramUsername;
  }
  return "—";
}

function debtValue(m: MemberDto): number {
  const d = m.debt;
  if (typeof d === "number") return d;
  if (typeof d === "string") return parseFloat(d) || 0;
  return 0;
}

export default function MembersPage() {
  const [list, setList] = useState<MemberDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState<{ type: "ok" | "err"; text: string } | null>(null);
  const [editing, setEditing] = useState<MemberDto | null>(null);
  const [form, setForm] = useState({ displayName: "", role: "PLAYER", number: "" as string, status: "ACTIVE", debt: "" as string, isActive: true });

  function load() {
    apiGet<MemberDto[]>("/api/admin/members").then((res) => {
      setLoading(false);
      if (res.ok && Array.isArray(res.data)) setList(res.data);
    });
  }

  useEffect(() => {
    load();
  }, []);

  function openEdit(m: MemberDto) {
    setEditing(m);
    setForm({
      displayName: m.displayName?.trim() ?? "",
      role: m.role ?? "PLAYER",
      number: m.number != null ? String(m.number) : "",
      status: m.status ?? "ACTIVE",
      debt: String(debtValue(m)),
      isActive: m.isActive ?? true,
    });
  }

  async function saveMember() {
    if (!editing) return;
    setMessage(null);
    const res = await apiPatch<ActionResult>(
      `/api/admin/members/${encodeURIComponent(editing.telegramUserId)}`,
      {
        displayName: form.displayName.trim() || null,
        role: form.role,
        isActive: form.isActive,
        number: form.number.trim() ? parseInt(form.number, 10) : null,
        status: form.status,
        debt: form.debt.trim() ? parseFloat(form.debt) : 0,
      }
    );
    if (res.ok && res.data?.success) {
      setMessage({ type: "ok", text: res.data.message ?? "Участник обновлён" });
      setEditing(null);
      load();
    } else {
      setMessage({ type: "err", text: res.data?.data ?? res.error ?? "Ошибка" });
    }
  }

  async function setActive(telegramUserId: string, isActive: boolean) {
    setMessage(null);
    const m = list.find((x) => x.telegramUserId === telegramUserId);
    if (!m) return;
    const res = await apiPatch<ActionResult>(
      `/api/admin/members/${encodeURIComponent(telegramUserId)}`,
      {
        displayName: m.displayName ?? undefined,
        role: m.role,
        isActive,
        number: m.number ?? undefined,
        status: m.status,
        debt: debtValue(m),
      }
    );
    if (res.ok && res.data?.success) {
      setMessage({ type: "ok", text: isActive ? "Участник активирован" : "Участник деактивирован" });
      load();
    } else {
      setMessage({ type: "err", text: res.data?.data ?? res.error ?? "Ошибка" });
    }
  }

  async function changeRole(telegramUserId: string, role: string) {
    const m = list.find((x) => x.telegramUserId === telegramUserId);
    if (!m) return;
    setMessage(null);
    const res = await apiPatch<ActionResult>(
      `/api/admin/members/${encodeURIComponent(telegramUserId)}`,
      {
        displayName: m.displayName ?? undefined,
        role,
        isActive: m.isActive,
        number: m.number ?? undefined,
        status: m.status,
        debt: debtValue(m),
      }
    );
    if (res.ok && res.data?.success) {
      setMessage({ type: "ok", text: "Роль обновлена" });
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
          Состав и роли: имя, номер, статус, долг. Редактирование по кнопке «Редактировать». Деактивация скрывает участника из активного состава в боте (без удаления данных).
        </p>
      </div>
      {message && (
        <p
          className={`mb-4 rounded-lg px-3 py-2 text-sm ${message.type === "ok" ? "bg-green-100 text-green-800" : "bg-red-100 text-red-800"}`}
        >
          {message.text}
        </p>
      )}

      {editing && (
        <div className="fixed inset-0 z-10 flex items-center justify-center bg-black/30 p-4">
          <div className="max-w-md rounded-xl bg-white p-6 shadow-lg">
            <h2 className="mb-4 text-lg font-medium text-zinc-800">Редактировать участника</h2>
            <div className="space-y-3">
              <div>
                <label className="block text-sm font-medium text-zinc-600">Имя</label>
                <input
                  type="text"
                  value={form.displayName}
                  onChange={(e) => setForm((f) => ({ ...f, displayName: e.target.value }))}
                  className="mt-1 w-full rounded-lg border border-zinc-300 px-3 py-2"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-zinc-600">Номер</label>
                <input
                  type="text"
                  inputMode="numeric"
                  value={form.number}
                  onChange={(e) => setForm((f) => ({ ...f, number: e.target.value }))}
                  className="mt-1 w-full rounded-lg border border-zinc-300 px-3 py-2"
                  placeholder="—"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-zinc-600">Статус</label>
                <select
                  value={form.status}
                  onChange={(e) => setForm((f) => ({ ...f, status: e.target.value }))}
                  className="mt-1 w-full rounded-lg border border-zinc-300 px-3 py-2"
                >
                  {Object.entries(STATUS_LABELS).map(([k, v]) => (
                    <option key={k} value={k}>{v}</option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-zinc-600">Долг (₽)</label>
                <input
                  type="text"
                  inputMode="decimal"
                  value={form.debt}
                  onChange={(e) => setForm((f) => ({ ...f, debt: e.target.value }))}
                  className="mt-1 w-full rounded-lg border border-zinc-300 px-3 py-2"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-zinc-600">Роль</label>
                <select
                  value={form.role}
                  onChange={(e) => setForm((f) => ({ ...f, role: e.target.value }))}
                  className="mt-1 w-full rounded-lg border border-zinc-300 px-3 py-2"
                >
                  {Object.entries(ROLE_LABELS).map(([k, v]) => (
                    <option key={k} value={k}>{v}</option>
                  ))}
                </select>
              </div>
              <label className="flex items-center gap-2">
                <input
                  type="checkbox"
                  checked={form.isActive}
                  onChange={(e) => setForm((f) => ({ ...f, isActive: e.target.checked }))}
                  className="rounded border-zinc-300"
                />
                <span className="text-sm text-zinc-700">Активен (участвует в составе)</span>
              </label>
            </div>
            <div className="mt-6 flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setEditing(null)}
                className="rounded-lg border border-zinc-300 px-4 py-2 text-zinc-700 hover:bg-zinc-50"
              >
                Отмена
              </button>
              <button
                type="button"
                onClick={() => saveMember()}
                className="rounded-lg bg-blue-600 px-4 py-2 text-white hover:bg-blue-700"
              >
                Сохранить
              </button>
            </div>
          </div>
        </div>
      )}

      <div className="overflow-x-auto rounded-xl border border-zinc-200 bg-white shadow-sm">
        <table className="w-full min-w-[800px]">
          <thead className="bg-zinc-50">
            <tr>
              <th className="px-4 py-2 text-left text-sm font-medium text-zinc-600">Имя</th>
              <th className="px-4 py-2 text-left text-sm font-medium text-zinc-600">Контакт</th>
              <th className="px-4 py-2 text-left text-sm font-medium text-zinc-600">№</th>
              <th className="px-4 py-2 text-left text-sm font-medium text-zinc-600">Статус</th>
              <th className="px-4 py-2 text-left text-sm font-medium text-zinc-600">Долг</th>
              <th className="px-4 py-2 text-left text-sm font-medium text-zinc-600">Роль</th>
              <th className="px-4 py-2 text-left text-sm font-medium text-zinc-600">Активен</th>
              <th className="px-4 py-2 text-left text-sm font-medium text-zinc-600">Действия</th>
            </tr>
          </thead>
          <tbody>
            {list.length === 0 ? (
              <tr>
                <td colSpan={8} className="px-4 py-6 text-center text-zinc-500">
                  Нет записей. Участники появляются после создания команды в боте и приглашений или назначения ролей (/setrole в Telegram).
                </td>
              </tr>
            ) : (
              list.map((m) => (
                <tr
                  key={m.telegramUserId}
                  className={`border-t border-zinc-100 ${!m.isActive ? "bg-zinc-50 text-zinc-500" : ""}`}
                >
                  <td className="px-4 py-2 font-medium">{m.displayName?.trim() || "—"}</td>
                  <td className="px-4 py-2 text-zinc-600">{formatContact(m)}</td>
                  <td className="px-4 py-2">{m.number != null ? m.number : "—"}</td>
                  <td className="px-4 py-2">{STATUS_LABELS[m.status] ?? m.status}</td>
                  <td className="px-4 py-2">{debtValue(m) > 0 ? `${debtValue(m)} ₽` : "—"}</td>
                  <td className="px-4 py-2">
                    <select
                      value={m.role}
                      onChange={(e) => changeRole(m.telegramUserId, e.target.value)}
                      className="rounded border border-zinc-300 px-2 py-1 text-sm"
                      disabled={!m.isActive}
                    >
                      <option value="ADMIN">{ROLE_LABELS.ADMIN}</option>
                      <option value="PLAYER">{ROLE_LABELS.PLAYER}</option>
                    </select>
                  </td>
                  <td className="px-4 py-2">{m.isActive ? "Да" : "Нет"}</td>
                  <td className="px-4 py-2">
                    <button
                      type="button"
                      onClick={() => openEdit(m)}
                      className="mr-2 text-sm text-blue-600 hover:underline"
                    >
                      Редактировать
                    </button>
                    {m.isActive ? (
                      <button
                        type="button"
                        onClick={() => window.confirm("Деактивировать участника? Он не будет учитываться в активном составе.") && setActive(m.telegramUserId, false)}
                        className="text-sm text-amber-600 hover:underline"
                      >
                        Деактивировать
                      </button>
                    ) : (
                      <button
                        type="button"
                        onClick={() => setActive(m.telegramUserId, true)}
                        className="text-sm text-green-600 hover:underline"
                      >
                        Активировать
                      </button>
                    )}
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
