import { createRouter, createWebHistory } from 'vue-router'
import DashboardView from '../views/DashboardView.vue'
import FlowsView from '../views/FlowsView.vue'
import FlowDetailView from '../views/FlowDetailView.vue'
import TrailDetailView from '../views/TrailDetailView.vue'
import AssertView from '../views/AssertView.vue'
import EvidenceVaultView from '../views/EvidenceVaultView.vue'
import IntegrationsView from '../views/IntegrationsView.vue'
import AtlassianIntegrationsView from '../views/AtlassianIntegrationsView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: DashboardView },
    { path: '/flows', component: FlowsView },
    { path: '/flows/:id', component: FlowDetailView },
    { path: '/trails/:id', component: TrailDetailView },
    { path: '/assert', component: AssertView },
    { path: '/evidence', component: EvidenceVaultView },
    { path: '/integrations', component: IntegrationsView },
    { path: '/integrations/atlassian', component: AtlassianIntegrationsView }
  ]
})

export default router
