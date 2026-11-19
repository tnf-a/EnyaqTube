# EnyaqTube

YouTube auf dem Display deines Škoda Enyaq – über Android Auto, mit deinem Konto
(Abos, Verlauf, Playlists) und mit Werbeblocker.

## So funktioniert's

Der Enyaq (MIB3) hat **Android Auto**, kein eigenes Android. Android Auto erlaubt
eigentlich keine Video-Apps. EnyaqTube meldet sich deshalb als *Navigations-App* an.
Solche Apps bekommen eine freie Zeichenfläche auf dem Autodisplay, und darauf läuft
die mobile YouTube-Website. Tippen und Wischen am Autodisplay wird an die Seite
weitergegeben.

* **Anmeldung:** einmal in der App auf dem Handy anmelden. Die Anmeldung gilt dann
  auch im Auto.
* **Werbung:** Werbeserver werden blockiert, und Werbung wird aus den Player-Daten
  entfernt. Falls doch eine erscheint, wird sie sofort übersprungen.
* **Fahrsperre:** Ab ca. 10 km/h wird das Bild schwarz und das Video pausiert. Die
  Geschwindigkeit kommt vom Auto und vom GPS des Handys. Optional läuft nur der Ton
  weiter (Schalter in der Handy-App). Die Sperre lässt sich nicht abschalten.

## Installation

1. **APK herunterladen:** Auf dem Handy unter *Releases → latest* die Datei
   `EnyaqTube.apk` laden. Alternativ: *Actions → letzter Lauf → Artifacts*.
2. **Installieren:** APK öffnen und „Installation aus unbekannten Quellen“ für den
   Browser bzw. Dateimanager erlauben.
3. **App öffnen** und Standort erlauben (für die Fahrsperre). Dann auf **Anmelden**
   tippen und mit dem Google-Konto einloggen.
4. **Android Auto Entwicklermodus:**
   Einstellungen → Apps → Android Auto → *Weitere Einstellungen in der App*
   (oder Android-Auto-Einstellungen direkt öffnen) → ganz unten **10× auf „Version“**
   tippen → bestätigen.
   Danach oben rechts ⋮ → **Entwicklereinstellungen** → **„Unbekannte Quellen“**
   aktivieren.
5. Handy mit dem Enyaq verbinden. **EnyaqTube** erscheint im App-Menü von Android Auto.
   Falls nicht: In den Android-Auto-Einstellungen unter *Launcher anpassen* einblenden.

## Bedienung im Auto

| Knopf | Funktion |
|---|---|
| Abos | Deine Abos-Übersicht |
| Suche | Suchen mit der Android-Auto-Tastatur (nur im Stand) |
| Zurück | Vollbild verlassen bzw. eine Seite zurück |
| Start | YouTube-Startseite |
| ⟲ / ⏯ / ⟳ | 10 s zurück, Play/Pause, 10 s vor |
| ✥ | Verschiebemodus (falls Wischen sonst nicht ankommt) |

Seite zu klein oder zu groß? In der Handy-App **Zoom − / Zoom +** tippen und die App
im Auto neu starten.

## Hinweise

* Google kann den Login in App-Browsern sperren („Dieser Browser ist nicht sicher“).
  EnyaqTube gibt sich deshalb als normales Chrome aus. Klappt es trotzdem nicht:
  Später nochmal versuchen oder per Passkey / Handy-Bestätigung anmelden.
* Werbeblocker verstoßen gegen die Nutzungsbedingungen von YouTube. Werbefrei und im
  Sinne von YouTube geht es mit YouTube Premium. Das Konto wird dann ganz normal
  erkannt.
* YouTube ändert die Website ab und zu. Dann kann der Werbeblocker zeitweise
  weniger zuverlässig sein.

## Selbst bauen

```
./gradlew assembleRelease
```
Ergebnis: `app/build/outputs/apk/release/app-release.apk`. Jeder Push baut die APK
automatisch per GitHub Actions. Signiert wird mit `keystore/enyaqtube.jks`
(Passwort `enyaqtube`), damit Updates über die alte Version installiert werden
können.
