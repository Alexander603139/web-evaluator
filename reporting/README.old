# Базовый reporting (reporting)

Данный документ описывает архитектуру и логику работы модуля отчетности, который отвечает за сохранение, хранение и выдачу результатов выполнения сценариев тестирования.

## 1. Назначение и границы ответственности

Модуль `reporting` выступает хранилищем и процессором результатов, полученных от движков сценариев (`scenario-*`).

**Что делает модуль:**

- Принимает структурированные данные о выполнении (сводка и пошаговые действия).
- Сериализует эти данные в формат JSON для гибкого хранения в реляционной базе данных.
- Сохраняет отчет, жестко привязанный к сущности Job (один к одному).
- Предоставляет данные для формирования DTO при запросах через API.

**Чего модуль НЕ делает:**

- Не генерирует и не выполняет сценарии (это зона ответственности `orchestrator` и `scenario-*`).
- Не управляет бизнес-логикой изменения статусов задач (это делает `JobWorker`).
- Не хранит тяжелые бинарные артефакты (скриншоты, видео) напрямую в БД (поле `artifacts` зарезервировано под ссылки или метаданные, а не под сами файлы).

## 2. Архитектура и ключевые компоненты

### 2.1. ReportService

[//]: # (Основной сервис модуля. Отвечает за преобразование Java-объектов в JSON и сохранение.)
Реализует `ArtifactUploader`. Метод `uploadArtifact` загружает `byte[]` в S3 по пути `{jobId}/{filename}` и возвращает presigned URL (1 час). `generateHtmlReport` парсит JSON шагов, строит HTML-таблицу с `<img>`/ссылками, загружает её как `report.html` и возвращает её presigned URL.

**Ключевой метод:**

```java
@Transactional
public Report saveReport(Job job, Object summary, Object steps)
```

Принимает сущность `Job`, объект сводки (`summary`) и список шагов (`steps`). Использует `Spring ObjectMapper` для сериализации `summary` и `steps` в строки JSON. Находит существующий `Report` по `jobId` или создает новый. Сохраняет данные в поля `summary` (JSON), `steps` (JSON) и `artifacts` (заглушка `"{}"`).

### 2.2. ReportRepository

Интерфейс, наследующий `JpaRepository<Report, UUID>`. Обеспечивает стандартные CRUD-операции для сущности `Report`.

### 2.3. Доменная сущность Report (в модуле core)

Хранится в таблице `reports`. Имеет следующую структуру:

| Поле       | Тип                                      | Описание                                                                 |
|------------|------------------------------------------|--------------------------------------------------------------------------|
| `id`       | `UUID`, Primary Key                      | совпадает с `job_id`.                                                    |
| `job`      | `OneToOne`, `Lazy`                       | ссылка на задачу.                                                        |
| `summary`  | `String`, `columnDefinition = "TEXT"`    | JSON-строка с итоговой статистикой.                                      |
| `steps`    | `String`, `columnDefinition = "TEXT"`    | JSON-массив с детализацией каждого шага.                                 |
| `artifacts`| `String`, `columnDefinition = "TEXT"`    | JSON-строка для метаданных артефактов (скриншоты, логи).                 |

## 3. Поток данных (Data Flow)

[//]: # (1. **Генерация:** `MonkeyScenarioEngine` завершает работу и возвращает объект `ScenarioResult`, содержащий `totalSteps`, `errorCount`, `goalAchieved` и `List<ScenarioStep>`.)

[//]: # (2. **Передача:** `JobWorker` вызывает `reportService.saveReport&#40;job, result, result.getSteps&#40;&#41;&#41;`.)

[//]: # (3. **Сериализация:** `ReportService` превращает объекты в JSON-строки с помощью Jackson.)

[//]: # (4. **Сохранение:** `ReportRepository` сохраняет запись в таблицу `reports`.)

[//]: # (5. **Чтение:** При запросе `GET /api/v1/runs/{jobId}`, контроллер извлекает `Job` и связанный `Report`, десериализует JSON-строки обратно в DTO &#40;`JobResponse.ReportData`, `Summary`, `Step`&#41; и отдает клиенту.)

При ошибке шага: `PlaywrightActions.takeScreenshot()` → `ArtifactUploader.uploadArtifact()` → URL сохраняется в `ScenarioStep.screenshotUrl`.
По завершении: `JobWorker` собирает все `screenshotUrl` в `artifacts.screenshots`, вызывает `generateHtmlReport()` → `artifacts.htmlReport` → `ReportService.saveReport(..., artifacts)`.
API отдаёт presigned URL напрямую из S3 без проксирования трафика через Java.

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

## 5. Важные нюансы и уроки этапа

- **Гарантированная сериализация полей:**
  Во время разработки была выявлена проблема, при которой поле `target` в `ScenarioStep` не попадало в итоговый JSON, если оно было пустым.

  **Решение:**
  - В класс `ScenarioStep` добавлена аннотация `@JsonInclude(JsonInclude.Include.ALWAYS)`.
  - В `application.yml` глобально настроено: `spring.jackson.default-property-inclusion: always`.
  - В DTO `JobResponse.Step` явно добавлено поле `private String target;`.

- **Транзакционность:** Метод `saveReport` помечен `@Transactional`, чтобы гарантировать, что отчет и статус задачи обновляются согласованно.

- **Разделение ответственности:** Модуль `reporting` зависит только от `core`. Он не знает о существовании Playwright или конкретных типов сценариев, работая исключительно с абстрактными `Object` или базовыми DTO, что делает его универсальным для любого будущего типа тестирования (BDD, AI и т.д.).

## 6. Проверки (Definition of Done)

- `ReportServiceTest` успешно проходит, проверяя корректную сериализацию и сохранение в репозиторий.
- После выполнения задачи в БД (таблица `reports`) появляется запись с валидным JSON в колонках `summary` и `steps`.
- API-эндпоинт `GET /api/v1/runs/{jobId}` возвращает HTTP 200 и корректно десериализованный объект `report` со всеми полями (включая `target` в шагах).
- Сборка модуля проходит успешно: `./gradlew :reporting:test` и `./gradlew clean build`.
