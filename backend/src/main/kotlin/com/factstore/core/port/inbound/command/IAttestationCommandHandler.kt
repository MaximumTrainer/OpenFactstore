package com.factstore.core.port.inbound.command

import com.factstore.dto.command.CommandResult
import com.factstore.dto.command.RecordAttestationCommand
import com.factstore.dto.command.UploadEvidenceCommand

interface IAttestationCommandHandler {
    fun recordAttestation(command: RecordAttestationCommand): CommandResult
    fun uploadEvidence(command: UploadEvidenceCommand): CommandResult
}
