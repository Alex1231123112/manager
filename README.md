# BasketBot — Цифровой менеджер

Telegram-бот для управления баскетбольной командой: учёт игроков, матчи, результаты, посты, опросы, долги. Веб-админка — дашборд, состав, матчи, участники (роли), приглашения с QR, настройки.

## Требования

- Java 17+
- PostgreSQL (или Docker для полного стека)
- Токен бота от [@BotFather](https://t.me/BotFather)

## Быстрый старт (Docker)

1. **Создай бота в Telegram:** [@BotFather](https://t.me/BotFather) → `/newbot` → сохрани токен и имя бота.

2. **Создай `.env` в корне проекта** (файл в .gitignore):
   ```
   TELEGRAM_BOT_TOKEN=your_token_from_botfather
   TELEGRAM_BOT_USERNAME=YourBotName
   ```
   Опционально: `ADMIN_PASSWORD` — пароль входа в веб-админку (по умолчанию `admin`).

3. **Запуск:**
   ```bash
   docker compose up -d
   ```
   При первом запуске образы соберутся (приложение — Java/Spring Boot, админка — Next.js). Миграции БД применятся автоматически.

4. **Открой:**
   - **Веб-админка:** http://localhost:3000 — логин `admin`, пароль из `ADMIN_PASSWORD` или `admin`.
   - **API/бэкенд:** http://localhost:8095 (здоровье: http://localhost:8095/actuator/health).
   - **БД:** localhost:5432, пользователь `basketbot`, пароль `basketbot`, БД `basketbot`.

5. **В админке:** выбери команду (или создай её в боте). Доступны: Дашборд, Состав, Матчи, Долги, **Участники** (имя, @username, роли), **Приглашения** (ссылка и QR), **Настройки** (канал для постов; **Telegram администратора** в формате @username — кто может создавать команду в боте без приглашения).

6. **В боте:** напиши `/start`. Если твой @username указан в Настройках → «Доступ в бот» — создай команду (отправь название). Иначе попроси ссылку-приглашение у менеджера. Меню кнопок зависит от роли: капитан/админ видят «Приглашение», ввод результата, новый матч и т.д.; игрок — состав, опрос, долги, команды.

**Подробные сценарии бота** — [docs/BOT_SCENARIOS.md](docs/BOT_SCENARIOS.md). **Проверка и чек-листы** — [docs/VERIFICATION.md](docs/VERIFICATION.md).

## Запуск без Docker (разработка)

1. **PostgreSQL:** создай БД и пользователя:
   ```sql
   CREATE DATABASE basketbot;
   CREATE USER basketbot WITH PASSWORD 'basketbot';
   GRANT ALL PRIVILEGES ON DATABASE basketbot TO basketbot;
   ```

2. **Переменные:** `TELEGRAM_BOT_TOKEN` (обязательно), при необходимости `ADMIN_PASSWORD`, `SPRING_DATASOURCE_*` при отличии от дефолта.

3. **Запуск приложения:**
   ```bash
   mvn spring-boot:run
   ```
   API и встроенная админка (Thymeleaf): http://localhost:8080 (или порт из конфига).

4. **Админка (Next.js)** отдельно, если нужна полная функциональность:
   ```bash
   cd admin-ui && npm ci && npm run dev
   ```
   Укажи в `admin-ui/.env.local`: `NEXT_PUBLIC_API_URL=http://localhost:8080` (или 8095 при прокси).

## Запуск в контейнерах (детали)

**Полный стек (postgres + app + admin-ui):**

```bash
docker compose build --no-cache   # пересборка после изменений кода
docker compose up -d
```

- **postgres** — порт 5432.
- **app** — Spring Boot, порт 8095 (внутри 8080); healthcheck по `/actuator/health`.
- **admin-ui** — Next.js, порт 3000; запросы к API проксируются на `app:8080`.

Токен и имя бота задаются в `.env`:
```
TELEGRAM_BOT_TOKEN=...
TELEGRAM_BOT_USERNAME=BasketBot
```

**Только БД в Docker** (приложение локально):

```bash
docker compose -f docker-compose.dev.yml up -d
```

Подключение к БД: `localhost:5432`, пользователь `basketbot`, пароль `basketbot`, БД `basketbot`.

## Структура проекта

```
Manager/
├── src/main/java/com/basketbot/
│   ├── config/       — конфигурация (Security, Telegram)
│   ├── controller/   — REST API (admin, login)
│   ├── model/        — JPA (Team, Player, Match, TeamMember, Invitation, SystemSetting)
│   ├── repository/   — Spring Data JPA
│   ├── service/      — бизнес-логика
│   └── telegram/     — Telegram-бот (меню по ролям, /invite, приглашения)
├── src/main/resources/db/migration/  — Flyway (V1–V8)
├── admin-ui/         — веб-админка (Next.js): дашборд, участники, приглашения, настройки
├── docs/             — OVERVIEW, VERIFICATION, BOT_SCENARIOS, ROLES и др.
├── docker-compose.yml
└── Dockerfile        — образ приложения (Java 17, Maven, Spring Boot)
```

## Стандарты разработки

При любых изменениях в проекте действуют установки из [DEVELOPMENT.md](DEVELOPMENT.md): роль программиста (код, стек, продакшен) и аналитика (логика, сценарии, граничные случаи). В Cursor используется правило из `.cursor/rules/development-standards.mdc`.

## Документация

| Документ | Описание |
|----------|----------|
| [docs/OVERVIEW.md](docs/OVERVIEW.md) | Общая картина, стек, план развития |
| [docs/VERIFICATION.md](docs/VERIFICATION.md) | Проверка работы, обновление контейнеров, чек-листы бота и админки |
| [docs/BOT_SCENARIOS.md](docs/BOT_SCENARIOS.md) | Сценарии работы бота |
| [docs/ROLES.md](docs/ROLES.md) | Роли в боте и веб-админке |
| [DEVELOPMENT.md](DEVELOPMENT.md) | Стандарты разработки |
| [ROADMAP.md](ROADMAP.md) | Чек-листы по фазам |

Резервное копирование БД — [docs/BACKUP.md](docs/BACKUP.md).
