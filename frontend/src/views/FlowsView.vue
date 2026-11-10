<template>
  <div>
    <div class="flex items-center justify-between mb-6">
      <h1 class="text-2xl font-bold text-gray-900">Flows</h1>
      <div class="flex items-center gap-4">
        <label class="flex items-center gap-2 text-sm text-gray-600">
          <input
            v-model="includeArchived"
            data-test="include-archived"
            type="checkbox"
            class="rounded border-gray-300 text-indigo-600 focus:ring-indigo-500"
            @change="reload"
          />
          Show archived
        </label>
        <button
          v-if="canCreateFlow"
          class="bg-indigo-600 text-white px-4 py-2 rounded-md text-sm font-medium hover:bg-indigo-700"
          @click="showModal = true"
        >
          + New Flow
        </button>
      </div>
    </div>

    <div v-if="loading" class="text-center text-gray-500 py-12">Loading...</div>
    <div v-else-if="flows.length === 0" class="text-center text-gray-500 py-12">No flows found.</div>
    <div v-else class="bg-white shadow rounded-lg overflow-hidden">
      <table class="min-w-full divide-y divide-gray-200">
        <thead class="bg-gray-50">
          <tr>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Name</th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Description</th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">State</th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Required Attestations</th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Tags</th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Actions</th>
          </tr>
        </thead>
        <tbody class="bg-white divide-y divide-gray-200">
          <tr v-for="flow in flows" :key="flow.id" class="hover:bg-gray-50">
            <td class="px-6 py-4 text-sm font-medium text-gray-900">{{ flow.name }}</td>
            <td class="px-6 py-4 text-sm text-gray-500">{{ flow.description }}</td>
            <td class="px-6 py-4">
              <span
                :class="flow.archivedAt ? 'bg-gray-200 text-gray-700' : 'bg-green-100 text-green-800'"
                class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium"
              >{{ flow.archivedAt ? 'Archived' : 'Active' }}</span>
            </td>
            <td class="px-6 py-4">
              <div class="flex flex-wrap gap-1">
                <span
                  v-for="type in flow.requiredAttestationTypes"
                  :key="type"
                  class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-indigo-100 text-indigo-800"
                >
                  {{ type }}
                </span>
              </div>
            </td>
            <td class="px-6 py-4">
              <div class="flex flex-wrap gap-1">
                <span
                  v-for="(value, key) in flow.tags"
                  :key="key"
                  class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-amber-100 text-amber-800"
                >
                  {{ key }}: {{ value }}
                </span>
              </div>
            </td>
            <td class="px-6 py-4 text-sm">
              <div class="flex items-center gap-3">
                <RouterLink
                  :to="`/flows/${flow.id}`"
                  class="text-indigo-600 hover:text-indigo-900 font-medium"
                >View Trails</RouterLink>
                <button
                  data-test="edit-flow-row"
                  type="button"
                  class="text-indigo-600 hover:text-indigo-900 font-medium"
                  @click="editing = flow"
                >Edit</button>
                <button
                  data-test="archive-flow-row"
                  type="button"
                  class="text-gray-600 hover:text-gray-900 font-medium"
                  @click="toggleArchive(flow)"
                >{{ flow.archivedAt ? 'Unarchive' : 'Archive' }}</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="editing" class="fixed inset-0 bg-black bg-opacity-40 flex items-center justify-center z-50 p-4">
      <div class="bg-white rounded-lg shadow-xl p-6 w-full max-w-2xl max-h-full overflow-y-auto">
        <h2 class="text-lg font-bold text-gray-900 mb-4">Edit Flow</h2>
        <FlowEditForm :flow="editing" @saved="onFlowSaved" @cancel="editing = null" />
      </div>
    </div>

    <!-- Create Flow Modal -->
    <div v-if="showModal" class="fixed inset-0 bg-black bg-opacity-40 flex items-center justify-center z-50">
      <div class="bg-white rounded-lg shadow-xl p-6 w-full max-w-lg max-h-full overflow-y-auto">
        <h2 class="text-lg font-bold text-gray-900 mb-4">Create New Flow</h2>
        <FlowTemplatePicker class="mb-4" @change="onTemplateChange" />
        <form @submit.prevent="submitFlow">
          <div class="mb-4">
            <label class="block text-sm font-medium text-gray-700 mb-1">Name</label>
            <input
              v-model="form.name"
              type="text"
              required
              class="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
          </div>
          <div class="mb-4">
            <label class="block text-sm font-medium text-gray-700 mb-1">Description</label>
            <textarea
              v-model="form.description"
              rows="3"
              class="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
          </div>
          <div class="mb-4">
            <label class="block text-sm font-medium text-gray-700 mb-1">
              Required Attestation Types <span class="text-gray-400">(comma-separated)</span>
            </label>
            <input
              v-model="form.attestationTypes"
              type="text"
              placeholder="e.g. SAST,SCA,DAST"
              class="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
          </div>
          <div class="mb-6">
            <label class="block text-sm font-medium text-gray-700 mb-1">
              Tags <span class="text-gray-400">(key=value, comma-separated)</span>
            </label>
            <input
              v-model="form.tags"
              type="text"
              placeholder="e.g. risk-level=high,team=payments"
              class="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
          </div>
          <div v-if="formError" class="mb-4 text-sm text-red-600">{{ formError }}</div>
          <div class="flex justify-end gap-3">
            <button
              type="button"
              class="px-4 py-2 text-sm text-gray-700 border border-gray-300 rounded-md hover:bg-gray-50"
              @click="closeModal"
            >Cancel</button>
            <button
              type="submit"
              :disabled="submitting"
              class="px-4 py-2 text-sm text-white bg-indigo-600 rounded-md hover:bg-indigo-700 disabled:opacity-50"
            >{{ submitting ? 'Creating...' : 'Create' }}</button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useAuth } from '../composables/useAuth'
import { computed, ref, onMounted } from 'vue'
import { getFlows, createFlow, archiveFlow, unarchiveFlow } from '../api/flows'
import FlowEditForm from '../components/FlowEditForm.vue'
import FlowTemplatePicker from '../components/FlowTemplatePicker.vue'
import type { Flow } from '../types'

const flows = ref<Flow[]>([])
const loading = ref(true)
const { isAuthenticated, can } = useAuth()
// Offered only to callers who may create flows; the server is still the authority (#156 FR-6.5).
// With no one signed in the instance is not enforcing, so the control stays available.
const canCreateFlow = computed(() => !isAuthenticated.value || can('flows:write'))
const showModal = ref(false)
const submitting = ref(false)
const formError = ref('')

const form = ref({ name: '', description: '', attestationTypes: '', tags: '' })

// A template pre-populates the gates but never locks them: whatever ends up in the
// attestation-types field is what gets saved, so it stays editable before creation (#162).
const chosenTemplateIds = ref<string[]>([])

function onTemplateChange(selection: { templateIds: string[]; requiredAttestations: string[] }) {
  chosenTemplateIds.value = selection.templateIds
  form.value.attestationTypes = selection.requiredAttestations.join(', ')
}
const editing = ref<Flow | null>(null)
const includeArchived = ref(false)

async function reload() {
  const res = await getFlows(includeArchived.value)
  flows.value = res.data
}

function onFlowSaved(updated: Flow) {
  flows.value = flows.value.map(f => (f.id === updated.id ? updated : f))
  editing.value = null
}

async function toggleArchive(flow: Flow) {
  const { data } = flow.archivedAt ? await unarchiveFlow(flow.id) : await archiveFlow(flow.id)
  // An archived flow drops out of the default list, so reload rather than patch in place.
  if (!includeArchived.value && data.archivedAt) {
    flows.value = flows.value.filter(f => f.id !== flow.id)
    return
  }
  flows.value = flows.value.map(f => (f.id === data.id ? data : f))
}

function parseTagsInput(raw: string): Record<string, string> {
  const result: Record<string, string> = {}
  raw.split(',').forEach(pair => {
    const idx = pair.indexOf('=')
    if (idx > 0) {
      const key = pair.slice(0, idx).trim()
      const value = pair.slice(idx + 1).trim()
      if (key) result[key] = value
    }
  })
  return result
}

function closeModal() {
  showModal.value = false
  form.value = { name: '', description: '', attestationTypes: '', tags: '' }
  chosenTemplateIds.value = []
  formError.value = ''
}

async function submitFlow() {
  submitting.value = true
  formError.value = ''
  try {
    const types = form.value.attestationTypes
      .split(',')
      .map(s => s.trim())
      .filter(Boolean)
    const tags = parseTagsInput(form.value.tags)
    await createFlow({
      name: form.value.name,
      description: form.value.description,
      requiredAttestationTypes: types,
      tags,
      ...(chosenTemplateIds.value.length ? { templateIds: chosenTemplateIds.value } : {}),
    })
    await reload()
    closeModal()
  } catch {
    formError.value = 'Failed to create flow. Please try again.'
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  try {
    await reload()
  } catch {
    // silently fail
  } finally {
    loading.value = false
  }
})
</script>
