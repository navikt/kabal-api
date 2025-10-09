package no.nav.klage.oppgave.repositories

import com.ninjasquad.springmockk.MockkBean
import no.nav.klage.oppgave.db.PostgresIntegrationTestBase
import no.nav.klage.oppgave.domain.kafka.EventType
import no.nav.klage.oppgave.domain.kafka.KafkaEvent
import no.nav.klage.oppgave.domain.kafka.UtsendingStatus.*
import no.nav.klage.oppgave.util.TokenUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDateTime
import java.util.*

@ActiveProfiles("local")
@DataJpaTest
class KafkaEventRepositoryTest : PostgresIntegrationTestBase() {

    @Autowired
    lateinit var testEntityManager: TestEntityManager

    @Autowired
    lateinit var kafkaEventRepository: KafkaEventRepository

    //Because of Hibernate Envers and our setup for audit logs.
    @MockkBean
    lateinit var tokenUtil: TokenUtil

    @Test
    fun `store event works`() {
        val event = KafkaEvent(
            kildeReferanse = "TEST",
            kilde = "TEST",
            behandlingId = UUID.randomUUID(),
            status = IKKE_SENDT,
            jsonPayload = "{}",
            type = EventType.STATS_DVH
        )

        kafkaEventRepository.save(event)

        testEntityManager.flush()

        assertThat(kafkaEventRepository.findById(event.id).get()).isEqualTo(event)
    }

    @Test
    fun `fetch for status and type works and in right order (asc)`() {
        kafkaEventRepository.save(
            KafkaEvent(
                kildeReferanse = "TEST",
                kilde = "TEST",
                behandlingId = UUID.randomUUID(),
                status = IKKE_SENDT,
                jsonPayload = "{}",
                type = EventType.STATS_DVH
            )
        )

        val oldestKafkaEvent = KafkaEvent(
            kildeReferanse = "TEST",
            kilde = "TEST",
            behandlingId = UUID.randomUUID(),
            status = FEILET,
            jsonPayload = "{}",
            type = EventType.STATS_DVH,
            created = LocalDateTime.now().minusDays(1)
        )
        kafkaEventRepository.save(
            oldestKafkaEvent
        )

        //Should be ignored b/c wrong type
        kafkaEventRepository.save(
            KafkaEvent(
                kildeReferanse = "TEST",
                kilde = "TEST",
                behandlingId = UUID.randomUUID(),
                status = FEILET,
                jsonPayload = "{}",
                type = EventType.KLAGE_VEDTAK
            )
        )

        //Should be ignored b/c status = SENDT
        kafkaEventRepository.save(
            KafkaEvent(
                kildeReferanse = "TEST",
                kilde = "TEST",
                behandlingId = UUID.randomUUID(),
                status = SENDT,
                jsonPayload = "{}",
                type = EventType.STATS_DVH
            )
        )

        testEntityManager.flush()

        val list = kafkaEventRepository.getAllByStatusInAndTypeOrderByCreated(
            listOf(IKKE_SENDT, FEILET),
            EventType.STATS_DVH
        )
        assertThat(list.size).isEqualTo(2)
        assertThat(list.first()).isEqualTo(oldestKafkaEvent)
    }

}