package no.nav.klage.oppgave.service

import no.nav.klage.kodeverk.PartIdType
import no.nav.klage.kodeverk.Tema
import no.nav.klage.oppgave.api.mapper.BehandlingMapper
import no.nav.klage.oppgave.api.view.BehandlingDetaljerView
import no.nav.klage.oppgave.clients.ereg.EregClient
import no.nav.klage.oppgave.clients.krrproxy.KrrProxyClient
import no.nav.klage.oppgave.clients.pdl.PdlFacade
import no.nav.klage.oppgave.exceptions.MissingTilgangException
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getPartIdFromIdentifikator
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service

@Service
class PartSearchService(
    private val eregClient: EregClient,
    private val pdlFacade: PdlFacade,
    private val tilgangService: TilgangService,
    private val behandlingMapper: BehandlingMapper,
    private val krrProxyClient: KrrProxyClient,
    private val regoppslagService: RegoppslagService,
    private val dokDistKanalService: DokDistKanalService,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun searchPart(identifikator: String, systemUserContext: Boolean = false): BehandlingDetaljerView.SearchPartView =
        when (getPartIdFromIdentifikator(identifikator).type) {
            PartIdType.PERSON -> {
                if (systemUserContext || tilgangService.harInnloggetSaksbehandlerTilgangTil(identifikator).access) {
                    val person = pdlFacade.getPersonInfo(identifikator)
                    val krrInfo = if (systemUserContext) {
                        krrProxyClient.getDigitalKontaktinformasjonForFnrAppAccess(identifikator)
                    } else {
                        krrProxyClient.getDigitalKontaktinformasjonForFnrOnBehalfOf(identifikator)
                    }
                    BehandlingDetaljerView.SearchPartView(
                        identifikator = person.foedselsnr,
                        name = person.settSammenNavn(),
                        type = BehandlingDetaljerView.IdType.FNR,
                        available = person.doed == null,
                        language = krrInfo?.spraak,
                        statusList = behandlingMapper.getStatusList(person, krrInfo),
                        address = if (systemUserContext) {
                            regoppslagService.getAddressForPersonAppAccess(fnr = identifikator)
                        } else {
                            regoppslagService.getAddressForPersonOnBehalfOf(fnr = identifikator)
                        }
                    )
                } else {
                    val access = tilgangService.harInnloggetSaksbehandlerTilgangTil(identifikator)
                    logger.warn("Saksbehandler does not have access to view person due to: {}", access.reason)
                    throw MissingTilgangException(access.reason)
                }
            }

            PartIdType.VIRKSOMHET -> {
                val organisasjon = eregClient.hentNoekkelInformasjonOmOrganisasjon(identifikator)
                BehandlingDetaljerView.SearchPartView(
                    identifikator = organisasjon.organisasjonsnummer,
                    name = organisasjon.navn.sammensattnavn,
                    type = BehandlingDetaljerView.IdType.ORGNR,
                    available = organisasjon.isActive(),
                    language = null,
                    statusList = behandlingMapper.getStatusList(organisasjon),
                    address = behandlingMapper.getAddress(organisasjon),
                )
            }
        }

    @Retryable
    fun searchPartWithUtsendingskanal(
        identifikator: String,
        systemUserContext: Boolean = false,
        sakenGjelderId: String,
        tema: Tema,
        systemContext: Boolean,
    ): BehandlingDetaljerView.SearchPartViewWithUtsendingskanal =
        when (getPartIdFromIdentifikator(identifikator).type) {
            PartIdType.PERSON -> {
                if (systemUserContext || tilgangService.harInnloggetSaksbehandlerTilgangTil(identifikator).access) {
                    val person = pdlFacade.getPersonInfo(identifikator)
                    val krrInfo = krrProxyClient.getDigitalKontaktinformasjonForFnrOnBehalfOf(identifikator)
                    BehandlingDetaljerView.SearchPartViewWithUtsendingskanal(
                        identifikator = person.foedselsnr,
                        name = person.settSammenNavn(),
                        type = BehandlingDetaljerView.IdType.FNR,
                        available = person.doed == null,
                        language = krrInfo?.spraak,
                        statusList = behandlingMapper.getStatusList(person, krrInfo),
                        address = regoppslagService.getAddressForPersonOnBehalfOf(fnr = identifikator),
                        utsendingskanal = dokDistKanalService.getUtsendingskanal(
                            mottakerId = identifikator,
                            brukerId = sakenGjelderId,
                            tema = tema,
                            saksbehandlerContext = !systemContext,
                        )
                    )
                } else {
                    val access = tilgangService.harInnloggetSaksbehandlerTilgangTil(identifikator)
                    logger.warn("Saksbehandler does not have access to view person due to: {}", access.reason)
                    throw MissingTilgangException(access.reason)
                }
            }

            PartIdType.VIRKSOMHET -> {
                val organisasjon = eregClient.hentNoekkelInformasjonOmOrganisasjon(identifikator)
                BehandlingDetaljerView.SearchPartViewWithUtsendingskanal(
                    identifikator = organisasjon.organisasjonsnummer,
                    name = organisasjon.navn.sammensattnavn,
                    type = BehandlingDetaljerView.IdType.ORGNR,
                    available = organisasjon.isActive(),
                    language = null,
                    statusList = behandlingMapper.getStatusList(organisasjon),
                    address = behandlingMapper.getAddress(organisasjon),
                    utsendingskanal = dokDistKanalService.getUtsendingskanal(
                        mottakerId = identifikator,
                        brukerId = sakenGjelderId,
                        tema = tema,
                        saksbehandlerContext = !systemContext,
                    )
                )
            }
        }

    fun searchPerson(
        identifikator: String,
        systemUserContext: Boolean = false
    ): BehandlingDetaljerView.SearchPersonView =
        when (getPartIdFromIdentifikator(identifikator).type) {
            PartIdType.PERSON -> {
                if (systemUserContext || tilgangService.harInnloggetSaksbehandlerTilgangTil(identifikator).access) {
                    val person = pdlFacade.getPersonInfo(identifikator)
                    val krrInfo = krrProxyClient.getDigitalKontaktinformasjonForFnrOnBehalfOf(identifikator)
                    BehandlingDetaljerView.SearchPersonView(
                        identifikator = person.foedselsnr,
                        name = person.settSammenNavn(),
                        type = BehandlingDetaljerView.IdType.FNR,
                        available = person.doed == null,
                        sex = person.kjoenn?.let { BehandlingDetaljerView.Sex.valueOf(it) }
                            ?: BehandlingDetaljerView.Sex.UKJENT,
                        language = krrInfo?.spraak,
                        statusList = behandlingMapper.getStatusList(person, krrInfo),
                        address = regoppslagService.getAddressForPersonOnBehalfOf(fnr = identifikator),
                    )
                } else {
                    val access = tilgangService.harInnloggetSaksbehandlerTilgangTil(identifikator)
                    logger.warn("Saksbehandler does not have access to view person due to: {}", access.reason)
                    throw MissingTilgangException(access.reason)
                }
            }

            else -> throw RuntimeException(
                "Invalid part type: " + getPartIdFromIdentifikator(
                    identifikator
                ).type
            )
        }
}