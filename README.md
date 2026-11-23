# NPlanner (Version 0.21)

Minimalistischer Alltags-, Gewohnheits- und Trainingsplaner für Android im **Nothing OS Design** (`#080808` Hintergrund, `#191919` / `#252525` Panels, `#E8E7E2` Off-White, `#E03930` Nothing-Rot).

Die App ist bewusst **komplett offlinefähig**, benötigt keinen Account, kein Backend und hält die gesamte UI- und Persistenzlogik in einer einzigen Kotlin-Datei (`MainActivity.kt`) auf Basis von Jetpack Compose & Material 3.

---

## Projektstruktur im Repository

```text
app/src/main/
├── AndroidManifest.xml
├── java/at/lukaechen/nplanner/
│   └── MainActivity.kt              # Gesamte App-Logik, UI, Persistenz & Dialoge (v0.21)
└── res/
    ├── drawable/
    │   ├── ic_launcher_background.xml
    │   ├── ic_launcher_foreground.xml
    │   └── ic_launcher_monochrome.xml
    ├── font/
    │   └── ndot_47.ttf              # Optionale Nothing-Dot-Matrix-Schriftart für Überschriften
    ├── mipmap-anydpi-v26/
    │   ├── ic_launcher.xml
    │   └── ic_launcher_round.xml
    └── mipmap-xxxhdpi/
        ├── ic_launcher.png
        └── ic_launcher_round.png
```

---

## Funktionsumfang (Version 0.21)

### 1. Heute (`TodayScreen`)
* **Tagesfortschritt:** Umschaltbar zwischen segmentiertem Balken (`ProgressBars`) und segmentiertem Orbit-Ring (`ProgressOrbit`).
* **Aufgabentypen (`RuleKind`):**
  * `FIXED`: Feste Uhrzeit (z. B. `08:00`), inklusive „Als Nächstes“-Vorschau.
  * `INTERVAL`: Wiederkehrend im Zeitfenster (z. B. alle 2 Std. von `08:00` bis `20:00`) mit Teil-Zähler (`c/total`).
  * `ALLDAY`: Ganztägige Aufgabe an ausgewählten Wochentagen.
  * `WEEKLY`: Flexibles Wochenziel (`1×` bis `14×` pro Woche), das am Montag zurückgesetzt wird.
* **Pflicht vs. Optional (`Rule.optional`):** Optionale Aufgaben erscheinen in einer eigenen Sektion und fließen **nicht** in den Tagesscore bzw. den Perfect-Score ein.
* **Haptisches Feedback (`HapticStyle`):** Unterschiedliche Vibrationsmuster über `Vibrator` / `VibrationEffect` für `TASK_DONE`, `DAY_COMPLETE` (Doppel-Impuls bei 100 % Tagesabschluss), `UNDO` (Abhaken rückgängig / Löschen) und `TICK` (Navigation & UI-Interaktionen).

### 2. Rückblick (`HistoryScreen`)
* **Zeitraum:** Umschaltbar zwischen **Monat** und **Jahr** inklusive Vor-/Zurück-Navigation.
* **Darstellungsmodi (`history_style`):**
  * **Punkte (`dots`):** Reduzierte Punkt-Matrix ohne Zahlen oder Kalender-Raster (im Jahresrückblick eine durchgehende 365/366-Tage-Punktmatrix).
  * **Kalender (`calendar`):** Klassische Monats- bzw. 12-Monats-Kachelansicht.
* **Farbcodierung:**
  * **Rot (`#E03930`):** Alle Pflichtziele erreicht (`DayCompletionState.ALL`).
  * **Weiß (`#E8E7E2`):** Teilweise erreicht (`DayCompletionState.PARTIAL`).
  * **Dunkelgrau (`#2D2D2D`):** Kein Ziel erreicht (`DayCompletionState.NONE`).
* **Tages-Snapshots (`snap:<YYYY-MM-DD>`):** Beim Abhaken wird der jeweilige Tagesstand gespeichert, damit vergangene Tage bei späteren Regeländerungen unverfälscht bleiben. Tippen auf einen Tag/Punkt öffnet unten die `DayDetailCard`.

### 3. Training (`TrainingScreen`)
Aufgeteilt in drei Unterseiten:
* **Workouts (Hauptfokus):** Übersicht der erstellten Workouts (`Workout`). Tippen auf ein Workout öffnet ein subtiles Overlay-Fenster (`WorkoutSheetDialog`), in dem die zugehörigen Übungen verwaltet und Sätze eingetragen werden. Neue Workouts erhalten automatisch einen Namensvorschlag (`suggestWorkoutName`), der frei anpassbar ist.
* **Übungen:** Vollständiger Übungskatalog (`Exercise`) mit drei Typen (`ExerciseKind`):
  * `WEIGHT`: Gewicht (kg) × Wiederholungen.
  * `BODYWEIGHT`: Eigenes Körpergewicht × Wiederholungen.
  * `TIMED`: Zeit in Sekunden (Halten oder *X Wiederholungen in Y Sekunden*).
  * Lokale Fotos und Videos (`filesDir/exercise_media/`) werden über `ActivityResultContracts.GetContent()` kopiert und in `ExerciseDetailDialog` angezeigt bzw. als Loop abgespielt.
  * **KI- & JSON-Workflow (`nplanner.exercise.v1`):** Über *„Mit KI anlegen“* wird ein standardisierter Prompt samt JSON-Schema in die Zwischenablage kopiert und per Android Share-Sheet an eine beliebige KI-App (Gemini, ChatGPT, Claude etc.) gesendet; die JSON-Antwort lässt sich direkt einfügen oder als `.json`-Datei importieren.
* **Rekorde:** Automatische Übersicht der persönlichen Bestleistungen (`bestRecordText`) pro Übung sowie Gesamtzahl der geloggten Sätze.

### 4. Einstellungen (`SetupScreen`)
* **Überschriften-Schriftart:** Wechsel zwischen Systemschrift (`Klassisch`) und `R.font.ndot_47` (`N-Dot`).
* **Fortschritt Heute:** Wechsel zwischen `Balken` und `Kreis`.
* **Rückblick-Darstellung:** Wechsel zwischen `Punkte` und `Kalender`.
* **Sortierung & Verwaltung:** Aufgaben und Wochenziele lassen sich per Gedrückthalten (`detectDragGesturesAfterLongPress`) direkt verschieben, löschen oder über `+ NEU` anlegen.
* **Entwickler-Option:** `TEST-MODUS WOCHENZIELE` erlaubt beliebiges Hoch- und Runterzählen von Wochenzielen am selben Tag.

---

## Datenhaltung (`SharedPreferences: "nplanner-v2"`)

| Key / Präfix | Typ | Beschreibung |
| :--- | :--- | :--- |
| `rules` | JSON-String | Array aller `Rule`-Objekte (inkl. `kind`, `days`, `optional`, `weeklyTarget`, `createdDate`). |
| `done:<YYYY-MM-DD>:<ruleId>` | Boolean | Erledigt-Status für `FIXED`- und `ALLDAY`-Regeln. |
| `cnt:<YYYY-MM-DD>:<ruleId>` | Int | Fortschrittszähler für `INTERVAL`-Regeln. |
| `wk_cnt:<weekStart>:<ruleId>` | Int | Wochenzähler für `WEEKLY`-Regeln (`weekStart` = Montag der Woche). |
| `wk_day:<YYYY-MM-DD>:<ruleId>` | Boolean | Markiert, ob ein Wochenziel am jeweiligen Tag abgehakt wurde. |
| `snap:<YYYY-MM-DD>` | JSON-String | Gespeicherter Tages-Snapshot (`{"done": Int, "total": Int}`) für den Rückblick. |
| `exercises_v1` | JSON-String | Katalog aller `Exercise`-Objekte (inkl. Pfad zu lokalen Medien). |
| `workouts_v1` | JSON-String | Liste aller `Workout`-Objekte mit zugeordneten `exerciseIds`. |
| `exercise_logs_v1` | JSON-String | Chronologische Trainings-Logs (`ExerciseLog`, max. 500 Einträge). |
| `use_ndot_headings` | Boolean | `true` = `ndot_47` für Überschriften, `false` = Systemschrift. |
| `progress_style` | String | `"bars"` oder `"orbit"`. |
| `history_style` | String | `"dots"` oder `"calendar"`. |
| `test_mode_multi_weekly` | Boolean | Test-Modus für Wochenziele. |

---

## Hinweise für die Weiterentwicklung mit einer KI

1. **Single-File-Architektur beibehalten:** Halte den gesamten Kotlin/Compose-Code weiterhin kompakt in `app/src/main/java/at/lukaechen/nplanner/MainActivity.kt`, solange keine neuen Android-Komponenten (z. B. Widgets, Foreground-Services oder Room-Migrationen) zwingend separate Klassen erfordern.
2. **Design-Konstanten respektieren:** Nutze ausschließlich die definierten Farbwerte (`Bg = 0xFF080808`, `Panel = 0xFF191919`, `Panel2 = 0xFF252525`, `White = 0xFFE8E7E2`, `Muted = 0xFF858585`, `Red = 0xFFE03930`) sowie `DisplayTitle`, `DisplayCount`, `SectionLabel` und `ChoiceChip` für ein konsistentes Nothing-UI.
3. **Abwärtskompatibilität der `SharedPreferences`:** Neue Felder in `Rule`, `Exercise`, `Workout` oder `ExerciseLog` beim JSON-Parsing immer mit `.optString(...)` / `.optInt(...)` / `.optBoolean(...)` und sinnvollen Default-Werten lesen, damit bestehende Nutzerdaten beim Update nicht verloren gehen.
4. **Build-Umgebung:** Das Projekt wird per GitHub Actions (`gradle :app:assembleDebug --no-daemon --stacktrace`) gebaut. Achte darauf, keine nicht-existenten Ressourcen oder ungültige Dateien in `app/src/main/res/` abzulegen.
