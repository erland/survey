# Coolify deployment

Den här profilen kör Survey i Coolify med publicerade GHCR-images och en separat, gemensam PostgreSQL-instans som hanteras som en egen Coolify-resurs.

## Målarkitektur

```text
Internet
  |
  v
HTTPS / Coolify / Traefik
  |
  v
frontend:80
  |
  +--> /api/* --> backend:8080
                     |
                     v
             shared PostgreSQL:5432
```

Endast `frontend` ska exponeras via Coolifys proxy. Backend och PostgreSQL ska vara interna.

## 1. Gemensam PostgreSQL

Använd den befintliga gemensamma PostgreSQL-resursen i Coolify. Skapa inte en PostgreSQL-service från den här Compose-profilen.

Skapa en dedikerad databas och roll för Survey. `bootstrap-db.sql` visar ett minimalt exempel.

Rekommenderade namn:

```text
database: survey
owner:    survey
```

Flyway skapar och uppgraderar applikationsschemat automatiskt när backend startar.

Exponera inte PostgreSQL-port 5432 publikt.

## 2. Skapa Coolify-resursen

Skapa ett Docker Compose-resource från detta Git-repository och välj:

```text
deploy/coolify/compose.yaml
```

Aktivera därefter i Coolifys advanced configuration:

```text
Connect To Predefined Network
```

Detta krävs för att Compose-stacken ska kunna kommunicera med den separat hanterade PostgreSQL-resursen. På en standardinstallation är destinationsnätet normalt `coolify`.

Lägg inte till Coolify-nätet manuellt i Compose-filen; låt Coolify hantera nätverksanslutningen.

## 3. Environment variables

Använd `deploy/coolify/env.example` som checklista och lägg värdena i Coolify.

Viktigast:

```text
APP_VERSION=0.1.0-rc.1
DB_HOST=<internt hostnamn/alias för gemensam PostgreSQL>
DB_NAME=survey
DB_USERNAME=survey
DB_PASSWORD=<unikt starkt lösenord>
```

`DB_HOST` ska vara det interna hostnamn eller alias som den gemensamma PostgreSQL-resursen har på Coolifys predefined network.

Backend bygger JDBC-URL:n som:

```text
jdbc:postgresql://DB_HOST:DB_PORT/DB_NAME
```

## 4. GHCR-images

Produktionsprofilen använder:

```text
ghcr.io/erland/survey-backend:<version>
ghcr.io/erland/survey-frontend:<version>
```

När en GitHub Release publiceras bygger release-workflowen versionerade images. En release med taggen `v0.1.0-rc.1` publicerar exempelvis:

```text
ghcr.io/erland/survey-backend:0.1.0-rc.1
ghcr.io/erland/survey-frontend:0.1.0-rc.1
```

Sätt därför `APP_VERSION` till exakt den version som ska köras.

Om GHCR-paketen inte är publika måste Coolify konfigureras med registry credentials som har rätt att läsa paketen.

## 5. Domain

Tilldela den publika domänen endast till tjänsten:

```text
frontend
```

Frontend-containern lyssnar på port 80. Compose-filen innehåller avsiktligt ingen host-`ports:` mapping; Coolify/Traefik ska ansluta direkt till containerporten.

Backend ska inte ha någon publik domän.

## 6. HTTPS, cookies och CSRF

Coolify/Traefik ska terminera HTTPS. Backend kör med:

```text
ADMIN_COOKIE_SECURE=true
```

Frontend skickar `/api/*` till backend på samma browser-origin, vilket krävs för det same-origin-baserade CSRF-skyddet.

Verifiera efter deployment att:

- admininloggning fungerar via HTTPS,
- skrivande adminanrop inte ger `CSRF_ORIGIN_INVALID`,
- `Host` och `X-Forwarded-Proto` förmedlas korrekt.

## 7. SSE

Live-resultat och presentationsläge använder Server-Sent Events.

Frontendens Nginx-konfiguration har buffering avstängd och lång read-timeout. Coolifys yttre proxy måste också tillåta långlivade anslutningar utan aggressiv buffering.

Verifiera särskilt liveuppdatering efter första deployment.

## 8. Bootstrap-admin

För första deployment kan följande sättas temporärt:

```text
ADMIN_BOOTSTRAP_USER=admin
ADMIN_BOOTSTRAP_PASSWORD=<starkt lösenord>
```

När administratören har skapats bör båda variablerna tas bort eller lämnas tomma.

## 9. Första deployment

Kontrollera innan Deploy:

- databasen `survey` och rollen `survey` finns,
- `Connect To Predefined Network` är aktiverat,
- `DB_HOST` går att resolva från Coolify-nätet,
- `DB_PASSWORD` är satt,
- rätt `APP_VERSION` finns i GHCR,
- eventuell GHCR-auth är konfigurerad,
- domänen är kopplad endast till `frontend`.

Förväntat tillstånd:

- `backend`: healthy
- `frontend`: healthy
- Flyway-migrationer: lyckade

## 10. Uppgradering och rollback

För uppgradering:

1. publicera en ny GitHub Release,
2. verifiera att båda GHCR-images har skapats,
3. ändra `APP_VERSION` i Coolify,
4. redeploy.

Rollback görs genom att sätta tillbaka föregående `APP_VERSION` och redeploya. Observera att databas-migrationer kan kräva restore om de inte är bakåtkompatibla.

## 11. Backup

Den här Coolify-profilen har ingen egen persistent applikationsvolym. All beständig applikationsdata ligger i den gemensamma PostgreSQL-instansen.

Konfigurera därför schemalagda PostgreSQL-backuper i Coolify för databasen:

```text
survey
```

helst till extern S3/R2-kompatibel lagring.
