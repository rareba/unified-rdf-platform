/** Matches backend {@code io.rdfforge.pipeline.entity.CommentEntity.AssetKind}. */
export type AssetKind =
  | 'ONTOLOGY'
  | 'SHAPE'
  | 'MAPPING'
  | 'CUBE'
  | 'DIMENSION'
  | 'VALIDATION_SUITE'
  | 'RELEASE'
  | 'PROJECT';

export interface Comment {
  id: string;
  projectId: string;
  assetKind: AssetKind;
  assetId: string;
  body: string;
  authorId: string;
  authorEmail?: string;
  parentCommentId?: string | null;
  createdAt: string;
  updatedAt?: string | null;
}

export interface CommentCreateRequest {
  projectId: string;
  assetKind: AssetKind;
  assetId: string;
  body: string;
  parentCommentId?: string | null;
}

export interface CommentUpdateRequest {
  body: string;
}
