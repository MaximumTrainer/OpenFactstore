<template>
  <div>
    <div class="flex items-center justify-between mb-6">
      <h1 class="text-2xl font-bold text-gray-900">Environments</h1>
      <button
        class="bg-indigo-600 text-white px-4 py-2 rounded-md text-sm font-medium hover:bg-indigo-700"
        @click="showModal = true"
      >
        + New Environment
      </button>
    </div>

    <div v-if="loading" class="text-center text-gray-500 py-12">Loading...</div>
    <div v-else-if="environments.length === 0" class="text-center text-gray-500 py-12">No environments found.</div>
    <div v-else class="bg-white shadow rounded-lg overflow-hidden">
      <table class="min-w-full divide-y divide-gray-200">
        <thead class="bg-gray-50">
          <tr>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Name</th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Type</th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Description</th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Created</th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Actions</th>
          </tr>
        </thead>
        <tbody class="bg-white divide-y divide-gray-200">
          <tr v-for="env in environments" :key="env.id" class="hover:bg-gray-50">
            <td class="px-6 py-4 text-sm font-medium text-gray-900">{{ env.name }}</td>
            <td class="px-6 py-4">
              <span :class="typeBadgeClass(env.type)" class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium">
                {{ env.type }}
              </span>
            </td>
            <td class="px-6 py-4 text-sm text-gray-500">{{ env.description }}</td>
            <td class="px-6 py-4 text-sm text-gray-500">{{ new Date(env.createdAt).toLocaleDateString() }}</td>
            <td class="px-6 py-4 text-sm">
              <RouterLink
                :to="`/environments/${env.id}`"
                class="text-indigo-600 hover:text-indigo-900 font-medium"
              >View Snapshots</RouterLink>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Create Environment Modal -->
    <div v-if="showModal" class="fixed inset-0 bg-black bg-opacity-40 flex items-center justify-center z-50">
      <div class="bg-white rounded-lg shadow-xl p-6 w-full max-w-md">
        <h2 class="text-lg font-bold text-gray-900 mb-4">Register New Environment</h2>
        <form @submit.prevent="submitEnvironment">
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
            <label class="block text-sm font-medium text-gray-700 mb-1">Type</label>
            <select
              v-model="form.type"
              class="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
            >
              <option value="K8S">Kubernetes (K8S)</option>
              <option value="S3">S3 Bucket</option>
              <option value="LAMBDA">Lambda</option>
              <option value="GENERIC">Generic</option>
            </select>
          </div>
          <div class="mb-6">
            <label class="block text-sm font-medium text-gray-700 mb-1">Description</label>
            <textarea
              v-model="form.description"
              rows="3"
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
            >{{ submitting ? 'Registering...' : 'Register' }}</button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getEnvironments, createEnvironment } from '../api/environments'
import type { Environment, EnvironmentType } from '../types'

const environments = ref<Environment[]>([])
const loading = ref(true)
const showModal = ref(false)
const submitting = ref(false)
const formError = ref('')

const form = ref<{ name: string; type: EnvironmentType; description: string }>({
  name: '',
  type: 'K8S',
  description: ''
})

function closeModal() {
  showModal.value = false
  form.value = { name: '', type: 'K8S', description: '' }
  formError.value = ''
}

function typeBadgeClass(type: string) {
  const map: Record<string, string> = {
    K8S: 'bg-blue-100 text-blue-800',
    S3: 'bg-yellow-100 text-yellow-800',
    LAMBDA: 'bg-purple-100 text-purple-800',
    GENERIC: 'bg-gray-100 text-gray-800'
  }
  return map[type] ?? 'bg-gray-100 text-gray-800'
}

async function submitEnvironment() {
  submitting.value = true
  formError.value = ''
  try {
    await createEnvironment({ name: form.value.name, type: form.value.type, description: form.value.description })
    const res = await getEnvironments()
    environments.value = res.data
    closeModal()
  } catch {
    formError.value = 'Failed to register environment. Please try again.'
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  try {
    const res = await getEnvironments()
    environments.value = res.data
  } catch {
    // silently fail
  } finally {
    loading.value = false
  }
})
</script>
