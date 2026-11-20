# NPlanner

Native Android-App für tägliche Routinen und flexible Wochenziele, mit einer reduzierten, von Nothing OS 5 / Glyph inspirierten Darkmode-Oberfläche.

> **Projektstatus:** Prototyp in aktiver Entwicklung. Letzter hier vorbereiteter Kotlin-Stand: **UI-Version 0.9**. Ob genau dieser Stand bereits erfolgreich auf dem Gerät läuft, muss anhand des GitHub-Commits und der installierten APK geprüft werden. Diese README ist die Übergabe für die Weiterentwicklung, kein Nachweis vollständiger Funktionalität.

## Projektzugang

- Repository: https://github.com/lukaechen/NPlanner
- Android-Gerät: Nothing Phone (4a) Pro
- Implementierung: **Kotlin + Jetpack Compose**, keine WebView und kein HTML in der fertigen App
- Builds: GitHub Actions, ohne lokale Android-Studio-Installation
- APK: im erfolgreichen Workflow-Lauf unter **Artifacts**
- App-ID / Namespace: `at.lukaechen.nplanner`
- Sprache der App: Deutsch
- Der Nutzer möchte knappe, präzise Antworten und direkt verwendbare **geänderte Dateien**, nicht das ganze Repository.

## Produktidee

NPlanner ist ein persönlicher Routine- und Wochenziel-Tracker:

1. **Tägliche Aufgaben:** z. B. Morgenroutine oder Vitamine. Sie werden an jedem zutreffenden Tag neu als offen angezeigt.
2. **Feste Wochentage:** Aufgaben lassen sich Montag bis Sonntag individuell planen.
3. **Flexible Wochenziele:** z. B. Sport **4-mal pro Woche**, bevorzugt Mo/Di/Do/Fr, aber auch an anderen Tagen erfüllbar.
4. **Tagesabschluss:** Sobald alle tatsächlich für heute geplanten Aufgaben erledigt sind, erscheint deutlich „Für heute fertig“. Noch nicht erfüllte Wochenziele dürfen den Tagesabschluss **nicht** als unerledigt erscheinen lassen.
5. **Historie:** Vergangene Tage und damalige Aufgabenstände dauerhaft nachvollziehen; Änderungen an Regeln dürfen alte Tage nicht nachträglich verändern.
6. **Statistik:** Erledigungsquote, perfekte Tage, Serien, Wochenzielerfüllung.
7. **Widgets:** später native Android-Widgets, die dieselbe Datenbasis verwenden.

### Navigation

Vier Tabs, **nur Icons, keine Textbeschriftungen**:

- Heute: Haken im Kreis
- Verlauf: Monatskalender (kein separater Wochen-Tab)
- Statistik: Balkendiagramm
- Einstellungen: schlichtes, gut erkennbares Zahnrad

Schwebende, stark abgerundete dunkel-transparente Navigationsleiste, aktiver Tab hell. Seitenwechsel per Tippen und horizontaler Wischgeste mit Slide-/Fade-Animation.

## Designvorgaben

Die letzte HTML-Demo ist die **visuelle Referenz**, aber die native App muss in Compose umgesetzt werden. Referenz bei Bedarf erneut bereitstellen, falls nicht im Repository.

- Hintergrund: `#080808`
- Karten: `#191919`
- Navigation: ungefähr `#252525`, leicht transparent
- Text: `#E8E7E2`; sekundär `#858585`
- Roter Akzent: `#E03930`, Punkt neben der kleinen Seitenzeile wie „DEIN RHYTHMUS.“, nicht über der Android-Statusleiste
- **Alle großen Titel und großen Zahlen** verwenden die in den Einstellungen wählbare Überschriftenschrift.
- Auswahl **Klassisch** = `FontFamily.Default` mit leichtem Gewicht, systemnah; **N-Dot** = eingebettete `R.font.ndot_47`.
- Normale Texte und Aufgaben verwenden Androids Systemschrift (auf dem Gerät derzeit Geist), **keine separat eingebettete Geist-Schrift**.
- Die Textfarbe auch bei großen Zahlen, Titelkomponenten und Einstellungen **explizit hell** setzen. Frühere Versionen hatten schwarze, auf Schwarz unsichtbare Texte.
- Auf der Heute-Seite soll bei normaler Anzahl an Aufgaben **kein allgemeines Scrollen** nötig sein. Bei sehr vielen Aufgaben darf die Aufgabenliste separat scrollen.
- Aufgabenzeilen kompakt, aber Aufgabennamen gut lesbar (ca. 17 sp); Check-Kreis ca. 34 dp; Textblock vertikal mittig.
- Fortschrittsring kompakt, mittige Zahl z. B. `0/3` mit gewählter Überschriftenschrift.
- App-Version nur **unten in den Einstellungen**, nicht oben in der Statusleiste.

## Aktueller Code und Dateien

Hauptdatei:

`app/src/main/java/at/lukaechen/nplanner/MainActivity.kt`

Font:

`app/src/main/res/font/ndot_47.ttf` **oder** `.otf` (je nach tatsächlichem Repository-Stand). Nur eine Ressource mit dem Basisnamen `ndot_47`; keine `.txt`-Dateien unter `res/font`.

Weitere zentrale Dateien:

- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `.github/workflows/android.yml`

**Wichtig:** Im Repository darf nur **eine** `MainActivity`-Definition existieren. Ein früherer Upload erzeugte `MainActivity.kt herunterladen.kt` und verursachte `Redeclaration`-Fehler. Nur `MainActivity.kt` behalten.

### Technischer Status

Der native Proof-of-Concept und mindestens ein GitHub-Actions-Build funktionierten. Im weiteren Verlauf wurden UI und Navigation mehrfach überarbeitet. Die zuletzt vorbereitete `MainActivity.kt` trägt die Anzeige **`NPLANNER · VERSION 0.9`** im Setup und enthält:

- vierseitige Navigation und animierten Wechsel
- Wischgesten
- Tagesansicht mit datumsspezifischen Häkchen in `SharedPreferences`
- lokale Aufgabenliste und Pfeil-Sortierung
- kompakteren Ring, größere Checkbox-Kreise und vertikal zentrierte Task-Texte
- Umschaltung der Überschriftenschrift in Setup, gespeichert unter `use_ndot_headings`
- `statusBarsPadding()` gegen Überlagerung der Systemstatusleiste

**Nicht als fertig betrachten:** Der Sportwert `3/4` ist derzeit teilweise **hart kodiert**, Wochenziele sind noch nicht vollständig modelliert; Verlauf und Statistik sind teilweise Platzhalter. Es gibt noch keine Room-Datenbank, keine echten Widgets, keine vollständige Aufgabenbearbeitung/Archivierung und kein natives Drag-and-drop.

## Build / Test

1. Nur geänderte Dateien im GitHub-Repository ersetzen; die Pfade müssen exakt stimmen.
2. Bei einer heruntergeladenen `MainActivity_vX.kt` vor dem Upload **`MainActivity.kt`** daraus machen und die bestehende Datei ersetzen, nicht zusätzlich hochladen.
3. Commit erzeugt einen GitHub-Actions-Build; bei Bedarf unter **Actions → NPlanner APK → Run workflow** manuell starten.
4. Nur die APK aus dem **aktuellen erfolgreichen Lauf** installieren.
5. In den Einstellungen die angezeigte UI-Version prüfen und mit dem Quellcode vergleichen.
6. Bei Fehlern die ersten Zeilen `e: ... MainActivity.kt:<Zeile>` aus `compileDebugKotlin` schicken, nicht nur den Gradle-Stacktrace.

Frühere Probleme:

- `setup-android@v3` scheiterte an SDK-Paket `tools`: Workflow benötigt `packages: ''` und installiert benötigte SDK-Pakete separat.
- `.txt` unter `res/font` verursacht `mergeDebugResources`-Fehler.
- Doppelte `.kt`-Dateien verursachen `Redeclaration`.
- Fehlende explizite Textfarben machten Titel und Zahlen schwarz auf Schwarz.
- Wiederholte Downloads mit gleichem Dateinamen führten zu Versionsverwechslungen.

## Geplante nächste Schritte

1. **Repository-Iststand prüfen:** Aktuelle `MainActivity.kt`, Fontdatei und letzter erfolgreicher Build sind maßgeblich. Nicht blind auf eine alte Assistentendatei aufsetzen.
2. UI auf dem Nothing anhand eines Screenshots mit HTML-Referenz vergleichen; Abstände, Fortschrittsring, Typografie, Icons und Navigation gezielt korrigieren.
3. Datenmodell und persistente Datenbank mit **Room**: Aufgaben, Wochentage, Tages-Snapshots, Wochenziele und datierte Erledigungen. Bestehende lokale Daten bei Migration nicht unbedacht löschen.
4. Setup: Aufgaben bearbeiten, archivieren, Wochentage wählen, Drag-and-drop; Wochenziele vollständig konfigurierbar.
5. Echter Monats-/Jahresverlauf und Statistik.
6. Native Homescreen-Widgets über Glance mit gemeinsamer Datenbasis.
7. Stabile Release-Signierung und Backup/Export.

## Wichtige Zusammenarbeitshinweise

- **Nur tatsächlich geänderte Dateien** zurückgeben, mit eindeutigem Versionsnamen zum Download und exaktem Zielpfad. Keine kompletten Repository-ZIPs, außer ausdrücklich gewünscht.
- Vor Änderungen immer die **aktuelle Datei** als Grundlage nehmen. Funktionierende Bereiche unverändert lassen.
- Neue Datei gegen den Ausgangsstand vergleichen und wichtige Änderungsstellen prüfen.
- Keine erfolgreiche Kompilierung oder Gerätetests behaupten, solange diese nicht tatsächlich durchgeführt wurden.
- Keine UI-Demodaten als echte Historie ausgeben.
- Kurze, konkrete Anweisungen für GitHub-Browser-Uploads; keine lokale Installation voraussetzen.

---

## Prompt zum Fortsetzen mit einem anderen Copilot-/KI-Konto

> Ich entwickle **NPlanner**, eine native Android-App mit **Kotlin und Jetpack Compose** für mein Nothing Phone (4a) Pro. Das Repository liegt unter https://github.com/lukaechen/NPlanner. Lies zuerst die gesamte `README.md` und anschließend die aktuelle `app/src/main/java/at/lukaechen/nplanner/MainActivity.kt` sowie bei Bedarf die Gradle-/Workflow-Dateien. **Gehe nicht davon aus, dass frühere Chats verfügbar sind.**
>
> Ziel ist eine hochwertige, von **Nothing OS 5 / Glyph** inspirierte Darkmode-App für tägliche Routinen, feste Wochentage und flexible Wochenziele (z. B. Sport 4×/Woche), mit Monatsverlauf, Statistik und später Widgets. Der HTML-Prototyp dient als visuelle Referenz, die App selbst bleibt nativ. Große Titel und Zahlen sollen zwischen **N-Dot** und klassischer Systemschrift umschaltbar sein; normaler Text nutzt die Android-Systemschrift. Auf „Heute“ werden Aufgaben nur abgehakt, nicht verwaltet. Ein erfüllter Tag muss sich abgeschlossen anfühlen, auch wenn Wochenziele noch offen sind.
>
> **Wichtig:** Verwende ausschließlich den tatsächlichen aktuellen Repository-Stand. Prüfe zuerst, was bereits implementiert ist, und frage nur nach Dateien, die du nicht einsehen kannst. Behebe die von mir im nächsten Prompt beschriebene konkrete Änderung, ohne funktionierende Teile umzubauen. Gib **nur geänderte Dateien** mit eindeutigen Versionsnamen und Zielpfaden zurück, keine vollständigen Repository-ZIPs. Überprüfe Änderungen auf Kotlin-/Compose-Syntax und erfinde keine Testergebnisse. Ich baue APKs ausschließlich über GitHub Actions und teste sie auf meinem Nothing.
>
> **Meine nächste konkrete Aufgabe:** [HIER DIE GEWÜNSCHTE ÄNDERUNG EINFÜGEN]
