package no.nav.klage.oppgave.clients.kabaldocument

import no.nav.klage.kodeverk.PartIdType
import no.nav.klage.oppgave.clients.ereg.EregClient
import no.nav.klage.oppgave.clients.kabaldocument.model.Rolle
import no.nav.klage.oppgave.clients.kabaldocument.model.request.*
import no.nav.klage.oppgave.clients.pdl.PdlFacade
import no.nav.klage.oppgave.domain.klage.*
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
                    partIdTypeId = klagebehandling.sakenGjelder.partId.type.id,
                    value = klagebehandling.sakenGjelder.partId.value
                ),
                temaId = klagebehandling.ytelse.toTema().id,
                sakFagsakId = klagebehandling.sakFagsakId,
                sakFagsystemId = klagebehandling.sakFagsystem?.id,
                kildeReferanse = klagebehandling.id.toString(),
                enhet = klagebehandling.tildeling!!.enhet!!,
                behandlingstema = BEHANDLINGSTEMA_KLAGE_KLAGEINSTANS,
                tittel = BREV_TITTEL,
                brevKode = BREVKODE,
                tilleggsopplysning = TilleggsopplysningInput(
                    key = KLAGEBEHANDLING_ID_KEY,
                    value = klagebehandling.id.toString()
                )
            )
        )
    }

    fun mapBrevMottakere(klagebehandling: Klagebehandling): List<BrevMottakerInput> {
        val brevMottakere = mutableListOf<BrevMottakerInput>()
        if (klagebehandling.klager.prosessfullmektig != null) {
            brevMottakere.add(
                mapProsessfullmektig(
                    klagebehandling.klager.prosessfullmektig!!,
                    Rolle.HOVEDADRESSAT.name
                )
            )
            if (klagebehandling.klager.prosessfullmektig!!.skalPartenMottaKopi) {
                brevMottakere.add(mapKlager(klagebehandling.klager, Rolle.KOPIADRESSAT.name))
            }
        } else {
            brevMottakere.add(mapKlager(klagebehandling.klager, Rolle.HOVEDADRESSAT.name))
        }
        if (klagebehandling.sakenGjelder.partId != klagebehandling.klager.partId && klagebehandling.sakenGjelder.skalMottaKopi) {
            brevMottakere.add(mapSakenGjeder(klagebehandling.sakenGjelder, Rolle.KOPIADRESSAT.name))
        }

        return brevMottakere
    }

    private fun mapKlager(klager: Klager, rolle: String) =
        BrevMottakerInput(
            partId = mapPartId(klager.partId),
            navn = getNavn(klager.partId),
            rolle = rolle
        )

    private fun mapSakenGjeder(sakenGjelder: SakenGjelder, rolle: String) =
        BrevMottakerInput(
            partId = mapPartId(sakenGjelder.partId),
            navn = getNavn(sakenGjelder.partId),
            rolle = rolle
        )

    private fun mapProsessfullmektig(prosessfullmektig: Prosessfullmektig, rolle: String) =
        BrevMottakerInput(
            partId = mapPartId(prosessfullmektig.partId),
            navn = getNavn(prosessfullmektig.partId),
            rolle = rolle
        )


    private fun mapPartId(partId: PartId): PartIdInput =
        PartIdInput(
            partIdTypeId = partId.type.id,
            value = partId.value
        )

    private fun getNavn(partId: PartId): String? =
        if (partId.type == PartIdType.PERSON) {
            pdlFacade.getPersonInfo(partId.value).settSammenNavn()
        } else {
            eregClient.hentOrganisasjon(partId.value)?.navn?.navnelinje1
        }
}