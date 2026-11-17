# Horizon filterpagina, redesign: plan

Bron: `spec.md` (goedgekeurd 17 nov 2026), `design.md` (bevroren, canvas versie `1794952571-600b`).
Branch: `feature/filter-redesign` (op `feature/series`).

## Stack en opbouw
Zelfde stack als de rest van Horizon: Kotlin 2.1, Compose for TV (tv-material 1.0), Coil, OkHttp.

| Onderdeel | Wat | Android-vrij (unit test) |
|---|---|---|
| `FilterModel.kt` (nieuw) | `FilterKind` (Alles/Films/Series), `GenreOption` + `FilterGenres` (gezamenlijke lijst van 20, films 18, series 15), `BiosFilter` met `kind` en gekozen genre-sleutels, wisselen van soort. `Genre`, `FilterPeriod` en `FilterSort` verhuizen hierheen (zonder inhoudelijke wijziging) | ja |
| `FilterLogic.kt` (nieuw) | `FilterQuery` (welke soorten zoeken, welke TMDB-genrecombinaties), `FilterMerge` (films en series samenvoegen en sorteren), `FilterTexts` (waarde in de keuzeknop, aantal-regel, uitleg en voorstellen bij de lege staat) | ja |
| `BiosGenres.kt`, `SeriesGenres.kt` | `filter` krijgt genregroepen (OF binnen een samengevoegd genre, EN ertussen) en meldt een fout als TMDB niet antwoordt, in plaats van stil een lege lijst te geven | nee |
| `FilterSearch.kt` (nieuw) | Voert het zoeken uit voor de gekozen soort(en), parallel, met een resultaat per soort (titels, fout, nog aan het laden) | nee |
| `BiosFilterScreen.kt` (herschreven) | Filterbalk, raster van 7 met onderschrift, meldingen en staten | nee |
| `FilterPanels.kt` (nieuw) | Genrepaneel (4 of 5 kolommen) en keuzepaneel (Periode, Score, Sorteren), dimmen, Terug | nee |
| `BiosActivity.kt` | Filter opent op Alles; scherm weet of het film- of seriescherm open is (focus terugzetten) | nee |
| `BiosSearch.kt` | `ListingTile` krijgt een `onFocused` (standaard leeg) | nee |

## Wat niet mag veranderen
- Het bovenmenu (`TopNav`), inclusief de stip op de pil Filter (`!filter.isEmpty`).
- Hoe er per soort gezocht wordt: dezelfde TMDB-aanroepen, minimale aantallen stemmen, sortering en maximaal ~60 met genres / 120 zonder. Een filmfilter met gewone genres levert precies dezelfde lijst als nu.
- Zoeken, Mijn lijst, de tegels zelf (`VodTile`, `SeriesTile`), het film- en seriescherm.
- Huisstijl: alleen bestaande `HorizonColors`, plus de paar vaste waarden uit `design.md` (surfaceAlt `#222C4D`, checkboxRand `#5A6588`, tagOnFocus `#3A4566`).

## Testen
- **Unit tests** (JUnit, `app/src/test`): in de cloud met kotlinc 2.1.0 en een kleine JUnit-stand-in voor de Android-vrije bestanden. Op de pc draaien ze mee met Gradle (`testReleaseUnitTest`).
- **Op de tv:** bouwen en installeren met `7-bouw-installeer-meet.cmd`, toetsen sturen via `logs\commands.txt`, met screenshots van elk scherm, naast het ontwerp gelegd. Logregels met tag `HorizonFilter` voor wat niet op een screenshot te zien is (aantallen, fouten).

## Taken

### Fase 1: logica (Android-vrij, TDD)
| # | Taak | Bestanden | Criteria (spec) | Test |
|---|---|---|---|---|
| 1.1 | Genrelijsten: gezamenlijk 20 (3 samengevoegd), films 18, series 15; "alleen films/series" | `FilterModel.kt` | Soort 5, 6, 7; Keuzes 2 | `FilterGenresTest` |
| 1.2 | `BiosFilter` met `kind` (standaard Alles); wisselen: periode/score/sortering blijven, genres via gedeelde nummers, samengevoegde genres heen en terug | `FilterModel.kt` | Soort 1, 2, 8, 9 | `FilterSwitchTest` (herschreven) |
| 1.3 | `FilterQuery`: welke soorten zoeken; genrecombinaties voor TMDB; genre dat alleen bij één soort hoort | `FilterLogic.kt` | Soort 3, 7; randgeval wisselen | `FilterQueryTest` |
| 1.4 | `FilterMerge`: samenvoegen per sortering (Populair, Nieuw toegevoegd, Beste score, A–Z); film en serie met dezelfde titel blijven allebei | `FilterLogic.kt` | Soort 3; randgeval zelfde titel | `FilterMergeTest` |
| 1.5 | `FilterTexts`: keuzeknopwaarde ("Actie, Komedie +5"), aantal-regel ("38 titels · 29 films, 9 series", "· de bekendste"), lege staat (uitleg + voorstellen), Wissen zichtbaar | `FilterLogic.kt` | Overzicht 1–3; Staten 3; randgeval alle genres | `FilterTextsTest` |

### Fase 2: zoeken
| # | Taak | Bestanden | Criteria | Test |
|---|---|---|---|---|
| 2.1 | TMDB-filter met genregroepen en foutmelding bij de eerste pagina | `BiosGenres.kt`, `SeriesGenres.kt` | Staten 5, 6 | (netwerk) op de tv; combinaties via 1.3 |
| 2.2 | `FilterSearch`: films en series parallel, uitkomst per soort, series nog aan het laden | `FilterSearch.kt` | Soort 3; Staten 2, 4, 6 | op de tv (logregels) |

### Fase 3: scherm (design A)
| # | Taak | Bestanden | Criteria | Bord |
|---|---|---|---|---|
| 3.1 | Filterbalk: segment Alles/Films/Series, 4 keuzeknoppen met waarde, Wissen; focus/gekozen volgens tokens | `BiosFilterScreen.kt` | Uitstraling 1; Keuzes 1, 5, 6; Overzicht 1, 3; Bediening 1 | A0, A1 |
| 3.2 | Raster van 7 met onderschrift; bij focus "Film · 2u 34m" / "Serie · 2 seizoenen" (jaartal tot de info binnen is) | `BiosFilterScreen.kt`, `BiosSearch.kt` | Uitstraling 2; Soort 9; Bediening 2 | A1 |
| 3.3 | Genrepaneel (Alles 4 kolommen met tags, Films 4, Series 5), checkbox, "n gekozen", hints | `FilterPanels.kt` | Keuzes 3; Soort 5–7 | A2, A4 |
| 3.4 | Keuzepaneel Periode/Score/Sorteren onder de eigen knop; OK kiest en sluit | `FilterPanels.kt` | Keuzes 4 | A3 |
| 3.5 | Staten: skeletten, "series volgen", lege staat met knoppen, token-melding, TMDB-fout, fout bij één soort | `BiosFilterScreen.kt` | Staten 1–6 | S1–S6 |
| 3.6 | Bediening: ▼ naar het eerste resultaat, Terug in het raster → balk → tab, Terug sluit paneel, 350 ms wachten, focus terug na film-/seriescherm | `BiosFilterScreen.kt`, `BiosActivity.kt` | Bediening 1–6 | — |
| 3.7 | Filter opent op Alles; Alles wissen zet ook de soort terug | `BiosActivity.kt` | Soort 2; Overzicht 3 | A0 |

### Fase 4: controle
| # | Taak |
|---|---|
| 4.1 | Alle unit tests (cloud + pc), bouwen, installeren |
| 4.2 | Op de tv: A0, A1, A2, A3, A4, S1, S2, S3 (en S4–S6 voor zover op te wekken) met screenshots naast het ontwerp; elk criterium afvinken met bewijs |
| 4.3 | Diff nalopen op scope (non-goals), `overzicht.md` bijwerken |

## Dekking
- **User stories:** 1 → 3.1–3.5; 2 → 1.1–1.4, 3.1, 3.7; 3 → 1.1, 3.3; 4 → 3.1, 3.4; 5 → 1.5, 3.1; 6 → 3.6; 7 → 1.5, 3.1, 3.5; 8 → 2.2, 3.5; 9 → 3.6.
- **Schermen uit de spec:** 1 Beginstand → 3.1/3.7; 2 Films of Series → 3.3; 3 Actieve filters → 3.1; 4 Keuzes → 3.3/3.4; 5 Focus → 3.2; 6 Laden → 3.5; 7 Leeg → 3.5; 8 Token → 3.5; 9 TMDB-fout → 2.1/3.5.

## Verificatie (17 nov 2026)
- **Unit tests:** 54 geslaagd in de cloud (kotlinc 2.1.0); op de pc slaagt `testReleaseUnitTest` (via `9-test-bouw-installeer.cmd`).
- **Op de tv** (Chromecast, 192.168.50.104): gebouwd, geïnstalleerd en met `logs\commands-filter-test.txt` doorlopen, met screenshots `logs\f0`–`f13`. Geen crashes (`AndroidRuntime:E` leeg).

| Spec | Bewijs |
|---|---|
| Uitstraling 1–2 | f1, f4: huisstijlkleuren, Poppins, bovenmenu ongewijzigd; raster over de volle breedte (7 per rij) |
| Soort 1–2 | f1: Alles gekozen en met focus na openen; f13: Terug brengt je naar de tab (Series) |
| Soort 3–4 | f1/f4: films en series door elkaar, "75 titels · 66 films, 9 series"; `FilterMergeTest` |
| Soort 5–7 | f2: gezamenlijke lijst van 20 met "alleen films/series"; f10: 15 seriegenres in 5 kolommen; `FilterGenresTest`, `FilterQueryTest` |
| Soort 8 | f9/f10: Misdaad gaat mee van Alles naar Series; `FilterSwitchTest` |
| Soort 9 | f6: "Film · 1995" in mint onder de titel met focus (de aanbieder geeft voor deze film geen speelduur; daarom het jaartal) |
| Soort 10 | f1 "titels", f9 "9 series"; `FilterTextsTest` |
| Keuzes 1–4 | f2 (Genres, meerdere), f3 (Periode, één keuze, sluit na OK) |
| Keuzes 5–6 | f2/f3: gekozen = vinkje/checkbox + blauw; focus = lichte vulling |
| Overzicht 1–3 | f4: waardes in de balk, aantal met "de bekendste", Alles wissen rechts; f1 zonder Wissen |
| Bediening 1 | f1: focus op Alles na ▼ uit het bovenmenu |
| Bediening 2 | f5: 1× ▼ vanuit de balk = eerste titel |
| Bediening 3 | f8: Terug in het raster → Alles; f13: nog eens Terug → tab |
| Bediening 4 | f2→f3: Terug sluit het paneel, focus terug op de keuzeknop |
| Bediening 5 | f0: 0,25 s na de keuze "Titels zoeken…"; zoeken start na 350 ms |
| Bediening 6 | f7: na het filmscherm weer focus op dezelfde titel (Se7en) |
| Staten 1 | f0: skeletten + "Titels zoeken…", balk blijft staan |
| Staten 3 | f11: uitleg in gewone taal + knoppen; f12: "Genres wissen" werkt, focus op de balk |
| Staten 2, 4, 5, 6 | Niet op te wekken in deze test (series al geladen, token aanwezig, TMDB bereikbaar). Gedrag in code (`FilterSearch`, `FilterOutcome.allFailed/partlyFailed`) en teksten in `FilterTextsTest` |

### Afwijkingen van het ontwerp (gemeld)
1. **Alles wissen staat rechts op de regel met het aantal, niet in de filterbalk.** Op ware grootte (864 dp) passen segment, vier keuzeknoppen met waarde én Wissen niet op één regel (~1.000 dp nodig).
2. **Keuzeknoppen iets compacter:** labels en waardes 13 in plaats van 14, kleinere binnenruimte. De waarde van Genres wordt afgekort ("Actie, Komedie +5", of met "…") als het krap wordt.
3. **Genrepaneel bij Series (5 kolommen):** tekst 13 en iets minder binnenruimte, zodat "Documentaire" heel blijft.
4. **Populair bij Alles:** films en series om en om. De populariteitscijfers van TMDB zijn tussen films en series niet vergelijkbaar: de eerste rij bestond anders alleen uit series.

### Bekend, buiten deze ronde
- Zonder genres filtert Horizon op het aanbod zelf (zoals vóór deze ronde). Bij Jaren 90 + 7+ geeft dat ook concertregistraties en dubbele titels van de aanbieder (f12).
