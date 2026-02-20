"use client";

import { useEffect, useState } from "react";
import { apiGet, apiPost, apiPut } from "@/lib/api";
import { apiUrl } from "@/lib/api";
import { getUserFacingError } from "@/lib/errors";
import type { MatchDto, MatchStatsDto, MatchAttendanceDto, ActionResult } from "@/lib/types";

const statusLabel: Record<string, string> = {
  SCHEDULED: "Запланирован",
  PLAYED: "Сыгран",
  COMPLETED: "Сыгран",
  CANCELLED: "Отменён",
};

export default function MatchesPage() {
  const [list, setList] = useState<MatchDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
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
  const [statsModalId, setStatsModalId] = useState<number | null>(null);
  const [statsData, setStatsData] = useState<MatchStatsDto | null>(null);
  const [statsLoading, setStatsLoading] = useState(false);
  const [statsSaving, setStatsSaving] = useState(false);
  const [statsEdit, setStatsEdit] = useState<MatchStatsDto | null>(null);
  const [attendanceModalId, setAttendanceModalId] = useState<number | null>(null);
  const [attendanceData, setAttendanceData] = useState<MatchAttendanceDto | null>(null);
  const [attendanceLoading, setAttendanceLoading] = useState(false);
  const [attendanceSaving, setAttendanceSaving] = useState<string | null>(null);

  function load() {
    setLoadError(null);
    setLoading(true);
    apiGet<MatchDto[]>("/api/admin/matches").then((res) => {
      setLoading(false);
      if (res.status === 0 || res.networkError) {
        setLoadError(getUserFacingError(0));
        return;
      }
      if (res.ok && Array.isArray(res.data)) setList(res.data);
    });
  }

  useEffect(() => {
    load();
  }, []);

  async function openStatsModal(matchId: number) {
    setStatsModalId(matchId);
    setStatsData(null);
    setStatsEdit(null);
    setStatsLoading(true);
    const res = await apiGet<MatchStatsDto>(`/api/admin/matches/${matchId}/stats`);
    setStatsLoading(false);
    if (res.ok && res.data) {
      setStatsData(res.data);
      setStatsEdit(JSON.parse(JSON.stringify(res.data)));
    }
  }

  async function openAttendanceModal(matchId: number) {
    setAttendanceModalId(matchId);
    setAttendanceData(null);
    setAttendanceLoading(true);
    const res = await apiGet<MatchAttendanceDto>(`/api/admin/matches/${matchId}/attendance`);
    setAttendanceLoading(false);
    if (res.ok && res.data) setAttendanceData(res.data);
  }

  async function setAttendanceNotComing(matchId: number, telegramUserId: string) {
    setAttendanceSaving(telegramUserId);
    const res = await apiPut<ActionResult>(`/api/admin/matches/${matchId}/attendance`, {
      telegramUserId,
      status: "NOT_COMING",
    });
    setAttendanceSaving(null);
    if (res.ok && res.data?.success) {
      if (attendanceModalId === matchId) openAttendanceModal(matchId);
    } else {
      setMessage({ type: "err", text: getUserFacingError(res.status, res.data?.data ?? res.error) });
    }
  }

  function updateStatsRow(playerId: number, field: keyof MatchStatsDto["stats"][0], value: number | boolean) {
    if (!statsEdit) return;
    setStatsEdit({
      ...statsEdit,
      stats: statsEdit.stats.map((r) =>
        r.playerId === playerId ? { ...r, [field]: value } : r
      ),
    });
  }

  async function saveStats() {
    if (statsModalId == null || !statsEdit) return;
    setStatsSaving(true);
    const res = await apiPost<ActionResult>(`/api/admin/matches/${statsModalId}/stats`, {
      stats: statsEdit.stats.map((r) => ({
        playerId: r.playerId,
        minutes: r.minutes ?? 0,
        points: r.points,
        rebounds: r.rebounds,
        assists: r.assists,
        fouls: r.fouls,
        plusMinus: r.plusMinus ?? undefined,
        mvp: r.mvp,
      })),
    });
    setStatsSaving(false);
    if (res.ok && res.data?.success) {
      setMessage({ type: "ok", text: "Статистика сохранена" });
      setStatsModalId(null);
      load();
    } else {
      setMessage({ type: "err", text: getUserFacingError(res.status, res.data?.data ?? res.error) });
    }
  }

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
        setMessage({ type: "err", text: getUserFacingError(res.status, res.data?.data ?? res.error) });
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
        setMessage({ type: "err", text: getUserFacingError(res.status, res.data?.data ?? res.error) });
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
      setMessage({ type: "err", text: getUserFacingError(res.status, res.data?.data ?? res.error) });
    }
  }

  async function cancelMatch(id: number) {
    if (!confirm("Отменить матч?")) return;
    const res = await apiPost<ActionResult>(`/api/admin/matches/${id}/cancel`, {});
    if (res.ok && res.data?.success) {
      setMessage({ type: "ok", text: res.data.message ?? "Матч отменён" });
      load();
    } else {
      setMessage({ type: "err", text: getUserFacingError(res.status, res.data?.data ?? res.error) });
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
      setMessage({ type: "err", text: getUserFacingError(res.status, res.data?.data ?? res.error) });
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
  if (loadError) {
    return (
      <div>
        <h1 className="mb-6 text-2xl font-semibold text-zinc-800">Матчи</h1>
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
                        onClick={() => openAttendanceModal(m.id)}
                        className="text-blue-600 hover:underline"
                      >
                        Подтверждения
                      </button>
                      {" · "}
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
                  {(m.status === "PLAYED" || m.status === "COMPLETED" || (m.ourScore != null && m.opponentScore != null)) && (
                    <>
                      <button
                        type="button"
                        onClick={() => openStatsModal(m.id)}
                        className="text-blue-600 hover:underline"
                      >
                        Статистика
                      </button>
                      {" · "}
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

      {attendanceModalId != null && (
        <div className="fixed inset-0 z-10 flex items-center justify-center bg-black/30 p-4">
          <div className="max-h-[90vh] w-full max-w-2xl overflow-auto rounded-xl bg-white p-6 shadow-lg">
            <h2 className="mb-4 text-lg font-medium text-zinc-800">Состав по подтверждениям</h2>
            {attendanceLoading ? (
              <p className="text-zinc-500">Загрузка…</p>
            ) : attendanceData ? (
              <>
                <div className="space-y-4 text-sm">
                  {(["COMING", "LATE", "NOT_COMING"] as const).map((status) => {
                    const rows = attendanceData.responded.filter((r) => r.status === status);
                    if (rows.length === 0) return null;
                    const labels = { COMING: "Буду", LATE: "Опоздаю", NOT_COMING: "Не смогу" };
                    return (
                      <div key={status}>
                        <h3 className="mb-1 font-medium text-zinc-600">{labels[status]} ({rows.length})</h3>
                        <ul className="list-inside list-disc space-y-0.5 text-zinc-800">
                          {rows.map((r) => (
                            <li key={r.telegramUserId} className="flex items-center justify-between gap-2">
                              <span>{r.displayName || r.telegramUsername || r.telegramUserId}</span>
                              {status !== "NOT_COMING" && (
                                <button
                                  type="button"
                                  onClick={() => setAttendanceNotComing(attendanceModalId, r.telegramUserId)}
                                  disabled={attendanceSaving === r.telegramUserId}
                                  className="text-amber-600 hover:underline disabled:opacity-50"
                                >
                                  {attendanceSaving === r.telegramUserId ? "…" : "Отменить участие"}
                                </button>
                              )}
                            </li>
                          ))}
                        </ul>
                      </div>
                    );
                  })}
                  {attendanceData.noResponse.length > 0 && (
                    <div>
                      <h3 className="mb-1 font-medium text-zinc-600">Не ответили ({attendanceData.noResponse.length})</h3>
                      <ul className="list-inside list-disc space-y-0.5 text-zinc-500">
                        {attendanceData.noResponse.map((r) => (
                          <li key={r.telegramUserId}>{r.displayName || r.telegramUsername || r.telegramUserId}</li>
                        ))}
                      </ul>
                    </div>
                  )}
                </div>
                <div className="mt-4 flex justify-end">
                  <button
                    type="button"
                    onClick={() => setAttendanceModalId(null)}
                    className="rounded-lg border border-zinc-300 px-4 py-2 hover:bg-zinc-50"
                  >
                    Закрыть
                  </button>
                </div>
              </>
            ) : (
              <p className="text-zinc-500">Не удалось загрузить состав</p>
            )}
          </div>
        </div>
      )}

      {statsModalId != null && (
        <div className="fixed inset-0 z-10 flex items-center justify-center bg-black/30 p-4">
          <div className="max-h-[90vh] w-full max-w-4xl overflow-auto rounded-xl bg-white p-6 shadow-lg">
            <h2 className="mb-4 text-lg font-medium text-zinc-800">Статистика игроков в матче</h2>
            {statsLoading ? (
              <p className="text-zinc-500">Загрузка…</p>
            ) : statsEdit ? (
              <>
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="border-b border-zinc-200 text-left text-zinc-600">
                        <th className="pb-2 pr-2">Игрок</th>
                        <th className="w-16 pb-2">Мин</th>
                        <th className="w-16 pb-2">Очк</th>
                        <th className="w-16 pb-2">Пд</th>
                        <th className="w-16 pb-2">Пдч</th>
                        <th className="w-16 pb-2">Фол</th>
                        <th className="w-16 pb-2">+/-</th>
                        <th className="w-16 pb-2">MVP</th>
                        <th className="pb-2">Карточка</th>
                      </tr>
                    </thead>
                    <tbody>
                      {statsEdit.stats.map((r) => (
                        <tr key={r.playerId} className="border-b border-zinc-100">
                          <td className="py-1 pr-2">
                            {r.playerName}
                            {r.number != null && <span className="text-zinc-500"> №{r.number}</span>}
                          </td>
                          <td className="py-1">
                            <input
                              type="number"
                              min={0}
                              className="w-14 rounded border border-zinc-300 px-1 py-0.5"
                              value={r.minutes ?? ""}
                              onChange={(e) => updateStatsRow(r.playerId, "minutes", e.target.value === "" ? 0 : parseInt(e.target.value, 10))}
                            />
                          </td>
                          <td className="py-1">
                            <input
                              type="number"
                              min={0}
                              className="w-14 rounded border border-zinc-300 px-1 py-0.5"
                              value={r.points}
                              onChange={(e) => updateStatsRow(r.playerId, "points", parseInt(e.target.value, 10) || 0)}
                            />
                          </td>
                          <td className="py-1">
                            <input
                              type="number"
                              min={0}
                              className="w-14 rounded border border-zinc-300 px-1 py-0.5"
                              value={r.rebounds}
                              onChange={(e) => updateStatsRow(r.playerId, "rebounds", parseInt(e.target.value, 10) || 0)}
                            />
                          </td>
                          <td className="py-1">
                            <input
                              type="number"
                              min={0}
                              className="w-14 rounded border border-zinc-300 px-1 py-0.5"
                              value={r.assists}
                              onChange={(e) => updateStatsRow(r.playerId, "assists", parseInt(e.target.value, 10) || 0)}
                            />
                          </td>
                          <td className="py-1">
                            <input
                              type="number"
                              min={0}
                              className="w-14 rounded border border-zinc-300 px-1 py-0.5"
                              value={r.fouls}
                              onChange={(e) => updateStatsRow(r.playerId, "fouls", parseInt(e.target.value, 10) || 0)}
                            />
                          </td>
                          <td className="py-1">
                            <input
                              type="number"
                              className="w-14 rounded border border-zinc-300 px-1 py-0.5"
                              value={r.plusMinus ?? ""}
                              onChange={(e) => updateStatsRow(r.playerId, "plusMinus", e.target.value === "" ? 0 : parseInt(e.target.value, 10))}
                            />
                          </td>
                          <td className="py-1">
                            <input
                              type="checkbox"
                              checked={r.mvp}
                              onChange={(e) => updateStatsRow(r.playerId, "mvp", e.target.checked)}
                              className="rounded border-zinc-300"
                            />
                          </td>
                          <td className="py-1">
                            <a
                              href={apiUrl(`/api/admin/matches/${statsModalId}/player-card?playerId=${r.playerId}`)}
                              target="_blank"
                              rel="noopener noreferrer"
                              className="text-blue-600 hover:underline"
                            >
                              Карточка
                            </a>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
                {statsEdit.mvpPlayerName && <p className="mt-2 text-sm text-zinc-500">Лучший игрок матча: {statsEdit.mvpPlayerName}</p>}
                <div className="mt-4 flex justify-end gap-2">
                  <button
                    type="button"
                    onClick={() => setStatsModalId(null)}
                    className="rounded-lg border border-zinc-300 px-4 py-2 hover:bg-zinc-50"
                  >
                    Закрыть
                  </button>
                  <button
                    type="button"
                    onClick={() => saveStats()}
                    disabled={statsSaving}
                    className="rounded-lg bg-blue-600 px-4 py-2 text-white hover:bg-blue-700 disabled:opacity-50"
                  >
                    {statsSaving ? "Сохранение…" : "Сохранить"}
                  </button>
                </div>
              </>
            ) : (
              <p className="text-zinc-500">Нет данных</p>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
