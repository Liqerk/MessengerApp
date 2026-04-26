
<div align="center">

# 📱 MessengerApp

### _Modern Android Messenger with Real-Time Sync, Offline Mode, and DPI Bypass_

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.0-7F52FF.svg?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Android-14-3DDC84.svg?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/)
[![WebSocket](https://img.shields.io/badge/WebSocket-Real--Time-010101.svg?style=for-the-badge&logo=socket.io&logoColor=white)](https://developer.mozilla.org/en-US/docs/Web/API/WebSocket)
[![SQLite](https://img.shields.io/badge/SQLite-3.40+-003B57.svg?style=for-the-badge&logo=sqlite&logoColor=white)](https://www.sqlite.org/)

[![Status](https://img.shields.io/badge/Status-Production_Ready-00C853.svg?style=flat-square)]()
[![License](https://img.shields.io/badge/License-MIT-F5A623.svg?style=flat-square)](LICENSE)

> **Полнофункциональный мессенджер** с WebSocket, очередью офлайн-сообщений, уведомлениями, авто-реконнектом и встроенным модулем обхода DPI.

</div>

---

## 🎯 **О проекте**

**MessengerApp** — это современный Android-мессенджер, разработанный с нуля. Проект демонстрирует глубокое понимание клиент-серверной архитектуры, работу с реальным временем (WebSocket), сложную синхронизацию данных и production-ready решения для сложных сетевых условий (DPI-блокировки).

Проект включает:
- **Android-клиент** на Kotlin с Clean Architecture
- **Kotlin Multiplatform (KMP)** модуль для общих моделей
- Встроенный **модуль обхода DPI** (на базе ByeDPI)

---

## ✨ **Ключевые возможности**

### 💬 **Обмен сообщениями**
- ✅ Real-time отправка/получение через WebSocket
- ✅ Офлайн-очередь с автоматической синхронизацией
- ✅ Отправка изображений и голосовых сообщений
- ✅ Статусы "печатает..." и "онлайн"
- ✅ Уведомления о новых сообщениях (с поддержкой фона/foreground)
- ✅ Удаление сообщений и чатов (с синхронизацией)

### 🔌 **Продвинутая работа с сетью**
- ✅ **WebSocket Auto-Reconnect** (экспоненциальная задержка: 3с → 30с, health-check)
- ✅ **Офлайн-режим**: все сообщения уходят в очередь и отправляются при восстановлении соединения
- ✅ **DPI Bypass**: встроенный модуль на C для обхода блокировок (на базе ByeDPI)

### 🎨 **Пользовательский интерфейс**
- ✅ Тёмная тема с кастомной стилизацией
- ✅ Адаптивный дизайн (Material Design)
- ✅ Мультивыбор сообщений
- ✅ Поиск по сообщениям и чатам
- ✅ Избранное и закрепление чатов

### 🏗️ **Архитектура**
```
┌─────────────────────────────────────────┐
│         Presentation Layer              │
│     (Activities / Adapters / Views)     │
└─────────────────────────────────────────┘
                    │
┌─────────────────────────────────────────┐
│           Domain Layer                  │
│    (SyncManager / Models / Utils)       │
└─────────────────────────────────────────┘
                    │
┌─────────────────────────────────────────┐
│            Data Layer                   │
│  (Repository / Local DB / Transport)    │
└─────────────────────────────────────────┘
                    │
┌─────────────────────────────────────────┐
│          Network Layer                  │
│  (WebSocket / HTTP / DPI Bypass)        │
└─────────────────────────────────────────┘
```

---

## 🛠️ **Технический стек**

### **Клиент (Android)**
| Компонент | Технология | Назначение |
|-----------|------------|------------|
| Язык | Kotlin | Основной язык приложения |
| UI | XML + Material Design | Интерфейс пользователя |
| Сеть | OkHttp + WebSocket | HTTP/WS клиент |
| БД | SQLite (кастомный DbHelper) | Локальное хранение |
| Асинхронность | Kotlin Coroutines | Фоновые задачи |
| Архитектура | Clean Architecture + Repository | Разделение ответственности |
| Аудио | MediaRecorder + MediaPlayer | Запись/воспроизведение голосовых |
| Навигация | Intents + Activities | Экраны приложения |

### **Модуль DPI Bypass (C)**
- **Язык:** C17
- **Основа:** ByeDPI
- **Цель:** Обход DPI-блокировок для WebSocket/REST трафика
- **Интеграция:** JNI-вызовы из Kotlin

### **Шаблоны проектирования**
```kotlin
// Repository Pattern — единый источник данных
object Repository { /* ... */ }

// Observer Pattern — WebSocket коллбэки
interface MessageTransport {
    fun onMessageReceived(callback: (Message) -> Unit)
}

// Singleton Pattern — менеджеры и сервисы
object TransportManager { /* ... */ }
object SessionManager { /* ... */ }
```

---

## 🚀 **Установка и запуск**

### **Требования**
- Android Studio Iguana (2023.2.1+) или новее
- JDK 17+
- Android SDK (API 34+)
- Устройство/эмулятор с Android 12+

### **Сборка клиента**

```bash
# Клонирование репозитория
git clone https://github.com/Liqerk/MessengerApp.git
cd MessengerApp

# Открыть проект в Android Studio
# Запустить сборку (Build → Make Project)

# Установить на устройство
# Run → Run 'app'
```

### **Настройка сервера**
Для работы мессенджера требуется сервер. Проект сервера доступен по ссылке:
**[MessengerApp-Server](https://github.com/Liqerk/MessengerApp-Server)**

```bash
# Настройка сервера (быстрый старт)
git clone https://github.com/Liqerk/MessengerApp-Server.git
cd MessengerApp-Server
# Следуйте инструкции в README сервера
```

### **DPI Bypass (опционально)**
Модуль обхода DPI встроен в приложение и активируется автоматически при необходимости. Для его отключения измените конфигурацию в `DpiManager.kt`.

---

## 📁 **Структура проекта**

```
MessengerApp/
├── app/                           # Android-модуль
│   ├── src/main/java/.../
│   │   ├── data/                  # Data Layer
│   │   │   ├── database/          # SQLite (DbHelper, DAO)
│   │   │   ├── local/             # SessionManager
│   │   │   ├── models/            # Модели данных
│   │   │   └── repository/        # Repository
│   │   ├── domain/                # Domain Layer
│   │   │   ├── sync/              # SyncManager (очередь)
│   │   │   └── utils/             # HashUtils и др.
│   │   ├── network/               # Network Layer
│   │   │   ├── models/            # API-модели и WsEvents
│   │   │   └── transport/         # TransportManager, ServerTransport
│   │   ├── presentation/          # Presentation Layer
│   │   │   ├── activities/        # UI-активности
│   │   │   └── adapters/          # RecyclerView адаптеры
│   │   ├── dpi/                   # DPI Bypass модуль
│   │   │   ├── core/              # Нативный код (C)
│   │   │   └── services/          # VPN-сервисы
│   │   └── utils/                 # Вспомогательные классы
│   └── res/                       # Ресурсы (layout, drawable)
├── gradle/                        # Gradle Wrapper
└── build.gradle.kts               # Конфигурация сборки
```

---

## 💡 **Ключевые решения (для собеседования)**

### **1. Офлайн-синхронизация**
```kotlin
// MessageQueue.kt — потокобезопасная очередь
object MessageQueue {
    private val pendingMessages = ConcurrentLinkedQueue<QueuedMessage>()
    
    fun addPending(message: Message) { /* ... */ }
    fun processPending() { /* ... */ }
}
```
**Почему это важно:** Сообщения никогда не теряются, даже при полном отсутствии сети. При восстановлении соединения они автоматически уходят на сервер в правильном порядке.

### **2. WebSocket Auto-Reconnect**
```kotlin
// ServerTransport.kt — экспоненциальная задержка
private fun scheduleReconnect(delayMs: Long) {
    reconnectJob = CoroutineScope(Dispatchers.IO).launch {
        delay(delayMs)
        if (shouldBeConnected && !_isConnected) {
            attemptConnect()
        }
    }
}
```
**Результат:** Приложение выдерживает падения сервера, переключение сетей и длительные простои, не теряя соединение.

### **3. DPI Bypass**
Модуль на C (форк ByeDPI) внедряет паттерны трафика, маскирующие WebSocket-соединения под обычный HTTPS. **Важно:** Используется только при обнаружении блокировок, в обычных сетях неактивен.

---

## 📊 **Статус разработки**

| Компонент | Статус | Примечание |
|-----------|--------|-------------|
| Основной функционал | ✅ Завершён | Отправка/получение сообщений |
| WebSocket | ✅ Завершён | С авто-реконнектом |
| Офлайн-очередь | ✅ Завершён | С приоритетами |
| Медиа (фото/аудио) | ✅ Завершён | Локальное сохранение + upload |
| Уведомления | ✅ Завершён | Foreground/background |
| Удаление | ✅ Завершён | Сообщений и чатов |
| DPI Bypass | ✅ Завершён | На базе ByeDPI |
| UI/UX | ✅ Завершён | Material Design, темная тема |
| Поиск | ✅ Завершён | По сообщениям и чатам |
| Избранное/Пинги | ✅ Завершён | Локальное состояние |
| Unit-тесты | ⚠️ Частично | Основные модули покрыты |

---

## 🔍 **Точки роста (Self-reflection)**

| Область | План улучшения |
|---------|----------------|
| Тестирование | Расширить покрытие (интеграционные тесты WebSocket) |
| Производительность | Оптимизировать запросы к БД (RxJava/Flow) |
| Модульность | Выделить DPI-модуль в отдельную библиотеку |
| Документация | Добавить Javadoc/KDoc для всех публичных API |

---

## 🙏 **Благодарности**

- **[ByeDPI](https://github.com/hufrea/byedpi)** — за основу модуля обхода DPI
- **[OpenRouter](https://openrouter.ai)** — за тестовые AI-модели (использовались на этапе прототипирования)
- **[Ktor](https://ktor.io)** — за вдохновение и reference-реализацию WebSocket

---

## 📄 **Лицензия**

MIT © [Liqerk](https://github.com/Liqerk)

---

<div align="center">

### **🌟 Спасибо за внимание к проекту! 🌟**

**[→ Репозиторий сервера](https://github.com/Liqerk/MessengerApp-Server) · [Сообщить о проблеме](https://github.com/Liqerk/MessengerApp/issues)**

*Готов детально разобрать любое архитектурное решение, показать код вживую или пройти тестовое задание*

[![Telegram](https://img.shields.io/badge/Telegram-@Liqerk-0088cc?logo=telegram&logoColor=white)](https://t.me/Liqerk)

</div>


