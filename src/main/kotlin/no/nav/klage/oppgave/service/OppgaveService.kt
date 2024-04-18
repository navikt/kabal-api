package no.nav.klage.oppgave.service

import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Root
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.Ytelse
import no.nav.klage.kodeverk.hjemmel.Registreringshjemmel
import no.nav.klage.oppgave.api.view.BehandlingerListResponse
import no.nav.klage.oppgave.api.view.MineFerdigstilteOppgaverQueryParams
import no.nav.klage.oppgave.api.view.Rekkefoelge
import no.nav.klage.oppgave.api.view.Sortering
import no.nav.klage.oppgave.domain.klage.Behandling
import no.nav.klage.oppgave.domain.klage.Behandling_
import no.nav.klage.oppgave.repositories.BehandlingRepository
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service


@Service
class OppgaveService(
    private val behandlingRepository: BehandlingRepository,
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

        specification = addTypeSpecifications(queryParams, specification)
        specification = addYtelseSpecifications(queryParams, specification)
        specification = addRegistreringshjemmelSpecifications(queryParams, specification)

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

    private fun getSortDirection(queryParams: MineFerdigstilteOppgaverQueryParams): Sort.Direction {
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

    private fun getSortProperty(queryParams: MineFerdigstilteOppgaverQueryParams): String =
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

    private fun addRegistreringshjemmelSpecifications(
        queryParams: MineFerdigstilteOppgaverQueryParams,
        mainSpecification: Specification<Behandling>
    ): Specification<Behandling> {
        var specification = mainSpecification
        if (queryParams.registreringshjemler.isNotEmpty()) {
            queryParams.registreringshjemler.forEach { registreringshjemmelId ->
                specification =
                    specification.and { root: Root<Behandling>, _: CriteriaQuery<*>, builder: CriteriaBuilder ->
                        builder.isMember(Registreringshjemmel.of(registreringshjemmelId), root.get(Behandling_.registreringshjemler))
                    }
            }
        }
        return specification
    }

    private fun addTypeSpecifications(
        queryParams: MineFerdigstilteOppgaverQueryParams,
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
        queryParams: MineFerdigstilteOppgaverQueryParams,
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
}