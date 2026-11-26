package no.nav.klage.oppgave.service

import no.nav.klage.kodeverk.Enhet
import no.nav.klage.kodeverk.Type
import no.nav.klage.oppgave.clients.kant.KantApiClient
import no.nav.klage.oppgave.clients.kant.KantAvslutningInput
import no.nav.klage.oppgave.clients.kant.KantSaksdata
import no.nav.klage.oppgave.clients.kant.KantSaksdataInput
import no.nav.klage.oppgave.clients.kant.KantUpdateInput
import no.nav.klage.oppgave.domain.behandling.AnkeITrygderettenbehandlingEtter2027
import no.nav.klage.oppgave.domain.behandling.AnkebehandlingEtter2027
import no.nav.klage.oppgave.domain.behandling.Behandling
import no.nav.klage.oppgave.domain.behandling.BehandlingWithKantReference
import no.nav.klage.oppgave.domain.behandling.BehandlingWithTrygderettenSaksnummer
import no.nav.klage.oppgave.domain.behandling.GjenopptakITrygderettenbehandlingEtter2027
import no.nav.klage.oppgave.domain.behandling.GjenopptaksbehandlingEtter2027
import no.nav.klage.oppgave.exceptions.InvalidProperty
import no.nav.klage.oppgave.util.getLogger
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Service
class KantService(
    private val kantApiClient: KantApiClient,
    @Value($$"#{T(java.time.LocalDate).parse('${USE_KANT_FOR_OLD_TYPES_DATE}')}")
    private val useKantForOldTypesDate: LocalDate,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun kantShouldBeUsedForThisBehandling(behandling: Behandling): Boolean {
        if (behandling !is BehandlingWithKantReference) return false
        return when (behandling.type) {
            Type.ANKE_FOER_2027,
            Type.ANKE_I_TRYGDERETTEN_FOER_2027,
            Type.BEGJAERING_OM_GJENOPPTAK_FOER_2027,
            Type.BEGJAERING_OM_GJENOPPTAK_I_TRYGDERETTEN_FOER_2027,
            -> kantShouldBeUsedForOldTypes()

            Type.KLAGE,
            Type.BEHANDLING_ETTER_TRYGDERETTEN_OPPHEVET,
            Type.OMGJOERINGSKRAV,
            -> false

            Type.ANKE_ETTER_2027,
            Type.ANKE_I_TRYGDERETTEN_ETTER_2027,
            Type.BEGJAERING_OM_GJENOPPTAK_ETTER_2027,
            Type.BEGJAERING_OM_GJENOPPTAK_I_TRYGDERETTEN_ETTER_2027,
            -> true
        }
    }

    fun kantShouldBeUsedForOldTypes(): Boolean =
        (LocalDate.now().isAfter(useKantForOldTypesDate) || LocalDate.now().isEqual(useKantForOldTypesDate))

    fun createSaksdataInKant(behandling: BehandlingWithKantReference): KantSaksdata {
        behandling as Behandling
        val response =
            kantApiClient.createSaksdata(
                KantSaksdataInput(
                    kabalReference = behandling.id,
                    sakstype = behandling.type,
                    ytelse = behandling.ytelse,
                    trygderettenSaksnummer = if (behandling is BehandlingWithTrygderettenSaksnummer) behandling.trygderettenSaksnummer else null,
                ),
            )
        validateIdentity(behandling, response)
        check(behandling.kantReference == null || behandling.kantReference == response.id) {
            "Kant reference changed during creation"
        }
        return response
    }

    fun finalizeSaksdata(behandling: BehandlingWithKantReference) {
        behandling as Behandling
        check(behandling.feilregistrering == null) { "Cannot finalize Kant for feilregistrert behandling" }
        val tilknyttetEnhet = Enhet.entries.find { it.navn == behandling.tildeling?.enhet!! }
        val sakITrygderettenOpprettet =
            if (behandling is AnkeITrygderettenbehandlingEtter2027 || behandling is GjenopptakITrygderettenbehandlingEtter2027) {
                behandling.sendtTilTrygderetten.toLocalDate()
            } else {
                null
            }
        val mottattKlageinstans =
            if (behandling is AnkebehandlingEtter2027 || behandling is GjenopptaksbehandlingEtter2027) {
                behandling.mottattKlageinstans.toLocalDate()
            } else {
                null
            }
        val input =
            KantAvslutningInput(
                utfall = checkNotNull(behandling.utfall) { "Cannot finalize Kant without utfall" },
                registreringshjemmelIdSet = behandling.registreringshjemler.map { it.id }.toSet(),
                avsluttetAvSaksbehandler =
                    checkNotNull(behandling.ferdigstilling) {
                        "Cannot finalize Kant before user completion"
                    }.avsluttetAvSaksbehandler.truncatedTo(ChronoUnit.MICROS),
                sakITrygderettenOpprettet = sakITrygderettenOpprettet,
                mottattKlageinstans = mottattKlageinstans,
                tilknyttetEnhetId = tilknyttetEnhet!!.id,
                tilbakekreving = behandling.tilbakekreving,
                trygderettenSaksnummer = if (behandling is BehandlingWithTrygderettenSaksnummer) behandling.trygderettenSaksnummer else null,
            )

        val reference = checkNotNull(behandling.kantReference)
        val response = kantApiClient.finalizeSaksdata(id = reference, input = input)
        validateIdentity(behandling = behandling, response = response)
    }

    fun updateSaksdata(behandling: BehandlingWithKantReference) {
        behandling as Behandling
        val input =
            KantUpdateInput(
                registreringshjemmelIdSet = behandling.registreringshjemler.map { it.id }.toSet(),
                trygderettenSaksnummer = if (behandling is BehandlingWithTrygderettenSaksnummer) behandling.trygderettenSaksnummer else null,
            )
        val reference = checkNotNull(behandling.kantReference)
        val response = kantApiClient.updateSaksdata(id = reference, input = input)
        validateIdentity(behandling = behandling, response = response)
    }

    fun deleteSaksdata(behandling: BehandlingWithKantReference) {
        val reference = behandling.kantReference ?: return
        kantApiClient.deleteSaksdata(id = reference)
    }

    fun getValidationErrors(behandling: BehandlingWithKantReference): List<InvalidProperty> =
        behandling.kantReference?.let { kantApiClient.getValidationErrors(kantId = it) }?.validationErrors?.map {
            InvalidProperty(
                field = it.field,
                reason = it.reason,
            )
        } ?: emptyList()

    private fun validateIdentity(
        behandling: BehandlingWithKantReference,
        response: KantSaksdata,
    ) {
        behandling as Behandling
        if (response.kabalReference != behandling.id || response.sakstype != behandling.type ||
            response.ytelse != behandling.ytelse
        ) {
            logger.error("Kant returned an inconsistent identity for behandling {}", behandling.id)
            error("Kant identity does not match behandling")
        }
    }
}
