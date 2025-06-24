package icu.neurospicy.fibi.outgoing.mongodb.converters

import icu.neurospicy.fibi.outgoing.mongodb.converters.OffsetDateTimeToDocumentConverter.Companion.DATE_TIME
import icu.neurospicy.fibi.outgoing.mongodb.converters.OffsetDateTimeToDocumentConverter.Companion.OFFSET
import org.bson.Document
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import org.springframework.stereotype.Component
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

@Component
@ReadingConverter
class DocumentToOffsetDateTimeConverter : Converter<Document?, OffsetDateTime?> {
    override fun convert(document: Document): OffsetDateTime? {
        val dateTime: Date = document.getDate(DATE_TIME)
        val offsetId: String = document.getString(OFFSET)
        val offset = ZoneOffset.of(offsetId)
        return OffsetDateTime.ofInstant(dateTime.toInstant(), offset)
    }
}