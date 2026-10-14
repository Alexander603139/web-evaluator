# Проверки по этапам (1–4)

## 🟢 Этап 1: Структура проекта и база данных

### Локально
- `./gradlew clean build` — BUILD SUCCESSFUL
- Все модули компилируются без ошибок
- Юнит-тесты проходят

### На VPS
- `docker compose up -d` — оба контейнера в статусе Up
- `docker compose logs app --tail 20` содержит `Started WebEvaluatorApplication`
- `curl http://localhost:8080/actuator/health` → `{"status":"UP"}`
- Flyway успешно накатывает миграции (в логах: `Successfully applied N migrations`)
- Если задан `ADMIN_PASSWORD` — в логах есть строка `Admin user created` или `Admin user already exists`

---

## 🟢 Этап 2: Доменная модель

### Локально
- `./gradlew clean build` — BUILD SUCCESSFUL
- `UserTest` и `JobTest` проходят

### На VPS
Миграции `V1__init.sql` и `V2__domain_tables.sql` применены без ошибок.

Таблицы созданы (проверка через `psql`):

```bash
psql -U postgres -d webevaluator -c "\dt"
```

Ожидается: `users`, `api_keys`, `credentials`, `scenarios`, `jobs`, `reports`, `flyway_schema_history`

---

## 🟢 Этап 3: Базовый оркестратор

### Локально
- `./gradlew clean build` — BUILD SUCCESSFUL
- Тесты `JobServiceTest`, `JobWorkerTest`, `StaleJobRecoveryTest` проходят

### На VPS

Создать задачу:

```bash
curl -X POST http://localhost:8080/api/run \
  -H "Content-Type: application/json" \
  -d '{"url":"https://example.com"}'
```

Ожидается: `{"jobId":"<uuid>","status":"PENDING"}`

Через 3 секунды проверить статус:

```bash
curl http://localhost:8080/api/run/<jobId>
```

Ожидается: `{"jobId":"<uuid>","status":"SUCCESS"}`

Две задачи подряд — вторая начинает выполняться только после завершения первой (в логах видно `Processing job ...` с разницей ~2 секунды).

Проверка восстановления: создать задачу → сразу остановить app → запустить снова → задача должна стать `FAILED` с сообщением о stale job.

---

## 🟢 Этап 4: Базовый reporting

### Локально
- `./gradlew clean build` — BUILD SUCCESSFUL
- `ReportServiceTest` проходит

### На VPS

Создать задачу:

```bash
curl -X POST http://localhost:8080/api/run \
  -H "Content-Type: application/json" \
  -d '{"url":"https://example.com"}'
```

Ожидается: `{"jobId":"<uuid>","status":"PENDING"}`

Через 3 секунды получить полный отчёт:

```bash
curl http://localhost:8080/api/reports/<jobId>
```

Ожидается:

```json
{
  "jobId": "<uuid>",
  "status": "SUCCESS",
  "report": {
    "url": "https://example.com",
    "screenshots": [...],
    "findings": [...]
  }
}
```

Список всех задач:

```bash
curl http://localhost:8080/api/run
```

Ожидается: массив `[{...}, {...}]` со всеми созданными задачами.

---

## 📊 Итоговая таблица статусов

| Этап | Локальная сборка | Локальные тесты | VPS старт | VPS API |
|------|------------------|-----------------|-----------|---------|
| 1    | ✅               | ✅              | ✅        | ✅      |
| 2    | ✅               | ✅              | ✅        | —       |
| 3    | ✅               | ✅              | ✅        | ✅      |
| 4    | ✅               | ✅              | ✅        | ✅      |

Все 4 этапа закрыты. Готовы к **Этапу 5: Первый сценарий — Monkey**.
