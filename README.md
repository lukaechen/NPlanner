# Daily – nativer Teststand 0.1

Kotlin / Jetpack Compose, keine WebView. Erster Installations- und Buildtest, keine vollständige Portierung des HTML-Prototyps.

Enthalten: heutiges Datum, tägliche Aufgaben, datumsspezifische lokale Erledigungen, Aufgaben anlegen und per Pfeilen sortieren. Kein Internetzugriff in der App.
Noch nicht enthalten: Wochenziele, Wochentage, Verlaufansicht, Statistik, Widgets, Drag-and-drop, Backup, Bearbeiten/Archivieren. Historische Erledigungen werden gespeichert, aber noch nicht angezeigt. SharedPreferences ist eine vorläufige Speicherung; vor produktiver Nutzung auf Room migrieren.

## Upload
ZIP entpacken. Inhalt direkt ins Repository hochladen, nicht die ZIP und nicht einen zusätzlichen Projektordner.
Auch .github/workflows/android.yml muss vorhanden sein. Falls der Browser den Punktordner auslässt: Add file > Create new file, Dateiname .github/workflows/android.yml; Inhalt aus workflow-zum-kopieren.txt einfügen. Diese Kopie allein startet keinen Build.

## Build
Actions > Android APK. Nach erfolgreichem Lauf: Artifacts > Daily-Test-APK. ZIP entpacken und app-debug.apk installieren.
Kein Gradle Wrapper nötig: der Workflow installiert die festgelegte Gradle-Version.

## Signierung – wichtig
Dies ist eine Debug-APK. Ohne DEBUG_KEYSTORE_BASE64 erzeugt jeder frische Cloud-Runner einen neuen Debug-Schlüssel. Folgeversionen lassen sich dann eventuell nicht über die vorherige installieren. Nicht mit wichtigen Daten verwenden und nicht einfach deinstallieren, wenn Daten erhalten bleiben sollen.
Optional: einen dauerhaften Android-Debug-Keystore mit den Standard-Debug-Zugangsdaten als Base64 in das Repository-Secret DEBUG_KEYSTORE_BASE64 hinterlegen. Niemals Schlüsseldateien ins Repository committen. Eine richtige Release-Signierung ist vor dauerhafter Nutzung separat einzurichten.

## Prüfung
Projektdateien erstellt, aber hier weder Android-Build noch Gerätetest durchgeführt. Der erste GitHub-Actions-Lauf ist der Kompilierungstest. Bei Fehlern den fehlgeschlagenen Schritt und die Fehlermeldung teilen.
