import { createRouter, createWebHistory } from 'vue-router'
import DashboardView from '../views/DashboardView.vue'
import FlowsView from '../views/FlowsView.vue'
import FlowDetailView from '../views/FlowDetailView.vue'
import TrailDetailView from '../views/TrailDetailView.vue'
import AssertView from '../views/AssertView.vue'
import EvidenceVaultView from '../views/EvidenceVaultView.vue'
import SecureVaultView from '../views/SecureVaultView.vue'
import IntegrationsView from '../views/IntegrationsView.vue'
import AtlassianIntegrationsView from '../views/AtlassianIntegrationsView.vue'
import EnvironmentsView from '../views/EnvironmentsView.vue'
import EnvironmentDetailView from '../views/EnvironmentDetailView.vue'
import LogicalEnvironmentsView from '../views/LogicalEnvironmentsView.vue'
import LogicalEnvironmentDetailView from '../views/LogicalEnvironmentDetailView.vue'
import AuditLogView from '../views/AuditLogView.vue'
import LedgerView from '../views/LedgerView.vue'
import SearchView from '../views/SearchView.vue'
import NotificationsView from '../views/NotificationsView.vue'
import NotificationRulesView from '../views/NotificationRulesView.vue'
import SsoConfigView from '../views/SsoConfigView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: DashboardView },
    { path: '/flows', component: FlowsView },
    { path: '/flows/:id', component: FlowDetailView },
    { path: '/trails/:id', component: TrailDetailView },
    { path: '/assert', component: AssertView },
    { path: '/evidence', component: EvidenceVaultView },
    { path: '/vault', component: SecureVaultView },
    { path: '/search', component: SearchView },
    { path: '/integrations', component: IntegrationsView },
    { path: '/integrations/atlassian', component: AtlassianIntegrationsView },
    { path: '/integrations/sso', component: SsoConfigView },
    { path: '/environments', component: EnvironmentsView },
    { path: '/environments/:id', component: EnvironmentDetailView },
    { path: '/logical-environments', component: LogicalEnvironmentsView },
    { path: '/logical-environments/:id', component: LogicalEnvironmentDetailView },
    { path: '/audit', component: AuditLogView },
    { path: '/ledger', component: LedgerView },
    { path: '/notifications', component: NotificationsView },
    { path: '/notifications/rules', component: NotificationRulesView }
  ]
})

export default router
