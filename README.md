# 4PDA Авто-Сборщик Дайджеста Книгохранилища (WebView App)

Android WebView-приложение, построенное один-в-один по архитектуре [Practician/Bookstore](https://github.com/Practician/Bookstore), специально адаптированное под **`4pda_digest_mobile.html` (v6.4)**.

## Что реализовано по образцу `Practician/Bookstore`

1. **Двухслойный WebView (`activity_main.xml` + `MainActivity.kt`)**:
   - `webViewParser` — основной экран, загружающий зашифрованный локальный ассет `file:///android_asset/4pda_digest_mobile.html` через `EncryptedWebViewClient`.
   - `webViewForum` — встроенный браузер `4pda.to` для авторизации (`act=auth`), автоматического перехвата куки (`member_id`, `pass_hash`) и определения ника пользователя из DOM (`#user-menu` / `showuser`).
2. **Полная реализация JavaScript-моста `window.AndroidBridge`**:
   - `AndroidBridge.fetchForumPage(url, callbackId)` — выполняет фоновый HTTP-запрос через `OkHttpClient` с куками `CookieManager` от `4pda.to`, кодирует сырые байты ответа в `Base64` и вызывает `window.onForumPage(callbackId, true, base64)` (поддерживает и `windows-1251`, и `UTF-8` страницы форума без CORS-ограничений).
   - `AndroidBridge.getAuthState()` — возвращает JSON `{"loggedIn": true/false, "username": "..."}` и синхронизирует статус входа через `window.onAuthStateChanged(loggedIn, username)`.
   - `AndroidBridge.openForumUrl(url)` — открывает страницу входа или тему 4PDA во втором `WebView` с верхней панелью быстрого возврата `← В сборщик дайджеста`.
   - `AndroidBridge.logoutFromForum()` — очищает куки `CookieManager` и сбрасывает сессию.
   - `AndroidBridge.goBack()` — возвращает со страницы форума обратно в дайджест.
   - `AndroidBridge.saveTxtFile(fileName, content)` — сохраняет готовый BBCode-файл `novinki_knigohranilisha_post.txt` напрямую в системную папку «Загрузки» (`Downloads`).
   - `AndroidBridge.saveSetting` / `getSetting` / `setKeepScreenOn` / `nativeRequestWithProxy` — полностью совместимы с `Bookstore`.
3. **Шифрование ассетов (`encrypt_assets.py` + `EncryptedWebViewClient.kt`)**:
   - Исходник лежит в `app/src/main/html_sources/4pda_digest_mobile.html`.
   - Скрипт `encrypt_assets.py` шифрует его XOR-ключом в `app/src/main/assets/4pda_digest_mobile.html.enc`, а `EncryptedWebViewClient` расшифровывает на лету при открытии.
4. **Панель логов (`AppLogger.kt` + плавающая кнопка FAB)**:
   - Как и в `Bookstore`, плавающая кнопка в правом нижнем углу открывает оверлей логов всех сетевых запросов `fetchForumPage` и авторизации с кнопками «Копировать» и «Очистить».

## Как собрать APK

### Вариант 1: Автоматически через GitHub Actions (без установки Android Studio)
1. Создайте новый репозиторий на GitHub (или ветку) и загрузите в него содержимое этого архива.
2. Перейдите во вкладку **Actions** → **Build Android APK (4PDA Digest)** → нажмите **Run workflow**.
3. Через ~2 минуты скачайте готовый `4PDA-Digest-v6.4.0-release.apk` в блоке **Artifacts**.

### Вариант 2: Локально на ПК (Windows / Linux / macOS)
1. Откройте папку проекта в **Android Studio** или запустите в терминале:
   ```powershell
   python encrypt_assets.py
   ./gradlew assembleRelease
   ```
2. Готовый APK появится по пути:
   `app/build/outputs/apk/release/4PDA-Digest-v6.4.0-release.apk`

### Вариант 3: Интеграция вторым экраном прямо в ваш текущий проект `Practician/Bookstore`
Если вы хотите добавить сборщик дайджеста внутрь уже существующего приложения `Bookstore`:
1. Скопируйте `app/src/main/html_sources/4pda_digest_mobile.html` в папку `app/src/main/html_sources/` вашего репозитория `Bookstore` и запустите `python encrypt_assets.py`.
2. Добавьте метод `fetchForumPage(url: String, callbackId: String)` и `getAuthState(): String` из `MainActivity.kt` этого проекта внутрь `ParserJsInterface` вашего `Bookstore`.
