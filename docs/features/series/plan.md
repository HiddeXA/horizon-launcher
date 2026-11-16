# Series in Horizon — Plan

Bij: `spec.md` (goedgekeurd 2026-11-16) en `design.md` (bevroren 2026-11-16).

## Tech stack en architectuur
Ongewijzigd: Kotlin 2.1, Compose for TV, Media3 ExoPlayer, OkHttp, Coil, één app-pakket `nl.thuis.launcher`. Geen nieuwe bibliotheken.

Nieuwe onderdelen in `bios/` (de map van Horizon):

| Onderdeel | Wat | Android-vrij (unit test) |
|---|---|---|
| `SeriesModels.kt` | `SeriesItem`, `SeriesIndex` (zoeken, vinden op TMDB-nummer of titel), `Episode`, `Season`, `SeriesDetail` | ja |
| `SeriesLogic.kt` | `EpisodeOrder` (volgende aflevering, Specials), `NextUp` (waar je was + knoptekst), `NewEpisodes`, `Countdown` (wanneer de kaart verschijnt) | ja |
| `SeriesRepo.kt` | Xtream `get_series` (als stroom) en `get_series_info`, opslag `series-catalog-v1.json`, status los van films, afspeeladres van een aflevering | nee |
| `SeriesStore.kt` | positie per aflevering, gezien-vinkjes, laatst gekeken per serie, bekende afleveringen (voor Nieuwe aflevering) | deels (rekenwerk in `SeriesLogic`) |
| `SeriesGenres.kt` | rijen voor de tab Series via TMDB (`discover/tv`), dagopslag `series-genres.json`, tv-genres | nee |
| `SeriesHome.kt`, `SeriesDetail.kt`, `EpisodesScreen.kt`, `EpisodeMenu.kt` | schermen B, C, D uit het ontwerp | nee |
| `Listing.kt` | film óf serie als één soort tegel, voor Zoeken, Filter en Mijn lijst | ja |

Aangepast:
- `BiosActivity`/`BiosUi`: bovenmenu met Films en Series, laatst gebruikte tab, terug-gedrag, openen op een serie vanuit de launcher.
- `PlayerActivity`: afleveringen (titel, knop Volgende aflevering, kaart met aftelling, één stream tegelijk, gezien markeren).
- `BiosSearch`, `BiosFilterScreen`, `Bios.MyList`, `Taste`: films en series.
- Launcher: `MovieApi`, `Movie`, `Recommender`/`RecScoring`, `LikeStore`, `HomeScreen`, `MovieDetailScreen`, `LikedScreen`, `MainActivity`.

Gegevens blijven op het kastje. Wachtwoorden en afspeeladressen worden nooit gelogd.

## Wat niet mag veranderen
- Gedrag van films: rijen en volgorde op de tab Films, filmscherm, speler bij films, filmcache (`bios-catalog-v2.json`), posities en Mijn lijst van films (bestaande opslag blijft leesbaar).
- Horizon TV, Roze Hoek, de startanimatie, debloat-scripts.
- Starttijd: hooguit 2 s erbij (10.2). Films zijn bruikbaar ook als series laden mislukt.
- Eén stream tegelijk.
- Bestaande unit tests blijven groen.

## Testen
- **Unit tests** (JUnit, `app/src/test`): draaien in de cloud met kotlinc voor de Android-vrije bestanden, en met Gradle op de pc.
- **Op de tv**: build via `7-bouw-installeer-meet.cmd`, stappen in `logs\commands.txt` met `require` vóór toetsen, screenshots naast het ontwerp.

## Takenlijst

### Fase 0 — Meten (open vraag 1)
| # | Taak | Bestanden | AC | Test |
|---|---|---|---|---|
| 0.1 | Meetfunctie voor het serie-aanbod (aantal, TMDB-nummers, velden per serie en per aflevering; alleen veldnamen en tellingen in het logboek) | `Bios.kt` | open vraag 1 | op tv: logcat `BiosProbe` |
| 0.2 | Uitkomst vastleggen in dit plan en parsers daarop afstemmen | `plan.md` | 2.4 | — |

### Fase 1 — Series vinden en afspelen (stories 1, 2, 3)
| # | Taak | Bestanden | AC | Test |
|---|---|---|---|---|
| 1.1 | `SeriesItem` + `SeriesIndex` (zoeken, vinden, ontdubbelen, nieuwste) | `SeriesModels.kt` | 1.9, 1.10 | `SeriesIndexTest` |
| 1.2 | `EpisodeOrder`: volgorde, volgende aflevering, seizoenwissel, Specials, gaten | `SeriesLogic.kt` | 4.7, 4.8, randgevallen | `EpisodeOrderTest` |
| 1.3 | `NextUp`: waar je was + knoptekst | `SeriesLogic.kt` | 3.1, 3.2, 3.4, 3.5 | `NextUpTest` |
| 1.4 | Serielijst ophalen en opslaan, eigen status | `SeriesRepo.kt` | 10.1, 10.2, randgevallen laden | op tv: logcat-tijden |
| 1.5 | Afleveringen ophalen (`get_series_info`), afspeeladres | `SeriesRepo.kt` | 2.4, 10.3 | parser-test met voorbeeld-JSON |
| 1.6 | `SeriesStore`: posities, laatst gekeken, bewaren | `SeriesStore.kt` | 3.3, 3.7 | `NextUpTest` (rekenwerk) |
| 1.7 | Bovenmenu Films/Series, laatst gebruikte tab, terug-gedrag | `BiosUi.kt`, `BiosActivity.kt` | 1.1, 1.2, 1.7, 1.8, 10.4 | op tv |
| 1.8 | Tab Series: billboard + rijen Nieuwe series, Verder kijken, Mijn lijst, categorieën | `SeriesHome.kt`, `BiosActivity.kt` | 1.3–1.5, 3.3, 3.6, 6.9 | op tv, screenshot bord B |
| 1.9 | Rijen via TMDB: Top series, genres, billboard-info | `SeriesGenres.kt` | 1.4, 1.6 | op tv |
| 1.10 | Seriescherm (variant C) | `SeriesDetail.kt` | 2.1, 2.2, 2.8, 3.1, 6.7 | op tv, screenshot bord C |
| 1.11 | Afleveringenscherm: seizoenen, rijen, focus op volgende aflevering | `EpisodesScreen.kt` | 2.3–2.7, 2.9 | op tv, screenshot bord C |
| 1.12 | Speler speelt een aflevering: titel, positie bewaren, geen startanimatie erna | `PlayerActivity.kt` | 4.1, 4.2, 4.13, 2.6 | op tv |
| 1.13 | Laad-, lege en foutstaten | `SeriesHome.kt`, `EpisodesScreen.kt`, `BiosUi.kt` | randgevallen | op tv, bord J |

### Fase 2 — Doorkijken en vinkjes (stories 4, 5)
| # | Taak | Bestanden | AC | Test |
|---|---|---|---|---|
| 2.1 | `Countdown`: wanneer de kaart verschijnt, pauze, terugspoelen, duur onbekend | `SeriesLogic.kt` | 4.3, 4.11, randgeval | `CountdownTest` |
| 2.2 | Kaart Volgende aflevering + automatisch door, één stream | `PlayerActivity.kt` | 4.3–4.8, 4.12 | op tv, bord F |
| 2.3 | Knop Volgende aflevering in de bediening | `PlayerActivity.kt` | 4.9, 4.10 | op tv, bord E |
| 2.4 | Gezien-vinkjes: opslag, markeren, seizoen | `SeriesStore.kt`, `SeriesLogic.kt` | 5.1, 5.2, 5.5, 5.6 | `WatchedTest` |
| 2.5 | Menu bij lang OK (aflevering, seizoen) | `EpisodeMenu.kt`, `EpisodesScreen.kt` | 5.3, 5.4, 5.7 | op tv, bord D |

### Fase 3 — Zoeken, Filter, Mijn lijst, kijkprofiel, nieuwe afleveringen (stories 6, 7, 8)
| # | Taak | Bestanden | AC | Test |
|---|---|---|---|---|
| 3.1 | `Listing` + gemengd zoeken op score | `Listing.kt`, `BiosSearch.kt` | 6.1–6.3 | `ListingSearchTest` |
| 3.2 | Mijn lijst met series, één volgorde | `Bios.kt` (MyList), `BiosSearch.kt` | 6.7, 6.8 | `MyListOrderTest` |
| 3.3 | Filter: Soort Films/Series, tv-genres | `BiosFilterScreen.kt`, `SeriesGenres.kt` | 6.4–6.6 | `FilterSwitchTest` |
| 3.4 | Kijkprofiel voor series + rij Aanbevolen series | `Taste.kt`, `SeriesHome.kt`, `PlayerActivity.kt` | 7.1–7.5 | `TasteTest` (uitbreiden) |
| 3.5 | `NewEpisodes` + band op tegels, "Nieuw" in de lijst | `SeriesLogic.kt`, `SeriesStore.kt`, tegels | 8.1–8.5 | `NewEpisodesTest` |

### Fase 4 — Launcher (story 9)
| # | Taak | Bestanden | AC | Test |
|---|---|---|---|---|
| 4.1 | TMDB voor series (populair, klassiekers, lijkt op, details, IMDb-nummer) | `MovieApi.kt`, `Movie.kt` | 9.2 | parser-test |
| 4.2 | Gemengde rij: films en series apart scoren, samenvoegen op score | `Recommender.kt` | 9.1, 9.8 | `RecScoringTest` (uitbreiden) |
| 4.3 | Likes voor series (eigen sleutel, geen botsing met film-nummers) | `Stores.kt`, `LikedScreen.kt` | 9.7 | `LikeKeyTest` |
| 4.4 | Top shelf + seriescherm in de launcher, knoppen naar Horizon | `HomeScreen.kt`, `MovieDetailScreen.kt`, `MainActivity.kt`, `BiosActivity.kt` | 9.3–9.6 | op tv, borden K, L, M |

### Fase 5 — Afronden
| # | Taak | AC | Test |
|---|---|---|---|
| 5.1 | Alle unit tests + volledige controle op de tv tegen elk criterium, screenshots naast het ontwerp | alle | — |
| 5.2 | README, `claude/overzicht.md` en `claude/werkwijze.md` bijwerken | — | — |

## Dekking
- **Stories:** 1 → 1.1, 1.4, 1.7–1.9 · 2 → 1.5, 1.10, 1.11 · 3 → 1.3, 1.6, 1.8, 1.10 · 4 → 1.2, 1.12, 2.1–2.3 · 5 → 2.4, 2.5 · 6 → 3.1–3.3 · 7 → 3.4 · 8 → 3.5 · 9 → 4.1–4.4.
- **Schermen:** A → 1.7 · B → 1.8, 1.9 · C → 1.10, 1.11 · D → 2.5 · E → 1.12, 2.3 · F → 2.2 · G → 3.1 · H → 3.3 · I → 3.2 · J → 1.13 · K → 4.2, 4.4 · L → 4.4 · M → 4.3.

## Volgorde van opleveren
Na elke fase een build op de tv en een korte controle, zodat fouten vroeg boven komen. Fase 0 en 1 gaan samen in de eerste build.
