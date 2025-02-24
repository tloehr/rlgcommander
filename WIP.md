# Work in Progress
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
  

## current work
i18n - testen - siehe layout #30

## todo
* FarCry Anzeige Fixen. Restzeit und Spielzeit
* Hardpoint Rundungsfehler bei Flaggenpunkten
  * Wählen können ob blau oder rot zuerst drann kommt beim Drücken


## Release Notes
### 1.11
#### Vor update - Umgebung anpassen
* auf MQTT Server die Websockets freischalten
  * 250126 - erledigt auf pbfcmd1
* MYSQL installieren
  * User anlegen
  * Zugriff freigeben - my.cnf
  * Datenbank installieren und importieren
* application.yml
  * Users entfernen
* application.yml.default beim package build entfernen
