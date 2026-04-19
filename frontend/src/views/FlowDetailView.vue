<template>
  <div>
    <div class="mb-6">
      <RouterLink to="/flows" class="text-sm text-indigo-600 hover:text-indigo-900">← Back to Flows</RouterLink>
    </div>

    <div v-if="loading" class="text-center text-gray-500 py-12">Loading...</div>
    <div v-else-if="!flow" class="text-center text-gray-500 py-12">Flow not found.</div>
    <div v-else>
      <div class="bg-white rounded-lg shadow p-6 mb-6">
        <div class="flex items-start justify-between">
          <div>
            <h1 class="text-2xl font-bold text-gray-900 mb-1">{{ flow.name }}</h1>
            <p class="text-gray-500 mb-4">{{ flow.description }}</p>
            <div class="flex items-center gap-2 mb-3">
              <StatusBadge :status="flow.visibility ?? 'PRIVATE'" />
            </div>
            <div class="flex flex-wrap gap-2 mb-3">
              <span
                v-for="type in flow.requiredAttestationTypes"
                :key="type"
                class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-indigo-100 text-indigo-800"
              >{{ type }}</span>
            </div>
            <div v-if="Object.keys(flow.tags).length > 0" class="flex flex-wrap gap-2">
              <span
                v-for="(value, key) in flow.tags"
                :key="key"
                class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-amber-100 text-amber-800"
              >{{ key }}: {{ value }}</span>
            </div>
          </div>
          <button
            class="ml-4 flex-shrink-0 bg-white border border-gray-300 text-gray-700 px-4 py-2 rounded-md text-sm font-medium hover:bg-gray-50 flex items-center gap-2"
            :disabled="exportingReport"
            @click="exportComplianceReport"
          >
            <span v-if="exportingReport">Exporting…</span>
            <span v-else>⬇ Export Compliance Report</span>
          </button>
        </div>
        <p v-if="exportError" class="mt-3 text-sm text-red-600">{{ exportError }}</p>
      </div>

      <div class="bg-white rounded-lg shadow">
        <div class="px-6 py-4 border-b border-gray-200">
          <h2 class="text-lg font-medium text-gray-900">Trails</h2>
        </div>
        <div v-if="trailsLoading" class="p-6 text-center text-gray-500">Loading trails...</div>
        <div v-else-if="trails.length === 0" class="p-6 text-center text-gray-500">No trails for this flow.</div>
        <table v-else class="min-w-full divide-y divide-gray-200">
          <thead class="bg-gray-50">
            <tr>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Commit SHA</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Branch</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Author</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Status</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Created At</th>
            </tr>
          </thead>
          <tbody class="bg-white divide-y divide-gray-200">
            <tr
              v-for="trail in trails"
              :key="trail.id"
              class="hover:bg-gray-50 cursor-pointer"
              @click="$router.push(`/trails/${trail.id}`)"
            >
              <td class="px-6 py-4 text-sm font-mono text-gray-900">{{ trail.gitCommitSha.slice(0, 8) }}</td>
              <td class="px-6 py-4 text-sm text-gray-500">{{ trail.gitBranch }}</td>
              <td class="px-6 py-4 text-sm text-gray-500">{{ trail.gitAuthor }}</td>
              <td class="px-6 py-4"><StatusBadge :status="trail.status" /></td>
              <td class="px-6 py-4 text-sm text-gray-500">{{ formatDate(trail.createdAt) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import StatusBadge from '../components/StatusBadge.vue'
import { getFlow } from '../api/flows'
import { getTrails } from '../api/trails'
import { getComplianceReport } from '../api/reports'
import type { Flow, Trail } from '../types'

const route = useRoute()
const flow = ref<Flow | null>(null)
const trails = ref<Trail[]>([])
const loading = ref(true)
const trailsLoading = ref(true)
const exportingReport = ref(false)
const exportError = ref('')

function formatDate(dateStr: string): string {
  return new Date(dateStr).toLocaleString()
}

async function exportComplianceReport() {
  if (!flow.value) return
  exportingReport.value = true
  exportError.value = ''
  try {
    const res = await getComplianceReport(flow.value.id)
    const blob = new Blob([JSON.stringify(res.data, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    const safeName = flow.value.name.replace(/[^\w\-]/g, '-')
    a.href = url
    a.download = `compliance-report-${safeName}-${new Date().toISOString().slice(0, 10)}.json`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    setTimeout(() => URL.revokeObjectURL(url), 100)
  } catch {
    exportError.value = 'Failed to export report. Please try again.'
  } finally {
    exportingReport.value = false
  }
}

onMounted(async () => {
  const id = route.params.id as string
  try {
    const res = await getFlow(id)
    flow.value = res.data
  } catch {
    // flow not found
  } finally {
    loading.value = false
  }
  try {
    const res = await getTrails(id)
    trails.value = res.data
  } catch {
    // trails not found
  } finally {
    trailsLoading.value = false
  }
})
</script>
