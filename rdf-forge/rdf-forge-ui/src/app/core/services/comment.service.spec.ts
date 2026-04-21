import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { CommentService } from './comment.service';
import { environment } from '../../../environments/environment';
import { Comment } from '../models/comment.model';

describe('CommentService', () => {
  let service: CommentService;
  let http: HttpTestingController;

  const now = '2026-04-21T12:00:00Z';
  const sample: Comment = {
    id: '11111111-1111-1111-1111-111111111111',
    projectId: 'p1',
    assetKind: 'ONTOLOGY',
    assetId: 'o1',
    body: 'nice shape',
    authorId: 'u1',
    authorEmail: 'u1@example.org',
    createdAt: now
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [CommentService]
    });
    service = TestBed.inject(CommentService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('list requests /comments with query params', () => {
    service.list('ONTOLOGY', 'o1').subscribe(list => expect(list).toEqual([sample]));
    const req = http.expectOne(r =>
      r.url === `${environment.apiBaseUrl}/comments` &&
      r.params.get('assetKind') === 'ONTOLOGY' &&
      r.params.get('assetId') === 'o1'
    );
    req.flush([sample]);
  });

  it('create POSTs the request body', () => {
    const body = {
      projectId: 'p1',
      assetKind: 'ONTOLOGY' as const,
      assetId: 'o1',
      body: 'new'
    };
    service.create(body).subscribe(c => expect(c).toEqual(sample));
    const req = http.expectOne(`${environment.apiBaseUrl}/comments`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush(sample);
  });

  it('delete DELETEs /comments/{id}', () => {
    service.delete('11111111-1111-1111-1111-111111111111').subscribe();
    const req = http.expectOne(
      `${environment.apiBaseUrl}/comments/11111111-1111-1111-1111-111111111111`
    );
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
