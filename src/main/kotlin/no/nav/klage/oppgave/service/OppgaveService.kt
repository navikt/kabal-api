package no.nav.klage.oppgave.service

import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.Root
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.hjemmel.Registreringshjemmel
import no.nav.klage.kodeverk.ytelse.Ytelse
import no.nav.klage.oppgave.api.view.*
import no.nav.klage.oppgave.domain.behandling.Behandling
import no.nav.klage.oppgave.domain.behandling.Behandling_
import no.nav.klage.oppgave.domain.behandling.embedded.Ferdigstilling_
import no.nav.klage.oppgave.domain.behandling.embedded.Tildeling_
import no.nav.klage.oppgave.repositories.BehandlingRepository
import no.nav.klage.oppgave.util.getLogger
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.util.*


@Service
class OppgaveService(
    private val behandlingRepository: BehandlingRepository,
    private val innloggetSaksbehandlerService: InnloggetSaksbehandlerService,
    private val saksbehandlerService: SaksbehandlerService,
    private val tilgangService: TilgangService
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun getFerdigstilteOppgaverForNavIdent(queryParams: MineFerdigstilteOppgaverQueryParams): BehandlingerListResponse {
        var specification: Specification<Behandling> =
            Specification { root: Root<Behandling>, _, builder: CriteriaBuilder ->
                builder.isNotNull(root.get(Behandling_.ferdigstilling))
            }

        specification = addInnloggetSaksbehandler(specification)
        specification = addTypeSpecifications(queryParams, specification)
        specification = addYtelseSpecifications(queryParams, specification)
        specification = addRegistreringshjemmelSpecifications(queryParams, specification)
        specification = addFerdigstiltFromToSpecifications(queryParams, specification)
        specification = addFristFromToSpecifications(queryParams, specification)

        val data = behandlingRepository.findAll(
            specification,
            Sort.by(
                getSortDirection(queryParams),
                getSortProperty(queryParams),
            ),
        )

        return BehandlingerListResponse(
            behandlinger = data.map { behandling -> behandling.id },
            antallTreffTotalt = data.size,
        )
    }

    fun getFerdigstilteOppgaverForEnhet(queryParams: EnhetensFerdigstilteOppgaverQueryParams): BehandlingerListResponse {
        var specification: Specification<Behandling> =
            Specification { root: Root<Behandling>, _, builder: CriteriaBuilder ->
                builder.isNotNull(root.get(Behandling_.ferdigstilling))
            }

        specification = addEnhet(specification)
        specification = addTypeSpecifications(queryParams, specification)
        specification = addYtelseSpecifications(queryParams, specification)
        specification = addRegistreringshjemmelSpecifications(queryParams, specification)
        specification = addFerdigstiltFromToSpecifications(queryParams, specification)
        specification = addFristFromToSpecifications(queryParams, specification)

        val data = behandlingRepository.findAll(
            specification,
            Sort.by(
                getSortDirection(queryParams),
                getSortProperty(queryParams),
            ),
        )

        return BehandlingerListResponse(
            behandlinger = data.map { behandling -> behandling.id },
            antallTreffTotalt = data.size,
        )
    }

    fun searchOppgaverByFagsakId(fagsakId: String): SearchSaksnummerResponse {
        val paaVentBehandlinger = mutableListOf<UUID>()
        val feilregistrerteBehandlinger = mutableListOf<UUID>()
        val avsluttedeBehandlinger = mutableListOf<UUID>()
        val aapneBehandlinger = mutableListOf<UUID>()

        //There could be multiple behandlinger for a fagsak b/c fagsakId is not unique.
        val behandlinger = behandlingRepository.findByFagsakId(fagsakId = fagsakId)

        behandlinger.forEach { behandling ->
            val access = tilgangService.getSaksbehandlerAccessToSak(
                fnr = behandling.sakenGjelder.partId.value,
                sakId = behandling.fagsakId,
                ytelse = behandling.ytelse,
                fagsystem = behandling.fagsystem,
            )

            if (access.access) {
                when {
                    behandling.feilregistrering != null -> {
                        feilregistrerteBehandlinger.add(behandling.id)
                    }
                    behandling.sattPaaVent != null -> {
                        paaVentBehandlinger.add(behandling.id)
                    }
                    behandling.ferdigstilling != null -> {
                        avsluttedeBehandlinger.add(behandling.id)
                    }
                    else -> {
                        aapneBehandlinger.add(behandling.id)
                    }
                }
            }
        }


        return SearchSaksnummerResponse(
            aapneBehandlinger = aapneBehandlinger,
            avsluttedeBehandlinger = avsluttedeBehandlinger,
            feilregistrerteBehandlinger = feilregistrerteBehandlinger,
            paaVentBehandlinger = paaVentBehandlinger,
        )
    }

    private fun getSortDirection(queryParams: CommonOppgaverQueryParams): Sort.Direction {
        val order = when (queryParams.rekkefoelge) {
            Rekkefoelge.STIGENDE -> {
                Sort.Direction.ASC
            }

            Rekkefoelge.SYNKENDE -> {
                Sort.Direction.DESC
            }
        }
        if (queryParams.sortering == Sortering.ALDER) {
            return if (order == Sort.Direction.ASC) {
                Sort.Direction.DESC
            } else {
                Sort.Direction.ASC
            }
        }
        return order
    }

    private fun getSortProperty(queryParams: CommonOppgaverQueryParams): String =
        when (queryParams.sortering) {
            Sortering.AVSLUTTET_AV_SAKSBEHANDLER -> {
                Behandling_.ferdigstilling.name + "." + Ferdigstilling_.avsluttetAvSaksbehandler.name
            }

            Sortering.MOTTATT -> {
                Behandling_.mottattKlageinstans.name
            }

            Sortering.FRIST -> {
                Behandling_.frist.name
            }

            Sortering.ALDER -> {
                Behandling_.mottattKlageinstans.name
            }

            Sortering.PAA_VENT_FROM -> TODO()
            Sortering.PAA_VENT_TO -> TODO()
            Sortering.RETURNERT_FRA_ROL -> TODO()
        }

    private fun addEnhet(specification: Specification<Behandling>) =
        specification.and { root: Root<Behandling>, _, builder: CriteriaBuilder ->
            builder.equal(
                root.get(Behandling_.tildeling).get(Tildeling_.enhet),
                saksbehandlerService.getEnhetForSaksbehandler(
                    navIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
                    systemContext = false,
                ).enhetId
            )
        }

    private fun addInnloggetSaksbehandler(specification: Specification<Behandling>) =
        specification.and { root: Root<Behandling>, _, builder: CriteriaBuilder ->
            builder.equal(
                root.get(Behandling_.tildeling).get(Tildeling_.saksbehandlerident),
                innloggetSaksbehandlerService.getInnloggetIdent()
            )
        }

    private fun addRegistreringshjemmelSpecifications(
        queryParams: FerdigstilteOppgaverQueryParams,
        mainSpecification: Specification<Behandling>
    ): Specification<Behandling> {
        var specification = mainSpecification
        if (queryParams.registreringshjemler.isNotEmpty()) {
            queryParams.registreringshjemler.forEach { registreringshjemmelId ->
                specification =
                    specification.and { root: Root<Behandling>, _, builder: CriteriaBuilder ->
                        builder.isMember(
                            Registreringshjemmel.of(registreringshjemmelId),
                            root.get(Behandling_.registreringshjemler)
                        )
                    }
            }
        }
        return specification
    }

    private fun addTypeSpecifications(
        queryParams: CommonOppgaverQueryParams,
        mainSpecification: Specification<Behandling>
    ): Specification<Behandling> {
        var specification = mainSpecification
        if (queryParams.typer.isNotEmpty()) {
            var typeSpecifications: Specification<Behandling>? = null
            queryParams.typer.forEach { typeId ->
                val type = Type.of(typeId)
                typeSpecifications = if (typeSpecifications == null) {
                    Specification { root, _, builder ->
                        builder.equal(
                            root.get(Behandling_.type),
                            type
                        )
                    }
                } else {
                    typeSpecifications!!.or { root, _, builder ->
                        builder.equal(
                            root.get(Behandling_.type),
                            type
                        )
                    }
                }
            }
            if (typeSpecifications != null) {
                specification = specification.and(typeSpecifications)
            }
        }
        return specification
    }

    private fun addYtelseSpecifications(
        queryParams: CommonOppgaverQueryParams,
        mainSpecification: Specification<Behandling>
    ): Specification<Behandling> {
        var specification = mainSpecification
        if (queryParams.ytelser.isNotEmpty()) {
            var ytelseSpecifications: Specification<Behandling>? = null
            queryParams.ytelser.forEach { ytelseId ->
                val ytelse = Ytelse.of(ytelseId)
                ytelseSpecifications = if (ytelseSpecifications == null) {
                    Specification<Behandling> { root, _, builder ->
                        builder.equal(
                            root.get(Behandling_.ytelse),
                            ytelse
                        )
                    }
                } else {
                    ytelseSpecifications!!.or { root, _, builder ->
                        builder.equal(
                            root.get(Behandling_.ytelse),
                            ytelse
                        )
                    }
                }
            }
            if (ytelseSpecifications != null) {
                specification = specification.and(ytelseSpecifications)
            }
        }
        return specification
    }

    private fun addFerdigstiltFromToSpecifications(
        queryParams: FerdigstilteOppgaverQueryParams,
        mainSpecification: Specification<Behandling>
    ): Specification<Behandling> {
        var specification = mainSpecification

        if (queryParams.ferdigstiltFrom == null && queryParams.ferdigstiltTo == null) {
            return specification
        }

        val from = queryParams.ferdigstiltFrom ?: LocalDate.now().minusDays(36500)
        val to = queryParams.ferdigstiltTo ?: LocalDate.now().plusDays(36500)

        specification =
            specification.and { root: Root<Behandling>, _, builder: CriteriaBuilder ->
                builder.greaterThanOrEqualTo(
                    root.get(Behandling_.ferdigstilling).get(Ferdigstilling_.avsluttetAvSaksbehandler),
                    from.atStartOfDay()
                )
            }

        specification =
            specification.and { root: Root<Behandling>, _, builder: CriteriaBuilder ->
                builder.lessThan(
                    root.get(Behandling_.ferdigstilling).get(Ferdigstilling_.avsluttetAvSaksbehandler),
                    to!!.plusDays(1).atStartOfDay()
                )
            }

        return specification
    }

    private fun addFristFromToSpecifications(
        queryParams: CommonOppgaverQueryParams,
        mainSpecification: Specification<Behandling>
    ): Specification<Behandling> {

        if (queryParams.fristFrom == null && queryParams.fristTo == null) {
            return mainSpecification
        }

        val from = queryParams.fristFrom ?: LocalDate.now().minusDays(3650)
        val to = queryParams.fristTo ?: LocalDate.now().plusDays(3650)

        val betweenDates = Specification { root, _, builder ->
            builder.greaterThanOrEqualTo(
                root.get(Behandling_.frist),
                from
            )
        }.and { root: Root<Behandling>, _, builder: CriteriaBuilder ->
            builder.lessThanOrEqualTo(
                root.get(Behandling_.frist),
                to
            )
        }

        val isNull = Specification { root: Root<Behandling>, _, builder: CriteriaBuilder ->
            builder.isNull(root.get(Behandling_.frist))
        }

        return mainSpecification.and(betweenDates.or(isNull))
    }

}