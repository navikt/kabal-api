package no.nav.klage.dokument.api.mapper

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.klage.dokument.api.view.DokumentView
import no.nav.klage.dokument.api.view.DokumentViewWithList
import no.nav.klage.dokument.api.view.SmartEditorDocumentView
import no.nav.klage.dokument.clients.kabaljsontopdf.domain.InnholdsfortegnelseRequest
import no.nav.klage.dokument.clients.kabaljsontopdf.domain.InnholdsfortegnelseRequest.Document.Type
import no.nav.klage.dokument.clients.kabalsmarteditorapi.model.response.DocumentOutput
import no.nav.klage.dokument.domain.FysiskDokument
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeid
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeidAsSmartdokument
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeidAsVedlegg
import no.nav.klage.dokument.domain.dokumenterunderarbeid.JournalfoertDokumentUnderArbeidAsVedlegg
import no.nav.klage.kodeverk.DokumentType
import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.Tema
import no.nav.klage.oppgave.api.view.DokumentReferanse
import no.nav.klage.oppgave.clients.saf.graphql.*
import no.nav.klage.oppgave.domain.klage.Behandling
import no.nav.klage.oppgave.domain.klage.Saksdokument
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import no.nav.klage.oppgave.util.getSortKey
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class DokumentMapper {

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
        hoveddokument: DokumentUnderArbeid,
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
                    .map { journalfoerteDokumenterUnderArbeid ->
                        mapToInnholdsfortegnelseRequestDocumentFromJournalfoertDokument(
                            journalfoertDokumentUnderArbeidAsVedlegg = journalfoerteDokumenterUnderArbeid,
                            journalpost = journalpostList.find { it.journalpostId == journalfoerteDokumenterUnderArbeid.journalpostId }!!
                        )
                    }
    }

    fun mapToInnholdsfortegnelseRequestDocumentFromDokumentUnderArbeid(
        dokumentUnderArbeid: DokumentUnderArbeid,
        behandling: Behandling,
        hoveddokument: DokumentUnderArbeid,
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
            dato = journalfoertDokumentUnderArbeidAsVedlegg.opprettet.toLocalDate(),
            avsenderMottaker = journalpost.avsenderMottaker?.navn ?: "",
            saksnummer = journalpost.sak?.fagsakId ?: "Saksnummer ikke funnet i SAF",
            type = Type.valueOf(
                journalpost.journalposttype?.name ?: error("Type ikke funnet i SAF")
            )
        )
    }

    fun mapToDokumentView(dokumentUnderArbeid: DokumentUnderArbeid, journalpost: Journalpost?): DokumentView {
        var journalfoertDokumentReference: DokumentView.JournalfoertDokumentReference? = null

        var tittel = dokumentUnderArbeid.name

        if (dokumentUnderArbeid is JournalfoertDokumentUnderArbeidAsVedlegg) {
            if (journalpost == null) {
                throw RuntimeException("Need journalpost to handle JournalfoertDokumentUnderArbeidAsVedlegg")
            }
            val dokument =
                journalpost.dokumenter?.find { it.dokumentInfoId == dokumentUnderArbeid.dokumentInfoId }
                    ?: throw RuntimeException("Document not found in Dokarkiv")

            tittel = (dokument.tittel ?: "Tittel ikke funnet i SAF")

            journalfoertDokumentReference = DokumentView.JournalfoertDokumentReference(
                journalpostId = dokumentUnderArbeid.journalpostId,
                dokumentInfoId = dokumentUnderArbeid.dokumentInfoId,
                harTilgangTilArkivvariant = harTilgangTilArkivvariant(dokument),
                datoOpprettet = dokumentUnderArbeid.opprettet,
                sortKey = dokumentUnderArbeid.sortKey!!
            )
        }

        return DokumentView(
            id = dokumentUnderArbeid.id,
            tittel = tittel,
            dokumentTypeId = dokumentUnderArbeid.dokumentType?.id,
            created = dokumentUnderArbeid.created,
            modified = dokumentUnderArbeid.modified,
            isSmartDokument = dokumentUnderArbeid is DokumentUnderArbeidAsSmartdokument,
            templateId = if (dokumentUnderArbeid is DokumentUnderArbeidAsSmartdokument) dokumentUnderArbeid.smartEditorTemplateId else null,
            isMarkertAvsluttet = dokumentUnderArbeid.markertFerdig != null,
            parent = if (dokumentUnderArbeid is DokumentUnderArbeidAsVedlegg) dokumentUnderArbeid.parentId else null,
            parentId = if (dokumentUnderArbeid is DokumentUnderArbeidAsVedlegg) dokumentUnderArbeid.parentId else null,
            type = dokumentUnderArbeid.getType(),
            journalfoertDokumentReference = journalfoertDokumentReference,
            creatorIdent = dokumentUnderArbeid.creatorIdent,
            creatorRole = dokumentUnderArbeid.creatorRole,
        )
    }

    fun mapToDokumentListView(
        dokumentUnderArbeidList: List<DokumentUnderArbeid>,
        duplicateJournalfoerteDokumenter: List<DokumentUnderArbeid>,
        journalpostList: List<Journalpost>,
    ): DokumentViewWithList {
        val firstDokument = dokumentUnderArbeidList.firstOrNull()
        val firstDokumentView = if (firstDokument != null) {
            if (firstDokument is JournalfoertDokumentUnderArbeidAsVedlegg) {
                mapToDokumentView(
                    dokumentUnderArbeid = dokumentUnderArbeidList.first(),
                    journalpost = journalpostList.find { it.journalpostId == firstDokument.journalpostId })
            } else {
                mapToDokumentView(dokumentUnderArbeid = dokumentUnderArbeidList.first(), journalpost = null)
            }
        } else null

        return DokumentViewWithList(
            id = firstDokumentView?.id,
            tittel = firstDokumentView?.tittel,
            dokumentTypeId = firstDokumentView?.dokumentTypeId,
            created = firstDokumentView?.created,
            modified = firstDokumentView?.modified,
            type = firstDokumentView?.type,
            isSmartDokument = firstDokumentView?.isSmartDokument,
            templateId = firstDokumentView?.templateId,
            isMarkertAvsluttet = firstDokumentView?.isMarkertAvsluttet,
            parent = firstDokumentView?.parent,
            parentId = firstDokumentView?.parentId,
            journalfoertDokumentReference = firstDokumentView?.journalfoertDokumentReference,
            alteredDocuments = dokumentUnderArbeidList.map { dokumentUnderArbeid ->
                if (dokumentUnderArbeid is JournalfoertDokumentUnderArbeidAsVedlegg) {
                    mapToDokumentView(
                        dokumentUnderArbeid = dokumentUnderArbeid,
                        journalpost = journalpostList.find { it.journalpostId == dokumentUnderArbeid.journalpostId })
                } else {
                    mapToDokumentView(dokumentUnderArbeid = dokumentUnderArbeid, journalpost = null)
                }
            },
            duplicateJournalfoerteDokumenter = duplicateJournalfoerteDokumenter.map { duplicateJournalfoertDokument ->
                if (duplicateJournalfoertDokument is JournalfoertDokumentUnderArbeidAsVedlegg) {
                    mapToDokumentView(
                        dokumentUnderArbeid = duplicateJournalfoertDokument,
                        journalpost = journalpostList.find { it.journalpostId == duplicateJournalfoertDokument.journalpostId })
                } else {
                    mapToDokumentView(dokumentUnderArbeid = duplicateJournalfoertDokument, journalpost = null)
                }
            },
        )
    }

    fun mapToSmartEditorDocumentView(
        dokumentUnderArbeid: DokumentUnderArbeid,
        smartEditorDocument: DocumentOutput,
    ): SmartEditorDocumentView {
        return SmartEditorDocumentView(
            id = dokumentUnderArbeid.id,
            tittel = dokumentUnderArbeid.name,
            dokumentTypeId = dokumentUnderArbeid.dokumentType!!.id,
            templateId = if (dokumentUnderArbeid is DokumentUnderArbeidAsSmartdokument) dokumentUnderArbeid.smartEditorTemplateId else null,
            parent = if (dokumentUnderArbeid is DokumentUnderArbeidAsVedlegg) dokumentUnderArbeid.parentId else null,
            parentId = if (dokumentUnderArbeid is DokumentUnderArbeidAsVedlegg) dokumentUnderArbeid.parentId else null,
            content = jacksonObjectMapper().readTree(smartEditorDocument.json),
            created = smartEditorDocument.created,
            modified = smartEditorDocument.modified,
            creatorIdent = dokumentUnderArbeid.creatorIdent,
            creatorRole = dokumentUnderArbeid.creatorRole,
        )
    }

    //TODO: Har ikke tatt høyde for skjerming, ref https://confluence.adeo.no/pages/viewpage.action?pageId=320364687
    fun mapJournalpostToDokumentReferanse(
        journalpost: Journalpost,
        behandling: Behandling
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
            valgt = behandling.saksdokumenter.containsDokument(
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
                DokumentReferanse.RelevantDato(
                    dato = it.dato,
                    datotype = DokumentReferanse.RelevantDato.Datotype.valueOf(it.datotype.name)
                )
            },
            kanal = DokumentReferanse.Kanal.valueOf(journalpost.kanal.name),
            kanalnavn = journalpost.kanalnavn,
            utsendingsinfo = getUtsendingsinfo(journalpost.utsendingsinfo),
            originalJournalpostId = hoveddokument.originalJournalpostId,
            sortKey = getSortKey(journalpost = journalpost, dokumentInfoId = hoveddokument.dokumentInfoId)
        )

        dokumentReferanse.vedlegg.addAll(getVedlegg(journalpost, behandling))

        return dokumentReferanse
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
        behandling: Behandling
    ): List<DokumentReferanse.VedleggReferanse> {
        return if ((journalpost.dokumenter?.size ?: 0) > 1) {
            journalpost.dokumenter?.subList(1, journalpost.dokumenter.size)?.map { vedlegg ->
                DokumentReferanse.VedleggReferanse(
                    tittel = vedlegg.tittel,
                    dokumentInfoId = vedlegg.dokumentInfoId,
                    harTilgangTilArkivvariant = harTilgangTilArkivvariant(vedlegg),
                    valgt = behandling.saksdokumenter.containsDokument(
                        journalpost.journalpostId,
                        vedlegg.dokumentInfoId
                    ),
                    originalJournalpostId = vedlegg.originalJournalpostId,
                    sortKey = getSortKey(journalpost = journalpost, dokumentInfoId = vedlegg.dokumentInfoId)
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
}