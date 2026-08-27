# kabal-api
API for klagebehandling i klageinstansen.

##Lokal kjøring av appen
Det er mulig å kjøre appen lokalt ved hjelp av docker-compose. For å sette opp database, kafka og elastic search kan du kjøre følgende i rotmappa:

```docker-compose up --build```

Deretter kan du sette opp kjøring av spring boot-appen i IntelliJ, presiser `local` som `active profile`.

# Linting and verification

This project uses ktlint and detekt for linting and static code analysis. See internal Confluence page for Team Klage for more info.

```
./gradlew ktlintFormat   # auto-fix formatting
./gradlew ktlintCheck    # verify formatting
./gradlew detektMain detektTest
```

detekt is scoped to the `NamedArguments` rule, which requires call sites with more
than one argument to name their arguments.
