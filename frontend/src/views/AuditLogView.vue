<template>
  <div>
    <h1 class="text-2xl font-bold text-gray-900 mb-6">Audit Log</h1>

    <!-- Filters -->
    <div class="bg-white rounded-lg shadow p-4 mb-6">
      <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <div>
          <label class="block text-xs font-medium text-gray-500 mb-1">Event Type</label>
          <select
            v-model="filters.eventType"
            class="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-1 focus:ring-indigo-500"
          >
            <option value="">All</option>
            <option v-for="t in eventTypes" :key="t" :value="t">{{ t }}</option>
          </select>
        </div>
        <div>
          <label class="block text-xs font-medium text-gray-500 mb-1">Actor</label>
          <input
            v-model="filters.actor"
            type="text"
            placeholder="Filter by actor"
            class="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-1 focus:ring-indigo-500"
          />
        </div>
        <div>
          <label class="block text-xs font-medium text-gray-500 mb-1">From</label>
          <input
            v-model="filters.from"
            type="datetime-local"
            class="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-1 focus:ring-indigo-500"
          />
        </div>
        <div>
          <label class="block text-xs font-medium text-gray-500 mb-1">To</label>
          <input
            v-model="filters.to"
            type="datetime-local"
            class="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-1 focus:ring-indigo-500"
          />
        </div>
      </div>
      <div class="mt-3 flex items-center gap-3">
        <button
          class="bg-indigo-600 text-white px-4 py-2 rounded-md text-sm font-medium hover:bg-indigo-700"
          @click="applyFilters"
        >Apply Filters</button>
        <button
          class="text-sm text-gray-500 hover:text-gray-700"
          @click="resetFilters"
        >Reset</button>
        <label class="flex items-center gap-2 text-sm text-gray-600 ml-auto">
          <input v-model="filters.sortDesc" type="checkbox" class="rounded" @change="applyFilters" />
          Newest first
        </label>
      </div>
    </div>

    <!-- Event Table -->
    <div class="bg-white rounded-lg shadow">
      <div class="px-6 py-4 border-b border-gray-200 flex items-center justify-between">
        <h2 class="text-lg font-medium text-gray-900">
          Events
          <span v-if="page" class="ml-2 text-sm font-normal text-gray-500">
            ({{ page.totalElements }} total)
          </span>
        </h2>
      </div>
      <div v-if="loading" class="p-6 text-center text-gray-500">Loading...</div>
      <div v-else-if="loadError" class="p-6 text-center text-red-600">Failed to load audit events. Please try again.</div>
      <div v-else-if="events.length === 0" class="p-6 text-center text-gray-500">No audit events found.</div>
      <div v-else>
        <table class="min-w-full divide-y divide-gray-200">
          <thead class="bg-gray-50">
            <tr>
              <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Occurred At</th>
              <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Event Type</th>
              <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Actor</th>
              <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Trail ID</th>
              <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Artifact SHA256</th>
              <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Payload</th>
            </tr>
          </thead>
          <tbody class="bg-white divide-y divide-gray-200">
            <tr v-for="event in events" :key="event.id" class="hover:bg-gray-50">
              <td class="px-4 py-3 text-xs text-gray-500 whitespace-nowrap">{{ formatDate(event.occurredAt) }}</td>
              <td class="px-4 py-3">
                <span :class="eventTypeBadgeClass(event.eventType)" class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium">
                  {{ event.eventType }}
                </span>
              </td>
              <td class="px-4 py-3 text-sm text-gray-700">{{ event.actor }}</td>
              <td class="px-4 py-3 text-xs font-mono text-gray-500">
                <RouterLink
                  v-if="event.trailId"
                  :to="`/trails/${event.trailId}`"
                  class="text-indigo-600 hover:underline"
                >{{ event.trailId.slice(0, 8) }}...</RouterLink>
                <span v-else class="text-gray-300">—</span>
              </td>
              <td class="px-4 py-3 text-xs font-mono text-gray-500 max-w-xs truncate">
                {{ event.artifactSha256 ?? '—' }}
              </td>
              <td class="px-4 py-3 text-xs text-gray-500 max-w-xs truncate" :title="event.payload">
                {{ event.payload }}
              </td>
            </tr>
          </tbody>
        </table>

        <!-- Pagination -->
        <div v-if="page && page.totalPages > 1" class="px-6 py-3 border-t border-gray-200 flex items-center justify-between text-sm text-gray-600">
          <span>Page {{ currentPage + 1 }} of {{ page.totalPages }}</span>
          <div class="flex gap-2">
            <button
              :disabled="currentPage === 0"
              class="px-3 py-1 rounded border border-gray-300 disabled:opacity-40 hover:bg-gray-50"
              @click="goToPage(currentPage - 1)"
            >Previous</button>
            <button
              :disabled="currentPage + 1 >= page.totalPages"
              class="px-3 py-1 rounded border border-gray-300 disabled:opacity-40 hover:bg-gray-50"
              @click="goToPage(currentPage + 1)"
            >Next</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { getAuditEvents } from '../api/audit'
import type { AuditEvent, AuditEventPage, AuditEventType } from '../types'

const events = ref<AuditEvent[]>([])
const page = ref<AuditEventPage | null>(null)
const loading = ref(true)
const loadError = ref(false)
const currentPage = ref(0)

const eventTypes: AuditEventType[] = [
  'ARTIFACT_DEPLOYED',
  'ARTIFACT_REMOVED',
  'ARTIFACT_UPDATED',
  'ENVIRONMENT_CREATED',
  'ENVIRONMENT_DELETED',
  'POLICY_EVALUATED',
  'ATTESTATION_RECORDED',
  'APPROVAL_GRANTED',
  'APPROVAL_REJECTED',
  'GATE_BLOCKED',
  'GATE_ALLOWED'
]

const filters = reactive({
  eventType: '' as AuditEventType | '',
  actor: '',
  from: '',
  to: '',
  sortDesc: true
})

function formatDate(dateStr: string): string {
  return new Date(dateStr).toLocaleString()
}

function eventTypeBadgeClass(eventType: AuditEventType): string {
  if (eventType === 'GATE_BLOCKED' || eventType === 'ARTIFACT_REMOVED') {
    return 'bg-red-100 text-red-800'
  }
  if (eventType === 'GATE_ALLOWED' || eventType === 'ARTIFACT_DEPLOYED') {
    return 'bg-green-100 text-green-800'
  }
  if (eventType === 'ATTESTATION_RECORDED' || eventType === 'POLICY_EVALUATED') {
    return 'bg-blue-100 text-blue-800'
  }
  if (eventType === 'APPROVAL_GRANTED') {
    return 'bg-green-100 text-green-800'
  }
  if (eventType === 'APPROVAL_REJECTED') {
    return 'bg-red-100 text-red-800'
  }
  return 'bg-gray-100 text-gray-700'
}

async function loadEvents(p = 0) {
  loading.value = true
  loadError.value = false
  try {
    const params: Record<string, string | number | boolean | undefined> = {
      page: p,
      size: 20,
      sortDesc: filters.sortDesc
    }
    if (filters.eventType) params.eventType = filters.eventType
    if (filters.actor) params.actor = filters.actor
    if (filters.from) params.from = new Date(filters.from).toISOString()
    if (filters.to) params.to = new Date(filters.to).toISOString()

    const res = await getAuditEvents(params as Parameters<typeof getAuditEvents>[0])
    page.value = res.data
    events.value = res.data.events
    currentPage.value = p
  } catch (err) {
    console.error('Failed to load audit events', err)
    loadError.value = true
    events.value = []
  } finally {
    loading.value = false
  }
}

function applyFilters() {
  loadEvents(0)
}

function resetFilters() {
  filters.eventType = ''
  filters.actor = ''
  filters.from = ''
  filters.to = ''
  filters.sortDesc = true
  loadEvents(0)
}

function goToPage(p: number) {
  loadEvents(p)
}

onMounted(() => loadEvents())
</script>
