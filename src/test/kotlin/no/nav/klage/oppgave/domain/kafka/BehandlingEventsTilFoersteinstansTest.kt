package no.nav.klage.oppgave.domain.kafka

import com.kjetland.jackson.jsonSchema.JsonSchemaConfig
import com.kjetland.jackson.jsonSchema.JsonSchemaDraft
import com.kjetland.jackson.jsonSchema.JsonSchemaGenerator
import no.nav.klage.oppgave.util.ourJacksonObjectMapper
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*

internal class BehandlingEventsTilFoersteinstansTest {

    @Test
    @Disabled
    fun createJsonSchema() {
        val objectMapper = ourJacksonObjectMapper()

        val config = JsonSchemaConfig.vanillaJsonSchemaDraft4().withJsonSchemaDraft(JsonSchemaDraft.DRAFT_07)

        val schemaGen = JsonSchemaGenerator(objectMapper, config)
        val jsonSchema = schemaGen.generateJsonSchema(BehandlingEvent::class.java)

        println(objectMapper.writeValueAsString(jsonSchema))

        val event = BehandlingEvent(
            eventId = UUID.randomUUID(),
            kildeReferanse = "kildeRefeanse",
            kilde = "kilde",
            kabalReferanse = "kabalReferanse",
            type = BehandlingEventType.KLAGEBEHANDLING_AVSLUTTET,
            detaljer = BehandlingDetaljer(
                klagebehandlingAvsluttet = KlagebehandlingAvsluttetDetaljer(
                    avsluttet = LocalDateTime.now(),
                    utfall = ExternalUtfall.MEDHOLD,
                    journalpostReferanser = listOf("journalpostId1", "journalpostId2")
                ),
                ankebehandlingOpprettet = null,
                ankebehandlingAvsluttet = null
            )
        )

        println(objectMapper.writeValueAsString(event))
    }

}