package icu.neurospicy.fibi.outgoing.signal

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SignalMessageTest {
    @Test
    fun `should apply bold on markdown`() {
        val text = "This is a **great** idea!"

        val signalMessage = SignalMessage.from(text)

        assertThat(signalMessage.text).isEqualTo("This is a great idea!")
        assertThat(signalMessage.textStyles).allSatisfy {
            assertThat(it).contains("${"This is a ".length}:${"great".length}:BOLD")
        }
    }

    @Test
    fun `should apply italic on markdown`() {
        val text = "This is a __great__ idea!"

        val signalMessage = SignalMessage.from(text)

        assertThat(signalMessage.text).isEqualTo("This is a great idea!")
        assertThat(signalMessage.textStyles).allSatisfy {
            assertThat(it).contains("${"This is a ".length}:${"great".length}:ITALIC")
        }
    }

    @Test
    fun `should apply italic on message containing $`() {
        val text =
            "__Set reminder: Reminding you before the appointment to pick up from school. The regex pattern '^Pick up from school$' simply matches any entry in your schedule that exactly says 'Pick up from school'__"

        val signalMessage = SignalMessage.from(text)

        assertThat(signalMessage.text).isEqualTo("Set reminder: Reminding you before the appointment to pick up from school. The regex pattern '^Pick up from school$' simply matches any entry in your schedule that exactly says 'Pick up from school'")
        assertThat(signalMessage.textStyles).allSatisfy {
            assertThat(it).contains("0:${"Set reminder: Reminding you before the appointment to pick up from school. The regex pattern '^Pick up from school$' simply matches any entry in your schedule that exactly says 'Pick up from school'".length}:ITALIC")
        }
    }

    @Test
    fun `should apply monospace on markdown`() {
        val text = "This is a `great` idea!"

        val signalMessage = SignalMessage.from(text)

        assertThat(signalMessage.text).isEqualTo("This is a great idea!")
        assertThat(signalMessage.textStyles).allSatisfy {
            assertThat(it).contains("${"This is a ".length}:${"great".length}:MONOSPACE")
        }
    }
}