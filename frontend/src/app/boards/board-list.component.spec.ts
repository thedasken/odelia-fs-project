import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { AuthService } from '../auth/auth.service';
import { routes } from '../app.routes';
import { BoardListComponent } from './board-list.component';

describe('BoardListComponent', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BoardListComponent],
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('PUTs the trimmed name and re-sorts the list', () => {
    const fixture = TestBed.createComponent(BoardListComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();

    http.expectOne('/api/boards').flush([
      { id: 1, name: 'Zebra' },
      { id: 2, name: 'Apple' }
    ]);

    const board = component.boards().find(b => b.id === 1)!;
    component.startRename(board);
    component.renameDraft = '  Aardvark  ';
    component.saveRename(board);

    const put = http.expectOne('/api/boards/1');
    expect(put.request.method).toBe('PUT');
    expect(put.request.body).toEqual({ name: 'Aardvark' });
    put.flush({ id: 1, name: 'Aardvark' });

    expect(component.boards().map(b => b.name)).toEqual(['Aardvark', 'Apple']);
    expect(component.editingBoardId()).toBeNull();
  });

  it('lets only the owner or an admin rename and delete a board', () => {
    const component = TestBed.createComponent(BoardListComponent).componentInstance;
    const auth = TestBed.inject(AuthService);
    const board = { id: 1, name: 'Shared', owner: 'alice' };

    auth.username.set('bob');
    expect(component.canManage(board)).toBeFalse();
    auth.username.set('alice');
    expect(component.canManage(board)).toBeTrue();
    auth.username.set('carol');
    auth.roles.set(['admin']);
    expect(component.canManage(board)).toBeTrue();
  });
});
