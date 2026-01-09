package no.nav.klage.oppgave.clients.egenansatt

import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("local")
@Disabled("test without")
internal class EgenAnsattKafkaConsumerTest {

    @Test
    fun `should read and cache json with skjermetTil`() {
        val egenAnsattService = spyk<EgenAnsattService>()

        val fnr = "01017012345"
        val recordMock = mockk<ConsumerRecord<String, String>>()
        every { recordMock.value() } returns getJsonWithSkjermetTil()
        every { recordMock.key() } returns fnr
        every { recordMock.offset() } returns 1
        every { recordMock.partition() } returns 2
        every { recordMock.topic() } returns "whatever"

        val kafkaOppgaveConsumer = EgenAnsattKafkaConsumer(egenAnsattService)

        kafkaOppgaveConsumer.listen(recordMock)

        verify { egenAnsattService.oppdaterEgenAnsatt(fnr, any()) }
        assertThat(egenAnsattService.erEgenAnsatt(fnr)).isTrue
    }

    @Test
    fun `should read and cache json without skjermetTil`() {
        val egenAnsattService = spyk<EgenAnsattService>()

        val fnr = "01017012345"
        val recordMock = mockk<ConsumerRecord<String, String>>()
        every { recordMock.value() } returns getJsonWithoutSkjermetTil()
        every { recordMock.key() } returns fnr
        every { recordMock.offset() } returns 1
        every { recordMock.partition() } returns 2
        every { recordMock.topic() } returns "whatever"

        val kafkaOppgaveConsumer = EgenAnsattKafkaConsumer(egenAnsattService)

        kafkaOppgaveConsumer.listen(recordMock)

        verify { egenAnsattService.oppdaterEgenAnsatt(fnr, any()) }
        assertThat(egenAnsattService.erEgenAnsatt(fnr)).isTrue
    }

    @Test
    fun `should read and cache json that is valid in the past`() {
        val egenAnsattService = spyk<EgenAnsattService>()

        val fnr = "01017012345"
        val recordMock = mockk<ConsumerRecord<String, String>>()
        every { recordMock.value() } returns getJsonThatIsValidInThePast()
        every { recordMock.key() } returns fnr
        every { recordMock.offset() } returns 1
        every { recordMock.partition() } returns 2
        every { recordMock.topic() } returns "whatever"

        val kafkaOppgaveConsumer = EgenAnsattKafkaConsumer(egenAnsattService)

        kafkaOppgaveConsumer.listen(recordMock)

        verify { egenAnsattService.oppdaterEgenAnsatt(fnr, any()) }
        assertThat(egenAnsattService.erEgenAnsatt(fnr)).isFalse
    }

    @Language("JSON")
    fun getJsonWithSkjermetTil() = """
        {
          "skjermetFra": "2019-01-01T12:00:00",
          "skjermetTil": "2045-01-01T12:00:00"
        }
    """.trimIndent()

    @Language("JSON")
    fun getJsonWithoutSkjermetTil() = """
        {
          "skjermetFra": "2019-01-01T12:00:00"
        }
    """.trimIndent()

    @Language("JSON")
    fun getJsonThatIsValidInThePast() = """
        {
          "skjermetFra": "2019-01-01T12:00:00",
          "skjermetTil": "2020-01-01T12:00:00"
        }
    """.trimIndent()
}