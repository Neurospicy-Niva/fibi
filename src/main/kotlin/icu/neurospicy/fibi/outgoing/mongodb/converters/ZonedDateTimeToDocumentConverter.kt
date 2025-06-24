package icu.neurospicy.fibi.outgoing.mongodb.converters

import org.bson.Document
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.WritingConverter
import org.springframework.stereotype.Component
import java.time.ZonedDateTime
import java.util.*

@Component
@WritingConverter
class ZonedDateTimeToDocumentConverter : Converter<ZonedDateTime?, Document?> {
    companion object {
        const val DATE_TIME = "dateTime"
        const val ZONE = "zone"
    }

    override fun convert(zonedDateTime: ZonedDateTime): Document? {
        val document = Document()
        document[DATE_TIME] = Date.from(zonedDateTime.toInstant())
        document[ZONE] = zonedDateTime.zone.id
        document["offset"] = zonedDateTime.offset.toString()
        return document
    }
}
