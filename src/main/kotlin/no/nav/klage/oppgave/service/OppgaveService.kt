package no.nav.klage.oppgave.service

import no.nav.klage.oppgave.clients.gosys.Oppgave
import no.nav.klage.oppgave.clients.gosys.OppgaveClient
import no.nav.klage.oppgave.util.getLogger
import org.springframework.stereotype.Service

@Service
//TODO: Fjern denne og bakenforliggende greier
class OppgaveService(val oppgaveClient: OppgaveClient) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun assignOppgave(oppgaveId: Long, saksbehandlerIdent: String?) {
        val endreOppgave = oppgaveClient.getOppgave(oppgaveId).toEndreOppgave()
        logger.info(
            "Endrer tilordnetRessurs for oppgave {} fra {} til {}",
            endreOppgave.id,
            endreOppgave.tilordnetRessurs,
            saksbehandlerIdent
        )
        endreOppgave.apply {
            tilordnetRessurs = saksbehandlerIdent
        }

        oppgaveClient.putOppgave(oppgaveId, endreOppgave)
    }

    fun getOppgave(oppgaveId: Long): Oppgave {
        return oppgaveClient.getOppgave(oppgaveId)
    }


//    fun setHjemmel(oppgaveId: Long, hjemmel: String, oppgaveVersjon: Int?): OppgaveView {
//        val oppgave = oppgaveRepository.getOppgave(oppgaveId).toEndreOppgave()
//        oppgave.apply {
//            setHjemmel(hjemmel)
//            versjon = oppgaveVersjon
//        }
//
//        return updateAndReturn(oppgaveId, oppgave)
//    }
//
//    private fun EndreOppgave.setHjemmel(hjemmel: String) {
//        if (metadata == null) {
//            metadata = mutableMapOf()
//        }
//        logger.info("Endrer hjemmel for oppgave {} fra {} til {}", id, metadata?.get(HJEMMEL), hjemmel)
//        metadata!![HJEMMEL] = hjemmel
//    }
//

}