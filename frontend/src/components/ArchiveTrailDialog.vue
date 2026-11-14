<template>
  <div class="fixed inset-0 bg-black bg-opacity-40 flex items-center justify-center z-50 p-4">
    <div class="bg-white rounded-lg shadow-xl p-6 w-full max-w-lg">
      <h2 class="text-lg font-bold text-gray-900 mb-2">Archive this trail?</h2>
      <p class="text-sm text-gray-600 mb-4">
        Trail <span class="font-mono">{{ trail.id }}</span> —
        commit <span class="font-mono">{{ trail.gitCommitSha.slice(0, 8) }}</span> on
        {{ trail.gitBranch }}.
      </p>

      <!-- Trails are evidence: they are archived, never deleted (#245). -->
      <div
        data-test="cascade-summary"
        class="mb-4 p-3 rounded-md bg-gray-50 border border-gray-200 text-sm text-gray-700"
      >
        <p class="font-medium mb-1">Everything recorded against it is kept:</p>
        <ul v-if="cascade" class="list-disc list-inside space-y-0.5">
          <li v-for="item in itemised" :key="item.label">{{ item.count }} {{ item.label }}</li>
          <li v-if="!itemised.length">no other records</li>
        </ul>
        <p v-else class="text-gray-500">Counting what this trail holds…</p>
      </div>

      <p class="mb-4 text-xs text-gray-500">
        An archived trail is hidden from the listings and read-only: it takes no new attestations,
        artifacts or evidence. You can unarchive it at any time.
      </p>

      <p v-if="error" data-test="archive-error" class="mt-3 text-sm text-red-600">{{ error }}</p>

      <div class="mt-6 flex justify-end gap-3">
        <button
          data-test="cancel-archive"
          type="button"
          class="px-4 py-2 text-sm text-gray-700 border border-gray-300 rounded-md hover:bg-gray-50"
          @click="emit('cancel')"
        >
          Cancel
        </button>
        <button
          data-test="confirm-archive"
          type="button"
          :disabled="busy"
          class="px-4 py-2 text-sm text-white bg-indigo-600 rounded-md hover:bg-indigo-700 disabled:opacity-50"
          @click="archive"
        >
          {{ busy ? 'Working…' : 'Archive' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { archiveTrail, getTrailCascade } from '../api/trails'
import type { Trail, TrailCascadeCounts } from '../types'

const props = defineProps<{ trail: Trail }>()
const emit = defineEmits<{ archived: [trail: Trail]; cancel: [] }>()

const cascade = ref<TrailCascadeCounts | null>(null)
const busy = ref(false)
const error = ref('')

const LABELS: Array<[keyof TrailCascadeCounts, string]> = [
  ['attestations', 'attestations'],
  ['artifacts', 'artifacts'],
  ['evidenceFiles', 'evidence files'],
  ['approvals', 'approvals'],
  ['coverageReports', 'coverage reports'],
  ['securityScans', 'security scans'],
  ['complianceAssessments', 'compliance assessments'],
  ['jiraTickets', 'Jira tickets'],
]

const itemised = computed(() =>
  cascade.value
    ? LABELS.filter(([key]) => (cascade.value![key] as number) > 0).map(([key, label]) => ({
        label,
        count: cascade.value![key] as number,
      }))
    : []
)

async function archive() {
  busy.value = true
  error.value = ''
  try {
    const { data } = await archiveTrail(props.trail.id)
    emit('archived', data)
  } catch {
    error.value = 'Failed to archive the trail.'
  } finally {
    busy.value = false
  }
}

onMounted(async () => {
  try {
    const { data } = await getTrailCascade(props.trail.id)
    cascade.value = data
  } catch {
    // Without the counts the dialog still works; it just cannot itemise.
  }
})
</script>
