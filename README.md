# Bereza RP Launcher v2 (Android)

Kotlin + Jetpack Compose. Гілка `launcher-v2`. APK збирає GitHub Actions → розділ **Releases** (`launcher-v2-N`).

## Що працює
- Статус сервера `188.127.241.74:2406` — справжній UDP query-протокол SA-MP (онлайн, пінг, версія, список гравців).
- Завантаження файлів за JSON-маніфестом: докачування (HTTP Range), повтори при обриві, SHA-256, розпакування ZIP із захистом від zip-slip, видалення застарілих файлів.
- Посилання Dropbox вставляються як є — лаунчер сам ставить `dl=1`.
- Завантаження у foreground-сервісі (не обривається, коли лаунчер згорнуто), сповіщення з прогресом і кнопкою «Зупинити».
- Самооновлення лаунчера (APK з маніфесту, перевірка SHA-256, системне встановлення).
- Новини (JSON, кеш офлайн), профіль (нік з перевіркою формату SA-MP/RP), налаштування (лише Wi-Fi, повна перевірка файлів, видалення файлів, власні URL для тестів).

## Що НЕ працює (свідомо)
Запуск гри. Кнопка «ГРАТИ» вимкнена й показує причину: немає Android-клієнта SA-MP з підтвердженою ліцензією.
Точка інтеграції — `client/GameClient.kt`, деталі — `docs/CLIENT_INTEGRATION.md`. Дослідження легальних варіантів — `docs/RESEARCH.md`.

## Файли гри
Лаунчер кладе їх у `Android/data/ua.bereza.rp.launcher/files/game/` (дозволи на сховище не потрібні).

## Оновлення файлів
1. Заповни `manifest.src.json` (приклад у `tools/make_manifest.py`).
2. `python3 tools/make_manifest.py manifest.src.json manifest.json`
3. Залий `manifest.json` на `gh-pages` → `launcher/v2/manifest.json` (адреса в `app/src/main/assets/launcher_config.json`).

## Формат маніфесту (схема 2)
```json
{
  "schema": 2, "version": "2026.11.04-1", "minLauncherVersionCode": 1, "baseUrl": "",
  "files": [{"path": "mods/x.zip", "url": "https://…", "size": 123, "sha256": "…", "extractTo": "mods/x", "title": "…"}],
  "remove": ["old/file.txd"],
  "launcher": {"versionCode": 12, "versionName": "2.0.12", "apkUrl": "https://…", "sha256": "…", "size": 123, "notes": "…"}
}
```
