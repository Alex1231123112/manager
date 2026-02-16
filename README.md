# BasketBot — Цифровой менеджер

Telegram-бот для управления баскетбольной командой (учёт игроков, матчи, результаты, посты).

## Требования

- Java 17+
- PostgreSQL (или Maven для сборки)
- Токен бота от [@BotFather](https://t.me/BotFather)

## Быстрый старт

1. **Создай бота в Telegram:** напиши [@BotFather](https://t.me/BotFather), команда `/newbot`, сохрани токен.

2. **Подними PostgreSQL** и создай БД:
   ```sql
   CREATE DATABASE basketbot;
   CREATE USER basketbot WITH PASSWORD 'basketbot';
   GRANT ALL PRIVILEGES ON DATABASE basketbot TO basketbot;
   ```

3. **Настрой переменные окружения** (или создай `src/main/resources/application-local.yml`):
   - `TELEGRAM_BOT_TOKEN` — токен от BotFather (без него бот не стартует).
   - `DB_PASSWORD` — пароль БД (по умолчанию `basketbot`).

4. **Запуск:**
   ```bash
   # С Maven в PATH:
   mvn spring-boot:run

   # Или с Maven Wrapper (после mvn wrapper:wrapper):
   ./mvnw spring-boot:run
   ```

5. Напиши боту в Telegram команду `/start` — создай команду (отправь название), затем пользуйся меню:
   - **Состав** — список игроков; фильтр: `/roster активные | травма | отпуск | не оплатил`; статус: `/setstatus Имя статус`
   - **Добавить игрока** — затем: `Имя Номер` или `/addplayer Имя Номер`
   - **Новый матч** — затем: `/newmatch Соперник`
   - **Ввести результат** — затем: `/result наши их` (пост, картинка, кнопка «Опубликовать в канал»)
   - **Опрос на игру** — затем: `/poll Текст` (Еду / Не еду / Опоздаю)
   - **Долги** — список; выставить: `/setdebt Имя Сумма`; канал для постов: `/setchannel ID_канала`

   **Все сценарии работы бота** — в [docs/BOT_SCENARIOS.md](docs/BOT_SCENARIOS.md).

## Запуск в контейнерах

Нужны Docker и Docker Compose.

**Вариант 1: Всё в Docker (PostgreSQL + приложение)**

```bash
export TELEGRAM_BOT_TOKEN=your_token_from_botfather
docker compose up --build
```

Приложение будет на порту 8080, БД — 5432. Токен можно задать в `.env` (файл в .gitignore):

```
TELEGRAM_BOT_TOKEN=your_token
TELEGRAM_BOT_USERNAME=BasketBot
```

**Вариант 2: Только БД в контейнере (удобно для разработки)**

Поднять PostgreSQL, приложение запускать локально (IDE или `mvn spring-boot:run`):

```bash
docker compose -f docker-compose.dev.yml up -d
```

Подключение: `localhost:5432`, пользователь `basketbot`, пароль `basketbot`, БД `basketbot`.

**Сборка образа приложения без compose**

```bash
docker build -t basketbot:latest .
docker run --rm -e TELEGRAM_BOT_TOKEN=... -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/basketbot -e SPRING_DATASOURCE_USERNAME=basketbot -e SPRING_DATASOURCE_PASSWORD=basketbot -p 8080:8080 basketbot:latest
```

## Структура проекта

```
com.basketbot/
├── config/       — конфигурация (в т.ч. Telegram)
├── controller/   — REST (на будущее)
├── model/        — JPA-сущности (Team, Player, Match)
├── repository/   — Spring Data JPA
├── service/      — бизнес-логика
├── telegram/     — обработчики Telegram-бота
└── util/
```

## Стандарты разработки

При любых изменениях в проекте действуют установки из [DEVELOPMENT.md](DEVELOPMENT.md): роль программиста (код, стек, продакшен) и аналитика (логика, сценарии, граничные случаи). В Cursor используется правило из `.cursor/rules/development-standards.mdc`.

## Общая картина и план развития

Краткое описание сделанного и план дальнейшего развития — в [docs/OVERVIEW.md](docs/OVERVIEW.md). Детальные чек-листы по фазам — в [ROADMAP.md](ROADMAP.md), полный понедельный план — в [План разработки](План%20разработки).
