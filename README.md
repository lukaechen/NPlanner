# NPlanner 0.2 – visueller Compose-Port

Dieses Paket ersetzt den Proof-of-Concept durch eine native Jetpack-Compose-Oberfläche, die sich am letzten HTML-Prototyp orientiert.

## Unbedingt vor dem Commit
Die bereits vorhandene Schriftdatei muss hier liegen:

`app/src/main/res/font/ndot_47.otf`

Die Datei `NDOT_DATEI_HIER_ABLAGE.txt` danach löschen.

## Änderungen
- Projekt- und Appname: NPlanner
- Application-ID: `at.lukaechen.nplanner`
- N-Dot für Seitentitel
- Android-Systemschrift für normalen Text
- eigener Fortschrittsring
- eigene schwebende Bottom-Navigation
- HTML-nahe Heute-, Woche-, Verlauf-, Statistik- und Setup-Seiten
- keine WebView

## Noch nicht final
Dieser Build ist die visuelle Basis. Verlauf und Statistik sind noch nicht vollständig historisch. Room-Datenbank, echtes Wochenziel-Modell, Bearbeiten/Archivieren, natives Drag-and-drop und Widgets folgen.

## Installation
Durch die neue Application-ID wird NPlanner als neue App neben dem bisherigen Daily-Test installiert. Das ist beabsichtigt.
