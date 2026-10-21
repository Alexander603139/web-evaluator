# Этап 6: Аутентификация (JWT) и Авторизация (API Keys)

Данный документ описывает архитектуру, логику работы и проверки модуля безопасности, реализующего доступ к API через JWT-токены для пользователей и API-ключи для программных клиентов.

## 1. Назначение и границы ответственности

Модуль `auth` (в связке с `core`) отвечает за:

- Выпуск и валидацию JWT-токенов (Access и Refresh).
- Генерацию, хеширование и валидацию API-ключей.
- Интеграцию с Spring Security через кастомные фильтры для Stateless-аутентификации.
- Защиту эндпоинтов (например, `/api/v1/runs/**`), требующих наличия валидного токена или ключа.

**Чего модуль НЕ делает:**

- Не хранит пароли или ключи в открытом виде (используется BCrypt для паролей и SHA-256 для ключей и refresh-токенов).
- Не возвращает полный API-ключ после его создания (ключ выдается только один раз в момент генерации).

## 2. Архитектура и ключевые компоненты

### 2.1. Сервисы и Провайдеры

- **JwtTokenProvider:** Генерирует и валидирует JWT-токены. Использует алгоритм HS512 (`Keys.hmacShaKeyFor`). Предоставляет методы `generateAccessToken`, `generateRefreshToken`, `getUsernameFromToken`, `getRoleFromToken` и `validateToken`. Refresh-токены хешируются алгоритмом SHA-256 перед сохранением (`hashRefreshToken`).
- **ApiKeyService:**
  - Генерирует ключи с префиксом `wev_` и случайной hex-строкой.
  - Хеширует ключ алгоритмом SHA-256 перед сохранением в БД.
  - Метод `validateAndGetUser` обновляет поле `lastUsedAt` и возвращает реальный объект `User` для помещения в SecurityContext.

### 2.2. Фильтры безопасности

- **JwtAuthenticationFilter:** Перехватывает запросы с заголовком `Authorization: Bearer <token>`. Валидирует токен через `JwtTokenProvider`, извлекает `username` и `role`, формирует `UsernamePasswordAuthenticationToken` с ролью `ROLE_<role>` и помещает его в `SecurityContextHolder`.
- **ApiKeyAuthenticationFilter:** Перехватывает запросы с заголовком `X-API-Key`. При наличии ключа вызывает `ApiKeyService.validateAndGetUser(apiKeyHeader.trim())`. Если ключ валиден и активен, помещает соответствующего `User` и его `ROLE` в контекст. Если ключ невалиден или отозван, выбрасывает `BadCredentialsException`, что позволяет Spring Security корректно обработать ошибку и вернуть стандартизированный JSON-ответ.

### 2.3. Конфигурация (`AuthSecurityConfig`)

- Отключает CSRF и устанавливает `SessionCreationPolicy.STATELESS`.
- Разрешает анонимный доступ к `/api/v1/auth/**` и `/actuator/**`.
- Требует аутентификации для `/api/v1/runs/**` и всех остальных запросов.
- Настраивает кастомные JSON-ответы для ошибок 401 Unauthorized и 403 Forbidden через `AuthenticationEntryPoint` и `AccessDeniedHandler`.
- Регистрирует оба фильтра (`JwtAuthenticationFilter` и `ApiKeyAuthenticationFilter`) перед `UsernamePasswordAuthenticationFilter`.

## 3. Модель данных и Миграции

### 3.1. Сущность ApiKey (модуль core)

| Поле        | Тип          | Описание                                             |
|-------------|--------------|------------------------------------------------------|
| `id`        | `UUID`       | Первичный ключ.                                      |
| `user`      | `User`       | Владелец ключа (`ManyToOne`).                        |
| `name`      | `String`     | Человекочитаемое имя ключа.                          |
| `keyHash`   | `String`     | SHA-256 хеш ключа (уникальный).                      |
| `keyPrefix` | `String`     | Префикс ключа (`NOT NULL`, длина 12).                |
| `isActive`  | `boolean`    | Флаг активности (заменяет логику отзыва).            |
| `lastUsedAt`| `OffsetDateTime` | Время последнего использования.                  |
| `permissions`| `String`    | Метаданные прав (по умолчанию `"READ,WRITE"`).       |

### 3.2. Сущность RefreshToken (модуль core)

| Поле        | Тип          | Описание                                             |
|-------------|--------------|------------------------------------------------------|
| `id`        | `UUID`       | Первичный ключ.                                      |
| `user`      | `User`       | Ссылка на пользователя (`CASCADE delete`).           |
| `tokenHash` | `String`     | Уникальный хеш refresh-токена (`token_hash`).        |
| `expiresAt` | `OffsetDateTime` | Время истечения срока действия (`expires_at`).   |
| `revoked`   | `boolean`    | Флаг отзыва токена (по умолчанию `false`, `NOT NULL`). |

### 3.3. Ключевые миграции

- **`V2__domain_tables.sql`:** Базовое создание таблиц `users`, `api_keys` (с ограничением `NOT NULL` на `key_prefix`), `jobs`, `reports`.
- **`V4__auth_enhancements.sql`:**
  - Добавление колонок `is_active`, `permissions` в `api_keys`.
  - Создание таблицы `refresh_tokens` с колонкой `revoked` (строго `revoked`, а не `is_revoked`).
  - Создание индексов для быстрого поиска по `user_id` и `token_hash`.

## 4. API Контракт (Эндпоинты Этапа 6)

**POST `/api/v1/auth/login`**

- Тело: `{"username": "...", "password": "..."}`
- Ответ: `{"accessToken": "...", "refreshToken": "...", "tokenType": "Bearer", "expiresIn": 900000}`

**POST `/api/v1/auth/refresh`**

- Заголовок: `Authorization: Bearer <refreshToken>`
- Ответ: Новая пара токенов.

**POST `/api/v1/api-keys?name=...`**

- Заголовок: `Authorization: Bearer <accessToken>`
- Ответ: `{"id": "...", "name": "...", "apiKey": "wev_...", "permissions": "READ,WRITE"}` (поле `apiKey` в ответе есть только здесь).

**GET `/api/v1/api-keys`**

- Заголовок: `Authorization: Bearer <accessToken>`
- Ответ: Массив ключей пользователя (поле `apiKey` равно `null` в целях безопасности).

**DELETE `/api/v1/api-keys/{id}`**

- Заголовок: `Authorization: Bearer <accessToken>`
- Ответ: `204 No Content` (устанавливает `isActive = false`).

**POST `/api/v1/runs`**

- Заголовок: `Authorization: Bearer <accessToken>` ИЛИ `X-API-Key: wev_...`
- Ответ: `{"jobId": "...", "status": "PENDING"}` или `401 Unauthorized`.

## 5. Важные нюансы и уроки этапа

- **Обязательность `keyPrefix`:** Поскольку в миграции `V2` колонка `key_prefix` имеет ограничение `NOT NULL`, в `ApiKeyService` перед вызовом `save()` обязательно должно быть установлено значение `.keyPrefix("wev")`. Иначе возникнет `DataIntegrityViolationException`.

- **Единообразие именования `revoked`:** В таблице `refresh_tokens` и в Java-сущности `RefreshToken` колонка флага отзыва называется строго `revoked` (аннотация `@Column(name = "revoked", nullable = false)`). Использование `is_revoked` недопустимо и приводит к `SchemaManagementException`.

- **Обработка ошибок в фильтрах:** `ApiKeyAuthenticationFilter` выбрасывает `BadCredentialsException` при невалидном ключе, а не использует `response.sendError()`. Это позволяет `AuthenticationEntryPoint` перехватить исключение и вернуть корректный JSON-ответ с сообщением об ошибке, вместо стандартного HTML-ответа сервера.

- **Техническое наблюдение:** В файле `ApiKeyRepository.java` на данный момент присутствует метод `List<ApiKey> findByUserIdAndRevokedAtIsNull(UUID userId)`. Поскольку в сущности `ApiKey` используется поле `isActive`, а не `revokedAt`, этот метод не соответствует модели данных и может вызвать `PropertyReferenceException` при инициализации Spring Data JPA. Рекомендуется его удаление, если он не используется.

## 6. Проверки (Definition of Done)

### Локальные проверки

- `./gradlew clean build` завершается успешно.
- Приложение стартует без ошибок Schema-validation и PropertyReferenceException.
- `POST /api/v1/auth/login` возвращает валидные JWT-токены.
- `POST /api/v1/api-keys` возвращает ключ, начинающийся с `wev_`, и запись появляется в БД с заполненным `key_prefix`.
- `POST /api/v1/runs` с заголовком `X-API-Key` успешно создает задачу (`PENDING`).
- `DELETE /api/v1/api-keys/{id}` отзывает ключ, и последующий запрос с этим же ключом возвращает `401 Unauthorized` с сообщением `"Invalid or revoked API Key"`.

### Проверки на VPS

- Деплой через GitHub Actions проходит успешно, контейнер `web-evaluator-app` имеет статус `Up` (не `Restarting`).
- `curl -s http://localhost:8080/actuator/health` возвращает `{"status":"UP"}`.
- Полный цикл проверок (логин → создание ключа → запуск задачи → отзыв ключа → проверка блокировки) проходит успешно через `curl` на сервере.
