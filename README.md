# Это функция по имени Барсик 🐈

<img width="200" height="200" alt="barsik-mascot" src="https://github.com/user-attachments/assets/89a3d2b8-f1cf-47e7-b455-45afab207d14" />

Барсик — это автоматизированный агент для управления контентом сообщества ВКонтакте, работающий на базе AI и размещённый в [Yandex Cloud Functions](https://yandex.cloud/ru/docs/functions/).

## Описание

Барсик самостоятельно управляет [пабликом](https://vk.com/kittiesnemo), автоматически публикуя музыкальный контент и изображения. Он использует большие языковые модели для принятия решений о публикациях и отбора качественного контента.

### Основные возможности

- 🎵 **Музыкальный куратор**: Ищет музыку по профилю жанров в сообществах ВКонтакте, используя LLM для оценки релевантности треков
- 🐱 **Отбор изображений**: Находит качественные фотографии с помощью мультимодальной AI модели, проверяя соответствие критериям качества
- 🤖 **Автономное принятие решений**: Анализирует активность сообщества и самостоятельно решает, когда публиковать новый контент
- 📊 **Аналитика уникальности**: Проверяет уникальность контента относительно истории публикаций сообщества
- 📢 **Отчётность**: Отправляет отчёты о своей работе в Telegram

## Архитектура

### Технологический стек

- **Язык**: Kotlin 2.2.0
- **Runtime**: JVM 21
- **Фреймворк**: Spring (для DI и конфигурации)
- **AI Framework**: [Embabel Agent SDK](https://github.com/embabel) v0.3.4 — для построения AI-агентов
- **Serverless**: Yandex Cloud Functions
- **Конфигурация**: YAML (хранится в Yandex Object Storage S3)
- **API интеграции**:
  - VK API — для работы с контентом ВКонтакте
  - Yandex LLM API (YandexGPT) — для LLM моделей
  - Telegram Bot API — для отправки отчётов
  - Discogs API — для обогащения контекста о музыкальных исполнителях
  - AWS S3 SDK — для работы с Yandex Object Storage

### Компоненты системы

```
┌─────────────────────────────────────────────────┐
│         Yandex Cloud Function                   │
│                  (YcHandler)                    │
└────────────────┬────────────────────────────────┘
                 │
                 ├─> Загрузка конфигурации из S3
                 │
                 ├─> Инициализация Spring Context
                 │
                 └─> Запуск AgentPlatform
                     │
                     ├─> VkCommunityContentManagerAgent
                     │   ├─> collectCommunityContent()
                     │   ├─> givesNewContentPublishVerdict()
                     │   ├─> findAppropriateMusicMedia()
                     │   ├─> findAppropriateImageMedia()
                     │   └─> publishNewWallpost()
                     │
                     ├─> API Clients
                     │   ├─> VkApi
                     │   ├─> TelegramApi
                     │   ├─> DiscogsApi
                     │   ├─> ImagesApi
                     │   └─> YandexS3Api
                     │
                     └─> LLM Models
                         ├─> Generic Model (gpt-oss-120b)
                         └─> Multi-Modal Model (gemma-3-27b-it)
```

## Конфигурация

### Переменные окружения

Приложение использует Spring для загрузки конфигурации из Yandex Object Storage S3. Основные параметры передаются через переменные окружения:

#### Обязательные переменные

| Переменная | Описание |
|-----------|----------|
| `CONFIGURATION_S3_BUCKET` | Bucket в S3 для хранения конфигурации (например, `yabarsik`) |
| `CONFIGURATION_S3_OBJECT_ID` | Имя файла конфигурации в S3 (например, `application-production.yaml`) |
| `AWS_ACCESS_KEY_ID` | Ключ доступа к Yandex Object Storage |
| `AWS_SECRET_ACCESS_KEY` | Секретный ключ для Yandex Object Storage |
| `YANDEX_CLOUD_LLM_API_KEY` | API ключ для доступа к Yandex LLM API |
| `VK_SERVICE_ACCESS_TOKEN` | Сервисный токен VK для доступа к API |
| `VK_COMMUNITY_ACCESS_TOKEN` | Токен сообщества VK для публикации постов |
| `TELEGRAM_TOKEN` | Токен Telegram бота для отправки отчётов |
| `DISCOGS_TOKEN` | Токен для Discogs API |

### Конфигурационные файлы

#### `application-production.yaml` / `application-testing.yaml`

```yaml
# Настройки Embabel Agent Platform
embabel:
  models:
    default-llm: generic-model  # Модель по умолчанию
  agent:
    platform:
      action-qos:
        maxAttempts: 3           # Максимум попыток выполнения действия
      llm-operations:
        data-binding:
          max-attempts: 2        # Попытки парсинга ответа LLM

# Настройки Yabarsik
yabarsik:
  # Yandex Object Storage (S3)
  s3:
    access-key-id: ${AWS_ACCESS_KEY_ID}
    secret-access-key: ${AWS_SECRET_ACCESS_KEY}
    configuration-object-id: ${CONFIGURATION_S3_OBJECT_ID}
    configuration-bucket: ${CONFIGURATION_S3_BUCKET}

  # ВКонтакте
  vk:
    service-token: ${VK_SERVICE_ACCESS_TOKEN}
    community-token: ${VK_COMMUNITY_ACCESS_TOKEN}
    community:
      id: -161290464              # ID сообщества (production)
      domain: kittiesnemo         # Домен сообщества (production)

  # Telegram
  telegram:
    token: ${TELEGRAM_TOKEN}
    report:
      chatId: -4972260104         # ID чата для отчётов

  # Discogs
  discogs:
    token: ${DISCOGS_TOKEN}

  # OpenAI-совместимый API для Yandex LLM
  openai:
    base-url: https://llm.api.cloud.yandex.net
    api-key: ${YANDEX_CLOUD_LLM_API_KEY}

    # Текстовая модель (для анализа музыки и принятия решений)
    generic-model:
      name: "gpt://b1gioucfterb2rnsqb1q/gpt-oss-120b"
      retry:
        max-attempts: 3
        backoff-initial-interval-millis: 2000
        backoff-multiplier: 5.0
        backoff-max-interval-millis: 180000

    # Мультимодальная модель (для анализа изображений)
    multi-modal-generic-model:
      name: "gpt://b1gioucfterb2rnsqb1q/gemma-3-27b-it"
      retry:
        max-attempts: 3
        backoff-initial-interval-millis: 2000
        backoff-multiplier: 5.0
        backoff-max-interval-millis: 180000

  # Источники контента (сообщества ВКонтакте)
  content:
    providers:
      # Примеры источников для изображений
      - provider: VK
        domain: -119717318
        media:
          - IMAGES
      - provider: VK
        domain: -122103467
        media:
          - IMAGES

      # Примеры источников для музыки
      - provider: VK
        domain: -125253023
        media:
          - MUSIC
      - provider: VK
        domain: -18014153
        media:
          - MUSIC
```

### Промпт-шаблоны (Jinja2)

Барсик использует Jinja2 шаблоны для генерации промптов к LLM. Шаблоны находятся в `src/main/resources/prompts/`:

#### `publishNewContentVerdict.jinja`
Анализирует последние посты и принимает решение о публикации.

#### `findAppropriateMusicMedia.jinja`
Выбирает наиболее подходящий трек из коллекции.

#### `findAppropriateImageMedia.jinja`
Оценивает качество и соответствие изображений критериям.

# Деплой Барсика

Для упрошенного деплоя окружений были написаны таски

```kotlin
tasks.register<Exec>("ycDeployFunctionProduction")
tasks.register<Exec>("ycDeployFunctionTesting")
```

Переменные окружения и секреты для деплоя
```kotlin
// переменная для id-объекта конфигурации в S3
"--environment=CONFIGURATION_S3_OBJECT_ID=configuration.yml,CONFIGURATION_S3_BUCKET=yabarsik"

// переменная для доступа к телеграм каналу
"--secret=environment-variable=TELEGRAM_TOKEN,id=e6qunf2om3830utk4li6,key=token"

// переменная для доступа к конфигурации в S3
"--secret=environment-variable=AWS_ACCESS_KEY_ID,id=e6q7hvehrvtsf655otla,key=key-identifier"

// переменная для доступа к конфигурации в S3
"--secret=environment-variable=AWS_SECRET_ACCESS_KEY,id=e6q7hvehrvtsf655otla,key=secret-key"

// переменная для доступа к Yandex.Cloud Api-Key
"--secret=environment-variable=YANDEX_CLOUD_LLM_API_KEY,id=e6qeql4qbf61n4hcjkf4,key=secret-key"

// переменная для доступа к сервисному ключу Вконтакте
"--secret=environment-variable=VK_SERVICE_ACCESS_TOKEN,id=e6q9r81vfhv26ue9uumv,key=service"

// переменная для доступа к ключу сообщества Вконтакте (см. Конфигурация Барсика, communityId/domain)
"--secret=environment-variable=VK_COMMUNITY_ACCESS_TOKEN,id=e6q9r81vfhv26ue9uumv,key=community"

// переменная для доступа к токену DiscogsAPI
"--secret=environment-variable=DISCOGS_TOKEN,id=e6qos1a86pmne2pehkgh,key=token"
```