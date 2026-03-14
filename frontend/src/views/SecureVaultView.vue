<template>
  <div>
    <h1 class="text-2xl font-bold text-gray-900 mb-6">Secure Vault</h1>
    <p class="text-gray-500 mb-6">
      Store and retrieve tamper-proof compliance evidence in HashiCorp Vault.
      Evidence is encrypted at rest using Vault's KV v2 secrets engine.
    </p>

    <!-- Vault Health Status -->
    <div class="bg-white rounded-lg shadow p-5 mb-6 flex items-center justify-between">
      <div>
        <p class="text-sm text-gray-500">Vault URI</p>
        <p class="text-sm font-mono text-gray-800 mt-0.5">{{ health?.vaultUri ?? '—' }}</p>
      </div>
      <div>
        <p class="text-sm text-gray-500">Auth Method</p>
        <p class="text-sm font-medium text-gray-800 mt-0.5">{{ health?.authMethod ?? '—' }}</p>
      </div>
      <div>
        <p class="text-sm text-gray-500">Status</p>
        <span
          v-if="health"
          :class="health.healthy ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'"
          class="inline-flex items-center px-2.5 py-0.5 rounded-full text-sm font-medium mt-0.5"
        >
          {{ health.healthy ? '✔ Connected' : '✘ Unreachable' }}
        </span>
        <span v-else class="inline-flex items-center px-2.5 py-0.5 rounded-full text-sm font-medium bg-gray-100 text-gray-500 mt-0.5">
          — Unknown
        </span>
      </div>
      <div class="text-sm text-gray-500 max-w-xs truncate">{{ health?.message }}</div>
      <button
        @click="checkHealth"
        :disabled="healthChecking"
        class="text-sm text-indigo-600 hover:text-indigo-800 font-medium disabled:opacity-50"
      >
        {{ healthChecking ? 'Checking…' : 'Refresh' }}
      </button>
    </div>

    <div class="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-6">
      <!-- Store Evidence Panel -->
      <div class="bg-white rounded-lg shadow p-6">
        <h2 class="text-lg font-semibold text-gray-900 mb-4">Store Evidence</h2>
        <form @submit.prevent="handleStore" class="space-y-4">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Entity Type</label>
            <input
              v-model="storeForm.entityType"
              type="text"
              required
              placeholder="e.g. software_release"
              class="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Entity ID</label>
            <input
              v-model="storeForm.entityId"
              type="text"
              required
              placeholder="e.g. release-v1.2.3"
              class="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Evidence Type</label>
            <input
              v-model="storeForm.evidenceType"
              type="text"
              required
              placeholder="e.g. security_scan"
              class="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">
              Evidence Data
              <span class="text-xs text-gray-400 font-normal ml-1">(JSON key-value pairs)</span>
            </label>
            <textarea
              v-model="storeForm.dataJson"
              rows="5"
              required
              placeholder='{"result": "Passed", "report_url": "s3://bucket/report.pdf"}'
              class="w-full border border-gray-300 rounded-md px-3 py-2 text-sm font-mono focus:outline-none focus:ring-2 focus:ring-indigo-500"
            ></textarea>
          </div>
          <div v-if="storeError" class="text-sm text-red-600">{{ storeError }}</div>
          <button
            type="submit"
            :disabled="storing"
            class="w-full bg-indigo-600 text-white px-4 py-2 rounded-md text-sm font-medium hover:bg-indigo-700 disabled:opacity-50"
          >
            {{ storing ? 'Storing…' : 'Store in Vault' }}
          </button>
        </form>
        <div v-if="storeResult" class="mt-4 p-4 bg-green-50 rounded-lg">
          <p class="text-sm font-medium text-green-800">✔ Evidence stored successfully</p>
          <dl class="mt-2 text-sm text-green-700 space-y-1">
            <div><dt class="inline font-medium">Path: </dt><dd class="inline font-mono">{{ storeResult.vaultPath }}</dd></div>
            <div><dt class="inline font-medium">Version: </dt><dd class="inline">{{ storeResult.version }}</dd></div>
            <div><dt class="inline font-medium">Stored At: </dt><dd class="inline">{{ formatDate(storeResult.storedAt) }}</dd></div>
          </dl>
        </div>
      </div>

      <!-- Retrieve Evidence Panel -->
      <div class="bg-white rounded-lg shadow p-6">
        <h2 class="text-lg font-semibold text-gray-900 mb-4">Retrieve Evidence</h2>
        <form @submit.prevent="handleRetrieve" class="space-y-4">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Entity Type</label>
            <input
              v-model="retrieveForm.entityType"
              type="text"
              required
              placeholder="e.g. software_release"
              class="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Entity ID</label>
            <input
              v-model="retrieveForm.entityId"
              type="text"
              required
              placeholder="e.g. release-v1.2.3"
              class="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Evidence Type</label>
            <input
              v-model="retrieveForm.evidenceType"
              type="text"
              required
              placeholder="e.g. security_scan"
              class="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
          </div>
          <div v-if="retrieveError" class="text-sm text-red-600">{{ retrieveError }}</div>
          <div class="flex gap-2">
            <button
              type="submit"
              :disabled="retrieving"
              class="flex-1 bg-indigo-600 text-white px-4 py-2 rounded-md text-sm font-medium hover:bg-indigo-700 disabled:opacity-50"
            >
              {{ retrieving ? 'Retrieving…' : 'Retrieve Metadata' }}
            </button>
            <button
              type="button"
              @click="handleDownload"
              :disabled="retrieving || !retrieveForm.entityType || !retrieveForm.entityId || !retrieveForm.evidenceType"
              class="flex-1 bg-gray-600 text-white px-4 py-2 rounded-md text-sm font-medium hover:bg-gray-700 disabled:opacity-50"
            >
              Download
            </button>
          </div>
        </form>
        <div v-if="retrieveResult" class="mt-4 p-4 bg-blue-50 rounded-lg">
          <p class="text-sm font-medium text-blue-800 mb-2">Evidence Metadata</p>
          <dl class="text-sm text-blue-700 space-y-1">
            <div><dt class="inline font-medium">Entity Type: </dt><dd class="inline">{{ retrieveResult.entityType }}</dd></div>
            <div><dt class="inline font-medium">Entity ID: </dt><dd class="inline font-mono">{{ retrieveResult.entityId }}</dd></div>
            <div><dt class="inline font-medium">Evidence Type: </dt><dd class="inline">{{ retrieveResult.evidenceType }}</dd></div>
            <div><dt class="inline font-medium">Vault Path: </dt><dd class="inline font-mono break-all">{{ retrieveResult.vaultPath }}</dd></div>
          </dl>
        </div>
      </div>
    </div>

    <!-- List Evidence Panel -->
    <div class="bg-white rounded-lg shadow p-6 mb-6">
      <h2 class="text-lg font-semibold text-gray-900 mb-4">List Evidence for Entity</h2>
      <div class="flex flex-wrap gap-4 items-end mb-4">
        <div class="flex-1 min-w-[200px]">
          <label class="block text-sm font-medium text-gray-700 mb-1">Entity Type</label>
          <input
            v-model="listForm.entityType"
            type="text"
            placeholder="e.g. software_release"
            class="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
          />
        </div>
        <div class="flex-1 min-w-[200px]">
          <label class="block text-sm font-medium text-gray-700 mb-1">Entity ID</label>
          <input
            v-model="listForm.entityId"
            type="text"
            placeholder="e.g. release-v1.2.3"
            class="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
          />
        </div>
        <button
          @click="handleList"
          :disabled="listing || !listForm.entityType || !listForm.entityId"
          class="bg-indigo-600 text-white px-4 py-2 rounded-md text-sm font-medium hover:bg-indigo-700 disabled:opacity-50"
        >
          {{ listing ? 'Listing…' : 'List Evidence' }}
        </button>
      </div>
      <div v-if="listError" class="text-sm text-red-600 mb-2">{{ listError }}</div>
      <div v-if="listResult">
        <div v-if="listResult.evidenceTypes.length === 0" class="text-gray-400 text-sm">
          No evidence found for this entity.
        </div>
        <ul v-else class="space-y-2">
          <li
            v-for="et in listResult.evidenceTypes"
            :key="et"
            class="flex items-center justify-between p-3 bg-gray-50 rounded-lg"
          >
            <span class="text-sm font-medium text-gray-800">{{ et }}</span>
            <span class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-indigo-100 text-indigo-700">
              {{ listResult.entityType }}/{{ listResult.entityId }}
            </span>
          </li>
        </ul>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import {
  storeEvidence,
  retrieveEvidence,
  listEvidence,
  downloadEvidence,
  getVaultHealth,
} from '../api/vaultEvidence'
import type { VaultEvidenceResponse, VaultEvidenceListResponse, VaultHealthResponse } from '../types'

// Health status
const health = ref<VaultHealthResponse | null>(null)
const healthChecking = ref(false)

async function checkHealth() {
  healthChecking.value = true
  try {
    const res = await getVaultHealth()
    health.value = res.data
  } catch {
    health.value = { healthy: false, vaultUri: '—', authMethod: '—', message: 'Unable to reach Vault', checkedAt: new Date().toISOString() }
  } finally {
    healthChecking.value = false
  }
}

// Store evidence
const storeForm = ref({ entityType: '', entityId: '', evidenceType: '', dataJson: '' })
const storing = ref(false)
const storeError = ref('')
const storeResult = ref<VaultEvidenceResponse | null>(null)

async function handleStore() {
  storeError.value = ''
  storeResult.value = null
  let parsed: unknown
  try {
    parsed = JSON.parse(storeForm.value.dataJson)
  } catch {
    storeError.value = 'Invalid JSON — please provide a valid key-value object.'
    return
  }
  if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
    storeError.value = 'Evidence data must be a JSON object (e.g. {"result": "Passed"}).'
    return
  }
  // Coerce all values to strings so the backend Map<String, String> contract is satisfied.
  // Only string, number, and boolean primitives are accepted; complex objects/arrays are rejected.
  const entries = Object.entries(parsed as Record<string, unknown>)
  const invalidKeys = entries
    .filter(([, v]) => v !== null && v !== undefined && typeof v === 'object')
    .map(([k]) => k)
  if (invalidKeys.length > 0) {
    storeError.value = `Evidence values must be strings, numbers, or booleans. Found nested object/array for key(s): ${invalidKeys.join(', ')}.`
    return
  }
  const data: Record<string, string> = Object.fromEntries(
    entries.map(([k, v]) => [k, String(v)])
  )
  storing.value = true
  try {
    const res = await storeEvidence(storeForm.value.entityType, storeForm.value.entityId, {
      evidenceType: storeForm.value.evidenceType,
      data,
    })
    storeResult.value = res.data
  } catch {
    storeError.value = 'Failed to store evidence. Is Vault enabled and reachable?'
  } finally {
    storing.value = false
  }
}

// Retrieve evidence
const retrieveForm = ref({ entityType: '', entityId: '', evidenceType: '' })
const retrieving = ref(false)
const retrieveError = ref('')
const retrieveResult = ref<VaultEvidenceResponse | null>(null)

async function handleRetrieve() {
  retrieveError.value = ''
  retrieveResult.value = null
  retrieving.value = true
  try {
    const res = await retrieveEvidence(
      retrieveForm.value.entityType,
      retrieveForm.value.entityId,
      retrieveForm.value.evidenceType,
    )
    retrieveResult.value = res.data
  } catch {
    retrieveError.value = 'No evidence found for those parameters, or an error occurred.'
  } finally {
    retrieving.value = false
  }
}

async function handleDownload() {
  retrieveError.value = ''
  retrieving.value = true
  try {
    const res = await downloadEvidence(
      retrieveForm.value.entityType,
      retrieveForm.value.entityId,
      retrieveForm.value.evidenceType,
    )
    const blob = new Blob([JSON.stringify(res.data, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `evidence-${retrieveForm.value.entityType}-${retrieveForm.value.entityId}-${retrieveForm.value.evidenceType}.json`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    setTimeout(() => URL.revokeObjectURL(url), 100)
  } catch {
    retrieveError.value = 'Failed to download evidence.'
  } finally {
    retrieving.value = false
  }
}

// List evidence
const listForm = ref({ entityType: '', entityId: '' })
const listing = ref(false)
const listError = ref('')
const listResult = ref<VaultEvidenceListResponse | null>(null)

async function handleList() {
  listError.value = ''
  listResult.value = null
  listing.value = true
  try {
    const res = await listEvidence(listForm.value.entityType, listForm.value.entityId)
    listResult.value = res.data
  } catch {
    listError.value = 'Failed to list evidence. Is Vault enabled and reachable?'
  } finally {
    listing.value = false
  }
}

function formatDate(dateStr: string): string {
  return new Date(dateStr).toLocaleString()
}

onMounted(() => {
  checkHealth()
})
</script>
