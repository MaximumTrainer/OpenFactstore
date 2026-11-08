import client from './client'

export type MemberRole = 'ADMIN' | 'MEMBER' | 'VIEWER' | 'SERVICE_ACCOUNT'

export interface PrincipalOrganisation {
  orgSlug: string
  role: MemberRole
}

/**
 * Who the caller is and what it may do.
 *
 * The single source of truth for navigation and control visibility: the server is the
 * authority, and the UI asks it rather than guessing from which screen someone reached.
 */
export interface AuthenticatedPrincipal {
  type: 'USER' | 'API_KEY'
  userId?: string
  email?: string
  name?: string
  apiKeyId?: string
  ownerId?: string
  orgSlug?: string | null
  role?: MemberRole
  /** `resource:action` scopes, e.g. `flows:write`. */
  permissions: string[]
  sessionId?: string
  sessionExpiresAt?: string
  organisations: PrincipalOrganisation[]
}

export interface SessionSummary {
  sessionId: string
  provider: 'OIDC' | 'GITHUB' | 'DEV'
  orgSlug: string | null
  createdAt: string
  lastSeenAt: string
  expiresAt: string
  absoluteExpiresAt: string
  sourceIp: string | null
  userAgent: string | null
  current: boolean
}

/**
 * Whether this instance enforces authentication.
 *
 * Public, and deliberately so: a client has to be able to ask before it has a credential.
 * It reveals only what an unauthenticated request already reveals by being accepted or
 * refused.
 */
export interface AuthConfig {
  enforceAuth: boolean
}

export const getAuthConfig = () => client.get<AuthConfig>('/auth/config')

export const getMe = () => client.get<AuthenticatedPrincipal>('/auth/me')

export const logout = () => client.post<void>('/auth/logout', {})

export const refreshSession = () => client.post<{ expiresAt: string }>('/auth/refresh', {})

export const listMySessions = () => client.get<SessionSummary[]>('/auth/sessions')

export const revokeSession = (sessionId: string) => client.delete<void>(`/auth/sessions/${sessionId}`)

export const switchOrganisation = (orgSlug: string) =>
  client.post<AuthenticatedPrincipal>('/auth/organisation', { orgSlug })

/**
 * Dev sign-in (#156 FR-8.4): local development only. The server answers 404 unless
 * `security.dev-login.enabled=true`, so the login page offers it only when this succeeds.
 */
export const getDevLoginAvailability = () => client.get<{ enabled: boolean }>('/auth/dev-login')

export const devSignIn = (request: { email: string; role?: MemberRole; orgSlug?: string }) =>
  client.post<{ email: string; orgSlug: string; role: MemberRole; expiresAt: string }>('/auth/dev-login', request)

/** Starts an organisation's OIDC flow; the browser is redirected to the returned URL. */
export const getSsoLoginUrl = (orgSlug: string, redirectUri: string) =>
  client.get<{ loginUrl: string; state: string }>(
    `/organisations/${encodeURIComponent(orgSlug)}/sso/login`,
    { params: { redirectUri } }
  )
