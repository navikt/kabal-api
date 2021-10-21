package no.nav.klage.oppgave.clients.kabaldocument

import no.nav.klage.dokument.api.input.BrevMottakerInput
import no.nav.klage.dokument.api.input.DokumentEnhetInput
import no.nav.klage.dokument.api.input.JournalfoeringDataInput
import no.nav.klage.dokument.api.input.PartIdInput
import no.nav.klage.oppgave.clients.ereg.EregClient
import no.nav.klage.oppgave.clients.kabaldocument.model.request.TilleggsopplysningInput
import no.nav.klage.oppgave.clients.pdl.PdlFacade
import no.nav.klage.oppgave.domain.klage.*
import no.nav.klage.oppgave.domain.kodeverk.PartIdType
import no.nav.klage.oppgave.domain.kodeverk.Rolle
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import org.springframework.stereotype.Service

@Service
class KabalDocumentMapper(
    private val pdlFacade: PdlFacade,
    private val eregClient: EregClient
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()

        private const val BREV_TITTEL = "Brev fra Klageinstans"
        private const val BREVKODE = "BREV_FRA_KLAGEINSTANS"
        private const val BEHANDLINGSTEMA_KLAGE_KLAGEINSTANS = "ab0164"
        private const val KLAGEBEHANDLING_ID_KEY = "klagebehandling_id"
    }

    fun mapKlagebehandlingToDokumentEnhetInput(
        klagebehandling: Klagebehandling
    ): DokumentEnhetInput {
        return DokumentEnhetInput(
            brevMottakere = mapBrevMottakere(klagebehandling),
            journalfoeringData = JournalfoeringDataInput(
                sakenGjelder = PartIdInput(
                    type = klagebehandling.sakenGjelder.partId.type.name,
                    value = klagebehandling.sakenGjelder.partId.value
                ),
                tema = klagebehandling.tema.name,
                sakFagsakId = klagebehandling.sakFagsakId,
                sakFagsystem = klagebehandling.sakFagsystem?.name,
                kildeReferanse = klagebehandling.id.toString(),
                enhet = klagebehandling.tildeling!!.enhet!!,
                behandlingstema = BEHANDLINGSTEMA_KLAGE_KLAGEINSTANS,
                tittel = BREV_TITTEL,
                brevKode = BREVKODE,
                tilleggsopplysning = TilleggsopplysningInput(KLAGEBEHANDLING_ID_KEY, klagebehandling.id.toString())
            )
        )
    }

    private fun mapBrevMottakere(klagebehandling: Klagebehandling): List<BrevMottakerInput> {
        val brevMottakere = mutableListOf<BrevMottakerInput>()
        if (klagebehandling.klager.prosessfullmektig != null) {
            brevMottakere.add(mapProsessfullmektig(klagebehandling.klager.prosessfullmektig!!))
            if (klagebehandling.klager.prosessfullmektig!!.skalPartenMottaKopi) {
                brevMottakere.add(mapKlager(klagebehandling.klager))
            }
        } else {
            brevMottakere.add(mapKlager(klagebehandling.klager))
        }
        if (klagebehandling.sakenGjelder.partId != klagebehandling.klager.partId && klagebehandling.sakenGjelder.skalMottaKopi) {
            brevMottakere.add(mapSakenGjeder(klagebehandling.sakenGjelder))
        }
        return brevMottakere
    }

    private fun mapKlager(klager: Klager) =
        BrevMottakerInput(
            partId = mapPartId(klager.partId),
            navn = getNavn(klager.partId),
            rolle = Rolle.KLAGER.name
        )

    private fun mapSakenGjeder(sakenGjelder: SakenGjelder) =
        BrevMottakerInput(
            partId = mapPartId(sakenGjelder.partId),
            navn = getNavn(sakenGjelder.partId),
            rolle = Rolle.SAKEN_GJELDER.name
        )

    private fun mapProsessfullmektig(prosessfullmektig: Prosessfullmektig) =
        BrevMottakerInput(
            partId = mapPartId(prosessfullmektig.partId),
            navn = getNavn(prosessfullmektig.partId),
            rolle = Rolle.PROSESSFULLMEKTIG.name
        )

    private fun mapPartId(partId: PartId): PartIdInput =
        PartIdInput(
            type = partId.type.name,
            value = partId.value
        )

    private fun getNavn(partId: PartId): String? =
        if (partId.type == PartIdType.PERSON) {
            pdlFacade.getPersonInfo(partId.value).settSammenNavn()
        } else {
            eregClient.hentOrganisasjon(partId.value)?.navn?.navnelinje1
        }
}