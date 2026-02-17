"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { apiGet } from "@/lib/api";
import type { DashboardDto } from "@/lib/types";

function formatDate(s: string | null) {
  if (!s) return "—";
  try {
    const d = new Date(s);
    return d.toLocaleString("ru-RU", {
      day: "2-digit",
      month: "2-digit",
      year: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    });
  } catch {
    return s;
  }
}

export default function DashboardPage() {
  const [data, setData] = useState<DashboardDto | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    apiGet<DashboardDto>("/api/admin/dashboard").then((res) => {
      setLoading(false);
      if (res.ok && res.data) setData(res.data);
    });
  }, []);

  if (loading) return <div className="text-zinc-500">Загрузка…</div>;
  if (!data) return <div className="text-zinc-600">Нет данных</div>;

  const next = data.nextMatch;

  return (
    <div>
      <h1 className="mb-6 text-2xl font-semibold text-zinc-800">Дашборд</h1>
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <div className="rounded-xl border border-zinc-200 bg-white p-4 shadow-sm">
          <p className="text-sm text-zinc-500">Игроков в команде</p>
          <p className="text-2xl font-semibold text-zinc-800">
            {data.playerCount}
          </p>
        </div>
        <div className="rounded-xl border border-zinc-200 bg-white p-4 shadow-sm">
          <p className="text-sm text-zinc-500">Должников</p>
          <p className="text-2xl font-semibold text-zinc-800">
            {data.debtorCount}
          </p>
        </div>
        <div className="rounded-xl border border-zinc-200 bg-white p-4 shadow-sm">
          <p className="text-sm text-zinc-500">Сумма долгов</p>
          <p className="text-2xl font-semibold text-zinc-800">
            {data.totalDebt.toFixed(0)} ₽
          </p>
        </div>
      </div>
      {next && (
        <div className="mt-6 rounded-xl border border-zinc-200 bg-white p-4 shadow-sm">
          <h2 className="mb-2 text-lg font-medium text-zinc-800">
            Ближайший матч
          </h2>
          <p className="text-zinc-600">
            {next.opponent} — {formatDate(next.date)}
          </p>
          <p className="mt-1 text-sm text-zinc-500">{next.location ?? "—"}</p>
          <Link
            href="/matches"
            className="mt-2 inline-block text-blue-600 hover:underline"
          >
            К матчам →
          </Link>
        </div>
      )}
    </div>
  );
}
