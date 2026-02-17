# SmartPantry - Codebase Guide

Questa guida serve a chi apre il progetto per la prima volta e deve capire:
- architettura attuale
- responsabilita dei file
- dove modificare il codice per ogni feature
- limiti tecnici da conoscere prima di cambiare behavior

## 1. Stack tecnico

- Linguaggio: Kotlin
- UI: Jetpack Compose
- Architettura: ViewModel + UI state in memoria (senza layer DB locale)
- Networking: Retrofit + Gson
- Async: Coroutines
- Auth: Firebase Auth
- Scanner barcode/QR: Google Code Scanner (ML Kit)
- Reminder in background: WorkManager + FusedLocationProvider + notifiche Android

Dipendenze centralizzate in:
- `gradle/libs.versions.toml`

Configurazione modulo app:
- `app/build.gradle.kts`

## 2. Mappa del progetto

### UI (screen e composable)

- `app/src/main/java/it/sapienza/smartpantry/ui/LoginActivity.kt`
  - schermata login
  - naviga a `MainActivity` se utente gia autenticato

- `app/src/main/java/it/sapienza/smartpantry/ui/MainActivity.kt`
  - shell principale con bottom navigation
  - contiene:
    - Home
    - Diario alimentare
    - Shopping list
    - Profilo
  - contiene anche la UI per:
    - scanner shopping list
    - toggle promemoria supermercato
    - richieste runtime permissions (notifiche/posizione)

- `app/src/main/java/it/sapienza/smartpantry/ui/FoodSelectionActivity.kt`
  - ricerca alimento per testo o scanner
  - apre `FoodQuantityActivity`

- `app/src/main/java/it/sapienza/smartpantry/ui/FoodQuantityActivity.kt`
  - inserimento grammi e conferma alimento da passare al diario

### ViewModel (logica stato UI)

- `app/src/main/java/it/sapienza/smartpantry/ui/viewmodel/LoginViewModel.kt`
  - login Firebase
  - eventi UI (`ShowMessage`, `NavigateToMain`)

- `app/src/main/java/it/sapienza/smartpantry/ui/viewmodel/FoodJournalViewModel.kt`
  - stato diario
  - calcolo settimana
  - BMR e calorie consigliate da profilo

- `app/src/main/java/it/sapienza/smartpantry/ui/viewmodel/FoodSelectionViewModel.kt`
  - ricerca alimenti OpenFoodFacts
  - ricerca per codice scanner

- `app/src/main/java/it/sapienza/smartpantry/ui/viewmodel/ShoppingListViewModel.kt`
  - sezioni e articoli shopping list
  - add/edit/delete/toggle
  - lookup prodotto da codice con conferma
  - merge duplicati per nome articolo

- `app/src/main/java/it/sapienza/smartpantry/ui/viewmodel/ProfileViewModel.kt`
  - dati utente e calorie sport

### Modelli UI

- `app/src/main/java/it/sapienza/smartpantry/ui/model/UiModels.kt`
  - model usati tra ViewModel e UI

### Data layer

- OpenFoodFacts:
  - `app/src/main/java/it/sapienza/smartpantry/data/openfoodfacts/OpenFoodFactsApiService.kt`
  - `app/src/main/java/it/sapienza/smartpantry/data/openfoodfacts/OpenFoodFactsModels.kt`
  - `app/src/main/java/it/sapienza/smartpantry/data/openfoodfacts/OpenFoodFactsRepository.kt`

- Supermercati vicini (Overpass/OpenStreetMap):
  - `app/src/main/java/it/sapienza/smartpantry/data/supermarket/OverpassApiService.kt`
  - `app/src/main/java/it/sapienza/smartpantry/data/supermarket/OverpassModels.kt`
  - `app/src/main/java/it/sapienza/smartpantry/data/supermarket/SupermarketRepository.kt`

### Reminder geolocalizzati

- `app/src/main/java/it/sapienza/smartpantry/reminder/ShoppingProximityReminderManager.kt`
  - stato reminder (abilitato/disabilitato)
  - sync articoli pendenti
  - scheduling/cancel WorkManager
  - throttling notifiche

- `app/src/main/java/it/sapienza/smartpantry/reminder/ShoppingProximityReminderWorker.kt`
  - job background:
    1. legge articoli pendenti
    2. ottiene posizione
    3. cerca supermercati entro 300m
    4. invia notifica

## 3. Flussi funzionali principali

### 3.1 Login

1. `LoginActivity` mostra form
2. `LoginViewModel.login()` esegue Firebase Auth
3. in caso successo emette `NavigateToMain`

### 3.2 Diario alimentare

1. da `MainActivity` -> tab Diario -> click `Aggiungi alimento`
2. apertura `FoodSelectionActivity`
3. ricerca testo o scanner
4. scelta alimento -> `FoodQuantityActivity`
5. ritorno dati (nome, kcal/100g, grammi) a `MainActivity`
6. `FoodJournalViewModel.onFoodAdded()`

### 3.3 Shopping list

1. gestione sezioni e articoli da `ShoppingListScreen` (`MainActivity`)
2. logica mutazioni in `ShoppingListViewModel`
3. supporto add prodotto:
   - da codice inserito a mano
   - da scanner barcode/QR
4. conferma articolo in dialog prima del salvataggio

### 3.4 Reminder supermercato vicino

1. utente attiva toggle in `ShoppingListScreen`
2. app chiede permessi necessari
3. `ShoppingProximityReminderManager` schedula worker
4. worker controlla vicinanza supermercati e articoli pendenti
5. notifica locale se match

## 4. Dove mettere le mani (use case -> file)

### Cambiare UI tab Diario

- `app/src/main/java/it/sapienza/smartpantry/ui/MainActivity.kt`
  - `FoodJournalScreen`
  - `MealsSection`
  - `SummarySection`

### Cambiare ricerca alimenti (testo/scanner)

- UI scanner: `app/src/main/java/it/sapienza/smartpantry/ui/FoodSelectionActivity.kt`
- logica ricerca: `app/src/main/java/it/sapienza/smartpantry/ui/viewmodel/FoodSelectionViewModel.kt`
- mapping API: `app/src/main/java/it/sapienza/smartpantry/data/openfoodfacts/OpenFoodFactsRepository.kt`

### Cambiare Shopping List (struttura/articoli/merge)

- UI: `app/src/main/java/it/sapienza/smartpantry/ui/MainActivity.kt` (`ShoppingListScreen`, `ShoppingSectionCard`)
- logica stato: `app/src/main/java/it/sapienza/smartpantry/ui/viewmodel/ShoppingListViewModel.kt`

### Cambiare reminder geolocalizzati

- strategia schedule/throttle: `app/src/main/java/it/sapienza/smartpantry/reminder/ShoppingProximityReminderManager.kt`
- regole di invio notifica: `app/src/main/java/it/sapienza/smartpantry/reminder/ShoppingProximityReminderWorker.kt`
- query supermercati: `app/src/main/java/it/sapienza/smartpantry/data/supermarket/SupermarketRepository.kt`

### Cambiare raggio prossimita supermercato

Modifica queste costanti:
- `ShoppingProximityReminderManager.DEFAULT_RADIUS_METERS`
- `SupermarketRepository.DEFAULT_RADIUS_METERS`

Mantienile allineate.

### Cambiare testo notifica

- `ShoppingProximityNotificationHelper.buildNotificationBody()` in:
  - `app/src/main/java/it/sapienza/smartpantry/reminder/ShoppingProximityReminderWorker.kt`

### Cambiare permessi runtime

- dichiarazioni manifest:
  - `app/src/main/AndroidManifest.xml`
- flow richieste permessi in UI:
  - `ShoppingListScreen` in `app/src/main/java/it/sapienza/smartpantry/ui/MainActivity.kt`

## 5. Limitazioni attuali note

- Persistenza locale assente per molte feature UI:
  - gran parte dello stato vive in `ViewModel` in memoria
  - chiusura app/kill processo puo perdere stato non sincronizzato

- Reminder geolocalizzati:
  - WorkManager periodico minimo ~15 minuti (non realtime)
  - dipende da rete disponibile
  - dipende da permessi posizione/notifiche

- API supermarket (Overpass):
  - puo avere limiti/rate-limit o latenza variabile

- Scanner:
  - `AndroidManifest.xml` marca camera come required
  - dispositivi senza camera potrebbero non essere compatibili

## 6. Build e verifica

Compilazione veloce:

```bash
./gradlew :app:compileDebugKotlin
```

APK debug:

```bash
./gradlew :app:assembleDebug
```

Test (template):

```bash
./gradlew test
./gradlew connectedAndroidTest
```

## 7. Checklist per nuove feature

Quando aggiungi una nuova feature, segui questo ordine:

1. Definisci model UI in `ui/model` (se serve)
2. Implementa logica in ViewModel
3. Aggancia UI Compose
4. Se c e rete/background, aggiungi repository/worker dedicati
5. Aggiorna permessi manifest e flow runtime se necessario
6. Compila con `:app:compileDebugKotlin`
7. Aggiorna questa guida con i nuovi file entry-point

## 8. Nota manutenzione

Questa guida deve restare aggiornata ai refactor.
Se sposti responsabilita tra file, aggiorna almeno:
- sezione "Mappa del progetto"
- sezione "Dove mettere le mani"
- sezione "Limitazioni note"
