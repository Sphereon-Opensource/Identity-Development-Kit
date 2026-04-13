import {useCallback, useState} from 'react'

export interface UseBlobTreeReturn {
  expandedNodes: Set<string>
  toggleNode: (prefix: string) => void
  isExpanded: (prefix: string) => boolean
  expandAll: (prefixes: string[]) => void
  collapseAll: () => void
}

/**
 * Headless hook for tree expansion state management.
 */
export function useBlobTree(initialExpanded: string[] = []): UseBlobTreeReturn {
  const [expandedNodes, setExpandedNodes] = useState<Set<string>>(new Set(initialExpanded))

  const toggleNode = useCallback((prefix: string) => {
    setExpandedNodes((prev) => {
      const next = new Set(prev)
      if (next.has(prefix)) {
        next.delete(prefix)
      } else {
        next.add(prefix)
      }
      return next
    })
  }, [])

  const isExpanded = useCallback((prefix: string) => expandedNodes.has(prefix), [expandedNodes])

  const expandAll = useCallback((prefixes: string[]) => {
    setExpandedNodes(new Set(prefixes))
  }, [])

  const collapseAll = useCallback(() => {
    setExpandedNodes(new Set())
  }, [])

  return { expandedNodes, toggleNode, isExpanded, expandAll, collapseAll }
}
