# Создание репозитория на GitHub и первый пуш

Локальный репозиторий уже создан, первый коммит сделан.

## 1. Создай репозиторий на GitHub

1. Открой [github.com/new](https://github.com/new).
2. **Repository name:** например `basketbot` или `manager` (как хочешь).
3. **Description:** `Цифровой менеджер — Telegram-бот для баскетбольной команды`.
4. Выбери **Public**.
5. **Не ставь** галочки «Add a README», «Add .gitignore», «Choose a license» — у тебя уже есть свои файлы.
6. Нажми **Create repository**.

## 2. Подключи remote и запушь

После создания репозитория GitHub покажет URL. Подставь свой (замени `USER` и `REPO` на свои логин и имя репозитория):

```bash
cd c:\Manager

# Подключить удалённый репозиторий (HTTPS)
git remote add origin https://github.com/USER/REPO.git

# Или по SSH, если настроен:
# git remote add origin git@github.com:USER/REPO.git

# Переименовать ветку в main (по желанию)
git branch -M main

# Отправить код
git push -u origin main
```

Если оставишь ветку `master`:

```bash
git remote add origin https://github.com/USER/REPO.git
git push -u origin master
```

При первом push Git может запросить логин/пароль. Для HTTPS используй [Personal Access Token](https://github.com/settings/tokens) вместо пароля (или настрой [Git Credential Manager](https://github.com/git-ecosystem/git-credential-manager)).

## 3. Проверка

- Обнови страницу репозитория на GitHub — должны появиться все файлы.
- Файл `.env` в репозиторий не попадёт (он в `.gitignore`) — токен бота остаётся только у тебя.
