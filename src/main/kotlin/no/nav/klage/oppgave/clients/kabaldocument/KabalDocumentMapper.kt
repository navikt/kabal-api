package no.nav.klage.oppgave.clients.kabaldocument

import no.nav.klage.dokument.domain.dokumenterunderarbeid.*
import no.nav.klage.kodeverk.DokumentType
import no.nav.klage.kodeverk.PartIdType
import no.nav.klage.oppgave.clients.ereg.EregClient
import no.nav.klage.oppgave.clients.kabaldocument.model.request.*
import no.nav.klage.oppgave.clients.pdl.PdlFacade
import no.nav.klage.oppgave.domain.klage.Behandling
import no.nav.klage.oppgave.domain.klage.PartId
import no.nav.klage.oppgave.util.DokumentUnderArbeidTitleComparator
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getPartIdFromIdentifikator
import no.nav.klage.oppgave.util.getSecureLogger
import org.springframework.stereotype.Service

@Service
class KabalDocumentMapper(
    private val pdlFacade: PdlFacade,
    private val eregClient: EregClient,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()

        private const val BREVKODE_BREV = "BREV_FRA_KLAGEINSTANS"
        private const val BREVKODE_NOTAT = "NOTAT_FRA_KLAGEINSTANS"
        private const val BEHANDLINGSTEMA_KLAGE_KLAGEINSTANS = "ab0164"
        private const val KLAGEBEHANDLING_ID_KEY = "klagebehandling_id"
        private const val BREVKODE_KJENNELSE_FRA_TR = "NAV96-01.01"
        private const val BREVKODE_ANNET = "NAV 00-03.00"
    }

    fun mapBehandlingToDokumentEnhetWithDokumentreferanser(
        behandling: Behandling,
        hovedDokument: DokumentUnderArbeidAsHoveddokument,
        vedlegg: Set<DokumentUnderArbeidAsVedlegg>,
        innholdsfortegnelse: Innholdsfortegnelse?,
    ): DokumentEnhetWithDokumentreferanserInput {
        val innholdsfortegnelseDocument = if (innholdsfortegnelse != null && vedlegg.isNotEmpty()) {
            DokumentEnhetWithDokumentreferanserInput.DokumentInput.Dokument(
                mellomlagerId = innholdsfortegnelse.mellomlagerId!!,
                name = "Vedleggsoversikt",
                sourceReference = null,
            )
        } else null

        val vedleggMapped = if (hovedDokument.isInngaaende()) {
            vedlegg.filter { it !is JournalfoertDokumentUnderArbeidAsVedlegg }
                .sortedWith(DokumentUnderArbeidTitleComparator())
                .map { currentVedlegg ->
                    mapDokumentUnderArbeidToDokumentReferanse(
                        dokument = currentVedlegg,
                    )
                }.toMutableList()
        } else {
            vedlegg.filter { it !is JournalfoertDokumentUnderArbeidAsVedlegg }
                .sortedByDescending { it.created }
                .map { currentVedlegg ->
                    mapDokumentUnderArbeidToDokumentReferanse(
                        dokument = currentVedlegg,
                    )
                }.toMutableList()
        }
        if (innholdsfortegnelseDocument != null) {
            vedleggMapped.add(0, innholdsfortegnelseDocument)
        }

        val journalfoerteVedlegg =
            vedlegg.filterIsInstance<JournalfoertDokumentUnderArbeidAsVedlegg>()
                .sortedByDescending { it.sortKey }

        val datoMottatt = if (hovedDokument.isInngaaende()) {
            hovedDokument as OpplastetDokumentUnderArbeidAsHoveddokument
            hovedDokument.datoMottatt
        } else null

        val inngaaendeKanal = when (hovedDokument.dokumentType) {
            DokumentType.KJENNELSE_FRA_TRYGDERETTEN -> Kanal.ALTINN_INNBOKS
            DokumentType.ANNEN_INNGAAENDE_POST -> {
                hovedDokument as OpplastetDokumentUnderArbeidAsHoveddokument
                Kanal.valueOf(hovedDokument.inngaaendeKanal.toString())
            }
            DokumentType.BREV, DokumentType.NOTAT, DokumentType.VEDTAK, DokumentType.BESLUTNING -> null
        }

        return DokumentEnhetWithDokumentreferanserInput(
            brevMottakere = mapAvsenderMottakerInfoSetToAvsenderMottakerInput(
                behandling = behandling,
                avsenderMottakerInfoSet = hovedDokument.avsenderMottakerInfoSet,
                dokumentType = hovedDokument.dokumentType
            ),
            journalfoeringData = JournalfoeringDataInput(
                sakenGjelder = PartIdInput(
                    partIdTypeId = behandling.sakenGjelder.partId.type.id,
                    value = behandling.sakenGjelder.partId.value
                ),
                temaId = behandling.ytelse.toTema().id,
                sakFagsakId = behandling.fagsakId,
                sakFagsystemId = behandling.fagsystem.id,
                kildeReferanse = behandling.id.toString(),
                //TODO: Fjerne behandling.tildeling når på plass.
                enhet = hovedDokument.journalfoerendeEnhetId ?: behandling.tildeling!!.enhet!!,
                behandlingstema = BEHANDLINGSTEMA_KLAGE_KLAGEINSTANS,
                //Tittel gjelder journalposten, ikke selve dokumentet som lastes opp. Vises i Gosys.
                tittel = hovedDokument.dokumentType.beskrivelse,
                brevKode = getBrevkode(hovedDokument),
                tilleggsopplysning = TilleggsopplysningInput(
                    key = KLAGEBEHANDLING_ID_KEY,
                    value = behandling.id.toString()
                ),
                inngaaendeKanal = inngaaendeKanal,
                datoMottatt = datoMottatt,
            ),
            dokumentreferanser = DokumentEnhetWithDokumentreferanserInput.DokumentInput(
                hoveddokument = mapDokumentUnderArbeidToDokumentReferanse(hovedDokument),
                vedlegg = vedleggMapped,
                journalfoerteVedlegg = journalfoerteVedlegg
                    .map { currentVedlegg ->
                        DokumentEnhetWithDokumentreferanserInput.DokumentInput.JournalfoertDokument(
                            kildeJournalpostId = currentVedlegg.journalpostId,
                            dokumentInfoId = currentVedlegg.dokumentInfoId,
                        )
                    },
            ),
            dokumentTypeId = hovedDokument.dokumentType.id,
            journalfoerendeSaksbehandlerIdent = hovedDokument.markertFerdigBy!!
        )
    }

    private fun getBrevkode(hovedDokument: DokumentUnderArbeidAsHoveddokument): String {
        return when(hovedDokument.dokumentType) {
            DokumentType.NOTAT -> BREVKODE_NOTAT
            DokumentType.KJENNELSE_FRA_TRYGDERETTEN -> BREVKODE_KJENNELSE_FRA_TR
            DokumentType.BESLUTNING, DokumentType.VEDTAK, DokumentType.BREV -> BREVKODE_BREV
            DokumentType.ANNEN_INNGAAENDE_POST -> BREVKODE_ANNET
        }
    }

    private fun mapDokumentUnderArbeidToDokumentReferanse(dokument: DokumentUnderArbeid): DokumentEnhetWithDokumentreferanserInput.DokumentInput.Dokument {
        if (dokument !is DokumentUnderArbeidAsMellomlagret) {
            error("Must be mellomlagret document")
        }
        return DokumentEnhetWithDokumentreferanserInput.DokumentInput.Dokument(
            mellomlagerId = dokument.mellomlagerId!!,
            name = dokument.name,
            sourceReference = dokument.id,
        )
    }

    private fun mapAvsenderMottakerInfoSetToAvsenderMottakerInput(
        behandling: Behandling,
        avsenderMottakerInfoSet: Set<DokumentUnderArbeidAvsenderMottakerInfo>?,
        dokumentType: DokumentType
    ): List<AvsenderMottakerInput> {
        return if (dokumentType == DokumentType.NOTAT) {
            listOf(mapPartIdToBrevmottakerInput(
                partId = behandling.sakenGjelder.partId,
                localPrint = false
            ))
        } else {
            avsenderMottakerInfoSet!!.map {
                mapPartIdToBrevmottakerInput(
                    partId = getPartIdFromIdentifikator(it.identifikator),
                    localPrint = it.localPrint,
                )
            }
        }
    }

    private fun mapPartIdToBrevmottakerInput(
        partId: PartId,
        localPrint: Boolean,
    ) =
        AvsenderMottakerInput(
            partId = mapPartId(partId),
            navn = getNavn(partId),
            localPrint = localPrint
        )


    private fun mapPartId(partId: PartId): PartIdInput =
        PartIdInput(
            partIdTypeId = partId.type.id,
            value = partId.value
        )

    private fun getNavn(partId: PartId): String =
        if (partId.type == PartIdType.PERSON) {
            pdlFacade.getPersonInfo(partId.value).settSammenNavn()
        } else {
            eregClient.hentOrganisasjon(partId.value).navn.sammensattnavn
        }

}