import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterOutlet } from '@angular/router';

import { EMPTY, catchError, filter, switchMap, timer } from 'rxjs';

import { AuthService } from './auth/auth.service';
import { Notification } from './models/board';
import { BoardService } from './services/board.service';

/** How often the notifications are fetched again, in milliseconds. */
const NOTIFICATION_REFRESH_MS = 10_000;

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent {
  /** Public so the header can show who is signed in and offer to sign out. */
  readonly auth = inject(AuthService);

  private readonly boardService = inject(BoardService);

  title = 'kanban';

  readonly notifications = signal<Notification[]>([]);
  readonly unreadCount = computed(() => this.notifications().filter(n => !n.read).length);
  readonly notificationsOpen = signal(false);

  constructor() {
    // Periodic refresh: there is no push channel, so new notifications show up
    // within a few seconds. A failed call is skipped and retried on the next tick.
    timer(0, NOTIFICATION_REFRESH_MS)
      .pipe(
        filter(() => this.auth.authenticated()),
        switchMap(() => this.boardService.getNotifications().pipe(catchError(() => EMPTY))),
        takeUntilDestroyed()
      )
      .subscribe(notifications => this.notifications.set(notifications));
  }

  markRead(notification: Notification): void {
    if (notification.read) {
      return;
    }
    this.boardService.markNotificationRead(notification.id).subscribe({
      next: () =>
        this.notifications.update(list => list.map(n => (n.id === notification.id ? { ...n, read: true } : n))),
      error: err => console.error('Could not mark the notification as read', err)
    });
  }
}
