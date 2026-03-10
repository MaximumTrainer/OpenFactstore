<template>
  <div>
    <h1 class="text-2xl font-bold text-gray-900 mb-6">Assert Compliance</h1>

    <div class="bg-white rounded-lg shadow p-6 max-w-lg">
      <form @submit.prevent="submit">
        <div class="mb-4">
          <label class="block text-sm font-medium text-gray-700 mb-1">SHA256 Digest</label>
          <input
            v-model="form.sha256Digest"
            type="text"
            required
            placeholder="sha256:abc123..."
            class="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
          />
        </div>
        <div class="mb-6">
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
        <button
          type="submit"
          :disabled="submitting"
          class="w-full bg-indigo-600 text-white px-4 py-2 rounded-md text-sm font-medium hover:bg-indigo-700 disabled:opacity-50"
        >{{ submitting ? 'Asserting...' : 'Assert Compliance' }}</button>
      </form>

      <div v-if="result" class="mt-6 p-4 rounded-md" :class="result.compliant ? 'bg-green-50 border border-green-200' : 'bg-red-50 border border-red-200'">
        <div class="flex items-center gap-2 mb-2">
          <span class="text-lg font-bold" :class="result.compliant ? 'text-green-800' : 'text-red-800'">
            {{ result.compliant ? '✓ COMPLIANT' : '✗ NON_COMPLIANT' }}
          </span>
        </div>
        <p class="text-sm" :class="result.compliant ? 'text-green-700' : 'text-red-700'">{{ result.message }}</p>
        <div v-if="result.missingAttestations.length" class="mt-3">
          <div class="text-sm font-medium text-red-700">Missing Attestations:</div>
          <ul class="mt-1 text-sm text-red-600 list-disc list-inside">
            <li v-for="m in result.missingAttestations" :key="m">{{ m }}</li>
          </ul>
        </div>
        <div v-if="result.failedAttestations.length" class="mt-3">
          <div class="text-sm font-medium text-red-700">Failed Attestations:</div>
          <ul class="mt-1 text-sm text-red-600 list-disc list-inside">
            <li v-for="f in result.failedAttestations" :key="f">{{ f }}</li>
          </ul>
        </div>
      </div>

      <div v-if="error" class="mt-4 text-sm text-red-600">{{ error }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getFlows } from '../api/flows'
import { assertCompliance } from '../api/assert'
import type { Flow, AssertResult } from '../types'

const flows = ref<Flow[]>([])
const form = ref({ sha256Digest: '', flowId: '' })
const result = ref<AssertResult | null>(null)
const error = ref('')
const submitting = ref(false)

async function submit() {
  submitting.value = true
  result.value = null
  error.value = ''
  try {
    const res = await assertCompliance(form.value.sha256Digest, form.value.flowId)
    result.value = res.data
  } catch {
    error.value = 'Failed to assert compliance. Please check your inputs and try again.'
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  try {
    const res = await getFlows()
    flows.value = res.data
  } catch {
    // silently fail
  }
})
</script>
