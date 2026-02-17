"use client";

import { useEffect, useState } from "react";
import { apiGet, apiPost, apiPut, apiDelete } from "@/lib/api";
import type { PlayerDto, ActionResult } from "@/lib/types";

export default function PlayersPage() {
  const [list, setList] = useState<PlayerDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState<{ type: "ok" | "err"; text: string } | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [editId, setEditId] = useState<number | null>(null);
  const [name, setName] = useState("");
  const [number, setNumber] = useState("");
  const [status, setStatus] = useState("ACTIVE");

  function load() {
    apiGet<PlayerDto[]>("/api/admin/players").then((res) => {
      setLoading(false);
      if (res.ok && Array.isArray(res.data)) setList(res.data);
    });
  }

  useEffect(() => {
    load();
  }, []);

  function showCreate() {
    setEditId(null);
    setName("");
    setNumber("");
    setStatus("ACTIVE");
    setShowForm(true);
  }

  function showEdit(p: PlayerDto) {
    setEditId(p.id);
    setName(p.name);
    setNumber(p.number != null ? String(p.number) : "");
    setStatus(p.status);
    setShowForm(true);
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setMessage(null);
    const num = number.trim() ? parseInt(number, 10) : null;
    if (num !== null && isNaN(num)) {
      setMessage({ type: "err", text: "Номер — число" });
      return;
    }
    if (editId != null) {
      const res = await apiPut<ActionResult>(
        `/api/admin/players/${editId}`,
        { name: name.trim(), number: num, status }
      );
      if (res.ok && res.data?.success) {
        setMessage({ type: "ok", text: res.data.message ?? "Сохранено" });
        setShowForm(false);
        load();
      } else {
        setMessage({ type: "err", text: res.data?.data ?? res.error ?? "Ошибка" });
      }
    } else {
      const res = await apiPost<ActionResult>(
        "/api/admin/players",
        { name: name.trim(), number: num }
      );
      if (res.ok && res.data?.success) {
        setMessage({ type: "ok", text: res.data.message ?? "Игрок добавлен" });
        setShowForm(false);
        load();
      } else {
        setMessage({ type: "err", text: res.data?.data ?? res.error ?? "Ошибка" });
      }
    }
  }

  async function remove(id: number) {
    if (!confirm("Удалить игрока?")) return;
    const res = await apiDelete(`/api/admin/players/${id}`);
    if (res.ok) {
      setMessage({ type: "ok", text: "Удалено" });
      load();
    } else {
      setMessage({ type: "err", text: res.error ?? "Ошибка" });
    }
  }

  if (loading) return <div className="text-zinc-500">Загрузка…</div>;

  return (
    <div>
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-2xl font-semibold text-zinc-800">Состав</h1>
        <button
          type="button"
          onClick={showCreate}
          className="rounded-lg bg-blue-600 px-4 py-2 text-white hover:bg-blue-700"
        >
          Добавить игрока
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
        <form
          onSubmit={submit}
          className="mb-6 rounded-xl border border-zinc-200 bg-white p-4 shadow-sm"
        >
          <h2 className="mb-4 text-lg font-medium">
            {editId != null ? "Редактировать" : "Новый игрок"}
          </h2>
          <div className="flex flex-col gap-4 sm:flex-row sm:items-end">
            <div>
              <label className="mb-1 block text-sm text-zinc-600">Имя</label>
              <input
                type="text"
                value={name}
                onChange={(e) => setName(e.target.value)}
                className="w-full rounded-lg border border-zinc-300 px-3 py-2 sm:w-48"
                required
              />
            </div>
            <div>
              <label className="mb-1 block text-sm text-zinc-600">Номер</label>
              <input
                type="text"
                value={number}
                onChange={(e) => setNumber(e.target.value)}
                className="w-full rounded-lg border border-zinc-300 px-3 py-2 sm:w-24"
                placeholder="—"
              />
            </div>
            {editId != null && (
              <div>
                <label className="mb-1 block text-sm text-zinc-600">Статус</label>
                <select
                  value={status}
                  onChange={(e) => setStatus(e.target.value)}
                  className="rounded-lg border border-zinc-300 px-3 py-2"
                >
                  <option value="ACTIVE">Активный</option>
                  <option value="INACTIVE">Неактивный</option>
                </select>
              </div>
            )}
            <div className="flex gap-2">
              <button
                type="submit"
                className="rounded-lg bg-blue-600 px-4 py-2 text-white hover:bg-blue-700"
              >
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
      <div className="overflow-hidden rounded-xl border border-zinc-200 bg-white shadow-sm">
        <table className="w-full">
          <thead className="bg-zinc-50">
            <tr>
              <th className="px-4 py-2 text-left text-sm font-medium text-zinc-600">
                Имя
              </th>
              <th className="px-4 py-2 text-left text-sm font-medium text-zinc-600">
                Номер
              </th>
              <th className="px-4 py-2 text-left text-sm font-medium text-zinc-600">
                Статус
              </th>
              <th className="px-4 py-2 text-left text-sm font-medium text-zinc-600">
                Долг
              </th>
              <th className="px-4 py-2" />
            </tr>
          </thead>
          <tbody>
            {list.map((p) => (
              <tr key={p.id} className="border-t border-zinc-100">
                <td className="px-4 py-2">{p.name}</td>
                <td className="px-4 py-2">{p.number ?? "—"}</td>
                <td className="px-4 py-2">{p.status === "ACTIVE" ? "Активный" : "Неактивный"}</td>
                <td className="px-4 py-2">{p.debt ? `${p.debt} ₽` : "—"}</td>
                <td className="px-4 py-2">
                  <button
                    type="button"
                    onClick={() => showEdit(p)}
                    className="text-blue-600 hover:underline"
                  >
                    Изменить
                  </button>
                  {" · "}
                  <button
                    type="button"
                    onClick={() => remove(p.id)}
                    className="text-red-600 hover:underline"
                  >
                    Удалить
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
