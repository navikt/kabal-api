package no.nav.klage.oppgave.clients.kabaldocument

import no.nav.klage.dokument.domain.dokumenterunderarbeid.Adresse
import no.nav.klage.dokument.domain.dokumenterunderarbeid.Brevmottaker
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeid
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeidAsHoveddokument
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeidAsMellomlagret
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeidAsVedlegg
import no.nav.klage.dokument.domain.dokumenterunderarbeid.Innholdsfortegnelse
import no.nav.klage.dokument.domain.dokumenterunderarbeid.JournalfoertDokumentUnderArbeidAsVedlegg
import no.nav.klage.dokument.domain.dokumenterunderarbeid.OpplastetDokumentUnderArbeidAsHoveddokument
import no.nav.klage.dokument.domain.dokumenterunderarbeid.SmartdokumentUnderArbeidAsHoveddokument
import no.nav.klage.dokument.service.DokumentUnderArbeidService.Companion.EKSPEDISJONSBREV_TIL_TR_TEMPLATE_NAME
import no.nav.klage.dokument.service.DokumentUnderArbeidService.Companion.ETTERSENDING_TIL_TR_TEMPLATE_NAME
import no.nav.klage.dokument.service.DokumentUnderArbeidService.Companion.GJENOPPTAKELSESBEGJAERING_EKSPEDISJONSBREV_TIL_TR_TEMPLATE_NAME
import no.nav.klage.dokument.service.DokumentUnderArbeidService.Companion.GJENOPPTAKELSESBEGJAERING_ETTERSENDING_TIL_TR_TEMPLATE_NAME
import no.nav.klage.kodeverk.DokumentType
import no.nav.klage.kodeverk.PartIdType
import no.nav.klage.oppgave.clients.ereg.EregClient
import no.nav.klage.oppgave.clients.kabaldocument.model.request.AvsenderMottakerInput
import no.nav.klage.oppgave.clients.kabaldocument.model.request.DokumentEnhetWithDokumentreferanserInput
import no.nav.klage.oppgave.clients.kabaldocument.model.request.JournalfoeringDataInput
import no.nav.klage.oppgave.clients.kabaldocument.model.request.Kanal
import no.nav.klage.oppgave.clients.kabaldocument.model.request.PartIdInput
import no.nav.klage.oppgave.clients.kabaldocument.model.request.TilleggsopplysningInput
import no.nav.klage.oppgave.clients.kabaldocument.model.request.TrygderettenMetadataInput
import no.nav.klage.oppgave.clients.klageunleashproxy.KlageUnleashProxyClient
import no.nav.klage.oppgave.domain.behandling.Behandling
import no.nav.klage.oppgave.domain.behandling.BehandlingWithTrygderettenMetadata
import no.nav.klage.oppgave.domain.behandling.GjenopptakITrygderettenbehandling
import no.nav.klage.oppgave.domain.behandling.Gjenopptaksbehandling
import no.nav.klage.oppgave.domain.behandling.embedded.PartId
import no.nav.klage.oppgave.service.BehandlingService
import no.nav.klage.oppgave.service.DokDistKanalService
import no.nav.klage.oppgave.service.PersonService
import no.nav.klage.oppgave.util.DokumentUnderArbeidTitleComparator
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getPartIdFromIdentifikator
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Service
class KabalDocumentMapper(
    private val personService: PersonService,
    private val eregClient: EregClient,
    private val dokDistKanalService: DokDistKanalService,
    private val behandlingService: BehandlingService,
    private val klageUnleashProxyClient: KlageUnleashProxyClient,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)

        private const val BREVKODE_BREV = "BREV_FRA_KLAGEINSTANS"
        private const val BREVKODE_NOTAT = "NOTAT_FRA_KLAGEINSTANS"
        private const val BEHANDLINGSTEMA_KLAGE_KLAGEINSTANS = "ab0164"
        private const val KLAGEBEHANDLING_ID_KEY = "klagebehandling_id"
        private const val BREVKODE_KJENNELSE_FRA_TR = "NAV96-01.01"
        private const val BREVKODE_ANNET = "NAV 00-03.00"
        private val DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd. MMM yyyy", Locale.of("nb", "NO")).withZone(ZoneId.of("Europe/Oslo"))

        private const val NAV_TR_V2_TOGGLE = "nav-tr-v2"
    }

    fun mapBehandlingToDokumentEnhetWithDokumentreferanser(
        behandling: Behandling,
        hovedDokument: DokumentUnderArbeidAsHoveddokument,
        vedlegg: Set<DokumentUnderArbeidAsVedlegg>,
        innholdsfortegnelse: Innholdsfortegnelse?,
    ): DokumentEnhetWithDokumentreferanserInput {
        val innholdsfortegnelseDocument =
            if (innholdsfortegnelse != null && vedlegg.isNotEmpty()) {
                val documentName = "Vedleggsoversikt til \"${hovedDokument.name}\", ${LocalDate.now().format(DATE_FORMAT)}"

                DokumentEnhetWithDokumentreferanserInput.DokumentInput.Dokument(
                    mellomlagerId = innholdsfortegnelse.mellomlagerId!!,
                    name = documentName,
                    sourceReference = null,
                )
            } else {
                null
            }

        // Both opplastede and smartdokumenter end up here, since they are all mellomlagret.
        val mellomlagredeVedlegg = vedlegg.filter { it !is JournalfoertDokumentUnderArbeidAsVedlegg }

        // Only documents uploaded through Kabin have an explicit order, and then all of them have it.
        val useExplicitSortIndex =
            mellomlagredeVedlegg.isNotEmpty() &&
                mellomlagredeVedlegg.all { it.sortIndex != null }

        val sortedVedlegg =
            when {
                useExplicitSortIndex -> mellomlagredeVedlegg.sortedBy { it.sortIndex }
                hovedDokument.isInngaaende() -> mellomlagredeVedlegg.sortedWith(DokumentUnderArbeidTitleComparator())
                else -> mellomlagredeVedlegg.sortedByDescending { it.created }
            }

        val vedleggMapped =
            sortedVedlegg
                .map { currentVedlegg ->
                    mapDokumentUnderArbeidToDokumentReferanse(dokument = currentVedlegg)
                }.toMutableList()

        if (innholdsfortegnelseDocument != null) {
            vedleggMapped.add(index = 0, element = innholdsfortegnelseDocument)
        }

        val journalfoerteVedlegg =
            vedlegg
                .filterIsInstance<JournalfoertDokumentUnderArbeidAsVedlegg>()
                .sortedBy { it.sortKey }
                .groupBy { it.dokumentInfoId }
                .map { it.value.first() }

        val datoMottatt =
            if (hovedDokument.isInngaaende()) {
                hovedDokument as OpplastetDokumentUnderArbeidAsHoveddokument
                hovedDokument.datoMottatt
            } else {
                null
            }

        val inngaaendeKanal =
            when (hovedDokument.dokumentType) {
                DokumentType.KJENNELSE_FRA_TRYGDERETTEN -> {
                    Kanal.ALTINN_INNBOKS
                }

                DokumentType.ANNEN_INNGAAENDE_POST -> {
                    hovedDokument as OpplastetDokumentUnderArbeidAsHoveddokument
                    if (hovedDokument.inngaaendeKanal == null) error("DokumentType requires inngaaendeKanal")
                    Kanal.valueOf(hovedDokument.inngaaendeKanal.toString())
                }

                DokumentType.BREV,
                DokumentType.NOTAT,
                DokumentType.VEDTAK,
                DokumentType.BESLUTNING,
                DokumentType.SVARBREV,
                DokumentType.FORLENGET_BEHANDLINGSTIDSBREV,
                DokumentType.EKSPEDISJONSBREV_TIL_TRYGDERETTEN,
                -> {
                    null
                }
            }

        return DokumentEnhetWithDokumentreferanserInput(
            avsenderMottakerList =
                mapAvsenderMottakerInfoSetToAvsenderMottakerInput(
                    behandling = behandling,
                    avsenderMottakerInfoSet = hovedDokument.brevmottakere,
                    dokumentType = hovedDokument.dokumentType,
                ),
            journalfoeringData =
                JournalfoeringDataInput(
                    sakenGjelder =
                        PartIdInput(
                            partIdTypeId = behandling.sakenGjelder.partId.type.id,
                            value = behandling.sakenGjelder.partId.value,
                        ),
                    temaId = behandling.ytelse.toTema().id,
                    sakFagsakId = behandling.fagsakId,
                    sakFagsystemId = behandling.fagsystem.id,
                    kildeReferanse = behandling.id.toString(),
                    // TODO: Fjerne behandling.tildeling når på plass.
                    enhet = hovedDokument.journalfoerendeEnhetId ?: behandling.tildeling!!.enhet!!,
                    behandlingstema = BEHANDLINGSTEMA_KLAGE_KLAGEINSTANS,
                    // Tittel gjelder journalposten, ikke selve dokumentet som lastes opp. Vises i Gosys.
                    tittel = hovedDokument.dokumentType.beskrivelse,
                    brevKode = getBrevkode(hovedDokument),
                    tilleggsopplysning =
                        TilleggsopplysningInput(
                            key = KLAGEBEHANDLING_ID_KEY,
                            value = behandling.id.toString(),
                        ),
                    inngaaendeKanal = inngaaendeKanal,
                    datoMottatt = datoMottatt,
                ),
            dokumentreferanser =
                DokumentEnhetWithDokumentreferanserInput.DokumentInput(
                    hoveddokument = mapDokumentUnderArbeidToDokumentReferanse(hovedDokument),
                    vedlegg = vedleggMapped,
                    journalfoerteVedlegg =
                        journalfoerteVedlegg
                            .map { currentVedlegg ->
                                DokumentEnhetWithDokumentreferanserInput.DokumentInput.JournalfoertDokument(
                                    kildeJournalpostId = currentVedlegg.journalpostId,
                                    dokumentInfoId = currentVedlegg.dokumentInfoId,
                                )
                            },
                ),
            dokumentTypeId = hovedDokument.dokumentType.id,
            journalfoerendeSaksbehandlerIdent = hovedDokument.markertFerdigBy!!,
            trygderettenMetadata = mapToTrygderettenMetadata(hovedDokument = hovedDokument, behandling = behandling),
        )
    }

    private fun mapToTrygderettenMetadata(
        hovedDokument: DokumentUnderArbeidAsHoveddokument,
        behandling: Behandling,
    ): TrygderettenMetadataInput? =
        if (hovedDokument is SmartdokumentUnderArbeidAsHoveddokument &&
            hovedDokument.dokumentType == DokumentType.EKSPEDISJONSBREV_TIL_TRYGDERETTEN &&
            klageUnleashProxyClient.isEnabled(feature = NAV_TR_V2_TOGGLE, navIdent = hovedDokument.markertFerdigBy!!)
        ) {
            val trygderettenMetadata =
                behandling as? BehandlingWithTrygderettenMetadata
                    ?: error("Behandling ${behandling.id} of type ${behandling.type} does not support trygderetten metadata.")

            val paaanketVedtaksdato =
                if (hovedDokument.smartEditorTemplateId == EKSPEDISJONSBREV_TIL_TR_TEMPLATE_NAME &&
                    trygderettenMetadata.paaanketVedtaksdato == null
                ) {
                    throw IllegalArgumentException(
                        "paaanketVedtaksdato must be set on behandling ${behandling.id} before sending ekspedisjonsbrev til Trygderetten.",
                    )
                } else {
                    trygderettenMetadata.paaanketVedtaksdato
                }

            val forsterketRett =
                if (hovedDokument.smartEditorTemplateId in
                    listOf(
                        EKSPEDISJONSBREV_TIL_TR_TEMPLATE_NAME,
                        GJENOPPTAKELSESBEGJAERING_EKSPEDISJONSBREV_TIL_TR_TEMPLATE_NAME,
                    ) && trygderettenMetadata.forsterketRett == null
                ) {
                    throw IllegalArgumentException(
                        "forsterketRett must be set on behandling ${behandling.id} before sending ekspedisjonsbrev til Trygderetten.",
                    )
                } else {
                    trygderettenMetadata.forsterketRett
                }

            TrygderettenMetadataInput(
                kravfremsettelsesdato =
                    if (behandling is Gjenopptaksbehandling || behandling is GjenopptakITrygderettenbehandling) {
                        behandling.mottattKlageinstans.toLocalDate()
                    } else {
                        null
                    },
                paaanketVedtaksdato = paaanketVedtaksdato,
                tidligereITROgOpphevetHenvist =
                    behandlingService.isConnectedToPreviousITrygderettenbehandlingThatWasOpphevetOrHenvist(
                        behandling = behandling,
                    ),
                gjenopptak = behandling is Gjenopptaksbehandling || behandling is GjenopptakITrygderettenbehandling,
                forsterketRett = forsterketRett,
                ettersendelse =
                    hovedDokument.smartEditorTemplateId in
                        listOf(
                            ETTERSENDING_TIL_TR_TEMPLATE_NAME,
                            GJENOPPTAKELSESBEGJAERING_ETTERSENDING_TIL_TR_TEMPLATE_NAME,
                        ),
                lovhenvisning = behandling.hjemler.map { it.toSearchableString() }.toSet(),
                representant =
                    behandling.prosessfullmektig?.let { prosessfullmektig ->
                        TrygderettenMetadataInput.Representant(
                            partId = prosessfullmektig.partId,
                            navn = prosessfullmektig.navn,
                            adresse =
                                prosessfullmektig.address?.let { address ->
                                    AvsenderMottakerInput.Address(
                                        adressetype =
                                            if (address.landkode ==
                                                "NO"
                                            ) {
                                                AvsenderMottakerInput.Adressetype.NORSK_POSTADRESSE
                                            } else {
                                                AvsenderMottakerInput.Adressetype.UTENLANDSK_POSTADRESSE
                                            },
                                        adresselinje1 = address.adresselinje1,
                                        adresselinje2 = address.adresselinje2,
                                        adresselinje3 = address.adresselinje3,
                                        postnummer = address.postnummer,
                                        poststed = address.poststed,
                                        land = address.landkode,
                                    )
                                },
                        )
                    },
            )
        } else {
            null
        }

    private fun getBrevkode(hovedDokument: DokumentUnderArbeidAsHoveddokument): String =
        when (hovedDokument.dokumentType) {
            DokumentType.NOTAT -> BREVKODE_NOTAT

            DokumentType.KJENNELSE_FRA_TRYGDERETTEN -> BREVKODE_KJENNELSE_FRA_TR

            DokumentType.BESLUTNING,
            DokumentType.VEDTAK,
            DokumentType.BREV,
            DokumentType.SVARBREV,
            DokumentType.FORLENGET_BEHANDLINGSTIDSBREV,
            -> BREVKODE_BREV

            DokumentType.ANNEN_INNGAAENDE_POST -> BREVKODE_ANNET

            DokumentType.EKSPEDISJONSBREV_TIL_TRYGDERETTEN -> BREVKODE_BREV // is this correct?
        }

    private fun mapDokumentUnderArbeidToDokumentReferanse(
        dokument: DokumentUnderArbeid,
    ): DokumentEnhetWithDokumentreferanserInput.DokumentInput.Dokument {
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
        avsenderMottakerInfoSet: Set<Brevmottaker>?,
        dokumentType: DokumentType,
    ): List<AvsenderMottakerInput> =
        if (dokumentType == DokumentType.NOTAT) {
            listOf(
                mapPartIdToBrevmottakerInput(
                    partId = behandling.sakenGjelder.partId,
                    localPrint = false,
                    forceCentralPrint = false,
                    address = null,
                    kanal = null,
                    navn = null,
                ),
            )
        } else {
            avsenderMottakerInfoSet!!.map {
                mapPartIdToBrevmottakerInput(
                    partId = it.identifikator?.let { id -> getPartIdFromIdentifikator(id) },
                    localPrint = it.localPrint,
                    forceCentralPrint = it.forceCentralPrint,
                    address = it.address,
                    kanal =
                        getKanal(
                            avsenderMottakerInfo = it,
                            behandling = behandling,
                            dokumentType = dokumentType,
                        ),
                    navn = it.navn,
                )
            }
        }

    private fun getKanal(
        avsenderMottakerInfo: Brevmottaker,
        behandling: Behandling,
        dokumentType: DokumentType,
    ): Kanal? =
        when {
            avsenderMottakerInfo.localPrint -> {
                Kanal.L
            }

            dokumentType == DokumentType.EKSPEDISJONSBREV_TIL_TRYGDERETTEN -> {
                null
            }

            avsenderMottakerInfo.address != null ||
                avsenderMottakerInfo.forceCentralPrint ||
                avsenderMottakerInfo.identifikator == null
            -> {
                Kanal.S
            }

            else -> {
                val distribusjonKanalCode =
                    dokDistKanalService.getDistribusjonKanalCode(
                        mottakerId = avsenderMottakerInfo.identifikator!!,
                        brukerId = behandling.sakenGjelder.partId.value,
                        tema = behandling.ytelse.toTema(),
                        saksbehandlerContext = false,
                    )

                Kanal.valueOf(distribusjonKanalCode.utsendingkanalCode.name)
            }
        }

    private fun mapPartIdToBrevmottakerInput(
        partId: PartId?,
        navn: String?,
        localPrint: Boolean,
        forceCentralPrint: Boolean,
        address: Adresse?,
        kanal: Kanal?,
    ) = AvsenderMottakerInput(
        partId = partId?.let { mapPartId(it) },
        navn = navn ?: getNavn(partId!!),
        localPrint = localPrint,
        tvingSentralPrint = forceCentralPrint,
        adresse = getAdresse(address),
        kanal = kanal,
    )

    private fun getAdresse(address: Adresse?): AvsenderMottakerInput.Address? =
        if (address != null) {
            val adressetype =
                if (address.landkode ==
                    "NO"
                ) {
                    AvsenderMottakerInput.Adressetype.NORSK_POSTADRESSE
                } else {
                    AvsenderMottakerInput.Adressetype.UTENLANDSK_POSTADRESSE
                }
            AvsenderMottakerInput.Address(
                adressetype = adressetype,
                adresselinje1 = address.adresselinje1,
                adresselinje2 = address.adresselinje2,
                adresselinje3 = address.adresselinje3,
                postnummer = address.postnummer,
                poststed = address.poststed,
                land = address.landkode,
            )
        } else {
            null
        }

    private fun mapPartId(partId: PartId): PartIdInput =
        PartIdInput(
            partIdTypeId = partId.type.id,
            value = partId.value,
        )

    private fun getNavn(partId: PartId): String =
        if (partId.type == PartIdType.PERSON) {
            personService.getPerson(fnr = partId.value).sammensattNavn
        } else {
            eregClient.hentNoekkelInformasjonOmOrganisasjon(partId.value).navn.sammensattnavn
        }
}
