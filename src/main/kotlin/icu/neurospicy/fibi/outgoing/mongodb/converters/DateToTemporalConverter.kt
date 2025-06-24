package icu.neurospicy.fibi.outgoing.mongodb.converters

import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.Temporal
import java.util.*

@Component
@ReadingConverter
class DateToTemporalConverter : Converter<Date?, Temporal?> {
    /**
     * Expect dates to be a whole day represented by local date.
     */
    override fun convert(date: Date): Temporal? {
        return LocalDate.ofInstant(date.toInstant(), ZoneOffset.UTC)
    }
}