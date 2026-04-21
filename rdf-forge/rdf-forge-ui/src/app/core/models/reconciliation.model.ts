/**
 * Types for the Phase 8 Link Discovery / Reconciliation feature.
 */

export type MatchPredicate =
  | 'SAME_AS'
  | 'EXACT_MATCH'
  | 'CLOSE_MATCH'
  | 'RELATED_MATCH'
  | 'BROADER'
  | 'NARROWER';

export type MatcherSource = 'LOCAL_DUPLICATE' | 'MANUAL' | 'EXTERNAL_AUTHORITY';

export type MatchStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'ARCHIVED';

export interface MatchCandidate {
  id: string;
  projectId: string;
  sourceUri: string;
  targetUri: string;
  predicate: MatchPredicate;
  confidence: number;
  source: MatcherSource;
  matcherName: string;
  status: MatchStatus;
  evidence?: Record<string, unknown>;
  createdBy?: string;
  approvedBy?: string;
  createdAt: string;
  updatedAt: string;
  decidedAt?: string;
}

export interface SuggestRequest {
  projectId: string;
  sourceUri: string;
  label?: string;
  types?: string[];
  limit?: number;
  triplestoreId?: string;
  graph?: string;
  matcherIds?: string[];
}

export interface SuggestResponse {
  persisted: number;
  duplicatesSkipped: number;
  candidates: MatchCandidate[];
}

export interface ManualCandidateRequest {
  projectId: string;
  sourceUri: string;
  targetUri: string;
  predicate: MatchPredicate;
  confidence?: number;
  evidence?: Record<string, unknown>;
}

export interface MatchStats {
  projectId: string;
  pending: number;
  approved: number;
  rejected: number;
  archived: number;
  byPredicate: Record<string, number>;
  byMatcher: Record<string, number>;
}

export interface MatcherInfo {
  id: string;
  displayName: string;
  enabled: boolean;
}

export interface CandidateListFilter {
  status?: MatchStatus;
  predicate?: MatchPredicate;
  matcher?: string;
  search?: string;
}
