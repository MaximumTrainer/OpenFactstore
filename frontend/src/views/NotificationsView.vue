<template>
  <div class="max-w-3xl mx-auto px-4 py-8">
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-2xl font-bold text-gray-900">Notifications</h1>
        <p class="text-sm text-gray-500 mt-1">In-app compliance alerts and events.</p>
      </div>
      <button
        v-if="unreadCount > 0"
        @click="markAllAsRead"
        class="text-sm font-medium text-indigo-600 hover:text-indigo-800"
      >Mark all as read ({{ unreadCount }})</button>
    </div>

    <!-- Filters -->
    <div class="flex items-center gap-3 mb-4">
      <label class="text-sm font-medium text-gray-600">Filter:</label>
      <select v-model="filterRead" @change="loadNotifications"
        class="border border-gray-300 rounded-md px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500">
        <option :value="undefined">All</option>
        <option :value="false">Unread</option>
        <option :value="true">Read</option>
      </select>
      <select v-model="filterSeverity" @change="loadNotifications"
        class="border border-gray-300 rounded-md px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500">
        <option :value="undefined">Any severity</option>
        <option value="CRITICAL">Critical</option>
        <option value="WARNING">Warning</option>
        <option value="INFO">Info</option>
      </select>
    </div>

    <div v-if="loading" class="text-center py-12 text-gray-400">Loading…</div>
    <div v-else-if="notifications.length === 0" class="text-center py-12 text-gray-400">No notifications.</div>
    <div v-else class="space-y-2">
      <div
        v-for="n in notifications"
        :key="n.id"
        :class="{ 'bg-indigo-50 border-indigo-200': !n.isRead, 'bg-white border-gray-200': n.isRead }"
        class="flex items-start gap-3 p-4 border rounded-lg cursor-pointer hover:shadow-sm transition-shadow"
        @click="markRead(n)"
      >
        <span :class="severityDot(n.severity)" class="mt-1.5 w-2.5 h-2.5 rounded-full flex-shrink-0"></span>
        <div class="flex-1 min-w-0">
          <div class="flex items-center justify-between">
            <p class="text-sm font-semibold text-gray-900">{{ n.title }}</p>
            <span :class="severityBadge(n.severity)" class="ml-2 inline-flex items-center px-2 py-0.5 rounded text-xs font-medium">
              {{ n.severity }}
            </span>
          </div>
          <p class="text-sm text-gray-600 mt-1">{{ n.message }}</p>
          <p class="text-xs text-gray-400 mt-1.5">{{ new Date(n.createdAt).toLocaleString() }}</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { notificationsApi } from '../api/notifications'
import type { Notification, NotificationSeverity } from '../types'

const loading = ref(true)
const notifications = ref<Notification[]>([])
const unreadCount = ref(0)
const filterRead = ref<boolean | undefined>(undefined)
const filterSeverity = ref<NotificationSeverity | undefined>(undefined)

async function loadNotifications() {
  loading.value = true
  try {
    const res = await notificationsApi.list({
      isRead: filterRead.value,
      severity: filterSeverity.value
    })
    notifications.value = res.data
    const countRes = await notificationsApi.countUnread()
    unreadCount.value = countRes.data.count
  } catch {
    // ignore
  } finally {
    loading.value = false
  }
}

async function markRead(n: Notification) {
  if (n.isRead) return
  try {
    await notificationsApi.markAsRead(n.id)
    n.isRead = true
    unreadCount.value = Math.max(0, unreadCount.value - 1)
  } catch {
    // ignore
  }
}

async function markAllAsRead() {
  try {
    await notificationsApi.markAllAsRead()
    notifications.value = notifications.value.map(n => ({ ...n, isRead: true }))
    unreadCount.value = 0
  } catch {
    // ignore
  }
}

function severityDot(severity: string): string {
  switch (severity) {
    case 'CRITICAL': return 'bg-red-500'
    case 'WARNING': return 'bg-yellow-400'
    default: return 'bg-blue-400'
  }
}

function severityBadge(severity: string): string {
  switch (severity) {
    case 'CRITICAL': return 'bg-red-100 text-red-800'
    case 'WARNING': return 'bg-yellow-100 text-yellow-800'
    default: return 'bg-blue-100 text-blue-800'
  }
}

onMounted(loadNotifications)
</script>
