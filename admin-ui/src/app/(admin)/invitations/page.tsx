"use client";

import { useEffect, useState } from "react";
import { QRCodeSVG } from "qrcode.react";
import { apiGet, apiPost, apiDelete } from "@/lib/api";
import type {
  InvitationDto,
  InvitationCreateResponse,
  ActionResult,
} from "@/lib/types";

const ROLE_LABELS: Record<string, string> = {
  ADMIN: "Админ",
  CAPTAIN: "Капитан",
  PLAYER: "Игрок",
};

function formatExpiresAt(iso: string): string {
  try {
    const d = new Date(iso);
    return d.toLocaleString("ru-RU", {
      day: "2-digit",
      month: "2-digit",
      year: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    });
  } catch {
    return iso;
  }
}

export default function InvitationsPage() {
  const [list, setList] = useState<InvitationDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState<{ type: "ok" | "err"; text: string } | null>(null);
  const [creating, setCreating] = useState(false);
  const [createRole, setCreateRole] = useState("PLAYER");
  const [createDays, setCreateDays] = useState(7);

  function load() {
    apiGet<InvitationDto[]>("/api/admin/invitations").then((res) => {
      setLoading(false);
      if (res.ok && Array.isArray(res.data)) setList(res.data);
    });
  }

  useEffect(() => {
    load();
  }, []);

  async function createInvitation(e: React.FormEvent) {
    e.preventDefault();
    setMessage(null);
    setCreating(true);
    const res = await apiPost<InvitationCreateResponse>("/api/admin/invitations", {
      role: createRole,
      expiresInDays: createDays,
    });
    setCreating(false);
    if (res.ok && res.data?.success && res.data.invitation) {
      setMessage({ type: "ok", text: "Приглашение создано" });
      load();
    } else {
      setMessage({
        type: "err",
        text: res.data?.error ?? res.error ?? "Ошибка создания",
      });
    }
  }

  async function deleteInvitation(code: string) {
    if (!confirm("Удалить приглашение?")) return;
    setMessage(null);
    const res = await apiDelete<ActionResult>(`/api/admin/invitations/${encodeURIComponent(code)}`);
    if (res.ok) {
      setMessage({ type: "ok", text: res.data?.message ?? "Приглашение удалено" });
      load();
    } else {
      setMessage({ type: "err", text: res.data?.data ?? res.error ?? "Ошибка" });
    }
  }

  function copyLink(link: string) {
    navigator.clipboard.writeText(link).then(
      () => setMessage({ type: "ok", text: "Ссылка скопирована" }),
      () => setMessage({ type: "err", text: "Не удалось скопировать" })
    );
  }

  if (loading) return <div className="text-zinc-500">Загрузка…</div>;

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-2xl font-semibold text-zinc-800">Приглашения в команду</h1>
        <p className="mt-1 text-sm text-zinc-500">
          Создайте ссылку или QR-код. Новый участник переходит по ссылке или вводит в боте /start CODE и добавляется в команду с выбранной ролью.
        </p>
      </div>
      {message && (
        <p
          className={`mb-4 rounded-lg px-3 py-2 text-sm ${message.type === "ok" ? "bg-green-100 text-green-800" : "bg-red-100 text-red-800"}`}
        >
          {message.text}
        </p>
      )}

      <form
        onSubmit={createInvitation}
        className="mb-8 flex flex-wrap items-end gap-4 rounded-xl border border-zinc-200 bg-white p-4 shadow-sm"
      >
        <div>
          <label className="mb-1 block text-sm font-medium text-zinc-600">Роль</label>
          <select
            value={createRole}
            onChange={(e) => setCreateRole(e.target.value)}
            className="rounded-lg border border-zinc-300 px-3 py-2"
          >
            <option value="PLAYER">{ROLE_LABELS.PLAYER}</option>
            <option value="CAPTAIN">{ROLE_LABELS.CAPTAIN}</option>
            <option value="ADMIN">{ROLE_LABELS.ADMIN}</option>
          </select>
        </div>
        <div>
          <label className="mb-1 block text-sm font-medium text-zinc-600">Срок (дней)</label>
          <input
            type="number"
            min={1}
            max={365}
            value={createDays}
            onChange={(e) => setCreateDays(parseInt(e.target.value, 10) || 7)}
            className="w-20 rounded-lg border border-zinc-300 px-3 py-2"
          />
        </div>
        <button
          type="submit"
          disabled={creating}
          className="rounded-lg bg-blue-600 px-4 py-2 text-white hover:bg-blue-700 disabled:opacity-50"
        >
          {creating ? "Создание…" : "Создать приглашение"}
        </button>
      </form>

      <div className="overflow-hidden rounded-xl border border-zinc-200 bg-white shadow-sm">
        <table className="w-full">
          <thead className="bg-zinc-50">
            <tr>
              <th className="px-4 py-2 text-left text-sm font-medium text-zinc-600">QR</th>
              <th className="px-4 py-2 text-left text-sm font-medium text-zinc-600">Код / Ссылка</th>
              <th className="px-4 py-2 text-left text-sm font-medium text-zinc-600">Роль</th>
              <th className="px-4 py-2 text-left text-sm font-medium text-zinc-600">Действует до</th>
              <th className="px-4 py-2 text-left text-sm font-medium text-zinc-600">Действия</th>
            </tr>
          </thead>
          <tbody>
            {list.length === 0 ? (
              <tr>
                <td colSpan={5} className="px-4 py-6 text-center text-zinc-500">
                  Нет приглашений. Создайте приглашение выше или в боте: /invite или кнопка «Приглашение».
                </td>
              </tr>
            ) : (
              list.map((inv) => (
                <tr key={inv.code} className="border-t border-zinc-100">
                  <td className="px-4 py-2">
                    <QRCodeSVG value={inv.link} size={80} level="M" />
                  </td>
                  <td className="px-4 py-2">
                    <span className="font-mono text-sm text-zinc-600">{inv.code}</span>
                    <br />
                    <button
                      type="button"
                      onClick={() => copyLink(inv.link)}
                      className="text-sm text-blue-600 hover:underline"
                    >
                      Копировать ссылку
                    </button>
                    <br />
                    <span className="break-all text-xs text-zinc-500">{inv.link}</span>
                  </td>
                  <td className="px-4 py-2">{ROLE_LABELS[inv.role] ?? inv.role}</td>
                  <td className="px-4 py-2 text-sm text-zinc-600">{formatExpiresAt(inv.expiresAt)}</td>
                  <td className="px-4 py-2">
                    <button
                      type="button"
                      onClick={() => deleteInvitation(inv.code)}
                      className="rounded bg-red-100 px-2 py-1 text-sm text-red-700 hover:bg-red-200"
                    >
                      Удалить
                    </button>
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
