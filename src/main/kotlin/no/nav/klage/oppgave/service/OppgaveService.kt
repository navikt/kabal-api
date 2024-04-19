package no.nav.klage.oppgave.service

import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Root
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.Ytelse
import no.nav.klage.kodeverk.hjemmel.Registreringshjemmel
import no.nav.klage.oppgave.api.view.*
import no.nav.klage.oppgave.domain.klage.Behandling
import no.nav.klage.oppgave.domain.klage.Behandling_
import no.nav.klage.oppgave.domain.klage.Tildeling_
import no.nav.klage.oppgave.repositories.BehandlingRepository
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service


@Service
class OppgaveService(
    private val behandlingRepository: BehandlingRepository,
    private val innloggetSaksbehandlerService: InnloggetSaksbehandlerService,
    private val saksbehandlerService: SaksbehandlerService,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
    }

    fun getFerdigstilteOppgaverForNavIdent(queryParams: MineFerdigstilteOppgaverQueryParams): BehandlingerListResponse {
        var specification: Specification<Behandling> =
            Specification { root: Root<Behandling>, _: CriteriaQuery<*>, builder: CriteriaBuilder ->
                builder.isNotNull(root.get(Behandling_.avsluttetAvSaksbehandler))
            }

        specification = addInnloggetSaksbehandler(specification)
        specification = addTypeSpecifications(queryParams, specification)
        specification = addYtelseSpecifications(queryParams, specification)
        specification = addRegistreringshjemmelSpecifications(queryParams, specification)
        specification = addFromToSpecifications(queryParams, specification)

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
            Specification { root: Root<Behandling>, _: CriteriaQuery<*>, builder: CriteriaBuilder ->
                builder.isNotNull(root.get(Behandling_.avsluttetAvSaksbehandler))
            }

        specification = addEnhet(specification)
        specification = addTypeSpecifications(queryParams, specification)
        specification = addYtelseSpecifications(queryParams, specification)
        specification = addRegistreringshjemmelSpecifications(queryParams, specification)
        specification = addFromToSpecifications(queryParams, specification)

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
                Behandling_.avsluttetAvSaksbehandler.name
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
        specification.and { root: Root<Behandling>, _: CriteriaQuery<*>, builder: CriteriaBuilder ->
            builder.equal(
                root.get(Behandling_.tildeling).get(Tildeling_.enhet),
                saksbehandlerService.getEnhetForSaksbehandler(
                    innloggetSaksbehandlerService.getInnloggetIdent()
                ).enhetId
            )
        }

    private fun addInnloggetSaksbehandler(specification: Specification<Behandling>) =
        specification.and { root: Root<Behandling>, _: CriteriaQuery<*>, builder: CriteriaBuilder ->
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
                    specification.and { root: Root<Behandling>, _: CriteriaQuery<*>, builder: CriteriaBuilder ->
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
            specification = specification.and(typeSpecifications)
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
            specification = specification.and(ytelseSpecifications)
        }
        return specification
    }

    private fun addFromToSpecifications(
        queryParams: FerdigstilteOppgaverQueryParams,
        mainSpecification: Specification<Behandling>
    ): Specification<Behandling> {
        var specification = mainSpecification
        if ((queryParams.ferdigstiltFrom == null) xor (queryParams.ferdigstiltTo == null)) {
            throw IllegalArgumentException("Både ferdigstiltFrom og ferdigstiltTo må være satt, eller ingen av dem.")
        }

        if (queryParams.ferdigstiltFrom == null || queryParams.ferdigstiltTo == null) {
            return specification
        }

        specification =
            specification.and { root: Root<Behandling>, _: CriteriaQuery<*>, builder: CriteriaBuilder ->
                builder.greaterThanOrEqualTo(
                    root.get(Behandling_.avsluttetAvSaksbehandler),
                    queryParams.ferdigstiltFrom!!.atStartOfDay()
                )
            }

        specification =
            specification.and { root: Root<Behandling>, _: CriteriaQuery<*>, builder: CriteriaBuilder ->
                builder.lessThan(
                    root.get(Behandling_.avsluttetAvSaksbehandler),
                    queryParams.ferdigstiltTo!!.plusDays(1).atStartOfDay()
                )
            }

        return specification
    }

}