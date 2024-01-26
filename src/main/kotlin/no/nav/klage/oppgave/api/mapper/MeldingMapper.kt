package no.nav.klage.oppgave.api.mapper

import no.nav.klage.oppgave.api.view.MeldingModified
import no.nav.klage.oppgave.api.view.MeldingView
import no.nav.klage.oppgave.domain.klage.Melding
import no.nav.klage.oppgave.service.SaksbehandlerService
import org.springframework.stereotype.Service

@Service
class MeldingMapper(
    private val saksbehandlerService: SaksbehandlerService
) {

    fun toMeldingView(melding: Melding): MeldingView {
        return MeldingView(
            id = melding.id,
            text = melding.text,
            author = MeldingView.Author(
                navIdent = melding.saksbehandlerident,
                saksbehandlerIdent = melding.saksbehandlerident,
                navn = saksbehandlerService.getNameForIdent(melding.saksbehandlerident),
                name = saksbehandlerService.getNameForIdent(melding.saksbehandlerident),
            ),
            created = melding.created,
            modified = melding.modified
        )
    }

    fun toModifiedView(melding: Melding): MeldingModified {
        return MeldingModified(
            modified = melding.modified ?: throw RuntimeException("modified on melding not set")
        )
    }

    fun toMeldingerView(meldinger: List<Melding>): List<MeldingView> {
        return meldinger.map { melding ->
            MeldingView(
                id = melding.id,
                text = melding.text,
                author = MeldingView.Author(
                    saksbehandlerIdent = melding.saksbehandlerident,
                    navIdent = melding.saksbehandlerident,
                    name = saksbehandlerService.getNameForIdent(melding.saksbehandlerident),
                    navn = saksbehandlerService.getNameForIdent(melding.saksbehandlerident),
                ),
                created = melding.created,
                modified = melding.modified
            )
        }
    }
}