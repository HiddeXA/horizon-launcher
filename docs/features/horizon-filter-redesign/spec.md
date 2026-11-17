# Horizon filterpagina, redesign: spec

Status: goedgekeurd 17 nov 2026
Bouwt voort op: `docs/features/series` (branch `feature/series`). Daar kwam de keuze Films / Series in het filter bij (criteria 6.4–6.6). Deze spec vervangt series-criterium 6.4: het filter begint voortaan gemengd.

## Doel
De filterpagina van Horizon krijgt een strakke, professionele uitstraling en een betere opbouw. De filtermogelijkheden blijven zoals nu (genres, periode, minimale score en sortering), met één verandering: films en series staan standaard gemengd in de resultaten. Wie wil, beperkt tot alleen films of alleen series. De titels die erbij passen krijgen de hoofdrol op het scherm.

## Gebruikers & context
- Kijkers op de bank, met de afstandsbediening van de Chromecast met Google TV. Ze gebruiken alleen ▲▼◄►, OK en Terug.
- Het scherm is een tv van 1920×1080, bekeken van een paar meter afstand.
- Het aanbod telt ongeveer 55.000 films, plus series. De aanbieder levert geen genres. Filteren op genre loopt via TMDB en geeft hoogstens ongeveer 60 titels, de bekendste eerst. Zonder genre filtert Horizon zelf en toont hij hoogstens ongeveer 120 titels.
- Je opent de pagina via de pil **Filter** in het bovenmenu (Zoeken · Films · Series · Filter · Mijn lijst). De pil krijgt een stip als er een filter actief is.

## User stories
1. Als kijker wil ik een filterpagina die er verzorgd uitziet, zodat Horizon voelt als een echte streamingapp.
2. Als kijker wil ik standaard films en series samen zien, en desgewenst beperken tot alleen films of alleen series, zodat ik alles vind wat bij mijn zin past.
3. Als kijker wil ik één of meer genres kiezen, zodat ik titels zie die al die genres hebben (bijv. Actie + Komedie).
4. Als kijker wil ik een periode, een minimale score en een sortering kiezen, zodat ik precies vind wat ik zoek.
5. Als kijker wil ik altijd zien welke filters aan staan en hoeveel titels erbij passen, zodat ik weet waar ik naar kijk.
6. Als kijker wil ik snel heen en weer kunnen tussen de keuzes en de resultaten, met weinig keer drukken.
7. Als kijker wil ik met één actie alles wissen, en per filter terug kunnen naar "Alles", zodat ik makkelijk opnieuw begin.
8. Als kijker wil ik dat de pagina duidelijk zegt wat er aan de hand is als hij nog laadt of zoekt, niets vindt of iets mist, zodat ik niet naar een leeg scherm staar.
9. Als kijker wil ik na het bekijken van een film of serie terugkomen op dezelfde filters en dezelfde plek, zodat ik verder kan bladeren.

## Acceptatiecriteria
**Uitstraling**
- [ ] Gegeven de filterpagina, dan gebruikt hij alleen de bestaande Horizon-kleuren, het lettertype Poppins en het bestaande bovenmenu.
- [ ] Gegeven de filterpagina zonder actieve filters, dan neemt het resultatenraster minstens twee derde van de schermbreedte in.

**Soort: Alles / Films / Series**
- [ ] Gegeven de filterpagina, dan staat de keuze Alles / Films / Series als eerste en is altijd één van de drie gekozen.
- [ ] Wanneer ik de pagina open, dan staat Soort op **Alles**, ongeacht de tab waar ik vandaan kwam. Na terugkomen van een film- of seriescherm blijft de gekozen soort staan.
- [ ] Gegeven Alles, dan staan films en series door elkaar in één resultatenlijst, gesorteerd volgens de gekozen sortering (bijv. bij Beste score de hoogste score bovenaan, film of serie).
- [ ] Gegeven Alles, dan toont de pagina bij het aantal hoeveel films en hoeveel series erbij passen (bijv. "84 titels · 52 films, 32 series").
- [ ] Gegeven Alles, dan is één gezamenlijke genrelijst te zien. Genres die films en series allebei hebben (bijv. Drama, Komedie, Misdaad) staan er één keer in en gelden voor beide.
- [ ] Gegeven Alles, dan zijn drie genres samengevoegd tot één keuze die voor films én series geldt:
  - **Actie & Avontuur**: films met Actie of Avontuur, en series met Actie & Avontuur;
  - **Sci-Fi & Fantasy**: films met Sciencefiction of Fantasy, en series met Sci-Fi & Fantasy;
  - **Oorlog**: films met Oorlog, en series met Oorlog & Politiek.
  Bij Films of Series staan de gewone genres van die soort.
- [ ] Gegeven ik wissel van Alles naar Films of Series met een samengevoegd genre gekozen, dan gaat dat genre mee als de passende genre(s) van die soort, en andersom.
- [ ] Gegeven Alles en een genre dat alleen bij films hoort (bijv. Horror), dan zie ik alleen films. Bij een genre dat alleen bij series hoort (bijv. Reality), zie ik alleen series. De pagina zegt dat erbij.
- [ ] Gegeven Films, dan toont de genrelijst de 18 filmgenres en zie ik alleen films. Gegeven Series, dan toont hij de 15 seriegenres en zie ik alleen series.
- [ ] Gegeven ik wissel van soort, dan blijven periode, score en sortering staan. Gekozen genres die de nieuwe soort ook kent blijven gekozen, de rest vervalt.
- [ ] Gegeven een titel met focus in de resultaten, dan kan ik zien of het een film of een serie is (bijv. door "3 seizoenen" in plaats van een speelduur).
- [ ] Gegeven de resultaten, dan spreken teksten over "titels", "films" of "series", afhankelijk van de gekozen soort.

**Keuzes**
- [ ] Gegeven de filterpagina, dan zijn alle filtersoorten (Soort, Genres, Periode, Minimale score, Sorteren) zichtbaar of met één keer OK te openen. Er komen geen soorten bij en er gaan er geen af.
- [ ] Gegeven de keuzes per soort, dan zijn het dezelfde als nu:
  - genres: 18 voor films, 15 voor series, en bij Alles de gezamenlijke lijst;
  - periode: Alles, 2020 en later, Jaren 10, Jaren 00, Jaren 90, Jaren 80, Ouder;
  - score: Alles, 6+, 7+, 8+;
  - sorteren: Populair, Nieuw toegevoegd, Beste score, A–Z.
- [ ] Gegeven Genres, wanneer ik meerdere genres kies, dan blijven ze allemaal geselecteerd, en de pagina zegt dat de titels alle gekozen genres hebben.
- [ ] Gegeven Periode, Score of Sorteren, wanneer ik een keuze maak, dan vervangt die de vorige keuze binnen die soort.
- [ ] Gegeven een geselecteerde keuze, dan verschilt die ook zonder focus duidelijk van niet-geselecteerde keuzes, en niet alleen door kleur (bijv. ook een vinkje of een vorm).
- [ ] Gegeven een keuze met focus, dan is de focus op 3 meter afstand duidelijk te zien en anders dan "geselecteerd".

**Overzicht van actieve filters**
- [ ] Gegeven één of meer actieve filters, dan staat er een samenvatting die zichtbaar blijft terwijl ik door de resultaten blader (bijv. "Series · Misdaad · Drama · 2020 en later · 7+").
- [ ] Gegeven de resultaten, dan staat het aantal titels erbij. Bij genres en 60 of meer titels staat erbij dat het de bekendste zijn.
- [ ] Gegeven actieve filters of een andere sortering dan Populair, dan is er een duidelijke actie "Alles wissen". Die zet alles terug, ook de soort (naar Alles). Zonder actieve filters is hij er niet, of hij is uitgeschakeld.

**Bediening met de afstandsbediening**
- [ ] Wanneer ik de pagina open via de pil Filter en omlaag druk, dan staat de focus op de keuze Alles / Films / Series.
- [ ] Gegeven de focus op een filterelement, dan kom ik met hoogstens 2 keer drukken bij de eerste titel van de resultaten.
- [ ] Gegeven de focus ergens in de resultaten, dan brengt Terug de focus naar de filters. Nog een keer Terug gaat naar de tab waar ik vandaan kwam (Films of Series), zoals series-criterium 1.8 zegt.
- [ ] Gegeven een open keuzepaneel (als het ontwerp panelen gebruikt), wanneer ik Terug druk, dan sluit alleen dat paneel en blijven mijn keuzes staan.
- [ ] Gegeven een wijziging in de filters, dan worden de resultaten pas ververst als ik ongeveer een derde seconde niets meer kies, zodat snel doorklikken niet hapert.
- [ ] Gegeven een titel in de resultaten, wanneer ik OK druk, dan opent het filmscherm of het seriescherm. Wanneer ik daarna terugga, dan staan dezelfde filters aan en heeft dezelfde titel weer focus.

**Staten**
- [ ] Gegeven een wijziging in de filters, terwijl er gezocht wordt, dan zie ik een rustige laadstaat op de plek van de resultaten, en het overzicht van de filters blijft staan.
- [ ] Gegeven Series is gekozen terwijl het seriesaanbod nog laadt, dan zegt de pagina dat de series nog laden. Hij zegt dan niet "Geen series gevonden".
- [ ] Gegeven Alles terwijl het seriesaanbod nog laadt, dan zie ik de films alvast, met een melding dat de series nog komen. Zodra ze er zijn, komen ze in de lijst zonder dat mijn focus verspringt.
- [ ] Gegeven geen titels die passen, dan zie ik een lege staat die dat zegt en een voorstel doet (bijv. een filter losser zetten of Alles wissen), met een knop die je kunt kiezen.
- [ ] Gegeven gekozen genres zonder TMDB-token, dan legt de pagina uit dat filteren op genre een TMDB-token nodig heeft (te vullen via Instellingen), en worden de overige filters gewoon toegepast.
- [ ] Gegeven gekozen genres terwijl TMDB niet bereikbaar is, dan zegt de pagina dat genres nu niet geladen kunnen worden. Hij zegt dan niet "Geen films gevonden".
- [ ] Gegeven Alles en een fout bij alleen films of alleen series (bijv. TMDB geeft wel films maar geen series), dan zie ik wat er wel is, met een melding over het deel dat ontbreekt.

## Schermen / interacties (lijst, nog geen ontwerp)
1. **Filterpagina, beginstand (Alles)**: geen filters, sortering Populair, films en series gemengd (stories 1, 2, 5, 6).
2. **Filterpagina beperkt tot Films of Series**: eigen genrelijst, alleen die soort (story 2).
3. **Filterpagina met actieve filters**: overzicht, aantal, Alles wissen (stories 3, 4, 5, 7).
4. **Keuzes per filtersoort**: Soort (Alles / Films / Series), Genres (gezamenlijk of per soort, meerdere tegelijk), Periode, Minimale score, Sorteren. Op het scherm zelf of als paneel, dat beslist het ontwerp (stories 2, 3, 4, 7).
5. **Focus in de resultaten**: hoe een film of serie met focus eruitziet, en eventueel een preview (stories 5, 6, 9).
6. **Laadstaat**: zoeken, series die nog laden, en films alvast zichtbaar terwijl series laden (story 8).
7. **Lege staat: geen titels gevonden** (stories 7, 8).
8. **Melding TMDB-token ontbreekt** (story 8).
9. **Fout: TMDB niet bereikbaar**, helemaal of voor één soort (story 8).

## Randgevallen & foutsituaties
- Alle genres gekozen: de lege staat moet duidelijk zijn, en het overzicht mag niet uit het scherm lopen. Een lange samenvatting kort je af, bijv. "Actie, Komedie +5".
- Lange genrenamen bij series ("Actie & Avontuur", "Oorlog & Politiek", "Sci-Fi & Fantasy") moeten even netjes passen als de korte.
- Wisselen van soort terwijl er nog gezocht wordt: er mogen geen films in de serieresultaten staan, en ook niet andersom.
- Een film en een serie met dezelfde titel (bijv. een film en de serie erna): beide staan in de lijst en zijn bij focus van elkaar te onderscheiden.
- Bij Alles met genres kan het aantal oplopen tot ongeveer 120 (60 films + 60 series). Bij "de bekendste" geldt dat per soort.
- Titels zonder jaartal vallen weg bij een periodefilter. Titels zonder score vallen weg bij een minimale score.
- Het aanbod is nog aan het laden bij het openen (eerste keer, of uit de opslag in 5–7 s): de pagina toont de laadstaat en geen lege staat.
- Heel snel achter elkaar kiezen: er mag geen flikkering tussen oude en nieuwe resultaten zijn.
- Lange titels in de resultaten: afkorten met een beletselteken, zonder dat de layout verschuift.
- Posters die niet laden: een nette plaatsvervanger met de titel.

## Non-goals
- Geen nieuwe huisstijl (kleuren, Poppins en logo blijven).
- Geen tekstzoekveld in het filterscherm.
- Geen wijziging aan het bovenmenu, inclusief de stip op de pil Filter.
- Geen nieuwe filtersoorten of keuzes (zoals genre uitsluiten, taal of bewaarde filters).
- Geen andere werking van het filteren per soort: hoe er gezocht wordt, de TMDB-aanpak en het maximum aantal resultaten per soort blijven zoals nu. Nieuw is alleen het samenvoegen van films en series bij Alles.
- Geen label "Serie" op de tegels (net als in de series-spec); het verschil zie je bij focus.

## Open vragen
- Geen.
