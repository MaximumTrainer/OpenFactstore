package com.factstore.adapter.mock

import com.factstore.application.signing.EventCanonicalForm
import com.factstore.core.port.outbound.ChainHead
import com.factstore.core.port.outbound.IEventChainHead

class InMemoryEventChainHead : IEventChainHead {
    private var head = ChainHead(null, EventCanonicalForm.GENESIS_HASH)
    override fun lockForAppend(): ChainHead = head
    override fun advance(sequenceNumber: Long, hash: String) {
        head = ChainHead(sequenceNumber, hash)
    }
    override fun read(): ChainHead = head
}
