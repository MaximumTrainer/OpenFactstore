package com.factstore.application.gates

import jakarta.persistence.PostPersist
import jakarta.persistence.PostRemove
import jakarta.persistence.PostUpdate
import jakarta.persistence.PrePersist
import jakarta.persistence.PreRemove
import jakarta.persistence.PreUpdate
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A JPA entity listener registered for every entity (`META-INF/orm.xml`, #194). Any insert,
 * update or delete tells the read-side caches that their data may be stale: at once, and again
 * when the transaction commits. Clearing again after commit matters: a read in between still
 * sees the old data and could cache it. JPA instantiates listeners itself, so caches register
 * here rather than being injected.
 */
class ReadModelChangeListener {
    @PrePersist
    @PreUpdate
    @PreRemove
    @PostPersist
    @PostUpdate
    @PostRemove
    fun onChange(entity: Any) {
        notifySubscribers()
        if (TransactionSynchronizationManager.isSynchronizationActive() && !TransactionSynchronizationManager.hasResource(AFTER_COMMIT)) {
            TransactionSynchronizationManager.bindResource(AFTER_COMMIT, true)
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() = notifySubscribers()
                override fun afterCompletion(status: Int) {
                    TransactionSynchronizationManager.unbindResourceIfPossible(AFTER_COMMIT)
                }
            })
        }
    }

    companion object {
        private val AFTER_COMMIT = Any()
        private val subscribers = CopyOnWriteArrayList<ReadModelChangeNotifier>()

        fun subscribe(notifier: ReadModelChangeNotifier) {
            subscribers += notifier
        }

        fun unsubscribe(notifier: ReadModelChangeNotifier) {
            subscribers -= notifier
        }

        private fun notifySubscribers() = subscribers.forEach { it.changed() }
    }
}
