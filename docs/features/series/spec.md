Status: goedgekeurd 2026-11-16

# Series in Horizon — Spec

## Doel
Series uit je eigen aanbod kijken in Horizon, net zo makkelijk als films: een serie vinden, een aflevering kiezen, doorkijken, en terugvinden waar je was.

## Gebruikers & context
- Kijkers op de bank met de afstandsbediening van de Chromecast: pijltjes, OK, lang OK en terug. Geen toetsenbord.
- Het abonnement staat één stream tegelijk toe.
- Horizon toont nu alleen films, met het bovenmenu Zoeken · Home · Filter · Mijn lijst.
- Alles wat hieronder niet genoemd wordt, blijft zoals het is. Huisstijl, lettertype en kleuren zijn die van Horizon.

## Begrippen
- **Volgende aflevering van een serie ("waar je was"):**
  1. de aflevering waar je middenin zit (de laatst bekeken);
  2. anders de eerste niet-geziene aflevering ná de laatst geziene;
  3. anders seizoen 1, aflevering 1.
- **Gezien:** een aflevering waarvan je meer dan 95% hebt gekeken, of die je zelf op gezien hebt gezet.
- **Een serie volgen:** de serie staat in Mijn lijst, of je hebt er minstens één aflevering van (deels) gekeken.
- **S2 · A4:** korte schrijfwijze voor seizoen 2, aflevering 4.

## User stories
1. Als kijker wil ik een eigen tab Series met rijen, zodat ik series kan ontdekken zoals ik dat bij films doe.
2. Als kijker wil ik op een seriescherm de seizoenen en afleveringen zien, zodat ik kan kiezen wat ik kijk.
3. Als kijker wil ik met één druk op OK verdergaan waar ik was, zodat ik niet hoef te zoeken naar de juiste aflevering.
4. Als kijker wil ik dat de volgende aflevering vanzelf start, zodat ik kan doorkijken zonder iets te doen.
5. Als kijker wil ik zien welke afleveringen ik heb gezien en dat kunnen aanpassen, zodat mijn voortgang klopt.
6. Als kijker wil ik series kunnen zoeken, filteren en in Mijn lijst zetten, zodat ik ze net zo terugvind als films.
7. Als kijker wil ik dat de Series-tab zich aanpast aan wat ik kijk, zodat de genres die ik leuk vind bovenaan staan.
8. Als kijker wil ik zien dat een serie die ik volg een nieuwe aflevering heeft, zodat ik niets mis.
9. Als kijker wil ik op het homescherm van het kastje aanbevolen series zien en ze direct in Horizon openen.

## Acceptatiecriteria

### 1. Tab Series
- [ ] 1.1 Gegeven Horizon is geladen, dan toont het bovenmenu in deze volgorde: Zoeken · Films · Series · Filter · Mijn lijst.
- [ ] 1.2 Gegeven de tab Films, dan toont die dezelfde rijen in dezelfde volgorde als Home vóór deze wijziging.
- [ ] 1.3 Gegeven ik kies Series, dan zie ik bovenaan een groot beeld van de serie met focus, met titel, aantal seizoenen, jaar, score en een korte omschrijving.
- [ ] 1.4 Gegeven de tab Series, dan staan de rijen in deze volgorde: Nieuwe series, Top series, Verder kijken, Aanbevolen series, Mijn lijst, en daarna de genrerijen. Een rij zonder inhoud wordt niet getoond.
- [ ] 1.5 Gegeven de rij Nieuwe series, dan bevat die series die dit of vorig jaar zijn begonnen, nieuwst toegevoegd eerst.
- [ ] 1.6 Gegeven de rij Top series, dan bevat die de best beoordeelde series aller tijden die in mijn aanbod zitten, op volgorde van score.
- [ ] 1.7 Gegeven ik open Horizon, dan staat hij op de tab (Films of Series) die ik het laatst gebruikte; de eerste keer op Films.
- [ ] 1.8 Gegeven ik sta op Films of Series en druk op terug, dan sluit Horizon. Gegeven ik sta op Zoeken, Filter of Mijn lijst en druk op terug, dan ga ik naar de tab (Films of Series) waar ik vandaan kwam.
- [ ] 1.9 Gegeven serietitels van de aanbieder met voorvoegsels, jaartallen of kwaliteitslabels, dan worden die net als filmtitels opgeschoond getoond.
- [ ] 1.10 Gegeven dezelfde serie staat meerdere keren in het aanbod, dan staat hij hooguit één keer in een rij.

### 2. Seriescherm
- [ ] 2.1 Gegeven ik druk op OK op een serie (in een rij, Zoeken, Filter of Mijn lijst), dan opent het seriescherm met een groot beeld, titel, jaar, score, genres, aantal seizoenen, omschrijving en hoofdrolspelers.
- [ ] 2.2 Gegeven het seriescherm, dan staan er de knoppen: afspelen (zie 3.1), + Mijn lijst en Trailer; de focus staat op de afspeelknop.
- [ ] 2.3 Gegeven een serie met meer dan één seizoen, dan kan ik een seizoen kiezen; het seizoen van mijn volgende aflevering is vooraf gekozen.
- [ ] 2.4 Gegeven een gekozen seizoen, dan zie ik de afleveringen op volgorde, elk met nummer, titel en, als de aanbieder die geeft, duur, korte omschrijving en plaatje.
- [ ] 2.5 Gegeven de afleveringenlijst, dan staat mijn volgende aflevering in beeld zonder dat ik hoef te scrollen.
- [ ] 2.6 Gegeven ik druk op OK op een aflevering, dan speelt die af: verder waar ik was als ik er middenin zat, anders vanaf het begin.
- [ ] 2.7 Gegeven een aflevering waar ik middenin zit, dan toont de lijst een voortgangsbalk bij die aflevering.
- [ ] 2.8 Gegeven ik druk op terug op het seriescherm, dan sta ik weer op dezelfde tegel als waar ik vandaan kwam.
- [ ] 2.9 Gegeven ik kom terug uit de speler, dan toont het seriescherm meteen de bijgewerkte voortgang en vinkjes.

### 3. Verder waar je was
- [ ] 3.1 Gegeven het seriescherm, dan heet de afspeelknop:
  - "▶ Afspelen S1 · A1" als ik de serie nog niet heb gekeken;
  - "▶ Verder kijken S2 · A4" als ik middenin een aflevering zit, met eronder "Je was bij 12:34";
  - "▶ Volgende aflevering S2 · A5" als ik de vorige heb uitgekeken.
- [ ] 3.2 Gegeven ik druk op die knop, dan speelt precies de genoemde aflevering, vanaf de genoemde tijd.
- [ ] 3.3 Gegeven ik heb minstens 30 s van een aflevering gekeken, dan staat de serie in Verder kijken op de tab Series: één tegel per serie, laatst gekeken eerst, met het label van mijn volgende aflevering (bijv. S2 · A4) en een voortgangsbalk.
- [ ] 3.4 Gegeven ik heb een aflevering uitgekeken en er is een volgende, dan wijst de tegel in Verder kijken naar die volgende aflevering.
- [ ] 3.5 Gegeven ik heb de laatste beschikbare aflevering uitgekeken, dan verdwijnt de serie uit Verder kijken.
- [ ] 3.6 Gegeven Verder kijken op de tab Films, dan staan daar alleen films; op de tab Series alleen series.
- [ ] 3.7 Gegeven ik stop Horizon en open hem later opnieuw, dan zijn mijn posities en gezien-vinkjes bewaard.

### 4. Speler en doorkijken
- [ ] 4.1 Gegeven een aflevering speelt en de bediening is in beeld, dan staat bovenin: naam van de serie, S·A en de titel van de aflevering.
- [ ] 4.2 Gegeven een aflevering speelt, dan werken spoelen, ondertitels (keuze onthouden) en het aanbod van Kodi bij een afspeelfout hetzelfde als bij films.
- [ ] 4.3 Gegeven de laatste 10 s van een aflevering en er is een volgende, dan verschijnt rechtsonder een kaart "Volgende aflevering" met S·A, titel en een aftelling, met de knoppen Nu afspelen (focus) en Annuleren.
- [ ] 4.4 Gegeven de kaart en ik doe niets, dan start de volgende aflevering vanaf het begin zodra de aftelling op 0 staat.
- [ ] 4.5 Gegeven de kaart en ik kies Nu afspelen, dan start de volgende aflevering meteen.
- [ ] 4.6 Gegeven de kaart en ik kies Annuleren of druk op terug, dan verdwijnt de kaart, speelt de aflevering uit, en sluit daarna de speler; ik sta dan op het seriescherm bij de volgende aflevering.
- [ ] 4.7 Gegeven de laatste aflevering van een seizoen, dan is de volgende aflevering de eerste van het volgende seizoen.
- [ ] 4.8 Gegeven de laatste beschikbare aflevering van de serie, dan verschijnt er geen kaart; na afloop sluit de speler en sta ik op het seriescherm.
- [ ] 4.9 Gegeven een aflevering van een serie speelt, dan heeft de bediening een knop Volgende aflevering waarmee ik op elk moment naar de volgende ga (bijvoorbeeld tijdens de aftiteling).
- [ ] 4.10 Gegeven de volgende aflevering start (automatisch, via Nu afspelen of via de knop), dan is de vorige gemarkeerd als gezien.
- [ ] 4.11 Gegeven ik pauzeer tijdens de aftelling, dan staat de aftelling ook stil. Gegeven ik spoel terug tot vóór de laatste 10 s, dan verdwijnt de kaart en komt hij later opnieuw.
- [ ] 4.12 Gegeven de speler wisselt van aflevering, dan loopt er nooit meer dan één stream tegelijk.
- [ ] 4.13 Gegeven ik heb een aflevering gekeken en ga terug naar Horizon, dan speelt de startanimatie niet (net als na een film).

### 5. Gezien-vinkjes
- [ ] 5.1 Gegeven een geziene aflevering, dan staat er een vinkje bij in de afleveringenlijst.
- [ ] 5.2 Gegeven alle afleveringen van een seizoen zijn gezien, dan staat er een vinkje bij dat seizoen.
- [ ] 5.3 Gegeven ik houd OK lang ingedrukt op een aflevering, dan krijg ik een menu met "Markeer als gezien" of "Markeer als niet gezien" (wat van toepassing is).
- [ ] 5.4 Gegeven ik houd OK lang ingedrukt op een seizoen, dan krijg ik een menu met "Heel seizoen gezien" of "Heel seizoen niet gezien".
- [ ] 5.5 Gegeven ik zet een aflevering op niet gezien, dan is ook de bewaarde positie van die aflevering weg.
- [ ] 5.6 Gegeven ik verander vinkjes, dan passen de afspeelknop (3.1) en de tegel in Verder kijken zich direct aan.
- [ ] 5.7 Gegeven ik laat OK los na lang indrukken, dan start er geen aflevering.

### 6. Zoeken, Filter en Mijn lijst
- [ ] 6.1 Gegeven ik zoek op een titel, dan staan films en series samen in de resultaten, beste overeenkomst eerst, en is aan elke tegel te zien of het een serie is.
- [ ] 6.2 Gegeven het lege zoekscherm, dan staat er "Zoek een film of serie…" en hoeveel films en series doorzocht worden.
- [ ] 6.3 Gegeven Horizon wordt vanuit de launcher geopend met een zoekopdracht, dan staan ook series in de resultaten.
- [ ] 6.4 Gegeven het filterscherm, dan kan ik bovenaan kiezen tussen Films en Series; vooraf gekozen is de tab waar ik vandaan kwam.
- [ ] 6.5 Gegeven Series is gekozen in het filter, dan werken genres (meerdere tegelijk), periode, minimale score en sortering op series, en toont de genrelijst de genres van series.
- [ ] 6.6 Gegeven ik wissel in het filter tussen Films en Series, dan blijven periode, score en sortering staan; gekozen genres die het andere type niet kent vervallen.
- [ ] 6.7 Gegeven het seriescherm en ik kies + Mijn lijst, dan staat de serie in Mijn lijst en heet de knop ✓ Mijn lijst; nogmaals kiezen haalt hem eruit.
- [ ] 6.8 Gegeven de tab Mijn lijst, dan staan films en series samen, laatst toegevoegd eerst, en is aan elke tegel te zien of het een serie is.
- [ ] 6.9 Gegeven de rij Mijn lijst op de tab Series, dan staan daar alleen series; op de tab Films alleen films.

### 7. Kijkprofiel voor series
- [ ] 7.1 Gegeven ik kijk series, dan schuiven de genrerijen op de tab Series van de genres die ik het meest kijk naar boven.
- [ ] 7.2 Gegeven het kijkprofiel voor series, dan tellen dezelfde regels als bij films: kijken telt naar minuten (per aflevering, pas vanaf 3 minuten), in Mijn lijst zetten telt mee, een seriescherm openen telt licht mee, en oud kijkgedrag telt elke 30 dagen half zo zwaar.
- [ ] 7.3 Gegeven ik kijk alleen films, dan verandert de volgorde op de tab Series niet, en andersom.
- [ ] 7.4 Gegeven ik heb nog geen series gekeken, dan staan de genrerijen in de standaardvolgorde.
- [ ] 7.5 Gegeven de launcher heeft aanbevolen series (zie 9) die in mijn aanbod zitten, dan staan die in de rij Aanbevolen series op de tab Series.

### 8. Nieuwe afleveringen
- [ ] 8.1 Gegeven een serie die ik volg en de aanbieder voegt een aflevering toe, dan krijgt de tegel van die serie het label "Nieuwe aflevering" zodra Horizon het aanbod heeft ververst (uiterlijk 12 uur later, bij het openen van Horizon).
- [ ] 8.2 Gegeven het label, dan is het te zien op de tegel in Verder kijken, de rij en tab Mijn lijst en in zoekresultaten.
- [ ] 8.3 Gegeven een serie die ik helemaal had uitgekeken en die een nieuwe aflevering krijgt, dan staat hij weer in Verder kijken, met de nieuwe aflevering als volgende aflevering.
- [ ] 8.4 Gegeven ik open het seriescherm van een serie met het label, dan zijn de nieuwe afleveringen in de lijst gemarkeerd als "Nieuw" en verdwijnt daarna het label van de tegel.
- [ ] 8.5 Gegeven een serie die ik niet volg, dan krijgt die nooit het label.

### 9. Launcher
- [ ] 9.1 Gegeven de launcher met een TMDB-sleutel, dan staat onder "Aanbevolen voor jou" een rij "Aanbevolen series"; de filmrij blijft zoals hij is.
- [ ] 9.2 Gegeven de rij Aanbevolen series, dan komen de series uit: populair deze week, klassiekers en series die lijken op series die ik leuk vond, met scores van IMDb en Rotten Tomatoes waar bekend.
- [ ] 9.3 Gegeven een serie in de rij heeft focus, dan toont de top shelf het beeld, de titel en waarom hij is aanbevolen, net als bij een film.
- [ ] 9.4 Gegeven ik druk op OK op een serie, dan opent een seriescherm met beeld, titel, jaar, aantal seizoenen, scores, omschrijving en de knoppen Trailer en Vind ik leuk.
- [ ] 9.5 Gegeven de serie zit in mijn aanbod, dan heeft dat scherm ook: een afspeelknop met dezelfde tekst als 3.1, die de genoemde aflevering direct afspeelt, en een knop Afleveringen, die het seriescherm in Horizon opent.
- [ ] 9.6 Gegeven de serie zit niet in mijn aanbod, dan staat er "Zoek in Horizon", die Horizon opent met de titel als zoekopdracht.
- [ ] 9.7 Gegeven ik kies Vind ik leuk bij een serie, dan staat die in de lijst met wat ik leuk vond (lang OK op de klok), herkenbaar als serie, en telt hij mee voor Aanbevolen series.
- [ ] 9.8 Gegeven likes van series, dan veranderen die de aanbevolen films niet, en andersom.

### 10. Laden en snelheid
- [ ] 10.1 Gegeven de eerste keer na deze wijziging, dan haalt Horizon de series op bij de aanbieder en toont het laadscherm dat.
- [ ] 10.2 Gegeven een latere start, dan openen films en series uit de opslag op het kastje, en duurt het laden hooguit 2 s langer dan vóór deze wijziging.
- [ ] 10.3 Gegeven ik open een seriescherm, dan zijn de afleveringen binnen 3 s in beeld bij een werkende verbinding; tot die tijd staat er "Afleveringen laden…".
- [ ] 10.4 Gegeven ik wissel tussen de tabs Films en Series, dan wordt er niet opnieuw geladen.

## Schermen / interacties
| # | Scherm | Nieuw of aangepast | Hoort bij story |
|---|---|---|---|
| A | Bovenmenu: Zoeken · Films · Series · Filter · Mijn lijst | aangepast | 1 |
| B | Tab Series (groot beeld + rijen) | nieuw | 1, 3, 7, 8 |
| C | Seriescherm (info, knoppen, seizoenen, afleveringen) | nieuw | 2, 3, 5, 8 |
| D | Menu bij lang OK op aflevering of seizoen | nieuw | 5 |
| E | Speler: titel met S·A, knop Volgende aflevering | aangepast | 4 |
| F | Kaart "Volgende aflevering" met aftelling | nieuw | 4 |
| G | Zoeken: films en series samen | aangepast | 6 |
| H | Filter: keuze Films / Series | aangepast | 6 |
| I | Mijn lijst: films en series samen | aangepast | 6 |
| J | Laadscherm: ook series | aangepast | 1 |
| K | Launcher: rij Aanbevolen series | nieuw | 9 |
| L | Launcher: seriescherm | nieuw | 9 |
| M | Launcher: lijst Vind ik leuk met series | aangepast | 9 |

## Randgevallen & foutsituaties
- **Aanbieder heeft geen series:** de tab Series toont "Je aanbieder heeft geen series"; Films werkt gewoon.
- **Series ophalen mislukt:** Films werkt gewoon; de tab Series toont een melding met "Opnieuw proberen". Een eerder opgeslagen serielijst blijft bruikbaar.
- **Afleveringen ophalen mislukt:** het seriescherm toont "Afleveringen konden niet geladen worden" met "Opnieuw proberen"; + Mijn lijst en Trailer blijven werken.
- **Serie zonder afleveringen:** "Nog geen afleveringen beschikbaar"; geen afspeelknop.
- **Aflevering zonder titel:** de titel is "Aflevering 4".
- **Serie met één seizoen:** er is geen seizoenkeuze.
- **Specials (seizoen 0):** staan als "Specials" achteraan. Doorkijken gaat niet vanzelf van een gewoon seizoen naar Specials.
- **Ontbrekende nummers** (A1, A2, A4): de volgende aflevering is de eerstvolgende die er is.
- **Aflevering verdwenen bij de aanbieder:** Verder kijken wijst naar de eerstvolgende die er wel is. Is de hele serie weg, dan verdwijnt hij uit Verder kijken en Mijn lijst-rijen; hij komt terug als de aanbieder hem terugzet.
- **Volgende aflevering start niet:** er komt een foutmelding met het aanbod van Kodi; de speler probeert niet vanzelf nog een aflevering.
- **Duur van de aflevering onbekend:** de kaart verschijnt als de aflevering is afgelopen en telt dan 10 s af.
- **Geen TMDB-sleutel:** de tab Series toont de categorieën van de aanbieder als rijen (zoals films nu); Top series en Aanbevolen series ontbreken; in het filter ontbreken de genres; de launcher toont de rij Aanbevolen series niet.
- **Serie zonder TMDB-nummer:** hij is te vinden via Zoeken, Nieuwe series, categorieën en Mijn lijst, maar staat niet in Top series of genrerijen.
- **Andere aanbieder of inlog ingesteld:** de serielijst wordt opnieuw opgehaald; posities, vinkjes en Mijn lijst van series die niet meer bestaan worden niet getoond.

## Non-goals
- Wijzigingen aan hoe films werken, behalve de naam van de tab (Home → Films) en de gedeelde schermen Zoeken, Filter en Mijn lijst.
- Downloaden of offline kijken.
- Meerdere profielen.
- Intro of aftiteling automatisch herkennen en overslaan.
- Meldingen buiten de app.
- Gezien-status delen met andere apparaten of diensten.
- Series in Horizon TV of Roze Hoek.
- Audiotaal kiezen in de speler.
- Verwijderen uit Verder kijken (bestaat ook niet voor films).
- Verder kijken of Mijn lijst tonen in de launcher.

## Open vragen
1. **Serie-aanbod van de aanbieder is nog niet gemeten:** aantal series, hoeveel er een TMDB-nummer hebben, en wat er per aflevering bekend is (titel, duur, plaatje, omschrijving). De randgevallen hierboven dekken de slechtste uitkomst af. Voorstel: meten op de tv vóór het ontwerp, zodat het seriescherm past bij wat er echt is.
2. **"Kijk je nog?"** na een aantal afleveringen zonder dat je de afstandsbediening aanraakt. Voorstel: niet in versie 1.
