package com.factstore.application.ledger

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.security.MessageDigest

/**
 * #188 — the Merkle tree over checkpointed event records (RFC 6962 hashing: leaves prefixed 0x00,
 * nodes 0x01, the larger subtree a power of two). Every leaf of every tree size must prove its
 * inclusion against the root, and any change to the leaf, a sibling or the position must fail.
 */
class MerkleTreeTest {

    private fun leaves(n: Int): List<ByteArray> =
        (0 until n).map { MessageDigest.getInstance("SHA-256").digest("record-$it".toByteArray()) }

    @Test
    fun `#188 every leaf proves its inclusion, for every tree shape`() {
        for (n in listOf(1, 2, 3, 4, 5, 7, 8, 9, 16, 17, 33)) {
            val data = leaves(n)
            val root = MerkleTree.root(data)
            for (i in 0 until n) {
                val path = MerkleTree.auditPath(data, i)
                assertThat(MerkleTree.verify(data[i], i, n, path, root)).describedAs("leaf $i of $n").isTrue()
            }
        }
    }

    @Test
    fun `#188 a changed leaf, sibling or position does not verify`() {
        val data = leaves(9)
        val root = MerkleTree.root(data)
        val path = MerkleTree.auditPath(data, 4)
        val forged = data[4].copyOf().also { it[0] = (it[0] + 1).toByte() }
        val badSibling = path.toMutableList().also { it[1] = it[1].copyOf().also { s -> s[5] = (s[5] + 1).toByte() } }

        assertThat(MerkleTree.verify(forged, 4, 9, path, root)).isFalse()
        assertThat(MerkleTree.verify(data[4], 4, 9, badSibling, root)).isFalse()
        assertThat(MerkleTree.verify(data[4], 5, 9, path, root)).isFalse()
        // The tree size is bound by the signed checkpoint, not by the path alone; a size the path cannot reach fails.
        assertThat(MerkleTree.verify(data[4], 4, 17, path, root)).isFalse()
    }

    @Test
    fun `#188 the tree follows RFC 6962`() {
        fun sha(vararg parts: ByteArray) = MessageDigest.getInstance("SHA-256").run { parts.forEach { update(it) }; digest() }
        val (a, b, c) = leaves(3)
        val leaf = { d: ByteArray -> sha(byteArrayOf(0), d) }
        val node = { l: ByteArray, r: ByteArray -> sha(byteArrayOf(1), l, r) }

        assertThat(MerkleTree.root(listOf(a))).isEqualTo(leaf(a))
        assertThat(MerkleTree.root(listOf(a, b, c))).isEqualTo(node(node(leaf(a), leaf(b)), leaf(c)))
    }
}
