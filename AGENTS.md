# Agentinstrukser for kabal-api

## Prosjektet

Kotlin/Spring Boot med Gradle Kotlin DSL og Java 25.
Hovedkode ligger i `src/main/kotlin/no/nav/klage/`.
Databaseendringer ligger i `src/main/resources/db/migration/`.

## Før du endrer kode

Les relevant kode og tester først.
Følg lagdelingsreglene i `LayeredArchitectureTest.kt`.
Gjenbruk eksisterende mønstre for klienter og integrasjoner.
Hold endringen avgrenset. Ikke overskriv andres lokale endringer.

## Database

Legg skjemaendringer i nye Flyway-migrasjoner.
Ikke endre migrasjoner som allerede er kjørt i delte miljøer.
Vurder eksisterende data og kompatibilitet ved skjemaendringer.

## Personvern og sikkerhet

Bruk syntetiske testdata.
Ikke legg personopplysninger, dokumentinnhold eller secrets i
prompter, logger eller nye testdata.
Bevar skillet mellom TokenX for `/api/innsyn/**` og Azure for
øvrige beskyttede endepunkter.
Avklar endringer i autentisering, autorisasjon og behandling
av personopplysninger før implementering.

## Verifisering

Kjør relevante tester for endringen først.
Integrasjonstester bruker Testcontainers og krever Docker.

CI bruker:

- `./gradlew ktlintCheck`
- `./gradlew detektMain detektTest`
- `./gradlew build`

Ikke svekk tester eller statiske kontroller for å få grønt bygg.
Rapporter hva som er endret, og hva som ikke kunne verifiseres.
