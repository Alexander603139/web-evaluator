# Структура проекта и база данных

Данный документ описывает базовую инфраструктуру проекта web-evaluator: многомодульную Gradle-архитектуру, контейнеризацию с PostgreSQL, миграции базы данных через Flyway и начальную конфигурацию Spring Boot приложения.

## 📁 Многомодульная архитектура

Проект организован как Gradle multi-module project с разделением ответственности между 15 модулями (`settings.gradle`):

### Базовые модули

- `app` — точка входа Spring Boot приложения, объединяет все модули в исполняемый JAR
- `core` — общие доменные сущности, интерфейсы и контракты, используемые другими модулями
- `api` — REST-контроллеры и DTO для внешнего API
- `security` — базовые компоненты безопасности

### Модули аутентификации

- `auth` — реализация JWT и API-ключей

### Оркестрация и отчётность

- `orchestrator` — управление жизненным циклом задач (Jobs), планировщик, worker
- `reporting` — сохранение и получение отчётов о выполненных сценариях

### Сценарии тестирования

- `scenario-monkey` — хаотичное тестирование (Monkey Testing)
- `scenario-bdd` — BDD-сценарии
- `scenario-exploratory` — исследовательское тестирование (BFS-обход)
- `scenario-ai` — AI-driven сценарии

### Инструменты взаимодействия

- `ui-actions` — обёртка над Playwright для взаимодействия с браузером
- `crawler` — обход страниц
- `ai-gateway` — интеграция с AI-провайдерами
- `oracle` — модуль оракула для валидации результатов

## 🐳 Docker Compose

Конфигурация `docker-compose.yml` описывает два сервиса:

### Сервис `db` (PostgreSQL)

- **Образ:** `postgres:15-alpine`
- **Контейнер:** `web-evaluator-db`
- **База данных:** `web_evaluator`
- **Healthcheck:** `pg_isready -U postgres -d web_evaluator` (интервал 10s, таймаут 5s, 5 попыток)
- **Том:** `postgres_data:/var/lib/postgresql/data` для персистентности данных
- **Политика перезапуска:** `unless-stopped`

### Сервис `app` (Spring Boot)

- **Сборка:** из локального `Dockerfile`
- **Контейнер:** `web-evaluator-app`
- **Порт:** `8080:8080`
- **Зависимость:** запускается только после healthcheck БД (`service_healthy`)
- **Переменные окружения:**
  - `DB_HOST=db`, `DB_PORT=5432`, `DB_NAME=web_evaluator`
  - `DB_USER=postgres`, `DB_PASSWORD=postgres`
  - `ADMIN_USERNAME=admin`, `ADMIN_PASSWORD=change_me_in_production`
- **Политика перезапуска:** `unless-stopped`

## 🐳 Dockerfile (многоэтапная сборка)

### Этап 1 — Сборка (`gradle:8.10-jdk21`)

```dockerfile
FROM gradle:8.10-jdk21 AS build
WORKDIR /home/gradle/project
COPY . .
RUN gradle clean :app:bootJar -x test --no-daemon
```

### Этап 2 — Runtime (Playwright Java)

```dockerfile
FROM mcr.microsoft.com/playwright/java:v1.49.0-noble
WORKDIR /app
COPY --from=build /home/gradle/project/app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Использование образа Playwright в runtime позволяет приложению запускать браузерные сценарии внутри контейнера.

## ⚙️ Конфигурация Spring Boot

Файл `app/src/main/resources/application.yml`:

```yaml
spring:
  application:
    name: web-evaluator
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:web_evaluator}
    username: ${DB_USER:postgres}
    password: ${DB_PASSWORD:postgres}
    driver-class-name: org.postgresql.Driver
  flyway:
    enabled: true
    locations: classpath:db/migration
  jpa:
    hibernate:
      ddl-auto: validate
  jackson:
    default-property-inclusion: always

server:
  port: ${SERVER_PORT:8080}

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics

app:
  admin:
    username: ${ADMIN_USERNAME:admin}
    password: ${ADMIN_PASSWORD:}
```

### Ключевые особенности

- Переменные окружения с дефолтами для всех критичных параметров БД и сервера
- Flyway включён с локацией `classpath:db/migration` для версионирования схемы БД
- `JPA ddl-auto: validate` — Hibernate только проверяет соответствие схемы, не создаёт таблицы автоматически (этим управляет Flyway)
- `Jackson default-property-inclusion: always` — все поля (включая пустые) попадают в JSON-ответы
- Actuator с эндпоинтами `health`, `info`, `metrics` для мониторинга

## 🗃️ Миграции базы данных

Миграции находятся в `app/src/main/resources/db/migration/`:

| Файл                             | Описание                                      |
|----------------------------------|-----------------------------------------------|
| `V1__init.sql`                   | начальная схема БД (фундамент для последующих таблиц) |
| `V2__domain_tables.sql`          | таблицы доменной модели                       |
| `V3__make_user_id_nullable.sql`  | последующие правки схемы                      |

Flyway автоматически применяет миграции при старте приложения и отслеживает их в таблице `flyway_schema_history`.

## 🔍 Проверки (Health checks)

После запуска контейнеров доступны следующие проверки:

```bash
# Статус контейнеров
docker compose ps

# Health check приложения
curl http://localhost:8080/actuator/health
# Ожидаемый ответ: {"status":"UP"}

# Логи запуска (проверка Flyway миграций)
docker compose logs app --tail 30
# Ожидается: "Successfully applied N migrations to schema"
```

## 🚀 Запуск

```bash
# Сборка и запуск
docker compose up -d --build

# Остановка
docker compose down

# Полная очистка (с удалением данных БД)
docker compose down -v
```
