"use client";

import { useEffect, useState } from "react";
import { apiGet, apiPost } from "@/lib/api";
import { getUserFacingError } from "@/lib/errors";
import type { DebtListDto, ActionResult, PlayerDto } from "@/lib/types";

export default function DebtPage() {
  const [data, setData] = useState<DebtListDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [message, setMessage] = useState<{ type: "ok" | "err"; text: string } | null>(null);
  const [playerName, setPlayerName] = useState("");
  const [amount, setAmount] = useState("");
  const [notifyLoading, setNotifyLoading] = useState(false);

  function load() {
    setLoadError(null);
    setLoading(true);
    apiGet<DebtListDto>("/api/admin/debt").then((res) => {
      setLoading(false);
      if (res.status === 0 || res.networkError) {
        setLoadError(getUserFacingError(0));
        return;
      }
      if (res.ok && res.data) setData(res.data);
    });
  }

  useEffect(() => {
    load();
  }, []);

  async function setDebt(e: React.FormEvent) {
    e.preventDefault();
    setMessage(null);
    const res = await apiPost<ActionResult>("/api/admin/debt", {
      playerName: playerName.trim(),
      amount: amount.trim(),
    });
    if (res.ok && res.data && res.data.success) {
      setMessage({ type: "ok", text: res.data.message ?? "Долг выставлен" });
      setPlayerName("");
      setAmount("");
      load();
    } else {
      setMessage({ type: "err", text: getUserFacingError(res.status, res.data?.data ?? res.error) });
    }
  }

  async function notifyDebt() {
    setMessage(null);
    setNotifyLoading(true);
    const res = await apiPost<ActionResult>("/api/admin/notify-debt", {});
    setNotifyLoading(false);
    if (res.ok && res.data) {
      setMessage({ type: res.data.success ? "ok" : "err", text: res.data.message ?? res.data.data ?? "" });
      if (res.data.success) load();
    } else {
      setMessage({ type: "err", text: getUserFacingError(res.status, res.data?.data ?? res.error) });
    }
  }

  async function markPaid(playerId: number) {
    setMessage(null);
    const res = await apiPost<ActionResult>(`/api/admin/debt/paid/${playerId}`, {});
    if (res.ok && res.data && res.data.success) {
      setMessage({ type: "ok", text: res.data.message ?? "Оплата отмечена" });
      load();
    } else {
      setMessage({ type: "err", text: getUserFacingError(res.status, res.data?.data ?? res.error) });
    }
  }

  if (loading) return <div className="text-zinc-500">Загрузка…</div>;
  if (loadError) {
    return (
      <div>
        <h1 className="mb-6 text-2xl font-semibold text-zinc-800">Долги</h1>
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

  const debtors = data?.debtors ?? [];

  return (
    <div>
      <h1 className="mb-6 text-2xl font-semibold text-zinc-800">Долги</h1>
      {message && (
        <p
          className={`mb-4 rounded-lg px-3 py-2 text-sm ${message.type === "ok" ? "bg-green-100 text-green-800" : "bg-red-100 text-red-800"}`}
        >
          {message.text}
        </p>
      )}
      <form
        onSubmit={setDebt}
        className="mb-6 rounded-xl border border-zinc-200 bg-white p-4 shadow-sm"
      >
        <h2 className="mb-4 text-lg font-medium">Выставить долг</h2>
        <div className="flex flex-col gap-4 sm:flex-row sm:items-end">
          <div>
            <label className="mb-1 block text-sm text-zinc-600">Игрок</label>
            <input
              type="text"
              value={playerName}
              onChange={(e) => setPlayerName(e.target.value)}
              className="w-full rounded-lg border border-zinc-300 px-3 py-2 sm:w-48"
              required
            />
          </div>
          <div>
            <label className="mb-1 block text-sm text-zinc-600">Сумма (₽)</label>
            <input
              type="text"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              className="w-full rounded-lg border border-zinc-300 px-3 py-2 sm:w-32"
              placeholder="0"
            />
          </div>
          <button
            type="submit"
            className="rounded-lg bg-blue-600 px-4 py-2 text-white hover:bg-blue-700"
          >
            Выставить
          </button>
        </div>
      </form>
      <div className="rounded-xl border border-zinc-200 bg-white shadow-sm">
        <div className="flex flex-wrap items-center justify-between gap-4 border-b border-zinc-200 px-4 py-3">
          <h2 className="text-lg font-medium text-zinc-800">
            Должники ({debtors.length})
          </h2>
          {debtors.length > 0 && (
            <button
              type="button"
              onClick={() => notifyDebt()}
              disabled={notifyLoading}
              className="rounded-lg bg-amber-500 px-4 py-2 text-white hover:bg-amber-600 disabled:opacity-50"
            >
              {notifyLoading ? "Отправка…" : "Напомнить о взносе"}
            </button>
          )}
        </div>
        {debtors.length === 0 ? (
          <p className="p-4 text-zinc-500">Нет должников</p>
        ) : (
          <ul className="divide-y divide-zinc-100">
            {debtors.map((p: PlayerDto) => (
              <li key={p.id} className="flex items-center justify-between px-4 py-3">
                <span>
                  {p.name}
                  {p.number != null && <span className="ml-2 text-zinc-500">№{p.number}</span>}
                </span>
                <span className="font-medium">
                  {typeof p.debt === "number" ? p.debt : Number(p.debt)} ₽
                </span>
                <button
                  type="button"
                  onClick={() => markPaid(p.id)}
                  className="rounded-lg bg-green-600 px-3 py-1 text-sm text-white hover:bg-green-700"
                >
                  Оплатил
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
