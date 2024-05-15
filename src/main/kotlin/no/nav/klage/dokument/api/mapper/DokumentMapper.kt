package no.nav.klage.dokument.api.mapper

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.klage.dokument.api.view.*
import no.nav.klage.dokument.clients.kabaljsontopdf.domain.InnholdsfortegnelseRequest
import no.nav.klage.dokument.clients.kabaljsontopdf.domain.InnholdsfortegnelseRequest.Document.Type
import no.nav.klage.dokument.clients.kabalsmarteditorapi.model.response.SmartDocumentResponse
import no.nav.klage.dokument.domain.FysiskDokument
import no.nav.klage.dokument.domain.dokumenterunderarbeid.*
import no.nav.klage.dokument.repositories.DokumentUnderArbeidRepository
import no.nav.klage.kodeverk.DokumentType
import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.Tema
import no.nav.klage.oppgave.api.mapper.BehandlingMapper
import no.nav.klage.oppgave.api.view.BehandlingDetaljerView
import no.nav.klage.oppgave.api.view.DokumentReferanse
import no.nav.klage.oppgave.api.view.SaksbehandlerView
import no.nav.klage.oppgave.clients.saf.graphql.*
import no.nav.klage.oppgave.domain.klage.Behandling
import no.nav.klage.oppgave.domain.klage.Saksdokument
import no.nav.klage.oppgave.service.DokDistKanalService
import no.nav.klage.oppgave.service.SaksbehandlerService
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getPartIdFromIdentifikator
import no.nav.klage.oppgave.util.getSecureLogger
import no.nav.klage.oppgave.util.getSortKey
import org.hibernate.Hibernate
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class DokumentMapper(
    private val saksbehandlerService: SaksbehandlerService,
    private val behandlingMapper: BehandlingMapper,
    private val dokumentUnderArbeidRepository: DokumentUnderArbeidRepository,
    private val dokDistKanalService: DokDistKanalService,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
    }

    fun mapToByteArray(fysiskDokument: FysiskDokument): ResponseEntity<ByteArray> {
        return ResponseEntity(
            fysiskDokument.content,
            HttpHeaders().apply {
                contentType = fysiskDokument.contentType
                add(
                    "Content-Disposition",
                    "inline; filename=\"${fysiskDokument.title.removeSuffix(".pdf")}.pdf\""
                )
            },
            HttpStatus.OK
        )
    }

    @Suppress("UNCHECKED_CAST")
    fun getSortedDokumentViewListForInnholdsfortegnelse(
        allDokumenterUnderArbeid: Set<DokumentUnderArbeidAsVedlegg>,
        behandling: Behandling,
        hoveddokument: DokumentUnderArbeidAsHoveddokument,
        journalpostList: List<Journalpost>,
    ): List<InnholdsfortegnelseRequest.Document> {
        val (dokumenterUnderArbeid, journalfoerteDokumenterUnderArbeid) = allDokumenterUnderArbeid.partition {
            it !is JournalfoertDokumentUnderArbeidAsVedlegg
        } as Pair<List<DokumentUnderArbeid>, List<JournalfoertDokumentUnderArbeidAsVedlegg>>

        return dokumenterUnderArbeid.sortedByDescending { it.created }
            .map {
                mapToInnholdsfortegnelseRequestDocumentFromDokumentUnderArbeid(
                    dokumentUnderArbeid = it,
                    behandling = behandling,
                    hoveddokument = hoveddokument,
                )
            } +
                journalfoerteDokumenterUnderArbeid
                    .sortedByDescending { (it).sortKey }
                    .map { journalfoertDokumentUnderArbeid ->
                        mapToInnholdsfortegnelseRequestDocumentFromJournalfoertDokument(
                            journalfoertDokumentUnderArbeidAsVedlegg = journalfoertDokumentUnderArbeid,
                            journalpost = journalpostList.find { it.journalpostId == journalfoertDokumentUnderArbeid.journalpostId }!!
                        )
                    }
    }

    fun mapToInnholdsfortegnelseRequestDocumentFromDokumentUnderArbeid(
        dokumentUnderArbeid: DokumentUnderArbeid,
        behandling: Behandling,
        hoveddokument: DokumentUnderArbeidAsHoveddokument,
    ): InnholdsfortegnelseRequest.Document {
        return InnholdsfortegnelseRequest.Document(
            tittel = dokumentUnderArbeid.name,
            tema = behandling.ytelse.toTema().navn,
            dato = LocalDateTime.now().toLocalDate(),
            avsenderMottaker = "",
            saksnummer = behandling.fagsakId,
            type = if (hoveddokument.dokumentType == DokumentType.NOTAT) Type.N else throw RuntimeException("Wrong hoveddokument type: ${hoveddokument.dokumentType}.")
        )
    }


    private fun mapToInnholdsfortegnelseRequestDocumentFromJournalfoertDokument(
        journalfoertDokumentUnderArbeidAsVedlegg: JournalfoertDokumentUnderArbeidAsVedlegg,
        journalpost: Journalpost,
    ): InnholdsfortegnelseRequest.Document {
        val dokumentInDokarkiv =
            journalpost.dokumenter?.find { it.dokumentInfoId == journalfoertDokumentUnderArbeidAsVedlegg.dokumentInfoId }
                ?: throw RuntimeException("Document not found in Dokarkiv")

        return InnholdsfortegnelseRequest.Document(
            tittel = dokumentInDokarkiv.tittel ?: "Tittel ikke funnet i SAF",
            tema = Tema.fromNavn(journalpost.tema?.name).beskrivelse,
            dato = journalpost.getDatoRegSendt()?.toLocalDate() ?: journalpost.datoOpprettet.toLocalDate(),
            avsenderMottaker = journalpost.avsenderMottaker?.navn ?: "",
            saksnummer = journalpost.sak?.fagsakId ?: "Saksnummer ikke funnet i SAF",
            type = Type.valueOf(
                journalpost.journalposttype?.name ?: error("Type ikke funnet i SAF")
            )
        )
    }

    fun mapToDokumentView(
        dokumentUnderArbeid: DokumentUnderArbeid,
        journalpost: Journalpost?,
        smartEditorDocument: SmartDocumentResponse?,
        behandling: Behandling,
    ): DokumentView {
        val unproxiedDUA = Hibernate.unproxy(dokumentUnderArbeid) as DokumentUnderArbeid

        var journalfoertDokumentReference: DokumentView.JournalfoertDokumentReference? = null

        var tittel = unproxiedDUA.name

        if (unproxiedDUA is JournalfoertDokumentUnderArbeidAsVedlegg) {
            if (journalpost == null) {
                throw RuntimeException("Need journalpost to handle JournalfoertDokumentUnderArbeidAsVedlegg")
            }
            val dokument =
                journalpost.dokumenter?.find { it.dokumentInfoId == unproxiedDUA.dokumentInfoId }
                    ?: throw RuntimeException("Document not found in Dokarkiv")

            tittel = (dokument.tittel ?: "Tittel ikke funnet i SAF")

            journalfoertDokumentReference = DokumentView.JournalfoertDokumentReference(
                journalpostId = unproxiedDUA.journalpostId,
                dokumentInfoId = unproxiedDUA.dokumentInfoId,
                harTilgangTilArkivvariant = harTilgangTilArkivvariant(dokument),
                datoOpprettet = unproxiedDUA.opprettet,
                sortKey = unproxiedDUA.sortKey!!
            )
        }

        var inngaaendeKanal: InngaaendeKanal? = null
        var avsender: BehandlingDetaljerView.PartView? = null
        var mottakerList: List<DokumentView.Mottaker> = mutableListOf()
        val dokumentTypeId: String

        if (unproxiedDUA is DokumentUnderArbeidAsHoveddokument) {
            dokumentTypeId = unproxiedDUA.dokumentType.id

            if (unproxiedDUA.isInngaaende()) {
                unproxiedDUA as OpplastetDokumentUnderArbeidAsHoveddokument
                inngaaendeKanal =
                    if (unproxiedDUA.inngaaendeKanal != null) unproxiedDUA.inngaaendeKanal!! else null
                val avsenderIdentifikator = unproxiedDUA.avsenderMottakerInfoSet.firstOrNull()?.identifikator
                if (avsenderIdentifikator != null) {
                    avsender = behandlingMapper.getPartView(getPartIdFromIdentifikator(avsenderIdentifikator))
                }
            } else if (unproxiedDUA.isUtgaaende()) {
                val mottakerInfoSet = unproxiedDUA.avsenderMottakerInfoSet
                if (mottakerInfoSet.isNotEmpty()) {

                    mottakerList = mottakerInfoSet.map {
                        DokumentView.Mottaker(
                            part = behandlingMapper.getPartViewWithUtsendingskanal(
                                partId = getPartIdFromIdentifikator(it.identifikator),
                                behandling = behandling
                            ),
                            overriddenAddress = getBehandlingDetaljerViewAddress(it.address),
                            handling = getHandlingEnum(
                                markLocalPrint = it.localPrint,
                                forceCentralPrint = it.forceCentralPrint,
                                utsendingskanal = dokDistKanalService.getUtsendingskanal(
                                    mottakerId = it.identifikator,
                                    brukerId = behandling.sakenGjelder.partId.value,
                                    tema = behandling.ytelse.toTema(),
                                )
                            ),
                        )
                    }
                }
            }
        } else {
            unproxiedDUA as DokumentUnderArbeidAsVedlegg
            val parentDocument = dokumentUnderArbeidRepository.findById(unproxiedDUA.parentId)
                .get() as DokumentUnderArbeidAsHoveddokument
            dokumentTypeId = parentDocument.dokumentType.id
        }


        return DokumentView(
            id = unproxiedDUA.id,
            tittel = tittel,
            dokumentTypeId = dokumentTypeId,
            created = unproxiedDUA.created,
            modified = if (dokumentUnderArbeid is DokumentUnderArbeidAsSmartdokument) {
                smartEditorDocument!!.modified
            } else unproxiedDUA.modified,
            isSmartDokument = unproxiedDUA is DokumentUnderArbeidAsSmartdokument,
            templateId = if (unproxiedDUA is DokumentUnderArbeidAsSmartdokument) unproxiedDUA.smartEditorTemplateId else null,
            content = if (dokumentUnderArbeid is DokumentUnderArbeidAsSmartdokument) {
                jacksonObjectMapper().readTree(smartEditorDocument!!.json)
            } else null,
            version = if (dokumentUnderArbeid is DokumentUnderArbeidAsSmartdokument) {
                smartEditorDocument?.version
            } else null,
            isMarkertAvsluttet = unproxiedDUA.markertFerdig != null,
            parentId = if (unproxiedDUA is DokumentUnderArbeidAsVedlegg) unproxiedDUA.parentId else null,
            type = unproxiedDUA.getType(),
            journalfoertDokumentReference = journalfoertDokumentReference,
            creator = unproxiedDUA.toCreatorView(),
            creatorIdent = unproxiedDUA.creatorIdent,
            creatorRole = unproxiedDUA.creatorRole,
            datoMottatt = if (unproxiedDUA is OpplastetDokumentUnderArbeidAsHoveddokument) unproxiedDUA.datoMottatt else null,
            avsender = avsender,
            mottakerList = mottakerList,
            inngaaendeKanal = inngaaendeKanal,
            language = if (dokumentUnderArbeid is DokumentUnderArbeidAsSmartdokument) {
                DokumentView.Language.valueOf(dokumentUnderArbeid.language.name)
            } else null,
        )
    }

    private fun getBehandlingDetaljerViewAddress(address: DokumentUnderArbeidAdresse?): BehandlingDetaljerView.Address? {
        return if (address != null) {
            BehandlingDetaljerView.Address(
                adresselinje1 = address.adresselinje1,
                adresselinje2 = address.adresselinje2,
                adresselinje3 = address.adresselinje3,
                landkode = address.landkode,
                postnummer = address.postnummer,
                poststed = address.poststed,
            )
        } else null

    }

    private fun DokumentUnderArbeid.toCreatorView(): DokumentView.Creator {
        return DokumentView.Creator(
            employee = SaksbehandlerView(
                navIdent = creatorIdent,
                navn = saksbehandlerService.getNameForIdentDefaultIfNull(creatorIdent),
            ),
            creatorRole = creatorRole,
        )
    }

    fun mapToDokumentListView(
        dokumentUnderArbeidList: List<DokumentUnderArbeid>,
        duplicateJournalfoerteDokumenter: List<DokumentUnderArbeid>,
        journalpostList: List<Journalpost>,
    ): DokumentViewWithList {
        val firstDokument = Hibernate.unproxy(dokumentUnderArbeidList.first()) as DokumentUnderArbeid

        return DokumentViewWithList(
            modified = firstDokument.modified,
            alteredDocuments = dokumentUnderArbeidList.drop(1).map { dokumentUnderArbeid ->
                val duaUnproxied = Hibernate.unproxy(dokumentUnderArbeid) as DokumentUnderArbeidAsVedlegg
                NewParent(
                    id = duaUnproxied.id,
                    modified = duaUnproxied.modified,
                    parentId = duaUnproxied.parentId,
                )
            },
            duplicateJournalfoerteDokumenter = duplicateJournalfoerteDokumenter.map { duplicateJournalfoertDokument ->
                val duaUnproxied = Hibernate.unproxy(duplicateJournalfoertDokument) as DokumentUnderArbeid
                duaUnproxied.id
            },
        )
    }

    //TODO: Har ikke tatt høyde for skjerming, ref https://confluence.adeo.no/pages/viewpage.action?pageId=320364687
    fun mapJournalpostToDokumentReferanse(
        journalpost: Journalpost,
        saksdokumenter: MutableSet<Saksdokument>
    ): DokumentReferanse {

        val hoveddokument = journalpost.dokumenter?.firstOrNull()
            ?: throw RuntimeException("Could not find hoveddokument for journalpost ${journalpost.journalpostId}")

        val dokumentReferanse = DokumentReferanse(
            tittel = hoveddokument.tittel,
            tema = Tema.fromNavn(journalpost.tema?.name).id,
            temaId = Tema.fromNavn(journalpost.tema?.name).id,
            dokumentInfoId = hoveddokument.dokumentInfoId,
            journalpostId = journalpost.journalpostId,
            harTilgangTilArkivvariant = harTilgangTilArkivvariant(hoveddokument),
            valgt = saksdokumenter.containsDokument(
                journalpost.journalpostId,
                hoveddokument.dokumentInfoId
            ),
            journalposttype = DokumentReferanse.Journalposttype.valueOf(journalpost.journalposttype!!.name),
            journalstatus = if (journalpost.journalstatus != null) {
                DokumentReferanse.Journalstatus.valueOf(journalpost.journalstatus.name)
            } else null,
            sak = if (journalpost.sak != null) {
                DokumentReferanse.Sak(
                    datoOpprettet = journalpost.sak.datoOpprettet,
                    fagsakId = journalpost.sak.fagsakId,
                    fagsaksystem = journalpost.sak.fagsaksystem,
                    fagsystemId = journalpost.sak.fagsaksystem?.let { Fagsystem.fromNavn(it).id }
                )
            } else null,
            avsenderMottaker = if (journalpost.avsenderMottaker == null ||
                (journalpost.avsenderMottaker.id == null ||
                        journalpost.avsenderMottaker.type == null)
            ) {
                null
            } else {
                DokumentReferanse.AvsenderMottaker(
                    id = journalpost.avsenderMottaker.id,
                    type = DokumentReferanse.AvsenderMottaker.AvsenderMottakerIdType.valueOf(
                        journalpost.avsenderMottaker.type.name
                    ),
                    navn = journalpost.avsenderMottaker.navn,
                )
            },
            opprettetAvNavn = journalpost.opprettetAvNavn,
            datoOpprettet = journalpost.datoOpprettet,
            datoRegSendt = journalpost.getDatoRegSendt(),
            relevanteDatoer = journalpost.relevanteDatoer?.map {
                DokumentReferanse.RelevantDatoOld(
                    dato = it.dato,
                    datotype = DokumentReferanse.RelevantDatoOld.DatotypeOld.valueOf(it.datotype.name)
                )
            } ?: emptyList(),
            timeline = journalpost.toTimeline(),
            kanal = journalpost.kanal,
            kanalnavn = journalpost.kanalnavn,
            utsendingsinfo = getUtsendingsinfo(journalpost.utsendingsinfo),
            originalJournalpostId = hoveddokument.originalJournalpostId,
            sortKey = getSortKey(journalpost = journalpost, dokumentInfoId = hoveddokument.dokumentInfoId),
            logiskeVedlegg = hoveddokument.logiskeVedlegg?.map {
                no.nav.klage.oppgave.api.view.LogiskVedlegg(
                    tittel = it.tittel,
                    logiskVedleggId = it.logiskVedleggId
                )
            }
        )

        dokumentReferanse.vedlegg.addAll(getVedlegg(journalpost, saksdokumenter))

        return dokumentReferanse
    }

    private fun Journalpost.toTimeline(): List<DokumentReferanse.TimelineItem> {
        val relevantDates = (this.relevanteDatoer?.mapNotNull {
            if (it.datotype.name == "DATO_DOKUMENT") {
                null
            } else {
                DokumentReferanse.TimelineItem(
                    timestamp = it.dato,
                    type = it.datotype.name.toTimelineType()
                )
            }
        } ?: emptyList())

        return listOf(DokumentReferanse.TimelineItem(
            timestamp = this.datoOpprettet,
            type = DokumentReferanse.TimelineItem.TimelineType.OPPRETTET
        )) + relevantDates.sortedBy { it.timestamp }
    }

    private fun String.toTimelineType(): DokumentReferanse.TimelineItem.TimelineType {
        return when (this) {
            "DATO_JOURNALFOERT" -> DokumentReferanse.TimelineItem.TimelineType.JOURNALFOERT
            "DATO_EKSPEDERT" -> DokumentReferanse.TimelineItem.TimelineType.EKSPEDERT
            "DATO_SENDT_PRINT" -> DokumentReferanse.TimelineItem.TimelineType.SENDT_PRINT
            "DATO_REGISTRERT" -> DokumentReferanse.TimelineItem.TimelineType.REGISTRERT
            "DATO_AVS_RETUR" -> DokumentReferanse.TimelineItem.TimelineType.AVSENDER_RETUR
            "DATO_LEST" -> DokumentReferanse.TimelineItem.TimelineType.LEST
            else -> throw RuntimeException("Unknown datotype: $this")
        }
    }

    private fun getUtsendingsinfo(utsendingsinfo: Utsendingsinfo?): DokumentReferanse.Utsendingsinfo? {
        if (utsendingsinfo == null) {
            return null
        }

        return with(utsendingsinfo) {
            DokumentReferanse.Utsendingsinfo(
                epostVarselSendt = if (epostVarselSendt != null) {
                    DokumentReferanse.Utsendingsinfo.EpostVarselSendt(
                        tittel = epostVarselSendt.tittel,
                        adresse = epostVarselSendt.adresse,
                        varslingstekst = epostVarselSendt.varslingstekst,
                    )
                } else null,
                smsVarselSendt = if (smsVarselSendt != null) {
                    DokumentReferanse.Utsendingsinfo.SmsVarselSendt(
                        adresse = smsVarselSendt.adresse,
                        varslingstekst = smsVarselSendt.varslingstekst,
                    )
                } else null,
                fysiskpostSendt = if (fysiskpostSendt != null) {
                    DokumentReferanse.Utsendingsinfo.FysiskpostSendt(
                        adressetekstKonvolutt = fysiskpostSendt.adressetekstKonvolutt,
                    )
                } else null,
                digitalpostSendt = if (digitalpostSendt != null) {
                    DokumentReferanse.Utsendingsinfo.DigitalpostSendt(
                        adresse = digitalpostSendt.adresse,
                    )
                } else null,
            )
        }
    }

    private fun getVedlegg(
        journalpost: Journalpost,
        saksdokumenter: MutableSet<Saksdokument>
    ): List<DokumentReferanse.VedleggReferanse> {
        return if ((journalpost.dokumenter?.size ?: 0) > 1) {
            journalpost.dokumenter?.subList(1, journalpost.dokumenter.size)?.map { vedlegg ->
                DokumentReferanse.VedleggReferanse(
                    tittel = vedlegg.tittel,
                    dokumentInfoId = vedlegg.dokumentInfoId,
                    harTilgangTilArkivvariant = harTilgangTilArkivvariant(vedlegg),
                    valgt = saksdokumenter.containsDokument(
                        journalpost.journalpostId,
                        vedlegg.dokumentInfoId
                    ),
                    originalJournalpostId = vedlegg.originalJournalpostId,
                    sortKey = getSortKey(journalpost = journalpost, dokumentInfoId = vedlegg.dokumentInfoId),
                    logiskeVedlegg = vedlegg.logiskeVedlegg?.map {
                        no.nav.klage.oppgave.api.view.LogiskVedlegg(
                            tittel = it.tittel,
                            logiskVedleggId = it.logiskVedleggId
                        )
                    }
                )
            } ?: throw RuntimeException("could not create VedleggReferanser from dokumenter")
        } else {
            emptyList()
        }
    }

    fun harTilgangTilArkivvariant(dokumentInfo: DokumentInfo): Boolean =
        dokumentInfo.dokumentvarianter.any { dv ->
            dv.variantformat == Variantformat.ARKIV && dv.saksbehandlerHarTilgang
        }

    private fun MutableSet<Saksdokument>.containsDokument(journalpostId: String, dokumentInfoId: String) =
        any {
            it.journalpostId == journalpostId && it.dokumentInfoId == dokumentInfoId
        }

    private fun Journalpost.getDatoRegSendt(): LocalDateTime? {
        return try {
            when (this.journalposttype) {
                Journalposttype.I -> {
                    this.getRelevantDato(Datotype.DATO_REGISTRERT)
                        ?: error("could not find datoRegSendt for inngående dokument")
                }

                Journalposttype.N -> {
                    this.dokumenter?.firstOrNull()?.datoFerdigstilt
                        ?: this.getRelevantDato(Datotype.DATO_JOURNALFOERT)
                        ?: this.getRelevantDato(Datotype.DATO_DOKUMENT)
                        ?: error("could not find datoRegSendt for notat")
                }

                Journalposttype.U -> {
                    this.getRelevantDato(Datotype.DATO_EKSPEDERT)
                        ?: this.getRelevantDato(Datotype.DATO_SENDT_PRINT)
                        ?: this.getRelevantDato(Datotype.DATO_JOURNALFOERT)
                        ?: this.getRelevantDato(Datotype.DATO_DOKUMENT)
                        ?: error("could not find datoRegSendt for utgående dokument")
                }

                null -> error("cannot happen")
            }
        } catch (e: Exception) {
            logger.error("could not getDatoRegSendt", e)
            null
        }

    }

    fun Journalpost.getRelevantDato(datotype: Datotype): LocalDateTime? {
        return this.relevanteDatoer?.find { it.datotype == datotype }?.dato
    }

    private fun getHandlingEnum(
        markLocalPrint: Boolean,
        forceCentralPrint: Boolean,
        utsendingskanal: BehandlingDetaljerView.Utsendingskanal
    ): HandlingEnum {
        return if (markLocalPrint && !forceCentralPrint) {
            HandlingEnum.LOCAL_PRINT
        } else if (!markLocalPrint && forceCentralPrint) {
            if (utsendingskanal == BehandlingDetaljerView.Utsendingskanal.SENTRAL_UTSKRIFT) {
                HandlingEnum.AUTO
            } else {
                HandlingEnum.CENTRAL_PRINT
            }
        } else if (!markLocalPrint && !forceCentralPrint) {
            HandlingEnum.AUTO
        } else {
            error("Invalid combination markLocalPrint $markLocalPrint and forceCentralPrint $forceCentralPrint")
        }
    }
}