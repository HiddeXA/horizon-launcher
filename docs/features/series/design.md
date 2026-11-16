Status: goedgekeurd & bevroren 2026-11-16

# Series in Horizon — Ontwerp

- **Claude Design-canvas:** https://claude.ai/artifact/Xy8ZtiZMNArEv9jHAHdqrS (versie `1794830571-8770`)
- **Schaal:** elk bord is 960×540, gelijk aan het tv-scherm in dp (1920×1080 bij xhdpi).
- Grijze vlakken op de borden zijn plekken voor beeld; titels en aantallen zijn verzonnen voorbeelden.

## Gekozen variant
**C: seriescherm zoals het filmscherm, met een apart afleveringenscherm.** Het seriescherm is gelijk aan het bestaande filmscherm en krijgt één extra knop, Afleveringen, die een tweede scherm opent met seizoenen links en afleveringen rechts.
Motivatie: sluit het strakst aan op wat er voor films al is, en geeft de meeste ruimte per aflevering. De prijs is één klik extra om zelf een aflevering te kiezen; de afspeelknop op het seriescherm gaat wel direct verder waar je was.
Vervallen: A (afleveringen als rij onderin) en B (lijst naast de info).

## Wijzigingen ten opzichte van de eerste spec (verwerkt in spec.md)
- Geen label "Serie" op tegels (Zoeken, Mijn lijst, Vind ik leuk, launcher).
- Launcher: geen aparte rij Aanbevolen series; films en series staan door elkaar in "Aanbevolen voor jou".
- Toegevoegd in het ontwerp: "Vanaf begin afspelen" in het menu bij lang OK op een aflevering, en "3 van 7" (gezien) bij een seizoen.

## Design tokens

### Horizon (bestaande huisstijl Deep Space / Twilight)
| Token | Waarde | Gebruik |
|---|---|---|
| background | `#0B132B` | achtergrond |
| surface | `#1C2541` | tegels, gekozen pil, gekozen rij |
| surfaceHigh | `#26315A` | knoppen zonder focus |
| accent | `#48CAE4` | primaire knop, focusrand, voortgang, label Nieuw |
| mint | `#5BC0BE` | score, gezien-vinkje |
| text | `#E0E1DD` | tekst; knop met focus (niet-primair) |
| dim | `#9AA3B8` | bijschriften, geziene afleveringen |
| onAccent | `#0B132B` | tekst op accent |
| dialoog-achtergrond | `#060B1A` | achter het menu bij lang OK |

- **Lettertype:** Poppins. Titel seriescherm 42/46 black; titel afleveringenscherm 28 black; billboard 36/40 black; rijtitel 17 bold; knop 15 semibold; aflevering 15 semibold (met focus bold); meta 14; bijschrift 12–13; label 11 bold.
- **Spacing:** linkerrand inhoud 48 (schermen met bovenmenu) of 56 (seriescherm); tussen tegels 12; tussen knoppen 12; tussen afleveringsrijen 8; tussen seizoenen 6.
- **Radius:** tegel 6; afleveringsrij en seizoen 8; pil en knop volledig rond (hoogte 44, pillen 36); kaart en dialoog 14; label 4.
- **Focus:** tegel en afleveringsrij: rand 2 px accent. Primaire knop: accent. Andere knop met focus: vlak in `text`, tekst in `background`.
- **Maten:** postertegel 104×156; afleveringsplaatje 128×72 (16:9); afleveringsrij 88 hoog; seizoenkolom 200 breed; kaart Volgende aflevering 380 breed, rechtsonder op 48/44 van de rand.

### Launcher (bestaand)
background `#0B0D10`, surface `#1C1F24`, surfaceFocused `#2A2E35`, text `#F2F2F2`, textDim `#A7ACB4`, accent `#F2B84B`, IMDb `#F5C518`, RT `#FA6A3C`, in aanbod `#46D369`. Systeemlettertype.

## Schermen

| Bord | Scherm | Doel | Belangrijkste onderdelen | Staten |
|---|---|---|---|---|
| A/B | Bovenmenu + tab Series | Series ontdekken | Menu Zoeken · Films · Series · Filter · Mijn lijst; billboard (titel, score, jaren, seizoenen, genres, omschrijving, "Verder met S2 · A4"); rijen met postertegels | Tegel in Verder kijken: label S·A onderin + voortgangsbalk; band "Nieuwe aflevering" bovenin; zonder TMDB: categorieën van de aanbieder als rijen; geen series; ophalen mislukt (bord J) |
| C | Seriescherm | Info en direct verder kijken | Groot beeld, titel, score/jaren/seizoenen/genres, "Je was bij…", omschrijving, cast; knoppen: afspelen (focus), Afleveringen, + Mijn lijst, Trailer | Afspeelknop: "Afspelen S1 · A1" / "Verder kijken S2 · A4" / "Volgende aflevering S2 · A5"; ✓ Mijn lijst; zonder afleveringen: geen afspeelknop en geen Afleveringen (bord J) |
| C | Afleveringenscherm | Seizoen en aflevering kiezen | Titel serie; links seizoenen (met ✓ of "3 van 7", Specials achteraan); rechts rijen: plaatje, "4. Titel", duur, omschrijving | Rij: gezien (gedimd, ✓ op plaatje), bezig (voortgangsbalk, "nog 35 m"), focus (vlak + rand), Nieuw (label); zonder plaatje: leeg vlak; één seizoen: geen seizoenkolom; laden; ophalen mislukt (bord J) |
| D | Menu bij lang OK | Gezien-status aanpassen | Aflevering: Markeer als (niet) gezien, Vanaf begin afspelen, Sluiten. Seizoen: Heel seizoen gezien / niet gezien, Sluiten | Tekst wisselt met de huidige status |
| E | Speler | Aflevering kijken | Bovenin serienaam + "S2 · A4 · titel"; bediening met extra knop Volgende aflevering naast Ondertitels | Laatste aflevering: knop Volgende aflevering ontbreekt |
| F | Kaart Volgende aflevering | Doorkijken | Rechtsonder: plaatje, "Volgende aflevering over 8 s", S·A + titel, duur, balk, Nu afspelen (focus), Annuleren | Pauze: aftelling staat stil; duur onbekend: kaart na afloop |
| G | Zoeken | Films en series vinden | Toetsenbord links, raster rechts; "Zoek een film of serie…"; aantal films en series | Geen label op tegels; band Nieuwe aflevering waar van toepassing |
| H | Filter | Filteren op soort | Bovenaan Soort: Films / Series; genres van het gekozen type; periode, minimale score, sorteren; resultaten rechts | Stip op de pil Filter als er een filter actief is |
| I | Mijn lijst | Bewaarde titels | Raster met films en series door elkaar | Band Nieuwe aflevering; leeg (bestaande melding) |
| J | Laad-, lege en foutstaten | Duidelijk bij wachten of fouten | Laadscherm "Series laden…"; geen series; series ophalen mislukt + Opnieuw proberen; afleveringen laden; afleveringen mislukt + Opnieuw proberen; nog geen afleveringen | — |
| K | Launcher: Aanbevolen voor jou | Films en series door elkaar | Top shelf met reden, titel, jaren/seizoenen, scores, "In je aanbod · verder met S2 · A4"; één gemengde rij; groene stip = in je aanbod | Zonder TMDB: geen aanbevelingen (zoals nu) |
| L | Launcher: seriescherm | Doorverwijzen naar Horizon | Titel, meta, scores, omschrijving, cast; knoppen: afspelen (focus), Afleveringen, Trailer, Vind ik leuk; "Beschikbaar in je aanbod · je was bij…" | Niet in aanbod: Zoek in Horizon in plaats van afspelen en Afleveringen |
| M | Launcher: Vind ik leuk | Likes beheren | Raster met films en series door elkaar; teller "6 films en 3 series" | Leeg (bestaande melding) |
