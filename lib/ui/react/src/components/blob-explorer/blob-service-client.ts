import type {BlobDescriptorDTO, BlobStoreCapabilitiesDTO, ListResultDTO,} from './blob-types'
import type {UseBlobExplorerProps} from './use-blob-explorer'

/**
 * Authentication mode for the blob service client.
 */
export type BlobAuthMode = 'bearer' | 'static-token' | 'none'

/**
 * Configuration for {@link BlobServiceClient}.
 */
export interface BlobServiceClientConfig {
  /** Base URL of service-data (e.g., "http://service-data:8080") */
  baseUrl: string
  /** Store ID (defaults to "default") */
  storeId?: string
  /** Auth mode */
  auth?: BlobAuthMode
  /** Static token for "static-token" mode, or JWT for "bearer" mode */
  token?: string | (() => string | null)
}

/**
 * HTTP client for the blob store REST API, mirroring the Kotlin HttpBlobServiceClient.
 *
 * Calls service-data's blob endpoints directly (same URL patterns as the KMP client).
 * Use alongside the existing fetch-through-proxy approach — consumers choose which to use.
 *
 * Usage:
 * ```typescript
 * const client = new BlobServiceClient({
 *   baseUrl: 'http://localhost:8081',
 *   storeId: 'documents',
 *   auth: 'bearer',
 *   token: () => sessionToken,
 * })
 *
 * // Use directly
 * const list = await client.listBlobs('docs/')
 *
 * // Or generate BlobExplorer props
 * const props = client.toBlobExplorerProps()
 * <BlobExplorer {...props} />
 * ```
 */
export class BlobServiceClient {
  private readonly baseUrl: string
  private readonly storeId: string
  private readonly auth: BlobAuthMode
  private readonly token?: string | (() => string | null)

  constructor(config: BlobServiceClientConfig) {
    this.baseUrl = config.baseUrl.replace(/\/+$/, '')
    this.storeId = config.storeId ?? 'default'
    this.auth = config.auth ?? 'none'
    this.token = config.token
  }

  private blobsUrl(path?: string): string {
    const base = `${this.baseUrl}/api/blob-stores/${this.storeId}/blobs`
    return path ? `${base}/${path}` : base
  }

  private getHeaders(extra?: Record<string, string>): Record<string, string> {
    const headers: Record<string, string> = { ...extra }

    const resolvedToken = typeof this.token === 'function' ? this.token() : this.token
    if (resolvedToken && this.auth !== 'none') {
      headers['Authorization'] = `Bearer ${resolvedToken}`
    }

    return headers
  }

  private async handleError(response: Response, context: string): Promise<never> {
    let detail = `HTTP ${response.status} for ${context}`
    try {
      const body = await response.json()
      if (body.message || body.error) {
        detail = body.message ?? body.error
      }
    } catch {
      // ignore parse errors
    }
    throw new Error(detail)
  }

  async listBlobs(prefix?: string | null, pageToken?: string | null): Promise<ListResultDTO> {
    const params = new URLSearchParams()
    if (prefix) params.set('prefix', prefix)
    if (pageToken) params.set('pageToken', pageToken)
    const qs = params.toString()
    const url = qs ? `${this.blobsUrl()}?${qs}` : this.blobsUrl()

    const response = await fetch(url, { headers: this.getHeaders() })
    if (!response.ok) await this.handleError(response, 'list')

    const data = await response.json()
    return {
      items: (data.descriptors ?? data.items ?? []).map(mapDescriptor),
      commonPrefixes: data.commonPrefixes ?? [],
      nextPageToken: data.nextPageToken ?? undefined,
    }
  }

  async getBlob(path: string): Promise<{ data: Blob; descriptor: BlobDescriptorDTO }> {
    const response = await fetch(`${this.blobsUrl(path)}/content`, {
      headers: this.getHeaders(),
    })
    if (!response.ok) await this.handleError(response, path)

    const blob = await response.blob()
    const contentType = response.headers.get('content-type') ?? undefined
    const disposition = response.headers.get('content-disposition')
    const filename = disposition?.match(/filename="?([^";\s]+)"?/)?.[1] ?? path.split('/').pop()

    return {
      data: blob,
      descriptor: {
        path,
        filename,
        sizeBytes: blob.size,
        contentType,
      },
    }
  }

  async getBlobInfo(path: string): Promise<BlobDescriptorDTO> {
    const response = await fetch(`${this.blobsUrl(path)}/stat`, {
      headers: this.getHeaders(),
    })
    if (!response.ok) await this.handleError(response, path)
    return mapDescriptor(await response.json())
  }

  async storeBlob(
    path: string,
    data: ArrayBuffer | Uint8Array | File,
    contentType?: string,
  ): Promise<BlobDescriptorDTO> {
    let dataBase64: string
    let resolvedContentType = contentType

    if (data instanceof File) {
      resolvedContentType ??= data.type || 'application/octet-stream'
      const buffer = await data.arrayBuffer()
      dataBase64 = bufferToBase64(new Uint8Array(buffer))
    } else {
      const bytes = data instanceof ArrayBuffer ? new Uint8Array(data) : data
      dataBase64 = bufferToBase64(bytes)
    }

    const response = await fetch(this.blobsUrl(path), {
      method: 'PUT',
      headers: this.getHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify({
        path,
        dataBase64,
        metadata: resolvedContentType ? { contentType: resolvedContentType } : {},
      }),
    })
    if (!response.ok) await this.handleError(response, path)
    return mapDescriptor(await response.json())
  }

  async deleteBlob(path: string): Promise<boolean> {
    const response = await fetch(this.blobsUrl(path), {
      method: 'DELETE',
      headers: this.getHeaders(),
    })
    if (response.status === 404) return false
    if (!response.ok) await this.handleError(response, path)
    const data = await response.json()
    return data.deleted ?? true
  }

  async copyBlob(source: string, destination: string): Promise<BlobDescriptorDTO> {
    const response = await fetch(`${this.blobsUrl(source)}/copy`, {
      method: 'POST',
      headers: this.getHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify({ destination }),
    })
    if (!response.ok) await this.handleError(response, source)
    return mapDescriptor(await response.json())
  }

  async moveBlob(source: string, destination: string): Promise<BlobDescriptorDTO> {
    const response = await fetch(`${this.blobsUrl(source)}/move`, {
      method: 'POST',
      headers: this.getHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify({ destination }),
    })
    if (!response.ok) await this.handleError(response, source)
    return mapDescriptor(await response.json())
  }

  async createTempUrl(path: string): Promise<string> {
    const response = await fetch(`${this.blobsUrl(path)}/temp-url`, {
      method: 'POST',
      headers: this.getHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify({}),
    })
    if (!response.ok) await this.handleError(response, path)
    const data = await response.json()
    return data.url
  }

  /**
   * Declared capabilities when using the HTTP client.
   * The server supports all standard operations.
   */
  get capabilities(): BlobStoreCapabilitiesDTO {
    return {
      supportsCopy: true,
      supportsMove: true,
      supportsDelete: true,
      supportsUpload: true,
      supportsTempUrls: true,
    }
  }

  /**
   * Creates {@link UseBlobExplorerProps} callbacks from this client.
   *
   * Returns an object that can be spread into `<BlobExplorer />` or `useBlobExplorer()`.
   * The existing fetch-through-proxy approach still works — this is an alternative.
   *
   * ```tsx
   * const client = new BlobServiceClient({ baseUrl: '...' })
   * <BlobExplorer {...client.toBlobExplorerProps()} />
   * ```
   */
  toBlobExplorerProps(): UseBlobExplorerProps {
    return {
      fetchBlobs: (prefix, pageToken) => this.listBlobs(prefix, pageToken),
      capabilities: this.capabilities,
      onDeleteBlob: async (blob) => this.deleteBlob(blob.path),
      onCopyBlob: (source, destination) => this.copyBlob(source, destination),
      onMoveBlob: (source, destination) => this.moveBlob(source, destination),
      onUploadBlob: (path, file) => this.storeBlob(path, file, file.type),
      onCreateTempUrl: (path) => this.createTempUrl(path),
      onDownloadBlob: (blob) => {
        window.open(`${this.blobsUrl(blob.path)}/content`, '_blank')
      },
      onViewBlob: (blob) => {
        window.open(`${this.blobsUrl(blob.path)}/content`, '_blank')
      },
    }
  }
}

// ── Helpers ─────────────────────────────────────────────────────────────

function mapDescriptor(raw: Record<string, unknown>): BlobDescriptorDTO {
  const ref = raw.ref as Record<string, string> | undefined
  return {
    path: ref?.path ?? (raw.path as string) ?? '',
    filename: (raw.filename as string) ?? (ref?.path ?? (raw.path as string) ?? '').split('/').pop(),
    sizeBytes: (raw.sizeBytes as number) ?? 0,
    contentType: raw.contentType as string | undefined,
    lastModified: raw.lastModified as string | undefined,
    createdAt: raw.createdAt as string | undefined,
    etag: raw.etag as string | undefined,
    metadata: (raw.metadata as Record<string, unknown>)?.custom as Record<string, string> | undefined,
  }
}

function bufferToBase64(bytes: Uint8Array): string {
  let binary = ''
  for (let i = 0; i < bytes.length; i++) {
    binary += String.fromCharCode(bytes[i])
  }
  return btoa(binary)
}
