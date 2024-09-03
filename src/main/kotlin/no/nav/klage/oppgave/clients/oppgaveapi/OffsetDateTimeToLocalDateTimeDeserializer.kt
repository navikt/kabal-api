package no.nav.klage.oppgave.clients.oppgaveapi

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class OffsetDateTimeToLocalDateTimeDeserializer : StdDeserializer<LocalDateTime>(LocalDateTime::class.java) {

    override fun deserialize(jsonParser: JsonParser, ctxt: DeserializationContext?): LocalDateTime {
        return LocalDateTime.parse(jsonParser.readValueAs(String::class.java), DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }

}