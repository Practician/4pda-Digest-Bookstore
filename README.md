<div align="center">

# 📚 4PDA Дайджест Книгохранилища

*Ежедневные новинки Книгохранилища — теперь в один тап*

![Version](https://img.shields.io/badge/версия-6.4.0-blue?style=flat-square)
![Platform](https://img.shields.io/badge/платформа-Android-green?style=flat-square&logo=android&logoColor=white)
![Build](https://img.shields.io/github/actions/workflow/status/Practician/4pda-Digest-Bookstore/android.yml?style=flat-square&label=сборка)
![Release](https://img.shields.io/github/v/release/Practician/4pda-Digest-Bookstore?style=flat-square&label=APK)
![License](https://img.shields.io/badge/license-UNLICENSED-lightgrey?style=flat-square)

[**⬇️ Скачать APK**](https://github.com/Practician/4pda-Digest-Bookstore/releases/latest) · [**🛠 Собрать самому**](#-как-собрать-самому)

</div>

---

## 💬 Что это?

Устали вручную листать Книгохранилище, чтобы узнать, что вышло нового?

Это приложение делает всю рутину за вас: **заходит на форум 4PDA, собирает самые свежие книги из тем Книгохранилища и складывает готовый пост в формате BBCode** — со ссылками, обложками и описаниями. Вам остаётся только скопировать и опубликовать.

> 💡 Работает прямо с телефона. Ничего не нужно устанавливать на ПК.

---

## ✨ Возможности

| | |
|---|---|
| 🗂 **Авто-сборка дайджеста** | Листает свежие темы Книгохранилища и собирает новинки одним списком |
| 🔐 **Вход на форум** | Авторизация через ваш аккаунт 4PDA — куки хранятся только на устройстве |
| 📝 **Готовый BBCode** | Одно нажатие — и готовый пост-файл оказался в папке «Загрузки» |
| 🌐 **Встроенный браузер** | Кнопка «Войти» открывает форум прямо внутри приложения |
| 📋 **Журнал запросов** | Плавающая кнопка со всеми сетевыми событиями — удобно и для отладки |

---

## 🚀 Установка

1. Скачайте [**последний APK**](https://github.com/Practician/4pda-Digest-Bookstore/releases/latest)
2. Разрешите установку из неизвестных источников (Android спросит сам)
3. Установите и откройте — дальше всё интуитивно

> 📱 Требуется Android 7.0+

---

## 🛠 Как собрать самому

### ☁️ Через GitHub — без Android Studio

1. Форкните или скопируйте этот репозиторий
2. Вкладка **Actions → Build Android APK → Run workflow**
3. Через пару минут заберите готовый APK из результата

<details>
<summary><b>🖥 На своём ПК</b></summary>

```bash
./gradlew assembleRelease
```

Готовый файл: <code>app/build/outputs/apk/release/…-release.apk</code>
</details>

---

## 🙏 Благодарности

- Архитектура построена по образцу [**Practician/Bookstore**](https://github.com/Practician/Bookstore) 💙

---

<div align="center">

*Сделано для людей, которые просто любят книги* 📖

</div>
