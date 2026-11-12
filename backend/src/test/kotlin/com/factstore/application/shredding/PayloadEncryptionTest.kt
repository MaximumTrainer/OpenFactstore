package com.factstore.application.shredding

import com.factstore.adapter.outbound.signing.LocalKeyEncryptionKey
import com.factstore.core.domain.DataKey
import com.factstore.core.domain.DataKeyStatus
import com.factstore.core.port.outbound.IDataKeyRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment
import java.time.Instant
import java.util.UUID

/** #200 — the envelope encryption underneath shredding. */
class PayloadEncryptionTest {

    private class InMemoryKeys : IDataKeyRepository {
        val keys = mutableMapOf<Pair<String, String>, DataKey>()
        override fun save(key: DataKey) = key.also { keys[key.tenant to key.period] = it }
        override fun find(tenant: String, period: String) = keys[tenant to period]
        override fun findAll() = keys.values.toList()
    }

    private val keys = InMemoryKeys()
    private val encryption = PayloadEncryption(keys, LocalKeyEncryptionKey(MockEnvironment()))
    private val factId = UUID.randomUUID()

    @Test
    fun `#200 content round-trips and the ciphertext names its key, not its content`() {
        val sealed = encryption.encrypt("acme", "2026-03", factId, """{"secret":"value"}""")
        assertThat(sealed).startsWith("enc:v1:acme:2026-03:").doesNotContain("secret")
        assertThat(encryption.decrypt(factId, sealed)).isEqualTo("""{"secret":"value"}""")
    }

    @Test
    fun `#200 the data key is stored only wrapped`() {
        encryption.encrypt("acme", "2026-03", factId, "x")
        val key = keys.find("acme", "2026-03")!!
        assertThat(key.wrappedKey).isNotBlank()
        assertThat(key.kekId).isEqualTo("local")
    }

    @Test
    fun `#200 ciphertext cannot be moved to another fact`() {
        val sealed = encryption.encrypt("acme", "2026-03", factId, "x")
        assertThatThrownBy { encryption.decrypt(UUID.randomUUID(), sealed) }.isInstanceOf(javax.crypto.AEADBadTagException::class.java)
    }

    @Test
    fun `#200 once the key is shredded, content is gone and the period is closed to new writes`() {
        val sealed = encryption.encrypt("acme", "2026-03", factId, "x")
        keys.find("acme", "2026-03")!!.apply { wrappedKey = null; status = DataKeyStatus.SHREDDED }

        assertThatThrownBy { encryption.decrypt(factId, sealed) }.isInstanceOf(FactShreddedException::class.java)
        assertThatThrownBy { encryption.encrypt("acme", "2026-03", UUID.randomUUID(), "y") }.isInstanceOf(FactShreddedException::class.java)
    }

    @Test
    fun `#200 each tenant and month has its own key`() {
        encryption.encrypt("acme", "2026-03", factId, "x")
        encryption.encrypt("acme", "2026-04", factId, "x")
        encryption.encrypt("globex", "2026-03", factId, "x")
        assertThat(keys.keys.keys).containsExactlyInAnyOrder("acme" to "2026-03", "acme" to "2026-04", "globex" to "2026-03")
        assertThat(encryption.periodOf(Instant.parse("2026-12-31T23:59:59Z"))).isEqualTo("2026-12")
    }

    @Test
    fun `#200 content stored before encryption still reads`() {
        assertThat(encryption.decrypt(factId, """{"legacy":true}""")).isEqualTo("""{"legacy":true}""")
    }
}
