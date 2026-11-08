import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import NotificationRulesView from './NotificationRulesView.vue'
import type { NotificationDelivery, NotificationRule } from '../types'

const rule: NotificationRule = {
  id: 'rule-1',
  name: 'Ops pager',
  isActive: true,
  triggerEvent: 'SYSTEM_ERROR',
  channelType: 'WEBHOOK',
  channelConfig: '{}',
  createdAt: '2026-11-08T10:00:00Z',
  updatedAt: '2026-11-08T10:00:00Z',
} as NotificationRule

const delivery = (id: string, status: string): NotificationDelivery => ({
  id,
  ruleId: 'rule-1',
  eventType: 'SYSTEM_ERROR',
  status,
  attemptCount: status === 'FAILED' ? 3 : 1,
  sentAt: '2026-11-08T10:00:00Z',
} as NotificationDelivery)

const getDeliveries = vi.fn()

vi.mock('../api/notificationRules', () => ({
  notificationRulesApi: {
    list: vi.fn(() => Promise.resolve({ data: [rule] })),
    getDeliveries: (...args: unknown[]) => getDeliveries(...args),
    create: vi.fn(),
    update: vi.fn(),
    delete: vi.fn(),
    test: vi.fn(),
  },
}))

describe('NotificationRulesView (#185)', () => {
  beforeEach(() => getDeliveries.mockReset())

  it('offers the operational triggers alongside the compliance ones', async () => {
    const wrapper = mount(NotificationRulesView)
    await flushPromises()

    await wrapper.find('button.bg-indigo-600').trigger('click')
    const options = wrapper.findAll('option').map(o => o.attributes('value'))

    expect(options).toContain('SYSTEM_ERROR')
    expect(options).toContain('INTEGRITY_FAILURE')
    expect(options).toContain('GATE_BLOCKED')
  })

  it('counts dead-lettered deliveries at the top of the delivery history', async () => {
    getDeliveries.mockResolvedValue({
      data: [delivery('d1', 'FAILED'), delivery('d2', 'SENT'), delivery('d3', 'FAILED')],
    })
    const wrapper = mount(NotificationRulesView)
    await flushPromises()

    await wrapper.findAll('button').find(b => b.text() === 'History')!.trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="failed-delivery-count"]').text()).toContain('2 failed')
  })

  it('shows no failure count when every delivery succeeded', async () => {
    getDeliveries.mockResolvedValue({ data: [delivery('d1', 'SENT')] })
    const wrapper = mount(NotificationRulesView)
    await flushPromises()

    await wrapper.findAll('button').find(b => b.text() === 'History')!.trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="failed-delivery-count"]').exists()).toBe(false)
  })
})
