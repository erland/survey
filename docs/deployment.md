# Produktionsdrift

## Rekommenderad profil

Produktion kör tre containrar: `frontend` (Nginx + React), `backend` (Quarkus) och `postgres`. Endast frontendporten exponeras. En extern reverse proxy/ingress terminerar HTTPS och skickar trafik till frontendcontainern.

```bash
cp .env.production.example .env.production
# ändra lösenord och övriga värden
docker compose --env-file .env.production -f compose.production.yaml up -d --build
```

Kontroll:

```bash
docker compose --env-file .env.production -f compose.production.yaml ps
curl -f http://127.0.0.1:${APP_PORT:-8088}/healthz
```

## HTTPS och reverse proxy

Produktion ska exponeras via HTTPS. Reverse proxyn ska vidarebefordra `Host`, `X-Forwarded-Proto` och klientadress, men ska sanera inkommande `X-Forwarded-For`/`X-Real-IP` så klienten inte kan välja sin rate-limit-identitet.

Frontendcontainern proxar `/api/*` vidare till backend på samma origin. Detta förenklar cookies, CSRF-skydd och CORS.

## SSE

Livevyer använder Server-Sent Events. Den yttre reverse proxyn måste därför:

- tillåta långlivade HTTP/1.1- eller HTTP/2-anslutningar,
- inte buffra SSE-svar,
- ha en read timeout på minst flera minuter, gärna 1 timme,
- inte cacha `/api/**/events`.

Den medföljande Nginx-konfigurationen har `proxy_buffering off`, `proxy_cache off` och `proxy_read_timeout 1h` för backendtrafiken.

## Persistens

PostgreSQL-data ligger i Docker-volymen `survey-postgres-data`. Frontend och backend är stateless och kan byggas om utan dataförlust.

## Backup

Skapa en komprimerad PostgreSQL custom-format backup:

```bash
COMPOSE_FILE=compose.production.yaml ./scripts/backup-db.sh
```

Backupfiler hamnar normalt i `./backups` och bör kopieras till separat lagringsplats enligt verksamhetens backupkrav.

## Restore

Restore är en destruktiv driftåtgärd och bör göras under underhållsfönster. Stoppa applikationstrafik först och återställ sedan:

```bash
COMPOSE_FILE=compose.production.yaml ./scripts/restore-db.sh backups/survey-YYYYMMDDTHHMMSSZ.dump
```

Verifiera därefter readiness och ett administrativt smoke test.

## Uppgradering

1. Ta backup.
2. Hämta ny kod/image.
3. Kör `docker compose ... up -d --build`.
4. Quarkus kör Flyway-migrationer vid start.
5. Kontrollera backend readiness och frontend health.
6. Verifiera login, öppna en enkät och anslut en testdeltagare.

## Bootstrap-admin

`ADMIN_BOOTSTRAP_USER` och `ADMIN_BOOTSTRAP_PASSWORD` används bara för att skapa den första lokala administratören om användaren saknas. Efter första lyckade bootstrap bör värdena tas bort eller lämnas tomma i produktionsmiljön.

## Coolify

För Coolify finns en separat profil i `deploy/coolify/`. Den använder de versionerade GHCR-images som publiceras vid GitHub Release och ansluter till en separat gemensam PostgreSQL-resurs i Coolify i stället för att starta en egen databascontainer.

Se `deploy/coolify/README.md` för:
- `Connect To Predefined Network`,
- `DB_HOST`/databasinställningar,
- image-versionering via `APP_VERSION`,
- HTTPS/CSRF och SSE,
- uppgradering, rollback och backup.

## Podman

Arkitekturen är inte beroende av Docker. `compose.production.yaml` kan användas som grund i en Compose-kompatibel Podman-miljö. Säkerställ särskilt att den yttre proxyn har SSE-buffering avstängd och tillräcklig timeout.
