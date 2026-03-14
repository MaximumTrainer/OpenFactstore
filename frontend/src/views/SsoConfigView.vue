<script setup lang="ts">
import { ref, onMounted } from 'vue'
import {
  getSsoConfig,
  createSsoConfig,
  updateSsoConfig,
  deleteSsoConfig,
  testSsoConnection,
} from '../api/sso'
import type { SsoConfig, SsoProvider, SsoTestConnectionResponse } from '../types'

const orgSlug = ref('default')

const config = ref<SsoConfig | null>(null)
const loading = ref(false)
const loadError = ref('')
const saving = ref(false)
const saveError = ref('')
const saveSuccess = ref('')
const deleteError = ref('')
const testResult = ref<SsoTestConnectionResponse | null>(null)
const testing = ref(false)
const showForm = ref(false)
const isEditing = ref(false)

// Form fields
const provider = ref<SsoProvider>('OKTA')
const issuerUrl = ref('')
const clientId = ref('')
const clientSecret = ref('')
const attributeMappings = ref('{"email":"email","name":"name"}')
const groupRoleMappings = ref('{}')
const isMandatory = ref(false)

const providers: { value: SsoProvider; label: string }[] = [
  { value: 'OKTA', label: 'Okta' },
  { value: 'ENTRA_ID', label: 'Microsoft Entra ID (Azure AD)' },
]

function resetForm() {
  provider.value = 'OKTA'
  issuerUrl.value = ''
  clientId.value = ''
  clientSecret.value = ''
  attributeMappings.value = '{"email":"email","name":"name"}'
  groupRoleMappings.value = '{}'
  isMandatory.value = false
  saveError.value = ''
  saveSuccess.value = ''
}

function populateForm(c: SsoConfig) {
  provider.value = c.provider
  issuerUrl.value = c.issuerUrl
  clientId.value = c.clientId
  clientSecret.value = ''
  attributeMappings.value = c.attributeMappings
  groupRoleMappings.value = c.groupRoleMappings
  isMandatory.value = c.isMandatory
}

async function loadConfig() {
  loading.value = true
  loadError.value = ''
  try {
    const res = await getSsoConfig(orgSlug.value)
    config.value = res.data
  } catch (err: unknown) {
    const e = err as { response?: { status?: number } }
    if (e.response?.status === 404) {
      config.value = null
    } else {
      loadError.value = 'Failed to load SSO configuration.'
    }
  } finally {
    loading.value = false
  }
}

function startCreate() {
  isEditing.value = false
  resetForm()
  showForm.value = true
  testResult.value = null
}

function startEdit() {
  if (!config.value) return
  isEditing.value = true
  populateForm(config.value)
  showForm.value = true
  testResult.value = null
}

async function handleSave() {
  saving.value = true
  saveError.value = ''
  saveSuccess.value = ''
  try {
    if (isEditing.value) {
      const payload = {
        provider: provider.value,
        issuerUrl: issuerUrl.value,
        clientId: clientId.value,
        ...(clientSecret.value ? { clientSecret: clientSecret.value } : {}),
        attributeMappings: attributeMappings.value,
        groupRoleMappings: groupRoleMappings.value,
        isMandatory: isMandatory.value,
      }
      const res = await updateSsoConfig(orgSlug.value, payload)
      config.value = res.data
      saveSuccess.value = 'SSO configuration updated successfully.'
    } else {
      const res = await createSsoConfig(orgSlug.value, {
        provider: provider.value,
        issuerUrl: issuerUrl.value,
        clientId: clientId.value,
        ...(clientSecret.value ? { clientSecret: clientSecret.value } : {}),
        attributeMappings: attributeMappings.value,
        groupRoleMappings: groupRoleMappings.value,
        isMandatory: isMandatory.value,
      })
      config.value = res.data
      saveSuccess.value = 'SSO configuration created successfully.'
    }
    showForm.value = false
  } catch (err: unknown) {
    const e = err as { response?: { data?: { message?: string } } }
    saveError.value = e.response?.data?.message ?? 'Failed to save SSO configuration.'
  } finally {
    saving.value = false
  }
}

async function handleDelete() {
  if (!confirm('Delete the SSO configuration for this organisation? Users will no longer be able to log in via SSO.')) return
  deleteError.value = ''
  try {
    await deleteSsoConfig(orgSlug.value)
    config.value = null
    showForm.value = false
  } catch {
    deleteError.value = 'Failed to delete SSO configuration.'
  }
}

async function handleTest() {
  testing.value = true
  testResult.value = null
  try {
    const res = await testSsoConnection(orgSlug.value)
    testResult.value = res.data
  } catch {
    testResult.value = { success: false, message: 'Test request failed.' }
  } finally {
    testing.value = false
  }
}

function formatDate(iso: string) {
  return new Date(iso).toLocaleString()
}

onMounted(loadConfig)
</script>

<template>
  <div>
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-2xl font-bold text-gray-900">SSO Configuration</h1>
        <p class="mt-1 text-sm text-gray-500">
          Configure OIDC Single Sign-On for your organisation (Microsoft Entra ID or Okta).
        </p>
      </div>
      <div class="flex items-center gap-3">
        <label class="text-sm text-gray-600 font-medium">Organisation slug:</label>
        <input
          v-model="orgSlug"
          class="border border-gray-300 rounded-md px-3 py-1.5 text-sm"
          placeholder="org-slug"
          @change="loadConfig"
        />
        <button
          v-if="!config"
          class="bg-indigo-600 text-white px-4 py-2 rounded-md text-sm font-medium hover:bg-indigo-700"
          @click="startCreate"
        >
          + Configure SSO
        </button>
      </div>
    </div>

    <div v-if="loading" class="text-center text-gray-500 py-12">Loading...</div>
    <div v-else-if="loadError" class="text-center text-red-600 py-12">{{ loadError }}</div>

    <!-- Existing config card -->
    <div v-else-if="config && !showForm" class="bg-white shadow rounded-lg divide-y divide-gray-200">
      <div class="px-6 py-4 flex items-center justify-between">
        <h2 class="text-lg font-semibold text-gray-900">Current Configuration</h2>
        <div class="flex gap-2">
          <button
            class="text-sm text-indigo-600 hover:text-indigo-900 font-medium"
            @click="startEdit"
          >Edit</button>
          <button
            class="text-sm text-red-600 hover:text-red-900 font-medium"
            @click="handleDelete"
          >Delete</button>
        </div>
      </div>
      <div class="px-6 py-4 grid grid-cols-1 sm:grid-cols-2 gap-4 text-sm">
        <div>
          <span class="font-medium text-gray-500">Provider</span>
          <p class="mt-1 text-gray-900">
            {{ config.provider === 'ENTRA_ID' ? 'Microsoft Entra ID' : 'Okta' }}
          </p>
        </div>
        <div>
          <span class="font-medium text-gray-500">Issuer URL</span>
          <p class="mt-1 text-gray-900 break-all">{{ config.issuerUrl }}</p>
        </div>
        <div>
          <span class="font-medium text-gray-500">Client ID</span>
          <p class="mt-1 text-gray-900 font-mono">{{ config.clientId }}</p>
        </div>
        <div>
          <span class="font-medium text-gray-500">Mandatory SSO</span>
          <p class="mt-1">
            <span
              class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium"
              :class="config.isMandatory ? 'bg-red-100 text-red-800' : 'bg-gray-100 text-gray-600'"
            >
              {{ config.isMandatory ? 'Enforced' : 'Optional' }}
            </span>
          </p>
        </div>
        <div>
          <span class="font-medium text-gray-500">Attribute Mappings</span>
          <pre class="mt-1 text-xs text-gray-700 bg-gray-50 rounded p-2 overflow-auto">{{ config.attributeMappings }}</pre>
        </div>
        <div>
          <span class="font-medium text-gray-500">Group → Role Mappings</span>
          <pre class="mt-1 text-xs text-gray-700 bg-gray-50 rounded p-2 overflow-auto">{{ config.groupRoleMappings }}</pre>
        </div>
        <div>
          <span class="font-medium text-gray-500">Created</span>
          <p class="mt-1 text-gray-700">{{ formatDate(config.createdAt) }}</p>
        </div>
        <div>
          <span class="font-medium text-gray-500">Updated</span>
          <p class="mt-1 text-gray-700">{{ formatDate(config.updatedAt) }}</p>
        </div>
      </div>
      <!-- Test connection -->
      <div class="px-6 py-4">
        <button
          class="bg-green-600 text-white px-4 py-2 rounded-md text-sm font-medium hover:bg-green-700 disabled:opacity-50"
          :disabled="testing"
          @click="handleTest"
        >
          {{ testing ? 'Testing…' : 'Test Connection' }}
        </button>
        <div v-if="testResult" class="mt-3">
          <div
            class="flex items-start gap-2 p-3 rounded-md text-sm"
            :class="testResult.success ? 'bg-green-50 text-green-800' : 'bg-red-50 text-red-800'"
          >
            <span>{{ testResult.success ? '✅' : '❌' }}</span>
            <div>
              <p class="font-medium">{{ testResult.message }}</p>
              <p v-if="testResult.authorizationEndpoint" class="mt-1 text-xs break-all">
                Authorization endpoint: {{ testResult.authorizationEndpoint }}
              </p>
              <p v-if="testResult.tokenEndpoint" class="mt-1 text-xs break-all">
                Token endpoint: {{ testResult.tokenEndpoint }}
              </p>
            </div>
          </div>
        </div>
        <p v-if="deleteError" class="mt-2 text-sm text-red-600">{{ deleteError }}</p>
      </div>
    </div>

    <!-- No config & no form -->
    <div v-else-if="!config && !showForm" class="text-center text-gray-500 py-12">
      No SSO configuration found for <strong>{{ orgSlug }}</strong>. Click <em>Configure SSO</em> to get started.
    </div>

    <!-- Create / Edit form -->
    <div v-if="showForm" class="bg-white shadow rounded-lg p-6 mt-4">
      <h2 class="text-lg font-semibold text-gray-900 mb-4">
        {{ isEditing ? 'Edit SSO Configuration' : 'New SSO Configuration' }}
      </h2>
      <form class="space-y-4" @submit.prevent="handleSave">
        <!-- Provider -->
        <div>
          <label class="block text-sm font-medium text-gray-700">Identity Provider</label>
          <select
            v-model="provider"
            class="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:ring-indigo-500 focus:border-indigo-500"
          >
            <option v-for="p in providers" :key="p.value" :value="p.value">{{ p.label }}</option>
          </select>
        </div>

        <!-- Issuer URL -->
        <div>
          <label class="block text-sm font-medium text-gray-700">Issuer URL</label>
          <p class="text-xs text-gray-500 mt-0.5">
            For Okta: <code>https://&lt;your-domain&gt;.okta.com/oauth2/default</code><br/>
            For Entra ID: <code>https://login.microsoftonline.com/&lt;tenantId&gt;/v2.0</code>
          </p>
          <input
            v-model="issuerUrl"
            type="url"
            required
            class="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:ring-indigo-500 focus:border-indigo-500"
            placeholder="https://..."
          />
        </div>

        <!-- Client ID -->
        <div>
          <label class="block text-sm font-medium text-gray-700">Client ID</label>
          <input
            v-model="clientId"
            type="text"
            required
            class="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:ring-indigo-500 focus:border-indigo-500"
          />
        </div>

        <!-- Client Secret -->
        <div>
          <label class="block text-sm font-medium text-gray-700">
            Client Secret
            <span v-if="isEditing" class="text-gray-400 font-normal">(leave blank to keep existing)</span>
          </label>
          <input
            v-model="clientSecret"
            type="password"
            :required="!isEditing"
            class="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:ring-indigo-500 focus:border-indigo-500"
          />
        </div>

        <!-- Attribute Mappings -->
        <div>
          <label class="block text-sm font-medium text-gray-700">Attribute Mappings (JSON)</label>
          <p class="text-xs text-gray-500 mt-0.5">
            Keys are Factstore field names (<code>email</code>, <code>name</code>, <code>role</code>);
            values are the corresponding claim names in the IdP's ID token.
            Example: <code>{"email":"email","name":"name","role":"groups"}</code>
          </p>
          <textarea
            v-model="attributeMappings"
            rows="2"
            class="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2 text-sm font-mono focus:ring-indigo-500 focus:border-indigo-500"
          />
        </div>

        <!-- Group → Role Mappings -->
        <div>
          <label class="block text-sm font-medium text-gray-700">Group → Role Mappings (JSON)</label>
          <p class="text-xs text-gray-500 mt-0.5">
            Maps IdP group names to Factstore roles (<code>ADMIN</code>, <code>MEMBER</code>, <code>VIEWER</code>).
            Example: <code>{"admins":"ADMIN","developers":"MEMBER"}</code>
          </p>
          <textarea
            v-model="groupRoleMappings"
            rows="2"
            class="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2 text-sm font-mono focus:ring-indigo-500 focus:border-indigo-500"
          />
        </div>

        <!-- Mandatory SSO -->
        <div class="flex items-center gap-3">
          <input
            id="mandatory"
            v-model="isMandatory"
            type="checkbox"
            class="h-4 w-4 text-indigo-600 border-gray-300 rounded"
          />
          <label for="mandatory" class="text-sm font-medium text-gray-700">
            Enforce mandatory SSO
            <span class="text-gray-400 font-normal">(disables password login for this organisation)</span>
          </label>
        </div>

        <div v-if="saveError" class="text-sm text-red-600">{{ saveError }}</div>
        <div v-if="saveSuccess" class="text-sm text-green-600">{{ saveSuccess }}</div>

        <div class="flex gap-3 pt-2">
          <button
            type="submit"
            :disabled="saving"
            class="bg-indigo-600 text-white px-5 py-2 rounded-md text-sm font-medium hover:bg-indigo-700 disabled:opacity-50"
          >
            {{ saving ? 'Saving…' : isEditing ? 'Update' : 'Create' }}
          </button>
          <button
            type="button"
            class="border border-gray-300 text-gray-700 px-5 py-2 rounded-md text-sm font-medium hover:bg-gray-50"
            @click="showForm = false; resetForm()"
          >
            Cancel
          </button>
        </div>
      </form>
    </div>
  </div>
</template>
