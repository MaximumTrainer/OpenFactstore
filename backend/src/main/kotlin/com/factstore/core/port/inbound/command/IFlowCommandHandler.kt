package com.factstore.core.port.inbound.command

import com.factstore.dto.command.CommandResult
import com.factstore.dto.command.CreateFlowCommand
import com.factstore.dto.command.DeleteFlowCommand
import com.factstore.dto.command.UpdateFlowCommand

interface IFlowCommandHandler {
    fun createFlow(command: CreateFlowCommand): CommandResult
    fun updateFlow(command: UpdateFlowCommand): CommandResult
    fun deleteFlow(command: DeleteFlowCommand)
}
