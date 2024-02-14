package no.nav.klage.oppgave.service

import no.nav.klage.oppgave.clients.kodeverk.KodeverkClient
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import org.springframework.stereotype.Service

@Service
class KodeverkService(
    private val kodeverkClient: KodeverkClient
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
    }

    fun getPoststed(postnummer: String): Poststed {
        val postnummerKodeverk = kodeverkClient.getPoststeder()
        return Poststed(
            poststed = postnummerKodeverk.betydninger[postnummer]?.firstOrNull()?.beskrivelser?.get("NO")?.term
                ?: "Ukjent"
        )
    }

    fun getLandkoder(): List<Landkode> {
        val landkoderKodeverk = kodeverkClient.getLandkoder()
        return landkoderKodeverk.betydninger.map {
            Landkode(
                land = it.value.firstOrNull()?.beskrivelser?.get("NO")?.term ?: "Ukjent",
                landkode = it.key,
            )
        }
    }

    data class Landkode(
        val land: String,
        val landkode: String,
    )

    data class Poststed(
        val poststed: String,
    )
}