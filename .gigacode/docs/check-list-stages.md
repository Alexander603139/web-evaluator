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

## 🟢 Этап 5: Первый сценарий — Monkey

**Цель:** Убедиться, что модуль `scenario-monkey` корректно реализует логику случайного взаимодействия со страницей, фиксирует действия и ошибки, а `ReportService` успешно сохраняет и возвращает структурированный отчёт.

### Локальные проверки (Local)

**Сборка проекта:**
- Команда: `./gradlew build`
- Ожидаемый результат: `BUILD SUCCESSFUL`

**Подготовка тестового окружения:**
- Действие: Создать локальную тестовую HTML-фикстуру, содержащую элемент с заведомой JS-ошибкой.
- Пример элемента: `<button onclick="throw new Error('test error')">Click me</button>`

**Запуск сценария Monkey:**
- Команда: `POST /api/v1/runs`
- Тело запроса:

```json
{
  "url": "<URL_на_локальную_фикстуру>",
  "scenarioType": "MONKEY",
  "scenarioConfig": {
    "totalActions": 50
  }
}
```

- Ожидаемый результат: `HTTP 200 OK`, ответ содержит `jobId` и `status: "PENDING"` (или `"RUNNING"`).

**Валидация отчёта (Report):**
- Команда: `GET /api/v1/runs/{jobId}` (после завершения задачи)
- Ожидаемый результат: `HTTP 200 OK`, JSON-ответ содержит объект `report`, в котором:
  - `summary.totalSteps` равен фактическому количеству выполненных действий.
  - `steps` представляет собой массив объектов, каждый из которых содержит поля: `action` (тип действия), `target` (селектор элемента), `status` (`success`/`failed`).
- Условное требование: Если в ходе выполнения обезьянка кликнула по кнопке с ошибкой, в массиве `errors` соответствующего шага должна присутствовать запись, содержащая `"Uncaught Error: test error"`.

### Проверки на VPS (Production/Staging)

**Интеграционная проверка на реальном сайте:**
- Действие: Повторить шаги 3 и 4, используя публичный URL (например, `https://example.com`) вместо локальной фикстуры.
- Команда запуска: `POST /api/v1/runs` с `scenarioType: "MONKEY"` и `url: "https://example.com"`.
- Команда проверки: `GET /api/v1/runs/{jobId}`
- Ожидаемый результат: Успешное выполнение тех же проверок структуры отчёта (`totalSteps`, массив `steps` с полями `action`, `target`, `status`), что и в локальной среде.

## 🟢 Этап 6: Auth (JWT + API Keys)

### На VPS

Все команды выполняются на сервере (`root@msk-1-vm-39kb`).

**Проверка 1: Логин администратора**

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"change_me_in_production"}'
```

- Ожидаемый ответ: JSON с полями `accessToken`, `refreshToken`, `tokenType`, `expiresIn`.
- Сохраните `accessToken` и `refreshToken` в переменные для следующих проверок:

```bash
export TOKEN="eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJhZG1pbiIsInJvbGUiOiJBRE1JTiIsImlhdCI6MTc5MjQ4NTA0OSwiZXhwIjoxNzkyNDg1OTQ5fQ.o5STftUvjnoPi8ArOCq1-Lzxb2hck7P-9pAb16HCk1uCrhadyaPmvAa7HcFVmaJGKO88e8yU8opkCubkrY4mnA"
export REFRESH="eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJhZG1pbiIsInJvbGUiOiJSRUZSRVNIIiwiaWF0IjoxNzkyNDg1MDQ5LCJleHAiOjE3OTMwODk4NDl9.Ab5A53fK6UdncX1InnepH24LgyYspYKkbnB3HyjXTVn13AWHy1AaHAqjNiUEHumTAafyBh-Sgk7lRh3sXXV-yw"
```

**Проверка 2: Доступ без токена → 401**

```bash
curl -i http://localhost:8080/api/v1/runs
```

- Ожидаемый ответ: `HTTP 401 Unauthorized` с JSON-телом:

```json
{"status":401,"error":"UNAUTHORIZED","message":"Authentication required","timestamp":"…"}
```

**Проверка 3: Доступ с валидным JWT → 200**

```bash
curl -i http://localhost:8080/api/v1/runs \
  -H "Authorization: Bearer $TOKEN"
```

- Ожидаемый ответ: `HTTP 200 OK` с JSON-массивом задач (может быть пустым `[]`).

**Проверка 4: Обновление access-токена через refresh**

```bash
curl -X POST http://localhost:8080/api/v1/auth/refresh \
  -H "Authorization: Bearer $REFRESH"
```

- Ожидаемый ответ: JSON с новым `accessToken` и тем же `refreshToken`.

```bash
export TOKEN="eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJhZG1pbiIsInJvbGUiOiJBRE1JTiIsImlhdCI6MTc5MjQ4NTI0MiwiZXhwIjoxNzkyNDg2MTQyfQ.DjbH-xnhjJmtJvXwKqxr__pQ-TK4c2zasXBrZukCI4gSXjxzpRMGS9MEaYZnUB9mIt3LjVw3qXIFOiRSFvx9eQ"
export REFRESH="eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJhZG1pbiIsInJvbGUiOiJSRUZSRVNIIiwiaWF0IjoxNzkyNDg1MDQ5LCJleHAiOjE3OTMwODk4NDl9.Ab5A53fK6UdncX1InnepH24LgyYspYKkbnB3HyjXTVn13AWHy1AaHAqjNiUEHumTAafyBh-Sgk7lRh3sXXV-yw"
```

**Проверка 5: Создание API-ключа**

```bash
curl -X POST "http://localhost:8080/api/v1/api-keys?name=TestKey" \
  -H "Authorization: Bearer $TOKEN"
```

- Ожидаемый ответ: JSON вида:

```json
{"id":"...","name":"TestKey","apiKey":"wev_...","permissions":"READ,WRITE","lastUsedAt":null}
```

- Сохраните ключ:

```bash
export API_KEY="wev_cb56ac619875e74112179e324153ce30"
export API_KEY_ID="4ad26390-ce1e-4c7c-8e3a-299d7c514c94"
```

**Проверка 6: Список API-ключей**

```bash
curl http://localhost:8080/api/v1/api-keys \
  -H "Authorization: Bearer $TOKEN"
```

- Ожидаемый ответ: JSON-массив, содержащий созданный ключ с `name: "TestKey"`.

**Проверка 7: Запуск задачи через API-ключ**

```bash
curl -X POST http://localhost:8080/api/v1/runs \
  -H "Content-Type: application/json" \
  -H "X-API-Key: wev_a09cfe47de90eaf649c795bf792c572c" \
  -d '{"url":"https://example.com","scenarioType":"MONKEY","scenarioConfig":"{\"totalActions\":3,\"maxDurationSeconds\":15}"}'
```

- Ожидаемый ответ: `{"jobId":"...","status":"PENDING"}`

**Проверка 8: Невалидный API-ключ → 401**

```bash
curl -i -X POST http://localhost:8080/api/v1/runs \
  -H "Content-Type: application/json" \
  -H "X-API-Key: wev_invalid_key_12345" \
  -d '{"url":"https://example.com","scenarioType":"MONKEY"}'
```

- Ожидаемый ответ: `HTTP 401` с JSON-телом `{"status":401,"error":"UNAUTHORIZED",...}`.

**Проверка 9: Отзыв API-ключа**

```bash
curl -i -X DELETE "http://localhost:8080/api/v1/api-keys/$API_KEY_ID" \
  -H "Authorization: Bearer $TOKEN"
```

- Ожидаемый ответ: `HTTP 204 No Content`.

**Проверка 10: Отозванный ключ → 401**

```bash
curl -i -X POST http://localhost:8080/api/v1/runs \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $API_KEY" \
  -d '{"url":"https://example.com","scenarioType":"MONKEY","scenarioConfig":"{\"totalActions\":3}"}'
```

- Ожидаемый ответ: `HTTP 401` (ключ больше не работает).

**Проверка 11: Изоляция пользователей (USER не видит чужие задачи)**

```bash
# Залогиниться как USER
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"<user_username>","password":"<user_password>"}'
```

Затем выполнить `GET /api/v1/runs` с токеном USER — в ответе не должно быть задач, созданных админом.
