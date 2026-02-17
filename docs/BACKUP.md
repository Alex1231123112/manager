# Резервное копирование PostgreSQL

Рекомендуется настроить регулярные бэкапы базы данных для команды «Цифровой менеджер».

## Ручной бэкап (pg_dump)

```bash
# Полный дамп БД (формат custom, удобен для восстановления)
pg_dump -h localhost -U basketbot -Fc basketbot -f basketbot_$(date +%Y%m%d_%H%M).dump

# Или SQL-файл (удобен для просмотра и переноса)
pg_dump -h localhost -U basketbot basketbot -f basketbot_$(date +%Y%m%d_%H%M).sql
```

Пароль можно задать через переменную `PGPASSWORD` или использовать `.pgpass`.

## Восстановление

```bash
# Из custom-формата
pg_restore -h localhost -U basketbot -d basketbot -c basketbot_YYYYMMDD_HHMM.dump

# Из SQL
psql -h localhost -U basketbot -d basketbot -f basketbot_YYYYMMDD_HHMM.sql
```

Опция `-c` в pg_restore удаляет объекты перед восстановлением; без неё данные добавляются к существующим (могут быть конфликты).

## Автоматизация (cron)

Пример задачи cron (ежедневно в 3:00, Linux/macOS):

```bash
0 3 * * * pg_dump -h localhost -U basketbot -Fc basketbot -f /backups/basketbot_$(date +\%Y\%m\%d).dump
```

Папку `/backups` создайте заранее; учётная запись должна иметь права на запись. Ротацию старых дампов можно делать отдельным скриптом или средствами системы (logrotate и т.п.).

При развёртывании в облаке используйте встроенные средства бэкапов (например, автоматические снимки диска или сервис бэкапов БД провайдера).
