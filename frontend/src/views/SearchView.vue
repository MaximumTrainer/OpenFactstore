<template>
  <div>
    <h1 class="text-2xl font-bold text-gray-900 mb-6">Search</h1>

    <div class="bg-white rounded-lg shadow p-6 mb-6">
      <form @submit.prevent="doSearch" class="flex gap-3">
        <input
          v-model="query"
          type="text"
          placeholder="Search trails, artifacts… (branch, author, image name, digest)"
          class="flex-1 border border-gray-300 rounded-md px-4 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
        />
        <select
          v-model="typeFilter"
          class="border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
        >
          <option value="">All types</option>
          <option value="trail">Trails</option>
          <option value="artifact">Artifacts</option>
        </select>
        <button
          type="submit"
          :disabled="searching || !query.trim()"
          class="bg-indigo-600 text-white px-5 py-2 rounded-md text-sm font-medium hover:bg-indigo-700 disabled:opacity-50"
        >
          {{ searching ? 'Searching…' : 'Search' }}
        </button>
      </form>
    </div>

    <div v-if="searched">
      <div v-if="searchError" class="text-center text-red-600 py-12">{{ searchError }}</div>
      <div v-else-if="results.length === 0" class="text-center text-gray-500 py-12">
        No results found for <strong>{{ lastQuery }}</strong>.
      </div>
      <div v-else>
        <p class="text-sm text-gray-500 mb-3">{{ results.length }} result{{ results.length !== 1 ? 's' : '' }} for <strong>{{ lastQuery }}</strong></p>
        <div class="space-y-3">
          <button
            v-for="item in results"
            :key="`${item.type}-${item.id}`"
            type="button"
            class="w-full bg-white rounded-lg shadow p-4 hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-indigo-500 flex items-start justify-between text-left"
            @click="navigateTo(item)"
            @keydown.enter="navigateTo(item)"
          >
            <div class="flex items-start gap-3">
              <span
                class="mt-0.5 inline-flex items-center px-2 py-0.5 rounded text-xs font-medium"
                :class="item.type === 'trail' ? 'bg-blue-100 text-blue-800' : 'bg-purple-100 text-purple-800'"
              >
                {{ item.type }}
              </span>
              <div>
                <div class="text-sm font-medium text-gray-900">{{ item.title }}</div>
                <div class="text-xs text-gray-500 mt-0.5">{{ item.description }}</div>
                <div v-if="item.type === 'trail'" class="mt-1 flex gap-3 text-xs text-gray-400">
                  <span v-if="item.metadata.status">
                    Status:
                    <span
                      class="font-medium"
                      :class="{
                        'text-green-600': item.metadata.status === 'COMPLIANT',
                        'text-red-600': item.metadata.status === 'NON_COMPLIANT',
                        'text-yellow-600': item.metadata.status === 'PENDING'
                      }"
                    >{{ item.metadata.status }}</span>
                  </span>
                  <span v-if="item.metadata.createdAt">{{ formatDate(item.metadata.createdAt) }}</span>
                </div>
              </div>
            </div>
            <span class="text-indigo-600 text-sm ml-4 flex-shrink-0" aria-hidden="true">View →</span>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { search } from '../api/search'
import type { SearchResultItem } from '../types'

const router = useRouter()

const query = ref('')
const typeFilter = ref('')
const results = ref<SearchResultItem[]>([])
const searching = ref(false)
const searched = ref(false)
const lastQuery = ref('')
const searchError = ref('')

async function doSearch() {
  if (!query.value.trim()) return
  searching.value = true
  searched.value = false
  searchError.value = ''
  lastQuery.value = query.value
  try {
    const res = await search(query.value, typeFilter.value || undefined)
    results.value = res.data.results
    searched.value = true
  } catch {
    results.value = []
    searched.value = true
    searchError.value = 'Search failed. Please check your connection and try again.'
  } finally {
    searching.value = false
  }
}

function navigateTo(item: SearchResultItem) {
  if (item.type === 'trail') {
    router.push(`/trails/${item.id}`)
  } else if (item.type === 'artifact') {
    const sha = item.metadata.sha256Digest
    if (sha) router.push(`/evidence?sha256=${encodeURIComponent(sha)}`)
  }
}

function formatDate(dateStr: string | null | undefined): string {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleDateString()
}
</script>
