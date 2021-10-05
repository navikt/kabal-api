package no.nav.klage.oppgave.service.mapper


import no.nav.klage.oppgave.clients.egenansatt.EgenAnsattService
import no.nav.klage.oppgave.clients.ereg.EregClient
import no.nav.klage.oppgave.clients.pdl.PdlFacade
import no.nav.klage.oppgave.domain.elasticsearch.EsKlagebehandling
import no.nav.klage.oppgave.domain.elasticsearch.EsSaksdokument
import no.nav.klage.oppgave.domain.elasticsearch.EsVedtak
import no.nav.klage.oppgave.domain.klage.Klagebehandling
import no.nav.klage.oppgave.domain.klage.PartId
import no.nav.klage.oppgave.domain.kodeverk.PartIdType
import no.nav.klage.oppgave.service.SaksbehandlerService
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import org.springframework.stereotype.Service

@Service
class EsKlagebehandlingMapper(
    private val pdlFacade: PdlFacade,
    private val egenAnsattService: EgenAnsattService,
    private val saksbehandlerService: SaksbehandlerService,
    private val eregClient: EregClient
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
    }

    fun mapKlagebehandlingOgMottakToEsKlagebehandling(klagebehandling: Klagebehandling): EsKlagebehandling {
        val klagerFnr = foedselsnummer(klagebehandling.klager.partId)
        val klagerPersonInfo = klagerFnr?.let { pdlFacade.getPersonInfo(it) }

        val klagerOrgnr = virksomhetsnummer(klagebehandling.klager.partId)
        val klagerOrgnavn = klagerOrgnr?.let { eregClient.hentOrganisasjon(it)?.navn?.sammensattNavn() }

        val sakenGjelderFnr = foedselsnummer(klagebehandling.sakenGjelder.partId)
        val sakenGjelderPersonInfo = sakenGjelderFnr?.let { pdlFacade.getPersonInfo(it) }

        val sakenGjelderOrgnr = virksomhetsnummer(klagebehandling.sakenGjelder.partId)
        val sakenGjelderOrgnavn = sakenGjelderOrgnr?.let { eregClient.hentOrganisasjon(it)?.navn?.sammensattNavn() }

        val erFortrolig = sakenGjelderPersonInfo?.harBeskyttelsesbehovFortrolig() ?: false
        val erStrengtFortrolig = sakenGjelderPersonInfo?.harBeskyttelsesbehovStrengtFortrolig() ?: false
        val erEgenAnsatt = sakenGjelderFnr?.let { egenAnsattService.erEgenAnsatt(it) } ?: false

        return EsKlagebehandling(
            id = klagebehandling.id.toString(),
            versjon = klagebehandling.versjon,
            klagerFnr = klagerFnr,
            klagerNavn = klagerPersonInfo?.sammensattNavn,
            klagerFornavn = klagerPersonInfo?.fornavn,
            klagerMellomnavn = klagerPersonInfo?.mellomnavn,
            klagerEtternavn = klagerPersonInfo?.etternavn,
            klagerOrgnr = klagerOrgnr,
            klagerOrgnavn = klagerOrgnavn,
            sakenGjelderFnr = sakenGjelderFnr,
            sakenGjelderNavn = sakenGjelderPersonInfo?.sammensattNavn,
            sakenGjelderFornavn = sakenGjelderPersonInfo?.fornavn,
            sakenGjelderMellomnavn = sakenGjelderPersonInfo?.mellomnavn,
            sakenGjelderEtternavn = sakenGjelderPersonInfo?.etternavn,
            sakenGjelderOrgnr = sakenGjelderOrgnr,
            sakenGjelderOrgnavn = sakenGjelderOrgnavn,
            tema = klagebehandling.tema.id,
            type = klagebehandling.type.id,
            kildeReferanse = klagebehandling.kildeReferanse,
            sakFagsystem = klagebehandling.sakFagsystem?.id,
            sakFagsakId = klagebehandling.sakFagsakId,
            innsendt = klagebehandling.innsendt,
            mottattFoersteinstans = klagebehandling.mottattFoersteinstans,
            avsenderSaksbehandleridentFoersteinstans = klagebehandling.avsenderSaksbehandleridentFoersteinstans,
            avsenderEnhetFoersteinstans = klagebehandling.avsenderEnhetFoersteinstans,
            mottattKlageinstans = klagebehandling.mottattKlageinstans,
            tildelt = klagebehandling.tildeling?.tidspunkt,
            foerstTildelt = klagebehandling.tildelingHistorikk.map { it.tildeling.tidspunkt }.minOrNull(),
            sistTildelt = klagebehandling.tildeling?.tidspunkt,
            avsluttet = klagebehandling.avsluttet,
            avsluttetAvSaksbehandler = klagebehandling.avsluttetAvSaksbehandler,
            frist = klagebehandling.frist,
            tildeltSaksbehandlerident = klagebehandling.tildeling?.saksbehandlerident,
            tildeltSaksbehandlernavn = getTildeltSaksbehandlernavn(klagebehandling),
            medunderskriverident = klagebehandling.medunderskriver?.saksbehandlerident,
            medunderskriverFlyt = klagebehandling.medunderskriverFlyt.navn,
            sendtMedunderskriver = klagebehandling.medunderskriver?.tidspunkt,
            tildeltEnhet = klagebehandling.tildeling?.enhet,
            hjemler = klagebehandling.hjemler.map { it.id },
            created = klagebehandling.created,
            modified = klagebehandling.modified,
            kilde = klagebehandling.kildesystem.id,
            kommentarFraFoersteinstans = klagebehandling.kommentarFraFoersteinstans,
            vedtak = klagebehandling.vedtak?.let { vedtak ->
                EsVedtak(
                    utfall = vedtak.utfall?.id,
                    grunn = vedtak.grunn?.id,
                    hjemler = vedtak.hjemler.map { hjemmel -> hjemmel.id },
                    brevmottakerFnr = vedtak.brevmottakere.filter { it.partId.type == PartIdType.PERSON }
                        .map { it.partId.value },
                    brevmottakerOrgnr = vedtak.brevmottakere.filter { it.partId.type == PartIdType.VIRKSOMHET }
                        .map { it.partId.value },
                    journalpostId = vedtak.journalpostId,
                    created = vedtak.created,
                    modified = vedtak.modified,
                    ferdigstiltIJoark = vedtak.ferdigstiltIJoark
                )
            },
            saksdokumenter = klagebehandling.saksdokumenter.map { EsSaksdokument(it.journalpostId, it.dokumentInfoId) },
            saksdokumenterJournalpostId = klagebehandling.saksdokumenter.map { it.journalpostId },
            saksdokumenterJournalpostIdOgDokumentInfoId = klagebehandling.saksdokumenter.map {
                it.journalpostId + it.dokumentInfoId
            },
            egenAnsatt = erEgenAnsatt,
            fortrolig = erFortrolig,
            strengtFortrolig = erStrengtFortrolig,
            vedtakUtfall = klagebehandling.vedtak?.utfall?.id,
            vedtakGrunn = klagebehandling.vedtak?.grunn?.id,
            vedtakHjemler = klagebehandling.vedtak?.hjemler?.map { hjemmel -> hjemmel.id } ?: emptyList(),
            vedtakBrevmottakerFnr = klagebehandling.vedtak?.brevmottakere?.filter { it.partId.type == PartIdType.PERSON }
                ?.map { it.partId.value } ?: emptyList(),
            vedtakBrevmottakerOrgnr = klagebehandling.vedtak?.brevmottakere?.filter { it.partId.type == PartIdType.VIRKSOMHET }
                ?.map { it.partId.value } ?: emptyList(),
            vedtakJournalpostId = klagebehandling.vedtak?.journalpostId,
            vedtakCreated = klagebehandling.vedtak?.created,
            vedtakModified = klagebehandling.vedtak?.modified,
            vedtakFerdigstiltIJoark = klagebehandling.vedtak?.ferdigstiltIJoark,
            temaNavn = klagebehandling.tema.name,
            typeNavn = klagebehandling.type.name,
            hjemlerNavn = klagebehandling.hjemler.map { it.name },
            vedtakUtfallNavn = klagebehandling.vedtak?.utfall?.name,
            vedtakGrunnNavn = klagebehandling.vedtak?.grunn?.name,
            sakFagsystemNavn = klagebehandling.sakFagsystem?.name,
            status = EsKlagebehandling.Status.valueOf(klagebehandling.getStatus().name)
        )
    }

    private fun getTildeltSaksbehandlernavn(klagebehandling: Klagebehandling): String? {
        return klagebehandling.tildeling?.saksbehandlerident?.let {
            val names = saksbehandlerService.getNamesForSaksbehandlere(
                setOf(it)
            )
            names[it]
        }
    }

    private fun foedselsnummer(partId: PartId) =
        if (partId.type == PartIdType.PERSON) {
            partId.value
        } else {
            null
        }

    private fun virksomhetsnummer(partId: PartId) =
        if (partId.type == PartIdType.VIRKSOMHET) {
            partId.value
        } else {
            null
        }
}

