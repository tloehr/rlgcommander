# Work in Progress
## Game Testing
* Conquest - 250420 - works
* Center Flags - 250420 - works
* FarCry - 250420 - works
* FetchEm - 250420 - works
* Hardpoint - 250420 - works
* Signal - 250420 - works
* Street - 250420 - works
* Timed Only - 250420 - works

Stronghold 1 und 2 noch fehler in der Anzeige im ACTIVE und auf den Spawn Agenten
Signal noch Nachrichen Zeilen 1-2 hinzufügen - ähnlich wie bei Timed Only

## Transient Notes

* pretty json einbauen (Server.html)
* (zurückgestellt) seitenwechsel im geladenen Spiel geht das ?
* geladenes Spiel speichern
* bei agenten bei incoming events automatisch die tabelle neu laden
  * erledigt
* bei active games time und score mittels sich wiederholender abfragen aktualisieren
  * https://stackoverflow.com/a/20371831/1171329
* geändert, dass bei Agenten die noch unbekannt sind und bei denen man eine Taste gedrückt hat, der commander sofort eine State Abfrage stellt. Dann braucht man nicht immer die ganze Minute zu warten.
* das admin password kann nun über einen Eintrag in application.yml gesetzt werden.
  * rlgs:admin:set_password: password
* Hardpoint
  * Wählen können ob blau oder rot zuerst drann kommt beim Drücken
  * Buzzer bei neu aktivierten Flaggen
  * Punkte Berechnung korrigiert. Jetzt werden immer 500ms als Zeit gerechnet. Damit kommt es zu verlässlicheren Endergebnissen.
* FetchEm
  * Neuer Modus - Toby
* Habe das LOCALE Select auf der Webseite korrigiert. Wird jetzt automatisch auf den Standard gesetzt für den jeweiligen User.
* ScoreBroadcasting
  * Habe eine eigene Zwischen Klasse nur für das Score Senden und evtl. auch berechnen erstellt und einezogen. Bestehende Klassen angepasst.
    * conquest
    * center_flags
    * farcry

## todo
* FarCry Anzeige Fixen. Restzeit und Spielzeit
* Stronghold2 das macht keinen Sinn mit der Zeit für ROT. Muss etwas anders aufgebaut sein. 

## Release Notes
### 1.11
#### Vor update - Umgebung anpassen

Hat sich alles durch DOCKER erledigt.

* auf MQTT Server die Websockets freischalten
  * 250126 - erledigt auf pbfcmd1
* MYSQL installieren
  * User anlegen
  * Zugriff freigeben - my.cnf
  * Datenbank installieren und importieren
* application.yml
  * Users entfernen
* application.yml.default beim package build entfernen
