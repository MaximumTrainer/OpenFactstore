<template>
  <nav class="bg-gray-900 text-white shadow-lg">
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
      <div class="flex items-center h-16">
        <div class="flex items-center space-x-2 mr-8">
          <span class="text-xl font-bold text-indigo-400">⛓</span>
          <span class="text-xl font-bold">Factstore</span>
        </div>
        <div class="flex space-x-1">
          <RouterLink
            v-for="link in links"
            :key="link.to"
            :to="link.to"
            class="px-4 py-2 rounded-md text-sm font-medium transition-colors hover:bg-gray-700 hover:text-white"
            :class="isActive(link.to) ? 'bg-indigo-600 text-white' : 'text-gray-300'"
          >
            {{ link.label }}
          </RouterLink>
        </div>
      </div>
    </div>
  </nav>
</template>

<script setup lang="ts">
import { useRoute } from 'vue-router'

const route = useRoute()

const links = [
  { to: '/', label: 'Dashboard' },
  { to: '/flows', label: 'Flows' },
  { to: '/search', label: 'Search' },
  { to: '/assert', label: 'Assert' },
  { to: '/evidence', label: 'Evidence Vault' },
  { to: '/integrations', label: 'Integrations' },
  { to: '/audit', label: 'Audit Log' }
  { to: '/ledger', label: 'Ledger' },
  { to: '/integrations', label: 'Integrations' }
]

function isActive(path: string): boolean {
  if (path === '/') return route.path === '/'
  return route.path.startsWith(path)
}
</script>
