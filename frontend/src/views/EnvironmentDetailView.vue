<template>
  <div>
    <div class="mb-6">
      <RouterLink to="/environments" class="text-indigo-600 hover:text-indigo-900 text-sm font-medium">
        ← Back to Environments
      </RouterLink>
    </div>

    <div v-if="loading" class="text-center text-gray-500 py-12">Loading...</div>
    <div v-else-if="!environment" class="text-center text-gray-500 py-12">Environment not found.</div>
    <template v-else>
      <!-- Environment header -->
      <div class="bg-white shadow rounded-lg p-6 mb-6">
        <div class="flex items-start justify-between">
          <div>
            <h1 class="text-2xl font-bold text-gray-900">{{ environment.name }}</h1>
            <p v-if="environment.description" class="mt-1 text-gray-500">{{ environment.description }}</p>
          </div>
          <span :class="typeBadgeClass(environment.type)" class="inline-flex items-center px-3 py-1 rounded-full text-sm font-medium">
            {{ environment.type }}
          </span>
        </div>
        <div class="mt-4 grid grid-cols-2 gap-4 text-sm text-gray-500">
          <div>Created: {{ new Date(environment.createdAt).toLocaleString() }}</div>
          <div>Updated: {{ new Date(environment.updatedAt).toLocaleString() }}</div>
        </div>
      </div>

      <!-- Current state: latest snapshot -->
      <div class="mb-6">
        <h2 class="text-lg font-semibold text-gray-900 mb-3">Current State</h2>
        <div v-if="!latestSnapshot" class="bg-white shadow rounded-lg p-6 text-center text-gray-500">
          No snapshots recorded yet.
        </div>
        <div v-else class="bg-white shadow rounded-lg p-6">
          <div class="flex items-center justify-between mb-4">
            <div class="text-sm text-gray-500">
              Snapshot #{{ latestSnapshot.snapshotIndex }} &bull;
              Recorded {{ new Date(latestSnapshot.recordedAt).toLocaleString() }} by
              <span class="font-medium text-gray-700">{{ latestSnapshot.recordedBy }}</span>
            </div>
          </div>
          <div v-if="latestSnapshot.artifacts.length === 0" class="text-sm text-gray-500">No artifacts in this snapshot.</div>
          <table v-else class="min-w-full divide-y divide-gray-200">
            <thead class="bg-gray-50">
              <tr>
                <th class="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Name</th>
                <th class="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Tag</th>
                <th class="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">SHA256</th>
                <th class="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Instances</th>
              </tr>
            </thead>
            <tbody class="bg-white divide-y divide-gray-200">
              <tr v-for="artifact in latestSnapshot.artifacts" :key="artifact.artifactSha256" class="hover:bg-gray-50">
                <td class="px-4 py-2 text-sm font-medium text-gray-900">{{ artifact.artifactName }}</td>
                <td class="px-4 py-2 text-sm text-gray-500">{{ artifact.artifactTag }}</td>
                <td class="px-4 py-2 text-sm text-gray-500 font-mono text-xs">{{ artifact.artifactSha256.slice(0, 20) }}…</td>
                <td class="px-4 py-2 text-sm text-gray-500">{{ artifact.instanceCount }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- Snapshot timeline -->
      <div>
        <div class="flex items-center justify-between mb-3">
          <h2 class="text-lg font-semibold text-gray-900">Snapshot History</h2>
          <button
            class="bg-indigo-600 text-white px-3 py-1.5 rounded-md text-sm font-medium hover:bg-indigo-700"
            @click="showRecordModal = true"
          >
            + Record Snapshot
          </button>
        </div>

        <div v-if="snapshots.length === 0" class="bg-white shadow rounded-lg p-6 text-center text-gray-500">
          No snapshots yet.
        </div>
        <div v-else class="space-y-3">
          <div
            v-for="snap in [...snapshots].reverse()"
            :key="snap.id"
            class="bg-white shadow rounded-lg p-4 cursor-pointer hover:bg-gray-50"
            @click="toggleSnapshot(snap.snapshotIndex)"
          >
            <div class="flex items-center justify-between">
              <div class="text-sm">
                <span class="font-semibold text-gray-900">#{{ snap.snapshotIndex }}</span>
                <span class="ml-3 text-gray-500">{{ new Date(snap.recordedAt).toLocaleString() }}</span>
                <span class="ml-3 text-gray-400">by {{ snap.recordedBy }}</span>
              </div>
              <span class="text-xs bg-indigo-100 text-indigo-800 px-2 py-0.5 rounded">
                {{ snap.artifacts.length }} artifact{{ snap.artifacts.length !== 1 ? 's' : '' }}
              </span>
            </div>
            <div v-if="expandedIndex === snap.snapshotIndex && snap.artifacts.length > 0" class="mt-3">
              <table class="min-w-full divide-y divide-gray-200">
                <thead class="bg-gray-50">
                  <tr>
                    <th class="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Name</th>
                    <th class="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Tag</th>
                    <th class="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">SHA256</th>
                    <th class="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Instances</th>
                  </tr>
                </thead>
                <tbody class="bg-white divide-y divide-gray-200">
                  <tr v-for="artifact in snap.artifacts" :key="artifact.artifactSha256" class="hover:bg-gray-50">
                    <td class="px-4 py-2 text-sm font-medium text-gray-900">{{ artifact.artifactName }}</td>
                    <td class="px-4 py-2 text-sm text-gray-500">{{ artifact.artifactTag }}</td>
                    <td class="px-4 py-2 text-sm text-gray-500 font-mono text-xs">{{ artifact.artifactSha256.slice(0, 20) }}…</td>
                    <td class="px-4 py-2 text-sm text-gray-500">{{ artifact.instanceCount }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
    </template>

    <!-- Record Snapshot Modal -->
    <div v-if="showRecordModal" class="fixed inset-0 bg-black bg-opacity-40 flex items-center justify-center z-50">
      <div class="bg-white rounded-lg shadow-xl p-6 w-full max-w-lg">
        <h2 class="text-lg font-bold text-gray-900 mb-4">Record Snapshot</h2>
        <form @submit.prevent="submitSnapshot">
          <div class="mb-4">
            <label class="block text-sm font-medium text-gray-700 mb-1">Recorded By</label>
            <input
              v-model="snapshotForm.recordedBy"
              type="text"
              required
              placeholder="e.g. ci-bot or username"
              class="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
          </div>
          <div class="mb-4">
            <label class="block text-sm font-medium text-gray-700 mb-2">Artifacts</label>
            <div v-for="(artifact, idx) in snapshotForm.artifacts" :key="idx" class="mb-3 border border-gray-200 rounded-md p-3 relative">
              <button type="button" class="absolute top-2 right-2 text-gray-400 hover:text-red-500 text-xs" @click="removeArtifact(idx)">✕</button>
              <div class="grid grid-cols-2 gap-2">
                <div>
                  <label class="block text-xs text-gray-500 mb-1">Name</label>
                  <input v-model="artifact.artifactName" type="text" required placeholder="my-app"
                    class="w-full border border-gray-300 rounded px-2 py-1 text-sm" />
                </div>
                <div>
                  <label class="block text-xs text-gray-500 mb-1">Tag</label>
                  <input v-model="artifact.artifactTag" type="text" required placeholder="v1.0.0"
                    class="w-full border border-gray-300 rounded px-2 py-1 text-sm" />
                </div>
                <div class="col-span-2">
                  <label class="block text-xs text-gray-500 mb-1">SHA256</label>
                  <input v-model="artifact.artifactSha256" type="text" required placeholder="sha256:..."
                    class="w-full border border-gray-300 rounded px-2 py-1 text-sm font-mono" />
                </div>
                <div>
                  <label class="block text-xs text-gray-500 mb-1">Instances</label>
                  <input v-model.number="artifact.instanceCount" type="number" min="1" required
                    class="w-full border border-gray-300 rounded px-2 py-1 text-sm" />
                </div>
              </div>
            </div>
            <button type="button" class="text-indigo-600 hover:text-indigo-900 text-sm font-medium" @click="addArtifact">
              + Add Artifact
            </button>
          </div>
          <div v-if="snapshotFormError" class="mb-4 text-sm text-red-600">{{ snapshotFormError }}</div>
          <div class="flex justify-end gap-3">
            <button type="button" class="px-4 py-2 text-sm text-gray-700 border border-gray-300 rounded-md hover:bg-gray-50" @click="closeRecordModal">Cancel</button>
            <button type="submit" :disabled="recordingSnapshot" class="px-4 py-2 text-sm text-white bg-indigo-600 rounded-md hover:bg-indigo-700 disabled:opacity-50">
              {{ recordingSnapshot ? 'Recording...' : 'Record' }}
            </button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getEnvironment, listSnapshots, getLatestSnapshot, recordSnapshot } from '../api/environments'
import type { Environment, EnvironmentSnapshot } from '../types'

const route = useRoute()
const id = route.params.id as string

const environment = ref<Environment | null>(null)
const snapshots = ref<EnvironmentSnapshot[]>([])
const latestSnapshot = ref<EnvironmentSnapshot | null>(null)
const loading = ref(true)
const expandedIndex = ref<number | null>(null)

const showRecordModal = ref(false)
const recordingSnapshot = ref(false)
const snapshotFormError = ref('')
const snapshotForm = ref({
  recordedBy: '',
  artifacts: [] as Array<{ artifactSha256: string; artifactName: string; artifactTag: string; instanceCount: number }>
})

function typeBadgeClass(type: string) {
  const map: Record<string, string> = {
    K8S: 'bg-blue-100 text-blue-800',
    S3: 'bg-yellow-100 text-yellow-800',
    LAMBDA: 'bg-purple-100 text-purple-800',
    GENERIC: 'bg-gray-100 text-gray-800'
  }
  return map[type] ?? 'bg-gray-100 text-gray-800'
}

function toggleSnapshot(index: number) {
  expandedIndex.value = expandedIndex.value === index ? null : index
}

function addArtifact() {
  snapshotForm.value.artifacts.push({ artifactSha256: '', artifactName: '', artifactTag: '', instanceCount: 1 })
}

function removeArtifact(idx: number) {
  snapshotForm.value.artifacts.splice(idx, 1)
}

function closeRecordModal() {
  showRecordModal.value = false
  snapshotForm.value = { recordedBy: '', artifacts: [] }
  snapshotFormError.value = ''
}

async function submitSnapshot() {
  recordingSnapshot.value = true
  snapshotFormError.value = ''
  try {
    await recordSnapshot(id, {
      recordedBy: snapshotForm.value.recordedBy,
      artifacts: snapshotForm.value.artifacts
    })
    const [snapsRes, latestRes] = await Promise.all([listSnapshots(id), getLatestSnapshot(id)])
    snapshots.value = snapsRes.data
    latestSnapshot.value = latestRes.data
    closeRecordModal()
  } catch {
    snapshotFormError.value = 'Failed to record snapshot. Please try again.'
  } finally {
    recordingSnapshot.value = false
  }
}

onMounted(async () => {
  try {
    const [envRes, snapsRes] = await Promise.all([getEnvironment(id), listSnapshots(id)])
    environment.value = envRes.data
    snapshots.value = snapsRes.data
    if (snapsRes.data.length > 0) {
      const latestRes = await getLatestSnapshot(id)
      latestSnapshot.value = latestRes.data
    }
  } catch {
    // silently fail
  } finally {
    loading.value = false
  }
})
</script>
