# RC-checklista – 0.1.0-rc.1

## Före merge

- [x] Backendtester passerar i CI.
- [x] Frontendtester passerar i CI.
- [x] Frontend production build passerar i CI.
- [x] Playwright E2E täcker huvudflödet.
- [x] Säkerhetsfilter och CSRF-flöde är verifierade i CI.
- [x] Databasmigrationer körs mot PostgreSQL i CI.
- [x] Versionsnummer är satt till 0.1.0-rc.1.
- [x] Release notes och changelog finns i repositoryt.
- [x] Kända begränsningar är dokumenterade.

## Efter merge till main

- [ ] Bekräfta att CI på main är grön.
- [ ] Bygg produktionscontainrar.
- [ ] Starta `compose.production.yaml` med riktiga secrets.
- [ ] Verifiera `/q/health/ready`.
- [ ] Verifiera administratörsinloggning via HTTPS.
- [ ] Skapa en enkät och starta ett workshopgenomförande.
- [ ] Anslut en deltagare via QR eller kortkod.
- [ ] Svara, submit:a och verifiera live-resultat.
- [ ] Öppna presentationsläge och verifiera read-only-token.
- [ ] Verifiera JSON-, CSV- och ZIP-export.
- [ ] Ta en databasbackup och verifiera att filen går att läsa med `pg_restore --list`.
- [ ] Verifiera SSE genom den faktiska reverse proxyn.
- [ ] Kontrollera att externa proxyheaders saneras enligt `docs/security.md`.
- [ ] Skapa taggen `v0.1.0-rc.1`.
- [ ] Publicera GitHub-releasen som prerelease.

## Stop-kriterier

Tagga inte RC:n om något av följande gäller:

- CI är inte helt grön.
- databas-migration eller startup misslyckas i målmiljön.
- admininloggning, deltagarsubmit eller resultatvisning inte fungerar i smoke test.
- HTTPS/proxy-konfiguration gör att CSRF-skydd eller SSE inte fungerar.
- exportpaket inte kan skapas eller öppnas.
