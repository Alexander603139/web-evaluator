# Руководство по репозиторию

## Структура проекта и модули

Это **многомодульное Spring Boot 3.5 приложение** на Gradle (`com.webevaluator`) с 15 модулями:

| Категория | Модули |
|---|---|
| **Ядро** | `app` (точка входа), `core` (доменные модели и интерфейсы), `api` (REST-контроллеры и DTO), `security` |
| **Аутентификация** | `auth` — JWT + API-ключи |
| **Оркестрация** | `orchestrator` — жизненный цикл задач, планировщик, воркеры; `reporting` — хранение отчётов о сценариях |
| **Сценарии** | `scenario-monkey` (хаотичное тестирование), `scenario-bdd` (BDD), `scenario-exploratory` (BFS), `scenario-ai` (AI-driven) |
| **Инфраструктура** | `ui-actions` (обёртка Playwright), `crawler` (обход страниц), `ai-gateway` (интеграция с AI-провайдерами), `oracle` (валидация результатов) |

**Расположение кода:** `java` — `com.webevaluator.{module}` &nbsp;|&nbsp; `resources` — конфигурация и миграции Flyway &nbsp;|&nbsp; `test` — повторяет структуру `main`.

## Сборка, тестирование и запуск

```bash
./gradlew build          # Компиляция + тесты всех модулей
./gradlew :app:bootRun   # Локальный запуск приложения (порт 8080)
./gradlew :module:test   # Тесты одного модуля
./gradlew clean          # Очистка артефактов сборки
```

**Docker:**
```bash
docker compose up -d --build   # Сборка и запуск приложения + PostgreSQL
docker compose down -v         # Остановка + удаление контейнеров и тома БД
curl http://localhost:8080/actuator/health  # Проверка здоровья → {"status":"UP"}
```

## Стиль кода и именование

- **Java 21** — используйте records, `var`, pattern matching, sealed classes.
- **Lombok** — `@Data`, `@Builder`, `@RequiredArgsConstructor`; версия `lombokVersion=1.18.38`.
- **Пакеты:** `com.webevaluator.{module}.{subdomain}` (например, `com.webevaluator.orchestrator.service`).
- **Внедрение зависимостей:** конструкторное (`@RequiredArgsConstructor` на `@Service` / `@Controller`).
- **Конфигурация:** выносите в `application.yml` + переменные окружения (см. `app/src/main/resources/application.yml`).
- **Jackson:** `default-property-inclusion: always` — все поля сериализуются в JSON, включая `null`.

## Тестирование

- **Фреймворк:** JUnit 5 (`junit-jupiter`) с JUnit Platform.
- **Моки:** Mockito (`mockitoVersion=5.17.0`).
- **Именование:** `*Test.java` (например, `JobServiceTest.java`).
- **Запуск всех тестов:** `./gradlew test`
- **Запуск одного теста:** `./gradlew :module:test --tests com.webevaluator.module.service.JobServiceTest`
- Тесты находятся в `src/test/java` и повторяют структуру `main`.

## Миграции базы данных

Flyway управляет схемой в `app/src/main/resources/db/migration/`.

**Формат имён:** `V{версия}__описание.sql` — например, `V4__auth_enhancements.sql`.

Flyway выполняется автоматически при старте (`spring.flyway.enabled=true`). Hibernate настроен на `ddl-auto: validate` — никогда не создавайте и не изменяйте таблицы через JPA.

## Коммиты и pull requests

**Сообщения коммитов** в кратком императивном стиле:

```
fix V4__auth_enhancements.sql
stage 6: auth = JWT + API keys
fix MonkeyScenarioEngine.performAction + add @JsonInclude
```

- Без точки в конце.
- Префикс действия: `fix`, `feat`, `refactor`, `stage N:`.
- Указывайте затронутый файл или модуль, если уместно.

**Pull requests** должны:
- Иметь целевую ветку `main`.
- Содержать краткое описание изменений и причин.
- Проходить `./gradlew build` локально перед отправкой.

## Развёртывание

Пуш в `main` запускает GitHub Actions-воркфлоу (`.github/workflows/ci.yml`), который:
1. Подключается по SSH к серверу Timeweb.
2. Копирует репозиторий через SCP.
3. Собирает Docker-образы и запускает контейнеры.
4. Выполняет healthcheck (`/actuator/health`) — при неудаче деплой прерывается.

## Архитектура (обзор)

```
Клиент → api (REST) → orchestrator (управление задачами)
                           ↓
                 scenario-* (движки выполнения)
                           ↓
                 ui-actions (Playwright) + crawler (обход страниц)
                           ↓
                 oracle (валидация) → reporting (хранение результатов)
```

Аутентификация (`auth`) и безопасность (`security`) — сквозные модули, применяются через фильтры Spring Security.
