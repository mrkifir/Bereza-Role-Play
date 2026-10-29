# Bereza RP Launcher (Android)

Android-лаунчер для SA-MP RP-сервера **Bereza Role Play**: встановлення й оновлення файлів за маніфестом із перевіркою SHA-256, докачування, безпечне розпакування, отримання клієнта через системний інсталятор, реальний онлайн сервера (SA-MP query) і запуск клієнта з передачею адреси/порту/ніка.

Kotlin · Jetpack Compose · Material 3 · Coroutines · OkHttp · kotlinx.serialization · DataStore.
minSdk 26 (Android 8.0), targetSdk 35.

---

## 1. Структура проєкту

```
BerezaLauncher/
├─ .github/workflows/build-apk.yml   ← збірка APK у хмарі GitHub (можна з телефона)
├─ settings.gradle.kts, build.gradle.kts, gradle.properties
├─ gradle/libs.versions.toml         ← усі версії залежностей
├─ gradle/wrapper/                   ← Gradle 8.14.3
├─ server-example/
│  ├─ make_manifest.py               ← генерує manifest.json (розміри + SHA-256)
│  ├─ manifest.example.json
│  └─ news.example.json
└─ app/src/main/
   ├─ AndroidManifest.xml
   ├─ assets/launcher_config.json    ← ВАША конфігурація (адреса, URL маніфесту, новин)
   ├─ res/                           ← іконка (векторний знак), тема, рядки
   └─ java/ua/bereza/launcher/
      ├─ BerezaApp.kt                ← контейнер залежностей, канал сповіщень
      ├─ MainActivity.kt
      ├─ core/Core.kt                ← HTTP, JSON, коди помилок, журнал, форматування
      ├─ data/
      │  ├─ model/Models.kt          ← конфіг, маніфест, новини, стан встановлення
      │  ├─ ConfigRepository.kt
      │  ├─ ManifestRepository.kt    ← ManifestRepository + перевірка шляхів (SafePath)
      │  ├─ NewsRepository.kt
      │  ├─ SettingsRepository.kt    ← нік, сховище, Wi-Fi, перевірка нікнейму
      │  └─ Storage.kt               ← папка гри, вільне місце, атомарний стан
      ├─ install/
      │  ├─ DownloadManager.kt       ← HTTP Range, повтори, скасування
      │  ├─ FileVerifier.kt          ← потоковий SHA-256
      │  ├─ ArchiveExtractor.kt      ← ZIP із захистом від Zip Slip і zip-бомб
      │  ├─ GameAssetsProvider.kt    ← Remote (ваш HTTPS) + UserOwned (копія гравця)
      │  ├─ GameInstaller.kt         ← увесь алгоритм встановлення/оновлення
      │  ├─ InstallController.kt, InstallService.kt (foreground), InstallModels.kt
      ├─ client/
      │  ├─ ClientProvider.kt        ← перевірка, завантаження, підпис, PackageInstaller
      │  └─ PackageInstallReceiver.kt
      ├─ launch/GameLauncher.kt      ← GameLauncher + ServerConnector
      ├─ server/ServerStatus.kt      ← SA-MP query (UDP) або ваш HTTP API
      └─ ui/
         ├─ theme/Theme.kt           ← дизайн-система «Береста»
         ├─ components/              ← логотип, іконки, прогрес «Кора», кнопки, картки
         ├─ screens/                 ← 7 екранів + діалог ніку
         ├─ BerezaNavHost.kt, MainViewModel.kt
```

---

## 2. Збірка APK

### Варіант А — з телефона через GitHub Actions (без комп'ютера)

1. Створіть на GitHub новий **порожній** репозиторій, напр. `bereza-launcher`.
2. Розпакуйте архів проєкту в Termux:
   ```
   cd ~/storage/downloads && unzip BerezaLauncher.zip && cd BerezaLauncher
   ```
3. Відредагуйте `app/src/main/assets/launcher_config.json` (розділ 3).
4. Завантажте код:
   ```
   git init && git add . && git commit -m "Bereza launcher"
   git branch -M main
   git remote add origin https://github.com/<ваш-логін>/bereza-launcher.git
   git push -u origin main
   ```
   Пароль — це **Personal Access Token** (GitHub → Settings → Developer settings → Tokens).
5. На GitHub: вкладка **Actions** → «Build Bereza Launcher APK». Збірка триває ~5–8 хв.
   Очікуйте: зелена галочка.
6. Відкрийте запуск → внизу **Artifacts** → `bereza-launcher-debug` → завантажиться ZIP з `app-debug.apk`.
7. Встановіть APK (дозвольте встановлення з цього джерела).

Якщо збірка червона — відкрийте крок «Build debug APK», скопіюйте перші рядки з `e:` і надішліть мені — виправлю.

### Варіант Б — Android Studio

Відкрийте папку `BerezaLauncher` → дочекайтеся синхронізації Gradle → **Build → Build APK(s)**.
Або в терміналі: `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.

### Релізна збірка

```
export BEREZA_KEYSTORE=/шлях/bereza.jks
export BEREZA_KEYSTORE_PASSWORD=...
export BEREZA_KEY_ALIAS=bereza
export BEREZA_KEY_PASSWORD=...
./gradlew assembleRelease
```
Ключ створюється один раз: `keytool -genkey -v -keystore bereza.jks -alias bereza -keyalg RSA -keysize 2048 -validity 10000`.
Не втрачайте його — інакше оновлення лаунчера не встановляться поверх.

---

## 3. Конфігурація — `app/src/main/assets/launcher_config.json`

| Поле | Що вписати |
|---|---|
| `server.name` | Назва сервера на екрані |
| `server.host` / `server.port` | IP або домен SA-MP сервера і порт (зазвичай 7777) |
| `server.clientVersion` | Версія SA-MP, напр. `0.3.7` (лише для показу) |
| `manifestUrl` | **HTTPS** адреса `manifest.json` |
| `newsUrl` | HTTPS адреса `news.json` (порожньо — новини вимкнено) |
| `statusApiUrl` | Необов'язково. Порожньо — онлайн береться прямо з сервера по SA-MP query |
| `minLauncherVersionCode` | Мінімальна версія лаунчера |
| `requireRpNickname` | `true` — нік лише у форматі `Ім'я_Прізвище` |
| `siteUrl`, `discordUrl`, `telegramUrl` | Посилання спільноти (необов'язково) |

Поки залишається `CHANGE_ME`, лаунчер показує банер «Режим налаштування» і не намагається нічого завантажувати.

Блок `server` у маніфесті (якщо є) перевизначає локальний — адресу сервера можна змінити без перезбирання APK.

---

## 4. Сервер оновлень

Підійде будь-який статичний HTTPS-хостинг, що підтримує заголовок `Range` (nginx, Cloudflare R2/Pages, VPS, GitHub Releases).

1. Покладіть файли збірки в папку, напр. `release/1.0.0/`.
2. Згенеруйте маніфест (Termux: `pkg install python`):
   ```
   python3 server-example/make_manifest.py release/1.0.0 \
     --base-url https://cdn.example.com/bereza/1.0.0/ \
     --version 1.0.0 \
     --extract bereza/textures.zip:bereza/textures/ \
     --out manifest.json
   ```
   Очікуйте: список файлів і рядок «Готово: manifest.json · файлів N».
3. Завантажте файли і `manifest.json` на хостинг. URL маніфесту має збігатися з `manifestUrl`.
4. Для оновлення: нова папка версії → новий маніфест із новою `gameVersion`. Лаунчер завантажить **лише змінені** файли, а файли, яких немає в новому маніфесті, видалить.

Поля файлу в маніфесті: `path`, `size`, `sha256`, `source` (`remote` / `user`), `url` (можна відносний), `extract`, `extractTo`, `unpackedSize`, `userFileName`, `acceptAnyHash`, `optional`. Приклад — `server-example/manifest.example.json`.

---

## 5. Джерела файлів і клієнт — що потрібно надати

Лаунчер **не містить і не завантажує** GTA San Andreas або сторонній клієнт SA-MP. Щоб гравець потрапив на сервер автоматично, потрібні три речі, які визначаєте ви:

**5.1. Ваші файли (`source: "remote"`)** — моди, текстури, конфіги, на які у вас є права. Працює одразу.

**5.2. Файли оригінальної гри (`source: "user"`)** — гравець один раз вказує папку своєї легальної копії (Налаштування → «Файли оригінальної гри»), лаунчер копіює потрібні файли й звіряє SHA-256.
Обмеження Android 11+: системний діалог не дає вибрати `Android/data` та `Android/obb` — гравцю треба скопіювати файли в звичайну папку (напр. `Download/GTA`).

**5.3. Сумісний Android-клієнт SA-MP** — блок `client` маніфесту. Офіційного Android-клієнта SA-MP немає; клієнт, який ви оберете, визначає все інше. Від його розробника/документації потрібно отримати:

| Що | Поле маніфесту |
|---|---|
| Package name клієнта | `client.packageName` |
| Мінімальна версія (versionCode) | `client.minVersionCode` |
| SHA-256 сертифіката підпису (захист від підробок) | `client.signingCertSha256` |
| APK — **лише якщо маєте право розповсюдження** | `client.apkUrl`, `apkSha256`, `apkSize` |
| Інакше — сторінка, де гравець бере клієнт сам | `client.infoUrl` |
| Звідки клієнт читає файли гри | режим сховища в налаштуваннях лаунчера |
| Як клієнт приймає адресу/порт/нік | блок `launch` (нижче) |

Блок `launch` — нічого не вигадано, ви вписуєте те, що підтримує клієнт:

- `"mode": "none"` — лише запуск; адреса копіюється в буфер, гравець вставляє вручну.
- `"mode": "intent_extras"` + `"extras": {"<ключ>": "{host}", "<ключ2>": "int:{port}", "<ключ3>": "{nick}"}`.
- `"mode": "uri"` + `"uriTemplate": "<схема>://{host}:{port}?name={nick}"`.
- `"configFile": {"path": "<файл відносно папки гри>", "template": "...{nick}...{host}...{port}..."}` — якщо клієнт читає нік/сервер із файлу налаштувань. Можна поєднувати з будь-яким режимом.
- `"activity"` — повне ім'я Activity, якщо запускати треба не головний екран.

Підстановки: `{host}`, `{port}`, `{nick}`, `{password}`, `{server_name}`.

Отримання APK завжди йде через системний **Package Installer** із підтвердженням гравця; лаунчер перевіряє SHA-256 файлу, package name і підпис.

---

## 6. Що працює одразу, а що чекає на зовнішні дані

| Частина | Стан |
|---|---|
| Інтерфейс, навігація, налаштування, нік, діагностика | працює одразу |
| Завантаження за маніфестом, докачування Range, SHA-256, повторне завантаження пошкоджених | працює, щойно є HTTPS-хостинг і маніфест |
| Розпакування ZIP, перевірка місця, відновлення після переривання, видалення застарілих файлів | працює з маніфестом |
| Онлайн/пінг сервера | працює, щойно вписано реальні `host`/`port` (стандартний SA-MP query) |
| Новини | працюють, щойно є `news.json` |
| Імпорт файлів із копії гравця | працює, потрібні правильні SHA-256 у маніфесті |
| Встановлення клієнта | потрібен клієнт із правом розповсюдження (`apkUrl`) або `infoUrl` |
| Автопідключення | залежить від клієнта: потрібні його ключі extras / URI-схема / формат файлу налаштувань |

Без налаштованого клієнта кнопка «Грати» показує екран помилки `BZ-CLI` з поясненням, що саме не задано, — а не імітує запуск.

**Важливо:** цей проєкт не був зібраний у середовищі, де я його писав (там заблоковано репозиторії Google/Maven). Синтаксис перевірено компілятором Kotlin, версії залежностей узгоджені між собою, але першу справжню збірку зробить GitHub Actions / Android Studio. Якщо вона видасть помилки — надішліть рядки з `e:`.

---

## 7. Дизайн-система «Береста»

| Рішення | Де в коді |
|---|---|
| Палітра: сажа `#0B0C0C`, графіт `#141716`, кора `#ECE9E1`, лист `#8CC63F`; бурштин — оновлення, жар — помилки | `ui/theme/Theme.kt` → `Bz` |
| Типографіка: стиснений гротеск (заголовки, кнопки, розрядка), моно — для чисел і хешів | `Theme.kt` → `BzTypography` |
| Знак: стовбур берези з насічками + два листки = літера «B» | `components/Brand.kt` → `BerezaMark`, `res/drawable/ic_launcher_foreground.xml` |
| Прогрес «Кора»: сегменти різної довжини, як насічки на корі; без вигаданих відсотків | `components/Components.kt` → `BarkProgress` |
| Кнопка «Грати»: зрізаний кут (надріз), три насічки, стискання при натисканні | `Components.kt` → `PlayButton` |
| Фоновий мотив: тонкі стовбури з дуже низькою непрозорістю | `Brand.kt` → `BirchBackdrop` |
| Власні лінійні іконки | `components/Glyphs.kt` |
| Головний екран: адаптивна сітка (1 колонка — телефон, 2 — планшет ≥720dp) | `screens/HomeScreen.kt` |
| Переходи: fade + короткий зсув | `ui/BerezaNavHost.kt` |
