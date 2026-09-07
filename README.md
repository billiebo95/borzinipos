# BORZINI — касса для кофейни

Нативное Android-приложение (Kotlin + Jetpack Compose + Room + WorkManager) для кассы, склада,
рецептур, статистики и синхронизации с Google Drive/Sheets.

## Структура репозитория

- `core/` — чистый Kotlin-модуль (без Android) с денежной арифметикой, себестоимостью,
  идемпотентностью продажи, возвратами, календарём недели/часового пояса. Покрыт юнит-тестами,
  которые реально прогоняются локально (`./gradlew :core:test`) без Android SDK.
- `app/` — само Android-приложение: Room-база, репозитории, Compose-экраны, синхронизация с
  Google Drive/Sheets, WorkManager.
- `.github/workflows/android-build.yml` — сборка APK в GitHub Actions (см. ниже, почему сборка
  идёт там, а не локально в этой рабочей сессии).

## Документация

- [`docs/INSTALL.md`](docs/INSTALL.md) — как получить и установить APK.
- [`docs/GOOGLE_SETUP.md`](docs/GOOGLE_SETUP.md) — пошаговая настройка Google Cloud/OAuth для
  синхронизации с Drive и Sheets.
- [`docs/USAGE.md`](docs/USAGE.md) — краткая инструкция: закупка → рецептура → продажа → прибыль.
- [`docs/BACKUP.md`](docs/BACKUP.md) — резервное копирование и восстановление.
- [`docs/QA_RESULTS.md`](docs/QA_RESULTS.md) — что реально проверено (юнит-тесты) и что не могло
  быть проверено в этой рабочей среде (сборка APK, установка на телефон, вход в Google).
- [`docs/LIMITATIONS.md`](docs/LIMITATIONS.md) — честный список ограничений первой версии.
- [`docs/RELEASE_SIGNING.md`](docs/RELEASE_SIGNING.md) — ключ подписи и обновления без потери
  данных.
- [`docs/MIGRATIONS.md`](docs/MIGRATIONS.md) — правило для будущих изменений схемы базы данных.

## Почему сборка APK идёт через GitHub Actions

Рабочая среда, в которой писался код, не имеет доступа к репозиторию Google
(`dl.google.com` / `maven.google.com`), откуда Gradle качает Android Gradle Plugin, Jetpack
Compose, Room и т.д. — это ограничение сетевой политики окружения, не проекта. Поэтому:

- Модуль `core` — чистый Kotlin/JVM, его зависимости (Kotlin, JUnit) идут с Maven Central, и он
  **реально собран и протестирован** прямо в этой сессии.
- Модуль `app` требует полноценный Android-инструментарий, поэтому его сборку выполняет
  GitHub Actions (у GitHub-раннеров есть доступ к нужным репозиториям) — см. вкладку **Actions**
  в репозитории после пуша, там будет APK как build-артефакт.

## Технические решения

- Деньги — `Long` в копейках (никогда `Double`), см. `core/Money.kt`.
- Дробные количества ингредиентов — `BigDecimal`, см. `core/Quantity.kt`.
- Идемпотентность продажи — id чека равен ключу, сгенерированному в начале оформления заказа;
  повторная вставка того же id — no-op на уровне SQLite (`OnConflictStrategy.IGNORE`).
- Продажа/списание/очередь синхронизации — одна Room-транзакция (`SaleRepository.completeSale`).
- Средневзвешенная себестоимость — `core/Costing.kt` (`WeightedAverageCost`).
