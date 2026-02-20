// Пустая строка = запросы на тот же origin (прокси в app/api/[[...path]]/route.ts)
const getBase = () => process.env.NEXT_PUBLIC_API_URL ?? "";

export function apiUrl(path: string): string {
  const base = getBase().replace(/\/$/, "");
  const p = path.startsWith("/") ? path : `/${path}`;
  return `${base}${p}`;
}

export type ApiResult<T> = {
  ok: boolean;
  status: number;
  data?: T;
  error?: string;
  /** true при сетевой ошибке (fetch выбросил или таймаут) */
  networkError?: boolean;
};

export async function apiFetch<T>(
  path: string,
  options: RequestInit = {}
): Promise<ApiResult<T>> {
  const url = apiUrl(path);
  let res: Response;
  try {
    res = await fetch(url, {
      ...options,
      credentials: "include",
      headers: {
        "Content-Type": "application/json",
        ...options.headers,
      },
    });
  } catch {
    return {
      ok: false,
      status: 0,
      error: "Нет связи с сервером. Проверьте подключение.",
      networkError: true,
    };
  }
  const contentType = res.headers.get("content-type");
  let data: T | undefined;
  if (contentType?.includes("application/json")) {
    try {
      data = (await res.json()) as T;
    } catch {
      // ignore
    }
  }
  if (!res.ok) {
    const err =
      data && typeof data === "object" && "error" in data
        ? String((data as { error?: string }).error)
        : res.statusText;
    return { ok: false, status: res.status, data, error: err };
  }
  return { ok: true, status: res.status, data };
}

export async function apiGet<T>(path: string) {
  return apiFetch<T>(path, { method: "GET" });
}

export async function apiPost<T>(path: string, body: unknown) {
  return apiFetch<T>(path, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export async function apiPut<T>(path: string, body: unknown) {
  return apiFetch<T>(path, {
    method: "PUT",
    body: JSON.stringify(body),
  });
}

export async function apiPatch<T>(path: string, body: unknown) {
  return apiFetch<T>(path, {
    method: "PATCH",
    body: JSON.stringify(body),
  });
}

export async function apiDelete<T = unknown>(path: string) {
  return apiFetch<T>(path, { method: "DELETE" });
}
