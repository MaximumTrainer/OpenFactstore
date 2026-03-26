package com.factstore.core.port.inbound.command

import com.factstore.dto.command.CommandResult
import com.factstore.dto.command.CreateTrailCommand

interface ITrailCommandHandler {
    fun createTrail(command: CreateTrailCommand): CommandResult
}
