import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import LoginView from './LoginView.vue'

const push = vi.fn()
const query: Record<string, string> = {}

vi.mock('vue-router', () => ({
  useRoute: () => ({ query }),
  useRouter: () => ({ push }),
}))

const getDevLoginAvailability = vi.fn()
const devSignIn = vi.fn()
const loadPrincipal = vi.fn()

vi.mock('../api/auth', () => ({
  getSsoLoginUrl: vi.fn(),
  getDevLoginAvailability: () => getDevLoginAvailability(),
  devSignIn: (...args: unknown[]) => devSignIn(...args),
}))

vi.mock('../composables/useAuth', () => ({
  loadPrincipal: (...args: unknown[]) => loadPrincipal(...args),
}))

describe('LoginView dev sign-in (#156 FR-8.4)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    for (const k of Object.keys(query)) delete query[k]
  })

  it('offers dev sign-in only when the server says it is available', async () => {
    getDevLoginAvailability.mockRejectedValue({ response: { status: 404 } })
    const wrapper = mount(LoginView)
    await flushPromises()

    expect(wrapper.find('[data-test="dev-login"]').exists()).toBe(false)
  })

  it('signs in with the chosen email and role, then goes where the user was heading', async () => {
    getDevLoginAvailability.mockResolvedValue({ data: { enabled: true } })
    devSignIn.mockResolvedValue({ data: {} })
    loadPrincipal.mockResolvedValue({ email: 'dev@localhost' })
    query.redirect = '/flows/123'
    const wrapper = mount(LoginView)
    await flushPromises()

    await wrapper.find('[data-test="dev-email"]').setValue('dev@localhost')
    await wrapper.find('[data-test="dev-role"]').setValue('VIEWER')
    await wrapper.find('[data-test="dev-login"]').trigger('submit')
    await flushPromises()

    expect(devSignIn).toHaveBeenCalledWith({ email: 'dev@localhost', role: 'VIEWER' })
    expect(loadPrincipal).toHaveBeenCalledWith(true)
    expect(push).toHaveBeenCalledWith('/flows/123')
  })

  it('does not follow a redirect to another site', async () => {
    getDevLoginAvailability.mockResolvedValue({ data: { enabled: true } })
    devSignIn.mockResolvedValue({ data: {} })
    loadPrincipal.mockResolvedValue({ email: 'dev@localhost' })
    query.redirect = '//evil.example/steal'
    const wrapper = mount(LoginView)
    await flushPromises()

    await wrapper.find('[data-test="dev-email"]').setValue('dev@localhost')
    await wrapper.find('[data-test="dev-login"]').trigger('submit')
    await flushPromises()

    expect(push).toHaveBeenCalledWith('/')
  })
})
