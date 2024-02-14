package no.nav.klage.oppgave.service

import no.nav.klage.oppgave.clients.kodeverk.KodeverkClient
import no.nav.klage.oppgave.domain.kodeverk.LandInfo
import no.nav.klage.oppgave.domain.kodeverk.PostInfo
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

    fun getPoststed(postnummer: String): String {
        val postnummerKodeverk = kodeverkClient.getPoststeder()
        return postnummerKodeverk.betydninger[postnummer]?.firstOrNull()?.beskrivelser?.get("NO")?.term
            ?: "Ukjent"

    }

    fun getPostInfo(): List<PostInfo> {
        val postnummerKodeverk = kodeverkClient.getPoststeder()
        return postnummerKodeverk.betydninger.map {
            PostInfo(
                poststed = it.value.firstOrNull()?.beskrivelser?.get("NO")?.term ?: "Ukjent",
                postnummer = it.key,
            )
        }
    }

    fun getLandkoder(): List<LandInfo> {
        val landkoderKodeverk = kodeverkClient.getLandkoder()
        return landkoderKodeverk.betydninger.map {
            LandInfo(
                land = it.value.firstOrNull()?.beskrivelser?.get("NO")?.term ?: "Ukjent",
                landkode = it.key,
            )
        }
    }


    data class Poststed(
        val poststed: String,
    )
}