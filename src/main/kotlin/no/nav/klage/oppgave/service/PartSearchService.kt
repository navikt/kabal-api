package no.nav.klage.oppgave.service

import no.nav.klage.kodeverk.PartIdType
import no.nav.klage.oppgave.api.mapper.BehandlingMapper
import no.nav.klage.oppgave.api.view.BehandlingDetaljerView
import no.nav.klage.oppgave.clients.ereg.EregClient
import no.nav.klage.oppgave.clients.krrproxy.KrrProxyClient
import no.nav.klage.oppgave.clients.pdl.PdlFacade
import no.nav.klage.oppgave.exceptions.MissingTilgangException
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getPartIdFromIdentifikator
import no.nav.klage.oppgave.util.getSecureLogger
import org.springframework.stereotype.Service

@Service
class PartSearchService(
    private val eregClient: EregClient,
    private val pdlFacade: PdlFacade,
    private val tilgangService: TilgangService,
    private val behandlingMapper: BehandlingMapper,
    private val krrProxyClient: KrrProxyClient,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
    }

    fun searchPart(identifikator: String, skipAccessControl: Boolean = false): BehandlingDetaljerView.PartView =
        when (getPartIdFromIdentifikator(identifikator).type) {
            PartIdType.PERSON -> {
                if (skipAccessControl || tilgangService.harInnloggetSaksbehandlerTilgangTil(identifikator)) {
                    val person = pdlFacade.getPersonInfo(identifikator)
                    val krrInfo = krrProxyClient.getDigitalKontaktinformasjonForFnr(identifikator)
                    BehandlingDetaljerView.PartView(
                        id = person.foedselsnr,
                        name = person.settSammenNavn(),
                        type = BehandlingDetaljerView.IdType.FNR,
                        available = person.doed == null,
                        language = krrInfo?.spraak,
                        statusList = behandlingMapper.getStatusList(person, krrInfo),
                        address = null,
                    )
                } else {
                    secureLogger.warn("Saksbehandler does not have access to view person")
                    throw MissingTilgangException("Saksbehandler does not have access to view person")
                }
            }

            PartIdType.VIRKSOMHET -> {
                val organisasjon = eregClient.hentNoekkelInformasjonOmOrganisasjon(identifikator)
                BehandlingDetaljerView.PartView(
                    id = organisasjon.organisasjonsnummer,
                    name = organisasjon.navn.sammensattnavn,
                    type = BehandlingDetaljerView.IdType.ORGNR,
                    available = organisasjon.isActive(),
                    language = null,
                    statusList = behandlingMapper.getStatusList(organisasjon),
                    address = behandlingMapper.getAddress(organisasjon),
                )
            }
        }

    fun searchPerson(
        identifikator: String,
        skipAccessControl: Boolean = false
    ): BehandlingDetaljerView.SakenGjelderView =
        when (getPartIdFromIdentifikator(identifikator).type) {
            PartIdType.PERSON -> {
                if (skipAccessControl || tilgangService.harInnloggetSaksbehandlerTilgangTil(identifikator)) {
                    val person = pdlFacade.getPersonInfo(identifikator)
                    val krrInfo = krrProxyClient.getDigitalKontaktinformasjonForFnr(identifikator)
                    BehandlingDetaljerView.SakenGjelderView(
                        id = person.foedselsnr,
                        name = person.settSammenNavn(),
                        type = BehandlingDetaljerView.IdType.FNR,
                        available = person.doed == null,
                        sex = person.kjoenn?.let { BehandlingDetaljerView.Sex.valueOf(it) }
                            ?: BehandlingDetaljerView.Sex.UKJENT,
                        language = krrInfo?.spraak,
                        statusList = behandlingMapper.getStatusList(person, krrInfo),
                    )
                } else {
                    secureLogger.warn("Saksbehandler does not have access to view person")
                    throw MissingTilgangException("Saksbehandler does not have access to view person")
                }
            }

            else -> throw RuntimeException(
                "Invalid part type: " + getPartIdFromIdentifikator(
                    identifikator
                ).type
            )
        }
}