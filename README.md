# NPlanner

**Routinen planen. Training dokumentieren. Fortschritt verstehen.**

Native Android-App mit Kotlin und Jetpack Compose, inspiriert vom reduzierten Design von Nothing OS. NPlanner verbindet tägliche Aufgaben und flexible Wochenziele mit einer Trainingsbibliothek, Workout-Vorlagen und nachvollziehbaren Trainingseinheiten.

[Repository öffnen](https://github.com/lukaechen/NPlanner)

**Dokumentationsstand:** 23. November 2026 · **Zuletzt vorbereitete UI-Version:** 0.43 einschließlich Compilerkorrektur und nachträglicher Designanpassung der Farb-/Sprachauswahl.

> Diese README dokumentiert den zuletzt gemeinsam entwickelten Stand. Maßgeblich sind immer die tatsächlich eingecheckten Dateien, der zugehörige Commit und dessen Build. Nicht jede vorbereitete Änderung wurde auf einem Android-Gerät getestet. Diese Dokumentation ist kein Nachweis eines erfolgreichen Builds oder einer vollständig abgeschlossenen Datenmigration.

---

## Inhalt

- [Projekt auf einen Blick](#projekt-auf-einen-blick)
- [Funktionen](#funktionen)
- [Bedienung](#bedienung)
- [Design und Interaktion](#design-und-interaktion)
- [Projektstruktur](#projektstruktur)
- [Daten und Migrationen](#daten-und-migrationen)
- [Build und Installation](#build-und-installation)
- [Entwicklungsstand und offene Arbeiten](#entwicklungsstand-und-offene-arbeiten)
- [Testcheckliste](#testcheckliste)
- [Weiterentwicklung und KI-Übergabe](#weiterentwicklung-und-ki-übergabe)

## Projekt auf einen Blick

| Bereich | Stand |
| --- | --- |
| Plattform | Native Android-App, keine WebView-Oberfläche |
| UI | Kotlin und Jetpack Compose, Material 3 mit eigenen Komponenten |
| Paket / Namespace | `at.lukaechen.nplanner` |
| Mindestversion | Android API 26 laut zuletzt bereitgestellter App-Gradle-Datei |
| Compile-/Target-SDK | API 35 laut zuletzt bereitgestellter App-Gradle-Datei |
| JVM-Ziel | Java 17 |
| Build | GitHub Actions; lokale Android-Studio-Installation für den bisherigen Arbeitsablauf nicht erforderlich |
| Daten | SharedPreferences als primäre Speicherung; zusätzliche Room-Anbindung als Übergang |
| Sprachen | Deutsch und Englisch für die ausgelagerten Oberflächentexte |
| Darstellung | Hell, Dunkel und Auto; N-Dot oder NType 82 für Überschriften |
| Cloud | Google-Drive-Anbindung geplant, noch nicht implementiert |

Die aktuellen Gradle-Dateien im Repository haben Vorrang vor Versionsangaben in dieser README. Abhängigkeiten nicht ohne konkreten Anlass aktualisieren.

## Funktionen

### Aufgaben und Wochenziele

- Tägliche Aufgaben und individuelle Wochentage.
- Ganztägige Aufgaben, feste Uhrzeiten und Intervalle.
- Flexible Wochenziele mit mehreren Erledigungen pro Woche.
- Pflichtziele und optionale Aufgaben.
- Tagesfortschritt als Ring oder Balken.
- Deutlicher Tagesabschluss, sobald die erforderlichen Tagesziele erfüllt sind.
- Monats-/Jahresverlauf mit Kalender- oder Punktdarstellung.
- Historische Tagesstände mit gespeicherten Aufgabendetails, soweit vorhanden.
- Aufgabenverwaltung und Sortierung in den Einstellungen.

**Grundregel:** Ein noch offenes Wochenziel darf nicht automatisch einen ansonsten abgeschlossenen Tag als unerledigt darstellen. Neue Aufgaben dürfen vergangene Tage nicht rückwirkend verschlechtern.

### Übungsbibliothek

- Kraft, Körpergewicht, Zeit und Cardio.
- Vorgegebene Muskelgruppen mit Haupt- und Nebenmuskeln statt freier Texteingabe.
- Suche, Filter und Sortierung.
- Standard: nach Übungsart gruppiert und innerhalb der Gruppen Name A–Z.
- Übungsdaten bearbeiten, ohne die bestehende ID zu ersetzen.
- Beschreibung, Ausführungsschritte und Medienlinks.
- Direkte HTTPS-Bildlinks innerhalb der App anzeigen.
- Videolinks extern öffnen; kein eingebetteter Videoplayer.
- Übungsdaten als JSON teilen oder als `.npex.json` exportieren.
- KI-Prompt und JSON-Import im Menü **Mit KI anlegen**.

Die Bibliothek dient zum Verwalten und Nachschlagen. **Sätze werden ausschließlich innerhalb eines gestarteten Workouts erfasst.** Ältere AMRAP-Übungseinträge bleiben aus Kompatibilitätsgründen lesbar; AMRAP ist im aktuellen Ablauf eine Workout-Art.

### Krafttraining und reguläre Workouts

- Workout vorbereiten und Übungen auswählen.
- Notizfeld erst über **+ Notiz hinzufügen** einblenden.
- Übungen während des Trainings ergänzen.
- Übungskarten antippen, um die Satzeingabe aufzuklappen.
- Gewicht und Wiederholungen direkt an der Übung erfassen.
- Übungskopf gedrückt halten und ziehen, um die Reihenfolge zu ändern.
- Automatisches Scrollen am Listenrand während des Ziehens.
- Informationssymbol für Übungsdetails.
- Direkt sichtbare Aktionen für Vorlage speichern, Bearbeiten und Löschen.
- Zuletzt gespeicherter Satz als nächste Vorgabe, nicht automatisch der persönliche Höchstwert.
- Eigene Sitzungs-ID für neue Trainingseinheiten, auch bei mehreren Workouts am selben Tag.
- Abschlussübersicht mit gruppierten Sätzen und optionaler Einzelsatzansicht.
- Vergangene Einheiten mit Bestätigung löschen; zugehörige Sätze werden entfernt und Rekorde neu berechnet.

Beispiel einer kompakten Auswertung:

| Bankdrücken | Ergebnis |
| --- | --- |
| Gesamt | 4 Sätze, 34 Wiederholungen |
| Gleiche Sätze zusammengefasst | 3 Sätze mit 65 kg × 8 Wiederholungen |
| Weiterer Satz | 1 Satz mit 60 kg × 10 Wiederholungen |

Ältere Logs ohne Sitzungs-ID werden nach Workout und Datum zusammengefasst. Daraus lassen sich weder getrennte Einheiten desselben Tages noch eine genaue Trainingsdauer zuverlässig rekonstruieren.

### AMRAP

- Manuell gestarteter Countdown mit Millisekundenanzeige.
- Feste Zeichenplätze verhindern das seitliche Springen der Ziffern.
- Aktuelle Übung und Wiederholungsziel anzeigen.
- **Übung erledigt** wechselt zur nächsten Übung.
- Nach der letzten Übung wird eine vollständige Runde gezählt.
- Pause/Fortsetzen über einen Symbolknopf.
- Ergebnis mit Runden, zusätzlichen vollständig bestätigten Übungen und Wiederholungen.
- Eigener AMRAP-Verlauf und Rekordbereich.
- AMRAP nicht zusätzlich als leeres reguläres Workout in der Vorbereitungsliste anzeigen.

**Cindy:** 20 Minuten; je Runde 5 Klimmzüge, 10 Liegestütze und 15 Kniebeugen.

**Dev-AMRAP:** 2 Minuten; je Runde 3 Liegestütze, 5 Kniebeugen und 2 Klimmzüge. Diese Vorlage dient dem schnellen Funktionstest, nicht als Trainingsvorgabe.

Teilwiederholungen einer nicht bestätigten Übung werden nicht geschätzt. Vorzeitig beendete Einheiten bleiben gekennzeichnet. Die aktuelle Rekordlogik berücksichtigt auch solche Einheiten innerhalb ihrer Konfiguration; ein fairer Vergleich muss daher Dauer, Übungsfolge, Zielwerte und Abschlussstatus berücksichtigen.

### Workout-Vorlagen

**Es gibt nur einen gemeinsamen Vorlagenbestand.** Keine getrennten Bereiche oder Berechtigungen für „Meine“ und „Standard“.

- Alle Einträge lassen sich verwenden, bearbeiten und löschen.
- Mitgelieferte Beispiele werden einmalig in denselben persistenten Bestand übernommen.
- Gelöschte Beispiele werden nicht bei jedem App-Start wieder angelegt.
- Vorlagen speichern Übungen, Reihenfolge, Workout-Art, Dauer und passende Zielwerte.
- Bearbeiten speichert unter demselben Vorlagenschlüssel.
- Beim Verwenden erscheint eine kompakte Vorbereitung statt des vollständigen manuellen Formulars.
- Bei AMRAP lässt sich insbesondere die Dauer vor dem Anlegen anpassen.
- Änderungen an einer Vorlage verändern bestehende Trainingseinheiten nicht rückwirkend.

### Darstellung und Sprache

**Farbmodus:**

| Auswahl | Verhalten |
| --- | --- |
| Hell | Helle Flächen, dunkle Schrift |
| Dunkel | Dunkle Flächen, helle Schrift |
| Auto | Folgt späteren Änderungen der Systemeinstellung |

Beim ersten Start ohne gespeicherte Farbauswahl wird die aktuelle Systemeinstellung einmalig als Hell oder Dunkel übernommen. Auto kann anschließend aktiviert werden. Beim Update von einem Stand ohne Farbauswahl gilt dieselbe Initialisierung.

**Sprache:** Deutsch oder Englisch, innerhalb der App gespeichert. Ohne eigene Sprachwahl wird bei deutscher Systemsprache Deutsch verwendet, sonst Englisch.

Die ausgelagerten Menüs, Dialogtexte, Statusmeldungen und Muskelbezeichnungen sind übersetzt. Eigene Aufgaben, Übungsnamen, Vorlagennamen, Notizen und gespeicherte Beschreibungen werden nicht automatisch übersetzt. Das gilt auch für bereits als Daten gespeicherte Beispielinhalte. Der technische KI-Import-Prompt bleibt derzeit deutsch.

## Bedienung

Die Hauptnavigation enthält vier Symbole:

| Seite | Zweck |
| --- | --- |
| Heute | Tagesaufgaben erledigen und Fortschritt sehen |
| Verlauf | Vergangene Tage ansehen |
| Training | Workouts, Übungen und Rekorde |
| Einstellungen | Aufgaben, Darstellung und Entwickleroptionen |

Unter Training sind die Rekorde in dieser Reihenfolge gegliedert:

1. Krafttraining
2. Cardio
3. AMRAP

Die Einstellungen bleiben in dieser Reihenfolge:

1. Aufgaben und Wochenziele
2. Darstellung einschließlich Farbmodus, Sprache, Überschriftenschrift und Ansichtsoptionen
3. Entwickler und Test
4. Versionsanzeige am Ende

## Design und Interaktion

NPlanner soll ruhig, reduziert und konsistent bleiben. Vorhandene Komponenten bevorzugen, statt für jede Funktion neue Standard-Material-Elemente einzuführen.

### Farben

| Rolle | Dunkel | Hell |
| --- | --- | --- |
| Hintergrund | `#080808` | `#F8F8F5` |
| Karten | `#191919` | `#FFFFFF` |
| Zweite Fläche | `#252525` | `#ECECE8` |
| Haupttext | `#E8E7E2` | `#171717` |
| Sekundärtext | `#929292` | `#626262` |
| Akzent | `#E03930` | `#C42C24` |

Neue Farben über die zentrale Palette beziehen. Keine fest verdrahteten dunklen Dialogflächen oder hellen Texte hinzufügen, die den Light-Mode unlesbar machen.

### Typografie

- Große Titel und vorgesehene große Zahlen: N-Dot oder NType 82 entsprechend der Auswahl.
- Klassische Überschriften: `ntype_82.ttf`, sofern vorhanden.
- Normale Texte, Eingabefelder und Beschriftungen: Android-Systemschrift.
- Keine separate Geist-Datei einbetten; die konkrete Systemschrift hängt vom Gerät ab.
- Zahlen im Live-Countdown behalten feste Zeichenplätze.

### Verbindliche UI-Regeln

- Passive Fenster möglichst mit einem großen unteren Schließen-/Fertig-Button schließen. Kein zusätzliches oberes X für denselben Zweck.
- Löschen immer mit Papierkorb, nie mit einem als Schließen missverständlichen roten X.
- Destruktive Aktionen bestätigen.
- Aktive Trainingsfenster dürfen minimierbar sein; das ist kein Trainingsabschluss.
- Aufklappen einer Übung darf den oberen Fensterrand nicht nach oben verschieben. Das Workout-Fenster besitzt dafür eine feste Höhe und einen intern scrollbaren Inhalt.
- Ausgewählte und nicht ausgewählte Übungen haben identische Abstände und feste Markierungsflächen.
- Vorlagenverwaltung als gut sichtbarer Einstieg oben im Workout-Bereich.
- Farb- und Sprachauswahl verwenden neutrale, abgerundete Auswahlflächen mit invertiertem Aktivzustand statt Standard-FilterChips.
- Fingerwischen soll der Fingerbewegung folgen. Tabwechsel dürfen keine dazwischenliegenden Seiten aufblitzen lassen und keine künstlichen Wartephasen einführen.

## Projektstruktur

Wichtige Dateien, keine vollständige Auflistung des Repositories:

| Pfad | Aufgabe |
| --- | --- |
| `app/src/main/java/at/lukaechen/nplanner/MainActivity.kt` | Hauptoberfläche, Modelle, Abläufe und bisherige Speicherlogik |
| `app/src/main/java/at/lukaechen/nplanner/NPlannerAppearance.kt` | Palette, Farb-/Sprachwahl, Ressourcenauflösung und zugehörige Einstellungsoberfläche |
| `app/src/main/java/at/lukaechen/nplanner/NPlannerMuscleLabels.kt` | Übersetzung vordefinierter Muskelbezeichnungen |
| `app/src/main/java/at/lukaechen/nplanner/NPlannerDatabase.kt` | Bisher eingeführte Room-Übergangsanbindung; aktuellen Repository-Stand prüfen |
| `app/src/main/res/values/nplanner_strings.xml` | Deutsche Fallback-Texte |
| `app/src/main/res/values-de/nplanner_strings.xml` | Deutsche Oberflächentexte |
| `app/src/main/res/values-en/nplanner_strings.xml` | Englische Oberflächentexte |
| `app/src/main/res/font/ndot_47.ttf` oder `.otf` | N-Dot; nur eine Ressource mit diesem Basisnamen |
| `app/src/main/res/font/ntype_82.ttf` | NType 82 |
| `app/src/main/AndroidManifest.xml` | Android-Komponenten und Berechtigungen |
| `app/build.gradle.kts` | App-Konfiguration und Abhängigkeiten |
| `build.gradle.kts` | Projektweite Plugins |
| `.github/workflows/android.yml` | Bisher verwendeter APK-Workflow; tatsächlichen Dateinamen prüfen |

Die App ist nicht mehr ausschließlich eine einzelne Kotlin-Datei. Neue Änderungen müssen die ausgelagerten Farb- und Sprachdateien berücksichtigen. Keine parallelen Kopien von `MainActivity` im Quellordner ablegen.

### Wichtig zum letzten 0.43-Update

Der zuletzt vorbereitete Stand besteht aus:

1. `MainActivity.kt` und den Sprach-/Muskeldateien aus dem korrigierten 0.43-Update.
2. Der nachträglich angepassten `NPlannerAppearance.kt` mit Nothing-inspirierter Farb-/Sprachauswahl.

Die Methoden heißen **`updateMode()` und `updateLanguage()`**. Nicht wieder `setMode()` beziehungsweise `setLanguage()` hinzufügen: Diese Namen kollidierten mit den von Kotlin erzeugten Property-Settern.

Die nachträgliche Designkorrektur ändert die angezeigte UI-Version nicht. Sie bleibt 0.43.

## Daten und Migrationen

### Aktueller Speicherstand

**SharedPreferences bleibt die primäre Datenquelle.** Die bisher ergänzte Room-Anbindung ist eine Übergangslösung und darf nicht als vollständig normalisierte, abgeschlossene Migration beschrieben werden.

Wichtige Speicherbereiche und Schlüssel aus dem aktuellen Quellstand:

| Bereich | Bedeutung |
| --- | --- |
| `nplanner-v2` | Hauptspeicher |
| `rules` | Aufgabenregeln |
| `exercises_v1` | Übungsbibliothek |
| `exercise_logs_v1` | Trainingssätze einschließlich Workout-/Sitzungszuordnung |
| `workouts_v1` | Angelegte Workouts |
| `snap:...` | Tages-Snapshots |
| `workout_session:...` | Abgeschlossene reguläre Trainingseinheiten |
| `amrap_session:...` | AMRAP-Ergebnisse |
| `workout_template:...` | Gemeinsamer Vorlagenbestand |
| `editable_template_catalog_v43` | Marker für einmalige Übernahme mitgelieferter Vorlagen |
| `appearance_mode_v43` | Hell/Dunkel/Auto |
| `app_language_v43` | Sprachwahl |
| `nplanner-training-session` | Laufender Trainings-/AMRAP-Zustand |

Bestehende Daten werden bei Änderungen nicht vorsorglich gelöscht. IDs, Schlüssel und JSON-Feldnamen sind technische Identitäten und werden nicht übersetzt.

### Historie und Speicherbedarf

- Übungsdefinitionen und Medienlinks werden nicht als komplette Medienkopien täglich archiviert.
- Tages-Snapshots enthalten im bisherigen Modell teilweise wiederholte Aufgabennamen und Erledigungswerte. Das ist noch nicht das geplante maximal normalisierte Datenmodell.
- Fehlende frühere Snapshots können nur aus noch vorhandenen Informationen rekonstruiert werden. Bereits gelöschte historische Details lassen sich nicht zuverlässig erfinden.
- Neue Sitzungsübersichten enthalten bewusst kleine Ergebnis-Snapshots, damit spätere Änderungen an Übungsnamen die alte Auswertung nicht verändern.
- Die frühere Begrenzung auf 500 Trainingslogs wurde entfernt.
- Bilder über direkte HTTPS-Links werden in der Anzeige geladen; der aktuelle Abruf ist begrenzt. Es gibt keine zugesicherte umfassende Cache-Verwaltung.
- Ältere lokale Fotos/Videos können weiterhin Speicher belegen. Externe Links entfernen bestehende Dateien nicht automatisch.

**Noch kein vollständiges App-Backup:** Der Übungsexport ersetzt keine Sicherung von Aufgaben, Einstellungen, Verlauf und Trainingseinheiten. Google Drive ist noch nicht angebunden.

### Import und Export

Das technische Übungsformat heißt `nplanner.exercise.v1`. Exportdateien verwenden `.npex.json`. Enthalten sind Übungsdefinitionen und unterstützte externe Medienlinks, nicht der gesamte Trainingsverlauf.

Schema und Formatversion bei Erweiterungen kompatibel halten. Direkte Dateizuordnung zu NPlanner und ein vollständiger Backup-/Restore-Ablauf sind nicht als fertig anzusehen.

## Build und Installation

### Üblicher Ablauf über GitHub

1. Geänderte Dateien an ihren exakten Projektpfaden ersetzen oder ergänzen.
2. Bei Update-ZIPs Ordner **zusammenführen**, nicht den bestehenden `app`-Ordner vollständig ersetzen.
3. Alle zusammengehörigen Kotlin- und Ressourcendateien gemeinsam committen.
4. Den GitHub-Actions-Lauf dieses Commits öffnen.
5. Erst nach erfolgreichem Build das zugehörige APK-Artefakt herunterladen.
6. APK als Update installieren und die Anzeige am Ende der Einstellungen kontrollieren.

Nicht deinstallieren und keine App-Daten löschen, um einen gewöhnlichen Updatefehler zu beheben. Bei Signaturproblemen zuerst die Signierung prüfen. Für langfristige Updates und die spätere Google-Anbindung ist eine dauerhafte Signatur zu klären.

### Wenn eine alte Version angezeigt wird

- Prüfen, ob wirklich die neue `MainActivity.kt` im Repository liegt.
- Bei 0.43 muss der Aufruf `NPlannerAppearanceSettings()` vorhanden sein.
- Prüfen, ob die Sprachressourcen gemeinsam aktualisiert wurden; die sichtbare Versionsanzeige ist inzwischen ein Ressourcentext.
- APK aus dem richtigen erfolgreichen Commit herunterladen, nicht eine ältere Datei erneut installieren.
- UI-Version, Gradle-`versionName`, `versionCode` und Commit sind unterschiedliche Angaben. Sie müssen künftig konsistent gepflegt werden; die Anzeige allein identifiziert keinen Build eindeutig.

### Fehlerberichte

Benötigt werden die ersten konkreten `e: ... Datei.kt:Zeile`-Meldungen oder Ressourcenfehler und die betroffenen aktuellen Dateien. Ein allgemeiner Gradle-Stacktrace reicht meist nicht aus.

Bereits aufgetretene Fehler:

| Meldung | Ursache / Abhilfe |
| --- | --- |
| `Unresolved reference: TextOverflow` | Import fehlte |
| `No value passed for parameter p6` | Callback-Signatur erweitert, Aufruf nicht vollständig angepasst |
| `Conflicting declarations` | Doppelte Exportvariablen oder Funktionen |
| `Platform declaration clash: setMode/setLanguage` | Explizite Methoden kollidierten mit Property-Settern; `updateMode/updateLanguage` verwenden |
| `FontFamily` statt `SystemFontFamily` | `compositionLocalOf<FontFamily>` ausdrücklich typisieren |
| `Redeclaration: MainActivity` | Zusätzliche Kopie der Kotlin-Datei im Quellordner |
| `Unable to strip ... libandroidx.graphics.path.so` | In den bisherigen Logs eine Warnung, nicht der eigentliche Kotlin-Buildfehler |

## Entwicklungsstand und offene Arbeiten

### Zuletzt vorbereitet

| Version | Schwerpunkt |
| --- | --- |
| 0.38–0.39 | Navigation, Übungsbearbeitung, Drag-and-drop und aufklappbare Trainingskarten |
| 0.40 | Trainingseinheiten, letzte Satzwerte als Vorgabe, direkte Aktionen und Systemschrift |
| 0.41 | Festes Workout-Fenster, gruppierte Auswertung, vergangene Einheiten löschen |
| 0.42 | Einstellungsreihenfolge und reduzierte Schließen-Aktionen |
| 0.43 | Gleichberechtigte Vorlagen, Hell/Dunkel/Auto, Deutsch/Englisch |
| 0.43 Korrekturen | JVM-Setter-Kollision behoben; Farb-/Sprachauswahl optisch an bestehende Komponenten angepasst |

### Noch nicht abgeschlossen

1. **Vollständige Room-Migration:** normalisierte Aufgaben-/Regelversionen, historische Tageszuordnung, Trainingseinheiten und Vorlagen; SharedPreferences-Daten verlustfrei übernehmen und Wiederherstellung testen.
2. **Google-Drive-Sicherung in den Einstellungen:** Konto verbinden, automatisch/manuell sichern, Sicherungsstatus und Wiederherstellung. Noch kein implementierter Login, OAuth-Ablauf oder Drive-Sync.
3. **Backup oder Mehrgeräte-Sync festlegen:** Bidirektionales Zusammenführen ist nicht dasselbe wie Sicherung/Wiederherstellung. Konfliktregeln nicht stillschweigend voraussetzen.
4. **Übungsaustausch abrunden:** sichere Dateiimporte, Validierung und gegebenenfalls Dateizuordnung. Keine automatischen Uploads zu Medienanbietern voraussetzen.
5. **Weitere Workout-Arten:** For Time und EMOM sind auswählbar, aber kein vollständig ausgebauter Live-Coach wie AMRAP.
6. **Widgets:** geplant, noch nicht implementiert.
7. **Qualitätssicherung:** reproduzierbare Builds, Migrationstests, Sprach-/Theme-Prüfung, dauerhafte Signierung und konsistente Versionsangaben.

Keine dieser geplanten Funktionen als bereits vorhanden bewerben. Vor einem größeren Umbau den tatsächlichen Repository-Stand prüfen.

## Testcheckliste

Nach Änderungen mindestens den betroffenen Bereich und naheliegende Regressionen prüfen:

- [ ] GitHub-Actions-Build des richtigen Commits erfolgreich.
- [ ] Update ohne Datenverlust installiert.
- [ ] Heute → Einstellungen und zurück: keine Zwischenseiten oder zusätzliche Wartephase.
- [ ] Fingerwischen und gespeicherte Scrollpositionen funktionieren.
- [ ] Aufgaben abhaken, App vollständig schließen und wieder öffnen.
- [ ] Neue/gelöschte Aufgaben verändern vorhandene historische Tagesstände nicht unbeabsichtigt.
- [ ] Workout-Karte aufklappen: oberer Fensterrand bleibt stehen.
- [ ] Übungen durch Halten/Ziehen umsortieren; Reihenfolge nach erneutem Öffnen prüfen.
- [ ] Satz mit verändertem Gewicht speichern; nächster Satz und nächstes Workout übernehmen die Werte.
- [ ] Zwei Einheiten desselben Workouts am selben Tag bleiben getrennt.
- [ ] Abschlussübersicht gruppiert gleiche Sätze korrekt; Einzelsätze bleiben abrufbar.
- [ ] Vergangene Einheit löschen: nur deren Daten verschwinden, Rekorde aktualisieren sich.
- [ ] Dev-AMRAP starten, pausieren, fortsetzen und vollständig abschließen.
- [ ] AMRAP-Verlauf und Rekorde zeigen das gespeicherte Ergebnis.
- [ ] Mitgelieferte und eigene Vorlage bearbeiten, verwenden und löschen; gelöschte Vorlage bleibt nach Neustart weg.
- [ ] Hell/Dunkel auf Hauptseiten und Dialogen lesbar; Auto folgt einem Systemwechsel.
- [ ] Deutsch/Englisch wechseln; Platzhalter, Menütexte und unveränderte eigene Inhalte prüfen.
- [ ] Videos öffnen extern, Bilder laden bei gültigem Direktlink; Offline-/Fehlerfall bleibt bedienbar.

**Prüfstatus des zuletzt vorbereiteten Updates:** Kotlin-Syntax, Ressourcenverweise, dynamische Textargumente, DE/EN-Platzhalter und XML-Struktur wurden statisch geprüft. Ein vollständiger Android-Build und ein visueller Gerätetest des letzten Design-Patches wurden in der Assistenzumgebung nicht durchgeführt. Statische Prüfungen ersetzen weder Kompilierung noch Laufzeittests.

## Weiterentwicklung und KI-Übergabe

### Arbeitsregeln

- Immer aktuelle Repository-Dateien oder aktuelle Uploads als Ausgangspunkt verwenden.
- Funktionierende Bereiche nicht nebenbei umschreiben.
- Nur tatsächlich geänderte Dateien liefern, mit eindeutigen Downloadnamen und exakten Zielpfaden.
- Bei mehreren zusammengehörigen Dateien ein kleines Update-Paket mit Projektpfaden und Einbauhinweisen bereitstellen, kein unnötiges vollständiges Repository.
- Dateinamen beim Einbau korrekt zurückbenennen; keine doppelten Klassen erzeugen.
- Bestehende IDs, Daten, Vorlagen und Migrationsmarker erhalten.
- Neue Oberflächentexte in allen drei `nplanner_strings.xml`-Dateien pflegen. Technische IDs nicht übersetzen.
- Für neue UI-Elemente zentrale Farben und vorhandene Typografie verwenden.
- Callback-Aufrufe, Imports und Ressourcen prüfen, nicht nur Klammern zählen.
- Erfolgreiche Builds, Gerätetests oder Cloud-Funktionen nur behaupten, wenn sie tatsächlich durchgeführt beziehungsweise implementiert wurden.
- Kurz, präzise und umsetzbar antworten. Keine unnötigen Features hinzufügen.

### Übergabe-Prompt für einen neuen Chat oder ein anderes KI-Konto

> Ich entwickle NPlanner, eine native Android-App mit Kotlin und Jetpack Compose. Lies zuerst die gesamte README.md und danach die aktuellen Projektdateien. Frühere Chats sind keine Voraussetzung. Maßgeblich ist der tatsächliche Repository-Stand, nicht eine ältere Versionsdatei aus einem Gespräch.
>
> Repository: [NPlanner auf GitHub](https://github.com/lukaechen/NPlanner).
>
> Die App verbindet Aufgaben, Wochenziele, Tageshistorie und Training. Das Design ist Nothing-inspiriert: ruhige Flächen, rote Akzente, N-Dot oder NType 82 für Überschriften, Systemschrift für normale Texte. Hell/Dunkel/Auto und Deutsch/Englisch sind zu berücksichtigen. Alle Workout-Vorlagen sind gleichberechtigt bearbeitbar und löschbar. Übungsdefinitionen sind keine Trainingseinheiten; Sätze werden nur in gestarteten Workouts geloggt.
>
> Prüfe MainActivity.kt, NPlannerAppearance.kt, NPlannerMuscleLabels.kt, NPlannerDatabase.kt, die Sprachressourcen und bei Bedarf Gradle/Manifest/Workflow. SharedPreferences ist bislang primär; Room ist noch eine Übergangsanbindung. Google Drive und Widgets sind geplant, nicht fertig. Die zuletzt vorbereitete UI-Version ist 0.43 mit korrigierten updateMode/updateLanguage-Methoden und nachträglich angepasster Farb-/Sprachauswahl. Prüfe, welche Änderungen wirklich eingecheckt sind.
>
> Erhalte bestehende Nutzerdaten und funktionierende Funktionen. Gib nur geänderte Dateien mit exakten Zielpfaden zurück. Ich baue über GitHub Actions und teste auf einem Android-Gerät. Unterscheide klar zwischen statischer Prüfung, erfolgreichem Build und Gerätetest. Wenn Dateien fehlen, frage gezielt danach, statt einen älteren Stand zu verwenden.
>
> Meine nächste konkrete Aufgabe beschreibe ich anschließend.

---

**Pflegehinweis:** Nach funktionalen Änderungen Funktionsstand, Dateistruktur, Datenmigrationen, bekannte Einschränkungen und Prüfstatus aktualisieren. Die README soll eine verlässliche Übergabe bleiben, keine Sammlung überholter Versprechen.
