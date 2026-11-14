<template>
  <div>
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-2xl font-bold text-gray-900">Compliance Posture</h1>
        <p class="mt-1 text-sm text-gray-500">Overview of compliance across all flows and environments.</p>
      </div>
    </div>

    <div v-if="loading" class="text-center text-gray-500 py-12">Loading...</div>
    <div v-else-if="loadError" class="text-center text-red-600 py-12">{{ loadError }}</div>
    <template v-else>
      <!-- Summary cards -->
      <div class="grid grid-cols-2 sm:grid-cols-4 gap-4 mb-8">
        <div class="bg-white shadow rounded-lg p-5 text-center">
          <div class="text-3xl font-bold text-gray-900">{{ totalFlows }}</div>
          <div class="text-sm text-gray-500 mt-1">Total Flows</div>
        </div>
        <div class="bg-white shadow rounded-lg p-5 text-center">
          <div class="text-3xl font-bold text-green-600">{{ compliantPct }}%</div>
          <div class="text-sm text-gray-500 mt-1">Compliant Trails</div>
        </div>
        <div class="bg-white shadow rounded-lg p-5 text-center">
          <div class="text-3xl font-bold text-red-600">{{ totalNonCompliant }}</div>
          <div class="text-sm text-gray-500 mt-1">Non-Compliant Trails</div>
        </div>
        <div class="bg-white shadow rounded-lg p-5 text-center">
          <div class="text-3xl font-bold text-yellow-500">{{ totalPending }}</div>
          <div class="text-sm text-gray-500 mt-1">Pending Trails</div>
        </div>
      </div>

      <!-- Per-flow compliance table -->
      <div class="bg-white shadow rounded-lg mb-8">
        <div class="px-6 py-4 border-b border-gray-200">
          <h2 class="text-lg font-semibold text-gray-900">Per-Flow Compliance</h2>
        </div>
        <div v-if="flowStats.length === 0" class="px-6 py-8 text-center text-gray-500">No flows found.</div>
        <table v-else class="min-w-full divide-y divide-gray-200">
          <thead class="bg-gray-50">
            <tr>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Flow Name</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Total Trails</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Compliant</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Non-Compliant</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Pending</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Compliance %</th>
            </tr>
          </thead>
          <tbody class="bg-white divide-y divide-gray-200">
            <tr v-for="stat in flowStats" :key="stat.flowId" class="hover:bg-gray-50">
              <td class="px-6 py-3 text-sm font-medium text-gray-900">{{ stat.flowName }}</td>
              <td class="px-6 py-3 text-sm text-gray-700">{{ stat.total }}</td>
              <td class="px-6 py-3 text-sm text-green-700">{{ stat.compliant }}</td>
              <td class="px-6 py-3 text-sm text-red-600">{{ stat.nonCompliant }}</td>
              <td class="px-6 py-3 text-sm text-yellow-600">{{ stat.pending }}</td>
              <td class="px-6 py-3 text-sm">
                <span
                  :class="stat.pct >= 80 ? 'text-green-700 font-semibold' : stat.pct >= 50 ? 'text-yellow-600 font-semibold' : 'text-red-600 font-semibold'"
                >{{ stat.pct }}%</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Environment deployment status -->
      <div class="bg-white shadow rounded-lg">
        <div class="px-6 py-4 border-b border-gray-200">
          <h2 class="text-lg font-semibold text-gray-900">Environment Deployment Status</h2>
          <p class="text-sm text-gray-500 mt-1">What's currently deployed, grouped by image.</p>
        </div>
        <div v-if="liveArtifacts.length === 0" class="px-6 py-8 text-center text-gray-500">
          No live artifact data available.
        </div>
        <div v-else class="divide-y divide-gray-200">
          <div v-for="repo in liveArtifacts" :key="repo.imageName" class="px-6 py-4">
            <div class="font-medium text-gray-900 mb-2 font-mono text-sm">{{ repo.imageName }}</div>
            <div class="overflow-x-auto">
              <table class="min-w-full divide-y divide-gray-100 text-sm">
                <thead>
                  <tr>
                    <th class="pr-6 py-1 text-left text-xs font-medium text-gray-400 uppercase">Environment</th>
                    <th class="pr-6 py-1 text-left text-xs font-medium text-gray-400 uppercase">Tag</th>
                    <th class="pr-6 py-1 text-left text-xs font-medium text-gray-400 uppercase">SHA256</th>
                    <th class="pr-6 py-1 text-left text-xs font-medium text-gray-400 uppercase">Snapshot At</th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-gray-50">
                  <tr v-for="dep in repo.deployments" :key="dep.environmentId" class="hover:bg-gray-50">
                    <td class="pr-6 py-1.5 text-gray-800">{{ dep.environmentName }}</td>
                    <td class="pr-6 py-1.5 text-gray-600 font-mono">{{ dep.imageTag }}</td>
                    <td class="pr-6 py-1.5 text-gray-500 font-mono text-xs">{{ dep.sha256Digest.slice(0, 20) }}…</td>
                    <td class="pr-6 py-1.5 text-gray-500">{{ new Date(dep.snapshotCreatedAt).toLocaleString() }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { getFlows } from '../api/flows'
import { getTrails } from '../api/trails'
import { getLiveArtifactsByRepo } from '../api/compliancePosture'
import type { Flow, Trail } from '../types'
import type { LiveArtifactByRepo } from '../api/compliancePosture'

const loading = ref(true)
const loadError = ref('')

const flows = ref<Flow[]>([])
const trails = ref<Trail[]>([])
const liveArtifacts = ref<LiveArtifactByRepo[]>([])

const totalFlows = computed(() => flows.value.length)
const totalNonCompliant = computed(() => trails.value.filter(t => t.status === 'NON_COMPLIANT').length)
const totalPending = computed(() => trails.value.filter(t => t.status === 'PENDING').length)
const totalCompliant = computed(() => trails.value.filter(t => t.status === 'COMPLIANT').length)
const compliantPct = computed(() => {
  const total = trails.value.length
  return total === 0 ? 0 : Math.round((totalCompliant.value / total) * 100)
})

interface FlowStat {
  flowId: string
  flowName: string
  total: number
  compliant: number
  nonCompliant: number
  pending: number
  pct: number
}

const flowStats = computed<FlowStat[]>(() => {
  return flows.value.map(flow => {
    const flowTrails = trails.value.filter(t => t.flowId === flow.id)
    const compliant = flowTrails.filter(t => t.status === 'COMPLIANT').length
    const nonCompliant = flowTrails.filter(t => t.status === 'NON_COMPLIANT').length
    const pending = flowTrails.filter(t => t.status === 'PENDING').length
    const total = flowTrails.length
    const pct = total === 0 ? 0 : Math.round((compliant / total) * 100)
    return { flowId: flow.id, flowName: flow.name, total, compliant, nonCompliant, pending, pct }
  })
})

onMounted(async () => {
  try {
    const [flowsRes, trailsRes, liveRes] = await Promise.all([
      getFlows(),
      getTrails(),
      getLiveArtifactsByRepo()
    ])
    flows.value = flowsRes.data
    trails.value = trailsRes.data
    liveArtifacts.value = liveRes.data
  } catch (err) {
    console.error('Failed to load compliance posture data', err)
    loadError.value = 'Failed to load compliance posture data.'
  } finally {
    loading.value = false
  }
})
</script>
