# SmartPantry

Applicazione Android (Kotlin + Jetpack Compose) per:
- diario alimentare giornaliero
- ricerca alimenti con testo o scanner barcode/QR
- gestione shopping list a sezioni
- promemoria posizione: notifica se sei vicino a un supermercato con articoli in lista

## Setup rapido

Prerequisiti:
- Android Studio aggiornato
- JDK 11
- SDK Android 34

Comandi utili:

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:assembleDebug
```

## Dove leggere prima

Per capire rapidamente il codice e sapere dove intervenire:

- `docs/CODEBASE_GUIDE.md`

## Entry point principali

- Login: `app/src/main/java/it/sapienza/smartpantry/ui/LoginActivity.kt`
- App shell: `app/src/main/java/it/sapienza/smartpantry/ui/MainActivity.kt`
- Modulo ricerca alimenti: `app/src/main/java/it/sapienza/smartpantry/ui/FoodSelectionActivity.kt`
- Reminder geolocalizzati: `app/src/main/java/it/sapienza/smartpantry/reminder/ShoppingProximityReminderWorker.kt`
