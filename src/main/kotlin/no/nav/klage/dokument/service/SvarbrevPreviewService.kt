package no.nav.klage.dokument.service

import no.nav.klage.dokument.api.view.PreviewSvarbrevAnonymousInput
import no.nav.klage.dokument.api.view.PreviewSvarbrevInput
import no.nav.klage.dokument.domain.dokumenterunderarbeid.Svarbrev
import no.nav.klage.dokument.exceptions.SvarbrevPreviewException
import no.nav.klage.kodeverk.Enhet
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.Ytelse
import no.nav.klage.oppgave.gateway.AzureGateway
import no.nav.klage.oppgave.service.PartSearchService
import no.nav.klage.oppgave.service.SvarbrevSettingsService
import no.nav.klage.oppgave.util.getLogger
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class SvarbrevPreviewService(
    private val partSearchService: PartSearchService,
    private val kabalJsonToPdfService: KabalJsonToPdfService,
    private val azureGateway: AzureGateway,
    private val svarbrevSettingsService: SvarbrevSettingsService,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun getSvarbrevPreviewPDF(
        input: PreviewSvarbrevInput
    ): ByteArray {
        if (input.svarbrev.type !in listOf(Type.KLAGE, Type.ANKE)) {
            throw SvarbrevPreviewException("Forhåndsvisning av svarbrev er bare tilgjengelig for Klage og Anke.")
        }

        val sakenGjelderName = partSearchService.searchPart(
            identifikator = input.sakenGjelder.value,
            skipAccessControl = true
        ).name

        return kabalJsonToPdfService.getSvarbrevPDF(
            svarbrev = input.svarbrev,
            mottattKlageinstans = input.mottattKlageinstans,
            sakenGjelderIdentifikator = input.sakenGjelder.value,
            sakenGjelderName = sakenGjelderName,
            ytelse = Ytelse.of(input.ytelseId),
            klagerIdentifikator = input.klager?.value ?: input.sakenGjelder.value,
            klagerName = if (input.klager != null) {
                partSearchService.searchPart(
                    identifikator = input.klager.value,
                    skipAccessControl = true
                ).name
            } else {
                sakenGjelderName
            },
            avsenderEnhetId = Enhet.E4291.navn,
        )
    }

    fun getAnonymousSvarbrevPreviewPDF(
        input: PreviewSvarbrevAnonymousInput
    ): ByteArray {
        val svarbrevSettings = svarbrevSettingsService.getSvarbrevSettings(ytelse = Ytelse.of(input.ytelseId))
        val mockName = "Navn Navnesen"
        val mockIdentifikator = "123456789101"

        if (input.typeId !in listOf(Type.KLAGE.id, Type.ANKE.id)) {
            throw SvarbrevPreviewException("Forhåndsvisning av svarbrev er bare tilgjengelig for Klage og Anke.")
        }

        return kabalJsonToPdfService.getSvarbrevPDF(
            svarbrev = Svarbrev(
                title = "NAV orienterer om saksbehandlingen",
                receivers = listOf(),
                fullmektigFritekst = null,
                varsletBehandlingstidUnits = svarbrevSettings.behandlingstidUnits,
                varsletBehandlingstidUnitType = svarbrevSettings.behandlingstidUnitType,
                type = Type.of(input.typeId),
                customText = svarbrevSettings.customText
            ),
            mottattKlageinstans = LocalDate.now(),
            sakenGjelderIdentifikator = mockIdentifikator,
            sakenGjelderName = mockName,
            ytelse = Ytelse.of(input.ytelseId),
            klagerIdentifikator = mockIdentifikator,
            klagerName = mockName,
            avsenderEnhetId = Enhet.E4291.navn,
        )
    }
}