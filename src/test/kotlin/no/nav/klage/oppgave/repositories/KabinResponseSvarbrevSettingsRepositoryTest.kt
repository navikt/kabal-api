package no.nav.klage.oppgave.repositories

import com.ninjasquad.springmockk.MockkBean
import no.nav.klage.kodeverk.TimeUnitType
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.ytelse.Ytelse
import no.nav.klage.oppgave.db.TestPostgresqlContainer
import no.nav.klage.oppgave.domain.klage.SvarbrevSettings
import no.nav.klage.oppgave.util.TokenUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDateTime

@ActiveProfiles("local")
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class KabinResponseSvarbrevSettingsRepositoryTest {

    companion object {
        @Container
        @JvmField
        val postgreSQLContainer: TestPostgresqlContainer = TestPostgresqlContainer.instance
    }

    @Autowired
    lateinit var testEntityManager: TestEntityManager

    @Autowired
    lateinit var svarbrevSettingsRepository: SvarbrevSettingsRepository

    //Because of Hibernate Envers and our setup for audit logs.
    @MockkBean
    lateinit var tokenUtil: TokenUtil

    @Test
    fun `add svarbrevSettings works`() {
        val svarbrevSettings = SvarbrevSettings(
            ytelse = Ytelse.TIL_TIP,
            type = Type.KLAGE,
            behandlingstidUnits = 12,
            behandlingstidUnitType = TimeUnitType.WEEKS,
            customText = "custom text",
            shouldSend = true,
            created = LocalDateTime.now(),
            modified = LocalDateTime.now(),
            createdBy = "Z999999",
        )

        testEntityManager.persistAndFlush(svarbrevSettings)
        testEntityManager.clear()

        val found = svarbrevSettingsRepository.findById(svarbrevSettings.id).get()

        assertThat(found).isEqualTo(svarbrevSettings)
    }

    @Test
    fun `add svarbrevSettings with history works`() {
        val svarbrevSettings = SvarbrevSettings(
            ytelse = Ytelse.TIL_TIP,
            type = Type.KLAGE,
            behandlingstidUnits = 12,
            behandlingstidUnitType = TimeUnitType.WEEKS,
            customText = "custom text",
            shouldSend = true,
            created = LocalDateTime.now(),
            modified = LocalDateTime.now(),
            createdBy = "Z999999",
        )

        val svarbrevSettingsHistory = svarbrevSettings.toHistory()

        svarbrevSettings.history.add(svarbrevSettingsHistory)

        testEntityManager.persistAndFlush(svarbrevSettings)
        testEntityManager.clear()

        val found = svarbrevSettingsRepository.findById(svarbrevSettings.id).get()

        assertThat(found.history.first()).isEqualTo(svarbrevSettingsHistory)
    }

}