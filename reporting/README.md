# Базовый reporting (reporting)

Данный документ описывает архитектуру и логику работы модуля отчетности, который отвечает за сохранение, хранение и выдачу результатов выполнения сценариев тестирования.

## 1. Назначение и границы ответственности

Модуль `reporting` выступает хранилищем и процессором результатов, полученных от движков сценариев (`scenario-*`).

**Что делает модуль:**

- Принимает структурированные данные о выполнении (сводка и пошаговые действия).
- Сериализует эти данные в формат JSON для гибкого хранения в реляционной базе данных.
- Сохраняет отчет, жестко привязанный к сущности Job (один к одному).
- Предоставляет данные для формирования DTO при запросах через API.
- Загружает бинарные артефакты (скриншоты, HTML-отчёт) в S3 и генерирует presigned URL для доступа к ним.

**Чего модуль НЕ делает:**

- Не генерирует и не выполняет сценарии (это зона ответственности `orchestrator` и `scenario-*`).
- Не управляет бизнес-логикой изменения статусов задач (это делает `JobWorker`).
- Не хранит тяжелые бинарные артефакты (скриншоты, видео) напрямую в БД (поле `artifacts` содержит ссылки и метаданные, а не сами файлы).

## 2. Архитектура и ключевые компоненты

### 2.1. ArtifactStorageService

Реализует `ArtifactUploader`. Метод `uploadArtifact` загружает `byte[]` в S3 по пути `{jobId}/{filename}` и возвращает presigned URL (1 час). `generateHtmlReport` парсит JSON шагов, строит HTML-таблицу с `<img>`/ссылками, загружает её как `report.html` и возвращает её presigned URL.

Класс `S3ArtifactStorageService` является пустой заготовкой, не используется и подлежит удалению.

### 2.2. ReportService

Отвечает за преобразование Java-объектов в JSON и сохранение в БД.

**Ключевой метод:**

```java
@Transactional
public Report saveReport(Job job, Object summary, Object steps) {
    // ... implementation
}
```

Принимает сущность `Job`, объект сводки (`summary`) и список шагов (`steps`). Использует `Spring ObjectMapper` для сериализации `summary` и `steps` в строки JSON. Находит существующий `Report` по `jobId` или создает новый. Сохраняет данные в поля `summary` (JSON), `steps` (JSON) и `artifacts` (JSON с `htmlReport` и `screenshots`).

### 2.3. ArtifactController (в модуле api)

Обрабатывает запросы к артефактам:

- `GET /api/v1/runs/{jobId}/artifacts/{filename}` — возвращает presigned URL для скачивания файла.
- `GET /api/v1/runs/{jobId}/report.html` — возвращает `302` redirect на presigned URL HTML-отчёта.

### 2.4. ReportRepository

Интерфейс, наследующий `JpaRepository<Report, UUID>`. Обеспечивает стандартные CRUD-операции для сущности `Report`.

### 2.5. Доменная сущность Report (в модуле core)

Хранится в таблице `reports`. Имеет следующую структуру:

| Поле        | Тип                                      | Описание                                             |
|-------------|------------------------------------------|------------------------------------------------------|
| `id`        | `UUID`, Primary Key                      | совпадает с `job_id`.                                |
| `job`       | `OneToOne`, `Lazy`                       | ссылка на задачу.                                    |
| `summary`   | `String`, `columnDefinition = "TEXT"`    | JSON-строка с итоговой статистикой.                  |
| `steps`     | `String`, `columnDefinition = "TEXT"`    | JSON-массив с детализацией каждого шага.             |
| `artifacts` | `String`, `columnDefinition = "TEXT"`    | JSON-строка с метаданными артефактов (`htmlReport`, `screenshots`). |

## 3. Поток данных (Data Flow)

1. **Генерация:** Движок сценария (например, `MonkeyScenarioEngine`) завершает работу и возвращает объект `ScenarioResult`, содержащий `totalSteps`, `errorCount`, `goalAchieved` и `List<ScenarioStep>`.
2. **Передача:** `JobWorker` вызывает `reportService.saveReport(job, result, result.getSteps())`.
3. **Сериализация:** `ReportService` превращает объекты в JSON-строки с помощью Jackson.
4. **Сохранение:** `ReportRepository` сохраняет запись в таблицу `reports`.
5. **Чтение:** При запросе `GET /api/v1/runs/{jobId}`, контроллер извлекает `Job` и связанный `Report`, десериализует JSON-строки обратно в DTO (`JobResponse.ReportData`, `Summary`, `Step`) и отдает клиенту.

**Загрузка артефактов:**

- При ошибке шага (`status="failed"`): `PlaywrightActions.takeScreenshot()` → `ArtifactUploader.uploadArtifact()` → URL сохраняется в `ScenarioStep.screenshotUrl`.
- По завершении: `JobWorker` собирает все `screenshotUrl` в `artifacts.screenshots`, вызывает `generateHtmlReport()` → `artifacts.htmlReport` → `ReportService.saveReport(..., artifacts)`.

> **Важно:** скриншоты создаются только для шагов со статусом `failed`. Для `success` и `skipped` артефактов нет — это **by design**.
>
> API отдаёт presigned URL напрямую из S3 без проксирования трафика через Java.

## 4. API Контракт (Формат ответа)

При запросе `GET /api/v1/runs/{jobId}` возвращается структура, содержащая вложенный объект `report`:

```json
{
  "jobId": "e25bdca7-1476-4625-9335-90cc6755ebc6",
  "scenarioType": "MONKEY",
  "status": "SUCCESS",
  "targetUrl": "https://example.com",
  "report": {
    "summary": {
      "totalSteps": 10,
      "errorCount": 0,
      "goalAchieved": true
    },
    "steps": [
      {
        "stepNumber": 1,
        "action": "click",
        "target": "a[href] >> nth=0",
        "status": "success",
        "timestamp": "2026-10-14T17:09:57.405299298Z",
        "errors": []
      }
    ]
  }
}
```

При запросе `GET /api/v1/runs/{jobId}/artifacts/{filename}` возвращается presigned URL (тело ответа — строка с URL).

При запросе `GET /api/v1/runs/{jobId}/report.html` возвращается `302` redirect на presigned URL HTML-отчёта.

## 5. Важные нюансы и уроки этапа

- **Гарантированная сериализация полей:**
  Во время разработки была выявлена проблема, при которой поле `target` в `ScenarioStep` не попадало в итоговый JSON, если оно было пустым.

  **Решение:**
  - В класс `ScenarioStep` добавлена аннотация `@JsonInclude(JsonInclude.Include.ALWAYS)`.
  - В `application.yml` глобально настроено: `spring.jackson.default-property-inclusion: always`.
  - В DTO `JobResponse.Step` явно добавлено поле `private String target;`.

- **Транзакционность:** Метод `saveReport` помечен `@Transactional`, чтобы гарантировать, что отчет и статус задачи обновляются согласованно.

- **Разделение ответственности:** Модуль `reporting` зависит только от `core`. Он не знает о существовании Playwright или конкретных типов сценариев, работая исключительно с абстрактными `Object` или базовыми DTO, что делает его универсальным для любого будущего типа тестирования.

## 6. Проверки (Definition of Done)

- `ReportServiceTest` успешно проходит, проверяя корректную сериализацию и сохранение в репозиторий.
- После выполнения задачи в БД (таблица `reports`) появляется запись с валидным JSON в колонках `summary` и `steps`.
- API-эндпоинт `GET /api/v1/runs/{jobId}` возвращает HTTP 200 и корректно десериализованный объект `report` со всеми полями (включая `target` в шагах).
- `GET /api/v1/runs/{jobId}/artifacts/{filename}` возвращает валидный presigned URL (HTTP 200).
- `GET /api/v1/runs/{jobId}/report.html` возвращает `302` на presigned URL HTML-отчёта.
- При наличии failed-шагов в сценарии поле `artifacts.screenshots` содержит непустой массив presigned URL, а `artifacts.htmlReport` — ссылку на HTML-отчёт.
- Сборка модуля проходит успешно: `./gradlew :reporting:test` и `./gradlew clean build`.

---

### Что изменено относительно старой редакции

1. **Раздел 1:** добавлен пункт про загрузку артефактов в S3; уточнено, что `artifacts` содержит ссылки/метаданные (а не сами файлы).
2. **Раздел 2:** явно выделен `ArtifactStorageService` как реализация `ArtifactUploader`; добавлен `ArtifactController` с описанием эндпоинтов; упомянуто, что `S3ArtifactStorageService` — пустая заготовка под удаление; в таблице `reports.artifacts` уточнено содержимое (`htmlReport`, `screenshots`).
3. **Раздел 3:** заменён закомментированный текст на рабочее описание потока; добавлен явный пункт о том, что скриншоты создаются только для `failed`-шагов (по факту кода `MonkeyScenarioEngine`).
4. **Раздел 4:** добавлены контракты для `/artifacts/{filename}` и `/report.html`.
5. **Раздел 5:** убрано упоминание BDD из фразы про универсальность модуля.
6. **Раздел 6 (DoD):** добавлены проверки артефакт-эндпоинтов и заполнения `artifacts` при наличии `failed`-шагов.

> Всё остальное (структура сущности, транзакционность, нюансы сериализации `target`) сохранено без изменений — эти части подтверждены кодом и текущим поведением.
