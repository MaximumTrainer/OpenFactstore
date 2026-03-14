<template>
  <div>
    <div class="mb-6">
      <button class="text-sm text-indigo-600 hover:text-indigo-900" @click="$router.back()">← Back</button>
    </div>

    <div v-if="loading" class="text-center text-gray-500 py-12">Loading...</div>
    <div v-else-if="!trail" class="text-center text-gray-500 py-12">Trail not found.</div>
    <div v-else>
      <!-- Trail Info -->
      <div class="bg-white rounded-lg shadow p-6 mb-6">
        <div class="flex items-center justify-between mb-4">
          <h1 class="text-2xl font-bold text-gray-900">Trail Detail</h1>
          <StatusBadge :status="trail.status" />
        </div>
        <div class="grid grid-cols-1 md:grid-cols-2 gap-4 text-sm">
          <div><span class="font-medium text-gray-500">Git Commit SHA:</span> <span class="font-mono text-gray-900">{{ trail.gitCommitSha }}</span></div>
          <div><span class="font-medium text-gray-500">Branch:</span> {{ trail.gitBranch }}</div>
          <div><span class="font-medium text-gray-500">Author:</span> {{ trail.gitAuthor }} ({{ trail.gitAuthorEmail }})</div>
          <div v-if="trail.pullRequestId"><span class="font-medium text-gray-500">PR ID:</span> {{ trail.pullRequestId }}</div>
          <div v-if="trail.pullRequestReviewer"><span class="font-medium text-gray-500">PR Reviewer:</span> {{ trail.pullRequestReviewer }}</div>
          <div v-if="trail.deploymentActor"><span class="font-medium text-gray-500">Deployment Actor:</span> {{ trail.deploymentActor }}</div>
          <div><span class="font-medium text-gray-500">Created:</span> {{ formatDate(trail.createdAt) }}</div>
        </div>
        <div class="mt-4">
          <button
            class="bg-indigo-600 text-white px-4 py-2 rounded-md text-sm font-medium hover:bg-indigo-700"
            @click="openAssertModal"
          >Assert Compliance</button>
        </div>
      </div>

      <!-- Artifacts -->
      <div class="bg-white rounded-lg shadow mb-6">
        <div class="px-6 py-4 border-b border-gray-200">
          <h2 class="text-lg font-medium text-gray-900">Artifacts</h2>
        </div>
        <div v-if="artifactsLoading" class="p-6 text-center text-gray-500">Loading...</div>
        <div v-else-if="artifacts.length === 0" class="p-6 text-center text-gray-500">No artifacts reported.</div>
        <table v-else class="min-w-full divide-y divide-gray-200">
          <thead class="bg-gray-50">
            <tr>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Image Name</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Tag</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">SHA256 Digest</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Reported By</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Provenance</th>
            </tr>
          </thead>
          <tbody class="bg-white divide-y divide-gray-200">
            <template v-for="artifact in artifacts" :key="artifact.id">
              <tr>
                <td class="px-6 py-4 text-sm text-gray-900">{{ artifact.imageName }}</td>
                <td class="px-6 py-4 text-sm text-gray-500">{{ artifact.imageTag }}</td>
                <td class="px-6 py-4 text-sm font-mono text-gray-500 truncate max-w-xs">{{ artifact.sha256Digest }}</td>
                <td class="px-6 py-4 text-sm text-gray-500">{{ artifact.reportedBy }}</td>
                <td class="px-6 py-4">
                  <span :class="provenanceBadgeClass(artifact.provenanceStatus)" class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium">
                    {{ provenanceLabel(artifact.provenanceStatus) }}
                  </span>
                  <button
                    v-if="artifact.provenanceStatus !== 'NO_PROVENANCE'"
                    class="ml-2 text-xs text-indigo-600 hover:text-indigo-900"
                    @click="toggleProvenance(artifact.id)"
                  >{{ expandedProvenance === artifact.id ? 'Hide' : 'Details' }}</button>
                </td>
              </tr>
              <tr v-if="expandedProvenance === artifact.id && provenanceMap[artifact.id]" class="bg-gray-50">
                <td colspan="5" class="px-6 py-4">
                  <div class="text-sm text-gray-700 grid grid-cols-1 md:grid-cols-2 gap-2">
                    <div><span class="font-medium">Builder ID:</span> {{ provenanceMap[artifact.id]?.builderId }}</div>
                    <div><span class="font-medium">Builder Type:</span> {{ provenanceMap[artifact.id]?.builderType }}</div>
                    <div v-if="provenanceMap[artifact.id]?.buildConfigUri"><span class="font-medium">Build Config:</span> {{ provenanceMap[artifact.id]?.buildConfigUri }}</div>
                    <div v-if="provenanceMap[artifact.id]?.sourceRepositoryUri"><span class="font-medium">Source Repo:</span> {{ provenanceMap[artifact.id]?.sourceRepositoryUri }}</div>
                    <div v-if="provenanceMap[artifact.id]?.sourceCommitSha"><span class="font-medium">Source Commit:</span> <span class="font-mono">{{ provenanceMap[artifact.id]?.sourceCommitSha }}</span></div>
                    <div v-if="provenanceMap[artifact.id]?.buildStartedOn"><span class="font-medium">Build Started:</span> {{ formatDate(provenanceMap[artifact.id]?.buildStartedOn ?? '') }}</div>
                    <div v-if="provenanceMap[artifact.id]?.buildFinishedOn"><span class="font-medium">Build Finished:</span> {{ formatDate(provenanceMap[artifact.id]?.buildFinishedOn ?? '') }}</div>
                    <div>
                      <span class="font-medium">SLSA Level:</span>
                      <span class="ml-1 inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-purple-100 text-purple-800">{{ provenanceMap[artifact.id]?.slsaLevel }}</span>
                    </div>
                  </div>
                </td>
              </tr>
            </template>
          </tbody>
        </table>
      </div>

      <!-- Attestations -->
      <div class="bg-white rounded-lg shadow mb-6">
        <div class="px-6 py-4 border-b border-gray-200">
          <h2 class="text-lg font-medium text-gray-900">Attestations</h2>
        </div>
        <div v-if="attestationsLoading" class="p-6 text-center text-gray-500">Loading...</div>
        <div v-else-if="attestations.length === 0" class="p-6 text-center text-gray-500">No attestations recorded.</div>
        <table v-else class="min-w-full divide-y divide-gray-200">
          <thead class="bg-gray-50">
            <tr>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Type</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Status</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Evidence File</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Created At</th>
            </tr>
          </thead>
          <tbody class="bg-white divide-y divide-gray-200">
            <tr v-for="att in attestations" :key="att.id">
              <td class="px-6 py-4 text-sm font-medium text-gray-900">{{ att.type }}</td>
              <td class="px-6 py-4"><StatusBadge :status="att.status" /></td>
              <td class="px-6 py-4 text-sm text-gray-500">{{ att.evidenceFileName ?? '—' }}</td>
              <td class="px-6 py-4 text-sm text-gray-500">{{ formatDate(att.createdAt) }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Evidence Summary -->
      <div class="bg-white rounded-lg shadow mb-6">
        <div class="px-6 py-4 border-b border-gray-200 flex items-center justify-between">
          <h2 class="text-lg font-medium text-gray-900">Evidence Collection Summary</h2>
          <span v-if="evidenceSummary" :class="evidenceSummary.isComplete ? 'bg-green-100 text-green-800' : 'bg-yellow-100 text-yellow-800'" class="px-3 py-1 rounded-full text-xs font-medium">
            {{ evidenceSummary.isComplete ? '✓ Complete' : '⚠ Incomplete' }}
          </span>
        </div>
        <div v-if="evidenceSummaryLoading" class="p-6 text-center text-gray-500">Loading...</div>
        <div v-else-if="!evidenceSummary" class="p-6 text-center text-gray-500">No evidence summary available.</div>
        <div v-else class="p-6">
          <div class="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6">
            <div class="bg-gray-50 rounded-md p-3 text-center">
              <div class="text-2xl font-bold text-gray-900">{{ evidenceSummary.totalAttestations }}</div>
              <div class="text-xs text-gray-500 mt-1">Total</div>
            </div>
            <div class="bg-green-50 rounded-md p-3 text-center">
              <div class="text-2xl font-bold text-green-700">{{ evidenceSummary.passedAttestations }}</div>
              <div class="text-xs text-gray-500 mt-1">Passed</div>
            </div>
            <div class="bg-red-50 rounded-md p-3 text-center">
              <div class="text-2xl font-bold text-red-700">{{ evidenceSummary.failedAttestations }}</div>
              <div class="text-xs text-gray-500 mt-1">Failed</div>
            </div>
            <div class="bg-yellow-50 rounded-md p-3 text-center">
              <div class="text-2xl font-bold text-yellow-700">{{ evidenceSummary.pendingAttestations }}</div>
              <div class="text-xs text-gray-500 mt-1">Pending</div>
            </div>
          </div>

          <div v-if="evidenceSummary.missingRequiredTypes.length > 0" class="mb-4 p-3 bg-red-50 rounded-md">
            <div class="text-sm font-medium text-red-800 mb-1">Missing Required Evidence Types:</div>
            <div class="flex flex-wrap gap-2">
              <span v-for="t in evidenceSummary.missingRequiredTypes" :key="t"
                class="px-2 py-1 bg-red-100 text-red-700 rounded text-xs font-mono">{{ t }}</span>
            </div>
          </div>

          <div v-if="evidenceSummary.collectedTypes.length > 0" class="mb-4">
            <div class="text-sm font-medium text-gray-700 mb-1">Collected Evidence Types:</div>
            <div class="flex flex-wrap gap-2">
              <span v-for="t in evidenceSummary.collectedTypes" :key="t"
                class="px-2 py-1 bg-indigo-50 text-indigo-700 rounded text-xs font-mono">{{ t }}</span>
            </div>
          </div>

          <!-- Coverage Reports -->
          <div v-if="evidenceSummary.coverageReports.length > 0">
            <div class="text-sm font-medium text-gray-700 mb-2">Coverage Reports:</div>
            <table class="min-w-full divide-y divide-gray-200 border border-gray-200 rounded-md">
              <thead class="bg-gray-50">
                <tr>
                  <th class="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Tool</th>
                  <th class="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Line Coverage</th>
                  <th class="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Branch Coverage</th>
                  <th class="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Min Required</th>
                  <th class="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Result</th>
                </tr>
              </thead>
              <tbody class="bg-white divide-y divide-gray-200">
                <tr v-for="r in evidenceSummary.coverageReports" :key="r.id">
                  <td class="px-4 py-2 text-sm font-mono text-gray-900">{{ r.tool }}</td>
                  <td class="px-4 py-2 text-sm text-gray-700">{{ formatPct(r.lineCoverage) }}</td>
                  <td class="px-4 py-2 text-sm text-gray-700">{{ formatPct(r.branchCoverage) }}</td>
                  <td class="px-4 py-2 text-sm text-gray-700">{{ formatPct(r.minCoverage) }}</td>
                  <td class="px-4 py-2">
                    <span :class="r.passed ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'" class="px-2 py-0.5 rounded text-xs font-medium">
                      {{ r.passed ? 'PASSED' : 'FAILED' }}
                    </span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>

    </div>

    <!-- Assert Compliance Modal -->
    <div v-if="showAssertModal" class="fixed inset-0 bg-black bg-opacity-40 flex items-center justify-center z-50">
      <div class="bg-white rounded-lg shadow-xl p-6 w-full max-w-md">
        <h2 class="text-lg font-bold text-gray-900 mb-4">Assert Compliance</h2>
        <div class="mb-4">
          <label class="block text-sm font-medium text-gray-700 mb-1">SHA256 Digest</label>
          <input
            v-model="assertSha256"
            type="text"
            class="w-full border border-gray-300 rounded-md px-3 py-2 text-sm bg-gray-50"
          />
        </div>
        <div class="mb-6">
          <label class="block text-sm font-medium text-gray-700 mb-1">Flow ID</label>
          <input
            v-model="assertFlowId"
            type="text"
            class="w-full border border-gray-300 rounded-md px-3 py-2 text-sm bg-gray-50"
          />
        </div>
        <div v-if="assertResult" class="mb-4 p-3 rounded-md" :class="assertResult.compliant ? 'bg-green-50 text-green-800' : 'bg-red-50 text-red-800'">
          <div class="font-medium">{{ assertResult.compliant ? '✓ COMPLIANT' : '✗ NON_COMPLIANT' }}</div>
          <div class="text-sm mt-1">{{ assertResult.message }}</div>
          <ul v-if="assertResult.missingAttestations.length" class="mt-2 text-xs list-disc list-inside">
            <li v-for="m in assertResult.missingAttestations" :key="m">Missing: {{ m }}</li>
          </ul>
          <ul v-if="assertResult.failedAttestations.length" class="mt-2 text-xs list-disc list-inside">
            <li v-for="f in assertResult.failedAttestations" :key="f">Failed: {{ f }}</li>
          </ul>
        </div>
        <div v-if="assertError" class="mb-4 text-sm text-red-600">{{ assertError }}</div>
        <div class="flex justify-end gap-3">
          <button
            type="button"
            class="px-4 py-2 text-sm text-gray-700 border border-gray-300 rounded-md hover:bg-gray-50"
            @click="closeAssertModal"
          >Close</button>
          <button
            :disabled="asserting"
            class="px-4 py-2 text-sm text-white bg-indigo-600 rounded-md hover:bg-indigo-700 disabled:opacity-50"
            @click="runAssert"
          >{{ asserting ? 'Asserting...' : 'Assert' }}</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import StatusBadge from '../components/StatusBadge.vue'
import { getTrail } from '../api/trails'
import { getAttestations } from '../api/attestations'
import { getArtifacts, getProvenance } from '../api/artifacts'
import { assertCompliance } from '../api/assert'
import { getEvidenceSummary } from '../api/evidence'
import type { Trail, Attestation, Artifact, AssertResult, EvidenceSummary, BuildProvenance, ProvenanceStatus } from '../types'

const route = useRoute()
const trail = ref<Trail | null>(null)
const attestations = ref<Attestation[]>([])
const artifacts = ref<Artifact[]>([])
const evidenceSummary = ref<EvidenceSummary | null>(null)
const loading = ref(true)
const attestationsLoading = ref(true)
const artifactsLoading = ref(true)
const evidenceSummaryLoading = ref(true)

const expandedProvenance = ref<string | null>(null)
const provenanceMap = ref<Record<string, BuildProvenance | null>>({})

const showAssertModal = ref(false)
const assertSha256 = ref('')
const assertFlowId = ref('')
const assertResult = ref<AssertResult | null>(null)
const assertError = ref('')
const asserting = ref(false)

function formatDate(dateStr: string): string {
  return new Date(dateStr).toLocaleString()
}

function formatPct(value: number | null): string {
  return value != null ? value + '%' : '—'
}

function provenanceLabel(status: ProvenanceStatus): string {
  switch (status) {
    case 'PROVENANCE_VERIFIED': return '✓ Verified'
    case 'PROVENANCE_UNVERIFIED': return '⚠ Unverified'
    default: return 'No Provenance'
  }
}

function provenanceBadgeClass(status: ProvenanceStatus): string {
  switch (status) {
    case 'PROVENANCE_VERIFIED': return 'bg-green-100 text-green-800'
    case 'PROVENANCE_UNVERIFIED': return 'bg-yellow-100 text-yellow-800'
    default: return 'bg-gray-100 text-gray-600'
  }
}

async function toggleProvenance(artifactId: string) {
  if (expandedProvenance.value === artifactId) {
    expandedProvenance.value = null
    return
  }
  expandedProvenance.value = artifactId
  if (provenanceMap.value[artifactId] === undefined) {
    try {
      const trailId = route.params.id as string
      const res = await getProvenance(trailId, artifactId)
      provenanceMap.value[artifactId] = res.data
    } catch {
      delete provenanceMap.value[artifactId]
      expandedProvenance.value = null
    }
  }
}

function openAssertModal() {
  assertResult.value = null
  assertError.value = ''
  if (artifacts.value.length > 0) {
    assertSha256.value = artifacts.value[0].sha256Digest
  }
  assertFlowId.value = trail.value?.flowId ?? ''
  showAssertModal.value = true
}

function closeAssertModal() {
  showAssertModal.value = false
  assertResult.value = null
}

async function runAssert() {
  asserting.value = true
  assertError.value = ''
  assertResult.value = null
  try {
    const res = await assertCompliance(assertSha256.value, assertFlowId.value)
    assertResult.value = res.data
  } catch {
    assertError.value = 'Assertion failed. Please check your inputs.'
  } finally {
    asserting.value = false
  }
}

onMounted(async () => {
  const id = route.params.id as string
  try {
    const res = await getTrail(id)
    trail.value = res.data
  } catch {
    // trail not found
  } finally {
    loading.value = false
  }
  try {
    const res = await getAttestations(id)
    attestations.value = res.data
  } catch {
    // attestations not found
  } finally {
    attestationsLoading.value = false
  }
  try {
    const res = await getArtifacts(id)
    artifacts.value = res.data
  } catch {
    // artifacts not found
  } finally {
    artifactsLoading.value = false
  }
  try {
    const res = await getEvidenceSummary(id)
    evidenceSummary.value = res.data
  } catch {
    // evidence summary not found
  } finally {
    evidenceSummaryLoading.value = false
  }
})
</script>
