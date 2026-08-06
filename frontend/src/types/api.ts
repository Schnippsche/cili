// ── Auth ─────────────────────────────────────────────────────────────────
export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginUserInfo {
  id: number;
  username: string;
  displayName: string | null;
  role: 'ADMIN' | 'USER';
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  user: LoginUserInfo;
}

// ── User ─────────────────────────────────────────────────────────────────
export interface UserDto {
  id: number;
  username: string;
  email: string;
  displayName: string | null;
  memberId: number | null;
  url: string | null;
  phone: string | null;
  active: boolean;
  role: string;
  createdAt: string;
}

export interface CreateUserRequest {
  username: string;
  email: string;
  password: string;
  role?: string;
  displayName?: string;
  memberId?: number;
  url?: string;
  phone?: string;
}

export interface UpdateUserRequest {
  email?: string;
  password?: string;
  displayName?: string;
  memberId?: number;
  url?: string;
  phone?: string;
  role?: string;
  active?: boolean;
}

// ── Group ────────────────────────────────────────────────────────────────
export interface GroupDto {
  id: number;
  name: string;
  description: string | null;
  system: boolean;
  memberCount: number;
  createdAt: string;
}

export interface CreateGroupRequest {
  name: string;
  description?: string;
}

export interface UpdateGroupRequest {
  name?: string;
  description?: string;
}

// ── ACL ──────────────────────────────────────────────────────────────────
export type AclPermission =
    'READ'
    | 'WRITE'
    | 'DELETE'
    | 'DOWNLOAD'
    | 'UPLOAD'
    | 'SHARE'
    | 'MANAGE_METADATA'
    | 'MANAGE_SUBTITLES'
    | 'TRANSLATE_SUBTITLES'
    | 'ADMIN'
    | 'MANAGE_TEMPLATES';

export interface CreateAclEntryRequest {
  subjectType: 'USER' | 'GROUP';
  subjectId: number;
  resourceType: 'FOLDER' | 'RESOURCE' | 'GLOBAL';
  resourceId?: number;
  permission: AclPermission;
  grantType?: 'ALLOW' | 'DENY';
  inheritable?: boolean;
}

export interface AclEntryDto {
  id: number;
  subjectType: string;
  subjectId: number;
  resourceType: string;
  resourceId: number | null;
  permission: AclPermission;
  grantType: string;
  inheritable: boolean;
}

export interface EffectivePermissionsResponse {
  permissions: AclPermission[];
}

// ── Folder ───────────────────────────────────────────────────────────────
export interface FolderDto {
  id: number;
  name: string;
  parentId: number | null;
  path: string;
  description: string | null;
  trashed: boolean;
  trashedAt: string | null;
  createdBy: number;
  createdAt: string;
  updatedAt: string;
}

export interface BreadcrumbItemDto {
  id: number;
  name: string;
}

export interface CreateFolderRequest {
  name: string;
  parentId?: number;
  description?: string;
}

export interface UpdateFolderRequest {
  name?: string;
  description?: string;
}

// ── Resource ─────────────────────────────────────────────────────────────
export interface MetadataDto {
  title: string | null;
  description: string | null;
  tags: string | null;
  categories: string | null;
  language: string | null;
}

export interface AiSummaryDto {
  languageCode: string;
  summary: string;
  createdAt: string;
}

export interface ResourceDto {
  id: number;
  folderId: number;
  originalName: string;
  storedName: string;
  mimeType: string;
  size: number;
  checksum: string | null;
  uploaderId: number;
  storageType: string;
  fileDate: string | null;
  sortOrder: number | null;
  createdAt: string;
  updatedAt: string;
  metadata: MetadataDto | null;
  thumbnailStatus: 'DONE' | 'PENDING' | 'PROCESSING' | 'FAILED' | null;
  hasAnalyzableSubtitles: boolean;
}

export interface UpdateResourceRequest {
  originalName: string;
}

export interface UpdateMetadataRequest {
  title?: string;
  description?: string;
  tags?: string;
  categories?: string;
  language?: string;
}

// ── Subtitles ────────────────────────────────────────────────────────────
export interface SubtitleTrackDto {
  id: number;
  resourceId: number;
  languageCode: string;
  label: string | null;
  format: 'SRT' | 'VTT';
  createdAt: string;
  hasTextContent: boolean;
}

// ── Upload ───────────────────────────────────────────────────────────────
export interface InitUploadRequest {
  fileName: string;
  mimeType: string;
  totalSize: number;
  chunkSize: number;
  folderId?: number;
  testimonialId?: number;
  fileLastModified?: number;
  bulkImportItemId?: number;
}

export interface UploadJobDto {
  jobId: string;
  chunksTotal: number;
  chunksReceived: number;
  status: string;
}

export interface CompleteUploadResponse {
  resourceId: number;
  codecWarning?: string;
}

// ── Bulk Import ──────────────────────────────────────────────────────────
export interface BulkImportEntry {
  relativePath: string;
  fileSize: number;
  mimeType: string;
  fileLastModified: number | null;
}

export interface CreateBulkImportRequest {
  targetFolderId: number;
  rootName: string;
  entries: BulkImportEntry[];
}

export type BulkImportItemStatus = 'PENDING' | 'UPLOADING' | 'DONE' | 'SKIPPED' | 'FAILED';

export interface BulkImportItemDto {
  id: number;
  relativePath: string;
  resolvedFolderId: number | null;
  status: BulkImportItemStatus;
  skipReason: string | null;
  errorMessage: string | null;
  resourceId: number | null;
}

export type BulkImportJobStatus = 'RUNNING' | 'COMPLETED' | 'COMPLETED_WITH_ERRORS';

export interface BulkImportJobDto {
  jobId: string;
  rootName: string;
  targetFolderId: number;
  status: BulkImportJobStatus;
  filesTotal: number;
  filesDone: number;
  filesSkipped: number;
  filesFailed: number;
  items: BulkImportItemDto[];
}

export interface CreateBulkImportResponse {
  jobId: string;
  items: BulkImportItemDto[];
}

// ── Search ───────────────────────────────────────────────────────────────
export interface SnippetDto {
  text: string;
  timestamp: string | null;
  timestampSeconds: number | null;
  language: string;
}

export interface SearchHitDto {
  resourceId: number;
  name: string;
  title: string | null;
  mimeType: string;
  size: number;
  folderId: number;
  folderPath: string | null;
  uploadedAt: string;
  score: number;
  snippets: SnippetDto[];
  thumbnailStatus: string | null;
  storedName: string;
}

export interface SearchResponse {
  hits: SearchHitDto[];
  totalHits: number;
  page: number;
  size: number;
  testimonialHits: TestimonialSearchHitDto[];
  testimonialTotalHits: number;
  testimonialPage: number;
  testimonialSize: number;
}

export interface FacetDto {
  value: string;
  count: number;
}

export interface FacetsResponse {
  mimeTypes: FacetDto[];
  languages: FacetDto[];
}

// ── Share ─────────────────────────────────────────────────────────────────
export interface ShareTokenDto {
  resourceId: number;
  token: string;
  createdAt: string;
  expiresAt: string;
  validityDays: number;
}

export interface ShareConfigDto {
  validityDays: number;
  baseUrl?: string;
}

export interface ShareInfoDto {
  originalName: string;
  mimeType: string;
  subtitles: SubtitleTrackDto[];
}

// ── Jobs ─────────────────────────────────────────────────────────────────
export type JobStatus = 'PENDING' | 'RUNNING' | 'DONE' | 'FAILED' | 'CANCELLED';
export type JobType =
    'THUMBNAIL'
    | 'TRANSCODE'
    | 'VIDEO_TRANSCODE'
    | 'WAV_EXTRACT'
    | 'WHISPER_TRANSCRIBE'
    | 'OCR'
    | 'PREVIEW'
    | 'VIDEO_ANALYSIS'
    | 'SUBTITLE_TRANSLATE'
    | 'DOCUMENT_TRANSLATE'
    | 'VIDEO_URL_IMPORT'
    | 'TESTIMONIAL_SUMMARY';

export interface ProcessingJobDto {
  id: number;
  resourceId: number;
  type: JobType;
  source: string | null;
  status: JobStatus;
  attempts: number;
  maxAttempts: number;
  errorMessage: string | null;
  result: string | null;
  startedAt: string | null;
  finishedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface TelegramSourceDto {
  name: string;
  label: string;
}

// ── Language ─────────────────────────────────────────────────────────────
export interface LanguageOptionDto {
  code: string;
  label: string;
  translationSupported: boolean;
}

// ── Version ──────────────────────────────────────────────────────────────
export interface VersionResponse {
  version: string;
}

// ── Common ───────────────────────────────────────────────────────────────
// Matches the flat de.toengi.cili.dto.common.PageResponse record (admin/acl/job endpoints).
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

// Matches Spring Data's native Page<T> JSON shape (endpoints that return org.springframework.data.domain.Page).
export interface SpringPage<T> {
  content: T[];
  page: { totalElements: number; totalPages: number; number: number; size: number };
}

// ── Testimonials ────────────────────────────────────────────────────────────
export interface TestimonialAttachmentDto {
  id: number;
  originalName: string;
  mimeType: string;
  size: number;
  createdAt: string;
  thumbnailStatus: 'DONE' | 'PENDING' | 'PROCESSING' | 'FAILED' | null;
  storedName: string | null;
}

export interface TestimonialDto {
  id: number;
  authorName: string;
  tags: string | null;
  text: string;
  human: boolean;
  animal: boolean;
  userId: number;
  createdAt: string;
  updatedAt: string;
  attachments: TestimonialAttachmentDto[];
}

export interface PublicTestimonialDto {
  id: number;
  authorName: string;
  tags: string | null;
  text: string;
  human: boolean;
  animal: boolean;
  createdAt: string;
  updatedAt: string;
  attachments: TestimonialAttachmentDto[];
}

export interface CreateTestimonialRequest {
  authorName: string;
  tags: string | null;
  text: string;
  human: boolean;
  animal: boolean;
}

export interface UpdateTestimonialRequest {
  authorName: string;
  tags: string | null;
  text: string;
  human: boolean;
  animal: boolean;
}

export interface TestimonialSearchHitDto {
  id: number;
  authorName: string;
  tags: string | null;
  text: string;
  human: boolean;
  animal: boolean;
  createdAt: string;
}

// ── Report Jobs ──────────────────────────────────────────────────────────
export type ReportJobStatus = 'PENDING' | 'RUNNING' | 'DONE' | 'FAILED';

export interface ReportJobDto {
  jobId: number;
  status: ReportJobStatus;
  errorMessage: string | null;
  createdAt: string;
  finishedAt: string | null;
}

// ── Logs ─────────────────────────────────────────────────────────────────
export interface LogResponse {
  lines: string[];
  totalLines: number;
  lastModified: string;
}

// ── Collections / Sammlungen ─────────────────────────────────────────────
export interface CollectionDto {
  id: number;
  name: string;
  itemCount: number;
  testimonialCount: number;
  isTemplate: boolean;
  createdAt: string;
}

export interface CollectionNameRequest {
  name: string;
  isTemplate?: boolean;
}

export interface CreateCollectionRequest {
  name: string;
  isTemplate: boolean;
}

export interface AddToCollectionRequest {
  resourceId: number;
}

export interface AddTestimonialToCollectionRequest {
  testimonialId: number;
}

export interface CreateFromTemplateRequest {
  templateId: number;
  name: string;
}

// ── Collection Share ──────────────────────────────────────────────────────
export interface CollectionShareTokenDto {
  collectionId: number;
  token: string;
  createdAt: string;
  expiresAt: string;
  validityDays: number;
}

export interface SharedResourceItem {
  id: number;
  originalName: string;
  mimeType: string;
  hasThumbnail: boolean;
  subtitles: SubtitleTrackDto[];
}

export interface CollectionShareInfoDto {
  collectionName: string;
  expiresAt: string;
  resources: SharedResourceItem[];
  testimonials: PublicTestimonialDto[];
}

// ── Mailflow ──────────────────────────────────────────────────────────────
export interface CustomerDto {
  id: number;
  name: string;
  firstName: string | null;
  email: string;
  mobilePhone: string | null;
  birthDate: string | null;
  memberId: number | null;
  gender: 'MAENNLICH' | 'WEIBLICH' | null;
  informalAddress: boolean | null;
  sponsorUserId: number;
  consentGranted: boolean;
  consentGrantedAt: string;
  consentRevokedAt: string | null;
  createdAt: string;
}

export interface CreateCustomerRequest {
  name: string;
  firstName?: string;
  email: string;
  mobilePhone?: string;
  birthDate?: string;
  memberId?: number;
  gender?: 'MAENNLICH' | 'WEIBLICH';
  informalAddress?: boolean;
}

export interface UpdateCustomerRequest {
  name?: string;
  firstName?: string;
  email?: string;
  mobilePhone?: string;
  birthDate?: string;
  memberId?: number;
  gender?: 'MAENNLICH' | 'WEIBLICH';
  informalAddress?: boolean;
}

export interface MailflowStepDto {
  stepId: string;
  scheduledFor: string;
  sentAt: string | null;
  status: 'PENDING' | 'SENT' | 'SKIPPED' | 'ERROR' | 'FAILED';
  attemptCount: number;
  lastError: string | null;
}

export interface MailflowInstanceDto {
  id: number;
  flowName: string;
  description: string;
  startedAt: string;
  status: 'RUNNING' | 'COMPLETED';
  steps: MailflowStepDto[];
}

export interface StartMailflowRequest {
  flowName: string;
}

export interface AvailableMailflowDto {
  flowName: string;
  description: string;
}
