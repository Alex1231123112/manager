"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { apiGet, apiPost } from "@/lib/api";
import type { MeResponse, TeamDto } from "@/lib/types";

export default function TeamSelectPage() {
  const router = useRouter();
  const [me, setMe] = useState<MeResponse | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    apiGet<MeResponse>("/api/admin/me").then((res) => {
      setLoading(false);
      if (!res.ok || res.status === 401) {
        router.replace("/login");
        return;
      }
      if (res.data) setMe(res.data);
    });
  }, [router]);

  async function selectTeam(teamId: number) {
    const res = await apiPost<{ success: boolean }>(
      "/api/admin/team-select",
      { teamId }
    );
    if (res.ok && res.data?.success) {
      router.push("/dashboard");
      router.refresh();
    }
  }

  if (loading) return <div className="text-zinc-500">Загрузка…</div>;
  if (!me) return null;

  return (
    <div>
      <h1 className="mb-4 text-xl font-semibold text-zinc-800">
        Выберите команду
      </h1>
      <div className="flex flex-col gap-2">
        {me.teams.map((t: TeamDto) => (
          <button
            key={t.id}
            onClick={() => selectTeam(t.id)}
            className="rounded-lg border border-zinc-300 bg-white px-4 py-3 text-left hover:bg-zinc-50"
          >
            {t.name}
          </button>
        ))}
      </div>
      <p className="mt-4">
        <Link href="/dashboard" className="text-blue-600 hover:underline">
          Назад к дашборду
        </Link>
      </p>
    </div>
  );
}
