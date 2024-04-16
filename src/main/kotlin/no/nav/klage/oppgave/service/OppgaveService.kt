package no.nav.klage.oppgave.service

import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Root
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.Ytelse
import no.nav.klage.oppgave.api.view.BehandlingerListResponse
import no.nav.klage.oppgave.api.view.MineFerdigstilteOppgaverQueryParams
import no.nav.klage.oppgave.api.view.Rekkefoelge
import no.nav.klage.oppgave.api.view.Sortering
import no.nav.klage.oppgave.domain.klage.Behandling
import no.nav.klage.oppgave.domain.klage.Behandling_
import no.nav.klage.oppgave.repositories.BehandlingRepository
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service


@Service
class OppgaveService(
    private val behandlingRepository: BehandlingRepository,
    private val innloggetSaksbehandlerService: InnloggetSaksbehandlerService,
) {

    fun getFerdigstilteOppgaverForNavIdent(queryParams: MineFerdigstilteOppgaverQueryParams): BehandlingerListResponse {

        val specification: Specification<Behandling> = Specification { root: Root<Behandling>, _: CriteriaQuery<*>, builder: CriteriaBuilder ->
            builder.isNotNull(root.get(Behandling_.avsluttetAvSaksbehandler))
        }

        queryParams.typer.forEach { type ->
            specification.and(isType(Type.of(type)))
        }

        queryParams.ytelser.forEach { ytelse ->
            specification.and(isYtelse(Ytelse.of(ytelse)))
        }

        /*queryParams.registreringshjemler.forEach { hjemmel ->
            specification.and(hasRegistreringshjemmel(Ytelse.of(ytelse)))
        }*/

        val sortDirection = when (queryParams.rekkefoelge) {
            Rekkefoelge.STIGENDE -> {
                Sort.Direction.ASC
            }
            Rekkefoelge.SYNKENDE -> {
                Sort.Direction.DESC
            }
        }

        val sortProperty = when (queryParams.sortering) {
            Sortering.AVSLUTTET_AV_SAKSBEHANDLER -> {
                Behandling::avsluttetAvSaksbehandler.name
            }
            Sortering.MOTTATT -> {
                Behandling::mottattKlageinstans.name
            }
            Sortering.FRIST -> {
                Behandling::frist.name
            }
            Sortering.ALDER -> {
                Behandling::mottattKlageinstans.name
            }
            Sortering.PAA_VENT_FROM -> TODO()
            Sortering.PAA_VENT_TO -> TODO()
            Sortering.RETURNERT_FRA_ROL -> TODO()
        }

        val data = behandlingRepository.findAll(
            specification,
            Sort.by(sortDirection, sortProperty),
        )

        return BehandlingerListResponse(
            behandlinger = data.map { behandling -> behandling.id },
            antallTreffTotalt = data.size,
        )
    }

}

fun isType(type: Type): Specification<Behandling> {
    return Specification { root: Root<Behandling>, _: CriteriaQuery<*>?, builder: CriteriaBuilder ->
        builder.equal(root.get(Behandling_.type), type)
    }
}

fun isYtelse(ytelse: Ytelse): Specification<Behandling> {
    return Specification { root: Root<Behandling>, _: CriteriaQuery<*>?, builder: CriteriaBuilder ->
        builder.equal(root.get(Behandling_.ytelse), ytelse)
    }
}

/*
fun hasRegistreringshjemmel(registreringshjemmelList: List<Registreringshjemmel>): Specification<Behandling> {
    return Specification { root: Root<Behandling>, _: CriteriaQuery<*>?, builder: CriteriaBuilder ->



        val inClause: CriteriaBuilder.In<Registreringshjemmel> = builder.`in`(registreringshjemmelList.first())
        for (registreringshjemmel in registreringshjemmelList) {
            inClause.value(registreringshjemmel)
        }
        builder.and(inClause)
    }
}*/
