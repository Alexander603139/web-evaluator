# Модуль ui-actions (Playwright Wrapper)

## 1. Назначение и границы ответственности

Модуль `ui-actions` предоставляет унифицированную, потокобезопасную (в рамках одного экземпляра) и наблюдаемую обёртку над Java-API библиотеки Playwright.

**Что делает модуль:**

- Управление жизненным циклом браузера (инициализация, навигация, корректное закрытие).
- Выполнение низкоуровневых действий с DOM (клики, ввод текста, скролл).
- Перехват и буферизация побочных эффектов: JavaScript-ошибок (`pageerror`) и сбоев сетевых запросов (`requestfailed`).
- Предоставление утилитарных методов для поиска и анализа интерактивных элементов (необходимо для Monkey-сценариев).

**Чего модуль НЕ делает:**

- Не содержит бизнес-логики сценариев (это ответственность модулей `scenario-*`).
- Не управляет очередями задач или оркестрацией (это ответственность `orchestrator`).
- Не сохраняет данные в БД (это ответственность `reporting`).

## 2. Зависимости (`ui-actions/build.gradle`)

```gradle
plugins {
    id 'java-library'
    id 'io.spring.dependency-management'
}

dependencies {
    // API-зависимость, чтобы другие модули (например, scenario-monkey) 
    // видели типы Playwright при необходимости
    api 'com.microsoft.playwright:playwright:1.49.0'
    
    implementation 'org.springframework:spring-context'
    implementation 'org.slf4j:slf4j-api'
    
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'
    
    testImplementation 'org.junit.jupiter:junit-jupiter'
    testImplementation 'org.assertj:assertj-core'
}
```

## 3. Архитектура и ключевые компоненты

### 3.1. Основной класс: `PlaywrightActions`

Реализует интерфейс `AutoCloseable` для гарантированного освобождения ресурсов. Является Spring-бином (`@Component`) с областью видимости по умолчанию (Singleton), но должен использоваться аккуратно: каждый сценарий должен работать в рамках изолированного `BrowserContext` или корректно переинициализировать `Page`, либо (предпочтительно для параллельных запусков в будущем) создаваться как Prototype/через Factory. На текущем этапе допускается создание нового экземпляра на каждую задачу или использование с синхронизацией.

**Состояние (поля):**

- `Playwright playwright`
- `Browser browser` (Chromium, `headless=true`)
- `BrowserContext context`
- `Page page`
- `List<PageError> pageErrors` (буфер ошибок JS)
- `List<NetworkError> networkErrors` (буфер сетевых ошибок)

### 3.2. DTO для телеметрии

- **PageError:** `message` (String), `location` (String, опционально).
- **NetworkError:** `url` (String), `method` (String), `failureReason` (String).

## 4. Публичный API (Контракт)

### 4.1. Управление жизненным циклом и навигация

| Метод                        | Описание                                                                 |
|------------------------------|--------------------------------------------------------------------------|
| `void navigate(String url)`  | Переход по указанному URL.                                               |
| `void waitForLoadState(String state)` | Ожидание состояния страницы (например, `"networkidle"`, `"domcontentloaded"`). |
| `void close()`               | Реализация AutoCloseable. Гарантированно закрывает page, context, browser и playwright в безопасном порядке. |

### 4.2. Взаимодействие с DOM

| Метод                          | Описание                                                                 |
|--------------------------------|--------------------------------------------------------------------------|
| `void click(String selector)`  | Стандартный клик по селектору.                                           |
| `void clickWithForce(String selector)` | Клик с флагом `force=true` (игнорирует перекрытия, критично для Monkey-тестов). |
| `void type(String selector, String text)` | Очистка поля и ввод текста (`fill`).                                  |
| `void pressEnter()`            | Симуляция нажатия клавиши Enter на текущем фокусе.                       |
| `void scroll(int deltaX, int deltaY)` | Прокрутка страницы или элемента через `page.mouse().wheel()`.       |

### 4.3. Discovery и утилиты (для Monkey-сценариев)

| Метод                              | Описание                                                                 |
|------------------------------------|--------------------------------------------------------------------------|
| `List<String> findInteractiveElements()` | Возвращает список CSS-селекторов всех видимых интерактивных элементов (`a:visible`, `button:visible`, `input:visible`, `textarea:visible`, `[role='button']:visible`). Формат: `"tag >> nth=INDEX"`. |
| `String getElementType(String selector)` | Возвращает `tagName` элемента в нижнем регистре (например, `"input"`, `"button"`). |
| `String generateRandomText()`      | Генерирует псевдослучайную строку (8 символов: буквы, цифры, спецсимволы) для заполнения полей. |

### 4.4. Телеметрия и очистка

| Метод                         | Описание                                                                 |
|-------------------------------|--------------------------------------------------------------------------|
| `List<PageError> getPageErrors()` | Возвращает накопленные JS-ошибки.                                      |
| `List<NetworkError> getNetworkErrors()` | Возвращает накопленные сетевые ошибки.                          |
| `void clearErrors()`          | Очищает оба буфера ошибок (вызывается после сохранения шага, чтобы не дублировать ошибки между шагами). |

## 5. Механизм перехвата ошибок (Event Listeners)

При инициализации Page модуль обязан подписаться на следующие события:

```text
page.onPageError(error -> {
        log.warn("JS error captured: {}", error);
    pageErrors.add(new PageError(error, null));
        });

        page.onRequestFailed(request -> {
        log.warn("Network request failed: {} ({})", request.url(), request.failure());
        networkErrors.add(new NetworkError(request.url(), request.method(), request.failure()));
        });
```

> **Примечание:** Ошибки не должны прерывать выполнение сценария автоматически. Они лишь фиксируются в буфере, а решение о том, считать ли шаг `failed` или `skipped`, принимает вызывающий код (`ScenarioEngine`).

## 6. Управление ресурсами и безопасность

- **Гарантированное закрытие:** Использование паттерна `try-finally` в вызывающем коде (`MonkeyScenarioEngine`) или реализация `AutoCloseable` в самом классе.
- **Headless-режим:** Браузер всегда запускается с `headless=true` для работы в контейнеризированной среде (Docker).
- **Таймауты:** Все действия с DOM должны иметь разумные дефолтные таймауты Playwright (30 сек), но для Monkey-кликов рекомендуется явно указывать `timeout=3000` (3 сек), чтобы не блокировать поток на зависших анимациях.

## 7. Стратегия тестирования

Модуль проверяется интеграционными тестами (`PlaywrightActionsTest`), которые:

1. Запускают локальный HTTP-сервер (или используют статический ресурс `test-page.html` из `src/test/resources`).
2. Инициализируют `PlaywrightActions`.
3. Выполняют целевые действия (например, `triggerJsError()`).
4. Ассертят наличие ожидаемых ошибок в `getPageErrors()`.
5. Гарантируют вызов `close()` в блоке `@AfterEach` или через `try-with-resources`.

**Пример тестовой фикстуры (`test-page.html`):**

```html
<button id="error-btn" onclick="throw new Error('Test JS error')">Break Me</button>
<input type="text" id="test-input" />
```

## 8. Критерии готовности (Definition of Done)

- Класс `PlaywrightActions` реализует `AutoCloseable` и корректно освобождает ресурсы.
- Все методы из раздела 4 реализованы и покрыты Javadoc.
- Подписка на `pageerror` и `requestfailed` работает и заполняет соответствующие списки.
- Метод `findInteractiveElements` корректно возвращает селекторы с `nth` для однозначной идентификации.
- Зависимость Playwright объявлена как `api` в `build.gradle`.
- Локальный тест `PlaywrightActionsTest` проходит успешно (`BUILD SUCCESSFUL`).
- Код не содержит жестко заданных путей к браузеру (используется дефолтная установка Playwright в Docker-образе `mcr.microsoft.com/playwright/java`).
