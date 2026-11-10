package com.factstore.core.port.outbound

/** The newest signed event record (#184). */
data class ChainHead(val lastSequence: Long?, val lastHash: String)

interface IEventChainHead {
    /** The head, locked until the transaction ends, so appends chain in sequence order. */
    fun lockForAppend(): ChainHead
    fun advance(sequenceNumber: Long, hash: String)
    fun read(): ChainHead
}
