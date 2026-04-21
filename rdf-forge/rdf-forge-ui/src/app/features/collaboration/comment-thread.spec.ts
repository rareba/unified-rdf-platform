import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { CommentThread } from './comment-thread';
import { environment } from '../../../environments/environment';
import { Comment } from '../../core/models';
import { AuthService } from '../../core/services/auth.service';

describe('CommentThread', () => {
  let fixture: ComponentFixture<CommentThread>;
  let http: HttpTestingController;

  const existing: Comment = {
    id: 'c1',
    projectId: 'p1',
    assetKind: 'ONTOLOGY',
    assetId: 'o1',
    body: 'first',
    authorId: 'u1',
    createdAt: '2026-04-21T00:00:00Z'
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CommentThread, HttpClientTestingModule, NoopAnimationsModule],
      providers: [
        { provide: AuthService, useValue: { isAdmin: () => false, userProfile: undefined } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(CommentThread);
    fixture.componentInstance.projectId = 'p1';
    fixture.componentInstance.assetKind = 'ONTOLOGY';
    fixture.componentInstance.assetId = 'o1';
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    // trigger ngOnChanges via signal-free path
    fixture.componentInstance.ngOnChanges({
      assetId: { currentValue: 'o1', previousValue: null, firstChange: true, isFirstChange: () => true }
    });
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('loads comments on init', () => {
    const req = http.expectOne(r =>
      r.url === `${environment.apiBaseUrl}/comments` &&
      r.params.get('assetKind') === 'ONTOLOGY' &&
      r.params.get('assetId') === 'o1'
    );
    req.flush([existing]);
    fixture.detectChanges();

    expect(fixture.componentInstance.comments().length).toBe(1);
    expect(fixture.componentInstance.tree().length).toBe(1);
  });

  it('posts a new comment and appends it', () => {
    const load = http.expectOne(r => r.url === `${environment.apiBaseUrl}/comments`);
    load.flush([]);
    fixture.detectChanges();

    fixture.componentInstance.newBody = 'hello';
    fixture.componentInstance.submit();

    const post = http.expectOne(`${environment.apiBaseUrl}/comments`);
    expect(post.request.method).toBe('POST');
    expect(post.request.body.body).toBe('hello');
    post.flush({ ...existing, body: 'hello' });
    fixture.detectChanges();

    expect(fixture.componentInstance.comments().length).toBe(1);
  });
});
