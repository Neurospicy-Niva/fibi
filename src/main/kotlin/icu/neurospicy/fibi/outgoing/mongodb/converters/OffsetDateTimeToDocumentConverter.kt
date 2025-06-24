package icu.neurospicy.fibi.outgoing.mongodb.converters

import org.bson.Document
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.WritingConverter
import org.springframework.stereotype.Component
import java.time.OffsetDateTime
import java.util.*

@Component
@WritingConverter
class OffsetDateTimeToDocumentConverter : Converter<OffsetDateTime?, Document?> {
    companion object {
        const val DATE_TIME = "dateTime"
        const val OFFSET = "offset"
    }

    override fun convert(dt: OffsetDateTime): Document? {
        val document = Document()
        document[DATE_TIME] = Date.from(dt.toInstant())
        document[OFFSET] = dt.offset.id
        return document
    }
}
