"use client";

import { useEffect, useState } from "react";
import { apiGet, apiPost, apiDelete } from "@/lib/api";
import { getUserFacingError } from "@/lib/errors";
import type { LeagueTableRowDto, LeagueTableRowCreateDto, ActionResult } from "@/lib/types";

export default function LeagueTablePage() {
  const [rows, setRows] = useState<LeagueTableRowDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [message, setMessage] = useState<{ type: "ok" | "err"; text: string } | null>(null);
  const [form, setForm] = useState<LeagueTableRowCreateDto>({
    position: 1,
    teamName: "",
    wins: 0,
    losses: 0,
    pointsDiff: 0,
  });
  const [saving, setSaving] = useState(false);

  function load() {
    setLoadError(null);
    setLoading(true);
    apiGet<LeagueTableRowDto[]>("/api/admin/league-table").then((res) => {
      setLoading(false);
      if (res.status === 0 || res.networkError) {
        setLoadError(getUserFacingError(0));
        return;
      }
      if (res.ok && Array.isArray(res.data)) setRows(res.data);
    });
  }

  useEffect(() => {
    load();
  }, []);

  async function addRow(e: React.FormEvent) {
    e.preventDefault();
    setMessage(null);
    if (!form.teamName?.trim()) {
      setMessage({ type: "err", text: "Введите название команды" });
      return;
    }
    setSaving(true);
    const res = await apiPost<ActionResult>("/api/admin/league-table", {
      position: form.position ?? rows.length + 1,
      teamName: form.teamName.trim(),
      wins: form.wins ?? 0,
      losses: form.losses ?? 0,
      pointsDiff: form.pointsDiff ?? 0,
    });
    setSaving(false);
    if (res.ok && res.data?.success) {
      setMessage({ type: "ok", text: res.data.message ?? "Строка добавлена" });
      setForm({ position: rows.length + 2, teamName: "", wins: 0, losses: 0, pointsDiff: 0 });
      load();
    } else {
      setMessage({ type: "err", text: getUserFacingError(res.status, res.data?.data ?? res.error) });
    }
  }

  async function deleteRow(id: number) {
    if (!window.confirm("Удалить строку из таблицы?")) return;
    setMessage(null);
    const res = await apiDelete<ActionResult>(`/api/admin/league-table/${id}`);
    if (res.ok && res.data?.success) {
      setMessage({ type: "ok", text: "Строка удалена" });
      load();
    } else {
      setMessage({ type: "err", text: getUserFacingError(res.status, res.data?.data ?? res.error) });
    }
  }

  if (loading) return <div className="text-zinc-500">Загрузка…</div>;
  if (loadError) {
    return (
      <div>
        <h1 className="mb-6 text-2xl font-semibold text-zinc-800">Турнирная таблица</h1>
        <p className="mb-4 rounded-lg bg-amber-100 px-3 py-2 text-zinc-800">{loadError}</p>
        <button type="button" onClick={() => load()} className="rounded-lg bg-blue-600 px-4 py-2 text-white hover:bg-blue-700">
          Повторить
        </button>
      </div>
    );
  }

  return (
    <div>
      <h1 className="mb-6 text-2xl font-semibold text-zinc-800">Турнирная таблица</h1>
      {message && (
        <p
          className={`mb-4 rounded-lg px-3 py-2 text-sm ${message.type === "ok" ? "bg-green-100 text-green-800" : "bg-red-100 text-red-800"}`}
        >
          {message.text}
        </p>
      )}

      <form onSubmit={addRow} className="mb-6 flex flex-wrap items-end gap-3 rounded-lg border border-zinc-200 bg-white p-4">
        <div>
          <label className="mb-1 block text-sm font-medium text-zinc-700">Место</label>
          <input
            type="number"
            min={1}
            value={form.position ?? ""}
            onChange={(e) => setForm((f) => ({ ...f, position: parseInt(e.target.value, 10) || undefined }))}
            className="w-16 rounded border border-zinc-300 px-2 py-1.5"
          />
        </div>
        <div>
          <label className="mb-1 block text-sm font-medium text-zinc-700">Команда</label>
          <input
            type="text"
            value={form.teamName ?? ""}
            onChange={(e) => setForm((f) => ({ ...f, teamName: e.target.value }))}
            placeholder="Название"
            className="min-w-[140px] rounded border border-zinc-300 px-2 py-1.5"
          />
        </div>
        <div>
          <label className="mb-1 block text-sm font-medium text-zinc-700">Победы</label>
          <input
            type="number"
            min={0}
            value={form.wins ?? ""}
            onChange={(e) => setForm((f) => ({ ...f, wins: parseInt(e.target.value, 10) || 0 }))}
            className="w-16 rounded border border-zinc-300 px-2 py-1.5"
          />
        </div>
        <div>
          <label className="mb-1 block text-sm font-medium text-zinc-700">Поражения</label>
          <input
            type="number"
            min={0}
            value={form.losses ?? ""}
            onChange={(e) => setForm((f) => ({ ...f, losses: parseInt(e.target.value, 10) || 0 }))}
            className="w-16 rounded border border-zinc-300 px-2 py-1.5"
          />
        </div>
        <div>
          <label className="mb-1 block text-sm font-medium text-zinc-700">+/−</label>
          <input
            type="number"
            value={form.pointsDiff ?? ""}
            onChange={(e) => setForm((f) => ({ ...f, pointsDiff: parseInt(e.target.value, 10) || 0 }))}
            className="w-16 rounded border border-zinc-300 px-2 py-1.5"
          />
        </div>
        <button
          type="submit"
          disabled={saving}
          className="rounded-lg bg-blue-600 px-4 py-2 text-white hover:bg-blue-700 disabled:opacity-50"
        >
          {saving ? "Добавление…" : "Добавить"}
        </button>
      </form>

      <div className="overflow-x-auto rounded-lg border border-zinc-200 bg-white">
        <table className="w-full min-w-[400px] text-left text-sm">
          <thead className="border-b border-zinc-200 bg-zinc-50">
            <tr>
              <th className="px-4 py-2 font-medium text-zinc-700">#</th>
              <th className="px-4 py-2 font-medium text-zinc-700">Команда</th>
              <th className="px-4 py-2 font-medium text-zinc-700">П</th>
              <th className="px-4 py-2 font-medium text-zinc-700">Пор</th>
              <th className="px-4 py-2 font-medium text-zinc-700">+/−</th>
              <th className="w-20 px-4 py-2"></th>
            </tr>
          </thead>
          <tbody>
            {rows.length === 0 ? (
              <tr>
                <td colSpan={6} className="px-4 py-6 text-center text-zinc-500">
                  Нет записей. Добавьте строки таблицы выше.
                </td>
              </tr>
            ) : (
              rows.map((r) => (
                <tr key={r.id} className="border-b border-zinc-100 hover:bg-zinc-50">
                  <td className="px-4 py-2">{r.position}</td>
                  <td className="px-4 py-2 font-medium">{r.teamName}</td>
                  <td className="px-4 py-2">{r.wins}</td>
                  <td className="px-4 py-2">{r.losses}</td>
                  <td className="px-4 py-2">{r.pointsDiff >= 0 ? `+${r.pointsDiff}` : r.pointsDiff}</td>
                  <td className="px-4 py-2">
                    <button
                      type="button"
                      onClick={() => deleteRow(r.id)}
                      className="text-red-600 hover:underline"
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
