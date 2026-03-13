import type { EnvironmentType } from '../types'

export function useEnvironmentTypeBadge() {
  function typeBadgeClass(type: EnvironmentType | string): string {
    const map: Record<string, string> = {
      K8S: 'bg-blue-100 text-blue-800',
      S3: 'bg-yellow-100 text-yellow-800',
      LAMBDA: 'bg-purple-100 text-purple-800',
      GENERIC: 'bg-gray-100 text-gray-800'
    }
    return map[type] ?? 'bg-gray-100 text-gray-800'
  }

  return { typeBadgeClass }
}
