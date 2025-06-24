package icu.neurospicy.fibi.outgoing.mongodb.converters

import icu.neurospicy.fibi.outgoing.mongodb.converters.OffsetDateTimeToDocumentConverter.Companion.DATE_TIME
import icu.neurospicy.fibi.outgoing.mongodb.converters.OffsetDateTimeToDocumentConverter.Companion.OFFSET
import icu.neurospicy.fibi.outgoing.mongodb.converters.ZonedDateTimeToDocumentConverter.Companion.ZONE
import org.bson.Document
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import org.springframework.stereotype.Component
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.Temporal
import java.util.*

@Component
@ReadingConverter
class DocumentToTemporalConverter : Converter<Document?, Temporal?> {
    override fun convert(document: Document): Temporal? {
        return when {
            document.containsKey(OFFSET) -> {
                val dateTime: Date = document.getDate(DATE_TIME)
                val offsetId: String = document.getString(OFFSET)
                val offset = ZoneOffset.of(offsetId)
                OffsetDateTime.ofInstant(dateTime.toInstant(), offset)
            }

            document.containsKey(ZONE) -> {
                val dateTime: Date = document.getDate(ZonedDateTimeToDocumentConverter.DATE_TIME)
                val zoneId: String = document.getString(ZONE)
                val zone = ZoneId.of(zoneId)
                ZonedDateTime.ofInstant(dateTime.toInstant(), zone)
            }

            else -> {
                null
            }
        }
    }
}