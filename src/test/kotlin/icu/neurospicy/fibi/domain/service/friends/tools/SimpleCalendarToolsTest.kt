package icu.neurospicy.fibi.domain.service.friends.tools

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SimpleCalendarToolsTest {
    @Test
    fun `get day of week for certain date`() {
        assertThat(SimpleCalendarTools().getWeekDay("2019-02-12")).isEqualTo("Tuesday, February 12, 2019")
        assertThat(SimpleCalendarTools().getWeekDay("1989-11-10")).isEqualTo("Friday, November 10, 1989")
    }

    @Test
    fun `get days of week for certain dates`() {
        assertThat(SimpleCalendarTools().getWeekDays("2019-02-12")).contains("Tuesday, February 12, 2019")
            .contains(" Monday, February 18, 2019")
    }
}