package com.factstore.adapter.outbound

import java.net.InetAddress
import java.net.URI

/**
 * Validates outbound webhook URLs to prevent SSRF attacks.
 * Rejects:
 * - Non-HTTPS URLs
 * - Loopback / link-local / private network targets (e.g. 127.x, 10.x, 172.16-31.x, 192.168.x)
 */
object UrlValidator {

    private val PRIVATE_RANGES: List<(ByteArray) -> Boolean> = listOf(
        { b -> b[0] == 10.toByte() },                                           // 10.0.0.0/8
        { b -> b[0] == 172.toByte() && b[1].toInt() and 0xFF in 16..31 },       // 172.16.0.0/12
        { b -> b[0] == 192.toByte() && b[1] == 168.toByte() },                  // 192.168.0.0/16
        { b -> b[0] == 127.toByte() },                                          // 127.0.0.0/8 loopback
        { b -> b[0] == 169.toByte() && b[1] == 254.toByte() },                  // 169.254.0.0/16 link-local
        { b -> b[0] == 0.toByte() },                                            // 0.0.0.0/8
        { b -> b[0] == 100.toByte() && b[1].toInt() and 0xFF in 64..127 }       // 100.64.0.0/10 shared address
    )

    /**
     * Validates that the URL is a safe, non-private HTTPS endpoint.
     * @throws IllegalArgumentException if the URL is unsafe.
     */
    fun validate(url: String) {
        val uri = try {
            URI(url)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid URL: $url")
        }

        require(uri.scheme?.lowercase() == "https") {
            "Only HTTPS webhook URLs are allowed (got scheme '${uri.scheme}')"
        }

        val host = uri.host
            ?: throw IllegalArgumentException("URL must have a valid host: $url")

        val address = try {
            InetAddress.getByName(host)
        } catch (e: Exception) {
            throw IllegalArgumentException("Cannot resolve hostname '$host': ${e.message}")
        }

        val bytes = address.address
        for (check in PRIVATE_RANGES) {
            if (check(bytes)) {
                throw IllegalArgumentException(
                    "Webhook URL targets a private/internal network address '$host' — this is not allowed"
                )
            }
        }
    }
}
