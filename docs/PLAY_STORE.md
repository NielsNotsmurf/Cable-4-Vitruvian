# Google Play: privacy & compliance

Alles wat de app en de web-app nodig hebben voor Google Play, plus wat alleen jij in Play Console kunt doen.

## Wat in de code geregeld is

| Onderwerp | Hoe |
|---|---|
| Privacybeleid | `docs/privacy/index.html`, live op https://nielsnotsmurf.github.io/Cable-4-Vitruvian/privacy/. Gelinkt in Instellingen > Help (app en web, gedeelde clean-ui). |
| Oefenvideo's | De officiële Vitruvian-video's (Mux) worden nergens meer getoond (app, web, 1RM-test). In plaats daarvan een eigen demo: het lichaamsfiguur met het getrainde deel dat pulseert, plus de spieren. `ExerciseGuide.videoUrl` blijft bestaan voor eigen clips later. Mux is uit de web-CSP gehaald. |
| Betalingen | Play staat externe betaal-/donatielinks niet toe. De Play-build (`-Pplay=true`) verbergt Prijs, GoFundMe, GitHub Sponsors en de maandelijkse donatievraag. GitHub-APK en web houden ze. |
| Klassieke layout | Speelt nog de oude video's af, dus verborgen in de Play-build. |
| Rechten | Locatie alleen tot Android 11 (`maxSdkVersion=30`); Android 12+ scant met `neverForLocation`. Geen notificatie-, camera-, opslag- of contactenrechten. |
| Target SDK | 36 (Play eist minimaal 35). |
| Analytics | App: geen. Web: anonieme GoatCounter-tellingen, zonder cookies, uit bij Do Not Track/GPC en uit te zetten in Instellingen; staat in het privacybeleid. |
| Data wissen | Geen accounts of servers, dus geen account-verwijder-URL nodig. Wissen kan in de app (Backup > alles wissen, profiel verwijderen) of door te verwijderen; uitgelegd in het beleid. |
| Play-build | Actions > "Google Play bundle" > Run workflow. Geeft een ondertekende `.aab` als artifact (versionCode 10000+run). |

## Play Console: wat jij moet doen

1. **App aanmaken**: naam `Cable`, gratis, type App, categorie *Health & Fitness*.
2. **Play App Signing** aanzetten en de huidige sleutel (`phoenix-release.jks`) als *upload key* gebruiken.
   Let op: Play ondertekent met een eigen sleutel, dus de Play-versie en de GitHub-APK kunnen niet over elkaar heen worden geïnstalleerd (zelfde app-ID). Bestaande gebruikers: eerst backup maken, dan wisselen.
3. **Privacy policy URL**: https://nielsnotsmurf.github.io/Cable-4-Vitruvian/privacy/
4. **Data safety** (zie hieronder).
5. **Health apps declaration**: *Fitness* (krachttraining) en *hartslag via Bluetooth-sensor*; geen Health Connect, geen medisch hulpmiddel.
6. **Foreground service declaration**: type `connectedDevice`. Uitleg: "Houdt de Bluetooth-verbinding met de Vitruvian-trainer actief tijdens een workout terwijl het scherm uit is." Voeg een korte schermopname toe (workout starten, scherm uit, trainer blijft tellen).
7. **Ads**: Nee, bevat geen advertenties.
8. **Content rating** (IARC-vragenlijst): alles "Nee" → PEGI 3 / Everyone.
9. **Target audience**: 18+ (of 16+). Niet voor kinderen.
10. **News app**: Nee. **Government app**: Nee. **Financial features**: geen.
11. **Store listing**: teksten uit `fastlane/metadata/android/en-US/` (titel, korte en lange beschrijving, screenshots). Disclaimer "unofficial, not affiliated with Vitruvian" staat er al in; gebruik geen Vitruvian-logo of -foto's.
12. **Contactgegevens**: e-mailadres verplicht (wordt publiek getoond); overweeg een apart adres.
13. **Testen**: nieuwe persoonlijke accounts moeten eerst een *closed test* draaien met minstens 12 testers, 14 dagen aaneen, voordat Production aangevraagd kan worden.
14. **Upload**: `.aab` uit de workflow naar de closed-test-track.

## Data safety-antwoorden

- *Verzamelt of deelt je app gebruikersgegevens?* **Nee.** Alles blijft op het apparaat (on-device verwerking telt niet als verzamelen).
- *Is alle data versleuteld tijdens verzending?* Niet van toepassing (er wordt niets verstuurd). Indien gevraagd: Ja (HTTPS voor de oefenbibliotheek-update).
- *Kunnen gebruikers vragen om verwijdering?* Ja: data staat alleen op het apparaat en wordt gewist via de app of door te verwijderen.
- Geen advertentie-ID, geen derde-partij-SDK's die data verzamelen.

## Openstaand risico

- De oefennamen en de lange "How to do it"-teksten komen uit de openbare exercise library (gebaseerd op Vitruvian). Namen zijn prima; als je helemaal zeker wil zijn, vervang de lange teksten later door eigen teksten (de korte samenvattingen zijn al door Cable zelf gegenereerd).
