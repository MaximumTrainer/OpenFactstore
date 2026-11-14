import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ArchiveTrailDialog from './ArchiveTrailDialog.vue'
import type { Trail } from '../types'

const archiveTrail = vi.fn()
const getTrailCascade = vi.fn()

vi.mock('../api/trails', () => ({
  archiveTrail: (...a: unknown[]) => archiveTrail(...a),
  getTrailCascade: (...a: unknown[]) => getTrailCascade(...a),
}))

const trail = (overrides: Partial<Trail> = {}): Trail => ({
  id: 'trail-1',
  flowId: 'flow-1',
  gitCommitSha: 'abc1234567',
  gitBranch: 'main',
  gitAuthor: 'alice',
  gitAuthorEmail: 'alice@example.com',
  status: 'PENDING',
  createdAt: '2026-01-01T10:00:00Z',
  updatedAt: '2026-01-01T10:00:00Z',
  ...overrides,
})

const cascade = {
  attestations: 3,
  artifacts: 1,
  evidenceFiles: 2,
  approvals: 0,
  coverageReports: 0,
  securityScans: 0,
  complianceAssessments: 0,
  jiraTickets: 0,
  total: 6,
}

const mountDialog = (t = trail()) => mount(ArchiveTrailDialog, { props: { trail: t } })

// #245: trails are archived, never deleted.
describe('ArchiveTrailDialog', () => {
  beforeEach(() => {
    archiveTrail.mockReset().mockResolvedValue({ data: trail({ archivedAt: '2026-02-01T10:00:00Z' }) })
    getTrailCascade.mockReset().mockResolvedValue({ data: cascade })
  })

  it('states the evidence that archiving keeps', async () => {
    const wrapper = mountDialog()
    await flushPromises()

    const summary = wrapper.get('[data-test="cascade-summary"]').text()
    expect(summary.toLowerCase()).toContain('kept')
    expect(summary).toContain('3')
    expect(summary.toLowerCase()).toContain('attestation')
    expect(summary).toContain('2')
    expect(summary.toLowerCase()).toContain('evidence')
  })

  it('offers no way to delete the trail', async () => {
    const wrapper = mountDialog()
    await flushPromises()

    expect(wrapper.find('[data-test="confirm-delete"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="confirm-input"]').exists()).toBe(false)
    expect(wrapper.text().toLowerCase()).not.toContain('permanent')
  })

  it('archives and emits the archived trail', async () => {
    const wrapper = mountDialog()
    await flushPromises()

    await wrapper.get('[data-test="confirm-archive"]').trigger('click')
    await flushPromises()

    expect(archiveTrail).toHaveBeenCalledWith('trail-1')
    expect(wrapper.emitted('archived')?.[0]?.[0]).toMatchObject({ archivedAt: '2026-02-01T10:00:00Z' })
  })

  it('reports a failed archive and stays open', async () => {
    archiveTrail.mockRejectedValue(new Error('500'))
    const wrapper = mountDialog()
    await flushPromises()

    await wrapper.get('[data-test="confirm-archive"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-test="archive-error"]').text()).toContain('Failed')
    expect(wrapper.emitted('archived')).toBeUndefined()
  })

  it('cancels without archiving', async () => {
    const wrapper = mountDialog()
    await flushPromises()

    await wrapper.get('[data-test="cancel-archive"]').trigger('click')

    expect(wrapper.emitted('cancel')).toBeTruthy()
    expect(archiveTrail).not.toHaveBeenCalled()
  })
})
