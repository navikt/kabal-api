package no.nav.klage.dokument.api.mapper

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.klage.dokument.api.view.DokumentView
import no.nav.klage.dokument.api.view.DokumentViewWithList
import no.nav.klage.dokument.api.view.SmartEditorDocumentView
import no.nav.klage.dokument.clients.kabaljsontopdf.domain.InnholdsfortegnelseRequest
import no.nav.klage.dokument.clients.kabalsmarteditorapi.model.response.DocumentOutput
import no.nav.klage.dokument.domain.FysiskDokument
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeid
import no.nav.klage.dokument.domain.dokumenterunderarbeid.JournalfoertDokumentUnderArbeidAsVedlegg
import no.nav.klage.kodeverk.DokumentType
import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.Tema
import no.nav.klage.oppgave.api.view.DokumentReferanse
import no.nav.klage.oppgave.clients.saf.graphql.*
import no.nav.klage.oppgave.domain.klage.Behandling
import no.nav.klage.oppgave.domain.klage.Saksdokument
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class DokumentMapper(
    private val safClient: SafGraphQlClient,
) {

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

    fun getSortedDokumentViewList(allDokumenterUnderArbeid: List<DokumentUnderArbeid>): List<DokumentView> {
        val (dokumenterUnderArbeid, journalfoerteDokumenterUnderArbeid) = allDokumenterUnderArbeid.partition {
            it !is JournalfoertDokumentUnderArbeidAsVedlegg
        }

        return dokumenterUnderArbeid.sortedByDescending { it.created }
            .map { mapToDokumentView(it) }
            .plus(journalfoerteDokumenterUnderArbeid
                .map { mapToDokumentView(it) }
                .sortedWith(compareByDescending<DokumentView> { it.journalfoertDokumentReference?.datoOpprettet }.thenBy { it.tittel })
            )
    }

    fun getSortedDokumentViewListForInnholdsfortegnelse(
        allDokumenterUnderArbeid: List<DokumentUnderArbeid>,
        mottakere: List<String>,
        behandling: Behandling,
        hoveddokument: DokumentUnderArbeid,
    ): Pair<List<InnholdsfortegnelseRequest.Document>, List<InnholdsfortegnelseRequest.Document>> {
        val (dokumenterUnderArbeid, journalfoerteDokumenterUnderArbeid) = allDokumenterUnderArbeid.partition {
            it !is JournalfoertDokumentUnderArbeidAsVedlegg
        }

        return dokumenterUnderArbeid.sortedByDescending { it.created }
            .map {
                mapToInnholdsfortegnelseRequestDocumentFromDokumentUnderArbeid(
                    dokumentUnderArbeid = it,
                    mottakere = mottakere,
                    behandling = behandling,
                    hoveddokument = hoveddokument,
                )
            } to journalfoerteDokumenterUnderArbeid
            .map {
                mapToInnholdsfortegnelseRequestDocumentFromJournalfoertDokument(
                    dokumentUnderArbeid = it,
                    behandling = behandling
                )
            }
            .sortedWith(compareByDescending<InnholdsfortegnelseRequest.Document> { it.opprettet }.thenBy { it.tittel })
    }

    fun mapToInnholdsfortegnelseRequestDocumentFromJournalfoertDokument(
        dokumentUnderArbeid: DokumentUnderArbeid,
        mottakere: List<String> = emptyList(),
        behandling: Behandling,
    ): InnholdsfortegnelseRequest.Document {
        if (dokumentUnderArbeid !is JournalfoertDokumentUnderArbeidAsVedlegg) {
            error("Document must be JOURNALFOERT")
        }
        val journalpost =
            safClient.getJournalpostAsSaksbehandler(dokumentUnderArbeid.journalpostId)
        val dokumentInDokarkiv =
            journalpost.dokumenter?.find { it.dokumentInfoId == dokumentUnderArbeid.dokumentInfoId }
                ?: throw RuntimeException("Document not found in Dokarkiv")

        return InnholdsfortegnelseRequest.Document(
            tittel = dokumentInDokarkiv.tittel ?: "Tittel ikke funnet i SAF",
            tema = Tema.fromNavn(journalpost.tema?.name).navn,
            opprettet = journalpost.datoOpprettet,
            avsenderMottaker = journalpost.avsenderMottaker?.navn ?: "",
            saksnummer = journalpost.sak?.fagsakId ?: "Saksnummer ikke funnet i SAF",
            type = journalpost.journalposttype?.name ?: "Type ikke funnet i SAF"
        )
    }

    fun mapToInnholdsfortegnelseRequestDocumentFromDokumentUnderArbeid(
        dokumentUnderArbeid: DokumentUnderArbeid,
        mottakere: List<String> = emptyList(),
        behandling: Behandling,
        hoveddokument: DokumentUnderArbeid,
    ): InnholdsfortegnelseRequest.Document {
        return InnholdsfortegnelseRequest.Document(
            tittel = dokumentUnderArbeid.name,
            tema = behandling.ytelse.toTema().navn,
            opprettet = LocalDateTime.now(),
            avsenderMottaker = mottakere.joinToString(),
            saksnummer = behandling.fagsakId,
            type = if (hoveddokument.dokumentType == DokumentType.NOTAT) "N" else "U"
        )
    }

    fun mapToDokumentView(dokumentUnderArbeid: DokumentUnderArbeid): DokumentView {
        val type = dokumentUnderArbeid.getType()

        var journalfoertDokumentReference: DokumentView.JournalfoertDokumentReference? = null

        val dokumentInDokarkiv = if (type == DokumentUnderArbeid.DokumentUnderArbeidType.JOURNALFOERT) {
            val journalpostInDokarkiv =
                safClient.getJournalpostAsSaksbehandler(dokumentUnderArbeid.journalfoertDokumentReference!!.journalpostId)
            val dokumentInDokarkiv =
                journalpostInDokarkiv.dokumenter?.find { it.dokumentInfoId == dokumentUnderArbeid.journalfoertDokumentReference.dokumentInfoId }
                    ?: throw RuntimeException("Document not found in Dokarkiv")

            journalfoertDokumentReference =
                DokumentView.JournalfoertDokumentReference(
                    journalpostId = journalpostInDokarkiv.journalpostId,
                    dokumentInfoId = dokumentInDokarkiv.dokumentInfoId,
                    harTilgangTilArkivvariant = harTilgangTilArkivvariant(dokumentInDokarkiv),
                    datoOpprettet = journalpostInDokarkiv.datoOpprettet,
                )
            dokumentInDokarkiv
        } else null

        val tittel = if (dokumentInDokarkiv != null) {
            (dokumentInDokarkiv.tittel ?: "Tittel ikke funnet i SAF")
        } else dokumentUnderArbeid.name

        return DokumentView(
            id = dokumentUnderArbeid.id,
            tittel = tittel,
            dokumentTypeId = dokumentUnderArbeid.dokumentType?.id,
            created = dokumentUnderArbeid.created,
            modified = dokumentUnderArbeid.modified,
            isSmartDokument = dokumentUnderArbeid.smartEditorId != null,
            templateId = dokumentUnderArbeid.smartEditorTemplateId,
            isMarkertAvsluttet = dokumentUnderArbeid.markertFerdig != null,
            parent = dokumentUnderArbeid.parentId,
            parentId = dokumentUnderArbeid.parentId,
            type = type,
            journalfoertDokumentReference = journalfoertDokumentReference,
            creatorIdent = dokumentUnderArbeid.creatorIdent,
            creatorRole = dokumentUnderArbeid.creatorRole,
        )
    }

    fun mapToDokumentListView(
        dokumentUnderArbeidList: List<DokumentUnderArbeid>,
        duplicateJournalfoerteDokumenter: List<DokumentUnderArbeid>
    ): DokumentViewWithList {
        val firstDokument = dokumentUnderArbeidList.firstOrNull()
        val firstDokumentView = if (firstDokument != null) {
            mapToDokumentView(dokumentUnderArbeidList.first())
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
            alteredDocuments = dokumentUnderArbeidList.map { mapToDokumentView(it) },
            duplicateJournalfoerteDokumenter = duplicateJournalfoerteDokumenter.map { mapToDokumentView(it) },
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
            templateId = dokumentUnderArbeid.smartEditorTemplateId,
            parent = dokumentUnderArbeid.parentId,
            parentId = dokumentUnderArbeid.parentId,
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
}