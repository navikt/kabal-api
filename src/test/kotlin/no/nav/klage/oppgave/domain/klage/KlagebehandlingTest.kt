package no.nav.klage.oppgave.domain.klage

import no.nav.klage.oppgave.domain.kodeverk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*

internal class KlagebehandlingTest {

    private val journalpostId = "1"
    private val vedtakId = UUID.randomUUID()
    private val fnr = "12345678910"
    private val fnr2 = "22345678910"
    private val fnr3 = "32345678910"

    @Nested
    inner class LagBrevMottakereForVedtak {

        @Test
        fun `klager og sakenGjelder er samme person`() {
            val klagebehandling = Klagebehandling(
                kildesystem = Fagsystem.AO01,
                klager = Klager(PartId(PartIdType.PERSON, fnr)),
                sakenGjelder = SakenGjelder(PartId(PartIdType.PERSON, fnr), false),
                mottakId = UUID.randomUUID(),
                mottattKlageinstans = LocalDateTime.now(),
                tema = Tema.AAP,
                type = Type.KLAGE,
                vedtak = Vedtak(
                    id = vedtakId,
                    journalpostId = journalpostId
                )
            )

            val fasitMottakere = setOf(
                BrevMottaker(
                    partId = PartId(PartIdType.PERSON, fnr),
                    rolle = Rolle.KLAGER
                )
            )

            klagebehandling.lagBrevmottakereForVedtak()
            val brevMottakere = klagebehandling.getVedtakOrException().brevmottakere
            assert(brevMottakerSetsAreEqual(fasitMottakere, brevMottakere))
        }

        @Test
        fun `klager og sakenGjelder er ikke samme person, sakenGjelder skal ikke ha kopi`() {
            val klagebehandling = Klagebehandling(
                kildesystem = Fagsystem.AO01,
                klager = Klager(PartId(PartIdType.PERSON, fnr)),
                sakenGjelder = SakenGjelder(PartId(PartIdType.PERSON, fnr2), false),
                mottakId = UUID.randomUUID(),
                mottattKlageinstans = LocalDateTime.now(),
                tema = Tema.AAP,
                type = Type.KLAGE,
                vedtak = Vedtak(
                    id = vedtakId,
                    journalpostId = journalpostId
                )
            )

            val fasitMottakere = setOf(
                BrevMottaker(
                    partId = PartId(PartIdType.PERSON, fnr),
                    rolle = Rolle.KLAGER
                )
            )

            klagebehandling.lagBrevmottakereForVedtak()
            val brevMottakere = klagebehandling.getVedtakOrException().brevmottakere
            assert(brevMottakerSetsAreEqual(fasitMottakere, brevMottakere))
        }

        @Test
        fun `klager og sakengjelder er ikke samme person, sakenGjelder skal ha kopi`() {
            val klagebehandling = Klagebehandling(
                kildesystem = Fagsystem.AO01,
                klager = Klager(PartId(PartIdType.PERSON, fnr)),
                sakenGjelder = SakenGjelder(PartId(PartIdType.PERSON, fnr2), true),
                mottakId = UUID.randomUUID(),
                mottattKlageinstans = LocalDateTime.now(),
                tema = Tema.AAP,
                type = Type.KLAGE,
                vedtak = Vedtak(
                    id = vedtakId,
                    journalpostId = journalpostId
                )
            )

            val fasitMottakere = setOf(
                BrevMottaker(
                    partId = PartId(PartIdType.PERSON, fnr),
                    rolle = Rolle.KLAGER
                ),

                BrevMottaker(
                    partId = PartId(PartIdType.PERSON, fnr2),
                    rolle = Rolle.SAKEN_GJELDER
                )
            )

            klagebehandling.lagBrevmottakereForVedtak()
            val brevMottakere = klagebehandling.getVedtakOrException().brevmottakere
            assert(brevMottakerSetsAreEqual(fasitMottakere, brevMottakere))
        }

        @Test
        fun `klager er prosessfullmektig, parten skal ikke motta kopi`() {
            val klagebehandling = Klagebehandling(
                kildesystem = Fagsystem.AO01,
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
                tema = Tema.AAP,
                type = Type.KLAGE,
                vedtak = Vedtak(
                    id = vedtakId,
                    journalpostId = journalpostId
                )
            )

            val fasitMottakere = setOf(
                BrevMottaker(
                    partId = PartId(PartIdType.PERSON, fnr2),
                    rolle = Rolle.PROSESSFULLMEKTIG
                )
            )

            klagebehandling.lagBrevmottakereForVedtak()
            val brevMottakere = klagebehandling.getVedtakOrException().brevmottakere
            assert(brevMottakerSetsAreEqual(fasitMottakere, brevMottakere))
        }

        @Test
        fun `klager er prosessfullmektig, parten skal motta kopi`() {
            val klagebehandling = Klagebehandling(
                kildesystem = Fagsystem.AO01,
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
                tema = Tema.AAP,
                type = Type.KLAGE,
                vedtak = Vedtak(
                    id = vedtakId,
                    journalpostId = journalpostId
                )
            )

            val fasitMottakere = setOf(
                BrevMottaker(
                    partId = PartId(PartIdType.PERSON, fnr),
                    rolle = Rolle.KLAGER
                ),
                BrevMottaker(
                    partId = PartId(PartIdType.PERSON, fnr2),
                    rolle = Rolle.PROSESSFULLMEKTIG
                )
            )

            klagebehandling.lagBrevmottakereForVedtak()
            val brevMottakere = klagebehandling.getVedtakOrException().brevmottakere
            assert(brevMottakerSetsAreEqual(fasitMottakere, brevMottakere))
        }

        @Test
        fun `klager er prosessfullmektig, parten skal motta kopi, sakenGjelder er en annen, sakenGjelder skal ikke ha kopi`() {
            val klagebehandling = Klagebehandling(
                kildesystem = Fagsystem.AO01,
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
                tema = Tema.AAP,
                type = Type.KLAGE,
                vedtak = Vedtak(
                    id = vedtakId,
                    journalpostId = journalpostId
                )
            )

            val fasitMottakere = setOf(
                BrevMottaker(
                    partId = PartId(PartIdType.PERSON, fnr),
                    rolle = Rolle.KLAGER
                ),
                BrevMottaker(
                    partId = PartId(PartIdType.PERSON, fnr2),
                    rolle = Rolle.PROSESSFULLMEKTIG
                )
            )

            klagebehandling.lagBrevmottakereForVedtak()
            val brevMottakere = klagebehandling.getVedtakOrException().brevmottakere
            assert(brevMottakerSetsAreEqual(fasitMottakere, brevMottakere))
        }

        @Test
        fun `klager er prosessfullmektig, parten skal motta kopi, sakenGjelder er en annen, sakenGjelder skal ha kopi`() {
            val klagebehandling = Klagebehandling(
                kildesystem = Fagsystem.AO01,
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
                tema = Tema.AAP,
                type = Type.KLAGE,
                vedtak = Vedtak(
                    id = vedtakId,
                    journalpostId = journalpostId
                )

            )

            val fasitMottakere = setOf(
                BrevMottaker(
                    partId = PartId(PartIdType.PERSON, fnr),
                    rolle = Rolle.KLAGER
                ),
                BrevMottaker(
                    partId = PartId(PartIdType.PERSON, fnr3),
                    rolle = Rolle.SAKEN_GJELDER
                ),
                BrevMottaker(
                    partId = PartId(PartIdType.PERSON, fnr2),
                    rolle = Rolle.PROSESSFULLMEKTIG
                )
            )

            klagebehandling.lagBrevmottakereForVedtak()
            val brevMottakere = klagebehandling.getVedtakOrException().brevmottakere
            assert(brevMottakerSetsAreEqual(fasitMottakere, brevMottakere))
        }

        @Test
        fun `klager er prosessfullmektig, parten skal ikke motta kopi, sakenGjelder er en annen, sakenGjelder skal ikke ha kopi`() {
            val klagebehandling = Klagebehandling(
                kildesystem = Fagsystem.AO01,
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
                tema = Tema.AAP,
                type = Type.KLAGE,
                vedtak = Vedtak(
                    id = vedtakId,
                    journalpostId = journalpostId
                )
            )

            val fasitMottakere = setOf(
                BrevMottaker(
                    partId = PartId(PartIdType.PERSON, fnr2),
                    rolle = Rolle.PROSESSFULLMEKTIG
                )
            )

            klagebehandling.lagBrevmottakereForVedtak()
            val brevMottakere = klagebehandling.getVedtakOrException().brevmottakere
            assert(brevMottakerSetsAreEqual(fasitMottakere, brevMottakere))
        }

        @Test
        fun `klager er prosessfullmektig, parten skal ikke motta kopi, sakenGjelder er en annen, sakenGjelder skal ha kopi`() {
            val klagebehandling = Klagebehandling(
                kildesystem = Fagsystem.AO01,
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
                tema = Tema.AAP,
                type = Type.KLAGE,
                vedtak = Vedtak(
                    id = vedtakId,
                    journalpostId = journalpostId
                )
            )

            val fasitMottakere = setOf(
                BrevMottaker(
                    partId = PartId(PartIdType.PERSON, fnr3),
                    rolle = Rolle.SAKEN_GJELDER
                ),
                BrevMottaker(
                    partId = PartId(PartIdType.PERSON, fnr2),
                    rolle = Rolle.PROSESSFULLMEKTIG
                )
            )

            klagebehandling.lagBrevmottakereForVedtak()
            val brevMottakere = klagebehandling.getVedtakOrException().brevmottakere
            assert(brevMottakerSetsAreEqual(fasitMottakere, brevMottakere))
        }
    }

    @Nested
    inner class Status {
        @Test
        fun `status IKKE_TILDELT`() {
            val klagebehandling = Klagebehandling(
                kildesystem = Fagsystem.AO01,
                klager = Klager(PartId(PartIdType.PERSON, fnr)),
                sakenGjelder = SakenGjelder(PartId(PartIdType.PERSON, fnr), false),
                mottakId = UUID.randomUUID(),
                mottattKlageinstans = LocalDateTime.now(),
                tema = Tema.AAP,
                type = Type.KLAGE
            )
            assertThat(klagebehandling.getStatus()).isEqualTo(Klagebehandling.Status.IKKE_TILDELT)
        }

        @Test
        fun `status IKKE_TILDELT etter tidligere tildeling`() {
            val klagebehandling = Klagebehandling(
                kildesystem = Fagsystem.AO01,
                klager = Klager(PartId(PartIdType.PERSON, fnr)),
                sakenGjelder = SakenGjelder(PartId(PartIdType.PERSON, fnr), false),
                mottakId = UUID.randomUUID(),
                mottattKlageinstans = LocalDateTime.now(),
                tema = Tema.AAP,
                type = Type.KLAGE,
                tildeling = Tildeling(saksbehandlerident = null, tidspunkt = LocalDateTime.now())
            )
            assertThat(klagebehandling.getStatus()).isEqualTo(Klagebehandling.Status.IKKE_TILDELT)
        }

        @Test
        fun `status TILDELT`() {
            val klagebehandling = Klagebehandling(
                kildesystem = Fagsystem.AO01,
                klager = Klager(PartId(PartIdType.PERSON, fnr)),
                sakenGjelder = SakenGjelder(PartId(PartIdType.PERSON, fnr), false),
                mottakId = UUID.randomUUID(),
                mottattKlageinstans = LocalDateTime.now(),
                tema = Tema.AAP,
                type = Type.KLAGE,
                tildeling = Tildeling(saksbehandlerident = "abc", tidspunkt = LocalDateTime.now())
            )
            assertThat(klagebehandling.getStatus()).isEqualTo(Klagebehandling.Status.TILDELT)
        }

        @Test
        fun `status SENDT_TIL_MEDUNDERSKRIVER`() {
            val klagebehandling = Klagebehandling(
                kildesystem = Fagsystem.AO01,
                klager = Klager(PartId(PartIdType.PERSON, fnr)),
                sakenGjelder = SakenGjelder(PartId(PartIdType.PERSON, fnr), false),
                mottakId = UUID.randomUUID(),
                mottattKlageinstans = LocalDateTime.now(),
                tema = Tema.AAP,
                type = Type.KLAGE,
                medunderskriver = MedunderskriverTildeling("abc123", LocalDateTime.now()),
                medunderskriverFlyt = MedunderskriverFlyt.OVERSENDT_TIL_MEDUNDERSKRIVER
            )
            assertThat(klagebehandling.getStatus()).isEqualTo(Klagebehandling.Status.SENDT_TIL_MEDUNDERSKRIVER)
        }

        @Test
        fun `status RETURNERT_TIL_SAKSBEHANDLER`() {
            val klagebehandling = Klagebehandling(
                kildesystem = Fagsystem.AO01,
                klager = Klager(PartId(PartIdType.PERSON, fnr)),
                sakenGjelder = SakenGjelder(PartId(PartIdType.PERSON, fnr), false),
                mottakId = UUID.randomUUID(),
                mottattKlageinstans = LocalDateTime.now(),
                tema = Tema.AAP,
                type = Type.KLAGE,
                medunderskriver = MedunderskriverTildeling("abc123", LocalDateTime.now()),
                medunderskriverFlyt = MedunderskriverFlyt.RETURNERT_TIL_SAKSBEHANDLER
            )
            assertThat(klagebehandling.getStatus()).isEqualTo(Klagebehandling.Status.RETURNERT_TIL_SAKSBEHANDLER)
        }

        @Test
        fun `status MEDUNDERSKRIVER_VALGT`() {
            val klagebehandling = Klagebehandling(
                kildesystem = Fagsystem.AO01,
                klager = Klager(PartId(PartIdType.PERSON, fnr)),
                sakenGjelder = SakenGjelder(PartId(PartIdType.PERSON, fnr), false),
                mottakId = UUID.randomUUID(),
                mottattKlageinstans = LocalDateTime.now(),
                tema = Tema.AAP,
                type = Type.KLAGE,
                medunderskriver = MedunderskriverTildeling("abc123", LocalDateTime.now())
            )
            assertThat(klagebehandling.getStatus()).isEqualTo(Klagebehandling.Status.MEDUNDERSKRIVER_VALGT)
        }

        @Test
        fun `status TILDELT når medunderskriver er fjernet`() {
            val klagebehandling = Klagebehandling(
                kildesystem = Fagsystem.AO01,
                klager = Klager(PartId(PartIdType.PERSON, fnr)),
                sakenGjelder = SakenGjelder(PartId(PartIdType.PERSON, fnr), false),
                mottakId = UUID.randomUUID(),
                mottattKlageinstans = LocalDateTime.now(),
                tema = Tema.AAP,
                type = Type.KLAGE,
                tildeling = Tildeling(saksbehandlerident = "abc", tidspunkt = LocalDateTime.now()),
                medunderskriver = MedunderskriverTildeling(null, LocalDateTime.now())
            )
            assertThat(klagebehandling.getStatus()).isEqualTo(Klagebehandling.Status.TILDELT)
        }

        @Test
        fun `status FULLFOERT`() {
            val klagebehandling = Klagebehandling(
                kildesystem = Fagsystem.AO01,
                klager = Klager(PartId(PartIdType.PERSON, fnr)),
                sakenGjelder = SakenGjelder(PartId(PartIdType.PERSON, fnr), false),
                mottakId = UUID.randomUUID(),
                mottattKlageinstans = LocalDateTime.now(),
                tema = Tema.AAP,
                type = Type.KLAGE,
                medunderskriver = MedunderskriverTildeling("abc123", LocalDateTime.now()),
                avsluttet = LocalDateTime.now()
            )
            assertThat(klagebehandling.getStatus()).isEqualTo(Klagebehandling.Status.FULLFOERT)
        }
    }

    private fun BrevMottaker.isEqualTo(other: BrevMottaker): Boolean {
        return partId == other.partId &&
                rolle == other.rolle &&
                dokdistReferanse == other.dokdistReferanse &&
                journalpostId == other.journalpostId
    }

    private fun BrevMottaker.isMemberOf(otherSet: Set<BrevMottaker>): Boolean {
        return otherSet.any { brevMottaker ->
            this.isEqualTo(brevMottaker)
        }
    }

    private fun brevMottakerSetsAreEqual(firstSet: Set<BrevMottaker>, secondSet: Set<BrevMottaker>): Boolean {
        return firstSet.size == secondSet.size &&
                firstSet.all { brevMottaker -> brevMottaker.isMemberOf(secondSet) }
    }
}