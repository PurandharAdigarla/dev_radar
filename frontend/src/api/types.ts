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
}

export interface RadarItem {
  id: number;
  title: string;
  url: string;
  author: string | null;
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

export type ActionProposalKind = "CVE_FIX_PR";
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

/** Shape of `payloadJson` for CVE_FIX_PR kind once parsed. */
export interface CveFixPayload {
  cveId: string;
  packageName: string;
  currentVersion: string;
  fixVersion: string;
  repoOwner: string;
  repoName: string;
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
