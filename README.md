# Bereza Role Play — Android-лаунчер для SA-MP

Власний лаунчер проєкту Bereza RP: Kotlin, Jetpack Compose, Material 3, MVVM, DataStore, SAF.
Мінімальна версія — Android 8.0 (API 26), цільова — API 35.

> **Статус збірки.** Проєкт написано повністю, але в середовищі, де його створено, не було доступу
> до Android SDK і Google Maven, тому **тут він не компілювався**. Перша збірка — через GitHub Actions
> (розділ 2). Якщо збірка впаде — скопіюй текст помилки з логу Actions, і я виправлю.

---

## 1. Що вміє

| Функція | Як працює |
|---|---|
| Статус сервера, гравці, пінг | Реальний UDP-запит **SA-MP Query** (`'i'` і `'r'`) напряму до сервера. Кирилиця — windows-1251. Автооновлення кожні 30 с, поки екран відкритий. |
| Кнопка «ГРАТИ» | Перевіряє нікнейм → зберігає → відкриває встановлений клієнт (`getLaunchIntentForPackage`) → копіює `IP:порт` у буфер обміну. |
| Нікнейм | Правила SA-MP (3–20 символів, `A-Z a-z 0-9 _ [ ] ( ) $ @ . =`) + RP-формат `Ім'я_Прізвище` (вимикається в конфігурації). |
| Новини, «Останні оновлення» | Лише з твоїх JSON-файлів за https. Немає адреси — показується «не налаштовано», нічого не вигадується. |
| Файли гри | Користувач обирає папку через системний провідник (SAF). Перевірка за маніфестом: наявність, розмір, SHA-256. |
| Маніфест | Кнопка «Створити маніфест з цієї папки» сканує твою робочу копію і записує JSON з SHA-256 кожного файлу. Його можна вбудувати в APK. |
| Встановлення | Копіювання папки → папка гри з реальним прогресом (байти/файли), скасуванням, пропуском однакових файлів. |
| Оновлення лаунчера | `version.json` → завантаження APK по https → обов'язкова SHA-256 → перевірка пакета і versionCode → системний інсталятор. |
| Сумісність | Чек-лист: Android, ABI, інтернет, клієнт, папка, маніфест + що встановити легально. |
| Діагностика | Звіт (пристрій, мережа, сервер, клієнт, папка) + журнал подій; копіювання/надсилання. |

Дозволи: `INTERNET`, `ACCESS_NETWORK_STATE`, `REQUEST_INSTALL_PACKAGES` (лише для оновлення самого лаунчера). Доступу до всієї пам'яті лаунчер **не** просить.

---

## 2. Збірка APK з телефону (GitHub Actions)

1. На GitHub створи **новий** репозиторій, напр. `Bereza-Launcher` (можна приватний).
2. Завантаж туди **вміст** архіву (папки `app`, `gradle`, `.github` і файли з кореня). Зручно з телефону:
   Termux → `pkg install git` → розпакувати архів → `git init`, `git add .`, `git commit -m init`,
   `git branch -M main`, `git remote add origin https://github.com/mrkifir/Bereza-Launcher.git`, `git push -u origin main`
   (пароль — Personal Access Token).
   Перевір: у репозиторії має бути видно папку `.github/workflows` з файлом `build-apk.yml`.
3. **Ключ підпису** (один раз, щоб оновлення ставились поверх старої версії):
   репозиторій → Settings → Secrets and variables → Actions → New repository secret:
   - `BEREZA_KEYSTORE_B64` — весь вміст файлу `BEREZA_KEYSTORE_B64.txt`
   - `BEREZA_KEY_PASSWORD` — вміст `BEREZA_KEY_PASSWORD.txt`

   Без секретів збірка теж пройде, але APK буде підписано тимчасовим ключем, і кожна нова збірка
   встановлюватиметься тільки після видалення старої.
   **Файл `bereza-release.jks` і пароль збережи окремо — не клади їх у репозиторій.**
4. Вкладка **Actions** → «Build APK» → має з'явитися запуск (або натисни **Run workflow**). Чекай ~5–8 хв.
   Очікуваний результат: зелена галочка.
5. Відкрий запуск → внизу **Artifacts** → `BerezaLauncher-apk` → завантажиться zip з `app-release.apk`
   і `.sha256`. Розпакуй і встанови APK.

Кожен `push` у `main` збирає новий APK автоматично.

## 3. Збірка в Android Studio (на ноутбуці)

1. Android Studio Ladybug (2024.2) або новіша → **Open** → папка `BerezaLauncher`.
2. Дочекайся Gradle Sync (вперше завантажить залежності).
3. **Build → Build App Bundle(s) / APK(s) → Build APK(s)** — APK буде в `app/build/outputs/apk/`.
   Або з терміналу: `./gradlew assembleRelease`.

---

## 4. Налаштування сервера (IP, порт, посилання)

Усе в одному файлі — **`bereza.properties`** у корені:

```properties
server.name=Bereza Role Play
server.host=188.127.241.74
server.port=2406
client.package=game.samp.ru
client.name=SAMPMobile
nickname.rpFormat=true
news.url=
update.url=
links.discord=
links.site=
links.telegram=
launcher.versionCode=1
launcher.versionName=1.0.0
```

Після зміни — перезібрати APK (push у GitHub). Порожнє посилання = кнопка не показується.

### Формат `news.url` (JSON, тільки https)

```json
{
  "news": [
    { "id": "1", "date": "2026-10-29", "title": "Відкриття сервера",
      "body": "Текст новини", "url": "https://...", "tag": "подія" }
  ]
}
```

### Формат `update.url` (version.json)

```json
{
  "versionCode": 2,
  "versionName": "1.1.0",
  "date": "2026-11-05",
  "apkUrl": "https://.../app-release.apk",
  "sha256": "64 hex-символи з файлу .sha256 з Actions",
  "size": 9876543,
  "changelog": ["Новий екран", "Виправлено помилки"]
}
```

Де зберігати: будь-який https-хостинг статичних файлів. Варіант — **публічний** репозиторій
з цими JSON і посиланням `https://raw.githubusercontent.com/<user>/<repo>/main/news.json`
(з приватного репозиторію raw-посилання без токена не відкриються). APK для оновлень — у GitHub Releases.

Порядок релізу оновлення: збільш `launcher.versionCode` → push → візьми APK і `.sha256` з Actions →
завантаж APK у Releases → онови `version.json`.

---

## 5. Маніфест файлів гри

Вбудований маніфест — `app/src/main/assets/game_manifest.json` (зараз порожній, бо точна структура
файлів твого клієнта мені невідома, а вигадувати її не можна).

Як заповнити:
1. У лаунчері: вкладка **Файли** → «Обрати папку гри» → обери робочу папку клієнта (напр. `MordorMobile`).
2. «Створити маніфест з цієї папки» → збережи `bereza_manifest.json`. Він одразу стає активним.
3. Щоб маніфест був у всіх гравців — заміни ним `app/src/main/assets/game_manifest.json` і перезбери.
   Можна вручну залишити лише важливі файли або поставити `"required": false` для необов'язкових.

Обмеження Android 11+: системний провідник не дає обрати `Android/data/...` — це правило Android,
не лаунчера. Папки у корені пам'яті (як `MordorMobile`) обираються нормально.

---

## 6. Інтеграція ігрового клієнта

Поточна реалізація — `launcher/PackageLaunchAdapter.kt`:
- шукає пакет із `client.package` (або з налаштувань лаунчера);
- відкриває його стандартним `PackageManager.getLaunchIntentForPackage()` (як з іконки);
- копіює `IP:порт` у буфер обміну і показує, який нік указати.

**Чому не підключення «одним натиском»:** SAMPMobile (і відомі мені інші мобільні клієнти SA-MP)
не мають **документованого** intent / URI-схеми для передачі адреси і ніка. Вигадувати їх не можна —
непрацюючий виклик просто зламає кнопку. Тому `supportsDirectConnect = false`.

Як додати пряме підключення, коли розробник клієнта дасть документований спосіб:
1. Створи клас, напр. `launcher/DirectConnectAdapter.kt`, що реалізує `GameClientLauncher`.
2. У `launch()` сформуй intent **точно за документацією клієнта**, поверни
   `LaunchResult.Started(directConnect = true, addressCopied = false)`.
3. Встанови `supportsDirectConnect = true`.
4. В `AppContainer.clientLauncher()` поверни новий адаптер замість `PackageLaunchAdapter`.

Інший варіант — клієнт читає налаштування з файлу (нік, сервер). Якщо ти **сам перевіриш** формат
такого файлу у своєму клієнті, адаптер може записувати його через SAF у вже обрану папку гри
(доступ на запис лаунчер отримує при виборі папки). Повідом формат — допишу.

`<queries>` у маніфесті відкриває видимість застосунків з іконкою в лаунчері (Android 11+), тому
змінений у налаштуваннях пакет теж знаходиться — без дозволу `QUERY_ALL_PACKAGES`.

---

## 7. Компоненти, що потребують окремої легальної залежності

| Компонент | Хто надає | У лаунчері |
|---|---|---|
| GTA: San Andreas (Android) | Rockstar Games (Google Play) | Не входить, не завантажується |
| Android-клієнт SA-MP (напр. SAMPMobile) | Розробник клієнта | Лише перевірка наявності й запуск |
| Ігрові файли клієнта | Офіційний інсталятор клієнта | Вибір, копіювання, перевірка SHA-256 |
| JSON новин/оновлень, APK оновлень | Ти (хостинг) | Читання по https |
| Сервер SA-MP / open.mp | Ти (hostgta) | Запит статусу по UDP |

---

## 8. Структура проєкту

```
BerezaLauncher/
├── .github/workflows/build-apk.yml     збірка APK у GitHub Actions
├── bereza.properties                   ЄДИНА конфігурація (IP, порт, посилання, версія)
├── build.gradle.kts · settings.gradle.kts · gradle.properties
├── gradlew · gradlew.bat · gradle/wrapper/
└── app/
    ├── build.gradle.kts                залежності, BuildConfig з bereza.properties, підпис
    └── src/main/
        ├── AndroidManifest.xml
        ├── assets/game_manifest.json   маніфест файлів гри
        ├── res/ (values/strings.xml — український текст, themes, colors, іконка, file_paths)
        └── java/ua/bereza/launcher/
            ├── BerezaApplication.kt · MainActivity.kt · AppContainer.kt
            ├── config/AppConfig.kt
            ├── network/  SampQueryClient · HttpJsonClient · NetworkMonitor
            ├── data/     Models · UiText · ErrorMessages · NewsRepository · UpdateRepository · ServerMonitor
            ├── settings/ SettingsRepository (DataStore) · NicknameValidator
            ├── installer/ GameFilesManifest · SafTree · FileVerifier · ComponentInstaller · LauncherUpdater
            ├── launcher/ GameClientLauncher · PackageLaunchAdapter
            ├── diagnostics/ DiagnosticsLog · DiagnosticsCollector
            ├── navigation/BerezaNavHost.kt
            └── ui/ theme · components · home · news · server · files · install · compat · diagnostics · settings · about
```

## 9. Інші мови

Створи `app/src/main/res/values-pl/strings.xml` (польська), `values-en/…` тощо з тими самими `name`.
Android сам обере мову системи; українська — за замовчуванням.
