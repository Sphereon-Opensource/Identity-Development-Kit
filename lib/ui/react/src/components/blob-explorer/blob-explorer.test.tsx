import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { BlobExplorer } from './BlobExplorer'
import type { ListResultDTO } from './blob-types'

const emptyResult: ListResultDTO = { items: [], commonPrefixes: [] }

const sampleResult: ListResultDTO = {
  items: [
    { path: 'readme.md', sizeBytes: 1024, contentType: 'text/markdown', filename: 'readme.md', lastModified: '2026-03-16T12:00:00Z', createdAt: '2026-03-16T10:00:00Z' },
    { path: 'photo.jpg', sizeBytes: 204800, contentType: 'image/jpeg', filename: 'photo.jpg', lastModified: '2026-03-16T14:00:00Z', createdAt: '2026-03-16T13:00:00Z' },
  ],
  commonPrefixes: ['docs/'],
}

describe('BlobExplorer component', () => {
  it('renders empty state when no files', async () => {
    const fetchBlobs = vi.fn().mockResolvedValue(emptyResult)
    render(<BlobExplorer fetchBlobs={fetchBlobs} />)

    await waitFor(() => {
      expect(screen.getByText('No files found')).toBeDefined()
    })
  })

  it('renders breadcrumb with "All files"', async () => {
    const fetchBlobs = vi.fn().mockResolvedValue(emptyResult)
    render(<BlobExplorer fetchBlobs={fetchBlobs} />)

    // "All files" appears in breadcrumb and sidebar tree
    expect(screen.getAllByText('All files').length).toBeGreaterThanOrEqual(1)
  })

  it('renders upload button when onUploadClick provided', async () => {
    const fetchBlobs = vi.fn().mockResolvedValue(emptyResult)
    const onUploadClick = vi.fn()
    render(
      <BlobExplorer
        fetchBlobs={fetchBlobs}
        onUploadClick={onUploadClick}
        capabilities={{ supportsUpload: true, supportsDelete: false, supportsCopy: false, supportsMove: false, supportsTempUrls: false }}
        onUploadBlob={vi.fn()}
      />,
    )

    const btn = screen.getByText('Upload')
    expect(btn).toBeDefined()
    fireEvent.click(btn)
    expect(onUploadClick).toHaveBeenCalledOnce()
  })

  it('does not render upload button when onUploadClick is not provided', async () => {
    const fetchBlobs = vi.fn().mockResolvedValue(emptyResult)
    render(<BlobExplorer fetchBlobs={fetchBlobs} />)

    expect(screen.queryByText('Upload')).toBeNull()
  })

  it('renders file list with items', async () => {
    const fetchBlobs = vi.fn().mockResolvedValue(sampleResult)
    render(<BlobExplorer fetchBlobs={fetchBlobs} initialPrefix={null} />)

    await waitFor(() => {
      expect(screen.getByText('readme.md')).toBeDefined()
      expect(screen.getByText('photo.jpg')).toBeDefined()
    })
  })

  it('renders column headers when files exist', async () => {
    const fetchBlobs = vi.fn().mockResolvedValue(sampleResult)
    render(<BlobExplorer fetchBlobs={fetchBlobs} />)

    await waitFor(() => {
      expect(screen.getByText('Name')).toBeDefined()
      expect(screen.getByText('Size')).toBeDefined()
      expect(screen.getByText('Modified')).toBeDefined()
      expect(screen.getByText('Created')).toBeDefined()
      expect(screen.getByText('Type')).toBeDefined()
    })
  })

  it('selects a file on click', async () => {
    const fetchBlobs = vi.fn().mockResolvedValue(sampleResult)
    render(<BlobExplorer fetchBlobs={fetchBlobs} />)

    await waitFor(() => {
      expect(screen.getByText('readme.md')).toBeDefined()
    })

    fireEvent.click(screen.getByText('readme.md'))

    // Detail pane would show (at wider screens) - at least the click doesn't crash
  })

  it('shows context menu on right click', async () => {
    const fetchBlobs = vi.fn().mockResolvedValue(sampleResult)
    const onDelete = vi.fn().mockResolvedValue(true)
    render(
      <BlobExplorer
        fetchBlobs={fetchBlobs}
        onDeleteBlob={onDelete}
        capabilities={{ supportsDelete: true, supportsUpload: false, supportsCopy: false, supportsMove: false, supportsTempUrls: false }}
      />,
    )

    await waitFor(() => {
      expect(screen.getByText('readme.md')).toBeDefined()
    })

    fireEvent.contextMenu(screen.getByText('readme.md'))

    await waitFor(() => {
      expect(screen.getByText('Delete')).toBeDefined()
    })
  })

  it('hides sidebar when defaultMode is hidden', async () => {
    const fetchBlobs = vi.fn().mockResolvedValue(emptyResult)
    const { container } = render(
      <BlobExplorer fetchBlobs={fetchBlobs} sidebar={{ defaultMode: 'hidden' }} />,
    )

    expect(container.querySelector('[aria-label="Folder tree"]')).toBeNull()
  })

  it('starts with sidebar collapsed when defaultMode is collapsed', async () => {
    const fetchBlobs = vi.fn().mockResolvedValue(sampleResult)
    const { container } = render(
      <BlobExplorer fetchBlobs={fetchBlobs} sidebar={{ defaultMode: 'collapsed' }} />,
    )

    const sidebar = container.querySelector('[aria-label="Folder tree"]')
    expect(sidebar).toBeDefined()
    // Sidebar should have collapsed class
    expect(sidebar?.className).toContain('sidebarCollapsed')
  })

  it('calls fetchBlobs on mount', async () => {
    const fetchBlobs = vi.fn().mockResolvedValue(emptyResult)
    render(<BlobExplorer fetchBlobs={fetchBlobs} />)

    await waitFor(() => {
      expect(fetchBlobs).toHaveBeenCalledWith(null, null)
    })
  })

  it('calls fetchBlobs with initialPrefix', async () => {
    const fetchBlobs = vi.fn().mockResolvedValue(emptyResult)
    render(<BlobExplorer fetchBlobs={fetchBlobs} initialPrefix="docs/" />)

    await waitFor(() => {
      expect(fetchBlobs).toHaveBeenCalledWith('docs/', null)
    })
  })

  it('opens context menu and triggers delete confirmation dialog', async () => {
    const fetchBlobs = vi.fn().mockResolvedValue(sampleResult)
    const onDelete = vi.fn().mockResolvedValue(true)
    render(
      <BlobExplorer
        fetchBlobs={fetchBlobs}
        onDeleteBlob={onDelete}
        capabilities={{ supportsDelete: true, supportsUpload: false, supportsCopy: false, supportsMove: false, supportsTempUrls: false }}
      />,
    )

    await waitFor(() => {
      expect(screen.getByText('readme.md')).toBeDefined()
    })

    fireEvent.contextMenu(screen.getByText('readme.md'))

    await waitFor(() => {
      expect(screen.getByText('Delete')).toBeDefined()
    })

    fireEvent.click(screen.getByText('Delete'))

    await waitFor(() => {
      expect(screen.getByRole('alertdialog')).toBeDefined()
    })

    // Click Delete in the confirmation dialog
    const confirmBtn = screen.getByRole('alertdialog').querySelector('button:last-child')!
    fireEvent.click(confirmBtn)

    await waitFor(() => {
      expect(onDelete).toHaveBeenCalled()
    })
  })

  it('shows pagination Load more button and fetches next page', async () => {
    const page1: ListResultDTO = {
      items: [
        { path: 'file1.txt', sizeBytes: 100, contentType: 'text/plain', filename: 'file1.txt' },
      ],
      commonPrefixes: [],
      nextPageToken: 'page2',
    }
    const page2: ListResultDTO = {
      items: [
        { path: 'file2.txt', sizeBytes: 200, contentType: 'text/plain', filename: 'file2.txt' },
      ],
      commonPrefixes: [],
    }
    const fetchBlobs = vi.fn()
      .mockResolvedValueOnce(page1)
      .mockResolvedValueOnce(page2)

    render(<BlobExplorer fetchBlobs={fetchBlobs} />)

    await waitFor(() => {
      expect(screen.getByText('file1.txt')).toBeDefined()
    })

    const loadMoreBtn = screen.getByText('Load more')
    expect(loadMoreBtn).toBeDefined()

    fireEvent.click(loadMoreBtn)

    await waitFor(() => {
      expect(fetchBlobs).toHaveBeenCalledWith(null, 'page2')
    })
  })

  it('delete confirmation dialog shows filename', async () => {
    const fetchBlobs = vi.fn().mockResolvedValue(sampleResult)
    const onDelete = vi.fn().mockResolvedValue(true)
    render(
      <BlobExplorer
        fetchBlobs={fetchBlobs}
        onDeleteBlob={onDelete}
        capabilities={{ supportsDelete: true, supportsUpload: false, supportsCopy: false, supportsMove: false, supportsTempUrls: false }}
      />,
    )

    await waitFor(() => {
      expect(screen.getByText('readme.md')).toBeDefined()
    })

    fireEvent.contextMenu(screen.getByText('readme.md'))

    await waitFor(() => {
      expect(screen.getByText('Delete')).toBeDefined()
    })

    fireEvent.click(screen.getByText('Delete'))

    await waitFor(() => {
      const dialog = screen.getByRole('alertdialog')
      expect(dialog.textContent).toContain('readme.md')
    })
  })

  it('delete button shows Deleting text while deleting', async () => {
    const fetchBlobs = vi.fn().mockResolvedValue(sampleResult)
    // Never-resolving promise to keep isDeleting=true
    const onDelete = vi.fn().mockReturnValue(new Promise(() => {}))
    render(
      <BlobExplorer
        fetchBlobs={fetchBlobs}
        onDeleteBlob={onDelete}
        capabilities={{ supportsDelete: true, supportsUpload: false, supportsCopy: false, supportsMove: false, supportsTempUrls: false }}
      />,
    )

    await waitFor(() => {
      expect(screen.getByText('readme.md')).toBeDefined()
    })

    // Right-click to open context menu, then trigger delete confirmation
    fireEvent.contextMenu(screen.getByText('readme.md'))
    await waitFor(() => {
      expect(screen.getByText('Delete')).toBeDefined()
    })
    fireEvent.click(screen.getByText('Delete'))

    await waitFor(() => {
      expect(screen.getByRole('alertdialog')).toBeDefined()
    })

    // Click Delete in the confirmation dialog (this calls deleteBlob which sets isDeleting=true, then closes dialog)
    const dialog = screen.getByRole('alertdialog')
    const confirmDeleteBtn = Array.from(dialog.querySelectorAll('button')).find(b => b.textContent === 'Delete')!
    fireEvent.click(confirmDeleteBtn)

    // The dialog closes after clicking delete, but the component re-renders with isDeleting=true.
    // We need to open a new delete dialog to see the "Deleting..." text.
    // Right-click another file and trigger delete again to see the Deleting state.
    fireEvent.contextMenu(screen.getByText('photo.jpg'))
    await waitFor(() => {
      // The context menu Delete re-appears
      expect(screen.getByText('Delete')).toBeDefined()
    })
    fireEvent.click(screen.getByText('Delete'))

    await waitFor(() => {
      const newDialog = screen.getByRole('alertdialog')
      const deletingBtn = Array.from(newDialog.querySelectorAll('button')).find(b => b.textContent?.includes('Deleting'))
      expect(deletingBtn).toBeDefined()
      expect(deletingBtn!.disabled).toBe(true)
    })
  })
})
