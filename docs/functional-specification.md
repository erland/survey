# Funktionell specifikation – Enkättjänst

**Status:** Första baslinje för CREATE  
**Projekt:** Arbetsnamn: Enkättjänst  
**Primärt användningsfall:** Liveenkäter i workshop  
**Sekundärt användningsfall:** Tidsbegränsade enkäter som besvaras via utskickad länk

## 1. Syfte och mål

Systemet ska göra det mycket enkelt för en administratör att skapa och genomföra anonyma enkäter.

Det primära användningsfallet är en workshop där administratören startar ett genomförande, visar en QR-kod eller kort anslutningskod, deltagarna svarar från telefon eller dator och resultaten visas live.

Systemet ska även stödja enkäter som är öppna under en längre tidsperiod och besvaras via en distribuerad länk.

### Mål

- En administratör ska snabbt kunna skapa och starta en enkät.
- En deltagare ska kunna börja svara utan konto eller inloggning.
- Deltagarnas svar ska vara anonyma.
- Resultaten ska kunna följas och presenteras live.
- Systemet ska fungera väl på både telefon och dator.
- Enkäter och resultat ska kunna flyttas ut ur tjänsten för arkivering och vidare bearbetning.

### Framgångskriterier för MVP

- En färdig enkät ska kunna startas för en workshop med högst några få interaktioner.
- En deltagare som skannar QR-koden ska komma direkt till rätt enkät utan registrering.
- Administratören ska kunna se antal påbörjade och slutförda deltagarsessioner live.
- Resultat för strukturerade frågetyper ska kunna visualiseras utan extern bearbetning.
- En enkätdefinition ska kunna exporteras och senare importeras igen.
- Resultat ska kunna exporteras i ett format som är enkelt att använda utanför tjänsten.

## 2. Scope

### Must – MVP

- Skapa, redigera, kopiera och radera enkätmallar.
- Skapa frågor av typerna:
  - fritext
  - ja/nej
  - enkelval
  - flerval/kryssrutor
  - skala
- Markera frågor som obligatoriska eller frivilliga.
- Skapa separata genomföranden av en enkätmall.
- Starta och stänga ett genomförande.
- Ange öppnings- och stängningstid för ett genomförande.
- Dela ett genomförande via:
  - direktlänk
  - QR-kod
  - tjänstens adress + kort kod
- Besvara enkät utan deltagarkonto.
- Spara anonym deltagarsession så att ett påbörjat svar normalt kan återupptas.
- Ge stöd mot oavsiktliga multipla svar från samma webbläsare.
- Visa live:
  - antal påbörjade deltagarsessioner
  - antal slutförda deltagarsessioner
  - sammanställda resultat
- Visa resultat per fråga.
- Presentationsläge för workshop/projektor.
- Exportera och importera enkätdefinition.
- Exportera resultat som CSV och JSON.
- Exportera ett komplett enkätpaket med definition och resultat.

### Should

- Möjlighet att konfigurera skalans min/max och etiketter, exempelvis 1–5 med "Instämmer inte" och "Instämmer helt".
- Möjlighet att dölja resultat tills administratören väljer att visa dem.
- Möjlighet att återanvända samma enkätmall för flera genomföranden.
- Möjlighet att se historiska genomföranden och deras resultat.
- Möjlighet att pausa ett genomförande för nya deltagare.
- Möjlighet att nollställa ett testgenomförande innan workshop startar.

### Could

- Engångskoder för starkare skydd mot flera svar utan att identiteten kopplas till svaret.
- Frågebank.
- Villkorsstyrda frågor.
- AI-stöd för sammanfattning av fritext.
- Jämförelse mellan flera genomföranden.
- E-postutskick från tjänsten.
- Teman och organisationsbranding.
- Integrationer med Teams, Slack eller liknande.

## 3. Aktörer

### Administratör

Skapar och hanterar enkäter, startar genomföranden, distribuerar deltagarlänkar och följer/presenterar resultat.

Administratören ska vara autentiserad.

### Deltagare

Besvarar en enkät anonymt via länk, QR-kod eller kort kod.

Deltagaren behöver inte skapa konto eller logga in.

### Presentatör

En roll/situation där administratören visar ett genomförandes resultat i en ren presentationsvy. I MVP kan detta vara samma autentiserade användare som administratören.

## 4. Centrala användningsfall

### UC-001 – Skapa enkät

1. Administratören skapar en ny enkät.
2. Administratören anger titel och eventuell introduktion.
3. Administratören lägger till frågor.
4. Administratören väljer frågetyp och eventuella svarsalternativ.
5. Administratören markerar frågor som obligatoriska eller frivilliga.
6. Enkäten sparas som mall.

### UC-002 – Starta workshopgenomförande

1. Administratören väljer en enkätmall.
2. Administratören skapar ett nytt genomförande.
3. Systemet genererar en unik direktlänk, kort kod och QR-kod.
4. Administratören öppnar genomförandet.
5. Administratören visar anslutningsinformationen för deltagarna.

### UC-003 – Besvara enkät

1. Deltagaren öppnar direktlänken, skannar QR-koden eller anger kortkoden.
2. Systemet öppnar rätt genomförande.
3. En anonym deltagarsession skapas eller återupptas.
4. Deltagaren besvarar frågorna.
5. Systemet sparar svar under arbetets gång.
6. Deltagaren skickar in enkäten.
7. Systemet markerar deltagarsessionen som slutförd.

### UC-004 – Följa workshop live

1. Administratören öppnar livevyn.
2. Systemet visar antal deltagare som påbörjat respektive slutfört.
3. Resultat uppdateras medan svar kommer in.
4. Administratören kan växla mellan översikt och resultat per fråga.

### UC-005 – Presentera resultat

1. Administratören väljer presentationsläge.
2. Systemet visar en ren projektoranpassad vy.
3. Administratören väljer fråga.
4. Systemet visar lämplig visualisering för frågetypen.

### UC-006 – Genomföra tidsbegränsad enkät

1. Administratören skapar ett genomförande och anger öppnings- och slutdatum.
2. Länken distribueras utanför tjänsten.
3. Deltagare svarar under öppningsperioden.
4. Efter slutdatum accepterar systemet inte nya svar.
5. Administratören kan fortsatt granska och exportera resultatet.

### UC-007 – Exportera och importera

1. Administratören exporterar en enkätdefinition eller ett komplett enkätpaket.
2. Systemet skapar ett portabelt filformat.
3. En administratör kan senare importera filen.
4. Systemet validerar innehållet innan import.

## 5. Funktionella krav

### Enkätmallar

**FR-001 [Must]** Administratören ska kunna skapa en enkätmall med titel och valfri beskrivning.

**FR-002 [Must]** Administratören ska kunna redigera en enkätmall som inte är låst av ett pågående genomförande.

**FR-003 [Must]** Administratören ska kunna kopiera en enkätmall.

**FR-004 [Must]** Administratören ska kunna radera en enkätmall när detta inte förstör resultat som ska bevaras.

**FR-005 [Must]** Systemet ska kunna använda samma enkätmall som grund för flera separata genomföranden.

### Frågor

**FR-010 [Must]** Administratören ska kunna skapa fritextfrågor.

**FR-011 [Must]** Administratören ska kunna skapa ja/nej-frågor.

**FR-012 [Must]** Administratören ska kunna skapa enkelvalsfrågor med administratörsdefinierade alternativ.

**FR-013 [Must]** Administratören ska kunna skapa flervalsfrågor med administratörsdefinierade alternativ.

**FR-014 [Must]** Administratören ska kunna skapa skalfrågor.

**FR-015 [Must]** Administratören ska kunna markera en fråga som obligatorisk eller frivillig.

**FR-016 [Must]** Administratören ska kunna ändra frågornas ordning.

**FR-017 [Should]** För skalfrågor ska administratören kunna ange minsta och högsta värde samt valfria ändpunktsrubriker.

### Genomföranden

**FR-020 [Must]** Administratören ska kunna skapa ett genomförande från en enkätmall.

**FR-021 [Must]** Ett genomförande ska ha en egen ögonblicksbild av enkätens frågor så att senare ändringar av mallen inte förändrar historiska resultat.

**FR-022 [Must]** Administratören ska kunna öppna ett genomförande för svar.

**FR-023 [Must]** Administratören ska kunna stänga ett genomförande.

**FR-024 [Must]** Administratören ska kunna ange ett framtida öppningsdatum och/eller slutdatum.

**FR-025 [Must]** Ett stängt eller ännu inte öppnat genomförande ska inte acceptera nya svar.

**FR-026 [Should]** Administratören ska kunna pausa ett öppet genomförande för nya deltagare utan att förlora redan insamlade svar.

### Distribution

**FR-030 [Must]** Varje genomförande ska ha en unik direktlänk.

**FR-031 [Must]** Systemet ska kunna visa en QR-kod som leder till genomförandets direktlänk.

**FR-032 [Must]** Varje genomförande ska ha en kort, människoläsbar anslutningskod.

**FR-033 [Must]** Deltagaren ska kunna ange kortkoden från tjänstens startsida för att öppna rätt genomförande.

**FR-034 [Must]** Systemet ska ge begripligt felmeddelande för ogiltig eller inaktiv kod.

### Deltagande och anonymitet

**FR-040 [Must]** Deltagaren ska kunna besvara en enkät utan konto och utan inloggning.

**FR-041 [Must]** Ett svar ska inte kräva namn, e-postadress eller annan direkt personidentifierare.

**FR-042 [Must]** Systemet ska skapa en slumpmässig teknisk identifierare för deltagarsessionen.

**FR-043 [Must]** Deltagaridentifieraren ska användas för att återuppta ett påbörjat svar och minska risken för oavsiktliga dubbletter.

**FR-044 [Must]** En redan slutförd deltagarsession ska normalt inte kunna skicka in ytterligare ett svar från samma lokala deltagarsession.

**FR-045 [Must]** Systemet ska inte beskriva mekanismen i FR-043–044 som ett garanterat skydd mot att samma person svarar flera gånger.

**FR-046 [Could]** Systemet ska kunna stödja separata engångskoder där kodens användning kan valideras utan att koden lagras tillsammans med svarets innehåll.

### Besvarande

**FR-050 [Must]** Deltagaren ska kunna besvara samtliga stödda frågetyper från telefon och dator.

**FR-051 [Must]** Systemet ska bevara pågående svar så att en tillfällig omladdning eller återgång till enkäten normalt inte raderar arbetet.

**FR-052 [Must]** Systemet ska hindra slutlig inlämning om en obligatorisk fråga saknar giltigt svar.

**FR-053 [Must]** Deltagaren ska kunna skicka in enkäten när alla obligatoriska krav är uppfyllda.

**FR-054 [Must]** Efter inlämning ska deltagaren få en tydlig bekräftelse.

### Live-status

**FR-060 [Must]** Administratören ska under ett öppet genomförande kunna se antal påbörjade deltagarsessioner.

**FR-061 [Must]** Administratören ska kunna se antal slutförda deltagarsessioner.

**FR-062 [Must]** Administratören ska kunna se hur många deltagarsessioner som är pågående enligt systemets definition av aktiv session.

**FR-063 [Must]** Status och resultat ska uppdateras automatiskt utan att administratören manuellt behöver ladda om sidan.

### Resultat

**FR-070 [Must]** Administratören ska kunna visa resultat för en enskild fråga.

**FR-071 [Must]** Ja/nej-resultat ska kunna visas grafiskt med antal eller andel per alternativ.

**FR-072 [Must]** Enkelvalsresultat ska kunna visas som stapeldiagram eller likvärdig jämförbar visualisering.

**FR-073 [Must]** Flervalsresultat ska kunna visas som stapeldiagram eller likvärdig visualisering per alternativ.

**FR-074 [Must]** Skalresultat ska kunna visas som en fördelning över skalans värden.

**FR-075 [Must]** Fritextsvar ska kunna visas som en lista.

**FR-076 [Must]** Resultatvyn ska visa hur många svar som ligger till grund för respektive resultat.

**FR-077 [Should]** För skalfrågor ska systemet kunna visa lämpliga sammanfattningsmått som komplement till hela fördelningen.

### Presentationsläge

**FR-080 [Must]** Administratören ska kunna öppna en ren presentationsvy av ett genomförande.

**FR-081 [Must]** Presentationsvyn ska vara lämplig för stor skärm/projektor och inte visa normala redigeringskontroller.

**FR-082 [Must]** Presentationsvyn ska kunna visa aktuell deltagarstatus och resultat per vald fråga.

**FR-083 [Should]** Administratören ska kunna välja att dölja resultat från presentationsvyn tills det är dags att diskutera dem.

### Import/export

**FR-090 [Must]** Administratören ska kunna exportera en enkätdefinition till ett dokumenterat JSON-format.

**FR-091 [Must]** Administratören ska kunna importera en giltig enkätdefinition från motsvarande format.

**FR-092 [Must]** Importerat innehåll ska valideras innan det sparas.

**FR-093 [Must]** Administratören ska kunna exportera insamlade svar som CSV.

**FR-094 [Must]** Administratören ska kunna exportera insamlade svar som JSON.

**FR-095 [Must]** Administratören ska kunna exportera ett komplett enkätpaket som innehåller enkätdefinition, genomförandeinformation och resultat.

## 6. Affärsregler

**BR-001** En enkätmall och ett faktiskt genomförande är separata objekt.

**BR-002** Ett genomförande ska bevara frågornas struktur så som den såg ut när genomförandet skapades/startades.

**BR-003** Ett anonymt svar får inte kräva att en direkt personidentifierare lagras tillsammans med svaret.

**BR-004** "Pågående deltagare" är ett tekniskt närvaromått och får inte presenteras som ett exakt antal fysiska personer.

**BR-005** Dubblettskyddet i standardläget är ett användbarhetsstöd, inte identitetskontroll.

**BR-006** En strukturerad fråga ska aggregeras utan att administratören behöver öppna enskilda deltagares svar.

**BR-007** Historiska resultat ska inte förändras av att ursprunglig enkätmall senare redigeras.

**BR-008** Ett passerat slutdatum ska funktionellt motsvara att genomförandet är stängt för nya svar.

## 7. Informationsbehov

Systemet behöver minst hantera följande informationsobjekt:

- Administratör
- Enkätmall
- Fråga
- Svarsalternativ
- Enkätgenomförande
- Anonym deltagarsession
- Svar
- Inlämning/status
- Exportmetadata

Deltagarsessionens tekniska ID ska inte i sig bära information om deltagarens identitet.

## 8. Integrationer

MVP kräver inga externa verksamhetsintegrationer.

QR-kod ska genereras av systemet eller av en lokalt inkluderad komponent och får inte kräva att enkätslänken skickas till en extern QR-tjänst.

E-postdistribution ligger utanför MVP; administratören kan kopiera länken och använda valfri befintlig kommunikationskanal.

## 9. Behörighet

**AUTH-001** Administrativa funktioner ska kräva autentisering.

**AUTH-002** En administratör ska bara kunna hantera enkäter och resultat som denne har behörighet till.

**AUTH-003** Deltagarfunktioner ska inte kräva autentisering.

**AUTH-004** Publika deltagarlänkar ska bara ge åtkomst till funktioner som krävs för att besvara det aktuella genomförandet.

**AUTH-005** Resultat och fritextsvar ska inte vara publikt åtkomliga via deltagarlänken.

Första versionen behöver inte definiera avancerade organisationsroller; det kan införas när fleradministratörsstöd blir aktuellt.

## 10. Fel- och undantagsfall

- Ogiltig kortkod: visa tydligt att ingen aktiv enkät hittades.
- Ej öppnad enkät: visa när den blir tillgänglig om öppningstid finns.
- Stängd enkät: informera om att svarstiden har gått ut.
- Förlorad nätanslutning under besvarande: behåll lokalt/serversparat arbete så långt möjligt och ge tydlig status.
- Samtidig ändring av enkätmall: systemet ska undvika att ett pågående genomförande får inkonsistent frågestruktur.
- Ogiltig importfil: import ska avvisas utan partiellt skapad enkät.
- Dubbel inlämning av samma deltagarsession: ska vara idempotent eller avvisas utan dubblerat resultat.
- Liveanslutning bryts: resultatvyn ska kunna återansluta utan att data går förlorad.
- Administratören stänger enkäten medan deltagaren svarar: systemet ska ha en tydlig och konsekvent regel; i MVP rekommenderas att redan påbörjade deltagarsessioner får slutföra inom en kort definierad respit eller att stängningen uttryckligen anger beteendet. Detta fastställs i arkitektur/designsteget.

## 11. Icke-funktionella krav

**NFR-001 [Must] – Responsivitet**  
Deltagar- och administratörsgränssnitt ska vara användbara på moderna telefoner, surfplattor och datorer.

**NFR-002 [Must] – Enkelhet**  
Deltagaren ska inte behöva registrera sig, logga in eller navigera genom funktioner som inte krävs för att besvara enkäten.

**NFR-003 [Must] – Workshopstart**  
En administratör med en färdig enkät ska kunna skapa/starta ett genomförande och få fram deltagarinformation utan teknisk konfiguration.

**NFR-004 [Must] – Liveuppdatering**  
Under normala driftförhållanden ska nya status- och resultatdata bli synliga i administratörens livevy inom några sekunder.

**NFR-005 [Must] – Dataintegritet**  
Ett svar får inte räknas flera gånger på grund av omladdning, återförsök eller duplicerad nätverksbegäran.

**NFR-006 [Must] – Anonymitet**  
Systemets normala enkätflöde ska inte kräva direkt personidentifierande deltagardata.

**NFR-007 [Must] – Säker transport**  
Produktionsdrift ska använda HTTPS.

**NFR-008 [Must] – Åtkomstskydd**  
Administrativa API:er och resultat ska skyddas server-side och inte enbart genom UI-begränsningar.

**NFR-009 [Must] – Exporterbarhet**  
Exportformat ska vara dokumenterade, portabla och inte kräva den ursprungliga installationen för att kunna läsas.

**NFR-010 [Must] – Tillgänglighet**  
Grundläggande deltagarflöde ska kunna användas med tangentbord och semantiskt tillgängliga formulärkontroller; färg får inte vara enda bärare av betydelse.

**NFR-011 [Should] – Återhämtning**  
Tillfälligt avbrott i liveuppdatering ska inte kräva att administratören startar om genomförandet.

**NFR-012 [Should] – Prestanda**  
MVP ska dimensioneras för typiska workshops med minst tiotals samtidiga deltagare utan märkbar försämring av svarsflödet.

**NFR-013 [Must] – Dataminimering**  
Loggning och teknisk telemetri ska utformas så att onödig identifierande deltagarinformation inte samlas in.

## 12. Acceptance criteria

**AC-001** Givet en administratör med en enkätmall, när ett nytt genomförande startas, då visas en fungerande direktlänk, QR-kod och kort kod som alla leder till samma genomförande.

**AC-002** Givet en deltagare utan konto, när deltagaren öppnar en aktiv enkät, då kan deltagaren besvara och skicka in enkäten utan autentisering.

**AC-003** Givet en enkät med alla fem frågetyper, när deltagaren svarar via en vanlig mobilskärm, då är samtliga frågor möjliga att besvara utan desktop-specifik funktion.

**AC-004** Givet en obligatorisk fråga utan svar, när deltagaren försöker skicka in enkäten, då skickas inget slutligt svar och deltagaren får begriplig vägledning.

**AC-005** Givet ett inskickat svar, när samma klient återförsöker samma inlämning, då skapas inte ytterligare ett räknat svar.

**AC-006** Givet ett öppet genomförande med aktiva deltagare, när deltagare börjar och slutför enkäten, då uppdateras administratörens statusvy automatiskt inom några sekunder.

**AC-007** Givet svar på en ja/nej-, enkelvals-, flervals- eller skalfråga, när administratören öppnar resultatvyn, då visas en grafisk aggregering och antal svar som ligger till grund för resultatet.

**AC-008** Givet fritextsvar, när administratören öppnar resultatvyn, då visas svaren läsbart i en lista.

**AC-009** Givet ett genomförande med resultat, när presentationsläget öppnas, då visas en ren vy lämpad för stor skärm utan normala redigeringskontroller.

**AC-010** Givet en exporterad enkätdefinition, när filen importeras i en kompatibel installation, då återställs enkätens frågor, typer, ordning, obligatoriskhet och svarsalternativ.

**AC-011** Givet ett genomförande med svar, när CSV- respektive JSON-export görs, då innehåller exporten tillräcklig struktur för extern analys utan beroende av tjänstens UI.

**AC-012** Givet ett framtida öppningsdatum, när en deltagare försöker svara före öppning, då accepteras inget svar och korrekt information visas.

**AC-013** Givet ett passerat slutdatum, när en ny deltagare försöker svara, då accepteras inget nytt svar.

**AC-014** Givet att en enkätmall har använts i ett genomförande, när mallen senare ändras, då förändras inte det historiska genomförandets frågor eller resultat.

**AC-015** Givet en redan påbörjad deltagarsession, när samma webbläsare återgår till genomförandet, då återupptas sessionen i stället för att en ny räknad deltagare skapas under normala förhållanden.

## 13. Out of scope för MVP

- Identifierade deltagarkonton.
- Garanti om exakt ett svar per fysisk person.
- E-postutskick från tjänsten.
- Avancerade organisationer, team och behörighetsmodeller.
- Villkorslogik mellan frågor.
- Frågebank.
- AI-analys av svar.
- Automatisk statistisk inferens eller avancerad analys.
- Integrationer med externa mötes- och samarbetsplattformar.
- Anpassade varumärkesteman.
- Offline-first drift utan serveranslutning.

## 14. Öppna frågor

Följande frågor blockerar inte funktionell baslinje men ska beslutas under risk-/arkitekturfasen:

1. Hur länge räknas en deltagarsession som "pågående" efter senaste aktivitet?
2. Vad händer med en redan påbörjad deltagarsession om administratören stänger genomförandet?
3. Ska resultat kunna visas för deltagarna efter egen inlämning, eller bara för administratören/presentationsvyn?
4. Hur lång retention ska standardmässigt gälla för resultat?
5. Vilken autentiseringslösning ska användas för administratörer?
6. Ska första driftprofilen vara lokal/Docker, generell containerdrift eller exempelvis Coolify?
7. Vilken praktisk samtidighetsnivå ska användas som verifieringsmål utöver minimikravet för workshopbruk?

## 15. Spårbar MVP-definition

MVP:n anses funktionellt komplett när:

- FR-001–005
- FR-010–016
- FR-020–025
- FR-030–034
- FR-040–045
- FR-050–054
- FR-060–063
- FR-070–076
- FR-080–082
- FR-090–095
- AUTH-001–005
- NFR-001–010 och NFR-013
- AC-001–015

är implementerade och verifierade, med eventuella uttryckliga avvikelser dokumenterade.
