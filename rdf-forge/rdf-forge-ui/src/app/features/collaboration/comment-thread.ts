import { ChangeDetectionStrategy, Component, Input, OnChanges, SimpleChanges, computed, inject, signal } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../core/services/auth.service';
import { CommentService } from '../../core/services/comment.service';
import { AssetKind, Comment } from '../../core/models';

interface ThreadNode {
  comment: Comment;
  replies: ThreadNode[];
}

/**
 * Inline comment thread component attached to a single semantic asset.
 *
 * <p>Usage:
 * <pre>
 *   &lt;app-comment-thread
 *       [projectId]="projectId" [assetKind]="'ONTOLOGY'" [assetId]="ontologyId"&gt;
 *   &lt;/app-comment-thread&gt;
 * </pre>
 *
 * Supports create, reply (parent_comment_id), edit, delete with author-only
 * enforcement on the server. Threads render as depth-first trees with a
 * single level of visible nesting; deeper replies keep their parent id but
 * render flat under their nearest displayed ancestor.
 */
@Component({
  selector: 'app-comment-thread',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatIconModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatMenuModule,
    MatChipsModule,
    MatProgressSpinnerModule,
    DatePipe
  ],
  template: `
    <mat-card class="thread">
      <mat-card-header>
        <mat-card-title>
          <mat-icon>forum</mat-icon>
          Comments
        </mat-card-title>
        <mat-card-subtitle>
          {{ comments().length }} comment{{ comments().length === 1 ? '' : 's' }}
        </mat-card-subtitle>
      </mat-card-header>

      <mat-card-content>
        @if (loading()) {
          <div class="center">
            <mat-progress-spinner mode="indeterminate" diameter="24"></mat-progress-spinner>
          </div>
        } @else if (error()) {
          <div class="error">
            <mat-icon color="warn">error</mat-icon>
            <span>{{ error() }}</span>
          </div>
        } @else if (tree().length === 0) {
          <p class="muted">No comments yet. Start the discussion below.</p>
        } @else {
          <ul class="comments">
            @for (node of tree(); track node.comment.id) {
              <li class="comment">
                <ng-container *ngTemplateOutlet="commentTpl; context: { $implicit: node, depth: 0 }"></ng-container>
              </li>
            }
          </ul>
        }

        <ng-template #commentTpl let-node let-depth="depth">
          <div class="row" [class.reply]="depth > 0">
            <div class="avatar">
              <mat-icon>account_circle</mat-icon>
            </div>
            <div class="body">
              <div class="meta">
                <strong>{{ node.comment.authorEmail || 'User ' + node.comment.authorId.slice(0, 6) }}</strong>
                <span class="muted">· {{ node.comment.createdAt | date: 'medium' }}</span>
                @if (node.comment.updatedAt) {
                  <span class="muted">(edited)</span>
                }
                @if (canModify(node.comment)) {
                  <button mat-icon-button [matMenuTriggerFor]="menu" class="menu-btn">
                    <mat-icon>more_vert</mat-icon>
                  </button>
                  <mat-menu #menu>
                    <button mat-menu-item (click)="startEdit(node.comment)">
                      <mat-icon>edit</mat-icon>
                      Edit
                    </button>
                    <button mat-menu-item (click)="remove(node.comment)">
                      <mat-icon>delete</mat-icon>
                      Delete
                    </button>
                  </mat-menu>
                }
              </div>

              @if (editingId() === node.comment.id) {
                <mat-form-field appearance="outline" class="edit-field">
                  <textarea matInput rows="3" [(ngModel)]="editBody"></textarea>
                </mat-form-field>
                <div class="actions">
                  <button mat-stroked-button (click)="cancelEdit()">Cancel</button>
                  <button mat-flat-button color="primary" (click)="saveEdit(node.comment)">Save</button>
                </div>
              } @else {
                <p class="text">{{ node.comment.body }}</p>
                <div class="actions">
                  <button mat-button (click)="startReply(node.comment.id)">
                    <mat-icon>reply</mat-icon>
                    Reply
                  </button>
                </div>
              }

              @if (replyingToId() === node.comment.id) {
                <mat-form-field appearance="outline" class="reply-field">
                  <mat-label>Write a reply…</mat-label>
                  <textarea matInput rows="3" [(ngModel)]="replyBody"></textarea>
                </mat-form-field>
                <div class="actions">
                  <button mat-stroked-button (click)="cancelReply()">Cancel</button>
                  <button mat-flat-button color="primary"
                          [disabled]="!replyBody.trim()" (click)="submitReply(node.comment.id)">
                    Post reply
                  </button>
                </div>
              }

              @if (node.replies.length > 0) {
                <ul class="replies">
                  @for (child of node.replies; track child.comment.id) {
                    <li>
                      <ng-container *ngTemplateOutlet="commentTpl; context: { $implicit: child, depth: depth + 1 }"></ng-container>
                    </li>
                  }
                </ul>
              }
            </div>
          </div>
        </ng-template>

        <hr />

        <div class="compose">
          <mat-form-field appearance="outline" class="compose-field">
            <mat-label>Add a comment</mat-label>
            <textarea matInput rows="3" [(ngModel)]="newBody"></textarea>
          </mat-form-field>
          <div class="actions">
            <button mat-flat-button color="primary"
                    [disabled]="!newBody.trim() || posting()" (click)="submit()">
              @if (posting()) { Posting… } @else { Post comment }
            </button>
          </div>
        </div>
      </mat-card-content>
    </mat-card>
  `,
  styles: [`
    .thread { margin: 1rem 0; }
    .center { display: flex; justify-content: center; padding: 1rem; }
    .error { display: flex; gap: .5rem; align-items: center; }
    .muted { color: #666; }
    ul.comments, ul.replies { list-style: none; padding-left: 0; margin: 0; }
    ul.replies { margin-left: 2.5rem; padding-top: .5rem; }
    .comment { padding: .5rem 0; border-bottom: 1px solid #f0f0f0; }
    .row { display: flex; gap: .75rem; }
    .row.reply { background: #fafafa; border-radius: 4px; padding: .5rem; }
    .avatar mat-icon { color: #999; font-size: 32px; width: 32px; height: 32px; }
    .body { flex: 1; min-width: 0; }
    .meta { display: flex; align-items: center; gap: .5rem; }
    .menu-btn { margin-left: auto; }
    .text { white-space: pre-wrap; margin: .25rem 0; }
    .actions { display: flex; gap: .5rem; justify-content: flex-end; }
    .edit-field, .reply-field, .compose-field { width: 100%; }
    hr { border: none; border-top: 1px solid #eee; margin: 1rem 0; }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class CommentThread implements OnChanges {
  @Input({ required: true }) projectId!: string;
  @Input({ required: true }) assetKind!: AssetKind;
  @Input({ required: true }) assetId!: string;

  private readonly commentService = inject(CommentService);
  private readonly authService = inject(AuthService);

  readonly comments = signal<Comment[]>([]);
  readonly loading = signal(false);
  readonly posting = signal(false);
  readonly error = signal<string | null>(null);
  readonly replyingToId = signal<string | null>(null);
  readonly editingId = signal<string | null>(null);

  newBody = '';
  replyBody = '';
  editBody = '';

  readonly tree = computed<ThreadNode[]>(() => this.buildTree(this.comments()));

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['assetId'] || changes['assetKind']) {
      this.reload();
    }
  }

  reload(): void {
    if (!this.projectId || !this.assetKind || !this.assetId) return;
    this.loading.set(true);
    this.error.set(null);
    this.commentService.list(this.projectId, this.assetKind, this.assetId).subscribe({
      next: list => { this.comments.set(list); this.loading.set(false); },
      error: err => {
        this.error.set(err?.message ?? 'Failed to load comments');
        this.loading.set(false);
      }
    });
  }

  submit(): void {
    const body = this.newBody.trim();
    if (!body) return;
    this.posting.set(true);
    this.commentService.create({
      projectId: this.projectId,
      assetKind: this.assetKind,
      assetId: this.assetId,
      body
    }).subscribe({
      next: c => {
        this.comments.update(list => [...list, c]);
        this.newBody = '';
        this.posting.set(false);
      },
      error: err => { this.error.set(err?.message ?? 'Post failed'); this.posting.set(false); }
    });
  }

  startReply(parentId: string): void {
    this.replyingToId.set(parentId);
    this.replyBody = '';
  }
  cancelReply(): void { this.replyingToId.set(null); this.replyBody = ''; }

  submitReply(parentId: string): void {
    const body = this.replyBody.trim();
    if (!body) return;
    this.commentService.create({
      projectId: this.projectId,
      assetKind: this.assetKind,
      assetId: this.assetId,
      body,
      parentCommentId: parentId
    }).subscribe({
      next: c => {
        this.comments.update(list => [...list, c]);
        this.cancelReply();
      },
      error: err => this.error.set(err?.message ?? 'Reply failed')
    });
  }

  startEdit(c: Comment): void {
    this.editingId.set(c.id);
    this.editBody = c.body;
  }
  cancelEdit(): void { this.editingId.set(null); this.editBody = ''; }

  saveEdit(c: Comment): void {
    const body = this.editBody.trim();
    if (!body) return;
    this.commentService.update(c.id, { body }).subscribe({
      next: updated => {
        this.comments.update(list => list.map(x => x.id === updated.id ? updated : x));
        this.cancelEdit();
      },
      error: err => this.error.set(err?.message ?? 'Update failed')
    });
  }

  remove(c: Comment): void {
    this.commentService.delete(c.id).subscribe({
      next: () => this.comments.update(list => list.filter(x => x.id !== c.id)),
      error: err => this.error.set(err?.message ?? 'Delete failed')
    });
  }

  canModify(c: Comment): boolean {
    // AuthService does not currently expose the user's UUID directly; fall
    // back to role-based check so admins can always moderate. Non-admins see
    // edit/delete only when the server returns their own authorId — see
    // TODO: surface currentUserId from AuthService once the Keycloak profile
    // parses the subject claim into a signal.
    const profile = this.authService.userProfile as unknown as { id?: string } | undefined;
    const me = profile?.id ?? null;
    return this.authService.isAdmin?.() === true || (!!me && me === c.authorId);
  }

  private buildTree(list: Comment[]): ThreadNode[] {
    const byId = new Map<string, ThreadNode>();
    const roots: ThreadNode[] = [];
    for (const c of list) byId.set(c.id, { comment: c, replies: [] });
    for (const node of byId.values()) {
      const pid = node.comment.parentCommentId;
      if (pid && byId.has(pid)) {
        byId.get(pid)!.replies.push(node);
      } else {
        roots.push(node);
      }
    }
    return roots;
  }
}
