<p align="center">
  <img src="docs/images/banner.png" alt="Horizon: een rustig homescherm voor Google TV, met je eigen films en live-tv" width="100%">
</p>

# Horizon launcher

Een eigen homescherm voor de **Chromecast met Google TV**, zonder advertenties. Eén app met vier onderdelen:

- **Thuis-launcher**: het homescherm zelf, met je apps en filmaanbevelingen (TMDB, met scores via OMDb).
- **Horizon**: de films uit je eigen Xtream-abonnement als overzichtelijke streamingapp, met zoeken, filters, Mijn lijst en Verder kijken.
- **Horizon TV**: live-tv met zenderlijst, tv-gids en terugkijken (als je aanbieder dat heeft).
- **Roze Hoek**: alleen de 18+-zenders uit je aanbod. Staat **standaard uit**; aanzetten via het tandwiel → *Apps*.

Alles werkt met de afstandsbediening.

<p align="center">
  <img src="launcher/app/src/main/res/drawable-xhdpi/horizon_banner.png" alt="Tegel Horizon" width="260">
  &nbsp;
  <img src="launcher/app/src/main/res/drawable-xhdpi/horizon_tv_banner.png" alt="Tegel Horizon TV" width="260">
  &nbsp;
  <img src="launcher/app/src/main/res/drawable-xhdpi/roze_hoek_banner.png" alt="Tegel Roze Hoek" width="260">
</p>

## Wat je nodig hebt

- Een Chromecast met Google TV, of een ander Google TV- of Android TV-kastje met Android 9 of nieuwer (getest op Android 14).
- Een Windows-pc in hetzelfde wifi-netwerk. Android Studio is niet nodig.
- Optioneel: een gratis TMDB-token en OMDb-sleutel (voor aanbevelingen en scores) en je eigen Xtream-abonnement (voor Horizon en Horizon TV).

## Installeren

**Op de tv (eenmalig):**
1. *Instellingen → Systeem → Over → Android TV OS-build*: klik er 7 keer op.
2. *Instellingen → Systeem → Ontwikkelaarsopties*: zet **Draadloze foutopsporing** aan.

**Op de pc:** download de repository (**Code → Download ZIP**) en dubbelklik de scripts op volgorde:

| Stap | Script | Wat het doet |
|:---:|---|---|
| 1 | `1-sleutels.cmd` | Sleutels en IPTV-inlog invullen. Mag je overslaan: dit kan ook later op de tv. |
| 2 | `2-bouwen.cmd` | Downloadt de eerste keer de bouwtools (ongeveer 1,5 GB, alleen in deze map) en bouwt de app. |
| 3 | `3-koppelen.cmd` | Koppelt de pc met de tv. Kies op de tv *Apparaat koppelen met koppelingscode*. |
| 4 | `4-installeren.cmd` | Installeert de app en zet hem als homescherm. |

Werkt verbinden niet meer na een herstart van de tv? Verwijder `tv-adres.txt`; het script vraagt dan het nieuwe adres.

**Sleutels en inlog** kun je ook op de tv invullen via het tandwiel in de launcher. Lange sleutels plak je vanaf je telefoon of pc via het adres en de code die op het scherm staan.

**Terug naar Google TV?** Verwijder de app via *Instellingen → Apps*.

## Privacy

- Er staan geen sleutels of wachtwoorden in deze repository. Ze staan alleen op je pc (`launcher/local.properties`) of op de tv.
- Sleutels die je via `1-sleutels.cmd` invult, zitten in de gebouwde APK. **Deel je zelfgebouwde APK dus niet.**
- Wat je kijkt blijft alleen op het kastje.

## Disclaimer

Dit project levert **geen films, zenders of streams**. Het is alleen een weergave voor een Xtream-abonnement dat je zelf afsluit; gebruik het alleen met een aanbod waarvoor je de rechten hebt.

Niet verbonden aan Google, TMDB, OMDb, IMDb of Rotten Tomatoes. Dit product gebruikt de TMDB API, maar wordt niet door TMDB ondersteund of gecertificeerd.

## Licentie

[MIT](LICENSE). Het lettertype Poppins valt onder de [SIL Open Font License 1.1](licenses/Poppins-OFL.txt).
