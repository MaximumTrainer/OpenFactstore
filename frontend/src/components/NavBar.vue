<template>
  <nav class="bg-gray-900 text-white shadow-lg">
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
      <div class="flex items-center justify-between h-16">
        <div class="flex items-center">
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
        <!-- Notification bell -->
        <div class="relative">
          <button
            @click="toggleDropdown"
            class="relative p-2 rounded-md text-gray-300 hover:text-white hover:bg-gray-700 transition-colors focus:outline-none"
            aria-label="Notifications"
          >
            <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
            </svg>
            <span
              v-if="unreadCount > 0"
              class="absolute top-1 right-1 inline-flex items-center justify-center w-4 h-4 text-xs font-bold text-white bg-red-500 rounded-full"
            >{{ unreadCount > MAX_BADGE_COUNT ? `${MAX_BADGE_COUNT}+` : unreadCount }}</span>
          </button>
          <!-- Notification dropdown -->
          <div
            v-if="showDropdown"
            class="absolute right-0 mt-2 w-80 bg-white rounded-lg shadow-xl z-50 overflow-hidden"
          >
            <div class="flex items-center justify-between px-4 py-3 bg-gray-50 border-b border-gray-200">
              <span class="text-sm font-semibold text-gray-700">Notifications</span>
              <button
                v-if="unreadCount > 0"
                @click="markAllAsRead"
                class="text-xs text-indigo-600 hover:text-indigo-800 font-medium"
              >Mark all read</button>
            </div>
            <div class="max-h-72 overflow-y-auto">
              <div v-if="notifications.length === 0" class="px-4 py-6 text-center text-sm text-gray-500">
                No notifications
              </div>
              <div
                v-for="n in notifications"
                :key="n.id"
                class="px-4 py-3 border-b border-gray-100 hover:bg-gray-50 cursor-pointer"
                :class="{ 'bg-indigo-50': !n.isRead }"
                @click="handleNotificationClick(n)"
              >
                <div class="flex items-start gap-2">
                  <span :class="severityDot(n.severity)" class="mt-1 w-2 h-2 rounded-full flex-shrink-0"></span>
                  <div class="flex-1 min-w-0">
                    <p class="text-sm font-medium text-gray-800 truncate">{{ n.title }}</p>
                    <p class="text-xs text-gray-500 mt-0.5 line-clamp-2">{{ n.message }}</p>
                    <p class="text-xs text-gray-400 mt-1">{{ formatTime(n.createdAt) }}</p>
                  </div>
                </div>
              </div>
            </div>
            <div class="px-4 py-2 bg-gray-50 border-t border-gray-200">
              <RouterLink
                to="/notifications"
                class="text-xs text-indigo-600 hover:text-indigo-800 font-medium"
                @click="showDropdown = false"
              >View all notifications →</RouterLink>
            </div>
          </div>
        </div>
      </div>
    </div>
  </nav>
  <!-- Click outside to close -->
  <div v-if="showDropdown" class="fixed inset-0 z-40" @click="showDropdown = false"></div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { notificationsApi } from '../api/notifications'
import type { Notification } from '../types'

const route = useRoute()

const links = [
  { to: '/', label: 'Dashboard' },
  { to: '/flows', label: 'Flows' },
  { to: '/environments', label: 'Environments' },
  { to: '/logical-environments', label: 'Logical Envs' },
  { to: '/search', label: 'Search' },
  { to: '/assert', label: 'Assert' },
  { to: '/evidence', label: 'Evidence Vault' },
  { to: '/vault', label: 'Secure Vault' },
  { to: '/integrations', label: 'Integrations' },
  { to: '/audit', label: 'Audit Log' },
  { to: '/ledger', label: 'Ledger' },
  { to: '/notifications/rules', label: 'Alerts' }
]

function isActive(path: string): boolean {
  if (path === '/') return route.path === '/'
  return route.path.startsWith(path)
}

const MAX_BADGE_COUNT = 9
const showDropdown = ref(false)
const notifications = ref<Notification[]>([])
const unreadCount = ref(0)
let pollIntervalId: ReturnType<typeof setInterval> | null = null

async function fetchUnreadCount() {
  try {
    const res = await notificationsApi.countUnread()
    unreadCount.value = res.data.count
  } catch {
    // silently ignore – backend may not be reachable in dev
  }
}

async function fetchNotifications() {
  try {
    const res = await notificationsApi.list({ isRead: false })
    notifications.value = res.data.slice(0, 10)
  } catch {
    // silently ignore
  }
}

async function toggleDropdown() {
  showDropdown.value = !showDropdown.value
  if (showDropdown.value) {
    await fetchNotifications()
  }
}

async function markAllAsRead() {
  try {
    await notificationsApi.markAllAsRead()
    unreadCount.value = 0
    notifications.value = notifications.value.map(n => ({ ...n, isRead: true }))
  } catch {
    // silently ignore
  }
}

async function handleNotificationClick(n: Notification) {
  if (!n.isRead) {
    try {
      await notificationsApi.markAsRead(n.id)
      n.isRead = true
      unreadCount.value = Math.max(0, unreadCount.value - 1)
    } catch {
      // silently ignore
    }
  }
}

function severityDot(severity: string): string {
  switch (severity) {
    case 'CRITICAL': return 'bg-red-500'
    case 'WARNING': return 'bg-yellow-400'
    default: return 'bg-blue-400'
  }
}

function formatTime(iso: string): string {
  const d = new Date(iso)
  return d.toLocaleString()
}

onMounted(() => {
  fetchUnreadCount()
  pollIntervalId = setInterval(fetchUnreadCount, 60_000)
})

onUnmounted(() => {
  if (pollIntervalId !== null) {
    clearInterval(pollIntervalId)
    pollIntervalId = null
  }
})
</script>
