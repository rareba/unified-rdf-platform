import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import {
  AssetKind,
  Comment,
  CommentCreateRequest,
  CommentUpdateRequest
} from '../models/comment.model';

/**
 * Client for {@code /api/v1/comments}. The backend hosts the endpoint once
 * (in pipeline-service) and serves comments across every asset kind.
 */
@Injectable({ providedIn: 'root' })
export class CommentService {
  private readonly api = inject(ApiService);

  /** All non-deleted comments for the given asset, oldest first. */
  list(assetKind: AssetKind, assetId: string): Observable<Comment[]> {
    return this.api.getArray<Comment>('/comments', { assetKind, assetId });
  }

  create(request: CommentCreateRequest): Observable<Comment> {
    return this.api.post<Comment>('/comments', request);
  }

  update(id: string, request: CommentUpdateRequest): Observable<Comment> {
    return this.api.put<Comment>(`/comments/${id}`, request);
  }

  delete(id: string): Observable<void> {
    return this.api.delete<void>(`/comments/${id}`);
  }
}
