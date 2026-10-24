# Сценарий Monkey Testing

Данный документ описывает реализацию модуля хаотичного тестирования (Monkey Testing) в проекте web-evaluator. Сценарий имитирует действия случайного пользователя на одной странице, выявляя JavaScript-ошибки и сетевые сбои.

## 📁 Ключевые компоненты

- `scenario-monkey/.../MonkeyScenarioEngine.java` — основная реализация интерфейса ScenarioEngine.
- `core/.../scenario/` — контрактные интерфейсы и DTO: ScenarioEngine, ScenarioContext, ScenarioResult, ScenarioStep.
- `ui-actions/.../PlaywrightActions.java` — низкоуровневая обертка над Playwright: поиск видимых элементов, выполнение действий, перехват pageerror.
- `orchestrator/.../worker/JobWorker.java` — оркестратор, который через ScenarioRegistry получает нужный движок по ScenarioType.MONKEY и сохраняет результат через ReportService.

## ⚙️ Алгоритм работы

1. **Инициализация:** Открытие целевого URL и ожидание состояния `networkidle`.
2. **Цикл действий:** Выполняется до достижения лимита `totalActions` ИЛИ истечения `maxDurationSeconds`.
   - **Поиск элементов:** Собираются все видимые (`:visible`) интерактивные элементы: `a`, `button`, `input`, `textarea`, `[role='button']`.
   - **Выбор и выполнение действия:**
     - Если элементов нет → выполняется скролл окна.
     - Если элемент `input` или `textarea` → всегда выполняется ввод случайного текста (8 символов), с вероятностью 50% дополнительно нажимается Enter.
     - Если элемент кнопка/ссылка → применяется взвешенный случайный выбор (по умолчанию: клик 70%, ввод 20%, скролл 10%). Клик выполняется с параметром `force=true` (для обхода перекрытий).
   - **Обработка состояний:**
     - Ожидание `domcontentloaded` после действия.
     - Исключения при взаимодействии с элементом (например, элемент исчез из DOM) перехватываются, шаг получает статус "skipped".
     - Глобальные JS-ошибки страницы (`pageerror`) перехватываются слушателем. Если они есть, шаг получает статус "failed", а текст ошибки добавляется в массив `errors`.
3. **Завершение:** Браузер гарантированно закрывается в блоке `finally`. Формируется `ScenarioResult` и передается в `ReportService`.

## ⚙️ Конфигурация (MonkeyConfig)

Параметры передаются в поле `scenarioConfig` запроса в формате JSON. Значения по умолчанию:

| Параметатр         | Тип   | По умолчанию | Описание                                             |
|--------------------|-------|--------------|------------------------------------------------------|
| `totalActions`     | int   | 50           | Максимальное количество действий.                    |
| `maxDurationSeconds` | int | 60           | Максимальное время выполнения сценария в секундах.   |
| `clickWeight`      | int   | 70           | Вероятность (%) выбора действия "клик".              |
| `typeWeight`       | int   | 20           | Вероятность (%) выбора действия "ввод текста".       |
| `scrollWeight`     | int   | 10           | Вероятность (%) выбора действия "скролл".            |

## 🚀 Использование через API

**Запуск задачи:**
Предварительно необходимо получить API-ключ.
```bash
curl -s -X POST http://localhost:8080/api/v1/runs -H "Content-Type: application/json" -H "X-API-Key: $API_KEY" -d '{"url":"https://example.com","scenarioType":"MONKEY","scenarioConfig":"{\"totalActions\":20,\"maxDurationSeconds\":30,\"clickWeight\":70,\"typeWeight\":20,\"scrollWeight\":10}"}'
```

**Получение отчета:**

```bash
curl -s http://localhost:8080/api/v1/runs/<ТВОЙ_JOB_ID> -H "X-API-Key: $API_KEY"
```

Ответ будет содержать массив `steps`, где каждый шаг имеет поля: `stepNumber`, `action`, `target` (CSS-селектор), `status` (success/failed/skipped), `timestamp`, `errors`.

## 🧪 Тестирование

Модуль покрыт юнит-тестами в классе `MonkeyScenarioEngineTest`:

- `execute_shouldPerformActionsAndCollectSteps` — проверка корректного количества шагов и вызовов Playwright.
- `execute_shouldCapturePageErrors` — проверка перехвата JS-ошибок и их попадания в поле `errors` шага.
- `execute_shouldRespectTimeLimit` — проверка принудительного завершения цикла при превышении `maxDurationSeconds`.
- `execute_shouldHandleException` — проверка корректной обработки фатальных ошибок (например, крах браузера) без падения всего процесса.

## 🔧 Сборка

Для успешной компиляции модуль `scenario-monkey` зависит от `ui-actions`, который, в свою очередь, предоставляет транзитивную зависимость на Playwright (`api 'com.microsoft.playwright:playwright:...'` в `ui-actions/build.gradle`).

**Локальная проверка:**

```bash
./gradlew :scenario-monkey:test
./gradlew clean build
```
