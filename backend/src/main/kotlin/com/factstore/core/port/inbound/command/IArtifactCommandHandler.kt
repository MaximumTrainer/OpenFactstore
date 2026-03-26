package com.factstore.core.port.inbound.command

import com.factstore.dto.command.CommandResult
import com.factstore.dto.command.ReportArtifactCommand

interface IArtifactCommandHandler {
    fun reportArtifact(command: ReportArtifactCommand): CommandResult
}
