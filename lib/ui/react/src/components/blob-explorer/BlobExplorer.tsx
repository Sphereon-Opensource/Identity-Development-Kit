import { useCallback, useEffect, useRef, useState, type MouseEvent } from 'react'
import { useBlobExplorer, type UseBlobExplorerProps, extractFilename, formatFileSize } from './use-blob-explorer'
import type { BlobDescriptorDTO, SortField } from './blob-types'
import styles from './BlobExplorer.module.css'

export interface BlobExplorerSidebarConfig {
  /** 'visible' = expanded, 'collapsed' = hidden but toggleable, 'hidden' = no sidebar at all */
  defaultMode?: 'visible' | 'collapsed' | 'hidden'
  /** When true, sidebar auto-collapses when detail pane opens (on medium screens) */
  autoCollapse?: boolean
  /** Restrict which folder prefixes are visible in the tree. If set, only these show. */
  visibleFolders?: string[]
}

export interface BlobExplorerProps extends UseBlobExplorerProps {
  className?: string
  initialPrefix?: string | null
  onUploadClick?: () => void
  sidebar?: BlobExplorerSidebarConfig
  /**
   * Custom date formatter. Receives an ISO-8601 date string (e.g. "2026-03-17T10:30:00Z")
   * and should return a display string. Defaults to browser-native locale-aware formatting.
   */
  formatDate?: (iso: string) => string
}

/* ── SVG Icons ────────────────────────────────────────────────────── */

const IconFolder = () => (
  <svg viewBox="0 0 24 24" fill="none" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
    <path d="M22 19a2 2 0 01-2 2H4a2 2 0 01-2-2V5a2 2 0 012-2h5l2 3h9a2 2 0 012 2z" fill="var(--color-primary, #6750a4)" fillOpacity=".15" stroke="var(--color-primary, #6750a4)" />
  </svg>
)
const IconFile = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
    <path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z" /><polyline points="14 2 14 8 20 8" />
  </svg>
)
const IconImage = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="var(--color-primary, #6750a4)" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
    <rect x="3" y="3" width="18" height="18" rx="2" /><circle cx="8.5" cy="8.5" r="1.5" /><polyline points="21 15 16 10 5 21" />
  </svg>
)
const IconPdf = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="#e53935" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
    <path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z" /><polyline points="14 2 14 8 20 8" />
    <text x="8" y="17" fontSize="6" fontWeight="700" fill="#e53935" stroke="none" fontFamily="inherit">PDF</text>
  </svg>
)
const IconUpload = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4" /><polyline points="17 8 12 3 7 8" /><line x1="12" y1="3" x2="12" y2="15" />
  </svg>
)
const IconDownload = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4" /><polyline points="7 10 12 15 17 10" /><line x1="12" y1="15" x2="12" y2="3" />
  </svg>
)
const IconEye = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" /><circle cx="12" cy="12" r="3" />
  </svg>
)
const IconTrash = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <polyline points="3 6 5 6 21 6" /><path d="M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2" />
  </svg>
)
const IconSidebar = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <rect x="3" y="3" width="18" height="18" rx="2" /><line x1="9" y1="3" x2="9" y2="21" />
  </svg>
)
const IconSortAsc = () => (
  <svg viewBox="0 0 12 12" fill="currentColor" width="12" height="12"><path d="M6 2l3 4H3z" /></svg>
)
const IconSortDesc = () => (
  <svg viewBox="0 0 12 12" fill="currentColor" width="12" height="12"><path d="M6 10l3-4H3z" /></svg>
)
const IconEmptyFolder = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M22 19a2 2 0 01-2 2H4a2 2 0 01-2-2V5a2 2 0 012-2h5l2 3h9a2 2 0 012 2z" />
    <line x1="9" y1="14" x2="15" y2="14" opacity=".5" />
  </svg>
)

function fileIcon(ct?: string) {
  if (ct?.startsWith('image/')) return <IconImage />
  if (ct === 'application/pdf') return <IconPdf />
  return <IconFile />
}

/**
 * Default date formatter — uses the browser's locale-aware formatting.
 * Produces output like "Mar 17, 2026" (en-US) or "17 mrt. 2026" (nl-NL).
 * Can be referenced by consumers as a base for custom formatters.
 */
export function defaultFormatDate(iso: string): string {
  try {
    const d = new Date(iso)
    if (isNaN(d.getTime())) return iso.substring(0, 10)
    return d.toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' })
  } catch { return iso.substring(0, 10) }
}

/* ── Main component ───────────────────────────────────────────────── */

export function BlobExplorer({ className, initialPrefix = null, onUploadClick, sidebar, formatDate: formatDateProp, ...hookProps }: BlobExplorerProps) {
  const explorer = useBlobExplorer(hookProps)
  const fmtDate = useCallback((iso?: string) => iso ? (formatDateProp ?? defaultFormatDate)(iso) : '', [formatDateProp])
  const [contextMenu, setContextMenu] = useState<{ x: number; y: number; blob: BlobDescriptorDTO } | null>(null)
  const [confirmDelete, setConfirmDelete] = useState<{ blob: BlobDescriptorDTO } | null>(null)
  const closeConfirm = useCallback(() => setConfirmDelete(null), [])
  const requestDelete = useCallback((blob: BlobDescriptorDTO) => setConfirmDelete({ blob }), [])
  const [copyMoveDialog, setCopyMoveDialog] = useState<{ blob: BlobDescriptorDTO; mode: 'copy' | 'move' } | null>(null)
  const [copyMoveDestination, setCopyMoveDestination] = useState('')

  const sidebarMode = sidebar?.defaultMode ?? 'visible'
  const sidebarHidden = sidebarMode === 'hidden'
  const [sidebarOpen, setSidebarOpen] = useState(sidebarMode === 'visible')

  // Auto-collapse sidebar when a file is selected (detail pane opens) on narrower screens
  const prevSelected = useRef(explorer.selectedBlob)
  useEffect(() => {
    if (sidebar?.autoCollapse !== false && explorer.selectedBlob && !prevSelected.current) {
      // Only auto-collapse if viewport is medium-ish (detail pane just appeared)
      if (window.innerWidth < 1100) {
        setSidebarOpen(false)
      }
    }
    prevSelected.current = explorer.selectedBlob
  }, [explorer.selectedBlob, sidebar?.autoCollapse])

  useEffect(() => {
    explorer.navigateToPrefix(initialPrefix ?? null)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const handleContextMenu = useCallback((e: MouseEvent, blob: BlobDescriptorDTO) => {
    e.preventDefault()
    setContextMenu({ x: e.clientX, y: e.clientY, blob })
  }, [])
  const closeContextMenu = useCallback(() => setContextMenu(null), [])

  return (
    <div className={`${styles.explorer} ${className ?? ''}`.trim()} data-detail-open={explorer.selectedBlob ? 'true' : 'false'}>
      {/* Toolbar */}
      <div className={styles.toolbar}>
        <nav className={styles.breadcrumbs} aria-label="File path">
          <button type="button" className={`${styles.breadcrumbItem} ${explorer.breadcrumbs.length === 0 ? styles.breadcrumbActive : ''}`} onClick={() => explorer.navigateToPrefix(null)}>
            All files
          </button>
          {explorer.breadcrumbs.map((crumb, i) => (
            <span key={crumb.prefix}>
              <span className={styles.breadcrumbSeparator}>/</span>
              <button type="button" className={`${styles.breadcrumbItem} ${i === explorer.breadcrumbs.length - 1 ? styles.breadcrumbActive : ''}`} onClick={() => explorer.navigateToPrefix(crumb.prefix)}>
                {crumb.label}
              </button>
            </span>
          ))}
        </nav>

        <div className={styles.toolbarActions}>
          {explorer.actions.canUpload && onUploadClick && (
            <button type="button" className={styles.toolbarBtn} onClick={onUploadClick}>
              <IconUpload /> Upload
            </button>
          )}
        </div>
      </div>

      {/* Content */}
      <div className={styles.content}>
        {/* Sidebar */}
        {!sidebarHidden && (
          <nav className={`${styles.sidebar} ${!sidebarOpen ? styles.sidebarCollapsed : ''}`} aria-label="Folder tree">
            <div className={styles.sidebarHeader}>
              <button type="button" className={styles.sidebarToggle} style={{ display: 'flex' }}
                onClick={() => setSidebarOpen(false)} title="Hide folders" aria-label="Toggle folder sidebar">
                <IconSidebar />
              </button>
            </div>
            <div className={styles.sidebarContent} role="tree">
              <TreeNode label="All files" prefix={null} depth={0} explorer={explorer}
                visibleFolders={sidebar?.visibleFolders} />
            </div>
          </nav>
        )}

        {/* File list */}
        <div className={styles.fileList}>
          {explorer.items.length === 0 && explorer.folders.length === 0 && !explorer.isLoading ? (
            <>
              <div className={styles.listHeader}>
                <div className={styles.headerIcon}>
                  {!sidebarHidden && !sidebarOpen && (
                    <button type="button" className={styles.sidebarToggle} style={{ margin: 0, display: 'flex' }}
                      onClick={() => setSidebarOpen(true)} title="Show folders" aria-label="Toggle folder sidebar">
                      <IconSidebar />
                    </button>
                  )}
                </div>
                <SortHeader field="name" label="Name" explorer={explorer} className={styles.headerName} />
                <div className={styles.headerActions} />
              </div>
              <div className={styles.empty}>
                <span className={styles.emptyIcon}><IconEmptyFolder /></span>
                <span className={styles.emptyText}>No files found</span>
              </div>
            </>
          ) : (
            <>
              {/* Column headers */}
              <div className={styles.listHeader}>
                <div className={styles.headerIcon}>
                  {!sidebarHidden && !sidebarOpen && (
                    <button type="button" className={styles.sidebarToggle} style={{ margin: 0, display: 'flex' }}
                      onClick={() => setSidebarOpen(true)} title="Show folders" aria-label="Toggle folder sidebar">
                      <IconSidebar />
                    </button>
                  )}
                </div>
                <SortHeader field="name" label="Name" explorer={explorer} className={styles.headerName} />
                <SortHeader field="size" label="Size" explorer={explorer} className={styles.headerSize} />
                <SortHeader field="lastModified" label="Modified" explorer={explorer} className={styles.headerDate} />
                <div className={`${styles.sortHeader} ${styles.headerCreated}`}>Created</div>
                <SortHeader field="contentType" label="Type" explorer={explorer} className={styles.headerType} />
                <div className={styles.headerActions} />
              </div>

              <div className={styles.fileListContent} onKeyDown={(e) => {
                if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
                  e.preventDefault()
                  const items = e.currentTarget.querySelectorAll('[role="button"]')
                  const current = document.activeElement
                  const idx = Array.from(items).indexOf(current as Element)
                  const next = e.key === 'ArrowDown' ? idx + 1 : idx - 1
                  if (next >= 0 && next < items.length) (items[next] as HTMLElement).focus()
                }
              }}>
                {/* Folders */}
                {explorer.folders.map((folder) => (
                  <ListItem key={`f:${folder}`} item={{ path: folder, sizeBytes: 0, isFolder: true }} isSelected={false}
                    onClick={() => explorer.navigateToPrefix(folder)} actions={explorer.actions}
                    onContextMenu={(e) => handleContextMenu(e, { path: folder, sizeBytes: 0, isFolder: true })} fmtDate={fmtDate} />
                ))}

                {/* Files */}
                {explorer.items.map((item) => (
                  <ListItem key={`i:${item.path}`} item={item} isSelected={explorer.selectedBlob?.path === item.path}
                    onClick={() => item.isFolder ? explorer.navigateToPrefix(item.path) : explorer.selectBlob(item)}
                    actions={explorer.actions} onContextMenu={(e) => handleContextMenu(e, item)}
                    onView={hookProps.onViewBlob} onDownload={hookProps.onDownloadBlob}
                    onDelete={() => requestDelete(item)} fmtDate={fmtDate} />
                ))}

                {explorer.isLoading && <div className={styles.loading}>Loading...</div>}
                {explorer.hasMore && !explorer.isLoading && (
                  <div className={styles.loading}>
                    <button type="button" className={styles.detailBtn} onClick={explorer.loadMore}>Load more</button>
                  </div>
                )}
              </div>
            </>
          )}
          {explorer.error && (
            <div className={styles.error} role="alert">
              <span>{explorer.error}</span>
              <button type="button" className={styles.errorDismiss} onClick={explorer.dismissError}>Dismiss</button>
            </div>
          )}
        </div>

        {/* Detail pane */}
        {explorer.selectedBlob && (
          <div className={styles.detail}>
            <DetailPane blob={explorer.selectedBlob} actions={explorer.actions}
              onView={hookProps.onViewBlob} onEdit={hookProps.onEditBlob}
              onDownload={hookProps.onDownloadBlob}
              onDelete={() => requestDelete(explorer.selectedBlob!)} fmtDate={fmtDate} />
          </div>
        )}
      </div>

      {/* Context menu */}
      {contextMenu && (
        <>
          <div className={styles.contextMenuOverlay} onClick={closeContextMenu} />
          <ContextMenu x={contextMenu.x} y={contextMenu.y} blob={contextMenu.blob} actions={explorer.actions}
            onView={hookProps.onViewBlob} onDownload={hookProps.onDownloadBlob}
            onDelete={() => { requestDelete(contextMenu.blob); closeContextMenu() }}
            onCopy={explorer.actions.canCopy ? () => { setCopyMoveDestination(contextMenu.blob.path); setCopyMoveDialog({ blob: contextMenu.blob, mode: 'copy' }); closeContextMenu() } : undefined}
            onMove={explorer.actions.canMove ? () => { setCopyMoveDestination(contextMenu.blob.path); setCopyMoveDialog({ blob: contextMenu.blob, mode: 'move' }); closeContextMenu() } : undefined}
            onClose={closeContextMenu} />
        </>
      )}

      {/* Delete confirmation dialog */}
      {confirmDelete && (
        <div className={styles.confirmOverlay}>
          <div className={styles.confirmDialog} role="alertdialog" aria-labelledby="delete-confirm-msg">
            <p id="delete-confirm-msg">Delete &ldquo;{confirmDelete.blob.filename ?? extractFilename(confirmDelete.blob.path)}&rdquo;?</p>
            <div className={styles.confirmActions}>
              <button type="button" className={styles.detailBtn} onClick={closeConfirm} disabled={explorer.isDeleting}>Cancel</button>
              <button type="button" className={`${styles.detailBtn} ${styles.detailBtnDanger}`}
                disabled={explorer.isDeleting}
                onClick={() => { explorer.deleteBlob(confirmDelete.blob); closeConfirm() }}>{explorer.isDeleting ? 'Deleting\u2026' : 'Delete'}</button>
            </div>
          </div>
        </div>
      )}

      {/* Copy/Move destination dialog */}
      {copyMoveDialog && (
        <div className={styles.confirmOverlay}>
          <div className={styles.confirmDialog}>
            <p>{copyMoveDialog.mode === 'copy' ? 'Copy' : 'Move'} &ldquo;{extractFilename(copyMoveDialog.blob.path)}&rdquo; to:</p>
            <input
              type="text"
              className={styles.destinationInput}
              value={copyMoveDestination}
              onChange={(e) => setCopyMoveDestination(e.target.value)}
              placeholder="Enter destination path"
              autoFocus
              onKeyDown={(e) => {
                if (e.key === 'Enter' && copyMoveDestination.trim()) {
                  const source = copyMoveDialog.blob.path
                  const dest = copyMoveDestination.trim()
                  if (copyMoveDialog.mode === 'copy') {
                    hookProps.onCopyBlob?.(source, dest)
                  } else {
                    hookProps.onMoveBlob?.(source, dest)
                  }
                  setCopyMoveDialog(null)
                  setCopyMoveDestination('')
                }
                if (e.key === 'Escape') {
                  setCopyMoveDialog(null)
                  setCopyMoveDestination('')
                }
              }}
            />
            <div className={styles.confirmActions}>
              <button type="button" className={styles.detailBtn}
                onClick={() => { setCopyMoveDialog(null); setCopyMoveDestination('') }}>Cancel</button>
              <button type="button" className={`${styles.detailBtn} ${styles.detailBtnPrimary}`}
                disabled={!copyMoveDestination.trim()}
                onClick={() => {
                  const source = copyMoveDialog.blob.path
                  const dest = copyMoveDestination.trim()
                  if (copyMoveDialog.mode === 'copy') {
                    hookProps.onCopyBlob?.(source, dest)
                  } else {
                    hookProps.onMoveBlob?.(source, dest)
                  }
                  setCopyMoveDialog(null)
                  setCopyMoveDestination('')
                }}>{copyMoveDialog.mode === 'copy' ? 'Copy' : 'Move'}</button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

/* ── Sort header ──────────────────────────────────────────────────── */

function SortHeader({ field, label, explorer, className }: {
  field: SortField; label: string; className?: string
  explorer: ReturnType<typeof useBlobExplorer>
}) {
  const isActive = explorer.sortField === field
  return (
    <button type="button" className={`${styles.sortHeader} ${className ?? ''} ${isActive ? styles.sortHeaderActive : ''}`}
      onClick={() => explorer.setSort(field)}>
      {label}
      {isActive && (
        <span className={styles.sortIcon}>
          {explorer.sortDirection === 'asc' ? <IconSortAsc /> : <IconSortDesc />}
        </span>
      )}
    </button>
  )
}

/* ── ListItem ─────────────────────────────────────────────────────── */

function ListItem({ item, isSelected, onClick, actions, onContextMenu, onView, onDownload, onDelete, fmtDate }: {
  item: BlobDescriptorDTO; isSelected: boolean; onClick: () => void
  actions: ReturnType<typeof useBlobExplorer>['actions']
  onContextMenu?: (e: MouseEvent) => void
  onView?: (b: BlobDescriptorDTO) => void
  onDownload?: (b: BlobDescriptorDTO) => void
  onDelete?: () => void
  fmtDate: (iso?: string) => string
}) {
  return (
    <div className={`${styles.listItem} ${isSelected ? styles.listItemSelected : ''}`}
      onClick={onClick} onContextMenu={onContextMenu} role="button" tabIndex={0}
      onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onClick() } }}>
      <span className={styles.itemIcon}>{item.isFolder ? <IconFolder /> : fileIcon(item.contentType)}</span>
      <div className={styles.itemName}>{item.filename ?? extractFilename(item.path)}</div>
      <div className={styles.itemSize}>{!item.isFolder ? formatFileSize(item.sizeBytes) : ''}</div>
      <div className={styles.itemDate}>{fmtDate(item.lastModified)}</div>
      <div className={styles.itemCreated}>{fmtDate(item.createdAt)}</div>
      <div className={styles.itemType}>{!item.isFolder ? (item.contentType ?? '') : 'Folder'}</div>
      {!item.isFolder ? (
        <div className={styles.itemActions}>
          {actions.canView && onView && <button type="button" className={styles.itemActionBtn} title="View" aria-label="View file" onClick={(e) => { e.stopPropagation(); onView(item) }}><IconEye /></button>}
          {actions.canDownload && onDownload && <button type="button" className={styles.itemActionBtn} title="Download" aria-label="Download file" onClick={(e) => { e.stopPropagation(); onDownload(item) }}><IconDownload /></button>}
          {actions.canDelete && onDelete && <button type="button" className={styles.itemActionBtn} title="Delete" aria-label="Delete file" onClick={(e) => { e.stopPropagation(); onDelete() }}><IconTrash /></button>}
        </div>
      ) : <div className={styles.itemActions} />}
    </div>
  )
}

/* ── Context menu ─────────────────────────────────────────────────── */

function ContextMenu({ x, y, blob, actions, onView, onDownload, onDelete, onCopy, onMove, onClose }: {
  x: number; y: number; blob: BlobDescriptorDTO
  actions: ReturnType<typeof useBlobExplorer>['actions']
  onView?: (b: BlobDescriptorDTO) => void; onDownload?: (b: BlobDescriptorDTO) => void
  onDelete: () => void; onCopy?: () => void; onMove?: () => void; onClose: () => void
}) {
  useEffect(() => {
    const h = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose() }
    document.addEventListener('keydown', h)
    return () => document.removeEventListener('keydown', h)
  }, [onClose])

  return (
    <div className={styles.contextMenu} style={{ left: x, top: y }}>
      {actions.canView && onView && <button type="button" className={styles.contextMenuItem} onClick={() => { onView(blob); onClose() }}><IconEye /> View</button>}
      {actions.canDownload && onDownload && <button type="button" className={styles.contextMenuItem} onClick={() => { onDownload(blob); onClose() }}><IconDownload /> Download</button>}
      {onCopy && <button type="button" className={styles.contextMenuItem} onClick={onCopy}>Copy to...</button>}
      {onMove && <button type="button" className={styles.contextMenuItem} onClick={onMove}>Move to...</button>}
      {(actions.canView || actions.canDownload || onCopy || onMove) && actions.canDelete && <div className={styles.contextMenuDivider} />}
      {actions.canDelete && <button type="button" className={`${styles.contextMenuItem} ${styles.contextMenuDanger}`} onClick={onDelete}><IconTrash /> Delete</button>}
    </div>
  )
}

/* ── Tree ─────────────────────────────────────────────────────────── */

function TreeNode({ label, prefix, depth, explorer, visibleFolders }: {
  label: string; prefix: string | null; depth: number
  explorer: ReturnType<typeof useBlobExplorer>
  visibleFolders?: string[]
}) {
  const isActive = explorer.currentPrefix === prefix
  const isExpanded = prefix === null || explorer.tree.isExpanded(prefix)
  let childFolders = explorer.folders.filter((f) => {
    if (prefix === null) return !f.replace(/\/+$/, '').includes('/')
    return f.startsWith(prefix) && f !== prefix && f.slice(prefix.length).replace(/\/+$/, '').split('/').length === 1
  })
  // Filter to only visible folders if configured
  if (visibleFolders) {
    childFolders = childFolders.filter((f) => visibleFolders.some((vf) => f.startsWith(vf) || vf.startsWith(f)))
  }
  return (
    <div>
      <div className={`${styles.treeNode} ${isActive ? styles.treeNodeActive : ''}`}
        onClick={() => { explorer.navigateToPrefix(prefix); if (prefix !== null) explorer.tree.toggleNode(prefix) }}
        role="treeitem" aria-expanded={isExpanded} tabIndex={0}
        onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); explorer.navigateToPrefix(prefix); if (prefix !== null) explorer.tree.toggleNode(prefix) } }}>
        <span className={styles.treeToggle}>{isExpanded ? '\u25BE' : '\u25B8'}</span>
        <span className={styles.treeNodeIcon}><IconFolder /></span>
        <span>{label}</span>
      </div>
      {isExpanded && childFolders.length > 0 && (
        <div className={styles.treeChildren} role="group">
          {childFolders.map((f) => <TreeNode key={f} label={extractFilename(f.replace(/\/+$/, ''))} prefix={f} depth={depth + 1} explorer={explorer} visibleFolders={visibleFolders} />)}
        </div>
      )}
    </div>
  )
}

/* ── Detail pane ──────────────────────────────────────────────────── */

function DetailPane({ blob, actions, onView, onEdit, onDownload, onDelete, fmtDate }: {
  blob: BlobDescriptorDTO; actions: ReturnType<typeof useBlobExplorer>['actions']
  onView?: (b: BlobDescriptorDTO) => void; onEdit?: (b: BlobDescriptorDTO) => void
  onDownload?: (b: BlobDescriptorDTO) => void; onDelete: () => void
  fmtDate: (iso?: string) => string
}) {
  return (
    <>
      <div className={styles.detailTitle}>{blob.filename ?? extractFilename(blob.path)}</div>
      <div className={styles.detailDivider} />
      <DetailRow label="Path" value={blob.path} />
      <DetailRow label="Size" value={formatFileSize(blob.sizeBytes)} />
      {blob.contentType && <DetailRow label="Type" value={blob.contentType} />}
      {blob.etag && <DetailRow label="ETag" value={blob.etag} />}
      {blob.lastModified && <DetailRow label="Modified" value={fmtDate(blob.lastModified)} />}
      {blob.createdAt && <DetailRow label="Created" value={fmtDate(blob.createdAt)} />}
      {blob.metadata && Object.keys(blob.metadata).length > 0 && (
        <>
          <div className={styles.detailDivider} />
          <div className={styles.detailTitle} style={{ fontSize: '0.8125rem' }}>Metadata</div>
          {Object.entries(blob.metadata).map(([k, v]) => <DetailRow key={k} label={k} value={v} />)}
        </>
      )}
      <div className={styles.detailDivider} />
      <div className={styles.detailActions}>
        {actions.canView && onView && <button type="button" className={`${styles.detailBtn} ${styles.detailBtnPrimary}`} onClick={() => onView(blob)}><IconEye /> View</button>}
        {actions.canDownload && onDownload && <button type="button" className={styles.detailBtn} onClick={() => onDownload(blob)}><IconDownload /> Download</button>}
        {actions.canEdit && onEdit && <button type="button" className={styles.detailBtn} onClick={() => onEdit(blob)}>Edit</button>}
        {actions.canDelete && <button type="button" className={`${styles.detailBtn} ${styles.detailBtnDanger}`} onClick={onDelete}><IconTrash /> Delete</button>}
      </div>
    </>
  )
}

function DetailRow({ label, value }: { label: string; value: string }) {
  return (
    <div className={styles.detailRow}>
      <div className={styles.detailLabel}>{label}</div>
      <div className={styles.detailValue}>{value}</div>
    </div>
  )
}
