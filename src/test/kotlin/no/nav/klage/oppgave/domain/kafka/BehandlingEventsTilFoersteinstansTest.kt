package no.nav.klage.oppgave.domain.kafka

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

//import no.nav.klage.oppgave.util.ourJsonMapper
//import java.time.LocalDateTime
//import java.util.*

internal class BehandlingEventsTilFoersteinstansTest {

    @Test
    @Disabled
    //TODO: Trengs denne?
    fun createJsonSchema() {
//        //Husk å tilpass utfallslisten i generert json basert på TypeToUtfall i kodeverk.
//        //AnkebehandlingAvsluttetDetaljer vil ha utfall fra både Ankebehandling og AnkeITrygderettenbehandling.
//        //AnkeITrygderettenbehandlingOpprettetDetaljer har feltet utfall, det gjelder utfallet på ankebehandlingen som førte til oversending til Trygderetten.
//        val objectMapper = ourJacksonObjectMapper()
//
//        val config = JsonSchemaConfig.vanillaJsonSchemaDraft4().withJsonSchemaDraft(JsonSchemaDraft.DRAFT_07)
//
//        val schemaGen = JsonSchemaGenerator(objectMapper, config)
//        val jsonSchema = schemaGen.generateJsonSchema(BehandlingEvent::class.java)
//
//        println(objectMapper.writeValueAsString(jsonSchema))
//
//        val event = BehandlingEvent(
//            eventId = UUID.randomUUID(),
//            kildeReferanse = "kildeRefeanse",
//            kilde = "kilde",
//            kabalReferanse = "kabalReferanse",
//            type = BehandlingEventType.KLAGEBEHANDLING_AVSLUTTET,
//            detaljer = BehandlingDetaljer(
//                klagebehandlingAvsluttet = KlagebehandlingAvsluttetDetaljer(
//                    avsluttet = LocalDateTime.now(),
//                    utfall = ExternalUtfall.MEDHOLD,
//                    journalpostReferanser = listOf("journalpostId1", "journalpostId2")
//                ),
//                ankebehandlingOpprettet = null,
//                ankebehandlingAvsluttet = null
//            )
//        )
//
//        println(objectMapper.writeValueAsString(event))
    }

}