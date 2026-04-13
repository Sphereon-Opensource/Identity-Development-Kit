import { useCallback, useMemo, useState } from 'react'
import type {
  BlobDescriptorDTO,
  BlobStoreCapabilitiesDTO,
  BreadcrumbSegment,
  ListResultDTO,
  SortDirection,
  SortField,
} from './blob-types'
import { useBlobTree, type UseBlobTreeReturn } from './use-blob-tree'

/** Props for the {@link useBlobExplorer} hook. */
export interface UseBlobExplorerProps {
  fetchBlobs: (prefix: string | null, pageToken: string | null) => Promise<ListResultDTO>
  capabilities?: BlobStoreCapabilitiesDTO
  readOnly?: boolean
  onViewBlob?: (blob: BlobDescriptorDTO) => void
  onEditBlob?: (blob: BlobDescriptorDTO) => void
  onDownloadBlob?: (blob: BlobDescriptorDTO) => void
  onDeleteBlob?: (blob: BlobDescriptorDTO) => Promise<boolean>
  onCopyBlob?: (source: string, destination: string) => Promise<BlobDescriptorDTO>
  onMoveBlob?: (source: string, destination: string) => Promise<BlobDescriptorDTO>
  onUploadBlob?: (path: string, data: File) => Promise<BlobDescriptorDTO>
  onCreateTempUrl?: (path: string) => Promise<string>
}

/** Return value of the {@link useBlobExplorer} hook. */
export interface UseBlobExplorerReturn {
  currentPrefix: string | null
  breadcrumbs: BreadcrumbSegment[]
  navigateToPrefix: (prefix: string | null) => void
  items: BlobDescriptorDTO[]
  folders: string[]
  isLoading: boolean
  isDeleting: boolean
  hasMore: boolean
  loadMore: () => void
  selectedBlob: BlobDescriptorDTO | null
  selectBlob: (blob: BlobDescriptorDTO | null) => void
  sortField: SortField
  sortDirection: SortDirection
  setSort: (field: SortField) => void
  tree: UseBlobTreeReturn
  error: string | null
  dismissError: () => void
  actions: {
    canUpload: boolean
    canDelete: boolean
    canCopy: boolean
    canMove: boolean
    canCreateTempUrl: boolean
    canView: boolean
    canEdit: boolean
    canDownload: boolean
  }
  deleteBlob: (blob: BlobDescriptorDTO) => void
  uploadBlob: (path: string, file: File) => void
}

/**
 * Extracts the filename from a full blob path.
 */
export function extractFilename(path: string): string {
  const trimmed = path.replace(/\/+$/, '')
  return trimmed.split('/').pop() ?? trimmed
}

/**
 * Formats bytes into a human-readable string.
 */
export function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(1)} GB`
}

/**
 * Builds breadcrumb segments from a prefix.
 */
function buildBreadcrumbs(prefix: string | null): BreadcrumbSegment[] {
  if (!prefix) return []
  const parts = prefix.replace(/\/+$/, '').split('/')
  return parts.map((part, i) => ({
    label: part,
    prefix: parts.slice(0, i + 1).join('/') + '/',
  }))
}

const DEFAULT_CAPABILITIES: BlobStoreCapabilitiesDTO = {
  supportsCopy: false,
  supportsMove: false,
  supportsDelete: true,
  supportsUpload: true,
  supportsTempUrls: false,
}

/**
 * Core headless hook for the BlobExplorer.
 * Manages navigation, selection, sorting, pagination, and action availability.
 */
export function useBlobExplorer(props: UseBlobExplorerProps): UseBlobExplorerReturn {
  const {
    fetchBlobs,
    capabilities = DEFAULT_CAPABILITIES,
    readOnly = false,
    onViewBlob,
    onEditBlob,
    onDownloadBlob,
    onDeleteBlob,
    onCopyBlob,
    onMoveBlob,
    onUploadBlob,
    onCreateTempUrl,
  } = props

  const [currentPrefix, setCurrentPrefix] = useState<string | null>(null)
  const [items, setItems] = useState<BlobDescriptorDTO[]>([])
  const [folders, setFolders] = useState<string[]>([])
  const [isLoading, setIsLoading] = useState(false)
  const [hasMore, setHasMore] = useState(false)
  const [nextPageToken, setNextPageToken] = useState<string | null>(null)
  const [selectedBlob, setSelectedBlob] = useState<BlobDescriptorDTO | null>(null)
  const [sortField, setSortField] = useState<SortField>('name')
  const [sortDirection, setSortDirection] = useState<SortDirection>('asc')
  const [error, setError] = useState<string | null>(null)
  const [isDeleting, setIsDeleting] = useState(false)

  const tree = useBlobTree()

  const breadcrumbs = useMemo(() => buildBreadcrumbs(currentPrefix), [currentPrefix])

  const sortItems = useCallback(
    (items: BlobDescriptorDTO[], field: SortField, dir: SortDirection): BlobDescriptorDTO[] => {
      const sorted = [...items].sort((a, b) => {
        // Folders first
        const aFolder = a.isFolder ? 0 : 1
        const bFolder = b.isFolder ? 0 : 1
        if (aFolder !== bFolder) return aFolder - bFolder

        let cmp = 0
        switch (field) {
          case 'name':
            cmp = extractFilename(a.path).localeCompare(extractFilename(b.path))
            break
          case 'size':
            cmp = (a.sizeBytes ?? 0) - (b.sizeBytes ?? 0)
            break
          case 'lastModified':
            cmp = (a.lastModified ?? '').localeCompare(b.lastModified ?? '')
            break
          case 'contentType':
            cmp = (a.contentType ?? '').localeCompare(b.contentType ?? '')
            break
        }
        return dir === 'desc' ? -cmp : cmp
      })
      return sorted
    },
    [],
  )

  const loadItems = useCallback(
    async (prefix: string | null, pageToken: string | null) => {
      setIsLoading(true)
      setError(null)
      try {
        const result = await fetchBlobs(prefix, pageToken)
        if (!pageToken) {
          setItems(sortItems(result.items, sortField, sortDirection))
          setFolders(result.commonPrefixes)
        } else {
          setItems((prev) => sortItems([...prev, ...result.items], sortField, sortDirection))
          setFolders((prev) => [...prev, ...result.commonPrefixes])
        }
        setNextPageToken(result.nextPageToken ?? null)
        setHasMore(!!result.nextPageToken)
      } catch (e) {
        setError(e instanceof Error ? e.message : 'Failed to load items')
      } finally {
        setIsLoading(false)
      }
    },
    [fetchBlobs, sortField, sortDirection, sortItems],
  )

  const navigateToPrefix = useCallback(
    (prefix: string | null) => {
      setCurrentPrefix(prefix)
      setSelectedBlob(null)
      setNextPageToken(null)
      loadItems(prefix, null)
    },
    [loadItems],
  )

  const selectBlob = useCallback((blob: BlobDescriptorDTO | null) => {
    setSelectedBlob(blob)
  }, [])

  const loadMore = useCallback(() => {
    if (hasMore && !isLoading && nextPageToken) {
      loadItems(currentPrefix, nextPageToken)
    }
  }, [hasMore, isLoading, nextPageToken, currentPrefix, loadItems])

  const setSort = useCallback(
    (field: SortField) => {
      const newDir = field === sortField ? (sortDirection === 'asc' ? 'desc' : 'asc') : 'asc'
      setSortField(field)
      setSortDirection(newDir)
      setItems((prev) => sortItems(prev, field, newDir))
    },
    [sortField, sortDirection, sortItems],
  )

  const dismissError = useCallback(() => setError(null), [])

  const deleteBlob = useCallback(
    (blob: BlobDescriptorDTO) => {
      if (!onDeleteBlob || isDeleting) return
      setIsDeleting(true)
      onDeleteBlob(blob)
        .then((success) => {
          if (success) {
            setItems((prev) => {
              const filtered = prev.filter((i) => i.path !== blob.path)
              // Focus next item after deletion (or previous if at end)
              setSelectedBlob((sel) => {
                if (sel?.path !== blob.path) return sel
                if (filtered.length === 0) return null
                const idx = prev.findIndex((i) => i.path === blob.path)
                return idx < filtered.length ? filtered[idx] : filtered[filtered.length - 1]
              })
              return filtered
            })
          }
        })
        .catch((e) => setError(e instanceof Error ? e.message : 'Delete failed'))
        .finally(() => setIsDeleting(false))
    },
    [onDeleteBlob, isDeleting],
  )

  const uploadBlob = useCallback(
    (path: string, file: File) => {
      if (!onUploadBlob) return
      onUploadBlob(path, file)
        .then((created) => {
          setItems((prev) => sortItems([...prev, created], sortField, sortDirection))
        })
        .catch((e) => setError(e instanceof Error ? e.message : 'Upload failed'))
    },
    [onUploadBlob, sortField, sortDirection, sortItems],
  )

  const actions = useMemo(
    () => ({
      canUpload: !readOnly && capabilities.supportsUpload && !!onUploadBlob,
      canDelete: !readOnly && capabilities.supportsDelete && !!onDeleteBlob,
      canCopy: !readOnly && capabilities.supportsCopy && !!onCopyBlob,
      canMove: !readOnly && capabilities.supportsMove && !!onMoveBlob,
      canCreateTempUrl: capabilities.supportsTempUrls && !!onCreateTempUrl,
      canView: !!onViewBlob,
      canEdit: !readOnly && !!onEditBlob,
      canDownload: !!onDownloadBlob,
    }),
    [readOnly, capabilities, onUploadBlob, onDeleteBlob, onCopyBlob, onMoveBlob, onCreateTempUrl, onViewBlob, onEditBlob, onDownloadBlob],
  )

  return {
    currentPrefix,
    breadcrumbs,
    navigateToPrefix,
    items,
    folders,
    isLoading,
    isDeleting,
    hasMore,
    loadMore,
    selectedBlob,
    selectBlob,
    sortField,
    sortDirection,
    setSort,
    tree,
    error,
    dismissError,
    actions,
    deleteBlob,
    uploadBlob,
  }
}
