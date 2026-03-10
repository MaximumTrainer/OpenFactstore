<template>
  <div>
    <h1 class="text-2xl font-bold text-gray-900 mb-6">Dashboard</h1>

    <div class="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
      <div class="bg-white rounded-lg shadow p-6">
        <div class="text-sm font-medium text-gray-500">Total Flows</div>
        <div class="mt-1 text-3xl font-bold text-gray-900">{{ flows.length }}</div>
      </div>
      <div class="bg-white rounded-lg shadow p-6">
        <div class="text-sm font-medium text-gray-500">Total Trails</div>
        <div class="mt-1 text-3xl font-bold text-gray-900">{{ trails.length }}</div>
        <div class="mt-2 flex gap-2 flex-wrap">
          <span class="text-xs bg-green-100 text-green-800 px-2 py-0.5 rounded-full">
            {{ statusCount('COMPLIANT') }} Compliant
          </span>
          <span class="text-xs bg-red-100 text-red-800 px-2 py-0.5 rounded-full">
            {{ statusCount('NON_COMPLIANT') }} Non-Compliant
          </span>
          <span class="text-xs bg-yellow-100 text-yellow-800 px-2 py-0.5 rounded-full">
            {{ statusCount('PENDING') }} Pending
          </span>
        </div>
      </div>
      <div class="bg-white rounded-lg shadow p-6">
        <div class="text-sm font-medium text-gray-500">Compliance Rate</div>
        <div class="mt-1 text-3xl font-bold text-gray-900">
          {{ complianceRate }}%
        </div>
      </div>
    </div>

    <div class="bg-white rounded-lg shadow">
      <div class="px-6 py-4 border-b border-gray-200">
        <h2 class="text-lg font-medium text-gray-900">Recent Trails</h2>
      </div>
      <div v-if="loading" class="p-6 text-center text-gray-500">Loading...</div>
      <div v-else-if="recentTrails.length === 0" class="p-6 text-center text-gray-500">No trails found.</div>
      <ul v-else class="divide-y divide-gray-200">
        <li
          v-for="trail in recentTrails"
          :key="trail.id"
          class="px-6 py-4 hover:bg-gray-50 cursor-pointer flex items-center justify-between"
          @click="$router.push(`/trails/${trail.id}`)"
        >
          <div>
            <div class="text-sm font-medium text-gray-900">
              {{ trail.gitCommitSha.slice(0, 8) }} — {{ trail.gitBranch }}
            </div>
            <div class="text-xs text-gray-500">{{ trail.gitAuthor }} · {{ formatDate(trail.createdAt) }}</div>
          </div>
          <StatusBadge :status="trail.status" />
        </li>
      </ul>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import StatusBadge from '../components/StatusBadge.vue'
import { getFlows } from '../api/flows'
import { getTrails } from '../api/trails'
import type { Flow, Trail } from '../types'

const flows = ref<Flow[]>([])
const trails = ref<Trail[]>([])
const loading = ref(true)

const recentTrails = computed(() => [...trails.value].slice(0, 5))

const statusCount = (status: string) =>
  trails.value.filter(t => t.status === status).length

const complianceRate = computed(() => {
  const completed = trails.value.filter(t => t.status !== 'PENDING')
  if (completed.length === 0) return 0
  const compliant = completed.filter(t => t.status === 'COMPLIANT').length
  return Math.round((compliant / completed.length) * 100)
})

function formatDate(dateStr: string): string {
  return new Date(dateStr).toLocaleDateString()
}

onMounted(async () => {
  try {
    const [flowsRes, trailsRes] = await Promise.all([getFlows(), getTrails()])
    flows.value = flowsRes.data
    trails.value = trailsRes.data
  } catch {
    // silently fail on load error
  } finally {
    loading.value = false
  }
})
</script>
