/**
 * Преобразует код ответа и опциональное сообщение бэкенда в понятный пользователю текст на русском.
 * Если бэкенд вернул безопасное сообщение (data.error), его можно показать; иначе — по статусу.
 */
export function getUserFacingError(
  status: number,
  backendError?: string | null
): string {
  const trimmed = backendError?.trim();
  if (trimmed && trimmed.length < 500) {
    return trimmed;
  }
  switch (status) {
    case 0:
      return "Нет связи с сервером. Проверьте подключение.";
    case 401:
      return "Неверный логин или пароль. Либо сессия истекла — войдите снова.";
    case 403:
      return "Нет доступа.";
    case 404:
      return "Не найдено.";
    case 500:
      return "Ошибка на сервере. Попробуйте позже.";
    default:
      return status >= 500
        ? "Ошибка на сервере. Попробуйте позже."
        : "Произошла ошибка. Попробуйте ещё раз.";
  }
}
