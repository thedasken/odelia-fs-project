import { TestBed, fakeAsync, discardPeriodicTasks, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, Router } from '@angular/router';

import { AuthService } from '../auth/auth.service';
import { BoardDetailComponent } from './board-detail.component';

describe('BoardDetailComponent', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BoardDetailComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: new Map([['id', '7']]) } } }
      ]
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads the board and its columns on init', () => {
    const fixture = TestBed.createComponent(BoardDetailComponent);
    fixture.detectChanges();

    http.expectOne('/api/boards/7').flush({ id: 7, name: 'Sprint 1' });
    http.expectOne('/api/boards/7/members').flush([]);

    const listsReq = http.expectOne('/api/boards/7/lists');
    listsReq.flush([{ id: 1, boardId: 7, name: 'To do', position: 0 }]);

    http.expectOne('/api/lists/1/cards').flush([
      { id: 10, listId: 1, title: 'First card', position: 0 }
    ]);

    expect(fixture.componentInstance.board()?.name).toBe('Sprint 1');
    expect(fixture.componentInstance.cardsFor(1).length).toBe(1);
  });

  it('PATCHes the card when it is dropped into another column', () => {
    const fixture = TestBed.createComponent(BoardDetailComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();

    http.expectOne('/api/boards/7').flush({ id: 7, name: 'Sprint 1' });
    http.expectOne('/api/boards/7/members').flush([]);
    http.expectOne('/api/boards/7/lists').flush([
      { id: 1, boardId: 7, name: 'To do', position: 0 },
      { id: 2, boardId: 7, name: 'Doing', position: 1 }
    ]);
    http.expectOne('/api/lists/1/cards').flush([{ id: 10, listId: 1, title: 'Card', position: 0 }]);
    http.expectOne('/api/lists/2/cards').flush([]);

    const card = component.cardsFor(1)[0];
    component.drop({
      item: { data: card },
      container: { data: { id: 2 } },
      previousContainer: { data: { id: 1 } },
      previousIndex: 0,
      currentIndex: 0
    } as never);

    const move = http.expectOne('/api/cards/10/move');
    expect(move.request.method).toBe('PATCH');
    expect(move.request.body).toEqual({ targetListId: 2, position: 0 });
    move.flush({ id: 10, listId: 2, title: 'Card', position: 0 });

    expect(component.cardsFor(1).length).toBe(0);
    expect(component.cardsFor(2).length).toBe(1);
  });

  it('PUTs the card and updates it in place when the inline editor is saved', () => {
    const fixture = TestBed.createComponent(BoardDetailComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();

    http.expectOne('/api/boards/7').flush({ id: 7, name: 'Sprint 1' });
    http.expectOne('/api/boards/7/members').flush([]);
    http.expectOne('/api/boards/7/lists').flush([
      { id: 1, boardId: 7, name: 'To do', position: 0 }
    ]);
    http.expectOne('/api/lists/1/cards').flush([
      { id: 10, listId: 1, title: 'Card', position: 0 }
    ]);

    const card = component.cardsFor(1)[0];
    component.startEdit(card);
    expect(component.editingCardId()).toBe(10);

    component.editDraft.title = 'Card renamed';
    component.editDraft.description = 'Now with details';
    component.saveEdit(card);

    const put = http.expectOne('/api/cards/10');
    expect(put.request.method).toBe('PUT');
    expect(put.request.body).toEqual({ title: 'Card renamed', description: 'Now with details' });
    put.flush({ id: 10, listId: 1, title: 'Card renamed', description: 'Now with details', position: 0 });

    expect(component.editingCardId()).toBeNull();
    expect(component.cardsFor(1)[0].description).toBe('Now with details');
  });

  it('opens and closes the "add a card" composer one column at a time', () => {
    const fixture = TestBed.createComponent(BoardDetailComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();

    http.expectOne('/api/boards/7').flush({ id: 7, name: 'Sprint 1' });
    http.expectOne('/api/boards/7/members').flush([]);
    http.expectOne('/api/boards/7/lists').flush([
      { id: 1, boardId: 7, name: 'To do', position: 0 }
    ]);
    http.expectOne('/api/lists/1/cards').flush([]);

    expect(component.composingColumnId()).toBeNull();

    component.startComposing(1);
    expect(component.composingColumnId()).toBe(1);

    component.cancelComposing();
    expect(component.composingColumnId()).toBeNull();
  });

  it('POSTs title and description from the "add a card" form', () => {
    const fixture = TestBed.createComponent(BoardDetailComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();

    http.expectOne('/api/boards/7').flush({ id: 7, name: 'Sprint 1' });
    http.expectOne('/api/boards/7/members').flush([]);
    http.expectOne('/api/boards/7/lists').flush([
      { id: 1, boardId: 7, name: 'To do', position: 0 }
    ]);
    http.expectOne('/api/lists/1/cards').flush([]);

    const column = component.columns()[0];
    const draft = component.draftFor(1);
    draft.title = 'New card';
    draft.description = 'With a description';
    component.addCard(column);

    const post = http.expectOne('/api/lists/1/cards');
    expect(post.request.method).toBe('POST');
    expect(post.request.body).toEqual({ title: 'New card', description: 'With a description' });
    post.flush({ id: 20, listId: 1, title: 'New card', description: 'With a description', position: 0 });

    expect(component.cardsFor(1).length).toBe(1);
    expect(draft.title).toBe('');
    expect(draft.description).toBe('');
  });

  it('adds and removes a member, and shows a readable error on a 400', () => {
    const fixture = TestBed.createComponent(BoardDetailComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();

    http.expectOne('/api/boards/7').flush({ id: 7, name: 'Sprint 1', owner: 'alice' });
    http.expectOne('/api/boards/7/members').flush([]);
    http.expectOne('/api/boards/7/lists').flush([]);

    component.newMemberName = '  bob  ';
    component.addMember();
    const post = http.expectOne('/api/boards/7/members');
    expect(post.request.method).toBe('POST');
    expect(post.request.body).toEqual({ username: 'bob' });
    post.flush({ id: 1, boardId: 7, username: 'bob' });
    expect(component.members().map(m => m.username)).toEqual(['bob']);

    component.newMemberName = 'bob';
    component.addMember();
    http.expectOne('/api/boards/7/members').flush('', { status: 400, statusText: 'Bad Request' });
    expect(component.error()).toContain('already a member');

    component.removeMember(component.members()[0]);
    const del = http.expectOne('/api/boards/7/members/bob');
    expect(del.request.method).toBe('DELETE');
    del.flush(null);
    expect(component.members().length).toBe(0);
  });

  it('renders the member controls for the owner and an admin, not for a member', () => {
    const fixture = TestBed.createComponent(BoardDetailComponent);
    const auth = TestBed.inject(AuthService);
    fixture.detectChanges();

    http.expectOne('/api/boards/7').flush({ id: 7, name: 'Sprint 1', owner: 'alice' });
    http.expectOne('/api/boards/7/members').flush([{ id: 1, boardId: 7, username: 'bob' }]);
    http.expectOne('/api/boards/7/lists').flush([]);

    const controls = () => {
      fixture.detectChanges();
      const el: HTMLElement = fixture.nativeElement;
      return {
        members: el.querySelectorAll('.members li').length,
        form: !!el.querySelector('form.new-member'),
        remove: el.querySelectorAll('.members li button').length
      };
    };

    auth.username.set('bob');
    expect(controls()).toEqual({ members: 1, form: false, remove: 0 });

    auth.username.set('alice');
    expect(controls()).toEqual({ members: 1, form: true, remove: 2 });

    auth.username.set('carol');
    auth.roles.set(['admin']);
    expect(controls()).toEqual({ members: 1, form: true, remove: 2 });
  });

  it('shows a readable error when adding or removing a member is refused with a 403', () => {
    const fixture = TestBed.createComponent(BoardDetailComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();

    http.expectOne('/api/boards/7').flush({ id: 7, name: 'Sprint 1', owner: 'alice' });
    http.expectOne('/api/boards/7/members').flush([{ id: 1, boardId: 7, username: 'bob' }]);
    http.expectOne('/api/boards/7/lists').flush([]);

    component.newMemberName = 'carol';
    component.addMember();
    http.expectOne('/api/boards/7/members').flush('', { status: 403, statusText: 'Forbidden' });
    expect(component.error()).toContain('only the owner can manage members');
    expect(component.members().length).toBe(1);

    component.removeMember(component.members()[0]);
    http.expectOne('/api/boards/7/members/bob').flush('', { status: 403, statusText: 'Forbidden' });
    expect(component.error()).toContain('only the owner can manage members');
    expect(component.members().length).toBe(1);
  });

  it('POSTs the transfer, then shows the new owner and reloads the members', () => {
    const fixture = TestBed.createComponent(BoardDetailComponent);
    const component = fixture.componentInstance;
    spyOn(window, 'confirm').and.returnValue(true);
    fixture.detectChanges();

    http.expectOne('/api/boards/7').flush({ id: 7, name: 'Sprint 1', owner: 'alice' });
    http.expectOne('/api/boards/7/members').flush([{ id: 1, boardId: 7, username: 'bob' }]);
    http.expectOne('/api/boards/7/lists').flush([]);

    component.makeOwner(component.members()[0]);
    const post = http.expectOne('/api/boards/7/owner');
    expect(post.request.method).toBe('POST');
    expect(post.request.body).toEqual({ username: 'bob' });
    post.flush({ id: 7, name: 'Sprint 1', owner: 'bob' });
    http.expectOne('/api/boards/7/members').flush([{ id: 2, boardId: 7, username: 'alice' }]);

    expect(component.board()?.owner).toBe('bob');
    expect(component.members().map(m => m.username)).toEqual(['alice']);
  });

  it('refreshes the board and members periodically', fakeAsync(() => {
    const fixture = TestBed.createComponent(BoardDetailComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();

    http.expectOne('/api/boards/7').flush({ id: 7, name: 'Sprint 1', owner: 'alice' });
    http.expectOne('/api/boards/7/members').flush([]);
    http.expectOne('/api/boards/7/lists').flush([]);

    tick(10_000);
    http.expectOne('/api/boards/7').flush({ id: 7, name: 'Sprint 1', owner: 'alice' });
    http.expectOne('/api/boards/7/members').flush([{ id: 1, boardId: 7, username: 'bob' }]);
    expect(component.members().map(m => m.username)).toEqual(['bob']);

    // Destroying the component stops the polling timer before fakeAsync ends.
    fixture.destroy();
    discardPeriodicTasks();
  }));

  it('goes back to the board list when access is lost', fakeAsync(() => {
    const fixture = TestBed.createComponent(BoardDetailComponent);
    const navigate = spyOn(TestBed.inject(Router), 'navigate');
    fixture.detectChanges();

    http.expectOne('/api/boards/7').flush({ id: 7, name: 'Sprint 1', owner: 'alice' });
    http.expectOne('/api/boards/7/members').flush([]);
    http.expectOne('/api/boards/7/lists').flush([]);

    tick(10_000);
    // forkJoin cancels the members request as soon as the board request fails.
    http.expectOne('/api/boards/7').flush('', { status: 403, statusText: 'Forbidden' });
    http.match('/api/boards/7/members');
    expect(navigate).toHaveBeenCalledWith(['/']);

    // Destroying the component stops the polling timer before fakeAsync ends.
    fixture.destroy();
    discardPeriodicTasks();
  }));

  it('goes back to the board list at once when the board cannot be opened', () => {
    const fixture = TestBed.createComponent(BoardDetailComponent);
    const navigate = spyOn(TestBed.inject(Router), 'navigate');
    fixture.detectChanges();

    http.expectOne('/api/boards/7').flush('', { status: 403, statusText: 'Forbidden' });
    http.expectOne('/api/boards/7/members').flush('', { status: 403, statusText: 'Forbidden' });
    http.expectOne('/api/boards/7/lists').flush('', { status: 404, statusText: 'Not Found' });

    expect(navigate).toHaveBeenCalledWith(['/']);
    expect(fixture.componentInstance.error()).toBeTruthy();
  });
});
