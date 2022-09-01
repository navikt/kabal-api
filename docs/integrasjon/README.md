# Integrere med Kabal

## Oversikt

Diagram som beskriver grunnleggende klageflyt fra innsendt klage via nav.no:
![](klage_teknisk.png "Dataflyt")

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
POST <kabal-api-url>/oversendelse/klage <oversendt-klage-json>
```

- DEV-url: `https://kabal-api.dev.intern.nav.no`
- PROD-url: `https://kabal-api.intern.nav.no`

[OpenAPI/Swagger doc for å sende over klager](https://kabal-api.dev.intern.nav.no/swagger-ui/index.html?urls.primaryName=external) (
husk naisdevice)

Eksempel

````
{
  "avsenderEnhet": "2312",
  "avsenderSaksbehandlerIdent": "W123456",
  "dvhReferanse": "687686978",
  "fagsak": {
    "fagsakId": "123455",
    "fagsystem": "K9"
  },
  "frist": "2021-04-26",
  "hjemler": [
    {
      "kapittel": 9,
      "lov": "FOLKETRYGDLOVEN",
      "paragraf": 1
    }
  ],
  "innsendtTilNav": "2021-04-26",
  "innsynUrl": "https://k9-sak.adeo.no/behandling/12345678",
  "kilde": "K9",
  "kildeReferanse": "687687",
  "klager": {
    "id": {
      "type": "PERSON / VIRKSOMHET",
      "verdi": "12345678910"
    },
    "klagersProsessfullmektig": {
      "id": {
        "type": "PERSON / VIRKSOMHET",
        "verdi": "12345678910"
      },
      "skalKlagerMottaKopi": true
    }
  },
  "kommentar": "string",
  "mottattVedtaksinstans": "2021-04-26",
  "oversendtEnhet": "4219",
  "sakenGjelder": {
    "id": {
      "type": "PERSON",
      "verdi": "12345678910"
    },
    "skalMottaKopi": true
  },
  "ytelse": "OMS_OMP",
  "tilknyttedeJournalposter": [
    {
      "journalpostId": "830498203",
      "type": "BRUKERS_KLAGE"
    }
  ],
  "type": "KLAGE"
}
````

### Motta resultat/vedtak fra KA på Kafka

[Skjema for klage-vedtak sendt på `klage.vedtak-fattet.v1`](../schema/klagevedtak-fattet.json).

Eksempel

```
{
    "eventId": "b79cae6f-4afc-4f51-9e0b-623bb53f1805"
    "kildeReferanse": "23da11e6-8130-4edc-acee-2eb2a5fd4d97"
    "kilde": "AO01"
    "utfall": "MEDHOLD"
    "vedtaksbrevReferanse": "510857598"
    "kabalReferanse": "af4204bb-7f2c-4354-8af9-b279a5492104"
}
```
