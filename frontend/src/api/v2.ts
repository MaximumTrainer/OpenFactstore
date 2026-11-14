/**
 * #250: request config for the /api/v2 command/query API. The /api/v1 twins of flows, trails,
 * artifacts and attestations are deprecated; pass this as the axios config to call v2 instead.
 */
export const v2 = { baseURL: '/api/v2' } as const
