package no.nav.klage.oppgave.api.mapper


import no.nav.klage.oppgave.api.view.KlagebehandlingListView
import no.nav.klage.oppgave.api.view.PersonSoekPersonView
import no.nav.klage.oppgave.clients.pdl.Sivilstand
import no.nav.klage.oppgave.domain.elasticsearch.EsKlagebehandling
import no.nav.klage.oppgave.domain.kodeverk.MedunderskriverFlyt
import no.nav.klage.oppgave.domain.kodeverk.Tema
import no.nav.klage.oppgave.domain.personsoek.PersonSoekResponseList
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@Service
class KlagebehandlingListMapper {

    fun mapPersonSoekResponseToPersonSoekListView(
        personSoekResponse: PersonSoekResponseList,
        viseUtvidet: Boolean,
        saksbehandler: String?,
        tilgangTilTemaer: List<Tema>
    ): List<PersonSoekPersonView> {
        return if (personSoekResponse.liste.size == 1) {
            personSoekResponse.liste.map { person ->
                val klagebehandlinger =
                    mapEsKlagebehandlingerToListView(
                        esKlagebehandlinger = person.klagebehandlinger,
                        viseUtvidet = viseUtvidet,
                        viseFullfoerte = true,
                        saksbehandler = saksbehandler,
                        tilgangTilTemaer = tilgangTilTemaer
                    )
                PersonSoekPersonView(
                    fnr = person.fnr,
                    navn = person.navn,
                    foedselsdato = person.foedselsdato,
                    klagebehandlinger = klagebehandlinger,
                    aapneKlagebehandlinger = klagebehandlinger.filter { it.avsluttetAvSaksbehandler == null },
                    avsluttedeKlagebehandlinger = klagebehandlinger.filter { it.avsluttetAvSaksbehandler != null }
                )
            }
        } else {
            personSoekResponse.liste.map { person ->
                PersonSoekPersonView(
                    fnr = person.fnr,
                    navn = person.navn,
                    foedselsdato = person.foedselsdato
                )
            }
        }
    }

    fun mapEsKlagebehandlingerToListView(
        esKlagebehandlinger: List<EsKlagebehandling>,
        viseUtvidet: Boolean,
        viseFullfoerte: Boolean,
        saksbehandler: String?,
        tilgangTilTemaer: List<Tema>,
        sivilstand: Sivilstand? = null
    ): List<KlagebehandlingListView> {
        return esKlagebehandlinger.map { esKlagebehandling ->
            KlagebehandlingListView(
                id = esKlagebehandling.id,
                person = if (viseUtvidet) {
                    KlagebehandlingListView.Person(
                        esKlagebehandling.sakenGjelderFnr,
                        esKlagebehandling.sakenGjelderNavn,
                        if (esKlagebehandling.sakenGjelderFnr == sivilstand?.foedselsnr) sivilstand?.type?.id else null
                    )
                } else {
                    null
                },
                type = esKlagebehandling.type,
                tema = esKlagebehandling.tema,
                hjemmel = esKlagebehandling.hjemler.firstOrNull(),
                frist = esKlagebehandling.frist,
                mottatt = esKlagebehandling.mottattKlageinstans.toLocalDate(),
                versjon = esKlagebehandling.versjon!!.toInt(),
                klagebehandlingVersjon = esKlagebehandling.versjon,
                harMedunderskriver = esKlagebehandling.medunderskriverident != null,
                erMedunderskriver = esKlagebehandling.medunderskriverident != null && esKlagebehandling.medunderskriverident == saksbehandler,
                medunderskriverident = esKlagebehandling.medunderskriverident,
                medunderskriverFlyt = if (esKlagebehandling.medunderskriverFlyt != null) MedunderskriverFlyt.fromNavn(esKlagebehandling.medunderskriverFlyt) else MedunderskriverFlyt.IKKE_SENDT,
                erTildelt = esKlagebehandling.tildeltSaksbehandlerident != null,
                tildeltSaksbehandlerident = esKlagebehandling.tildeltSaksbehandlerident,
                tildeltSaksbehandlerNavn = esKlagebehandling.tildeltSaksbehandlernavn,
                utfall = if (viseFullfoerte) {
                    esKlagebehandling.vedtakUtfall
                } else {
                    null
                },
                avsluttetAvSaksbehandler = if (viseFullfoerte) {
                    esKlagebehandling.avsluttetAvSaksbehandler?.toLocalDate()
                } else {
                    null
                },
                saksbehandlerHarTilgang = tilgangTilTemaer.contains(Tema.of(esKlagebehandling.tema)),
                egenAnsatt = esKlagebehandling.egenAnsatt,
                fortrolig = esKlagebehandling.fortrolig,
                strengtFortrolig = esKlagebehandling.strengtFortrolig,
                ageKA = esKlagebehandling.mottattKlageinstans.toAgeInDays()
            )
        }
    }

    private fun LocalDateTime.toAgeInDays() = ChronoUnit.DAYS.between(this.toLocalDate(), LocalDate.now()).toInt()
}