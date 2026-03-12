package com.factstore.application

import java.util.UUID

sealed class SlackCommand {
    object Help : SlackCommand()
    data class Search(val shaPrefix: String) : SlackCommand()
    data class Env(val name: String, val snapshotRef: String? = null) : SlackCommand()
    data class TrailDetails(val trailId: UUID) : SlackCommand()
    data class Approve(val approvalId: String, val comment: String? = null) : SlackCommand()
    data class Reject(val approvalId: String, val comment: String? = null) : SlackCommand()
    data class Unknown(val input: String) : SlackCommand()
}

class SlackCommandParser {

    /**
     * Parses the text portion of a Slack slash command into a typed [SlackCommand].
     *
     * Supported sub-commands:
     * - `help`
     * - `search <sha-prefix>`
     * - `env <name>[#<N>|@{timestamp}]`
     * - `trail <trailId>`
     * - `approve <approvalId> [comment]`
     * - `reject <approvalId> [comment]`
     */
    fun parse(text: String): SlackCommand {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return SlackCommand.Help

        val parts = trimmed.split("\\s+".toRegex(), limit = 3)
        val subCommand = parts[0].lowercase()

        return when (subCommand) {
            "help" -> SlackCommand.Help

            "search" -> {
                if (parts.size < 2) SlackCommand.Help
                else SlackCommand.Search(parts[1])
            }

            "env" -> {
                if (parts.size < 2) SlackCommand.Help
                else SlackCommand.Env(parts[1])
            }

            "trail" -> {
                if (parts.size < 2) SlackCommand.Help
                else {
                    runCatching { UUID.fromString(parts[1]) }.fold(
                        onSuccess = { SlackCommand.TrailDetails(it) },
                        onFailure = { SlackCommand.Help }
                    )
                }
            }

            "approve" -> {
                if (parts.size < 2) SlackCommand.Help
                else SlackCommand.Approve(parts[1], parts.getOrNull(2))
            }

            "reject" -> {
                if (parts.size < 2) SlackCommand.Help
                else SlackCommand.Reject(parts[1], parts.getOrNull(2))
            }

            else -> SlackCommand.Unknown(trimmed)
        }
    }
}
