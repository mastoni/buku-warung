package id.skmnetwork.bukuwarung.sync

import id.skmnetwork.bukuwarung.data.local.entity.SyncQueueEntity

interface SyncProvider {
    val providerName: String
    suspend fun processItem(item: SyncQueueEntity): Result<Unit>
}
