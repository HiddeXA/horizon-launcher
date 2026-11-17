Status: goedgekeurd & bevroren 2026-11-17

# Horizon filterpagina, redesign: ontwerp

- **Claude Design-canvas:** https://claude.ai/artifact/4VBPUPrhybY1YWq1uWfP1i (versie `1794952571-600b`)
- **Spec:** `spec.md` (goedgekeurd 17 nov 2026)
- **Schaal:** elk bord is 960×540, gelijk aan het tv-scherm in dp (1920×1080 bij xhdpi).
- Grijze vlakken op de borden zijn plekken voor posters; titels, jaartallen en aantallen zijn voorbeelden.

## Gekozen variant
**A: filterbalk met keuzepanelen.** Onder het bovenmenu staat één vaste balk:
- links de schakelaar Alles / Films / Series;
- daarnaast vier keuzeknoppen (Genres, Periode, Score, Sorteren) die hun waarde tonen;
- rechts Wissen.

OK op een keuzeknop opent een paneel over de resultaten. De resultaten vullen de rest van het scherm, met 7 posters per rij.

**Motivatie:**
- Het rustigst en het strakst: alle actieve filters staan in één regel en blijven altijd zichtbaar.
- Het raster krijgt de volle breedte. Je bent met 1× ▼ bij de eerste titel.
- Het sluit aan op het bestaande bovenmenu (ook pillen).

**Vervallen:**
- B (keuzekolom zoals tv-instellingen, die inklapt): meer drukken en een smaller raster zolang de kolom open is.
- C (preview bovenin + apart filterscherm): mooi, maar maar één rij resultaten en een extra scherm om een filter te wijzigen.

Beide staan onderaan het canvas.

## Design tokens
Bestaande huisstijl Deep Space / Twilight (zie ook `docs/features/series/design.md`).

| Token | Waarde | Gebruik |
|---|---|---|
| background | `#0B132B` | achtergrond |
| surface | `#1C2541` | keuzeknoppen, schakelaarbak, panelen, meldingsbalk, posters |
| surfaceAlt | `#222C4D` | optie in het genrepaneel |
| surfaceHigh | `#26315A` | keuzeknop waarvan het paneel open is, knoppen zonder focus, scheidingslijn |
| accent | `#48CAE4` | gekozen segment, gekozen waarde, vinkjes, focusrand tegel, badge |
| mint | `#5BC0BE` | extra regel onder de tegel met focus (bijv. "Serie · 2 seizoenen") |
| text | `#E0E1DD` | tekst; vulling van het element met focus |
| dim | `#9AA3B8` | labels (Genres, Periode …), bijschriften, aantallen |
| onAccent | `#0B132B` | tekst op accent of op de focusvulling |
| tagOnFocus | `#3A4566` | "alleen films/series" op een optie met focus |
| checkboxRand | `#5A6588` | lege checkbox in het genrepaneel |

- **Lettertype:** Poppins.
  - Paneeltitel: 18 bold.
  - Lege staat en fouttitel: 22 bold.
  - Keuzeknop en segment: 14, label 500, waarde 600.
  - Paneeloptie: 14 bij Genres, 15 bij Periode/Score/Sorteren.
  - Aantallen: 14.
  - Tegeltitel: 12, jaar 11, tag 11.
  - Hints: 12.
- **Spacing:**
  - Linker- en rechterrand 48.
  - Filterbalk op y=72 (bovenmenu op y=18).
  - Tussen keuzeknoppen 10.
  - Aantal-regel op y=128 en raster vanaf y=160. Met meldingsbalk: balk op y=124 (hoogte 56), aantal op y=194, raster vanaf y=226.
  - Rastergaten 14 horizontaal en 18 verticaal.
  - Genrepaneel: binnenruimte 20/22, opties 8 uit elkaar.
- **Radius:**
  - Keuzeknop, segment en knoppen: volledig rond (hoogte 40 in de balk, 32 voor een segment, 44 voor actieknoppen).
  - Paneel 14, meldingsbalk 12, paneeloptie 10, checkbox 5, poster 6.
- **Focus en gekozen:**
  - **Focus:** de vulling wordt `text` met donkere tekst (keuzeknop, segment, paneeloptie, Wissen, actieknoppen). Op een tegel is het een rand van 2 px `accent`, met 2 px afstand.
  - **Gekozen:** een segment krijgt een vulling in `accent`. In een paneel krijgt een optie een vinkje plus `accent`-tekst (Periode, Score, Sorteren) of een gevulde checkbox (Genres). In de balk staat de gekozen waarde in `accent`.
  - Focus op een gekozen segment: de vulling `text`, met daaromheen een ring van 2 px `accent`.
- **Paneel:** achtergrond `surface`, rand 1 px `surfaceHigh`, schaduw `0 24 60 rgba(6,11,26,.7)`. De rest van het scherm (bovenmenu, schakelaar, andere keuzeknoppen, raster) dimt naar 45% (raster 18%).
- **Maten:**
  - Postertegel ongeveer 111×156 (7 kolommen over 864).
  - Genrepaneel 864 breed: 4 kolommen bij Alles en Films, 5 bij Series.
  - Keuzepaneel Periode/Score/Sorteren 240 breed, onder de eigen knop.

## Schermen

| Bord | Scherm | Doel | Belangrijkste onderdelen | Staten |
|---|---|---|---|---|
| A0 | Beginstand | Openen via de pil Filter | Balk met Alles gekozen (en met focus), alle knoppen op hun standaard, geen Wissen; "120 titels · 81 films, 39 series · populairste eerst"; raster | Pil Filter zonder stip |
| A1 | Actieve filters | Bladeren met filters | Waardes in de balk (Misdaad · Jaren 90 · 7+ · Beste score), Wissen; "38 titels · 29 films, 9 series"; films en series gemengd | Tegel met focus: rand, vette titel, mintregel "Serie · 2 seizoenen" of "Film · 2 u 34 min"; stip op pil Filter |
| A2 | Paneel Genres (Alles) | Genres kiezen | Gezamenlijke lijst van 20 genres in 4 kolommen, met checkbox; "alleen films" of "alleen series" onder genres die maar bij één soort horen; samengevoegd: Actie & Avontuur, Sci-Fi & Fantasy, Oorlog; uitleg "titels hebben alle gekozen genres"; "1 gekozen"; hints OK/Terug | Optie met focus, gekozen optie |
| A3 | Paneel Periode | Eén keuze maken | Lijst onder de knop, vinkje bij de gekozen keuze; "OK kiest en sluit · Terug sluit". **Score** (Alles, 6+, 7+, 8+) en **Sorteren** (Populair, Nieuw toegevoegd, Beste score, A–Z) hebben precies dezelfde vorm | Focus op een andere optie dan de gekozen |
| A4 | Series gekozen | Beperken tot series | Segment Series gekozen; genrepaneel "Genres voor series" met de 15 seriegenres in 5 kolommen; genres van de vorige soort gaan mee (Misdaad, Drama) | Bij Films: 18 filmgenres, titel "Genres voor films" |
| S1 | Laden: zoeken | Wachten na een wijziging | Balk blijft staan; "Titels zoeken…" met draaiende ring; grijze skelettegels | — |
| S2 | Laden: series volgen | Alles, series nog niet binnen | "29 films" + label "Series worden nog geladen en komen er zo bij"; films staan er al | Series schuiven erbij zonder dat de focus verspringt |
| S3 | Geen titels | Niets past | Lege-planeet-icoon, "Geen titels gevonden", uitleg in gewone taal (en dat Horror alleen bij films hoort); knoppen om het strengste filter los te laten (bijv. "Score naar Alles", "Periode naar Alles") en "Alles wissen" | De eerste knop heeft focus |
| S4 | TMDB-token ontbreekt | Genres zonder token | Meldingsbalk "Filteren op genre werkt pas met een TMDB-token. Periode en sortering zijn wel toegepast." + knop "Naar Instellingen"; aantal "… · zonder genre"; resultaten zonder genrefilter | — |
| S5 | TMDB niet bereikbaar | Genres laden mislukt | Icoon zonder verbinding, "Genres kunnen nu niet geladen worden", uitleg; knoppen "Opnieuw proberen" (focus) en "Zonder genre tonen" | Nooit "Geen titels gevonden" |
| S6 | Eén soort mislukt | Alles, series mislukt | Meldingsbalk "Series met dit genre konden niet geladen worden. Je ziet nu alleen films." + "Opnieuw proberen"; "29 films · series ontbreken" | Andersom bij films: dezelfde tekst met "films" |
| — | Onderdeel filterbalk | Eén bron voor bovenmenu + balk | Props: soort, genres, periode, score, sorteren, focus, open | Wissen alleen bij een actief filter, een andere sortering of een andere soort dan Alles |

## Gedrag dat bij het ontwerp hoort
- **De balk blijft vast staan** bovenaan. Alleen het raster scrollt, zodat het overzicht altijd zichtbaar is.
- **Bediening:**
  - ▼ vanuit het bovenmenu gaat naar het gekozen segment van Alles / Films / Series. ◄ ► gaat door de balk.
  - OK op een keuzeknop opent het paneel. Bij Genres zet OK een genre aan of uit, bij de andere panelen kiest OK en sluit het paneel. Terug sluit het paneel.
  - ▼ vanuit de balk gaat naar de eerste titel. Terug in het raster gaat naar de balk, en nog eens Terug gaat naar de tab waar je vandaan kwam.
- **Lange waarde in een keuzeknop:** afkorten tot de eerste twee genres plus het aantal, bijv. "Actie, Komedie +5". De balk loopt nooit buiten het scherm.
- **Veel resultaten:** bij genres en 60 of meer per soort komt achter het aantal "· de bekendste" (geen bord, zelfde regel als het aantal).
- **Poster laadt niet:** het vlak in `surface` met de titel linksonder (zoals op de borden).

## Controle tegen de spec
Alle 36 criteria zijn gedekt door de borden of door de regels hierboven. Eén punt van aandacht voor het bouwen: "Actie & Avontuur" en "Sci-Fi & Fantasy" lopen in het genrepaneel bij Series (5 kolommen) over twee regels. De optiehoogte van 48 is daarop berekend.

Volgende stap: `sw-build`.
