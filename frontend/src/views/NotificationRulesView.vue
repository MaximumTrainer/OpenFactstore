<template>
  <div class="max-w-5xl mx-auto px-4 py-8">
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-2xl font-bold text-gray-900">Notification Rules</h1>
        <p class="text-sm text-gray-500 mt-1">Configure when and how compliance alerts are sent.</p>
      </div>
      <button
        @click="showCreateModal = true"
        class="bg-indigo-600 text-white px-4 py-2 rounded-md text-sm font-medium hover:bg-indigo-700 transition-colors"
      >+ New Rule</button>
    </div>

    <!-- Status banner -->
    <div
      v-if="statusMessage"
      :class="statusError ? 'bg-red-50 border-red-300 text-red-700' : 'bg-green-50 border-green-300 text-green-700'"
      class="mb-4 px-4 py-3 rounded-md border text-sm font-medium flex items-center justify-between"
    >
      {{ statusMessage }}
      <button @click="statusMessage = ''" class="ml-4 text-current opacity-60 hover:opacity-100">✕</button>
    </div>

    <!-- Rules table -->
    <div v-if="loading" class="text-center py-12 text-gray-400">Loading…</div>
    <div v-else-if="rules.length === 0" class="text-center py-12 text-gray-400">
      No notification rules yet. Create one to get started.
    </div>
    <div v-else class="bg-white rounded-lg shadow overflow-hidden">
      <table class="min-w-full divide-y divide-gray-200">
        <thead class="bg-gray-50">
          <tr>
            <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Name</th>
            <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Event</th>
            <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Channel</th>
            <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Status</th>
            <th class="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Actions</th>
          </tr>
        </thead>
        <tbody class="bg-white divide-y divide-gray-100">
          <tr v-for="rule in rules" :key="rule.id" class="hover:bg-gray-50">
            <td class="px-4 py-3 text-sm font-medium text-gray-900">{{ rule.name }}</td>
            <td class="px-4 py-3 text-sm text-gray-600">
              <span class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-orange-100 text-orange-800">
                {{ formatEvent(rule.triggerEvent) }}
              </span>
            </td>
            <td class="px-4 py-3 text-sm text-gray-600">
              <span :class="channelBadge(rule.channelType)" class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium">
                {{ rule.channelType }}
              </span>
            </td>
            <td class="px-4 py-3 text-sm">
              <span
                :class="rule.isActive ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-500'"
                class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium"
              >{{ rule.isActive ? 'Active' : 'Inactive' }}</span>
            </td>
            <td class="px-4 py-3 text-right text-sm">
              <button @click="openDeliveries(rule)" class="text-indigo-600 hover:text-indigo-800 mr-3 font-medium">History</button>
              <button @click="sendTest(rule)" class="text-blue-600 hover:text-blue-800 mr-3 font-medium">Test</button>
              <button @click="toggleActive(rule)" :class="rule.isActive ? 'text-yellow-600 hover:text-yellow-800' : 'text-green-600 hover:text-green-800'" class="mr-3 font-medium">
                {{ rule.isActive ? 'Disable' : 'Enable' }}
              </button>
              <button @click="confirmDelete(rule)" class="text-red-600 hover:text-red-800 font-medium">Delete</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Create Rule Modal -->
    <div v-if="showCreateModal" class="fixed inset-0 bg-black bg-opacity-40 flex items-center justify-center z-50">
      <div class="bg-white rounded-xl shadow-xl w-full max-w-lg mx-4 p-6">
        <h2 class="text-lg font-semibold text-gray-900 mb-4">New Notification Rule</h2>
        <form @submit.prevent="createRule" class="space-y-4">
          <div>
            <label class="block text-sm font-medium text-gray-700">Name</label>
            <input v-model="form.name" required type="text"
              class="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500" />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700">Trigger Event</label>
            <select v-model="form.triggerEvent" required
              class="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500">
              <option v-for="e in triggerEvents" :key="e" :value="e">{{ formatEvent(e) }}</option>
            </select>
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700">Channel Type</label>
            <select v-model="form.channelType" required
              class="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500">
              <option value="IN_APP">In-App</option>
              <option value="SLACK">Slack Webhook</option>
              <option value="WEBHOOK">Generic Webhook</option>
            </select>
          </div>
          <div v-if="form.channelType === 'SLACK' || form.channelType === 'WEBHOOK'">
            <label class="block text-sm font-medium text-gray-700">
              Webhook URL <span class="text-red-500">*</span>
            </label>
            <input v-model="webhookUrl" type="url" placeholder="https://..." required
              class="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500" />
            <p class="mt-1 text-xs text-gray-400">Must be a public HTTPS URL (private/internal addresses are blocked).</p>
          </div>
          <div class="flex justify-end gap-3 pt-2">
            <button type="button" @click="showCreateModal = false"
              class="px-4 py-2 text-sm font-medium text-gray-700 bg-gray-100 rounded-md hover:bg-gray-200">
              Cancel
            </button>
            <button type="submit" :disabled="saving || isSubmitDisabled"
              class="px-4 py-2 text-sm font-medium text-white bg-indigo-600 rounded-md hover:bg-indigo-700 disabled:opacity-50">
              {{ saving ? 'Creating…' : 'Create' }}
            </button>
          </div>
        </form>
      </div>
    </div>

    <!-- Delivery History Modal -->
    <div v-if="showDeliveriesModal" class="fixed inset-0 bg-black bg-opacity-40 flex items-center justify-center z-50">
      <div class="bg-white rounded-xl shadow-xl w-full max-w-2xl mx-4 p-6">
        <div class="flex items-center justify-between mb-4">
          <h2 class="text-lg font-semibold text-gray-900">Delivery History — {{ selectedRule?.name }}</h2>
          <button @click="showDeliveriesModal = false" class="text-gray-400 hover:text-gray-600 text-xl font-bold">×</button>
        </div>
        <div v-if="deliveries.length === 0" class="text-center py-6 text-gray-400 text-sm">No deliveries yet.</div>
        <div v-else class="overflow-y-auto max-h-96">
          <table class="min-w-full divide-y divide-gray-200 text-sm">
            <thead class="bg-gray-50">
              <tr>
                <th class="px-3 py-2 text-left text-xs text-gray-500 uppercase">Event</th>
                <th class="px-3 py-2 text-left text-xs text-gray-500 uppercase">Status</th>
                <th class="px-3 py-2 text-left text-xs text-gray-500 uppercase">Attempts</th>
                <th class="px-3 py-2 text-left text-xs text-gray-500 uppercase">Sent At</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-gray-100">
              <tr v-for="d in deliveries" :key="d.id">
                <td class="px-3 py-2 text-gray-700">{{ formatEvent(d.eventType) }}</td>
                <td class="px-3 py-2">
                  <span :class="deliveryStatusBadge(d.status)" class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium">
                    {{ d.status }}
                  </span>
                </td>
                <td class="px-3 py-2 text-gray-600">{{ d.attemptCount }}</td>
                <td class="px-3 py-2 text-gray-500">{{ new Date(d.sentAt).toLocaleString() }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="flex justify-end mt-4">
          <button @click="showDeliveriesModal = false"
            class="px-4 py-2 text-sm font-medium text-gray-700 bg-gray-100 rounded-md hover:bg-gray-200">
            Close
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { notificationRulesApi } from '../api/notificationRules'
import type { NotificationRule, NotificationDelivery, TriggerEvent, ChannelType } from '../types'

const loading = ref(true)
const rules = ref<NotificationRule[]>([])
const saving = ref(false)
const showCreateModal = ref(false)
const showDeliveriesModal = ref(false)
const selectedRule = ref<NotificationRule | null>(null)
const deliveries = ref<NotificationDelivery[]>([])
const webhookUrl = ref('')
const statusMessage = ref('')
const statusError = ref(false)

function showStatus(message: string, isError: boolean) {
  statusMessage.value = message
  statusError.value = isError
  setTimeout(() => { statusMessage.value = '' }, 4000)
}

const triggerEvents: TriggerEvent[] = [
  'ATTESTATION_FAILED',
  'GATE_BLOCKED',
  'DRIFT_DETECTED',
  'APPROVAL_REQUIRED',
  'TRAIL_NON_COMPLIANT',
  'APPROVAL_REJECTED'
]

const form = ref<{
  name: string
  triggerEvent: TriggerEvent
  channelType: ChannelType
}>({
  name: '',
  triggerEvent: 'TRAIL_NON_COMPLIANT',
  channelType: 'IN_APP'
})

const isSubmitDisabled = computed(() => {
  if (form.value.channelType === 'SLACK' || form.value.channelType === 'WEBHOOK') {
    return !webhookUrl.value.trim()
  }
  return false
})

async function loadRules() {
  loading.value = true
  try {
    const res = await notificationRulesApi.list()
    rules.value = res.data
  } catch {
    // ignore
  } finally {
    loading.value = false
  }
}

async function createRule() {
  saving.value = true
  try {
    let channelConfig = '{}'
    if ((form.value.channelType === 'SLACK' || form.value.channelType === 'WEBHOOK') && webhookUrl.value) {
      channelConfig = JSON.stringify({ webhookUrl: webhookUrl.value })
    }
    await notificationRulesApi.create({
      name: form.value.name,
      triggerEvent: form.value.triggerEvent,
      channelType: form.value.channelType,
      channelConfig
    })
    showCreateModal.value = false
    form.value = { name: '', triggerEvent: 'TRAIL_NON_COMPLIANT', channelType: 'IN_APP' }
    webhookUrl.value = ''
    await loadRules()
  } catch {
    // ignore
  } finally {
    saving.value = false
  }
}

async function toggleActive(rule: NotificationRule) {
  try {
    await notificationRulesApi.update(rule.id, { isActive: !rule.isActive })
    await loadRules()
  } catch {
    // ignore
  }
}

async function sendTest(rule: NotificationRule) {
  try {
    await notificationRulesApi.test(rule.id)
    showStatus(`Test notification sent for rule "${rule.name}"`, false)
  } catch {
    showStatus('Failed to send test notification', true)
  }
}

async function openDeliveries(rule: NotificationRule) {
  selectedRule.value = rule
  try {
    const res = await notificationRulesApi.getDeliveries(rule.id)
    deliveries.value = res.data
  } catch {
    deliveries.value = []
  }
  showDeliveriesModal.value = true
}

async function confirmDelete(rule: NotificationRule) {
  if (!confirm(`Delete rule "${rule.name}"?`)) return
  try {
    await notificationRulesApi.delete(rule.id)
    await loadRules()
  } catch {
    // ignore
  }
}

function formatEvent(event: string): string {
  return event.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, c => c.toUpperCase())
}

function channelBadge(type: string): string {
  switch (type) {
    case 'SLACK': return 'bg-green-100 text-green-800'
    case 'WEBHOOK': return 'bg-blue-100 text-blue-800'
    default: return 'bg-gray-100 text-gray-800'
  }
}

function deliveryStatusBadge(status: string): string {
  switch (status) {
    case 'SENT': return 'bg-green-100 text-green-800'
    case 'FAILED': return 'bg-red-100 text-red-800'
    default: return 'bg-gray-100 text-gray-600'
  }
}

onMounted(loadRules)
</script>
