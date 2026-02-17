"use client";

import { useEffect, useState } from "react";
import { apiGet, apiPost, apiPut } from "@/lib/api";
import { apiUrl } from "@/lib/api";
import type { MatchDto, ActionResult } from "@/lib/types";

const statusLabel: Record<string, string> = {
  SCHEDULED: "Запланирован",
  PLAYED: "Сыгран",
  CANCELLED: "Отменён",
};

export default function MatchesPage() {
  const [list, setList] = useState<MatchDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState<{ type: "ok" | "err"; text: string } | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [editId, setEditId] = useState<number | null>(null);
  const [opponent, setOpponent] = useState("");
  const [dateTime, setDateTime] = useState("");
  const [location, setLocation] = useState("");
  const [resultId, setResultId] = useState<number | null>(null);
  const [resultOur, setResultOur] = useState("");
  const [resultOpp, setResultOpp] = useState("");
  const [sendingId, setSendingId] = useState<number | null>(null);

  function load() {
    apiGet<MatchDto[]>("/api/admin/matches").then((res) => {
      setLoading(false);
      if (res.ok && Array.isArray(res.data)) setList(res.data);
    });
  }

  useEffect(() => {
    load();
  }, []);

  function dateInputValue(s: string | null) {
    if (!s) return "";
    try {
      return new Date(s).toISOString().slice(0, 16);
    } catch {
      return "";
    }
  }

  async function submitMatch(e: React.FormEvent) {
    e.preventDefault();
    setMessage(null);
    const [datePart, timePart] = dateTime ? dateTime.replace("T", " ").split(" ") : ["", ""];
    const date = datePart || "";
    const time = timePart || "12:00";
    if (editId != null) {
      const res = await apiPut<ActionResult>(`/api/admin/matches/${editId}`, {
        opponent: opponent.trim(),
        date,
        time,
        location: location.trim(),
      });
      if (res.ok && res.data?.success) {
        setMessage({ type: "ok", text: res.data.message ?? "Сохранено" });
        setShowForm(false);
        load();
      } else {
        setMessage({ type: "err", text: res.data?.data ?? res.error ?? "Ошибка" });
      }
    } else {
      const res = await apiPost<ActionResult>("/api/admin/matches", {
        opponent: opponent.trim(),
        date,
        time,
        location: location.trim(),
      });
      if (res.ok && res.data?.success) {
        setMessage({ type: "ok", text: res.data.message ?? "Матч создан" });
        setShowForm(false);
        load();
      } else {
        setMessage({ type: "err", text: res.data?.data ?? res.error ?? "Ошибка" });
      }
    }
  }

  async function submitResult(e: React.FormEvent) {
    e.preventDefault();
    if (resultId == null) return;
    const our = parseInt(resultOur, 10);
    const opp = parseInt(resultOpp, 10);
    if (isNaN(our) || isNaN(opp)) {
      setMessage({ type: "err", text: "Введите счёт числами" });
      return;
    }
    const res = await apiPost<ActionResult>(`/api/admin/matches/${resultId}/result`, {
      ourScore: our,
      opponentScore: opp,
    });
    if (res.ok && res.data?.success) {
      setMessage({ type: "ok", text: res.data.message ?? "Результат сохранён" });
      setResultId(null);
      load();
    } else {
      setMessage({ type: "err", text: res.data?.data ?? res.error ?? "Ошибка" });
    }
  }

  async function cancelMatch(id: number) {
    if (!confirm("Отменить матч?")) return;
    const res = await apiPost<ActionResult>(`/api/admin/matches/${id}/cancel`, {});
    if (res.ok && res.data?.success) {
      setMessage({ type: "ok", text: res.data.message ?? "Матч отменён" });
      load();
    } else {
      setMessage({ type: "err", text: res.data?.data ?? res.error ?? "Ошибка" });
    }
  }

  async function sendToChannel(id: number) {
    setSendingId(id);
    setMessage(null);
    const res = await apiPost<ActionResult>(`/api/admin/matches/${id}/send-to-channel`, {});
    setSendingId(null);
    if (res.ok && res.data?.success) {
      setMessage({ type: "ok", text: res.data.message ?? "Опубликовано" });
    } else {
      setMessage({ type: "err", text: res.data?.data ?? res.error ?? "Ошибка" });
    }
  }

  async function downloadCard(id: number) {
    try {
      const r = await fetch(apiUrl(`/api/admin/matches/${id}/card`), { credentials: "include" });
      if (!r.ok) return;
      const blob = await r.blob();
      const url = URL.createObjectURL(blob);
      window.open(url, "_blank");
      URL.revokeObjectURL(url);
    } catch {
      setMessage({ type: "err", text: "Не удалось загрузить карточку" });
    }
  }

  if (loading) return <div className="text-zinc-500">Загрузка…</div>;

  return (
    <div>
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-2xl font-semibold text-zinc-800">Матчи</h1>
        <button
          type="button"
          onClick={() => {
            setEditId(null);
            setOpponent("");
            setDateTime("");
            setLocation("");
            setShowForm(true);
          }}
          className="rounded-lg bg-blue-600 px-4 py-2 text-white hover:bg-blue-700"
        >
          Добавить матч
        </button>
      </div>
      {message && (
        <p
          className={`mb-4 rounded-lg px-3 py-2 text-sm ${message.type === "ok" ? "bg-green-100 text-green-800" : "bg-red-100 text-red-800"}`}
        >
          {message.text}
        </p>
      )}
      {showForm && (
        <form onSubmit={submitMatch} className="mb-6 rounded-xl border border-zinc-200 bg-white p-4 shadow-sm">
          <h2 className="mb-4 text-lg font-medium">
            {editId != null ? "Редактировать матч" : "Новый матч"}
          </h2>
          <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:flex-wrap">
            <div>
              <label className="mb-1 block text-sm text-zinc-600">Соперник</label>
              <input
                type="text"
                value={opponent}
                onChange={(e) => setOpponent(e.target.value)}
                className="w-full rounded-lg border border-zinc-300 px-3 py-2 sm:w-48"
                required
              />
            </div>
            <div>
              <label className="mb-1 block text-sm text-zinc-600">Дата и время</label>
              <input
                type="datetime-local"
                value={dateTime}
                onChange={(e) => setDateTime(e.target.value)}
                className="rounded-lg border border-zinc-300 px-3 py-2"
              />
            </div>
            <div>
              <label className="mb-1 block text-sm text-zinc-600">Место</label>
              <input
                type="text"
                value={location}
                onChange={(e) => setLocation(e.target.value)}
                className="w-full rounded-lg border border-zinc-300 px-3 py-2 sm:w-48"
              />
            </div>
            <div className="flex gap-2">
              <button type="submit" className="rounded-lg bg-blue-600 px-4 py-2 text-white hover:bg-blue-700">
                Сохранить
              </button>
              <button
                type="button"
                onClick={() => setShowForm(false)}
                className="rounded-lg border border-zinc-300 px-4 py-2 hover:bg-zinc-50"
              >
                Отмена
              </button>
            </div>
          </div>
        </form>
      )}
      {resultId != null && (
        <form onSubmit={submitResult} className="mb-6 rounded-xl border border-zinc-200 bg-white p-4 shadow-sm">
          <h2 className="mb-4 text-lg font-medium">Результат матча</h2>
          <div className="flex flex-wrap items-end gap-4">
            <div>
              <label className="mb-1 block text-sm text-zinc-600">Наши очки</label>
              <input
                type="number"
                min={0}
                value={resultOur}
                onChange={(e) => setResultOur(e.target.value)}
                className="w-20 rounded-lg border border-zinc-300 px-3 py-2"
              />
            </div>
            <div>
              <label className="mb-1 block text-sm text-zinc-600">Очки соперника</label>
              <input
                type="number"
                min={0}
                value={resultOpp}
                onChange={(e) => setResultOpp(e.target.value)}
                className="w-20 rounded-lg border border-zinc-300 px-3 py-2"
              />
            </div>
            <div className="flex gap-2">
              <button type="submit" className="rounded-lg bg-blue-600 px-4 py-2 text-white hover:bg-blue-700">
                Сохранить результат
              </button>
              <button type="button" onClick={() => setResultId(null)} className="rounded-lg border border-zinc-300 px-4 py-2 hover:bg-zinc-50">
                Отмена
              </button>
            </div>
          </div>
        </form>
      )}
      <div className="overflow-hidden rounded-xl border border-zinc-200 bg-white shadow-sm">
        <table className="w-full">
          <thead className="bg-zinc-50">
            <tr>
              <th className="px-4 py-2 text-left text-sm font-medium text-zinc-600">Соперник</th>
              <th className="px-4 py-2 text-left text-sm font-medium text-zinc-600">Дата</th>
              <th className="px-4 py-2 text-left text-sm font-medium text-zinc-600">Счёт</th>
              <th className="px-4 py-2 text-left text-sm font-medium text-zinc-600">Статус</th>
              <th className="px-4 py-2" />
            </tr>
          </thead>
          <tbody>
            {list.map((m) => (
              <tr key={m.id} className="border-t border-zinc-100">
                <td className="px-4 py-2">{m.opponent}</td>
                <td className="px-4 py-2">{m.date ?? "—"}</td>
                <td className="px-4 py-2">
                  {m.ourScore != null && m.opponentScore != null ? `${m.ourScore} : ${m.opponentScore}` : "—"}
                </td>
                <td className="px-4 py-2">{statusLabel[m.status] ?? m.status}</td>
                <td className="px-4 py-2">
                  {m.status === "SCHEDULED" && (
                    <>
                      <button
                        type="button"
                        onClick={() => {
                          setEditId(m.id);
                          setOpponent(m.opponent ?? "");
                          setDateTime(dateInputValue(m.date));
                          setLocation(m.location ?? "");
                          setShowForm(true);
                        }}
                        className="text-zinc-600 hover:underline"
                      >
                        Изменить
                      </button>
                      {" · "}
                      <button
                        type="button"
                        onClick={() => {
                          setResultId(m.id);
                          setResultOur(m.ourScore != null ? String(m.ourScore) : "");
                          setResultOpp(m.opponentScore != null ? String(m.opponentScore) : "");
                        }}
                        className="text-blue-600 hover:underline"
                      >
                        Результат
                      </button>
                      {" · "}
                      <button type="button" onClick={() => cancelMatch(m.id)} className="text-zinc-600 hover:underline">
                        Отменить
                      </button>
                    </>
                  )}
                  {m.status === "PLAYED" && (
                    <>
                      <button
                        type="button"
                        onClick={() => downloadCard(m.id)}
                        className="text-blue-600 hover:underline"
                      >
                        Карточка
                      </button>
                      {" · "}
                      <button
                        type="button"
                        onClick={() => sendToChannel(m.id)}
                        disabled={sendingId !== null}
                        className="text-blue-600 hover:underline disabled:opacity-50"
                      >
                        {sendingId === m.id ? "Отправка…" : "В канал"}
                      </button>
                    </>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
