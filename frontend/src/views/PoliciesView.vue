<template>
  <div>
    <div class="flex items-center justify-between mb-6">
      <h1 class="text-2xl font-bold text-gray-900">Deployment Policies</h1>
      <button
        class="bg-indigo-600 text-white px-4 py-2 rounded-md text-sm font-medium hover:bg-indigo-700"
        @click="showModal = true"
      >
        + New Policy
      </button>
    </div>

    <div v-if="loading" class="text-center text-gray-500 py-12">Loading...</div>
    <div v-else-if="loadError" class="text-center text-red-600 py-12">{{ loadError }}</div>
    <div v-else-if="policies.length === 0" class="text-center text-gray-500 py-12">No policies found.</div>
    <div v-else class="bg-white shadow rounded-lg overflow-hidden">
      <table class="min-w-full divide-y divide-gray-200">
        <thead class="bg-gray-50">
          <tr>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Name</th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Type</th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Threshold</th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Sig Required</th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Evaluator</th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Actions</th>
          </tr>
        </thead>
        <tbody class="bg-white divide-y divide-gray-200">
          <tr v-for="policy in policies" :key="policy.id" class="hover:bg-gray-50">
            <td class="px-6 py-4 text-sm font-medium text-gray-900">{{ policy.name }}</td>
            <td class="px-6 py-4">
              <span :class="policyTypeBadgeClass(policy.policyType)" class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium">
                {{ policy.policyType }}
              </span>
            </td>
            <td class="px-6 py-4 text-sm text-gray-500">{{ policy.threshold ?? '—' }}</td>
            <td class="px-6 py-4 text-sm text-gray-500">{{ policy.requireSignature ? 'Yes' : 'No' }}</td>
            <td class="px-6 py-4 text-sm text-gray-500">{{ policy.policyEvaluator ?? '—' }}</td>
            <td class="px-6 py-4 text-sm">
              <button
                class="text-red-600 hover:text-red-900 font-medium"
                @click="confirmDelete(policy)"
              >Delete</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Create Policy Modal -->
    <div v-if="showModal" class="fixed inset-0 bg-black bg-opacity-40 flex items-center justify-center z-50">
      <div class="bg-white rounded-lg shadow-xl p-6 w-full max-w-md">
        <h2 class="text-lg font-bold text-gray-900 mb-4">Create New Policy</h2>
        <form @submit.prevent="submitPolicy">
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
            <label class="block text-sm font-medium text-gray-700 mb-1">Policy Type</label>
            <select
              v-model="form.policyType"
              class="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
            >
              <option value="ATTESTATION_COUNT">ATTESTATION_COUNT</option>
              <option value="APPROVAL_REQUIRED">APPROVAL_REQUIRED</option>
              <option value="SIGNATURE_REQUIRED">SIGNATURE_REQUIRED</option>
              <option value="CUSTOM_WASM">CUSTOM_WASM</option>
            </select>
          </div>
          <div class="mb-4">
            <label class="block text-sm font-medium text-gray-700 mb-1">Threshold</label>
            <input
              v-model.number="form.threshold"
              type="number"
              min="0"
              class="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
          </div>
          <div class="mb-6">
            <label class="flex items-center gap-2 text-sm font-medium text-gray-700 cursor-pointer">
              <input v-model="form.requireSignature" type="checkbox" class="rounded" />
              Require Signature
            </label>
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

    <!-- Delete Confirmation Modal -->
    <div v-if="deleteTarget" class="fixed inset-0 bg-black bg-opacity-40 flex items-center justify-center z-50">
      <div class="bg-white rounded-lg shadow-xl p-6 w-full max-w-sm">
        <h2 class="text-lg font-bold text-gray-900 mb-2">Delete Policy</h2>
        <p class="text-sm text-gray-500 mb-6">
          Are you sure you want to delete <span class="font-medium text-gray-900">{{ deleteTarget.name }}</span>? This cannot be undone.
        </p>
        <div class="flex justify-end gap-3">
          <button
            class="px-4 py-2 text-sm text-gray-700 border border-gray-300 rounded-md hover:bg-gray-50"
            @click="deleteTarget = null"
          >Cancel</button>
          <button
            :disabled="deleting"
            class="px-4 py-2 text-sm text-white bg-red-600 rounded-md hover:bg-red-700 disabled:opacity-50"
            @click="doDelete"
          >{{ deleting ? 'Deleting...' : 'Delete' }}</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { listPolicies, createPolicy, deletePolicy } from '../api/policies'
import type { DeploymentPolicy } from '../api/policies'

const policies = ref<DeploymentPolicy[]>([])
const loading = ref(true)
const loadError = ref('')
const showModal = ref(false)
const submitting = ref(false)
const formError = ref('')
const deleteTarget = ref<DeploymentPolicy | null>(null)
const deleting = ref(false)

const form = ref({
  name: '',
  policyType: 'ATTESTATION_COUNT',
  threshold: undefined as number | undefined,
  requireSignature: false
})

function policyTypeBadgeClass(type: string): string {
  switch (type) {
    case 'ATTESTATION_COUNT': return 'bg-indigo-100 text-indigo-800'
    case 'APPROVAL_REQUIRED': return 'bg-yellow-100 text-yellow-800'
    case 'SIGNATURE_REQUIRED': return 'bg-green-100 text-green-800'
    case 'CUSTOM_WASM': return 'bg-purple-100 text-purple-800'
    default: return 'bg-gray-100 text-gray-800'
  }
}

function closeModal() {
  showModal.value = false
  form.value = { name: '', policyType: 'ATTESTATION_COUNT', threshold: undefined, requireSignature: false }
  formError.value = ''
}

async function submitPolicy() {
  submitting.value = true
  formError.value = ''
  try {
    await createPolicy(form.value)
    const res = await listPolicies()
    policies.value = res.data
    closeModal()
  } catch {
    formError.value = 'Failed to create policy. Please try again.'
  } finally {
    submitting.value = false
  }
}

function confirmDelete(policy: DeploymentPolicy) {
  deleteTarget.value = policy
}

async function doDelete() {
  if (!deleteTarget.value) return
  deleting.value = true
  try {
    await deletePolicy(deleteTarget.value.id)
    const res = await listPolicies()
    policies.value = res.data
    deleteTarget.value = null
  } catch {
    // silently ignore
  } finally {
    deleting.value = false
  }
}

onMounted(async () => {
  try {
    const res = await listPolicies()
    policies.value = res.data
  } catch {
    loadError.value = 'Failed to load policies.'
  } finally {
    loading.value = false
  }
})
</script>
