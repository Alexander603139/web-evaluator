# Этап 10: Ephemeral Credentials (Эфемерные учетные данные)

Данный этап реализует механизм одноразовой (эфемерной) передачи учетных данных непосредственно в запросе на запуск сценария. Учетные данные **не сохраняются** в виде отдельных записей в базе данных, а передаются через сущность задачи (`Job`) только на время её выполнения, что соответствует лучшим практикам безопасности для чувствительных данных.

## 1. Архитектура и границы ответственности

**Что делает модуль:**

- Предоставляет легковесные DTO (`Credential`, `CredentialType`) для описания типов авторизации: `FORM_LOGIN`, `BASIC_AUTH`, `BEARER_TOKEN`.
- `CredentialApplier` (модуль `ui-actions`) применяет учетные данные к контексту Playwright:
  - **BASIC_AUTH / BEARER_TOKEN:** Добавляет HTTP-заголовок `Authorization` *до* вызова `navigate()`.
  - **FORM_LOGIN:** Использует эвристику для поиска полей на странице *после* вызова `navigate()`:
    1. Ищет `input[type='password']` и заполняет его.
    2. Ищет первый `input[type='text']` или `input[type='email']` и заполняет его.
    3. Ищет кнопку `submit` и кликает по ней.
- Движки (`MonkeyScenarioEngine`, `BddScenarioEngine`) вызывают `CredentialApplier` в правильном порядке (заголовки до навигации, форма после).

**Чего модуль НЕ делает:**

- Не создает и не использует CRUD-сервис для постоянного хранения учетных данных в БД (это отложено/отменено в пользу эфемерного подхода).
- Не логирует значения паролей или токенов.

## 2. Поток данных (Data Flow)

1. **Запрос:** Клиент отправляет `POST /api/v1/runs` с объектом `credential` в теле JSON.
2. **API:** `RunController` передает `request.getCredential()` в `JobService`.
3. **Хранение:** `JobService` сохраняет объект `Credential` в колонку `credential_data` (тип `jsonb`) таблицы `jobs`.
4. **Исполнение:** `JobWorker` читает задачу, извлекает `job.getCredential()` и передает её в `ScenarioContext`.
5. **Применение:** `ScenarioEngine` извлекает `context.getCredential()` и передает его в `CredentialApplier`.
6. **Очистка:** После завершения задачи данные остаются в истории `jobs` в зашифрованном/JSON виде, но не попадают в итоговый `Report`.

## 3. API Контракт и Примеры

### Запуск с авторизацией через форму (FORM_LOGIN)

```bash
curl -s -X POST http://localhost:8080/api/v1/runs \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $API_KEY" \
  -d '{
    "url": "https://the-internet.herokuapp.com/login",
    "scenarioType": "MONKEY",
    "scenarioConfig": "{\"totalActions\": 1}",
    "credential": {
      "type": "FORM_LOGIN",
      "username": "tomsmith",
      "password": "SuperSecretPassword!"
    }
  }'
```

**Ожидаемый результат в логах:**

```text
// (логи)
```

### Запуск с Bearer-токеном (BEARER_TOKEN)

```bash
curl -s -X POST http://localhost:8080/api/v1/runs \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $API_KEY" \
  -d '{
    "url": "https://httpbin.org/headers",
    "scenarioType": "MONKEY",
    "scenarioConfig": "{\"totalActions\": 1}",
    "credential": {
      "type": "BEARER_TOKEN",
      "token": "my_test_token_123"
    }
  }'
```

**Ожидаемый результат в логах:**

```text
Set extra header: Authorization
```

## 4. Проверки (Definition of Done)

- Сборка `./gradlew clean build` успешна.
- Эвристика заполнения формы (`FORM_LOGIN`) работает на тестовых страницах.
- Заголовки (`BASIC_AUTH`, `BEARER_TOKEN`) применяются до загрузки страницы.
- Учетные данные корректно передаются по цепочке: `RunRequest` → `Job` (JSONB) → `ScenarioContext` → `ScenarioEngine`.
- Пароли не фигурируют в логах приложения и в JSON-отчетах.
