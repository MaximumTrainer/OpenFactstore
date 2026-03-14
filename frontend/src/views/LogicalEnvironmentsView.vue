<template>
  <div>
    <div class="flex items-center justify-between mb-6">
      <h1 class="text-2xl font-bold text-gray-900">Logical Environments</h1>
      <button
        class="bg-indigo-600 text-white px-4 py-2 rounded-md text-sm font-medium hover:bg-indigo-700"
        @click="showModal = true"
      >
        + New Logical Environment
      </button>
    </div>

    <div v-if="loading" class="text-center text-gray-500 py-12">Loading...</div>
    <div v-else-if="logicalEnvs.length === 0" class="text-center text-gray-500 py-12">
      No logical environments found. Create one to group physical environments.
    </div>
    <div v-else class="bg-white shadow rounded-lg overflow-hidden">
      <table class="min-w-full divide-y divide-gray-200">
        <thead class="bg-gray-50">
          <tr>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Name</th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Description</th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Members</th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Created</th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Actions</th>
          </tr>
        </thead>
        <tbody class="bg-white divide-y divide-gray-200">
          <tr v-for="env in logicalEnvs" :key="env.id" class="hover:bg-gray-50">
            <td class="px-6 py-4 text-sm font-medium text-gray-900">{{ env.name }}</td>
            <td class="px-6 py-4 text-sm text-gray-500">{{ env.description }}</td>
            <td class="px-6 py-4 text-sm text-gray-500">{{ env.members.length }}</td>
            <td class="px-6 py-4 text-sm text-gray-500">{{ new Date(env.createdAt).toLocaleDateString() }}</td>
            <td class="px-6 py-4 text-sm">
              <RouterLink
                :to="`/logical-environments/${env.id}`"
                class="text-indigo-600 hover:text-indigo-900 font-medium mr-4"
              >View</RouterLink>
              <button
                class="text-red-600 hover:text-red-900 font-medium"
                @click="confirmDelete(env)"
              >Delete</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Create Modal -->
    <div v-if="showModal" class="fixed inset-0 bg-black bg-opacity-40 flex items-center justify-center z-50">
      <div class="bg-white rounded-lg shadow-xl p-6 w-full max-w-md">
        <h2 class="text-lg font-bold text-gray-900 mb-4">Create Logical Environment</h2>
        <form @submit.prevent="submitCreate">
          <div class="mb-4">
            <label class="block text-sm font-medium text-gray-700 mb-1">Name</label>
            <input
              v-model="form.name"
              type="text"
              required
              class="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
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
            >{{ submitting ? 'Creating...' : 'Create' }}</button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { logicalEnvironmentsApi } from '../api/logicalEnvironments'
import type { LogicalEnvironment } from '../types'

const logicalEnvs = ref<LogicalEnvironment[]>([])
const loading = ref(true)
const showModal = ref(false)
const submitting = ref(false)
const formError = ref('')

const form = ref({ name: '', description: '' })

function closeModal() {
  showModal.value = false
  form.value = { name: '', description: '' }
  formError.value = ''
}

async function submitCreate() {
  submitting.value = true
  formError.value = ''
  try {
    await logicalEnvironmentsApi.create({ name: form.value.name, description: form.value.description })
    const res = await logicalEnvironmentsApi.list()
    logicalEnvs.value = res.data
    closeModal()
  } catch (err) {
    console.error('Failed to create logical environment', err)
    formError.value = 'Failed to create logical environment. Please try again.'
  } finally {
    submitting.value = false
  }
}

async function confirmDelete(env: LogicalEnvironment) {
  if (!confirm(`Delete logical environment "${env.name}"? This cannot be undone.`)) return
  try {
    await logicalEnvironmentsApi.delete(env.id)
    logicalEnvs.value = logicalEnvs.value.filter(e => e.id !== env.id)
  } catch (err) {
    console.error('Failed to delete logical environment', err)
  }
}

onMounted(async () => {
  try {
    const res = await logicalEnvironmentsApi.list()
    logicalEnvs.value = res.data
  } catch (err) {
    console.error('Failed to load logical environments', err)
  } finally {
    loading.value = false
  }
})
</script>
