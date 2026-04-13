import {describe, expect, it, vi} from 'vitest'
import {act, renderHook} from '@testing-library/react'
import {extractFilename, formatFileSize, useBlobExplorer} from './use-blob-explorer'
import type {ListResultDTO} from './blob-types'

const emptyResult: ListResultDTO = { items: [], commonPrefixes: [] }

const sampleResult: ListResultDTO = {
  items: [
    { path: 'docs/readme.md', sizeBytes: 1024, contentType: 'text/markdown' },
    { path: 'docs/logo.png', sizeBytes: 204800, contentType: 'image/png' },
  ],
  commonPrefixes: ['docs/images/'],
}

describe('useBlobExplorer', () => {
  it('initializes with empty state', () => {
    const fetchBlobs = vi.fn().mockResolvedValue(emptyResult)
    const { result } = renderHook(() => useBlobExplorer({ fetchBlobs }))

    expect(result.current.currentPrefix).toBeNull()
    expect(result.current.items).toEqual([])
    expect(result.current.folders).toEqual([])
    expect(result.current.isLoading).toBe(false)
    expect(result.current.selectedBlob).toBeNull()
    expect(result.current.error).toBeNull()
  })

  it('navigates to prefix and fetches blobs', async () => {
    const fetchBlobs = vi.fn().mockResolvedValue(sampleResult)
    const { result } = renderHook(() => useBlobExplorer({ fetchBlobs }))

    await act(async () => {
      result.current.navigateToPrefix('docs/')
    })

    expect(fetchBlobs).toHaveBeenCalledWith('docs/', null)
    expect(result.current.currentPrefix).toBe('docs/')
    expect(result.current.items.length).toBe(2)
    expect(result.current.folders).toEqual(['docs/images/'])
  })

  it('selects and deselects blobs', async () => {
    const fetchBlobs = vi.fn().mockResolvedValue(sampleResult)
    const { result } = renderHook(() => useBlobExplorer({ fetchBlobs }))

    await act(async () => {
      result.current.navigateToPrefix(null)
    })

    act(() => {
      result.current.selectBlob(result.current.items[0])
    })
    // Items are sorted by name ascending, so logo.png comes before readme.md
    expect(result.current.selectedBlob?.path).toBe('docs/logo.png')

    act(() => {
      result.current.selectBlob(null)
    })
    expect(result.current.selectedBlob).toBeNull()
  })

  it('toggles sort direction', async () => {
    const fetchBlobs = vi.fn().mockResolvedValue(sampleResult)
    const { result } = renderHook(() => useBlobExplorer({ fetchBlobs }))

    expect(result.current.sortField).toBe('name')
    expect(result.current.sortDirection).toBe('asc')

    act(() => {
      result.current.setSort('name')
    })
    expect(result.current.sortDirection).toBe('desc')

    act(() => {
      result.current.setSort('size')
    })
    expect(result.current.sortField).toBe('size')
    expect(result.current.sortDirection).toBe('asc')
  })

  it('computes actions from capabilities and config', () => {
    const fetchBlobs = vi.fn().mockResolvedValue(emptyResult)
    const onDeleteBlob = vi.fn().mockResolvedValue(true)
    const onViewBlob = vi.fn()

    const { result } = renderHook(() =>
      useBlobExplorer({
        fetchBlobs,
        capabilities: {
          supportsDelete: true,
          supportsUpload: false,
          supportsCopy: true,
          supportsMove: false,
          supportsTempUrls: false,
        },
        onDeleteBlob,
        onViewBlob,
      }),
    )

    expect(result.current.actions.canDelete).toBe(true)
    expect(result.current.actions.canUpload).toBe(false)
    expect(result.current.actions.canView).toBe(true)
    expect(result.current.actions.canCopy).toBe(false) // no onCopyBlob provided
    expect(result.current.actions.canMove).toBe(false)
  })

  it('readOnly disables write actions', () => {
    const fetchBlobs = vi.fn().mockResolvedValue(emptyResult)
    const onDeleteBlob = vi.fn().mockResolvedValue(true)
    const onUploadBlob = vi.fn().mockResolvedValue({ path: 'x', sizeBytes: 0 })

    const { result } = renderHook(() =>
      useBlobExplorer({
        fetchBlobs,
        readOnly: true,
        capabilities: { supportsDelete: true, supportsUpload: true, supportsCopy: false, supportsMove: false, supportsTempUrls: false },
        onDeleteBlob,
        onUploadBlob,
      }),
    )

    expect(result.current.actions.canDelete).toBe(false)
    expect(result.current.actions.canUpload).toBe(false)
  })

  it('handles fetch error gracefully', async () => {
    const fetchBlobs = vi.fn().mockRejectedValue(new Error('Network error'))
    const { result } = renderHook(() => useBlobExplorer({ fetchBlobs }))

    await act(async () => {
      result.current.navigateToPrefix(null)
    })

    expect(result.current.error).toBe('Network error')
    expect(result.current.items).toEqual([])

    act(() => {
      result.current.dismissError()
    })
    expect(result.current.error).toBeNull()
  })

  it('builds breadcrumbs from prefix', async () => {
    const fetchBlobs = vi.fn().mockResolvedValue(emptyResult)
    const { result } = renderHook(() => useBlobExplorer({ fetchBlobs }))

    await act(async () => {
      result.current.navigateToPrefix('a/b/c/')
    })

    expect(result.current.breadcrumbs).toEqual([
      { label: 'a', prefix: 'a/' },
      { label: 'b', prefix: 'a/b/' },
      { label: 'c', prefix: 'a/b/c/' },
    ])
  })

  it('clears selection on navigate', async () => {
    const fetchBlobs = vi.fn().mockResolvedValue(sampleResult)
    const { result } = renderHook(() => useBlobExplorer({ fetchBlobs }))

    await act(async () => {
      result.current.navigateToPrefix(null)
    })
    act(() => {
      result.current.selectBlob(result.current.items[0])
    })
    expect(result.current.selectedBlob).not.toBeNull()

    await act(async () => {
      result.current.navigateToPrefix('docs/')
    })
    expect(result.current.selectedBlob).toBeNull()
  })

  it('deleteBlob removes item and selects next', async () => {
    const fetchBlobs = vi.fn().mockResolvedValue(sampleResult)
    const onDeleteBlob = vi.fn().mockResolvedValue(true)
    const { result } = renderHook(() => useBlobExplorer({ fetchBlobs, onDeleteBlob }))

    await act(async () => {
      result.current.navigateToPrefix(null)
    })
    expect(result.current.items.length).toBe(2)

    // Select the first item then delete it
    act(() => {
      result.current.selectBlob(result.current.items[0])
    })
    const selectedPath = result.current.selectedBlob!.path

    await act(async () => {
      result.current.deleteBlob(result.current.selectedBlob!)
    })

    expect(onDeleteBlob).toHaveBeenCalled()
    expect(result.current.items.find(i => i.path === selectedPath)).toBeUndefined()
    // After deleting first item, the next item is auto-selected
    expect(result.current.selectedBlob).not.toBeNull()
    expect(result.current.items.length).toBe(1)
    expect(result.current.selectedBlob?.path).toBe(result.current.items[0].path)
  })

  it('deleteBlob does nothing when onDeleteBlob is not provided', async () => {
    const fetchBlobs = vi.fn().mockResolvedValue(sampleResult)
    const { result } = renderHook(() => useBlobExplorer({ fetchBlobs }))

    await act(async () => {
      result.current.navigateToPrefix(null)
    })

    const itemCount = result.current.items.length
    act(() => {
      result.current.deleteBlob(result.current.items[0])
    })
    expect(result.current.items.length).toBe(itemCount)
  })

  it('uploadBlob adds item to list', async () => {
    const fetchBlobs = vi.fn().mockResolvedValue(emptyResult)
    const newBlob = { path: 'docs/new-file.txt', sizeBytes: 256, contentType: 'text/plain' }
    const onUploadBlob = vi.fn().mockResolvedValue(newBlob)
    const { result } = renderHook(() => useBlobExplorer({ fetchBlobs, onUploadBlob }))

    await act(async () => {
      result.current.navigateToPrefix(null)
    })
    expect(result.current.items.length).toBe(0)

    await act(async () => {
      result.current.uploadBlob('docs/new-file.txt', new File(['hello'], 'new-file.txt'))
    })

    expect(onUploadBlob).toHaveBeenCalled()
    expect(result.current.items.length).toBe(1)
    expect(result.current.items[0].path).toBe('docs/new-file.txt')
  })

  it('loadMore appends items from next page', async () => {
    const page1: ListResultDTO = {
      items: [{ path: 'file1.txt', sizeBytes: 100 }],
      commonPrefixes: [],
      nextPageToken: 'page2',
    }
    const page2: ListResultDTO = {
      items: [{ path: 'file2.txt', sizeBytes: 200 }],
      commonPrefixes: [],
    }
    const fetchBlobs = vi.fn()
      .mockResolvedValueOnce(page1)
      .mockResolvedValueOnce(page2)
    const { result } = renderHook(() => useBlobExplorer({ fetchBlobs }))

    await act(async () => {
      result.current.navigateToPrefix(null)
    })
    expect(result.current.items.length).toBe(1)
    expect(result.current.hasMore).toBe(true)

    await act(async () => {
      result.current.loadMore()
    })
    expect(result.current.items.length).toBe(2)
    expect(result.current.hasMore).toBe(false)
  })

  it('exposes isDeleting state during delete', async () => {
    const fetchBlobs = vi.fn().mockResolvedValue(sampleResult)
    let resolveDelete: (value: boolean) => void
    const deletePromise = new Promise<boolean>((resolve) => { resolveDelete = resolve })
    const onDeleteBlob = vi.fn().mockReturnValue(deletePromise)
    const { result } = renderHook(() => useBlobExplorer({ fetchBlobs, onDeleteBlob }))

    await act(async () => {
      result.current.navigateToPrefix(null)
    })
    expect(result.current.isDeleting).toBe(false)

    act(() => {
      result.current.deleteBlob(result.current.items[0])
    })
    expect(result.current.isDeleting).toBe(true)

    await act(async () => {
      resolveDelete!(true)
    })
    expect(result.current.isDeleting).toBe(false)
  })

  it('sets error state when onDeleteBlob rejects', async () => {
    const fetchBlobs = vi.fn().mockResolvedValue(sampleResult)
    const onDeleteBlob = vi.fn().mockRejectedValue(new Error('Delete permission denied'))
    const { result } = renderHook(() => useBlobExplorer({ fetchBlobs, onDeleteBlob }))

    await act(async () => {
      result.current.navigateToPrefix(null)
    })
    expect(result.current.items.length).toBe(2)

    await act(async () => {
      result.current.deleteBlob(result.current.items[0])
    })

    expect(result.current.error).toBe('Delete permission denied')
  })

  it('sets error state when onUploadBlob rejects', async () => {
    const fetchBlobs = vi.fn().mockResolvedValue(emptyResult)
    const onUploadBlob = vi.fn().mockRejectedValue(new Error('Upload quota exceeded'))
    const { result } = renderHook(() => useBlobExplorer({ fetchBlobs, onUploadBlob }))

    await act(async () => {
      result.current.navigateToPrefix(null)
    })

    await act(async () => {
      result.current.uploadBlob('test/file.txt', new File(['data'], 'file.txt'))
    })

    expect(result.current.error).toBe('Upload quota exceeded')
  })

  it('selects next item after deleting middle item', async () => {
    const threeItemResult: ListResultDTO = {
      items: [
        { path: 'docs/aaa.txt', sizeBytes: 100, contentType: 'text/plain' },
        { path: 'docs/bbb.txt', sizeBytes: 200, contentType: 'text/plain' },
        { path: 'docs/ccc.txt', sizeBytes: 300, contentType: 'text/plain' },
      ],
      commonPrefixes: [],
    }
    const fetchBlobs = vi.fn().mockResolvedValue(threeItemResult)
    const onDeleteBlob = vi.fn().mockResolvedValue(true)
    const { result } = renderHook(() => useBlobExplorer({ fetchBlobs, onDeleteBlob }))

    await act(async () => {
      result.current.navigateToPrefix(null)
    })
    expect(result.current.items.length).toBe(3)

    // Select the middle item (index 1 = bbb.txt after sorting)
    act(() => {
      result.current.selectBlob(result.current.items[1])
    })
    expect(result.current.selectedBlob?.path).toBe('docs/bbb.txt')

    // Delete the selected middle item
    await act(async () => {
      result.current.deleteBlob(result.current.selectedBlob!)
    })

    expect(result.current.items.length).toBe(2)
    // Should select the next item (ccc.txt now at index 1)
    expect(result.current.selectedBlob?.path).toBe('docs/ccc.txt')
  })

  it('selects previous item after deleting last item', async () => {
    const twoItemResult: ListResultDTO = {
      items: [
        { path: 'docs/aaa.txt', sizeBytes: 100, contentType: 'text/plain' },
        { path: 'docs/bbb.txt', sizeBytes: 200, contentType: 'text/plain' },
      ],
      commonPrefixes: [],
    }
    const fetchBlobs = vi.fn().mockResolvedValue(twoItemResult)
    const onDeleteBlob = vi.fn().mockResolvedValue(true)
    const { result } = renderHook(() => useBlobExplorer({ fetchBlobs, onDeleteBlob }))

    await act(async () => {
      result.current.navigateToPrefix(null)
    })
    expect(result.current.items.length).toBe(2)

    // Select the last item (index 1 = bbb.txt after sorting)
    act(() => {
      result.current.selectBlob(result.current.items[1])
    })
    expect(result.current.selectedBlob?.path).toBe('docs/bbb.txt')

    // Delete the last item
    await act(async () => {
      result.current.deleteBlob(result.current.selectedBlob!)
    })

    expect(result.current.items.length).toBe(1)
    // Should select the previous item (aaa.txt at index 0)
    expect(result.current.selectedBlob?.path).toBe('docs/aaa.txt')
  })
})

describe('extractFilename', () => {
  it('extracts filename from path', () => {
    expect(extractFilename('folder/file.txt')).toBe('file.txt')
    expect(extractFilename('a/b/c/file.txt')).toBe('file.txt')
    expect(extractFilename('file.txt')).toBe('file.txt')
  })

  it('handles trailing slashes', () => {
    expect(extractFilename('folder/')).toBe('folder')
  })
})

describe('formatFileSize', () => {
  it('formats bytes correctly', () => {
    expect(formatFileSize(0)).toBe('0 B')
    expect(formatFileSize(512)).toBe('512 B')
    expect(formatFileSize(1024)).toBe('1.0 KB')
    expect(formatFileSize(1536)).toBe('1.5 KB')
    expect(formatFileSize(1048576)).toBe('1.0 MB')
    expect(formatFileSize(1073741824)).toBe('1.0 GB')
  })
})
