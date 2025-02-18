package no.nav.klage.dokument.service

import no.nav.klage.dokument.api.view.PreviewForlengetBehandlingstidInput
import no.nav.klage.dokument.api.view.PreviewSvarbrevAnonymousInput
import no.nav.klage.dokument.api.view.PreviewSvarbrevInput
import no.nav.klage.dokument.domain.dokumenterunderarbeid.Svarbrev
import no.nav.klage.dokument.exceptions.SvarbrevPreviewException
import no.nav.klage.kodeverk.Enhet
import no.nav.klage.kodeverk.TimeUnitType
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.ytelse.Ytelse
import no.nav.klage.oppgave.service.BehandlingService
import no.nav.klage.oppgave.service.PartSearchService
import no.nav.klage.oppgave.util.getLogger
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class PreviewService(
    private val partSearchService: PartSearchService,
    private val kabalJsonToPdfService: KabalJsonToPdfService,
    private val behandlingService: BehandlingService
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun getSvarbrevPreviewPDF(
        input: PreviewSvarbrevInput
    ): ByteArray {
        if (Type.of(input.typeId) !in listOf(Type.KLAGE, Type.ANKE, Type.OMGJOERINGSKRAV)) {
            throw SvarbrevPreviewException("Forhåndsvisning av svarbrev er bare tilgjengelig for klage, anke og omgjøringskrav.")
        }

        val sakenGjelderName = partSearchService.searchPart(
            identifikator = input.sakenGjelder,
            skipAccessControl = true
        ).name

        return kabalJsonToPdfService.getSvarbrevPDF(
            svarbrev = input.toSvarbrev(),
            mottattKlageinstans = input.mottattKlageinstans,
            sakenGjelderIdentifikator = input.sakenGjelder,
            sakenGjelderName = sakenGjelderName,
            ytelse = Ytelse.of(input.ytelseId),
            klagerIdentifikator = input.klager ?: input.sakenGjelder,
            klagerName = if (input.klager != null) {
                partSearchService.searchPart(
                    identifikator = input.klager,
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
        if (Type.of(input.typeId) !in listOf(Type.KLAGE, Type.ANKE, Type.OMGJOERINGSKRAV)) {
            throw SvarbrevPreviewException("Forhåndsvisning av svarbrev er bare tilgjengelig for klage, anke og omgjøringskrav.")
        }

        val mockName = "Navn Navnesen"
        val mockIdentifikator = "123456789101"

        return kabalJsonToPdfService.getSvarbrevPDF(
            svarbrev = input.toSvarbrev(),
            mottattKlageinstans = LocalDate.now(),
            sakenGjelderIdentifikator = mockIdentifikator,
            sakenGjelderName = mockName,
            ytelse = Ytelse.of(input.ytelseId),
            klagerIdentifikator = mockIdentifikator,
            klagerName = mockName,
            avsenderEnhetId = Enhet.E4291.navn,
        )
    }

    fun getForlengetBehandlingstidPreviewPDF(
        input: PreviewForlengetBehandlingstidInput
    ): ByteArray {
        val behandling = behandlingService.getBehandlingForReadWithoutCheckForAccess(input.behandlingId)

        val type = behandling.type

        if (type !in listOf(Type.KLAGE, Type.ANKE, Type.OMGJOERINGSKRAV)) {
            throw SvarbrevPreviewException("Forhåndsvisning av forlenget behandlingstid er bare tilgjengelig for klage, anke og omgjøringskrav.")
        }

        val sakenGjelderName = partSearchService.searchPart(
            identifikator = behandling.sakenGjelder.partId.value,
            skipAccessControl = true
        ).name

        return kabalJsonToPdfService.getForlengetBehandlingstidPDF(
            title = input.title,
            sakenGjelderName = sakenGjelderName,
            sakenGjelderIdentifikator = behandling.sakenGjelder.partId.value,
            klagerIdentifikator = behandling.klager.partId.value,
            klagerName = if (behandling.klager.partId.value != behandling.sakenGjelder.partId.value) {
                partSearchService.searchPart(
                    identifikator = behandling.klager.partId.value,
                    skipAccessControl = true
                ).name
            } else {
                sakenGjelderName
            },
            ytelse = behandling.ytelse,
            fullmektigFritekst = input.fullmektigFritekst,
            behandlingstidUnits = input.behandlingstidUnits,
            behandlingstidUnitType = input.behandlingstidUnitTypeId?.let { TimeUnitType.of(it) },
            avsenderEnhetId = Enhet.E4291.navn,
            type = type,
            mottattKlageinstans = behandling.mottattKlageinstans.toLocalDate(),
            previousBehandlingstidInfo = input.previousBehandlingstidInfo,
            reason = input.reason,
            behandlingstidDate = input.behandlingstidDate,

            )
    }

    private fun PreviewSvarbrevAnonymousInput.toSvarbrev(): Svarbrev {
        return Svarbrev(
            title = "Nav klageinstans orienterer om saksbehandlingen",
            receivers = listOf(),
            fullmektigFritekst = null,
            varsletBehandlingstidUnits = behandlingstidUnits,
            varsletBehandlingstidUnitType = getTimeUnitType(
                varsletBehandlingstidUnitTypeId = behandlingstidUnitTypeId,
                varsletBehandlingstidUnitType = behandlingstidUnitType
            ),
            type = Type.of(typeId),
            initialCustomText = initialCustomText,
            customText = customText,
        )
    }

    private fun PreviewSvarbrevInput.toSvarbrev(): Svarbrev {
        return Svarbrev(
            title = title,
            receivers = listOf(),
            fullmektigFritekst = fullmektigFritekst,
            varsletBehandlingstidUnits = varsletBehandlingstidUnits,
            varsletBehandlingstidUnitType = getTimeUnitType(
                varsletBehandlingstidUnitTypeId = varsletBehandlingstidUnitTypeId,
                varsletBehandlingstidUnitType = varsletBehandlingstidUnitType
            ),
            type = Type.of(typeId),
            initialCustomText = initialCustomText,
            customText = customText
        )
    }

    private fun getTimeUnitType(
        varsletBehandlingstidUnitTypeId: String?,
        varsletBehandlingstidUnitType: TimeUnitType?
    ): TimeUnitType {
        return if (varsletBehandlingstidUnitTypeId != null) {
            TimeUnitType.of(varsletBehandlingstidUnitTypeId)
        } else {
            varsletBehandlingstidUnitType!!
        }
    }
}