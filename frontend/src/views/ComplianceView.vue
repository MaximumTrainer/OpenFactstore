<template>
  <div>
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-2xl font-bold text-gray-900">Compliance Frameworks</h1>
        <p class="mt-1 text-sm text-gray-500">Browse and import compliance framework templates as flows.</p>
      </div>
    </div>

    <div v-if="loading" class="text-center text-gray-500 py-12">Loading...</div>
    <div v-else-if="loadError" class="text-center text-red-600 py-12">{{ loadError }}</div>
    <div v-else-if="templates.length === 0" class="text-center text-gray-500 py-12">No compliance frameworks found.</div>
    <div v-else class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
      <div
        v-for="tpl in templates"
        :key="tpl.id"
        class="bg-white rounded-lg shadow p-6 flex flex-col"
      >
        <div class="flex items-start justify-between mb-3">
          <span :class="frameworkBadgeClass(tpl.framework)" class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium">
            {{ tpl.framework }}
          </span>
          <span class="text-xs text-gray-400">v{{ tpl.version }}</span>
        </div>
        <h2 class="text-base font-semibold text-gray-900 mb-2">{{ tpl.name }}</h2>
        <p class="text-sm text-gray-500 flex-1 mb-4">{{ tpl.description }}</p>
        <div
          v-if="importStatus[tpl.id]"
          class="mb-3 text-sm"
          :class="importStatus[tpl.id] === 'ok' ? 'text-green-600' : 'text-red-600'"
        >
          {{ importStatus[tpl.id] === 'ok' ? '✓ Imported as flow' : '✗ Import failed' }}
        </div>
        <button
          :disabled="importing[tpl.id]"
          class="w-full bg-indigo-600 text-white px-4 py-2 rounded-md text-sm font-medium hover:bg-indigo-700 disabled:opacity-50"
          @click="doImport(tpl.id)"
        >{{ importing[tpl.id] ? 'Importing...' : 'Import as Flow' }}</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { listTemplates, importTemplate } from '../api/hub'
import type { HubTemplate } from '../api/hub'

const templates = ref<HubTemplate[]>([])
const loading = ref(true)
const loadError = ref('')
const importing = ref<Record<string, boolean>>({})
const importStatus = ref<Record<string, 'ok' | 'error'>>({})

function frameworkBadgeClass(framework: string): string {
  if (framework.startsWith('PCI')) return 'bg-red-100 text-red-800'
  if (framework.startsWith('SOX')) return 'bg-yellow-100 text-yellow-800'
  if (framework.startsWith('GDPR')) return 'bg-blue-100 text-blue-800'
  if (framework.startsWith('SLSA')) return 'bg-green-100 text-green-800'
  return 'bg-gray-100 text-gray-800'
}

async function doImport(id: string) {
  importing.value = { ...importing.value, [id]: true }
  delete importStatus.value[id]
  try {
    await importTemplate(id)
    importStatus.value = { ...importStatus.value, [id]: 'ok' }
  } catch {
    importStatus.value = { ...importStatus.value, [id]: 'error' }
  } finally {
    importing.value = { ...importing.value, [id]: false }
  }
}

onMounted(async () => {
  try {
    const res = await listTemplates()
    templates.value = res.data
  } catch {
    loadError.value = 'Failed to load compliance frameworks.'
  } finally {
    loading.value = false
  }
})
</script>
