"use client";

import { useEffect, useState } from "react";
import { apiGet, apiPost, apiDelete } from "@/lib/api";
import { getUserFacingError } from "@/lib/errors";
import type { FinanceEntryDto, FinanceReportDto, ActionResult } from "@/lib/types";

function num(v: number | string): number {
  if (typeof v === "number") return v;
  return parseFloat(String(v)) || 0;
}

export default function FinancePage() {
  const [entries, setEntries] = useState<FinanceEntryDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [message, setMessage] = useState<{ type: "ok" | "err"; text: string } | null>(null);
  const [form, setForm] = useState({ type: "INCOME", amount: "", description: "", entryDate: "" });
  const [report, setReport] = useState<FinanceReportDto | null>(null);
  const [reportFrom, setReportFrom] = useState("");
  const [reportTo, setReportTo] = useState("");

  function load() {
    setLoadError(null);
    setLoading(true);
    apiGet<FinanceEntryDto[]>("/api/admin/finance").then((res) => {
      setLoading(false);
      if (res.status === 0 || res.networkError) {
        setLoadError(getUserFacingError(0));
        return;
      }
      if (res.ok && Array.isArray(res.data)) setEntries(res.data);
    });
  }

  useEffect(() => {
    load();
    const today = new Date().toISOString().slice(0, 10);
    const firstDay = new Date(new Date().getFullYear(), new Date().getMonth(), 1).toISOString().slice(0, 10);
    setReportFrom(firstDay);
    setReportTo(today);
    setForm((f) => ({ ...f, entryDate: today }));
  }, []);

  async function submitEntry(e: React.FormEvent) {
    e.preventDefault();
    setMessage(null);
    const amount = form.amount.trim() ? parseFloat(form.amount.replace(",", ".")) : 0;
    const res = await apiPost<ActionResult>("/api/admin/finance", {
      type: form.type,
      amount,
      description: form.description.trim() || null,
      entryDate: form.entryDate || new Date().toISOString().slice(0, 10),
    });
    if (res.ok && res.data?.success) {
      setMessage({ type: "ok", text: res.data.message ?? "Запись добавлена" });
      setForm((f) => ({ ...f, amount: "", description: "" }));
      load();
    } else {
      setMessage({ type: "err", text: getUserFacingError(res.status, res.data?.data ?? res.error) });
    }
  }

  async function deleteEntry(id: number) {
    if (!window.confirm("Удалить запись?")) return;
    setMessage(null);
    const res = await apiDelete<ActionResult>(`/api/admin/finance/${id}`);
    if (res.ok && res.data?.success) {
      setMessage({ type: "ok", text: "Запись удалена" });
      load();
      if (report) setReport(null);
    } else {
      setMessage({ type: "err", text: getUserFacingError(res.status, res.data?.data ?? res.error) });
    }
  }

  async function loadReport() {
    setMessage(null);
    const from = reportFrom || new Date(new Date().getFullYear(), new Date().getMonth(), 1).toISOString().slice(0, 10);
    const to = reportTo || new Date().toISOString().slice(0, 10);
    const res = await apiGet<FinanceReportDto>(`/api/admin/finance/report?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`);
    if (res.ok && res.data) {
      setReport(res.data);
    } else {
      setMessage({ type: "err", text: getUserFacingError(res.status, res.error) });
    }
  }

  if (loading) return <div className="text-zinc-500">Загрузка…</div>;
  if (loadError) {
    return (
      <div>
        <h1 className="mb-6 text-2xl font-semibold text-zinc-800">Финансы</h1>
        <p className="mb-4 rounded-lg bg-amber-100 px-3 py-2 text-zinc-800">{loadError}</p>
        <button type="button" onClick={() => load()} className="rounded-lg bg-blue-600 px-4 py-2 text-white hover:bg-blue-700">
          Повторить
        </button>
      </div>
    );
  }

  return (
    <div>
      <h1 className="mb-6 text-2xl font-semibold text-zinc-800">Финансы</h1>
      {message && (
        <p className={`mb-4 rounded-lg px-3 py-2 text-sm ${message.type === "ok" ? "bg-green-100 text-green-800" : "bg-red-100 text-red-800"}`}>
          {message.text}
        </p>
      )}

      <form onSubmit={submitEntry} className="mb-6 rounded-xl border border-zinc-200 bg-white p-4 shadow-sm">
        <h2 className="mb-4 text-lg font-medium text-zinc-800">Приход / расход</h2>
        <div className="flex flex-wrap gap-4">
          <div>
            <label className="mb-1 block text-sm text-zinc-600">Тип</label>
            <select
              value={form.type}
              onChange={(e) => setForm((f) => ({ ...f, type: e.target.value }))}
              className="rounded-lg border border-zinc-300 px-3 py-2"
            >
              <option value="INCOME">Приход</option>
              <option value="EXPENSE">Расход</option>
            </select>
          </div>
          <div>
            <label className="mb-1 block text-sm text-zinc-600">Сумма (₽)</label>
            <input
              type="text"
              inputMode="decimal"
              value={form.amount}
              onChange={(e) => setForm((f) => ({ ...f, amount: e.target.value }))}
              className="w-32 rounded-lg border border-zinc-300 px-3 py-2"
              required
            />
          </div>
          <div className="min-w-[200px] flex-1">
            <label className="mb-1 block text-sm text-zinc-600">Описание</label>
            <input
              type="text"
              value={form.description}
              onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
              className="w-full rounded-lg border border-zinc-300 px-3 py-2"
              placeholder="Например: взносы за март"
            />
          </div>
          <div>
            <label className="mb-1 block text-sm text-zinc-600">Дата</label>
            <input
              type="date"
              value={form.entryDate}
              onChange={(e) => setForm((f) => ({ ...f, entryDate: e.target.value }))}
              className="rounded-lg border border-zinc-300 px-3 py-2"
            />
          </div>
          <div className="flex items-end">
            <button type="submit" className="rounded-lg bg-blue-600 px-4 py-2 text-white hover:bg-blue-700">
              Добавить
            </button>
          </div>
        </div>
      </form>

      <div className="mb-6 flex flex-wrap items-center gap-4">
        <span className="text-sm font-medium text-zinc-600">Отчёт за период:</span>
        <input
          type="date"
          value={reportFrom}
          onChange={(e) => setReportFrom(e.target.value)}
          className="rounded-lg border border-zinc-300 px-3 py-2 text-sm"
        />
        <input
          type="date"
          value={reportTo}
          onChange={(e) => setReportTo(e.target.value)}
          className="rounded-lg border border-zinc-300 px-3 py-2 text-sm"
        />
        <button
          type="button"
          onClick={() => loadReport()}
          className="rounded-lg bg-emerald-600 px-4 py-2 text-white hover:bg-emerald-700"
        >
          Сформировать отчёт
        </button>
      </div>

      {report && (
        <div className="mb-6 rounded-xl border border-zinc-200 bg-white p-4 shadow-sm">
          <h2 className="mb-3 text-lg font-medium text-zinc-800">
            Отчёт за {report.from} — {report.to}
          </h2>
          <div className="mb-4 grid grid-cols-3 gap-4 text-sm">
            <div className="rounded-lg bg-green-50 p-3">
              <span className="text-zinc-600">Приход</span>
              <p className="text-lg font-semibold text-green-800">{num(report.totalIncome)} ₽</p>
            </div>
            <div className="rounded-lg bg-red-50 p-3">
              <span className="text-zinc-600">Расход</span>
              <p className="text-lg font-semibold text-red-800">{num(report.totalExpense)} ₽</p>
            </div>
            <div className="rounded-lg bg-zinc-100 p-3">
              <span className="text-zinc-600">Баланс</span>
              <p className="text-lg font-semibold text-zinc-800">{num(report.balance)} ₽</p>
            </div>
          </div>
          {report.entries.length > 0 && (
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-zinc-200 text-left text-zinc-600">
                  <th className="pb-2">Дата</th>
                  <th className="pb-2">Тип</th>
                  <th className="pb-2">Сумма</th>
                  <th className="pb-2">Описание</th>
                </tr>
              </thead>
              <tbody>
                {report.entries.map((e) => (
                  <tr key={e.id} className="border-b border-zinc-100">
                    <td className="py-2">{e.entryDate}</td>
                    <td className="py-2">{e.type === "INCOME" ? "Приход" : "Расход"}</td>
                    <td className="py-2 font-medium">{num(e.amount)} ₽</td>
                    <td className="py-2 text-zinc-600">{e.description ?? "—"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}

      <div className="rounded-xl border border-zinc-200 bg-white shadow-sm">
        <h2 className="border-b border-zinc-200 px-4 py-3 text-lg font-medium text-zinc-800">Все записи</h2>
        {entries.length === 0 ? (
          <p className="p-4 text-zinc-500">Нет записей. Добавьте приход или расход выше.</p>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-zinc-200 bg-zinc-50 text-left text-zinc-600">
                <th className="px-4 py-2">Дата</th>
                <th className="px-4 py-2">Тип</th>
                <th className="px-4 py-2">Сумма</th>
                <th className="px-4 py-2">Описание</th>
                <th className="px-4 py-2"></th>
              </tr>
            </thead>
            <tbody>
              {entries.map((e) => (
                <tr key={e.id} className="border-b border-zinc-100">
                  <td className="px-4 py-2">{e.entryDate}</td>
                  <td className="px-4 py-2">{e.type === "INCOME" ? "Приход" : "Расход"}</td>
                  <td className="px-4 py-2 font-medium">{num(e.amount)} ₽</td>
                  <td className="px-4 py-2 text-zinc-600">{e.description ?? "—"}</td>
                  <td className="px-4 py-2">
                    <button
                      type="button"
                      onClick={() => deleteEntry(e.id)}
                      className="text-red-600 hover:underline"
                    >
                      Удалить
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
