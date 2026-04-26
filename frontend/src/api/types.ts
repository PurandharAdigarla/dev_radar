export interface User {
  id: number;
  email: string;
  displayName: string;
  active?: boolean;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  displayName: string;
}

export interface RefreshResponse {
  accessToken: string;
  refreshToken: string;
}

// ─── Radar ──────────────────────────────────────────────────────────────

export type RadarStatus = "GENERATING" | "READY" | "FAILED";

export interface RadarSummary {
  id: number;
  status: RadarStatus;
  periodStart: string; // ISO
  periodEnd: string;
  generatedAt: string | null;
  generationMs: number | null;
  tokenCount: number | null;
  themeCount: number | null;
}

export interface RadarItem {
  id: number;
  title: string;
  description: string | null;
  url: string;
  author: string | null;
  sourceName: string;
}

export interface RadarTheme {
  id: number;
  title: string;
  summary: string;
  displayOrder: number;
  items: RadarItem[];
}

export interface RadarDetail extends RadarSummary {
  themes: RadarTheme[];
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

// ─── Interest tag ───────────────────────────────────────────────────────

export type InterestCategory =
  | "language"
  | "framework"
  | "database"
  | "devops"
  | "security"
  | "other";

export interface InterestTag {
  id: number;
  slug: string;
  displayName: string;
  category: InterestCategory | null;
}

// ─── Action proposals ───────────────────────────────────────────────────

export type ActionProposalKind = "auto_pr_cve";
export type ActionProposalStatus = "PROPOSED" | "EXECUTED" | "DISMISSED" | "FAILED";

export interface ActionProposal {
  id: number;
  radarId: number;
  kind: ActionProposalKind;
  payloadJson: string;       // raw JSON string; parsed client-side
  status: ActionProposalStatus;
  prUrl: string | null;
  failureReason: string | null;
  createdAt: string;
  updatedAt: string;
}

/** Shape of `payloadJson` for auto_pr_cve kind once parsed. */
export interface CveFixPayload {
  ghsa_id: string;
  package: string;
  current_version: string;
  repo: string;
  file_path: string;
  file_sha: string;
}

// ─── SSE events ─────────────────────────────────────────────────────────

export interface RadarStartedEvent { radarId: number }
export interface ThemeCompleteEvent {
  radarId: number;
  themeId: number;
  title: string;
  summary: string;
  itemIds: number[];
  displayOrder: number;
}
export interface RadarCompleteEvent { radarId: number; elapsedMs: number; totalTokens: number }
export interface RadarFailedEvent { radarId: number; errorCode: string; errorMessage: string }
export interface ActionProposedEvent {
  radarId: number;
  proposalId: number;
  kind: string;
  payloadJson: string;
}

// ─── Observability ──────────────────────────────────────────────────

export interface ObservabilitySummary {
  totalRadars24h: number;
  totalTokens24h: number;
  totalTokensInput24h: number;
  totalTokensOutput24h: number;
  sonnetCalls24h: number;
  haikuCalls24h: number;
  p50Ms24h: number;
  p95Ms24h: number;
  avgGenerationMs24h: number;
  cacheHitRate24h: number;
  itemsIngested24h: number;
  evalScoreRelevance: number | null;
  evalScoreCitations: number | null;
  evalScoreDistinctness: number | null;
}

export interface MetricsDay {
  date: string; // "YYYY-MM-DD"
  totalRadars: number;
  totalTokensInput: number;
  totalTokensOutput: number;
  sonnetCalls: number;
  haikuCalls: number;
  cacheHits: number;
  cacheMisses: number;
  p50Ms: number;
  p95Ms: number;
  avgGenerationMs: number;
  itemsIngested: number;
  itemsDeduped: number;
  evalScoreRelevance: number | null;
  evalScoreCitations: number | null;
  evalScoreDistinctness: number | null;
}

// ─── GitHub ─────────────────────────────────────────────────────────

export interface GitHubStatus {
  linked: boolean;
  login: string | null;
}

// ─── Notification preferences ───────────────────────────────────────

export interface NotificationPreference {
  emailEnabled: boolean;
  emailAddress: string | null;
  digestDayOfWeek: number;
  digestHourUtc: number;
}

// ─── API Keys ───────────────────────────────────────────────────────

export type ApiKeyScope = "READ" | "WRITE";

export interface ApiKeySummary {
  id: number;
  name: string;
  scope: ApiKeyScope;
  keyPrefix: string;
  createdAt: string;
  lastUsedAt: string | null;
}

export interface ApiKeyCreateRequest {
  name: string;
  scope: ApiKeyScope;
}

export interface ApiKeyCreateResponse {
  id: number;
  name: string;
  scope: ApiKeyScope;
  key: string;
  keyPrefix: string;
  createdAt: string;
}
