package no.nav.klage.oppgave.repositories

import no.nav.klage.oppgave.domain.dokumenterunderarbeid.DokumentUnderArbeid
import no.nav.klage.oppgave.domain.dokumenterunderarbeid.HovedDokument
import no.nav.klage.oppgave.domain.dokumenterunderarbeid.Vedlegg
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.NoRepositoryBean
import org.springframework.transaction.annotation.Transactional
import java.util.*


@NoRepositoryBean
interface BaseDokumentUnderArbeidRepository<T : DokumentUnderArbeid> : JpaRepository<T, UUID> {
    //@Query("select d from #{#entityName} as d from dokument where d.ekstern_referanse = eksternReferanse")
}


@Transactional
interface DokumentUnderArbeidRepository : BaseDokumentUnderArbeidRepository<DokumentUnderArbeid> {
}

@Transactional
interface HovedDokumentRepository : BaseDokumentUnderArbeidRepository<HovedDokument>,
    JpaRepository<HovedDokument, UUID> {
    fun findByBehandlingId(behandlingId: UUID): List<HovedDokument>
    fun findByDokumentIdOrVedleggDokumentId(id: UUID, vedleggId: UUID): HovedDokument
    fun findByVedleggDokumentId(id: UUID): HovedDokument
    fun findByVedleggId(id: UUID): HovedDokument
}

fun HovedDokumentRepository.findByDokumentIdOrVedleggDokumentId(id: UUID): HovedDokument =
    this.findByDokumentIdOrVedleggDokumentId(id, id)

@Transactional
interface VedleggRepository : BaseDokumentUnderArbeidRepository<Vedlegg>, JpaRepository<Vedlegg, UUID>