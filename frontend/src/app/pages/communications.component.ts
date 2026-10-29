import { Component, inject, signal } from "@angular/core";
import { DatePipe } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { finalize } from "rxjs";
import { CommunicationService } from "../core/communication.service";
import { AuthService } from "../core/auth.service";
import { SalesService } from "../core/sales.service";
import { apiError } from "../core/report.service";
@Component({
  standalone: true,
  imports: [DatePipe, FormsModule],
  template: `
    <div class="page-heading">
      <div>
        <p class="eyebrow">Le fil officiel</p>
        <h1>Communiqués</h1>
        <p class="text-secondary">
          Les annonces de votre direction, réunies au même endroit.
        </p>
      </div>
      <span class="team-pill"
        >{{ comms.announcements().length }} annonce(s)</span
      >
    </div>
    @if (error() || comms.error()) {
      <div class="alert alert-danger" role="alert">
        {{ error() || comms.error() }}
        <button class="btn btn-sm btn-outline-danger" (click)="comms.reload()">
          Réessayer
        </button>
      </div>
    }
    @if (success()) {
      <div role="status" class="alert alert-success">{{ success() }}</div>
    }
    @if (auth.isDirector()) {
      <details class="panel mb-4">
        <summary class="fw-semibold">Publier un communiqué</summary>
        <form #f="ngForm" (ngSubmit)="publish()" class="mt-3">
          <div class="row g-3">
            <div class="col-12">
              <label for="announcementTitle" class="form-label"
                >Titre du communiqué</label
              ><input
                id="announcementTitle"
                name="title"
                [(ngModel)]="title"
                class="form-control"
                required
                maxlength="160"
                pattern=".*\\S.*"
              />
            </div>
            <div class="col-12">
              <label for="announcementContent" class="form-label"
                >Message officiel</label
              ><textarea
                id="announcementContent"
                name="content"
                [(ngModel)]="content"
                class="form-control"
                rows="5"
                required
                maxlength="8000"
              ></textarea>
            </div>
            <div class="col-sm-6">
              <label for="announcementCountry" class="form-label"
                >Pays destinataire</label
              ><select
                id="announcementCountry"
                name="country"
                [(ngModel)]="country"
                class="form-select"
              >
                @if (auth.isSuperAdmin()) {
                  <option value="">Tous les pays</option>
                }
                @for (c of countries(); track c.code) {
                  @if (auth.isSuperAdmin() || c.code === auth.user()?.country) {
                    <option [value]="c.code">{{ c.name }}</option>
                  }
                }
              </select>
            </div>
            <fieldset class="col-sm-6">
              <legend class="form-label">Projets destinataires</legend>
              @for (p of projects(); track p) {
                <label class="check-chip"
                  ><input
                    type="checkbox"
                    [checked]="selected.includes(p)"
                    (change)="toggle(p)"
                  />{{ p }}</label
                >
              }
              @if (auth.isSuperAdmin()) {
                <p class="form-text">Sans sélection : tous les projets.</p>
              }
            </fieldset>
          </div>
          <button
            class="btn btn-primary mt-3"
            [disabled]="
              f.invalid ||
              busy() ||
              !content.trim() ||
              (!auth.isSuperAdmin() && !selected.length)
            "
          >
            {{ busy() ? "Publication…" : "Diffuser le communiqué" }}
          </button>
        </form>
      </details>
    }
    <div class="announcement-list">
      @for (a of comms.announcements(); track a.id) {
        <article class="panel announcement-card">
          <div class="d-flex justify-content-between flex-wrap gap-2">
            <span class="eyebrow">Communiqué officiel</span
            ><time class="small text-secondary">{{
              a.createdAt | date: "dd/MM/yyyy HH:mm"
            }}</time>
          </div>
          <h2 class="h4">{{ a.title }}</h2>
          <p class="announcement-body">{{ a.content }}</p>
          <div class="announcement-meta">
            <strong>{{ a.authorName }}</strong
            ><span
              >{{ countryName(a.country) }} ·
              {{
                a.projects.length ? a.projects.join(" / ") : "Tous les projets"
              }}</span
            >
          </div>
        </article>
      } @empty {
        <div class="panel empty">
          <i class="bi bi-megaphone empty-icon" aria-hidden="true"></i>
          <h2 class="h5">Aucun communiqué pour le moment</h2>
          <p>Les annonces qui vous concernent apparaîtront ici.</p>
        </div>
      }
    </div>
  `,
})
export class CommunicationsComponent {
  readonly comms = inject(CommunicationService);
  readonly auth = inject(AuthService);
  private sales = inject(SalesService);
  readonly countries = signal<{ code: string; name: string }[]>([]);
  readonly error = signal("");
  readonly success = signal("");
  readonly busy = signal(false);
  title = "";
  content = "";
  country = this.auth.isSuperAdmin() ? "" : this.auth.user()?.country || "BJ";
  selected: string[] = this.auth.isSuperAdmin()
    ? []
    : [...(this.auth.user()?.projects || [])];
  constructor() {
    this.sales.countries().subscribe({
      next: (c) => this.countries.set(c),
      error: (e) => this.error.set(apiError(e)),
    });
    this.comms.reload();
  }
  projects() {
    return this.auth.isSuperAdmin()
      ? ["AZO", "ZEHAMI"]
      : this.auth.user()?.projects || [];
  }
  countryName(code: string | null) {
    return code
      ? this.countries().find((c) => c.code === code)?.name || code
      : "Tous les pays";
  }
  toggle(p: string) {
    this.selected = this.selected.includes(p)
      ? this.selected.filter((x) => x !== p)
      : [...this.selected, p];
  }
  publish() {
    if (this.busy()) return;
    this.busy.set(true);
    this.error.set("");
    this.success.set("");
    this.comms
      .publish({
        title: this.title,
        content: this.content,
        country: this.country || null,
        projects: this.selected,
      })
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: () => {
          this.title = "";
          this.content = "";
          this.success.set("Communiqué publié.");
          this.comms.reload();
        },
        error: (e) => this.error.set(apiError(e)),
      });
  }
}
@Component({
  standalone: true,
  imports: [DatePipe],
  template: `
    <div class="page-heading">
      <div>
        <p class="eyebrow">Suivi de l’activité</p>
        <h1>Notifications</h1>
        <p class="text-secondary">
          Les décisions et les actions qui concernent votre périmètre.
        </p>
      </div>
      <button class="btn btn-outline-primary" (click)="comms.markSeen()">
        Tout marquer comme lu
      </button>
    </div>
    <p class="live-status" [class.online]="comms.connected()">
      <span></span
      >{{
        comms.connected() ? "Actualisation en direct" : "Reconnexion en cours"
      }}
      <button class="btn btn-sm btn-link" (click)="comms.reload()">
        Actualiser
      </button>
    </p>
    @if (comms.error()) {
      <div role="alert" class="alert alert-danger">{{ comms.error() }}</div>
    }
    <section class="panel activity-list">
      @for (e of comms.events(); track e.id) {
        <article class="activity-item" [class.unread]="e.id > comms.seen()">
          <div class="activity-dot">
            <i class="bi bi-activity" aria-hidden="true"></i>
          </div>
          <div>
            <p class="mb-1">{{ e.message }}</p>
            <p class="small text-secondary mb-0">
              {{ e.actorName }} · {{ e.createdAt | date: "dd/MM/yyyy HH:mm" }}
              @if (e.country) {
                · {{ e.country }}
              }
              @if (e.project) {
                · {{ e.project }}
              }
            </p>
          </div>
        </article>
      } @empty {
        <div class="empty">
          <h2 class="h5">Aucune action récente</h2>
          <p>Les actions importantes de votre direction apparaîtront ici.</p>
        </div>
      }
    </section>
  `,
})
export class NotificationsComponent {
  readonly comms = inject(CommunicationService);
}
