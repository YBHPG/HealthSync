# HealthSync: Decentralized Local Health Data Synchronization
*(Русская версия ниже / Russian version below)*

**HealthSync** is a cross-platform peer-to-peer solution for locally synchronizing sports and medical metrics (steps, heart rate, weight, active calories burned, body measurements, vitals, nutrition, and cycle tracking) between the Apple ecosystem (Apple Watch, iPhone) and Android devices.

The core concept is **Zero-Trust Local Network Sync**. The system operates exclusively within a local Wi-Fi network (WLAN) without using external cloud servers, databases, or third-party APIs. This guarantees absolute privacy, independence from the internet, and high data transfer speeds.

---

## System Architecture

The project consists of two key components:
1. **Android Application (Server & Data Repository):**
   - Acts as a local asynchronous HTTP server powered by the **Ktor (CIO engine)** framework.
   - Runs as a **Foreground Service** with a persistent notification to prevent the OS from killing the process.
   - Uses `PowerManager.WakeLock` and `WifiManager.WifiLock` to ensure stable TCP packet reception even when the screen is locked.
   - Routes and writes incoming metrics to the system's **Health Connect** storage.

2. **iOS Application (Aggregator & Client):**
   - Fetches background and user-entered data from the local **HealthKit** database.
   - Serializes the data into JSON and sends it via POST requests to the Android server using the native **URLSession**.
   - Bypasses **Local Network Privacy** restrictions (iOS 14+) by using the `waitsForConnectivity` configuration in `URLSession`, waiting for the user to grant local network access.

---

## Repository Structure

```text
├── README.md                      # This guide
├── .gitignore                     # Git ignore rules
├── android/                       # Android application module (Gradle project)
│   ├── app/                       # App source code (Jetpack Compose, Ktor, Health Connect)
│   └── build.gradle.kts           # Android build configuration
├── ios/                           # iOS application module (XcodeGen project)
│   ├── project.yml                # Specification for generating the Xcode project
│   ├── fix_capabilities.sh        # Script to inject HealthKit SystemCapabilities into Xcode
│   └── HealthSync/                # iOS app source code (SwiftUI)
└── scratch/                       # Folder for local scripts and drafts (ignored by Git)
```

---

## Manual Build and Setup Guide

### 1. Environment Requirements
- **Android:** Android Studio Jellyfish (or newer), JDK 17, Android SDK 34+.
- **iOS:** macOS, Xcode 16.0+, [XcodeGen](https://github.com/yonaskolb/XcodeGen) utility installed.

### 2. Building the Android Application
1. Navigate to the `android/` directory:
   ```bash
   cd android
   ```
2. Build the Debug APK manually via the Gradle wrapper:
   ```bash
   ./gradlew assembleDebug
   ```
3. Install the generated APK on your device using ADB:
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```
   *Note: Health Connect requires a physical device or a specialized emulator with Health Connect support.*

### 3. Generating and Building the iOS Application
The Xcode project is generated dynamically based on the `project.yml` specification to prevent merge conflicts in Git.
1. Install XcodeGen if you haven't already:
   ```bash
   brew install xcodegen
   ```
2. Navigate to the `ios/` folder and generate the project:
   ```bash
   cd ios
   xcodegen generate
   ```
3. **Crucial Step:** Apply the capability fix script. XcodeGen doesn't natively configure HealthKit `SystemCapabilities` properly, which is required for HealthKit entitlements to work:
   ```bash
   ./fix_capabilities.sh
   ```
4. Build the project manually via command line (example for an iOS Simulator):
   ```bash
   xcodebuild -project HealthSync.xcodeproj -scheme HealthSync -destination 'platform=iOS Simulator,name=iPhone 17'
   ```
   *To build for a physical device, open `HealthSync.xcodeproj` in Xcode, select your provisioning profile/team in the "Signing & Capabilities" tab, select your iPhone, and press `Cmd + R`.*

---

## Using the System
1. Connect both your iPhone and Android smartphone to the **same local Wi-Fi network** (or enable a mobile hotspot on one of the devices).
2. Launch the Android app. Tap the switch to start the server. The local IP address of the server will be displayed on the screen (e.g., `http://192.168.1.5:8080`).
3. Launch the iOS app. Enter the Android server's IP address in the connection settings.
4. Grant Local Network access on iOS and Health Connect write permissions on Android.
5. Tap the "Sync Data" button on iOS to transfer your health metrics.

---
---
---

# HealthSync: Децентрализованная система локальной синхронизации метрик здоровья

**HealthSync** — это кросс-платформенное peer-to-peer решение для локальной синхронизации спортивных и медицинских показателей (шаги, пульс, вес, сожженные калории, параметры тела, питание, отслеживание цикла и др.) между экосистемой Apple (Apple Watch, iPhone) и устройствами под управлением Android.

Основная концепция проекта — **Zero-Trust Local Network Sync** (локальная синхронизация с нулевым доверием). Система работает исключительно внутри локальной Wi-Fi сети (WLAN) без использования внешних облачных серверов, баз данных или сторонних API. Это гарантирует абсолютную конфиденциальность, независимость от интернета и высокую скорость передачи данных.

---

## Архитектура системы

Проект состоит из двух ключевых компонентов:
1. **Android-приложение (Сервер и репозиторий данных):**
   - Выступает в роли локального асинхронного HTTP-сервера на базе фреймворка **Ktor (движок CIO)**.
   - Запущено как **Foreground Service** (Служба переднего плана) с постоянным уведомлением, что защищает процесс от вытеснения операционной системой.
   - Использует `PowerManager.WakeLock` и `WifiManager.WifiLock` для стабильного приема TCP-пакетов при заблокированном экране.
   - Маршрутизирует и записывает входящие метрики в системное хранилище **Health Connect**.

2. **iOS приложение (Агрегатор и клиент):**
   - Собирает фоновые и введенные пользователем данные из локальной базы **HealthKit**.
   - Сериализует данные в JSON и отправляет POST-запросами на Android-сервер через нативный **URLSession**.
   - Обходит ограничения **Local Network Privacy** (iOS 14+) с помощью настройки `waitsForConnectivity` в URLSession, дожидаясь согласия пользователя на доступ к локальной сети.

---

## Структура репозитория

```text
├── README.md                      # Данное руководство
├── .gitignore                     # Корневые и платформенные правила игнорирования Git
├── android/                       # Модуль Android-приложения (Gradle проект)
│   ├── app/                       # Исходный код приложения (Jetpack Compose, Ktor, Health Connect)
│   └── build.gradle.kts           # Конфигурация сборки Android
├── ios/                           # Модуль iOS приложения (XcodeGen проект)
│   ├── project.yml                # Спецификация для генерации Xcode-проекта
│   ├── fix_capabilities.sh        # Скрипт для инъекции HealthKit SystemCapabilities в проект Xcode
│   └── HealthSync/                # Исходный код iOS-приложения (SwiftUI)
└── scratch/                       # Папка для локальных скриптов и черновиков (игнорируется Git)
```

---

## Руководство по ручной сборке проекта

### 1. Требования к окружению
- **Android:** Android Studio Jellyfish (или новее), JDK 17, Android SDK 34+.
- **iOS:** macOS, Xcode 16.0+, установленная утилита [XcodeGen](https://github.com/yonaskolb/XcodeGen).

### 2. Сборка Android-приложения
1. Перейдите в директорию `android/`:
   ```bash
   cd android
   ```
2. Соберите Debug APK вручную с помощью Gradle-обертки:
   ```bash
   ./gradlew assembleDebug
   ```
3. Установите сгенерированный APK на устройство с помощью ADB:
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```
   *Примечание: для работы Health Connect требуется физический девайс или эмулятор со специальной поддержкой.*

### 3. Генерация и сборка iOS-приложения
Проект Xcode генерируется динамически на основе спецификации `project.yml`, чтобы избежать конфликтов слияния файлов проекта в Git.
1. Установите XcodeGen, если он еще не установлен:
   ```bash
   brew install xcodegen
   ```
2. Перейдите в папку `ios/` и сгенерируйте проект:
   ```bash
   cd ios
   xcodegen generate
   ```
3. **Критически важный шаг:** Примените скрипт исправления возможностей. XcodeGen не умеет нативно конфигурировать секцию `SystemCapabilities` для HealthKit, которая необходима для корректной работы разрешений:
   ```bash
   ./fix_capabilities.sh
   ```
4. Соберите проект вручную через командную строку (пример для симулятора):
   ```bash
   xcodebuild -project HealthSync.xcodeproj -scheme HealthSync -destination 'platform=iOS Simulator,name=iPhone 17'
   ```
   *Чтобы собрать проект для физического устройства, откройте `HealthSync.xcodeproj` в Xcode, выберите ваш профиль разработчика/команду во вкладке "Signing & Capabilities", выберите ваш iPhone и нажмите `Cmd + R`.*

---

## Использование системы
1. Подключите iPhone и Android-смартфон к **одной локальной Wi-Fi сети** (или включите режим модема на одном из устройств).
2. Запустите приложение на Android. Нажмите переключатель для старта сервера. На экране отобразится локальный IP-адрес сервера (например, `http://192.168.1.5:8080`).
3. Запустите приложение на iOS. Введите IP-адрес Android-сервера в настройках подключения.
4. Разрешите доступ к локальной сети на iOS и дайте разрешения на запись в Health Connect на Android.
5. Нажмите кнопку синхронизации для передачи данных.
