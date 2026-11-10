import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { computed, ref } from 'vue'
import FlowsView from './FlowsView.vue'

vi.mock('../api/flows', () => ({
  getFlows: vi.fn(() => Promise.resolve({ data: [] })),
  createFlow: vi.fn(),
  archiveFlow: vi.fn(),
  unarchiveFlow: vi.fn(),
}))

const permissions = ref<string[] | null>(null)

vi.mock('../composables/useAuth', () => ({
  useAuth: () => ({
    isAuthenticated: computed(() => permissions.value !== null),
    can: (p: string) => permissions.value?.includes(p) ?? false,
  }),
}))

const mountView = () =>
  mount(FlowsView, { global: { stubs: { RouterLink: true, FlowEditForm: true } } })

const newFlowButton = (wrapper: ReturnType<typeof mountView>) =>
  wrapper.findAll('button').find(b => b.text().includes('New Flow'))

/** #209 / #156 FR-6.5: controls the caller's role cannot use are not offered. */
describe('FlowsView role-gated controls', () => {
  beforeEach(() => { permissions.value = null })

  it('does not offer New Flow to a viewer', async () => {
    permissions.value = ['flows:read', 'trails:read']
    const wrapper = mountView()
    await flushPromises()

    expect(newFlowButton(wrapper)).toBeUndefined()
  })

  it('offers New Flow to a caller who may create flows', async () => {
    permissions.value = ['flows:read', 'flows:write']
    const wrapper = mountView()
    await flushPromises()

    expect(newFlowButton(wrapper)).toBeDefined()
  })

  it('offers New Flow on an instance that does not ask anyone to sign in', async () => {
    permissions.value = null
    const wrapper = mountView()
    await flushPromises()

    expect(newFlowButton(wrapper)).toBeDefined()
  })
})
