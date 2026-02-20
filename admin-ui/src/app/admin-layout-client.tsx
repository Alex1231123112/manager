"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { apiGet, apiPost, apiPut } from "@/lib/api";
import { getUserFacingError } from "@/lib/errors";
import type { MeResponse, TeamDto, SystemSettingsDto, ActionResult } from "@/lib/types";

function FirstRunSetup({ onLogout }: { onLogout: () => void }) {
  const [adminUsername, setAdminUsername] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<{ type: "ok" | "err"; text: string } | null>(null);

  useEffect(() => {
    apiGet<SystemSettingsDto>("/api/admin/system-settings").then((res) => {
      setLoading(false);
      if (res.ok && res.data?.adminTelegramUsername) {
        const u = res.data.adminTelegramUsername;
        setAdminUsername(u.startsWith("@") ? u : "@" + u);
      }
    });
  }, []);

  async function save() {
    setMessage(null);
    const value = adminUsername.trim().replace(/^@/, "");
    if (!value) {
      setMessage({ type: "err", text: "Введите @username" });
      return;
    }
    setSaving(true);
    const res = await apiPut<ActionResult>("/api/admin/system-settings", {
      adminTelegramUsername: value,
    });
    setSaving(false);
    if (res.ok && res.data?.success) {
      setMessage({ type: "ok", text: "Сохранено. Теперь этот пользователь может в Telegram написать боту /start и создать первую команду." });
    } else {
      setMessage({ type: "err", text: getUserFacingError(res.status, res.data?.data ?? res.error) });
    }
  }

  if (loading) {
    return (
      <div className="flex min-h-screen items-center justify-center text-zinc-500">
        Загрузка…
      </div>
    );
  }

  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-zinc-50 p-4">
      <div className="w-full max-w-md rounded-xl border border-zinc-200 bg-white p-6 shadow-sm">
        <h1 className="mb-2 text-xl font-semibold text-zinc-800">
          Первоначальная настройка
        </h1>
        <p className="mb-4 text-sm text-zinc-600">
          Команд пока нет. Укажите Telegram того, кто создаст первую команду: этот пользователь откроет бота в Telegram, отправит <strong>/start</strong> и введёт название команды. После этого команда появится в списке и вы сможете выбрать её здесь.
        </p>
        {message && (
          <p
            className={`mb-4 rounded-lg px-3 py-2 text-sm ${message.type === "ok" ? "bg-green-100 text-green-800" : "bg-red-100 text-red-800"}`}
          >
            {message.text}
          </p>
        )}
        <div className="mb-4">
          <label className="mb-1 block text-sm font-medium text-zinc-700">
            Telegram администратора (@username)
          </label>
          <input
            type="text"
            value={adminUsername}
            onChange={(e) => setAdminUsername(e.target.value)}
            placeholder="@username"
            className="w-full rounded-lg border border-zinc-300 px-3 py-2"
          />
        </div>
        <div className="flex gap-3">
          <button
            type="button"
            onClick={save}
            disabled={saving}
            className="rounded-lg bg-blue-600 px-4 py-2 text-white hover:bg-blue-700 disabled:opacity-50"
          >
            {saving ? "Сохранение…" : "Сохранить"}
          </button>
          <button
            type="button"
            onClick={onLogout}
            className="rounded-lg border border-zinc-300 px-4 py-2 text-zinc-700 hover:bg-zinc-50"
          >
            Выйти
          </button>
        </div>
      </div>
    </div>
  );
}

const nav = [
  { href: "/dashboard", label: "Дашборд" },
  { href: "/matches", label: "Матчи" },
  { href: "/calendar", label: "Календарь" },
  { href: "/league-table", label: "Таблица", title: "Турнирная таблица" },
  { href: "/debt", label: "Долги" },
  { href: "/finance", label: "Финансы" },
  { href: "/members", label: "Участники" },
  { href: "/invitations", label: "Приглашения" },
  { href: "/integration", label: "Интеграция", title: "Доставка сообщений и метрики Telegram" },
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
  const [selectingTeamId, setSelectingTeamId] = useState<number | null>(null);
  const [teamSelectError, setTeamSelectError] = useState<string | null>(null);

  function loadMe() {
    return apiGet<MeResponse>("/api/admin/me").then((res) => {
      setLoading(false);
      if (!res.ok || res.status === 401) {
        router.replace("/login");
        return;
      }
      if (res.data) setMe(res.data);
    });
  }

  // Загружаем me только при монтировании (не при каждой смене страницы), чтобы не блокировать навигацию
  useEffect(() => {
    loadMe();
  }, []);

  async function selectTeam(teamId: number) {
    setTeamSelectError(null);
    setSelectingTeamId(teamId);
    const res = await apiPost<{ success: boolean; error?: string }>(
      "/api/admin/team-select",
      { teamId }
    );
    setSelectingTeamId(null);
    if (res.ok && res.data?.success) {
      const meRes = await apiGet<MeResponse>("/api/admin/me");
      if (meRes.data) setMe(meRes.data);
      router.push("/dashboard");
      router.refresh();
    } else {
      setTeamSelectError(getUserFacingError(res.status, res.data?.error ?? res.error));
    }
  }

  async function logout() {
    await apiPost("/api/admin/logout", {});
    router.replace("/login");
    router.refresh();
  }

  // Показываем шапку сразу, контент — по готовности (чтобы не «мигало» полным экраном загрузки при навигации)
  if (loading && !me) {
    return (
      <div className="min-h-screen bg-zinc-50">
        <header className="border-b border-zinc-200 bg-white shadow-sm">
          <div className="mx-auto flex max-w-6xl items-center justify-between gap-4 px-4 py-3">
            <span className="text-sm text-zinc-400">Загрузка…</span>
          </div>
        </header>
        <main className="mx-auto flex max-w-6xl items-center justify-center px-4 py-12">
          <p className="text-zinc-500">Проверка доступа…</p>
        </main>
      </div>
    );
  }

  if (me && !me.currentTeam && me.teams.length > 0 && pathname !== "/team-select") {
    return (
      <div className="flex min-h-screen flex-col items-center justify-center gap-4 p-4">
        <h1 className="text-xl font-semibold text-zinc-800">Выберите команду</h1>
        {teamSelectError && (
          <p className="rounded-lg bg-red-100 px-3 py-2 text-sm text-red-800">
            {teamSelectError}
          </p>
        )}
        <div className="flex flex-col gap-2">
          {me.teams.map((t: TeamDto) => (
            <button
              key={t.id}
              type="button"
              onClick={() => selectTeam(t.id)}
              disabled={selectingTeamId !== null}
              className="rounded-lg border border-zinc-300 bg-white px-4 py-2 text-left hover:bg-zinc-50 disabled:opacity-50"
            >
              {selectingTeamId === t.id ? "Выбор…" : t.name}
            </button>
          ))}
        </div>
      </div>
    );
  }

  if (me && !me.currentTeam && me.teams.length === 0) {
    return (
      <FirstRunSetup onLogout={logout} />
    );
  }

  return (
    <div className="min-h-screen bg-zinc-50">
      <header className="border-b border-zinc-200 bg-white shadow-sm">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-x-6 gap-y-3 px-4 py-3">
          <nav className="flex flex-wrap items-center gap-x-4 gap-y-1">
            {nav.map(({ href, label, title }) => (
              <Link
                key={href}
                href={href}
                title={title ?? label}
                className={`whitespace-nowrap py-1 text-sm ${pathname === href ? "font-semibold text-blue-600" : "text-zinc-600 hover:text-zinc-900"}`}
              >
                {label}
              </Link>
            ))}
          </nav>
          <div className="flex shrink-0 items-center gap-3 border-l border-zinc-200 pl-4">
            {me?.currentTeam && (
              <span className="max-w-[140px] truncate text-sm font-medium text-zinc-700" title={me.currentTeam.name}>
                {me.currentTeam.name}
              </span>
            )}
            <Link
              href="/team-select"
              className="whitespace-nowrap text-sm text-zinc-500 hover:text-zinc-700"
            >
              Сменить команду
            </Link>
            <button
              type="button"
              onClick={logout}
              className="whitespace-nowrap text-sm text-zinc-500 hover:text-zinc-700"
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
