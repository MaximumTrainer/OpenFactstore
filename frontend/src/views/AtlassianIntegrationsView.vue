<template>
  <div>
    <div class="flex items-center justify-between mb-6">
      <h1 class="text-2xl font-bold text-gray-900">Atlassian Integration</h1>
      <div class="flex items-center gap-2 text-sm text-gray-500">
        <span class="inline-flex items-center gap-1">
          <span :class="jiraHealthClass" class="inline-block w-2 h-2 rounded-full"></span>
          Jira
        </span>
        <span class="inline-flex items-center gap-1">
          <span :class="confluenceHealthClass" class="inline-block w-2 h-2 rounded-full"></span>
          Confluence
        </span>
      </div>
    </div>

    <!-- Tabs -->
    <div class="border-b border-gray-200 mb-6">
      <nav class="-mb-px flex gap-4">
        <button
          v-for="tab in tabs"
          :key="tab.id"
          @click="activeTab = tab.id"
          class="py-2 px-1 text-sm font-medium border-b-2 transition-colors"
          :class="activeTab === tab.id
            ? 'border-indigo-500 text-indigo-600'
            : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'"
        >
          {{ tab.label }}
        </button>
      </nav>
    </div>

    <!-- Jira Configuration Tab -->
    <div v-if="activeTab === 'jira'">
      <div class="bg-white shadow rounded-lg p-6 mb-6">
        <h2 class="text-lg font-semibold text-gray-900 mb-4">Jira Configuration</h2>
        <form @submit.prevent="submitJiraConfig" class="space-y-4">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Jira Base URL</label>
            <input
              v-model="jiraForm.jiraBaseUrl"
              type="url"
              required
              placeholder="https://company.atlassian.net"
              class="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Username</label>
            <input
              v-model="jiraForm.jiraUsername"
              type="email"
              required
              placeholder="user@company.com"
              class="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">API Token</label>
            <input
              v-model="jiraForm.jiraApiToken"
              type="password"
              required
              placeholder="Jira API token"
              class="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Default Project Key</label>
            <input
              v-model="jiraForm.defaultProjectKey"
              type="text"
              required
              placeholder="COMP"
              class="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
          </div>
          <div v-if="jiraFormError" class="text-sm text-red-600">{{ jiraFormError }}</div>
          <div v-if="jiraFormSuccess" class="text-sm text-green-600">{{ jiraFormSuccess }}</div>
          <div class="flex gap-3">
            <button
              type="submit"
              :disabled="jiraSubmitting"
              class="px-4 py-2 text-sm text-white bg-indigo-600 rounded-md hover:bg-indigo-700 disabled:opacity-50"
            >{{ jiraSubmitting ? 'Saving...' : 'Save Configuration' }}</button>
            <button
              type="button"
              :disabled="jiraTesting"
              @click="handleTestJira"
              class="px-4 py-2 text-sm text-gray-700 border border-gray-300 rounded-md hover:bg-gray-50 disabled:opacity-50"
            >{{ jiraTesting ? 'Testing...' : 'Test Connectivity' }}</button>
          </div>
        </form>
        <div v-if="jiraTestResult" class="mt-4 p-3 rounded-md text-sm"
          :class="jiraTestResult.success ? 'bg-green-50 text-green-800' : 'bg-red-50 text-red-800'">
          {{ jiraTestResult.message }}
        </div>
      </div>

      <!-- Jira Tickets Section -->
      <div class="bg-white shadow rounded-lg p-6">
        <div class="flex items-center justify-between mb-4">
          <h2 class="text-lg font-semibold text-gray-900">Jira Tickets</h2>
          <button
            @click="handleSync"
            :disabled="syncing"
            class="px-3 py-1.5 text-sm text-white bg-indigo-600 rounded-md hover:bg-indigo-700 disabled:opacity-50"
          >{{ syncing ? 'Syncing...' : 'Sync to Jira' }}</button>
        </div>
        <div v-if="syncResult" class="mb-4 p-3 rounded-md text-sm bg-blue-50 text-blue-800">
          {{ syncResult.message }}
        </div>
        <div v-if="ticketsLoading" class="text-center text-gray-500 py-8">Loading tickets...</div>
        <div v-else-if="ticketsError" class="text-center text-red-600 py-8">{{ ticketsError }}</div>
        <div v-else-if="tickets.length === 0" class="text-center text-gray-500 py-8">
          No Jira tickets created yet. Tickets are created automatically when compliance events occur.
        </div>
        <div v-else class="overflow-hidden">
          <table class="min-w-full divide-y divide-gray-200">
            <thead class="bg-gray-50">
              <tr>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Key</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Summary</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Type</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Status</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Trail</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Created</th>
              </tr>
            </thead>
            <tbody class="bg-white divide-y divide-gray-200">
              <tr v-for="ticket in tickets" :key="ticket.id" class="hover:bg-gray-50">
                <td class="px-4 py-3 text-sm font-medium text-indigo-600">{{ ticket.ticketKey }}</td>
                <td class="px-4 py-3 text-sm text-gray-900">{{ ticket.summary }}</td>
                <td class="px-4 py-3 text-sm">
                  <span class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-blue-100 text-blue-800">
                    {{ ticket.issueType }}
                  </span>
                </td>
                <td class="px-4 py-3 text-sm">
                  <span class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium"
                    :class="ticketStatusClass(ticket.status)">
                    {{ ticket.status }}
                  </span>
                </td>
                <td class="px-4 py-3 text-sm font-mono text-gray-500">
                  {{ ticket.trailId ? ticket.trailId.substring(0, 8) + '...' : '-' }}
                </td>
                <td class="px-4 py-3 text-sm text-gray-500">{{ formatDate(ticket.createdAt) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- Confluence Configuration Tab -->
    <div v-if="activeTab === 'confluence'">
      <div class="bg-white shadow rounded-lg p-6">
        <h2 class="text-lg font-semibold text-gray-900 mb-4">Confluence Configuration</h2>
        <form @submit.prevent="submitConfluenceConfig" class="space-y-4">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Confluence Base URL</label>
            <input
              v-model="confluenceForm.confluenceBaseUrl"
              type="url"
              required
              placeholder="https://company.atlassian.net"
              class="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Username</label>
            <input
              v-model="confluenceForm.confluenceUsername"
              type="email"
              required
              placeholder="user@company.com"
              class="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">API Token</label>
            <input
              v-model="confluenceForm.confluenceApiToken"
              type="password"
              required
              placeholder="Confluence API token"
              class="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Default Space Key</label>
            <input
              v-model="confluenceForm.defaultSpaceKey"
              type="text"
              required
              placeholder="AUDIT"
              class="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
          </div>
          <div v-if="confluenceFormError" class="text-sm text-red-600">{{ confluenceFormError }}</div>
          <div v-if="confluenceFormSuccess" class="text-sm text-green-600">{{ confluenceFormSuccess }}</div>
          <div class="flex gap-3">
            <button
              type="submit"
              :disabled="confluenceSubmitting"
              class="px-4 py-2 text-sm text-white bg-indigo-600 rounded-md hover:bg-indigo-700 disabled:opacity-50"
            >{{ confluenceSubmitting ? 'Saving...' : 'Save Configuration' }}</button>
            <button
              type="button"
              :disabled="confluenceTesting"
              @click="handleTestConfluence"
              class="px-4 py-2 text-sm text-gray-700 border border-gray-300 rounded-md hover:bg-gray-50 disabled:opacity-50"
            >{{ confluenceTesting ? 'Testing...' : 'Test Connectivity' }}</button>
          </div>
        </form>
        <div v-if="confluenceTestResult" class="mt-4 p-3 rounded-md text-sm"
          :class="confluenceTestResult.success ? 'bg-green-50 text-green-800' : 'bg-red-50 text-red-800'">
          {{ confluenceTestResult.message }}
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import {
  getJiraConfig, saveJiraConfig, testJiraConnectivity, syncToJira, listJiraTickets,
  getConfluenceConfig, saveConfluenceConfig, testConfluenceConnectivity
} from '../api/atlassian'
import type { JiraTicket, ConnectionTestResponse, JiraSyncResponse } from '../types'

const tabs = [
  { id: 'jira', label: 'Jira' },
  { id: 'confluence', label: 'Confluence' }
]
const activeTab = ref('jira')

// Health indicators
const jiraHealthClass = ref('bg-gray-300')
const confluenceHealthClass = ref('bg-gray-300')

// Jira state
const jiraForm = ref({
  jiraBaseUrl: '',
  jiraUsername: '',
  jiraApiToken: '',
  defaultProjectKey: ''
})
const jiraSubmitting = ref(false)
const jiraTesting = ref(false)
const jiraFormError = ref('')
const jiraFormSuccess = ref('')
const jiraTestResult = ref<ConnectionTestResponse | null>(null)

// Jira tickets state
const tickets = ref<JiraTicket[]>([])
const ticketsLoading = ref(false)
const ticketsError = ref('')
const syncing = ref(false)
const syncResult = ref<JiraSyncResponse | null>(null)

// Confluence state
const confluenceForm = ref({
  confluenceBaseUrl: '',
  confluenceUsername: '',
  confluenceApiToken: '',
  defaultSpaceKey: ''
})
const confluenceSubmitting = ref(false)
const confluenceTesting = ref(false)
const confluenceFormError = ref('')
const confluenceFormSuccess = ref('')
const confluenceTestResult = ref<ConnectionTestResponse | null>(null)

async function submitJiraConfig() {
  jiraSubmitting.value = true
  jiraFormError.value = ''
  jiraFormSuccess.value = ''
  try {
    const resp = await saveJiraConfig(jiraForm.value)
    jiraForm.value.jiraBaseUrl = resp.data.jiraBaseUrl
    jiraForm.value.jiraUsername = resp.data.jiraUsername
    jiraForm.value.defaultProjectKey = resp.data.defaultProjectKey
    jiraFormSuccess.value = 'Jira configuration saved successfully.'
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : String(err)
    jiraFormError.value = `Failed to save Jira configuration: ${msg}`
  } finally {
    jiraSubmitting.value = false
  }
}

async function handleTestJira() {
  jiraTesting.value = true
  jiraTestResult.value = null
  try {
    const resp = await testJiraConnectivity()
    jiraTestResult.value = resp.data
    jiraHealthClass.value = resp.data.success ? 'bg-green-500' : 'bg-red-500'
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : String(err)
    jiraTestResult.value = { success: false, message: `Failed to perform connectivity test: ${msg}` }
    jiraHealthClass.value = 'bg-red-500'
  } finally {
    jiraTesting.value = false
  }
}

async function handleSync() {
  syncing.value = true
  syncResult.value = null
  try {
    const resp = await syncToJira()
    syncResult.value = resp.data
    await loadTickets()
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : String(err)
    syncResult.value = { syncedCount: 0, message: `Sync failed: ${msg}` }
  } finally {
    syncing.value = false
  }
}

async function loadTickets() {
  ticketsLoading.value = true
  ticketsError.value = ''
  try {
    const resp = await listJiraTickets()
    tickets.value = resp.data
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : String(err)
    ticketsError.value = `Failed to load Jira tickets: ${msg}`
  } finally {
    ticketsLoading.value = false
  }
}

async function submitConfluenceConfig() {
  confluenceSubmitting.value = true
  confluenceFormError.value = ''
  confluenceFormSuccess.value = ''
  try {
    const resp = await saveConfluenceConfig(confluenceForm.value)
    confluenceForm.value.confluenceBaseUrl = resp.data.confluenceBaseUrl
    confluenceForm.value.confluenceUsername = resp.data.confluenceUsername
    confluenceForm.value.defaultSpaceKey = resp.data.defaultSpaceKey
    confluenceFormSuccess.value = 'Confluence configuration saved successfully.'
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : String(err)
    confluenceFormError.value = `Failed to save Confluence configuration: ${msg}`
  } finally {
    confluenceSubmitting.value = false
  }
}

async function handleTestConfluence() {
  confluenceTesting.value = true
  confluenceTestResult.value = null
  try {
    const resp = await testConfluenceConnectivity()
    confluenceTestResult.value = resp.data
    confluenceHealthClass.value = resp.data.success ? 'bg-green-500' : 'bg-red-500'
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : String(err)
    confluenceTestResult.value = { success: false, message: `Failed to perform connectivity test: ${msg}` }
    confluenceHealthClass.value = 'bg-red-500'
  } finally {
    confluenceTesting.value = false
  }
}

function ticketStatusClass(status: string): string {
  switch (status.toLowerCase()) {
    case 'open': return 'bg-blue-100 text-blue-800'
    case 'in progress': return 'bg-yellow-100 text-yellow-800'
    case 'done': return 'bg-green-100 text-green-800'
    case 'pending': return 'bg-gray-100 text-gray-600'
    default: return 'bg-gray-100 text-gray-800'
  }
}

function formatDate(dateStr: string): string {
  return new Date(dateStr).toLocaleString()
}

onMounted(async () => {
  // Load existing Jira config if available
  try {
    const resp = await getJiraConfig()
    jiraForm.value.jiraBaseUrl = resp.data.jiraBaseUrl
    jiraForm.value.jiraUsername = resp.data.jiraUsername
    jiraForm.value.defaultProjectKey = resp.data.defaultProjectKey
  } catch {
    // No config yet — form stays empty
  }

  // Load existing Confluence config if available
  try {
    const resp = await getConfluenceConfig()
    confluenceForm.value.confluenceBaseUrl = resp.data.confluenceBaseUrl
    confluenceForm.value.confluenceUsername = resp.data.confluenceUsername
    confluenceForm.value.defaultSpaceKey = resp.data.defaultSpaceKey
  } catch {
    // No config yet — form stays empty
  }

  await loadTickets()
})
</script>
