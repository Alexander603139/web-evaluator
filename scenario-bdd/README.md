# Этап 9: Третий сценарий — BDD (Парсер текстовых сценариев)

Данный документ описывает архитектуру и логику работы модуля `scenario-bdd`, отвечающего за выполнение тестовых сценариев, описанных текстом (в формате Gherkin или произвольной user-story), без использования тяжелых внешних фреймворков вроде Cucumber.

## 1. Назначение и границы ответственности

**Что делает модуль:**

- Парсит текстовые сценарии, распознавая ключевые слова: `Дано/Given`, `Когда/When`, `То/Then`, `И/And`.
- Маппит предопределенные шаги на методы `PlaywrightActions`:
  - `открываю страницу {url}` → `playwright.navigate()`
  - `ввожу текст {text}` в поле `{selector}` → `playwright.type()`
  - `нажимаю на кнопку {selector}` → `playwright.clickWithForce()`
  - `заголовок страницы равен {expected}` → сравнение с `playwright.getTitle()`
- Поддерживает два способа получения сценария: `inline` через параметр `featureText` или по ссылке `scenarioId` из таблицы `scenarios` в БД.
- Реализует LLM-fallback заглушку: если строка не распознана детерминированно, шаг помечается как `skipped` с соответствующим сообщением в `errors`, не прерывая весь сценарий.
- Логирует каждый шаг в отчет с привязкой к исходной строке (`gherkinLine`).

**Чего модуль НЕ делает:**

- Не использует библиотеки Cucumber/JBehave (парсинг реализован через регулярные выражения для легковесности).
- Не управляет статусами задач (это зона ответственности `orchestrator`).
- Не выполняет глубокую семантическую оценку через LLM на данном этапе (fallback — это заглушка).

## 2. Архитектура и ключевые компоненты

### 2.1. Доменная модель и Хранение (`core`)

- **Scenario:** Сущность для библиотеки сценариев. Содержит `id`, `owner_id`, `name`, `content` (TEXT), `createdAt`, `updatedAt`.
- **ScenarioRepository:** Стандартный `JpaRepository<Scenario, UUID>` для CRUD-операций.

### 2.2. Движок сценария (`scenario-bdd`)

- **BddScenarioEngine:** Основная реализация интерфейса `ScenarioEngine`.
  - Метод `resolveFeatureText` проверяет наличие `featureText` в конфиге. Если его нет, загружает `content` из БД по `scenarioId`.
  - Метод `executeStep` применяет регулярные выражения (`Pattern.compile`) к каждой строке для детерминированного распознавания команд.
- **BddScenarioStep:** Полиморфный наследник абстрактного класса `ScenarioStep`. Содержит специфичные для BDD поля: `gherkinLine` (исходная строка), `expected` (ожидаемое значение) и `actual` (фактическое значение).
- **BddScenarioConfig:** Spring-конфигурация (`@Configuration` + `@ComponentScan`) для регистрации компонентов модуля.

### 2.3. Интеграция с отчетностью

Благодаря полиморфизму Jackson (настроенному в модуле `app`), при сериализации `ScenarioResult` в JSON, шаги BDD-сценария автоматически получают поле `"stepType": "BDD"`, что позволяет API и фронтенду корректно отображать специфичные поля (`gherkinLine`, `expected`, `actual`), не засоряя общую модель `ScenarioStep`.

## 3. Поток данных (Data Flow)

1. **Запрос:** Клиент отправляет `POST /api/v1/runs` с `scenarioType: "BDD"`.
2. **Валидация:** `UrlSecurityPolicy` (Этап 7) проверяет целевой URL.
3. **Инициализация:** `JobWorker` передает управление в `BddScenarioEngine.execute()`.
4. **Разрешение текста:** Движок получает текст сценария (из запроса или БД).
5. **Исполнение:** Текст разбивается на строки. Каждая строка проверяется regex-шаблонами. Выполняются действия через `PlaywrightActions`.
6. **Отчетность:** `JobWorker` вызывает `ReportService.saveReport()`, который сериализует `List<BddScenarioStep>` в JSONB колонку `steps` таблицы `reports`.

## 4. API Контракт

### Запуск сценария (inline)

```bash
curl -s -X POST http://localhost:8080/api/v1/runs \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $API_KEY" \
  -d '{"url":"https://the-internet.herokuapp.com/login","scenarioType":"BDD","scenarioConfig":"{\"featureText\":\"открываю страницу https://the-internet.herokuapp.com/login\\nввожу текст admin в поле #username\\nнажимаю на кнопку button[type=\\\\\"submit\\\\\"]\\nзаголовок страницы равен Dashboard\"}"}'
```

**Ожидаемый ответ:** `{"jobId":"...","status":"PENDING"}`

### Запуск сценария (из библиотеки)

```bash
curl -s -X POST http://localhost:8080/api/v1/runs \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $API_KEY" \
  -d '{"url":"https://example.com","scenarioType":"BDD","scenarioConfig":"{\"scenarioId\":\"123e4567-e89b-12d3-a456-426614174000\"}"}'
```

### Получение отчета

```bash
curl -s http://localhost:8080/api/v1/runs/{jobId} -H "X-API-Key: $API_KEY"
```

**Ожидаемый фрагмент ответа (успешный шаг):**

```json
{
  "stepType": "BDD",
  "stepNumber": 1,
  "gherkinLine": "открываю страницу https://example.com",
  "status": "success",
  "timestamp": "2026-10-26T12:00:00.000Z",
  "errors": []
}
```

**Ожидаемый фрагмент ответа (шаг с ошибкой утверждения):**

```json
{
  "stepType": "BDD",
  "stepNumber": 4,
  "gherkinLine": "заголовок страницы равен НеверныйЗаголовок",
  "status": "failed",
  "expected": "НеверныйЗаголовок",
  "actual": "Example Domain",
  "timestamp": "2026-10-26T12:01:00.000Z",
  "errors": ["Expected title: 'НеверныйЗаголовок', but got: 'Example Domain'"]
}
```

## 5. Важные нюансы и уроки этапа

- **Экранирование переносов строк:** При передаче `featureText` через `curl` в JSON, реальные переносы строк (`0x0a`) ломают парсер PostgreSQL. Необходимо использовать экранированные последовательности `\n` внутри JSON-строки.
- **Полиморфизм вместо "God Object":** Вынесение полей `gherkinLine`, `expected` и `actual` в отдельный класс `BddScenarioStep` предотвратило раздувание базового класса `ScenarioStep` null-полями, которые не используются в Monkey- или AI-сценариях.
- **Устойчивость к ошибкам парсинга:** LLM-fallback заглушка гарантирует, что опечатка в одной строке сценария не приведет к падению всей задачи (`FATAL_ERROR`), а лишь пропустит этот шаг с пометкой `skipped`.

## 6. Проверки (Definition of Done)

### Локальные проверки

- `./gradlew clean build` завершается успешно (`BUILD SUCCESSFUL`).
- Создана локальная тестовая HTML-фикстура с формой входа.
- `POST /api/v1/runs` с `scenarioType: "BDD"` и валидным `featureText` (5 шагов) успешно создает задачу.
- `GET /api/v1/runs/{jobId}` возвращает отчет, где:
  - `summary.totalSteps` равен количеству выполненных шагов.
  - Массив `steps` содержит объекты с обязательным полем `gherkinLine`.
  - Все шаги имеют `status: "success"`.
- Запуск сценария с заведомо неверным ожидаемым заголовком возвращает общий `status: "FAILED"`, а последний шаг имеет `status: "failed"` и описание расхождения в `errors` (или `expected`/`actual`).
- Успешный запуск по `scenarioId` (текст подгружается из таблицы `scenarios`).

### Проверки на VPS

- Деплой через GitHub Actions проходит успешно, контейнер `web-evaluator-app` имеет статус `Up`.
- Интеграционные проверки (успешный сценарий, сценарий с ошибкой, запуск по `scenarioId`) успешно выполняются через `curl` на реальном тестовом сайте (например, `https://the-internet.herokuapp.com/login`) с использованием заголовка `X-API-Key`.
