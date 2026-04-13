/** Descriptor for a stored blob, mirroring the IDK BlobDescriptor model. */
export interface BlobDescriptorDTO {
  path: string
  filename?: string
  sizeBytes: number
  contentType?: string
  lastModified?: string
  createdAt?: string
  etag?: string
  metadata?: Record<string, string>
  isFolder?: boolean
}

/** Result of a paginated blob listing. */
export interface ListResultDTO {
  items: BlobDescriptorDTO[]
  commonPrefixes: string[]
  nextPageToken?: string
}

/** Capabilities declared by the blob store backend. */
export interface BlobStoreCapabilitiesDTO {
  supportsCopy: boolean
  supportsMove: boolean
  supportsDelete: boolean
  supportsUpload: boolean
  supportsTempUrls: boolean
}

/** Metadata attached to a blob. */
export interface BlobMetadataDTO {
  contentType?: string
  custom?: Record<string, string>
}

/** Sort configuration. */
export type SortField = 'name' | 'size' | 'lastModified' | 'contentType'
export type SortDirection = 'asc' | 'desc'

/** Breadcrumb segment. */
export interface BreadcrumbSegment {
  label: string
  prefix: string
}
