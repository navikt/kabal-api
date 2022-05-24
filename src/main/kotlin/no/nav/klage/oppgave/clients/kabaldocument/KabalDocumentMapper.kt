package no.nav.klage.oppgave.clients.kabaldocument

import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeid
import no.nav.klage.kodeverk.Brevmottakertype
import no.nav.klage.kodeverk.PartIdType
import no.nav.klage.oppgave.clients.ereg.EregClient
import no.nav.klage.oppgave.clients.kabaldocument.model.Rolle
import no.nav.klage.oppgave.clients.kabaldocument.model.request.*
import no.nav.klage.oppgave.clients.pdl.PdlFacade
import no.nav.klage.oppgave.domain.Behandling
import no.nav.klage.oppgave.domain.klage.Klager
import no.nav.klage.oppgave.domain.klage.PartId
import no.nav.klage.oppgave.domain.klage.Prosessfullmektig
import no.nav.klage.oppgave.domain.klage.SakenGjelder
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import org.springframework.stereotype.Service
import java.util.*

@Service
class KabalDocumentMapper(
    private val pdlFacade: PdlFacade,
    private val eregClient: EregClient
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()

        private const val BREVKODE = "BREV_FRA_KLAGEINSTANS"
        private const val BEHANDLINGSTEMA_KLAGE_KLAGEINSTANS = "ab0164"
        private const val KLAGEBEHANDLING_ID_KEY = "klagebehandling_id"
    }

    fun mapBehandlingToDokumentEnhetWithDokumentreferanser(
        behandling: Behandling,
        hovedDokument: DokumentUnderArbeid,
        vedlegg: SortedSet<DokumentUnderArbeid>
    ): DokumentEnhetWithDokumentreferanserInput {
        return DokumentEnhetWithDokumentreferanserInput(
            brevMottakere = mapBrevmottakere(behandling, hovedDokument.brevmottakertyper),
            journalfoeringData = JournalfoeringDataInput(
                sakenGjelder = PartIdInput(
                    partIdTypeId = behandling.sakenGjelder.partId.type.id,
                    value = behandling.sakenGjelder.partId.value
                ),
                temaId = behandling.ytelse.toTema().id,
                sakFagsakId = behandling.sakFagsakId,
                sakFagsystemId = behandling.sakFagsystem?.id,
                kildeReferanse = behandling.id.toString(),
                enhet = behandling.tildeling!!.enhet!!,
                behandlingstema = BEHANDLINGSTEMA_KLAGE_KLAGEINSTANS,
                //Tittel gjelder journalposten, ikke selve dokumentet som lastes opp. Vises i Gosys.
                tittel = hovedDokument.dokumentType.beskrivelse,
                brevKode = BREVKODE,
                tilleggsopplysning = TilleggsopplysningInput(
                    key = KLAGEBEHANDLING_ID_KEY,
                    value = behandling.id.toString()
                )
            ),
            dokumentreferanser = DokumentEnhetWithDokumentreferanserInput.DokumentInput(
                hoveddokument = mapDokumentUnderArbeidToDokumentReferanse(hovedDokument),
                vedlegg = vedlegg.map { mapDokumentUnderArbeidToDokumentReferanse(it) }
            ),
            dokumentTypeId = hovedDokument.dokumentType.id,
        )
    }

    private fun mapDokumentUnderArbeidToDokumentReferanse(dokument: DokumentUnderArbeid): DokumentEnhetWithDokumentreferanserInput.DokumentInput.Dokument {
        return DokumentEnhetWithDokumentreferanserInput.DokumentInput.Dokument(
            mellomlagerId = dokument.mellomlagerId,
            opplastet = dokument.opplastet,
            size = dokument.size,
            name = dokument.name,
        )
    }

    fun mapBrevmottakere(
        behandling: Behandling,
        brevMottakertyper: MutableSet<Brevmottakertype>
    ): List<BrevmottakerInput> {
        val brevmottakere = mutableListOf<BrevmottakerInput>()
        if (behandling.klager.prosessfullmektig != null) {
            if (brevMottakertyper.contains(Brevmottakertype.PROSESSFULLMEKTIG)) {
                brevmottakere.add(
                    mapProsessfullmektig(
                        behandling.klager.prosessfullmektig!!,
                        Rolle.HOVEDADRESSAT.name
                    )
                )
            }
            if (behandling.klager.prosessfullmektig!!.skalPartenMottaKopi &&
                brevMottakertyper.contains(Brevmottakertype.KLAGER)
            ) {
                brevmottakere.add(mapKlager(behandling.klager, Rolle.KOPIADRESSAT.name))
            }
        } else {
            if (brevMottakertyper.contains(Brevmottakertype.KLAGER)) {
                brevmottakere.add(mapKlager(behandling.klager, Rolle.HOVEDADRESSAT.name))
            }
        }
        if (behandling.sakenGjelder.partId != behandling.klager.partId &&
            behandling.sakenGjelder.skalMottaKopi &&
            brevMottakertyper.contains(Brevmottakertype.SAKEN_GJELDER)
        ) {
            brevmottakere.add(mapSakenGjeder(behandling.sakenGjelder, Rolle.KOPIADRESSAT.name))
        }

        return brevmottakere
    }

    private fun mapKlager(klager: Klager, rolle: String) =
        BrevmottakerInput(
            partId = mapPartId(klager.partId),
            navn = getNavn(klager.partId),
            rolle = rolle
        )

    private fun mapSakenGjeder(sakenGjelder: SakenGjelder, rolle: String) =
        BrevmottakerInput(
            partId = mapPartId(sakenGjelder.partId),
            navn = getNavn(sakenGjelder.partId),
            rolle = rolle
        )

    private fun mapProsessfullmektig(prosessfullmektig: Prosessfullmektig, rolle: String) =
        BrevmottakerInput(
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