<template>
  <div>
    <div class="flex items-center gap-3 mb-6">
      <RouterLink to="/logical-environments" class="text-indigo-600 hover:text-indigo-900 text-sm font-medium">
        ← Logical Environments
      </RouterLink>
    </div>

    <div v-if="loading" class="text-center text-gray-500 py-12">Loading...</div>
    <div v-else-if="!logicalEnv" class="text-center text-gray-500 py-12">Logical environment not found.</div>
    <template v-else>
      <!-- Header -->
      <div class="flex items-start justify-between mb-6">
        <div>
          <h1 class="text-2xl font-bold text-gray-900">{{ logicalEnv.name }}</h1>
          <p v-if="logicalEnv.description" class="text-gray-500 mt-1">{{ logicalEnv.description }}</p>
        </div>
        <button
          class="bg-indigo-600 text-white px-4 py-2 rounded-md text-sm font-medium hover:bg-indigo-700"
          @click="showAddMemberModal = true"
        >
          + Add Physical Environment
        </button>
      </div>

      <!-- Members -->
      <div class="bg-white shadow rounded-lg overflow-hidden mb-6">
        <div class="px-6 py-4 border-b border-gray-200">
          <h2 class="text-lg font-semibold text-gray-900">Member Physical Environments</h2>
        </div>
        <div v-if="logicalEnv.members.length === 0" class="px-6 py-8 text-center text-gray-500">
          No physical environments added yet.
        </div>
        <table v-else class="min-w-full divide-y divide-gray-200">
          <thead class="bg-gray-50">
            <tr>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Name</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Type</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Added</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Actions</th>
            </tr>
          </thead>
          <tbody class="bg-white divide-y divide-gray-200">
            <tr v-for="member in logicalEnv.members" :key="member.physicalEnvId" class="hover:bg-gray-50">
              <td class="px-6 py-4 text-sm font-medium text-gray-900">
                <RouterLink :to="`/environments/${member.physicalEnvId}`" class="text-indigo-600 hover:text-indigo-900">
                  {{ member.physicalEnvName }}
                </RouterLink>
              </td>
              <td class="px-6 py-4">
                <span :class="typeBadgeClass(member.physicalEnvType)" class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium">
                  {{ member.physicalEnvType }}
                </span>
              </td>
              <td class="px-6 py-4 text-sm text-gray-500">{{ new Date(member.addedAt).toLocaleDateString() }}</td>
              <td class="px-6 py-4 text-sm">
                <button
                  class="text-red-600 hover:text-red-900 font-medium"
                  @click="removeMember(member.physicalEnvId)"
                >Remove</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Merged Snapshot -->
      <div class="bg-white shadow rounded-lg overflow-hidden">
        <div class="px-6 py-4 border-b border-gray-200 flex items-center justify-between">
          <h2 class="text-lg font-semibold text-gray-900">Latest Merged Snapshot</h2>
          <button
            class="text-sm text-indigo-600 hover:text-indigo-900 font-medium"
            @click="loadMergedSnapshot"
          >Refresh</button>
        </div>

        <div v-if="snapshotLoading" class="px-6 py-8 text-center text-gray-500">Loading snapshot...</div>
        <div v-else-if="snapshotError" class="px-6 py-8 text-center text-red-500">{{ snapshotError }}</div>
        <div v-else-if="mergedSnapshot">
          <!-- Compliance Status -->
          <div class="px-6 py-4 border-b border-gray-100 flex items-center gap-3">
            <span class="text-sm font-medium text-gray-700">Overall Compliance:</span>
            <span
              :class="mergedSnapshot.complianceStatus === 'COMPLIANT'
                ? 'bg-green-100 text-green-800'
                : 'bg-red-100 text-red-800'"
              class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium"
            >
              {{ mergedSnapshot.complianceStatus }}
            </span>
          </div>

          <!-- Member Snapshot Summary -->
          <div class="px-6 py-4 border-b border-gray-100">
            <h3 class="text-sm font-semibold text-gray-700 mb-3">Member Snapshot Summary</h3>
            <div class="space-y-2">
              <div
                v-for="summary in mergedSnapshot.memberSnapshots"
                :key="summary.physicalEnvId"
                class="flex items-center justify-between bg-gray-50 rounded-md px-4 py-2"
              >
                <span class="text-sm font-medium text-gray-800">{{ summary.physicalEnvName }}</span>
                <div class="flex items-center gap-4 text-sm text-gray-500">
                  <span v-if="summary.snapshotIndex !== null">
                    Snapshot #{{ summary.snapshotIndex }} &bull; {{ summary.artifactCount }} artifact(s)
                  </span>
                  <span v-else class="text-red-500">No snapshot</span>
                  <span v-if="summary.recordedAt">{{ new Date(summary.recordedAt).toLocaleString() }}</span>
                </div>
              </div>
            </div>
          </div>

          <!-- Merged Artifacts -->
          <div class="px-6 py-4">
            <h3 class="text-sm font-semibold text-gray-700 mb-3">Merged Artifacts ({{ mergedSnapshot.mergedArtifacts.length }})</h3>
            <div v-if="mergedSnapshot.mergedArtifacts.length === 0" class="text-sm text-gray-500">
              No artifacts in any member environment.
            </div>
            <table v-else class="min-w-full text-sm">
              <thead>
                <tr class="text-left text-xs font-medium text-gray-500 uppercase tracking-wider border-b border-gray-200">
                  <th class="pb-2 pr-4">Artifact</th>
                  <th class="pb-2 pr-4">Tag</th>
                  <th class="pb-2 pr-4">SHA256</th>
                  <th class="pb-2 pr-4">Instances</th>
                  <th class="pb-2">Source Environment</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-gray-100">
                <tr v-for="artifact in mergedSnapshot.mergedArtifacts" :key="`${artifact.artifactSha256}-${artifact.physicalEnvId}`" class="py-2">
                  <td class="py-2 pr-4 font-medium text-gray-900">{{ artifact.artifactName }}</td>
                  <td class="py-2 pr-4 text-gray-600">{{ artifact.artifactTag }}</td>
                  <td class="py-2 pr-4 font-mono text-xs text-gray-500 max-w-xs truncate">{{ artifact.artifactSha256 }}</td>
                  <td class="py-2 pr-4 text-gray-600">{{ artifact.instanceCount }}</td>
                  <td class="py-2">
                    <RouterLink :to="`/environments/${artifact.physicalEnvId}`" class="text-indigo-600 hover:text-indigo-900">
                      {{ artifact.physicalEnvName }}
                    </RouterLink>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
        <div v-else class="px-6 py-8 text-center text-gray-500">
          No snapshot data available. Add members to see the merged snapshot.
        </div>
      </div>
    </template>

    <!-- Add Member Modal -->
    <div v-if="showAddMemberModal" class="fixed inset-0 bg-black bg-opacity-40 flex items-center justify-center z-50">
      <div class="bg-white rounded-lg shadow-xl p-6 w-full max-w-md">
        <h2 class="text-lg font-bold text-gray-900 mb-4">Add Physical Environment</h2>
        <div class="mb-4">
          <label class="block text-sm font-medium text-gray-700 mb-1">Select Environment</label>
          <select
            v-model="selectedPhysicalEnvId"
            class="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
          >
            <option value="">-- Select --</option>
            <option
              v-for="env in availablePhysicalEnvs"
              :key="env.id"
              :value="env.id"
            >{{ env.name }} ({{ env.type }})</option>
          </select>
        </div>
        <div v-if="addMemberError" class="mb-4 text-sm text-red-600">{{ addMemberError }}</div>
        <div class="flex justify-end gap-3">
          <button
            type="button"
            class="px-4 py-2 text-sm text-gray-700 border border-gray-300 rounded-md hover:bg-gray-50"
            @click="closeAddMemberModal"
          >Cancel</button>
          <button
            :disabled="!selectedPhysicalEnvId || addingMember"
            class="px-4 py-2 text-sm text-white bg-indigo-600 rounded-md hover:bg-indigo-700 disabled:opacity-50"
            @click="doAddMember"
          >{{ addingMember ? 'Adding...' : 'Add' }}</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { logicalEnvironmentsApi } from '../api/logicalEnvironments'
import { getEnvironments } from '../api/environments'
import { useEnvironmentTypeBadge } from '../composables/useEnvironmentTypeBadge'
import type { LogicalEnvironment, MergedSnapshotResponse, Environment } from '../types'

const route = useRoute()
const id = route.params.id as string

const logicalEnv = ref<LogicalEnvironment | null>(null)
const loading = ref(true)
const mergedSnapshot = ref<MergedSnapshotResponse | null>(null)
const snapshotLoading = ref(false)
const snapshotError = ref('')

const showAddMemberModal = ref(false)
const selectedPhysicalEnvId = ref('')
const addingMember = ref(false)
const addMemberError = ref('')
const allPhysicalEnvs = ref<Environment[]>([])

const { typeBadgeClass } = useEnvironmentTypeBadge()

const availablePhysicalEnvs = computed(() => {
  if (!logicalEnv.value) return allPhysicalEnvs.value
  const memberIds = new Set(logicalEnv.value.members.map(m => m.physicalEnvId))
  return allPhysicalEnvs.value.filter(e => !memberIds.has(e.id))
})

async function loadMergedSnapshot() {
  snapshotLoading.value = true
  snapshotError.value = ''
  try {
    const res = await logicalEnvironmentsApi.getMergedSnapshot(id)
    mergedSnapshot.value = res.data
  } catch (err) {
    console.error('Failed to load merged snapshot', err)
    snapshotError.value = 'Failed to load merged snapshot.'
  } finally {
    snapshotLoading.value = false
  }
}

async function removeMember(physicalEnvId: string) {
  if (!confirm('Remove this environment from the logical group?')) return
  try {
    await logicalEnvironmentsApi.removeMember(id, physicalEnvId)
    const res = await logicalEnvironmentsApi.get(id)
    logicalEnv.value = res.data
    await loadMergedSnapshot()
  } catch (err) {
    console.error('Failed to remove member', err)
  }
}

function closeAddMemberModal() {
  showAddMemberModal.value = false
  selectedPhysicalEnvId.value = ''
  addMemberError.value = ''
}

async function doAddMember() {
  if (!selectedPhysicalEnvId.value) return
  addingMember.value = true
  addMemberError.value = ''
  try {
    const res = await logicalEnvironmentsApi.addMember(id, selectedPhysicalEnvId.value)
    logicalEnv.value = res.data
    closeAddMemberModal()
    await loadMergedSnapshot()
  } catch (err) {
    console.error('Failed to add member', err)
    addMemberError.value = 'Failed to add environment. Please try again.'
  } finally {
    addingMember.value = false
  }
}

onMounted(async () => {
  try {
    const [envRes, physRes] = await Promise.all([
      logicalEnvironmentsApi.get(id),
      getEnvironments()
    ])
    logicalEnv.value = envRes.data
    allPhysicalEnvs.value = physRes.data
    await loadMergedSnapshot()
  } catch (err) {
    console.error('Failed to load logical environment', err)
  } finally {
    loading.value = false
  }
})
</script>
