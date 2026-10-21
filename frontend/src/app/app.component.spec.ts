import { TestBed, fakeAsync, discardPeriodicTasks, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { AuthService } from './auth/auth.service';
import { AppComponent } from './app.component';
import { routes } from './app.routes';

describe('AppComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(AppComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render the title', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('h1').textContent).toContain('Kanban');
  });

  it('fetches the notifications periodically once signed in and counts the unread ones', fakeAsync(() => {
    TestBed.inject(AuthService).authenticated.set(true);
    const http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(AppComponent);
    const component = fixture.componentInstance;

    tick(0);
    http.expectOne('/api/notifications').flush([
      { id: 1, message: 'a', read: false },
      { id: 2, message: 'b', read: true }
    ]);
    expect(component.unreadCount()).toBe(1);

    tick(10_000);
    http.expectOne('/api/notifications').flush([]);
    expect(component.unreadCount()).toBe(0);

    fixture.destroy();
    discardPeriodicTasks();
  }));

  it('POSTs to mark a notification as read', () => {
    const http = TestBed.inject(HttpTestingController);
    const component = TestBed.createComponent(AppComponent).componentInstance;
    component.notifications.set([{ id: 5, message: 'hi', read: false }]);

    component.markRead(component.notifications()[0]);
    const post = http.expectOne('/api/notifications/5/read');
    expect(post.request.method).toBe('POST');
    post.flush(null);

    expect(component.unreadCount()).toBe(0);
  });

  it('renders each notification as a button so it works with the keyboard', () => {
    const fixture = TestBed.createComponent(AppComponent);
    TestBed.inject(AuthService).authenticated.set(true);
    fixture.componentInstance.notifications.set([{ id: 5, message: 'hi', read: false }]);
    fixture.componentInstance.notificationsOpen.set(true);
    fixture.detectChanges();
    TestBed.inject(HttpTestingController).match('/api/notifications');

    const button: HTMLButtonElement = fixture.nativeElement.querySelector('.notification-list button.notification');
    expect(button.textContent).toContain('hi');
  });
});
