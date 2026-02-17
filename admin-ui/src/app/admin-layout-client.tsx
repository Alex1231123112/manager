"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { apiGet, apiPost } from "@/lib/api";
import type { MeResponse, TeamDto } from "@/lib/types";

const nav = [
  { href: "/dashboard", label: "Дашборд" },
  { href: "/players", label: "Состав" },
  { href: "/matches", label: "Матчи" },
  { href: "/debt", label: "Долги" },
  { href: "/members", label: "Участники" },
  { href: "/invitations", label: "Приглашения" },
  { href: "/settings", label: "Настройки" },
];

export default function AdminLayoutClient({
  children,
}: {
  children: React.ReactNode;
}) {
  const pathname = usePathname();
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
    const res = await apiPost<{ success: boolean; error?: string }>(
      "/api/admin/team-select",
      { teamId }
    );
    if (res.ok && res.data?.success) {
      router.refresh();
      window.location.href = "/dashboard";
    }
  }

  async function logout() {
    await apiPost("/api/admin/logout", {});
    router.replace("/login");
    router.refresh();
  }

  if (loading) {
    return (
      <div className="flex min-h-screen items-center justify-center text-zinc-500">
        Загрузка…
      </div>
    );
  }

  if (me && !me.currentTeam && me.teams.length > 0 && pathname !== "/team-select") {
    return (
      <div className="flex min-h-screen flex-col items-center justify-center gap-4 p-4">
        <h1 className="text-xl font-semibold text-zinc-800">Выберите команду</h1>
        <div className="flex flex-col gap-2">
          {me.teams.map((t: TeamDto) => (
            <button
              key={t.id}
              onClick={() => selectTeam(t.id)}
              className="rounded-lg border border-zinc-300 bg-white px-4 py-2 text-left hover:bg-zinc-50"
            >
              {t.name}
            </button>
          ))}
        </div>
      </div>
    );
  }

  if (me && !me.currentTeam && me.teams.length === 0) {
    return (
      <div className="flex min-h-screen items-center justify-center p-4 text-zinc-600">
        Нет доступных команд.
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-zinc-50">
      <header className="border-b border-zinc-200 bg-white">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-4 py-3">
          <nav className="flex gap-4">
            {nav.map(({ href, label }) => (
              <Link
                key={href}
                href={href}
                className={
                  pathname === href
                    ? "font-medium text-blue-600"
                    : "text-zinc-600 hover:text-zinc-900"
                }
              >
                {label}
              </Link>
            ))}
          </nav>
          <div className="flex items-center gap-4">
            {me?.currentTeam && (
              <span className="text-sm text-zinc-500">{me.currentTeam.name}</span>
            )}
            <Link
              href="/team-select"
              className="text-sm text-zinc-500 hover:text-zinc-700"
            >
              Сменить команду
            </Link>
            <button
              type="button"
              onClick={logout}
              className="text-sm text-zinc-500 hover:text-zinc-700"
            >
              Выйти
            </button>
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-6xl px-4 py-6">{children}</main>
    </div>
  );
}
