# Integrere med Kabal

## Oversikt

Diagram som beskriver grunnleggende klageflyt fra innsendt klage via nav.no:
![](klage_teknisk.png "Dataflyt")
NB. klage.vedtak-fattet.v1 er nå erstattet med klage.behandling-events.v1

- Førsteinstans håndterer klage i sin vedtaksløsning
- Dersom klage skal videre til klageinstans gjøres en POST til `kabal-api`
- Når klage er ferdig behandlet av klageinstansen (KA), legges en melding med resultatet på et kafka-topic

## Oversende klage til Kabal

### Azure

Vedtaksapplikasjonen må være en "azure-app" (i nais.yaml):

```
azure:
  application:
    enabled: true
```

`kabal-api` må konfigurere tilgang til vedtaksappen. Derfor trenger klage-teamet info om: `cluster`, `namespace`
og `appnavn` i Kubernetes.

Kall mot kabal-api må inneholde et token med scope:

```
api://{cluster}.klage.kabal-api/.default
```

Dokumentasjon fra nais: https://security.labs.nais.io/pages/guide/api-kall/maskin_til_maskin_uten_bruker.html

### Oversendelse til kabal-api

```
POST <kabal-api-url>/oversendelse/v3/sak <oversendt-klage-json>
```

- DEV-url: `https://kabal-api.intern.dev.nav.no`
- PROD-url: `https://kabal-api.intern.nav.no`

[OpenAPI/Swagger doc for å sende over klager](https://kabal-api.intern.dev.nav.no/swagger-ui/index.html?urls.primaryName=external) (
husk naisdevice)

### Motta resultater og infomeldinger fra KA på Kafka

[Skjema for klage-vedtak sendt på `behandling-events.v1`](../schema/behandling-events.json).
