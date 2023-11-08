package no.nav.klage.oppgave.clients.pdl.graphql

data class HentIdenterResponse(val data: HentIdenter)

data class HentIdenter(val hentIdenter: Identer)

data class Identer(val identer: List<Ident>)

data class Ident(val ident: String, val historisk: Boolean, val gruppe: Gruppe)

enum class Gruppe {
    FOLKEREGISTERIDENT, AKTORID, NPID
}


