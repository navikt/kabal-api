package no.nav.klage.dokument.repositories

import no.nav.klage.dokument.domain.dokumenterunderarbeid.JournalfoertDokumentUnderArbeidAsVedlegg
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Transactional
interface JournalfoertDokumentUnderArbeidAsVedleggRepository : JpaRepository<JournalfoertDokumentUnderArbeidAsVedlegg, UUID> {

    fun findByParentId(dokumentId: UUID): Set<JournalfoertDokumentUnderArbeidAsVedlegg>

    fun findByParentIdAndJournalpostIdNotAndDokumentInfoIdNotAndIdNot(parentId: UUID, journalpostId: String, dokumentInfoId: String, id: UUID): List<JournalfoertDokumentUnderArbeidAsVedlegg>

    fun findByParentIdOrderByCreated(dokumentId: UUID): SortedSet<JournalfoertDokumentUnderArbeidAsVedlegg>

    fun findByMarkertFerdigNotNullAndFerdigstiltNullAndParentIdIsNull(): List<JournalfoertDokumentUnderArbeidAsVedlegg>

    fun findByMarkertFerdigNotNullAndFerdigstiltNotNullAndParentIdIsNullAndBehandlingId(behandlingId: UUID): SortedSet<JournalfoertDokumentUnderArbeidAsVedlegg>

}