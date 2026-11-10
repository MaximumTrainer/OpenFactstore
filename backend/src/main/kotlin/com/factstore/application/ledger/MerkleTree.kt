package com.factstore.application.ledger

import java.security.MessageDigest

/**
 * The Merkle tree over a checkpoint's event records (#188), as RFC 6962 / RFC 9162 define it:
 * a leaf hash is `SHA-256(0x00 || data)`, an interior node is `SHA-256(0x01 || left || right)`,
 * and a tree of n leaves splits at the largest power of two below n. The prefixes stop a leaf
 * from being passed off as a node. Using the published algorithm means an auditor can verify a
 * proof with any RFC 9162 implementation, not just Factstore's.
 */
object MerkleTree {

    fun leafHash(data: ByteArray): ByteArray = sha256(byteArrayOf(0x00), data)

    private fun nodeHash(left: ByteArray, right: ByteArray): ByteArray = sha256(byteArrayOf(0x01), left, right)

    /** The Merkle Tree Hash of [leaves] (RFC 9162 section 2.1.1). Needs at least one leaf. */
    fun root(leaves: List<ByteArray>): ByteArray {
        require(leaves.isNotEmpty()) { "a Merkle tree needs at least one leaf" }
        if (leaves.size == 1) return leafHash(leaves[0])
        val k = largestPowerOfTwoBelow(leaves.size)
        return nodeHash(root(leaves.subList(0, k)), root(leaves.subList(k, leaves.size)))
    }

    /** The inclusion proof for leaf [index], nearest sibling first (RFC 9162 section 2.1.3.1). */
    fun auditPath(leaves: List<ByteArray>, index: Int): List<ByteArray> {
        require(index in leaves.indices) { "leaf index $index is outside a tree of ${leaves.size}" }
        if (leaves.size == 1) return emptyList()
        val k = largestPowerOfTwoBelow(leaves.size)
        return if (index < k) {
            auditPath(leaves.subList(0, k), index) + listOf(root(leaves.subList(k, leaves.size)))
        } else {
            auditPath(leaves.subList(k, leaves.size), index - k) + listOf(root(leaves.subList(0, k)))
        }
    }

    /** Does [path] prove that [leaf] is leaf [index] of a tree of [treeSize] with [root]? (RFC 9162 section 2.1.3.2) */
    fun verify(leaf: ByteArray, index: Int, treeSize: Int, path: List<ByteArray>, root: ByteArray): Boolean {
        if (index < 0 || index >= treeSize) return false
        var fn = index.toLong()
        var sn = treeSize.toLong() - 1
        var r = leafHash(leaf)
        for (p in path) {
            if (sn == 0L) return false
            if (fn and 1L == 1L || fn == sn) {
                r = nodeHash(p, r)
                while (fn and 1L == 0L && fn != 0L) {
                    fn = fn shr 1
                    sn = sn shr 1
                }
            } else {
                r = nodeHash(r, p)
            }
            fn = fn shr 1
            sn = sn shr 1
        }
        return sn == 0L && MessageDigest.isEqual(r, root)
    }

    private fun largestPowerOfTwoBelow(n: Int): Int = Integer.highestOneBit(n - 1)

    private fun sha256(vararg parts: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").run {
            parts.forEach { update(it) }
            digest()
        }
}
