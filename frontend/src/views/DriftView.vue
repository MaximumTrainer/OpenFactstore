<template>
  <div>
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-2xl font-bold text-gray-900">Environment Drift Detection</h1>
        <p class="mt-1 text-sm text-gray-500">Monitor environments for artifact drift against the allowlist.</p>
      </div>
    </div>

    <div v-if="loading" class="text-center text-gray-500 py-12">Loading...</div>
    <div v-else-if="loadError" class="text-center text-red-600 py-12">{{ loadError }}</div>
    <div v-else-if="environments.length === 0" class="text-center text-gray-500 py-12">No environments found.</div>
    <div v-else class="space-y-4">
      <div
        v-for="env in environments"
        :key="env.id"
        class="bg-white rounded-lg shadow overflow-hidden"
      >
        <!-- Card Header -->
        <div
          class="px-6 py-4 flex items-center justify-between cursor-pointer hover:bg-gray-50"
          @click="toggleExpand(env.id)"
        >
          <div class="flex items-center gap-4">
            <div>
              <p class="text-sm font-semibold text-gray-900">{{ env.name }}</p>
              <p class="text-xs text-gray-500">{{ env.description }}</p>
            </div>
            <span
              v-if="envData[env.id] && !envData[env.id].loading"
              :class="hasDrift(env.id) ? 'bg-red-100 text-red-800' : 'bg-green-100 text-green-800'"
              class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium"
            >
              {{ hasDrift(env.id) ? 'DRIFT DETECTED' : 'COMPLIANT' }}
            </span>
            <span v-else class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-gray-100 text-gray-600">
              Checking...
            </span>
          </div>
          <div class="flex items-center gap-3">
            <button
              :disabled="envData[env.id]?.refreshing"
              class="bg-indigo-600 text-white px-3 py-1.5 rounded text-xs font-medium hover:bg-indigo-700 disabled:opacity-50"
              @click.stop="refreshSnapshot(env.id)"
            >{{ envData[env.id]?.refreshing ? 'Refreshing...' : 'Refresh' }}</button>
            <svg
              class="w-4 h-4 text-gray-400 transition-transform"
              :class="expandedEnv === env.id ? 'rotate-180' : ''"
              fill="none" stroke="currentColor" viewBox="0 0 24 24"
            >
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" />
            </svg>
          </div>
        </div>

        <!-- Expanded Detail -->
        <div v-if="expandedEnv === env.id" class="border-t border-gray-100 px-6 py-4">
          <div v-if="envData[env.id]?.loading" class="text-center text-gray-500 py-4">Loading details...</div>
          <div v-else class="grid grid-cols-1 md:grid-cols-2 gap-6">
            <!-- Allowlist -->
            <div>
              <h3 class="text-sm font-semibold text-gray-700 mb-3">
                Allowed Artifacts ({{ envData[env.id]?.allowlist?.length ?? 0 }})
              </h3>
              <div v-if="!envData[env.id]?.allowlist?.length" class="text-sm text-gray-400">No allowlist entries.</div>
              <ul v-else class="space-y-1">
                <li
                  v-for="a in envData[env.id]?.allowlist"
                  :key="a.sha256"
                  class="text-xs font-mono text-gray-600 bg-gray-50 rounded px-2 py-1 truncate"
                >{{ a.sha256 }}</li>
              </ul>
            </div>
            <!-- Latest Snapshot -->
            <div>
              <h3 class="text-sm font-semibold text-gray-700 mb-3">
                Latest Snapshot Artifacts ({{ latestSnapshotArtifacts(env.id).length }})
              </h3>
              <div v-if="!latestSnapshotArtifacts(env.id).length" class="text-sm text-gray-400">No snapshot recorded.</div>
              <ul v-else class="space-y-1">
                <li
                  v-for="a in latestSnapshotArtifacts(env.id)"
                  :key="a.artifactSha256"
                  :class="isArtifactDrifted(env.id, a.artifactSha256) ? 'bg-red-50 text-red-700' : 'bg-gray-50 text-gray-600'"
                  class="text-xs font-mono rounded px-2 py-1"
                >
                  <span>{{ a.artifactName }}:{{ a.artifactTag }}</span>
                  <span v-if="isArtifactDrifted(env.id, a.artifactSha256)" class="ml-2 text-red-500 font-semibold">(not in allowlist)</span>
                </li>
              </ul>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getEnvironments, listSnapshots, getEnvironmentAllowlist, recordSnapshot } from '../api/environments'
import type { Environment, EnvironmentSnapshot } from '../types'
import type { AllowedArtifact } from '../api/environments'

interface EnvData {
  loading: boolean
  refreshing: boolean
  snapshots: EnvironmentSnapshot[]
  allowlist: AllowedArtifact[]
}

const environments = ref<Environment[]>([])
const loading = ref(true)
const loadError = ref('')
const envData = ref<Record<string, EnvData>>({})
const expandedEnv = ref<string | null>(null)

function hasDrift(envId: string): boolean {
  const data = envData.value[envId]
  if (!data) return false
  if (data.snapshots.length === 0) return true
  const latest = data.snapshots[data.snapshots.length - 1]
  const allowedShas = new Set(data.allowlist.map((a) => a.sha256))
  return latest.artifacts.some((a) => !allowedShas.has(a.artifactSha256))
}

function latestSnapshotArtifacts(envId: string) {
  const data = envData.value[envId]
  if (!data || !data.snapshots.length) return []
  return data.snapshots[data.snapshots.length - 1].artifacts
}

function isArtifactDrifted(envId: string, sha256: string): boolean {
  const data = envData.value[envId]
  if (!data) return false
  const allowedShas = new Set(data.allowlist.map((a) => a.sha256))
  return !allowedShas.has(sha256)
}

async function loadEnvData(envId: string) {
  envData.value[envId] = {
    ...envData.value[envId],
    loading: true,
    snapshots: envData.value[envId]?.snapshots ?? [],
    allowlist: envData.value[envId]?.allowlist ?? [],
    refreshing: envData.value[envId]?.refreshing ?? false
  }
  try {
    const [snapshotsRes, allowlistRes] = await Promise.all([
      listSnapshots(envId),
      getEnvironmentAllowlist(envId)
    ])
    envData.value[envId].snapshots = snapshotsRes.data
    envData.value[envId].allowlist = allowlistRes.data
  } catch {
    // silently ignore; show empty state
  } finally {
    envData.value[envId].loading = false
  }
}

async function toggleExpand(envId: string) {
  if (expandedEnv.value === envId) {
    expandedEnv.value = null
    return
  }
  expandedEnv.value = envId
  if (!envData.value[envId] || envData.value[envId].snapshots.length === 0) {
    await loadEnvData(envId)
  }
}

async function refreshSnapshot(envId: string) {
  if (!envData.value[envId]) {
    envData.value[envId] = { loading: false, refreshing: true, snapshots: [], allowlist: [] }
  } else {
    envData.value[envId].refreshing = true
  }
  try {
    await recordSnapshot(envId, { recordedBy: 'dashboard', artifacts: [] })
    await loadEnvData(envId)
  } catch {
    // silently ignore
  } finally {
    if (envData.value[envId]) {
      envData.value[envId].refreshing = false
    }
  }
}

onMounted(async () => {
  try {
    const res = await getEnvironments()
    environments.value = res.data
    for (const env of environments.value) {
      envData.value[env.id] = { loading: true, refreshing: false, snapshots: [], allowlist: [] }
    }
  } catch {
    loadError.value = 'Failed to load environments.'
  } finally {
    loading.value = false
  }
  // Load drift data for all environments in background after page renders
  await Promise.all(environments.value.map((env) => loadEnvData(env.id)))
})
</script>
