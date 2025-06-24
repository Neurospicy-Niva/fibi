package icu.neurospicy.fibi.domain.service.friends.interaction.prompt

fun buildEntityIdentificationPrompt(
    action: String,
    entityName: String,
    entityListText: String,
    rawText: String,
    clarificationQuestion: String?,
    answer: String?,
    lastCreatedEntityDescription: String? = null
): String {
    val recentRefHint = if (!lastCreatedEntityDescription.isNullOrBlank())
        "\n📌 If the user vaguely refers to '$entityName' (e.g., 'update the task'), they likely mean the last created one:\n$lastCreatedEntityDescription\n"
    else ""

    return """
You are helping to identify which $entityName the user wants to $action.

You are given:
- a list of $entityName entries
- a user message
- optional clarification follow-ups

🎯 Select the single $entityName to $action based on user intent. Focus totally on which item to update.

✅ If there's only one matching $entityName, return its ID.
❓ If multiple match equally well, return a clarifying question.
❌ If no good match exists, return an empty JSON object.

User does not know IDs. Never include explanations.

Return JSON like:
{
  "id": "...",
  "clarifyingQuestion": "..." // optional
}

$entityName list:
$entityListText
$recentRefHint
Conversation:
"$rawText"
${if (!clarificationQuestion.isNullOrBlank()) "---\n\"$clarificationQuestion\"" else ""}
${if (!answer.isNullOrBlank()) "---\n\"$answer\"" else ""}
    """.trimIndent()
}