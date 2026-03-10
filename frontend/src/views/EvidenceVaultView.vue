<template>
  <div>
    <h1 class="text-2xl font-bold text-gray-900 mb-6">Evidence Vault</h1>
    <p class="text-gray-500 mb-6">Look up the full chain of custody for an artifact by its SHA256 digest.</p>

    <div class="bg-white rounded-lg shadow p-6 max-w-2xl mb-6">
      <form @submit.prevent="lookup">
        <div class="flex gap-3">
          <input
            v-model="sha256"
            type="text"
            required
            placeholder="sha256:abc123..."
            class="flex-1 border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
          />
          <button
            type="submit"
            :disabled="loading"
            class="bg-indigo-600 text-white px-4 py-2 rounded-md text-sm font-medium hover:bg-indigo-700 disabled:opacity-50 whitespace-nowrap"
          >{{ loading ? 'Looking up...' : 'Look Up' }}</button>
        </div>
      </form>
      <div v-if="error" class="mt-3 text-sm text-red-600">{{ error }}</div>
    </div>

    <div v-if="chain" class="space-y-6">
      <!-- Artifact Info -->
      <div class="bg-white rounded-lg shadow p-6">
        <h2 class="text-lg font-medium text-gray-900 mb-4">Artifact</h2>
        <div class="grid grid-cols-1 md:grid-cols-2 gap-3 text-sm">
          <div><span class="font-medium text-gray-500">Image Name:</span> {{ chain.artifact?.imageName }}</div>
          <div><span class="font-medium text-gray-500">Tag:</span> {{ chain.artifact?.imageTag }}</div>
          <div class="md:col-span-2"><span class="font-medium text-gray-500">SHA256:</span> <span class="font-mono">{{ chain.artifact?.sha256Digest }}</span></div>
          <div v-if="chain.artifact?.registry"><span class="font-medium text-gray-500">Registry:</span> {{ chain.artifact?.registry }}</div>
          <div><span class="font-medium text-gray-500">Reported By:</span> {{ chain.artifact?.reportedBy }}</div>
        </div>
      </div>

      <!-- Trail Info -->
      <div class="bg-white rounded-lg shadow p-6">
        <h2 class="text-lg font-medium text-gray-900 mb-4">Trail</h2>
        <div class="grid grid-cols-1 md:grid-cols-2 gap-3 text-sm">
          <div><span class="font-medium text-gray-500">Commit SHA:</span> <span class="font-mono">{{ chain.trail?.gitCommitSha }}</span></div>
          <div><span class="font-medium text-gray-500">Branch:</span> {{ chain.trail?.gitBranch }}</div>
          <div><span class="font-medium text-gray-500">Author:</span> {{ chain.trail?.gitAuthor }}</div>
          <div>
            <span class="font-medium text-gray-500">Status:</span>
            <StatusBadge v-if="chain.trail?.status" :status="chain.trail.status" class="ml-2" />
          </div>
        </div>
      </div>

      <!-- Attestations -->
      <div class="bg-white rounded-lg shadow">
        <div class="px-6 py-4 border-b border-gray-200">
          <h2 class="text-lg font-medium text-gray-900">Attestations</h2>
        </div>
        <div v-if="!chain.attestations || chain.attestations.length === 0" class="p-6 text-center text-gray-500">No attestations.</div>
        <table v-else class="min-w-full divide-y divide-gray-200">
          <thead class="bg-gray-50">
            <tr>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Type</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Status</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Evidence File</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Created At</th>
            </tr>
          </thead>
          <tbody class="bg-white divide-y divide-gray-200">
            <tr v-for="att in chain.attestations" :key="att.id">
              <td class="px-6 py-4 text-sm font-medium text-gray-900">{{ att.type }}</td>
              <td class="px-6 py-4"><StatusBadge :status="att.status" /></td>
              <td class="px-6 py-4 text-sm text-gray-500">{{ att.evidenceFileName ?? '—' }}</td>
              <td class="px-6 py-4 text-sm text-gray-500">{{ formatDate(att.createdAt) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import StatusBadge from '../components/StatusBadge.vue'
import { getChainOfCustody } from '../api/assert'
import type { Attestation, Artifact, Trail } from '../types'

interface ChainOfCustody {
  artifact?: Artifact
  trail?: Trail
  attestations?: Attestation[]
}

const sha256 = ref('')
const loading = ref(false)
const error = ref('')
const chain = ref<ChainOfCustody | null>(null)

function formatDate(dateStr: string): string {
  return new Date(dateStr).toLocaleString()
}

async function lookup() {
  loading.value = true
  error.value = ''
  chain.value = null
  try {
    const res = await getChainOfCustody(sha256.value)
    chain.value = res.data as ChainOfCustody
  } catch {
    error.value = 'No chain of custody found for that digest, or an error occurred.'
  } finally {
    loading.value = false
  }
}
</script>
