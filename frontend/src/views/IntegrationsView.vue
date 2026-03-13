<template>
  <div>
    <div class="flex items-center justify-between mb-6">
      <h1 class="text-2xl font-bold text-gray-900">Integrations</h1>
      <div class="flex items-center gap-3">
        <RouterLink
          to="/integrations/atlassian"
          class="inline-flex items-center gap-1.5 bg-blue-600 text-white px-4 py-2 rounded-md text-sm font-medium hover:bg-blue-700"
        >
          <span>🔗</span> Jira &amp; Confluence
        </RouterLink>
        <RouterLink
          to="/integrations/sso"
          class="inline-flex items-center gap-1.5 bg-purple-600 text-white px-4 py-2 rounded-md text-sm font-medium hover:bg-purple-700"
        >
          <span>🔐</span> SSO / OIDC
        </RouterLink>
        <button
          class="bg-indigo-600 text-white px-4 py-2 rounded-md text-sm font-medium hover:bg-indigo-700"
          @click="showModal = true"
        >
          + New Webhook
        </button>
      </div>
    </div>

    <div v-if="loading" class="text-center text-gray-500 py-12">Loading...</div>
    <div v-else-if="loadError" class="text-center text-red-600 py-12">{{ loadError }}</div>
    <div v-else-if="configs.length === 0" class="text-center text-gray-500 py-12">
      No webhook configurations found. Create one to get started.
    </div>
    <div v-else class="bg-white shadow rounded-lg overflow-hidden">
      <table class="min-w-full divide-y divide-gray-200">
        <thead class="bg-gray-50">
          <tr>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Source</th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Flow ID</th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Status</th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Created</th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Actions</th>
          </tr>
        </thead>
        <tbody class="bg-white divide-y divide-gray-200">
          <tr v-for="config in configs" :key="config.id" class="hover:bg-gray-50">
            <td class="px-6 py-4 text-sm font-medium text-gray-900">
              <span class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium"
                :class="sourceClass(config.source)">
                {{ config.source }}
              </span>
            </td>
            <td class="px-6 py-4 text-sm text-gray-500 font-mono">{{ config.flowId.substring(0, 8) }}...</td>
            <td class="px-6 py-4 text-sm">
              <span class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium"
                :class="config.isActive ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-800'">
                {{ config.isActive ? 'Active' : 'Inactive' }}
              </span>
            </td>
            <td class="px-6 py-4 text-sm text-gray-500">{{ formatDate(config.createdAt) }}</td>
            <td class="px-6 py-4 text-sm space-x-2">
              <button
                class="text-indigo-600 hover:text-indigo-900 font-medium"
                @click="viewDeliveries(config)"
              >Deliveries</button>
              <button
                class="text-red-600 hover:text-red-900 font-medium"
                @click="handleDelete(config.id)"
              >Delete</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="deleteError" class="mt-4 text-sm text-red-600">{{ deleteError }}</div>

    <!-- Deliveries Panel -->
    <div v-if="selectedConfig" class="mt-8">
      <div class="flex items-center justify-between mb-4">
        <h2 class="text-lg font-bold text-gray-900">
          Deliveries for {{ selectedConfig.source }} webhook
        </h2>
        <button
          class="text-sm text-gray-500 hover:text-gray-700"
          @click="selectedConfig = null; deliveries = []"
        >Close</button>
      </div>
      <div v-if="deliveriesLoading" class="text-center text-gray-500 py-8">Loading deliveries...</div>
      <div v-else-if="deliveriesError" class="text-center text-red-600 py-8">{{ deliveriesError }}</div>
      <div v-else-if="deliveries.length === 0" class="text-center text-gray-500 py-8">No deliveries yet.</div>
      <div v-else class="bg-white shadow rounded-lg overflow-hidden">
        <table class="min-w-full divide-y divide-gray-200">
          <thead class="bg-gray-50">
            <tr>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Delivery ID</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Event Type</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Status</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Message</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Received</th>
            </tr>
          </thead>
          <tbody class="bg-white divide-y divide-gray-200">
            <tr v-for="delivery in deliveries" :key="delivery.id" class="hover:bg-gray-50">
              <td class="px-6 py-4 text-sm font-mono text-gray-900">{{ delivery.deliveryId.substring(0, 12) }}...</td>
              <td class="px-6 py-4 text-sm text-gray-500">{{ delivery.eventType || '-' }}</td>
              <td class="px-6 py-4 text-sm">
                <span class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium"
                  :class="deliveryStatusClass(delivery.status)">
                  {{ delivery.status }}
                </span>
              </td>
              <td class="px-6 py-4 text-sm text-gray-500">{{ delivery.statusMessage || '-' }}</td>
              <td class="px-6 py-4 text-sm text-gray-500">{{ formatDate(delivery.receivedAt) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- Create Webhook Modal -->
    <div v-if="showModal" class="fixed inset-0 bg-black bg-opacity-40 flex items-center justify-center z-50">
      <div class="bg-white rounded-lg shadow-xl p-6 w-full max-w-md">
        <h2 class="text-lg font-bold text-gray-900 mb-4">Register Webhook</h2>
        <form @submit.prevent="submitConfig">
          <div class="mb-4">
            <label class="block text-sm font-medium text-gray-700 mb-1">Source</label>
            <select
              v-model="form.source"
              class="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
            >
              <option value="GENERIC">Generic</option>
              <option value="GITHUB">GitHub Actions</option>
              <option value="JENKINS">Jenkins</option>
              <option value="CIRCLECI">CircleCI</option>
              <option value="GITLAB">GitLab CI</option>
            </select>
          </div>
          <div class="mb-4">
            <label class="block text-sm font-medium text-gray-700 mb-1">Flow</label>
            <select
              v-model="form.flowId"
              required
              class="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
            >
              <option value="" disabled>Select a flow...</option>
              <option v-for="flow in flows" :key="flow.id" :value="flow.id">{{ flow.name }}</option>
            </select>
          </div>
          <div class="mb-6">
            <label class="block text-sm font-medium text-gray-700 mb-1">Secret</label>
            <input
              v-model="form.secret"
              type="password"
              required
              placeholder="Webhook signing secret"
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
import { getWebhookConfigs, createWebhookConfig, deleteWebhookConfig, getWebhookDeliveries } from '../api/webhooks'
import { getFlows } from '../api/flows'
import type { WebhookConfig, WebhookDelivery, Flow } from '../types'

const configs = ref<WebhookConfig[]>([])
const flows = ref<Flow[]>([])
const deliveries = ref<WebhookDelivery[]>([])
const selectedConfig = ref<WebhookConfig | null>(null)
const loading = ref(true)
const deliveriesLoading = ref(false)
const showModal = ref(false)
const submitting = ref(false)
const formError = ref('')
const loadError = ref('')
const deleteError = ref('')
const deliveriesError = ref('')

const form = ref({ source: 'GENERIC', flowId: '', secret: '' })

function closeModal() {
  showModal.value = false
  form.value = { source: 'GENERIC', flowId: '', secret: '' }
  formError.value = ''
}

async function submitConfig() {
  submitting.value = true
  formError.value = ''
  try {
    await createWebhookConfig({
      source: form.value.source,
      flowId: form.value.flowId,
      secret: form.value.secret
    })
    const res = await getWebhookConfigs()
    configs.value = res.data
    closeModal()
  } catch {
    formError.value = 'Failed to create webhook config. Please try again.'
  } finally {
    submitting.value = false
  }
}

async function handleDelete(id: string) {
  deleteError.value = ''
  try {
    await deleteWebhookConfig(id)
    configs.value = configs.value.filter(c => c.id !== id)
    if (selectedConfig.value?.id === id) {
      selectedConfig.value = null
      deliveries.value = []
    }
  } catch {
    deleteError.value = 'Failed to delete webhook config. Please try again.'
  }
}

async function viewDeliveries(config: WebhookConfig) {
  selectedConfig.value = config
  deliveriesLoading.value = true
  deliveriesError.value = ''
  try {
    const res = await getWebhookDeliveries(config.id)
    deliveries.value = res.data
  } catch {
    deliveries.value = []
    deliveriesError.value = 'Failed to load deliveries. Please try again.'
  } finally {
    deliveriesLoading.value = false
  }
}

function sourceClass(source: string): string {
  switch (source) {
    case 'GITHUB': return 'bg-gray-900 text-white'
    case 'JENKINS': return 'bg-red-100 text-red-800'
    case 'CIRCLECI': return 'bg-blue-100 text-blue-800'
    case 'GITLAB': return 'bg-orange-100 text-orange-800'
    default: return 'bg-indigo-100 text-indigo-800'
  }
}

function deliveryStatusClass(status: string): string {
  switch (status) {
    case 'SUCCESS': return 'bg-green-100 text-green-800'
    case 'FAILED': return 'bg-red-100 text-red-800'
    case 'DUPLICATE': return 'bg-yellow-100 text-yellow-800'
    default: return 'bg-gray-100 text-gray-800'
  }
}

function formatDate(dateStr: string): string {
  return new Date(dateStr).toLocaleString()
}

onMounted(async () => {
  try {
    const [configsRes, flowsRes] = await Promise.all([getWebhookConfigs(), getFlows()])
    configs.value = configsRes.data
    flows.value = flowsRes.data
  } catch {
    loadError.value = 'Failed to load data. Please refresh the page.'
  } finally {
    loading.value = false
  }
})
</script>
