import { effect, inject, Injectable, signal } from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { AuthService } from "./auth.service";
import { apiUrl } from "./api-url";
export interface Activity {
  id: number;
  actorName: string;
  action: string;
  message: string;
  country: string | null;
  project: string | null;
  createdAt: string;
}
export interface Announcement {
  id: number;
  authorName: string;
  title: string;
  content: string;
  country: string | null;
  projects: string[];
  createdAt: string;
}
@Injectable({ providedIn: "root" })
export class CommunicationService {
  private http = inject(HttpClient);
  private auth = inject(AuthService);
  readonly events = signal<Activity[]>([]);
  readonly announcements = signal<Announcement[]>([]);
  readonly connected = signal(false);
  readonly error = signal("");
  readonly seen = signal(0);
  private source?: EventSource;
  constructor() {
    effect((onCleanup) => {
      const user = this.auth.user();
      if (!user) {
        this.events.set([]);
        this.announcements.set([]);
        this.connected.set(false);
        return;
      }
      try {
        this.seen.set(
          Number(localStorage.getItem("angi-notifications-" + user.id) || 0),
        );
      } catch {}
      this.reload();
      const source = new EventSource(apiUrl("/api/communications/stream"), {
        withCredentials: true,
      });
      this.source = source;
      source.onopen = () => this.connected.set(true);
      source.addEventListener("refresh", () => {
        this.connected.set(true);
        this.reload();
      });
      source.onerror = () => {
        this.connected.set(false);
      };
      onCleanup(() => {
        source.close();
        this.connected.set(false);
      });
    });
  }
  reload() {
    const userId = this.auth.user()?.id;
    if (!userId) return;
    this.http.get<Activity[]>("/api/communications/notifications").subscribe({
      next: (e) => {
        if (this.auth.user()?.id === userId) {
          this.events.set(e);
          this.error.set("");
        }
      },
      error: () => this.error.set("Actualisation indisponible. Réessayez."),
    });
    this.http
      .get<Announcement[]>("/api/communications/announcements")
      .subscribe({
        next: (a) => {
          if (this.auth.user()?.id === userId) this.announcements.set(a);
        },
        error: () => this.error.set("Chargement des communiqués indisponible."),
      });
  }
  unread() {
    return this.events().filter((e) => e.id > this.seen()).length;
  }
  markSeen() {
    const id = this.events()[0]?.id || 0;
    this.seen.set(id);
    try {
      localStorage.setItem(
        "angi-notifications-" + this.auth.user()?.id,
        String(id),
      );
    } catch {}
  }
  publish(data: {
    title: string;
    content: string;
    country: string | null;
    projects: string[];
  }) {
    return this.http.post<Announcement>(
      "/api/communications/announcements",
      data,
    );
  }
}
