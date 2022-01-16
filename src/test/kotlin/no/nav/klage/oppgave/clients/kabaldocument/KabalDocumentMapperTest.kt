package no.nav.klage.oppgave.clients.kabaldocument

import io.mockk.every
import io.mockk.mockk
import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.PartIdType
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.Ytelse
import no.nav.klage.oppgave.clients.ereg.EregClient
import no.nav.klage.oppgave.clients.ereg.Navn
import no.nav.klage.oppgave.clients.ereg.Organisasjon
import no.nav.klage.oppgave.clients.kabaldocument.model.request.BrevMottakerInput
import no.nav.klage.oppgave.clients.kabaldocument.model.request.PartIdInput
import no.nav.klage.oppgave.clients.pdl.PdlFacade
import no.nav.klage.oppgave.clients.pdl.Person
import no.nav.klage.oppgave.domain.klage.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KabalDocumentMapperTest {


    companion object {
        private val vedtakId = UUID.randomUUID()
        private val fnr = "12345678910"
        private val fnr2 = "22345678910"
        private val fnr3 = "32345678910"
    }

    private val pdlFacade = mockk<PdlFacade>()
    private val eregClient = mockk<EregClient>()
    private val mapper = KabalDocumentMapper(pdlFacade, eregClient)

    @BeforeAll
    fun setup() {
        every { pdlFacade.getPersonInfo(any()) } returns Person(
            "fnr",
            "fornavn",
            null,
            "etternavn",
            null,
            null,
            null,
            null
        )

        every { eregClient.hentOrganisasjon(any()) } returns Organisasjon(
            Navn("navn", null, null, null, null, null),
            "orgnr",
            "type"
        )
    }


    @Test
    fun `klager og sakenGjelder er samme person`() {
        val klagebehandling = Klagebehandling(
            kildesystem = Fagsystem.AO01,
            kildeReferanse = "abc",
            klager = Klager(PartId(PartIdType.PERSON, fnr)),
            sakenGjelder = SakenGjelder(PartId(PartIdType.PERSON, fnr), false),
            mottakId = UUID.randomUUID(),
            mottattKlageinstans = LocalDateTime.now(),
            ytelse = Ytelse.OMS_OMP,
            type = Type.KLAGE,
            delbehandlinger = setOf(Delbehandling(
                id = vedtakId
            )),
            avsenderEnhetFoersteinstans = "4100",
            mottattFoersteinstans = LocalDate.now(),
        )

        val fasitMottakere = listOf(
            BrevMottakerInput(
                partId = PartIdInput("PERSON", fnr),
                navn = "fornavn etternavn",
                rolle = "HOVEDADRESSAT"
            )
        )
        assertThat(mapper.mapBrevMottakere(klagebehandling)).containsExactlyInAnyOrderElementsOf(fasitMottakere)
    }

    @Test
    fun `klager og sakenGjelder er ikke samme person, sakenGjelder skal ikke ha kopi`() {
        val klagebehandling = Klagebehandling(
            kildesystem = Fagsystem.AO01,
            kildeReferanse = "abc",
            klager = Klager(PartId(PartIdType.PERSON, fnr)),
            sakenGjelder = SakenGjelder(PartId(PartIdType.PERSON, fnr2), false),
            mottakId = UUID.randomUUID(),
            mottattKlageinstans = LocalDateTime.now(),
            ytelse = Ytelse.OMS_OMP,
            type = Type.KLAGE,
            delbehandlinger = setOf(Delbehandling(
                id = vedtakId
            )),
            avsenderEnhetFoersteinstans = "4100",
            mottattFoersteinstans = LocalDate.now(),
        )

        val fasitMottakere = setOf(
            BrevMottakerInput(
                partId = PartIdInput("PERSON", fnr),
                navn = "fornavn etternavn",
                rolle = "HOVEDADRESSAT"
            )
        )

        assertThat(mapper.mapBrevMottakere(klagebehandling)).containsExactlyInAnyOrderElementsOf(fasitMottakere)
    }

    @Test
    fun `klager og sakengjelder er ikke samme person, sakenGjelder skal ha kopi`() {
        val klagebehandling = Klagebehandling(
            kildesystem = Fagsystem.AO01,
            kildeReferanse = "abc",
            klager = Klager(PartId(PartIdType.PERSON, fnr)),
            sakenGjelder = SakenGjelder(PartId(PartIdType.PERSON, fnr2), true),
            mottakId = UUID.randomUUID(),
            mottattKlageinstans = LocalDateTime.now(),
            ytelse = Ytelse.OMS_OMP,
            type = Type.KLAGE,
            delbehandlinger = setOf(Delbehandling(
                id = vedtakId
            )),
            avsenderEnhetFoersteinstans = "4100",
            mottattFoersteinstans = LocalDate.now(),
        )

        val fasitMottakere = setOf(
            BrevMottakerInput(
                partId = PartIdInput("PERSON", fnr),
                navn = "fornavn etternavn",
                rolle = "HOVEDADRESSAT"
            ),

            BrevMottakerInput(
                partId = PartIdInput("PERSON", fnr2),
                navn = "fornavn etternavn",
                rolle = "KOPIADRESSAT"
            )
        )

        assertThat(mapper.mapBrevMottakere(klagebehandling)).containsExactlyInAnyOrderElementsOf(fasitMottakere)
    }

    @Test
    fun `klager er prosessfullmektig, parten skal ikke motta kopi`() {
        val klagebehandling = Klagebehandling(
            kildesystem = Fagsystem.AO01,
            kildeReferanse = "abc",
            klager = Klager(
                PartId(PartIdType.PERSON, fnr),
                prosessfullmektig = Prosessfullmektig(
                    PartId(
                        PartIdType.PERSON,
                        fnr2
                    ),
                    skalPartenMottaKopi = false
                )
            ),
            sakenGjelder = SakenGjelder(PartId(PartIdType.PERSON, fnr), false),
            mottakId = UUID.randomUUID(),
            mottattKlageinstans = LocalDateTime.now(),
            ytelse = Ytelse.OMS_OMP,
            type = Type.KLAGE,
            delbehandlinger = setOf(Delbehandling(
                id = vedtakId
            )),
            avsenderEnhetFoersteinstans = "4100",
            mottattFoersteinstans = LocalDate.now(),
        )

        val fasitMottakere = setOf(
            BrevMottakerInput(
                partId = PartIdInput("PERSON", fnr2),
                navn = "fornavn etternavn",
                rolle = "HOVEDADRESSAT"
            )
        )

        assertThat(mapper.mapBrevMottakere(klagebehandling)).containsExactlyInAnyOrderElementsOf(fasitMottakere)
    }

    @Test
    fun `klager er prosessfullmektig, parten skal motta kopi`() {
        val klagebehandling = Klagebehandling(
            kildesystem = Fagsystem.AO01,
            kildeReferanse = "abc",
            klager = Klager(
                PartId(PartIdType.PERSON, fnr),
                prosessfullmektig = Prosessfullmektig(
                    PartId(
                        PartIdType.PERSON,
                        fnr2
                    ),
                    skalPartenMottaKopi = true
                )
            ),
            sakenGjelder = SakenGjelder(PartId(PartIdType.PERSON, fnr), false),
            mottakId = UUID.randomUUID(),
            mottattKlageinstans = LocalDateTime.now(),
            ytelse = Ytelse.OMS_OMP,
            type = Type.KLAGE,
            delbehandlinger = setOf(Delbehandling(
                id = vedtakId
            )),
            avsenderEnhetFoersteinstans = "4100",
            mottattFoersteinstans = LocalDate.now(),
        )

        val fasitMottakere = setOf(
            BrevMottakerInput(
                partId = PartIdInput("PERSON", fnr),
                navn = "fornavn etternavn",
                rolle = "KOPIADRESSAT"
            ),
            BrevMottakerInput(
                partId = PartIdInput("PERSON", fnr2),
                navn = "fornavn etternavn",
                rolle = "HOVEDADRESSAT"
            )
        )

        assertThat(mapper.mapBrevMottakere(klagebehandling)).containsExactlyInAnyOrderElementsOf(fasitMottakere)
    }

    @Test
    fun `klager er prosessfullmektig, parten skal motta kopi, sakenGjelder er en annen, sakenGjelder skal ikke ha kopi`() {
        val klagebehandling = Klagebehandling(
            kildesystem = Fagsystem.AO01,
            kildeReferanse = "abc",
            klager = Klager(
                PartId(PartIdType.PERSON, fnr),
                prosessfullmektig = Prosessfullmektig(
                    PartId(
                        PartIdType.PERSON,
                        fnr2
                    ),
                    skalPartenMottaKopi = true
                )
            ),
            sakenGjelder = SakenGjelder(PartId(PartIdType.PERSON, fnr3), false),
            mottakId = UUID.randomUUID(),
            mottattKlageinstans = LocalDateTime.now(),
            ytelse = Ytelse.OMS_OMP,
            type = Type.KLAGE,
            delbehandlinger = setOf(Delbehandling(
                id = vedtakId
            )),
            avsenderEnhetFoersteinstans = "4100",
            mottattFoersteinstans = LocalDate.now(),
        )

        val fasitMottakere = setOf(
            BrevMottakerInput(
                partId = PartIdInput("PERSON", fnr),
                navn = "fornavn etternavn",
                rolle = "KOPIADRESSAT"
            ),
            BrevMottakerInput(
                partId = PartIdInput("PERSON", fnr2),
                navn = "fornavn etternavn",
                rolle = "HOVEDADRESSAT"
            )
        )

        assertThat(mapper.mapBrevMottakere(klagebehandling)).containsExactlyInAnyOrderElementsOf(fasitMottakere)
    }

    @Test
    fun `klager er prosessfullmektig, parten skal motta kopi, sakenGjelder er en annen, sakenGjelder skal ha kopi`() {
        val klagebehandling = Klagebehandling(
            kildesystem = Fagsystem.AO01,
            kildeReferanse = "abc",
            klager = Klager(
                PartId(PartIdType.PERSON, fnr),
                prosessfullmektig = Prosessfullmektig(
                    PartId(
                        PartIdType.PERSON,
                        fnr2
                    ),
                    skalPartenMottaKopi = true
                )
            ),
            sakenGjelder = SakenGjelder(PartId(PartIdType.PERSON, fnr3), true),
            mottakId = UUID.randomUUID(),
            mottattKlageinstans = LocalDateTime.now(),
            ytelse = Ytelse.OMS_OMP,
            type = Type.KLAGE,
            delbehandlinger = setOf(Delbehandling(
                id = vedtakId
            )),
            avsenderEnhetFoersteinstans = "4100",
            mottattFoersteinstans = LocalDate.now(),

            )

        val fasitMottakere = setOf(
            BrevMottakerInput(
                partId = PartIdInput("PERSON", fnr),
                navn = "fornavn etternavn",
                rolle = "KOPIADRESSAT"
            ),
            BrevMottakerInput(
                partId = PartIdInput("PERSON", fnr3),
                navn = "fornavn etternavn",
                rolle = "KOPIADRESSAT"
            ),
            BrevMottakerInput(
                partId = PartIdInput("PERSON", fnr2),
                navn = "fornavn etternavn",
                rolle = "HOVEDADRESSAT"
            )
        )

        assertThat(mapper.mapBrevMottakere(klagebehandling)).containsExactlyInAnyOrderElementsOf(fasitMottakere)
    }

    @Test
    fun `klager er prosessfullmektig, parten skal ikke motta kopi, sakenGjelder er en annen, sakenGjelder skal ikke ha kopi`() {
        val klagebehandling = Klagebehandling(
            kildesystem = Fagsystem.AO01,
            kildeReferanse = "abc",
            klager = Klager(
                PartId(PartIdType.PERSON, fnr),
                prosessfullmektig = Prosessfullmektig(
                    PartId(
                        PartIdType.PERSON,
                        fnr2
                    ),
                    skalPartenMottaKopi = false
                )
            ),
            sakenGjelder = SakenGjelder(PartId(PartIdType.PERSON, fnr3), false),
            mottakId = UUID.randomUUID(),
            mottattKlageinstans = LocalDateTime.now(),
            ytelse = Ytelse.OMS_OMP,
            type = Type.KLAGE,
            delbehandlinger = setOf(Delbehandling(
                id = vedtakId
            )),
            avsenderEnhetFoersteinstans = "4100",
            mottattFoersteinstans = LocalDate.now(),
        )

        val fasitMottakere = setOf(
            BrevMottakerInput(
                partId = PartIdInput("PERSON", fnr2),
                navn = "fornavn etternavn",
                rolle = "HOVEDADRESSAT"
            )
        )

        assertThat(mapper.mapBrevMottakere(klagebehandling)).containsExactlyInAnyOrderElementsOf(fasitMottakere)
    }

    @Test
    fun `klager er prosessfullmektig, parten skal ikke motta kopi, sakenGjelder er en annen, sakenGjelder skal ha kopi`() {
        val klagebehandling = Klagebehandling(
            kildesystem = Fagsystem.AO01,
            kildeReferanse = "abc",
            klager = Klager(
                PartId(PartIdType.PERSON, fnr),
                prosessfullmektig = Prosessfullmektig(
                    PartId(
                        PartIdType.PERSON,
                        fnr2
                    ),
                    skalPartenMottaKopi = false
                )
            ),
            sakenGjelder = SakenGjelder(PartId(PartIdType.PERSON, fnr3), true),
            mottakId = UUID.randomUUID(),
            mottattKlageinstans = LocalDateTime.now(),
            ytelse = Ytelse.OMS_OMP,
            type = Type.KLAGE,
            delbehandlinger = setOf(Delbehandling(
                id = vedtakId
            )),
            avsenderEnhetFoersteinstans = "4100",
            mottattFoersteinstans = LocalDate.now(),
        )

        val fasitMottakere = setOf(
            BrevMottakerInput(
                partId = PartIdInput("PERSON", fnr3),
                navn = "fornavn etternavn",
                rolle = "KOPIADRESSAT"
            ),
            BrevMottakerInput(
                partId = PartIdInput("PERSON", fnr2),
                navn = "fornavn etternavn",
                rolle = "HOVEDADRESSAT"
            )
        )

        assertThat(mapper.mapBrevMottakere(klagebehandling)).containsExactlyInAnyOrderElementsOf(fasitMottakere)
    }
}