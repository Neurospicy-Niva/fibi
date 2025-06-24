package icu.neurospicy.fibi.outgoing.signal

import java.util.regex.Matcher
import java.util.regex.Pattern

class SignalMessage(val text: String, val textStyles: List<String>) {
    companion object {
        fun from(text: String): SignalMessage {
            val textStyles = mutableListOf<String>()
            var matchFound: Boolean
            var sanitizedText = text
            do {
                matchFound = false
                Pattern.compile("\\*\\*.*\\*\\*").matcher(sanitizedText).apply {
                    if (find()) {
                        matchFound = true
                        sanitizedText =
                            replaceStyle(this, "BOLD", 2, textStyles)
                    }
                }
                Pattern.compile("__.*__").matcher(sanitizedText).apply {
                    if (find()) {
                        matchFound = true
                        sanitizedText = replaceStyle(this, "ITALIC", 2, textStyles)
                    }
                }
                Pattern.compile("`.*`").matcher(sanitizedText).apply {
                    if (find()) {
                        matchFound = true
                        sanitizedText =
                            replaceStyle(this, "MONOSPACE", 1, textStyles)
                    }
                }
                Pattern.compile("--.*--").matcher(sanitizedText).apply {
                    if (find()) {
                        matchFound = true
                        sanitizedText =
                            replaceStyle(this, "STRIKETHROUGH", 2, textStyles)
                    }
                }
            } while (matchFound)
            return SignalMessage(sanitizedText, textStyles)
        }

        private fun replaceStyle(
            matcher: Matcher,
            type: String,
            length: Int,
            textStyles: MutableList<String>
        ): String {
            textStyles.add(
                "${matcher.start()}:${matcher.end() - matcher.start() - (2 * length)}:$type"
            )
            // $ are treated as group reference in "replaceFirst", thus escape it and remove escaping afterward
            return matcher.replaceFirst(
                matcher.group(0).substring(length, matcher.group(0).length - length).replace("$", "\\$")
            ).replace("\\$", "$")
        }
    }

}