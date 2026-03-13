package com.factstore.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "ledger")
data class LedgerProperties(
    val enabled: Boolean = false,
    val type: String = "local",
    val qldb: QldbProperties = QldbProperties(),
    val local: LocalProperties = LocalProperties()
) {
    data class QldbProperties(
        val ledgerName: String = "factstore-ledger",
        val region: String = "us-east-1"
    )

    data class LocalProperties(
        val storagePath: String = "./data/ledger"
    )
}
