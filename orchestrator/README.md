# Минимальный оркестратор (orchestrator)

Данный документ описывает архитектуру и логику работы модуля оркестрации, который отвечает за асинхронное управление жизненным циклом задач (Job) и их выполнение.

## 1. Назначение и границы ответственности

Модуль `orchestrator` выступает связующим звеном между API-запросами и исполняемыми сценариями.

**Что делает модуль:**

- Принимает созданные задачи со статусом `PENDING` и ставит их в очередь на выполнение.
- Обеспечивает потокобезопасное извлечение задач (защита от гонки данных при масштабировании).
- Делегирует выполнение конкретному движку сценария через `ScenarioRegistry`.
- Сохраняет результаты выполнения через модуль `reporting`.
- Управляет финальными статусами задачи (`SUCCESS`, `FAILED`) и временными метками.
- Восстанавливает "зависшие" задачи (Stale Job Recovery).

**Чего модуль НЕ делает:**

- Не содержит логики взаимодействия с браузером (это ответственность `ui-actions` и `scenario-*`).
- Не формирует DTO для HTTP-ответов (это ответственность модуля `api`).

## 2. Архитектура и ключевые компоненты

### 2.1. JobWorker

Основной исполнительный компонент (`@Component`). Реализует метод `processNextJob()`, который выполняется по расписанию (через `@Scheduled` или внешний триггер).

**Алгоритм работы `processNextJob()`:**

1. Запрашивает одну задачу со статусом `PENDING` с использованием пессимистичной блокировки (`findFirstPendingWithLock`).
2. Если задач нет — завершает выполнение.
3. Меняет статус задачи на `RUNNING` и фиксирует `startedAt`.
4. Через `ScenarioRegistry` получает нужный `ScenarioEngine` по типу сценария (например, `MONKEY`).
5. Формирует `ScenarioContext` и вызывает `engine.execute(context)`.
6. Передает результат (`ScenarioResult`) в `ReportService` для сохранения.
7. Устанавливает финальный статус: `SUCCESS` (если `goalAchieved == true`) или `FAILED`. Фиксирует `finishedAt`.
8. **Обработка ошибок:** Любой `Exception` перехватывается, статус меняется на `FAILED`, текст ошибки сохраняется в поле `error`, фиксируется `finishedAt`.

### 2.2. JobRepository

Интерфейс наследуется от `JpaRepository`. Ключевой метод:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT j FROM Job j WHERE j.status = 'PENDING' ORDER BY j.createdAt ASC")
Optional<Job> findFirstPendingWithLock();
```

> **Примечание:** Блокировка гарантирует, что если запущено несколько экземпляров приложения, одну и ту же задачу не подхватят два воркера одновременно.

### 2.3. JobService

Сервис для создания задач. Принимает запрос, валидирует его, создает сущность `Job` со статусом `PENDING` и сохраняет в БД, возвращая `jobId` клиенту немедленно (не дожидаясь выполнения).

### 2.4. ScenarioRegistry

Реестр доступных движков сценариев. Автоматически собирает все бины, реализующие интерфейс `ScenarioEngine`, и маппит их по имени (например, `monkeyScenarioEngine` для `ScenarioType.MONKEY`). Позволяет добавлять новые сценарии без изменения кода `JobWorker`.

### 2.5. StaleJobRecovery (Восстановление зависших задач)

Фоновый компонент, который периодически сканирует задачи, находящиеся в статусе `RUNNING` дольше заданного лимита (например, 10 минут). Такие задачи принудительно переводятся в статус `FAILED` с сообщением об ошибке таймаута, чтобы освободить систему.

## 3. Зависимости модуля (`orchestrator/build.gradle`)

```gradle
dependencies {
    api project(':core')
    api project(':reporting')
    
    // Подключение всех доступных сценариев для регистрации в ScenarioRegistry
    api project(':scenario-monkey')
    api project(':scenario-bdd')
    api project(':scenario-exploratory')
    api project(':scenario-ai')
    
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter'
    
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'wx.projectlombok:lombok'
}
```

## 4. Жизненный цикл задачи (Job Lifecycle)

1. **Создание:** `POST /api/v1/runs` → `JobService` → БД (`status: PENDING`).
2. **Планирование:** `JobScheduler` вызывает `JobWorker.processNextJob()`.
3. **Блокировка:** `JobRepository` извлекает задачу с `FOR UPDATE`.
4. **Выполнение:** `JobWorker` → `ScenarioRegistry` → `MonkeyScenarioEngine.execute()`.
5. **Отчетность:** `ReportService.saveReport()` сохраняет JSON-результат в таблицу `reports`.
6. **Завершение:** `JobWorker` обновляет статус на `SUCCESS` или `FAILED`.

## 5. Проверки и тестирование

### Локальные unit-тесты

- **JobServiceTest:** Проверка корректного создания задачи и присвоения начального статуса.
- **JobWorkerTest:** Мокирование `ScenarioRegistry` и `ReportService` для проверки успешного перехода статусов `PENDING` → `RUNNING` → `SUCCESS`.
- **StaleJobRecoveryTest:** Проверка перевода задач из `RUNNING` в `FAILED` при превышении лимита времени.

### Интеграционная проверка (через API)

1. Отправить задачу:

```bash
   curl -X POST http://localhost:8080/api/v1/runs \
     -H "Content-Type: application/json" \
     -d '{"url":"https://example.com","scenarioType":"MONKEY"}'
```

2. Немедленно проверить статус (должен быть `PENDING` или `RUNNING`).
3. Подождать завершения (для Monkey с 5 действиями ~2-3 секунды).
4. Получить результат:

```bash
   curl http://localhost:8080/api/v1/runs/{jobId}
```

Ожидается `status: "SUCCESS"` и заполненный объект `report`.

## 6. Критерии готовности (Definition of Done)

- `JobWorker` корректно делегирует выполнение через `ScenarioRegistry`, а не использует заглушки.
- Реализована пессимистичная блокировка при выборке задач (`findFirstPendingWithLock`).
- Любое исключение внутри сценария корректно перехватывается, логируется и меняет статус задачи на `FAILED` без падения всего приложения.
- Отчет успешно сохраняется в БД через `ReportService` до изменения финального статуса задачи.
- Все unit-тесты модуля `orchestrator` проходят (`./gradlew :orchestrator:test`).
