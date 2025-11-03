# Это функция по имени Барсик 🐈

<img width="200" height="200" alt="barsik-mascot" src="https://github.com/user-attachments/assets/89a3d2b8-f1cf-47e7-b455-45afab207d14" /> 

Он хостится как функция в [Yandex Cloud Functions](https://yandex.cloud/ru/docs/functions/).

По задумке на вход он ничего не принимает, только периодически запускается
и делает полезную работу.

Его главная забота - поддерживать [паблик](https://vk.com/kittiesnemo) контентом.

Как Барсик будет это делать?

- Ходить по пабликам и искать музыку при помощи [YandexGPT](https://yandex.cloud/ru/docs/foundation-models/) по конкретным жанрам
- Искать подобных себе усачей на просторах Интернета, отбирать подходящих с помощью того же [YandexGPT](https://yandex.cloud/ru/docs/foundation-models/) и делиться ими в [паблике](https://vk.com/kittiesnemo)
- Просыпаться каждый день и заниматься данными задачами, самостоятельно принимая решение о необходимости нового контента на странице [паблика](https://vk.com/kittiesnemo)

Конфигурация Барсика собирается лежать в [Yandex Object Storage](https://yandex.cloud/en/services/storage).

Сообщать о своих трудностях Барсик будет в [Telegram](https://core.telegram.org/) чате.   

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

# Конфигурация Барсика

```yaml
# Настройки планировщика 
wallposts:
  # Сообщество ради которого Барсик трудится
  communityId: -161290464
  # Домен паблика Вконтакте
  domain: kittiesnemo
  # Ежедневное расписание
  dailySchedule:
    timeZone: Europe/Moscow
    # Кулдаун между двумя постами
    periodBetweenPostings: PT4H
    # Точки во времени, когда Барсик должен сделать пост
    checkpoints:
      - at: 09:00
        # Дополнительное время от момента в расписании,
        # используется для увеличения случайности, иначе PT0H - в тот же момент
        amortizationDuration: PT1H
      - at: 14:00
        amortizationDuration: PT1H
      - at: 20:00
        amortizationDuration: PT1H

# Настройки облака в Yandex.Cloud
cloud:
  function:
    # Каталога проекта
    folderId: b1gioucfterb2rnsqb1q

# Настройки для LLM
llm:
  # Настройки для модели текст-текст
  textGtp:
    model: gpt-oss-120b
    # Контракт интерфейса к модели YANDEX/OPENAI
    api: OPENAI
  # Настройки для модели текст+изображение-текст
  multiModalGpt:
    model: gemma-3-27b-it
    api: OPENAI
  # Пример промта для подбора музыки
  audioPromt:
    temperature: 0.3
    # Используется для обогащения контекста для модели об исполнителях из discogs API 
    discogsContext: 'В случае если ты совсем ничего не знаешь о данных исполнителях, то при оценке исполнителей ориентируйся на вспомогательные данные, полученные об исполнителях с сервиса discogs.com:'
    systemInstruction: 'Ты - опытный музыкальный слушатель и поклонник таких жанров как emo и midwest-emo, 
                                ты также иногда не прочь послушать жанры как math-rock, melodic-hardcore, pop-punk. 
                                Обычно ты используешь такие сервисы как bandcamp.com, spotify.com, last.fm, яндекс музыка.
                                
                                Пользователь будет тебе передавать данные о группах: группу, группа, и так далее.
                                На основе перечисленных групп от пользователя, оцени отношение от 0.00 до 1.00 этих групп к твоим любимым жанрам.
                                Ответ дай без комментариев и дополнительной информации. Твой формат ответа должен быть следующий: 
                                
                                {
                                    "result": [
                                        {
                                            "band": "название группы",
                                            "approval": <от 0.00 до 1.00>
                                        },
                                        {
                                            "band": "название группы",
                                            "approval": <от 0.00 до 1.00>
                                        }
                                    ]
                                }'
    
  # Пример промта для отбора картинок
  photoPromt:
    temperature: 1.0
    systemInstruction: 'Ты - любитель кошек, котов, котиков. Ты обладаешь своим сообществом, где делишься фотографиями этих животных.
                                Ты очень хочешь, чтобы твоим подписчикам понравился тот контент, который ты выбираешь для очередного поста в сообществе.
                                Ты тщательно проверяешь каждую фотографию на соответствие следующим требованиям:
                                 
                                - фото по краям не обрезано и не имеет никакого однотонного фона 
                                - фото в четком качестве 
                                - фото не имеет никаких иконок или водяных знаков 
                                - фото естественное, то есть снятое на телефон или камеру
                                - фото без надписей и текста 
                                - фото без человека
                                - фото без 18+ контента 
                                - фото без присутствия ран или ссадин 
                                - фото без крови
                                - на фото есть животное анатомически и физиалогически похожее на кошку или кота или котенка
                                - на фото нет специальных эффектов, которые могли бы быть наложены через такие программы как photoshop
                                
                                На основе перечисленных фотографий от пользователя, дай свой вердикт на соответствие твоим требованиям, где true - соответствует, а false - не соответствует.
                                Пользователь передаст тебе фото в таком порядке <идентификатор фото>, <само фото>, <идентификатор фото>, <само фото>, и так далее.
                                Ответ дай без комментариев, без дополнительной информации, без знаков форматирования, ответ никак не форматируй, оставь в чистом виде JSON-объект. 
                                Не используй в ответе символы ``` и слова json.
                                Твой формат ответа должен быть следующий:
                                
                                {
                                    "result": [
                                        {
                                            "photo": "идентификатор фото",
                                            "approval": true
                                        },
                                        {
                                            "photo": "идентификатор фото",
                                            "approval": false
                                        }
                                    ]
                                }'

# Настройки для поиска контента во Вконтакте
content:
  settings:
    # Набор данных для анализа перед конечной выборкой
    musicAttachmentsCollectorSize: 10
    # Разброс для подбора данных в каждом провайдере
    takeMusicAttachmentsPerProvider: 3
    imagesAttachmentsCollectorSize: 6
    takeImagesAttachmentsPerProvider: 2
    # Веса для отбора конечной выборки в музыке
    musicLlmApprovalThreshold: 0.6
    # Уникальность контента относительно паблика на протяжении времени (дни)
    attachmentsUniquenessDepthInDays: 30
  # Источники для контента
  providers:
    - provider: VK
      domain: -119717318
      media:
        - IMAGES
    - provider: VK
      domain: -125253023
      media:
        - MUSIC

# Настройки для отчетов в Telegram
telegram:
  report:
    chatId: -4972260104
```