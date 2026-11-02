package no.nav.klage.oppgave.service

import no.nav.klage.kodeverk.Type
import no.nav.klage.oppgave.domain.behandling.Behandling
import no.nav.klage.oppgave.domain.mottak.Mottak
import no.nav.klage.oppgave.util.getLogger
import org.springframework.stereotype.Service

@Service
class CreateBehandlingFromMottak(
    private val klagebehandlingService: KlagebehandlingService,
    private val ankebehandlingService: AnkebehandlingService,
    private val omgjoeringskravbehandlingService: OmgjoeringskravbehandlingService,
    private val gjenopptaksbehandlingService: GjenopptaksbehandlingService,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun createBehandling(mottak: Mottak): Behandling {
        logger.debug("Received mottak in CreateBehandlingFromMottak")

        return when (mottak.type) {
            Type.KLAGE -> {
                klagebehandlingService.createKlagebehandlingFromMottak(
                    mottak = mottak,
                )
            }

            Type.ANKE_FOER_2027 -> {
                val ankebehandling =
                    ankebehandlingService.createAnkebehandlingFromMottak(
                        mottak = mottak,
                    )

                ankebehandling
            }

            Type.OMGJOERINGSKRAV -> {
                omgjoeringskravbehandlingService.createOmgjoeringskravbehandlingFromMottak(
                    mottak = mottak,
                )
            }

            Type.ANKE_I_TRYGDERETTEN_FOER_2027 -> {
                TODO()
            }

            Type.BEHANDLING_ETTER_TRYGDERETTEN_OPPHEVET -> {
                TODO()
            }

            Type.BEGJAERING_OM_GJENOPPTAK_FOER_2027 -> {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFoer2027FromMottak(
                    mottak = mottak,
                )
            }

            Type.BEGJAERING_OM_GJENOPPTAK_ETTER_2027 -> {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingEtter2027FromMottak(
                    mottak = mottak,
                )
            }

            Type.BEGJAERING_OM_GJENOPPTAK_I_TRYGDERETTEN_FOER_2027 -> {
                TODO()
            }

            Type.BEGJAERING_OM_GJENOPPTAK_I_TRYGDERETTEN_ETTER_2027 -> {
                TODO()
            }

            Type.ANKE_ETTER_2027 -> {
                val ankebehandling =
                    ankebehandlingService.createAnkebehandlingEtter2027FromMottak(
                        mottak = mottak,
                    )

                ankebehandling
            }

            Type.ANKE_I_TRYGDERETTEN_ETTER_2027 -> {
                TODO()
            }
        }
    }
}
