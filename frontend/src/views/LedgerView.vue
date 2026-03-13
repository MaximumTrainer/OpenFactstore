<template>
  <div>
    <h1 class="text-2xl font-bold text-gray-900 mb-6">Immutable Ledger</h1>

    <!-- Status Card -->
    <div v-if="status" class="bg-white shadow rounded-lg p-5 mb-6 flex items-center justify-between">
      <div>
        <p class="text-sm text-gray-500">Ledger Type</p>
        <p class="text-lg font-semibold text-gray-900 capitalize">{{ status.type }}</p>
      </div>
      <div>
        <p class="text-sm text-gray-500">Total Entries</p>
        <p class="text-lg font-semibold text-gray-900">{{ status.totalEntries }}</p>
      </div>
      <div>
        <p class="text-sm text-gray-500">Status</p>
        <span
          :class="status.healthy ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'"
          class="inline-flex items-center px-2.5 py-0.5 rounded-full text-sm font-medium"
        >
          {{ status.healthy ? '✔ Healthy' : '✘ Unhealthy' }}
        </span>
      </div>
      <div class="text-sm text-gray-500 max-w-xs truncate">{{ status.message }}</div>
    </div>

    <!-- Chain Verification -->
    <div class="bg-white shadow rounded-lg p-5 mb-6">
      <h2 class="text-lg font-semibold text-gray-900 mb-4">Verify Chain Integrity</h2>
      <div class="flex flex-wrap gap-4 items-end">
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">From</label>
          <input
            v-model="chainFrom"
            type="datetime-local"
            class="border border-gray-300 rounded-md px-3 py-1.5 text-sm focus:ring-indigo-500 focus:border-indigo-500"
          />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">To</label>
          <input
            v-model="chainTo"
            type="datetime-local"
            class="border border-gray-300 rounded-md px-3 py-1.5 text-sm focus:ring-indigo-500 focus:border-indigo-500"
          />
        </div>
        <button
          @click="runChainVerification"
          :disabled="!chainFrom || !chainTo || chainVerifying"
          class="bg-indigo-600 text-white px-4 py-2 rounded-md text-sm font-medium hover:bg-indigo-700 disabled:opacity-50"
        >
          {{ chainVerifying ? 'Verifying…' : 'Verify Chain' }}
        </button>
      </div>
      <div v-if="chainResult" class="mt-4 p-4 rounded-lg" :class="chainResult.valid ? 'bg-green-50' : 'bg-red-50'">
        <div class="flex items-center gap-2 mb-2">
          <span class="text-lg">{{ chainResult.valid ? '✔' : '✘' }}</span>
          <span class="font-semibold" :class="chainResult.valid ? 'text-green-800' : 'text-red-800'">
            {{ chainResult.valid ? 'Chain Integrity Verified' : 'Chain Integrity Failure' }}
          </span>
          <span class="text-sm text-gray-500">({{ chainResult.entriesChecked }} entries checked)</span>
        </div>
        <p class="text-sm text-gray-700">{{ chainResult.message }}</p>
        <p v-if="chainResult.brokenAt" class="text-sm text-red-700 mt-1">
          Broken at entry: <code class="font-mono">{{ chainResult.brokenAt }}</code>
        </p>
      </div>
    </div>

    <!-- Fact Verification -->
    <div class="bg-white shadow rounded-lg p-5 mb-6">
      <h2 class="text-lg font-semibold text-gray-900 mb-4">Verify Specific Fact</h2>
      <div class="flex gap-4 items-end">
        <div class="flex-1">
          <label class="block text-sm font-medium text-gray-700 mb-1">Fact ID (UUID)</label>
          <input
            v-model="factIdInput"
            type="text"
            placeholder="e.g. a1b2c3d4-e5f6-7890-abcd-ef1234567890"
            class="w-full border border-gray-300 rounded-md px-3 py-1.5 text-sm focus:ring-indigo-500 focus:border-indigo-500"
          />
        </div>
        <button
          @click="runFactVerification"
          :disabled="!factIdInput || factVerifying"
          class="bg-indigo-600 text-white px-4 py-2 rounded-md text-sm font-medium hover:bg-indigo-700 disabled:opacity-50"
        >
          {{ factVerifying ? 'Verifying…' : 'Verify Fact' }}
        </button>
      </div>
      <div v-if="factResult" class="mt-4 p-4 rounded-lg" :class="factResult.verified ? 'bg-green-50' : 'bg-red-50'">
        <div class="flex items-center gap-2 mb-2">
          <span class="text-lg">{{ factResult.verified ? '✔' : '✘' }}</span>
          <span class="font-semibold" :class="factResult.verified ? 'text-green-800' : 'text-red-800'">
            {{ factResult.verified ? 'Fact Verified' : 'Fact Not Verified' }}
          </span>
          <span v-if="factResult.chainPosition !== null" class="text-sm text-gray-500">
            (chain position {{ factResult.chainPosition }})
          </span>
        </div>
        <dl class="grid grid-cols-1 gap-1 text-sm">
          <div v-if="factResult.contentHash">
            <dt class="text-gray-500 inline">Content Hash: </dt>
            <dd class="font-mono text-gray-800 inline break-all">{{ factResult.contentHash }}</dd>
          </div>
          <div v-if="factResult.previousHash">
            <dt class="text-gray-500 inline">Previous Hash: </dt>
            <dd class="font-mono text-gray-800 inline break-all">{{ factResult.previousHash }}</dd>
          </div>
          <div v-if="factResult.ledgerTimestamp">
            <dt class="text-gray-500 inline">Ledger Timestamp: </dt>
            <dd class="text-gray-800 inline">{{ new Date(factResult.ledgerTimestamp).toLocaleString() }}</dd>
          </div>
          <div>
            <dt class="text-gray-500 inline">Message: </dt>
            <dd class="text-gray-800 inline">{{ factResult.message }}</dd>
          </div>
        </dl>
      </div>
    </div>

    <!-- Ledger Entry Browser -->
    <div class="bg-white shadow rounded-lg overflow-hidden">
      <div class="px-5 py-4 border-b border-gray-200 flex items-center justify-between">
        <h2 class="text-lg font-semibold text-gray-900">Ledger Entries</h2>
        <span class="text-sm text-gray-500">{{ pagedEntries?.totalElements ?? 0 }} total</span>
      </div>

      <div v-if="loading" class="text-center text-gray-500 py-10">Loading…</div>
      <div v-else-if="!pagedEntries || pagedEntries.entries.length === 0" class="text-center text-gray-400 py-10">
        No ledger entries found.
      </div>
      <div v-else>
        <table class="min-w-full divide-y divide-gray-200 text-sm">
          <thead class="bg-gray-50">
            <tr>
              <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Entry ID</th>
              <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Fact ID</th>
              <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Event Type</th>
              <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Content Hash</th>
              <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Timestamp</th>
            </tr>
          </thead>
          <tbody class="bg-white divide-y divide-gray-200">
            <tr v-for="entry in pagedEntries.entries" :key="entry.entryId" class="hover:bg-gray-50">
              <td class="px-4 py-3 font-mono text-xs text-gray-600 max-w-[120px] truncate">{{ entry.entryId }}</td>
              <td class="px-4 py-3 font-mono text-xs text-gray-600 max-w-[120px] truncate">{{ entry.factId }}</td>
              <td class="px-4 py-3">
                <span class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-indigo-100 text-indigo-800">
                  {{ entry.eventType }}
                </span>
              </td>
              <td class="px-4 py-3 font-mono text-xs text-gray-500 max-w-[180px] truncate" :title="entry.contentHash">
                {{ entry.contentHash.substring(0, 16) }}…
              </td>
              <td class="px-4 py-3 text-gray-600 text-xs whitespace-nowrap">
                {{ new Date(entry.timestamp).toLocaleString() }}
              </td>
            </tr>
          </tbody>
        </table>

        <!-- Pagination -->
        <div class="px-5 py-3 border-t border-gray-200 flex items-center justify-between">
          <p class="text-sm text-gray-500">
            Page {{ currentPage + 1 }} of {{ pagedEntries.totalPages || 1 }}
          </p>
          <div class="flex gap-2">
            <button
              :disabled="currentPage === 0"
              @click="goToPage(currentPage - 1)"
              class="px-3 py-1 text-sm border rounded-md hover:bg-gray-50 disabled:opacity-40"
            >
              ← Prev
            </button>
            <button
              :disabled="currentPage + 1 >= (pagedEntries.totalPages || 1)"
              @click="goToPage(currentPage + 1)"
              class="px-3 py-1 text-sm border rounded-md hover:bg-gray-50 disabled:opacity-40"
            >
              Next →
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import {
  getLedgerEntries,
  getLedgerStatus,
  verifyFact,
  verifyChain
} from '../api/ledger'
import type { PagedLedgerEntries, LedgerVerification, ChainVerification, LedgerStatus } from '../types'

const loading = ref(true)
const currentPage = ref(0)
const pageSize = 20

const pagedEntries = ref<PagedLedgerEntries | null>(null)
const status = ref<LedgerStatus | null>(null)

const factIdInput = ref('')
const factVerifying = ref(false)
const factResult = ref<LedgerVerification | null>(null)

const chainFrom = ref('')
const chainTo = ref('')
const chainVerifying = ref(false)
const chainResult = ref<ChainVerification | null>(null)

async function fetchEntries(page = 0) {
  loading.value = true
  try {
    const res = await getLedgerEntries(page, pageSize)
    pagedEntries.value = res.data
    currentPage.value = page
  } catch {
    pagedEntries.value = null
  } finally {
    loading.value = false
  }
}

async function fetchStatus() {
  try {
    const res = await getLedgerStatus()
    status.value = res.data
  } catch {
    status.value = null
  }
}

async function runFactVerification() {
  if (!factIdInput.value) return
  factVerifying.value = true
  factResult.value = null
  try {
    const res = await verifyFact(factIdInput.value.trim())
    factResult.value = res.data
  } catch {
    factResult.value = null
  } finally {
    factVerifying.value = false
  }
}

async function runChainVerification() {
  if (!chainFrom.value || !chainTo.value) return
  chainVerifying.value = true
  chainResult.value = null
  try {
    const from = new Date(chainFrom.value).toISOString()
    const to = new Date(chainTo.value).toISOString()
    const res = await verifyChain(from, to)
    chainResult.value = res.data
  } catch {
    chainResult.value = null
  } finally {
    chainVerifying.value = false
  }
}

function goToPage(page: number) {
  fetchEntries(page)
}

onMounted(async () => {
  await Promise.all([fetchEntries(), fetchStatus()])
})
</script>
