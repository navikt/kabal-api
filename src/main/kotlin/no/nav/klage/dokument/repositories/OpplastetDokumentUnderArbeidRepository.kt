package no.nav.klage.dokument.repositories

import no.nav.klage.dokument.domain.dokumenterunderarbeid.OpplastetDokumentUnderArbeidAsHoveddokument
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Transactional
interface OpplastetDokumentUnderArbeidRepository : JpaRepository<OpplastetDokumentUnderArbeidAsHoveddokument, UUID>