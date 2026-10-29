import { Component, computed, inject, signal } from "@angular/core";
import { DatePipe, KeyValuePipe } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { RouterLink } from "@angular/router";
import { finalize, forkJoin, switchMap } from "rxjs";
import { AuthService } from "../core/auth.service";
import {
  SalesService,
  Summary,
  Member,
  localDay,
  goalLabels,
} from "../core/sales.service";
import {
  ReportService,
  Report,
  apiError,
  statusLabel,
} from "../core/report.service";
@Component({
  standalone: true,
  imports: [FormsModule, DatePipe, KeyValuePipe, RouterLink],
  template: `
    <div class="page-heading">
      <div>
        <p class="eyebrow">Commercial → superviseur → direction</p>
        <h1>
          {{ auth.isDirector() ? "Rapport général" : "Synthèse de ma zone" }}
        </h1>
        <p class="text-secondary">
          Les synthèses transmises regroupent les rapports validés. Les chiffres
          journaliers restent consultables dans les rapports source.
        </p>
      </div>
      <div>
        <label for="summaryDate" class="form-label">Journée</label
        ><input
          id="summaryDate"
          type="date"
          class="form-control"
          [(ngModel)]="date"
          [max]="today"
          (change)="load()"
        />
      </div>
    </div>
    @if (error()) {
      <div class="alert alert-danger" role="alert">{{ error() }}</div>
    }
    @if (success()) {
      <div class="alert alert-success" role="status">{{ success() }}</div>
    }
    @if (loading()) {
      <p role="status">Chargement…</p>
    } @else {
      @if (auth.isDirector()) {
        <section class="panel mb-4">
          <h2 class="h4">Bilan consolidé des synthèses reçues</h2>
          <p>
            {{ summaries().length }} synthèse(s) reçue(s) /
            {{ supervisorCount() }} superviseur(s) actif(s).
            {{ includedCount() }} rapport(s) inclus.
          </p>
          <div class="row g-3">
            @for (t of ["AZO", "ZEHAMI"]; track t) {
              <div class="col-md-6">
                <h3 class="h5">{{ t }}</h3>
                @for (g of total(t) | keyvalue; track g.key) {
                  <p>
                    {{ goalLabel(g.key) }} :
                    <strong>{{ g.value }}</strong>
                  </p>
                } @empty {
                  <p class="text-secondary">Aucun compteur transmis.</p>
                }
              </div>
            }
          </div>
          @if (missingSupervisors().length) {
            <p class="small text-warning-emphasis">
              Synthèse non reçue : {{ missingSupervisors().join(", ") }}
            </p>
          }
          <p class="small text-secondary">
            Les objectifs AZO et Zehami sont présentés séparément. Les rapports
            qui ne figurent pas dans une synthèse ne sont pas inclus dans ce
            total.
          </p>
        </section>
      }
      <section class="panel mb-4">
        <h2 class="h4">Statistiques journalières par commercial</h2>
        @for (r of daily(); track r.id) {
          <div class="border-bottom py-3">
            <div class="d-flex flex-wrap gap-2 justify-content-between">
              <strong
                >{{ r.authorName }} · {{ r.team }} ·
                {{ r.zone || "Zone non renseignée" }}</strong
              ><a routerLink="/rapports" [queryParams]="{ id: r.id }"
                >Lire le rapport #{{ r.id }}</a
              >
            </div>
            <p class="small">{{ label(r.status) }} · {{ r.title }}</p>
            @if (r.mode === "STATS") {
              <p class="small">
                {{ r.prospectsContacted }} prospects contactés ·
                {{ r.registrations }} comptes uniques inscrits.
              </p>
            }
            @for (g of r.goalCounts | keyvalue; track g.key) {
              <span class="team-pill me-2 mb-2 d-inline-block"
                >{{ goalLabel(g.key) }} : {{ g.value }}</span
              >
            } @empty {
              <p class="small text-secondary">
                Répartition des objectifs non fournie.
              </p>
            }
          </div>
        } @empty {
          <p>Aucun rapport envoyé pour cette journée.</p>
        }
        @if (missingCommercials().length) {
          <p class="small text-warning-emphasis mt-3">
            Sans rapport envoyé : {{ missingCommercials().join(", ") }}
          </p>
        }
      </section>
      @if (auth.isSupervisor() && !alreadySent()) {
        <section class="panel mb-4">
          <h2 class="h4">Rédiger la synthèse pour le directeur</h2>
          <p class="small">
            Validez les rapports de la journée avant l'envoi. Les rapports
            renvoyés doivent être corrigés et soumis de nouveau. Une synthèse
            transmise est définitive ; elle clôture les envois de cette journée
            pour votre équipe.
          </p>
          <form (ngSubmit)="send()" #f="ngForm">
            <label for="summaryContent" class="form-label"
              >Résumé de la zone et points à signaler</label
            ><textarea
              id="summaryContent"
              name="content"
              [(ngModel)]="content"
              class="form-control mb-3"
              rows="7"
              required
              maxlength="30000"
              placeholder="Résultats, difficultés, corrections obtenues, actions à prévoir…"
            ></textarea>
            <button
              class="btn btn-primary"
              [disabled]="f.invalid || busy() || !ready()"
            >
              Envoyer la synthèse au directeur
            </button>
            @if (!ready()) {
              <p class="small text-warning-emphasis mt-2">
                Au moins un rapport validé est nécessaire, et aucun rapport ne
                doit rester soumis ou en correction.
              </p>
            }
          </form>
        </section>
      }
      <h2 class="h4">Synthèses transmises</h2>
      <div class="report-grid">
        @for (s of summaries(); track s.id) {
          <article class="panel">
            <span class="team-pill">{{ s.team }} · {{ s.zone }}</span>
            <h3 class="h5 mt-3">{{ s.supervisorName }}</h3>
            <p class="small text-secondary">
              {{ s.createdAt | date: "dd/MM/yyyy HH:mm" }} ·
              {{ s.commercialCount }} commercial(aux)
            </p>
            <p class="report-body">{{ s.content }}</p>
            @for (g of s.totals | keyvalue; track g.key) {
              <p>
                {{ goalLabel(g.key) }} : <strong>{{ g.value }}</strong>
              </p>
            }
            <p class="small">Rapports sources :</p>
            <div class="d-flex flex-wrap gap-2">
              @for (id of s.reportIds; track id) {
                <a routerLink="/rapports" [queryParams]="{ id }"
                  >Rapport #{{ id }}</a
                >
              }
            </div>
          </article>
        } @empty {
          <div class="panel empty">
            Aucune synthèse transmise pour cette journée.
          </div>
        }
      </div>
    }
  `,
})
export class SummariesComponent {
  readonly auth = inject(AuthService);
  private api = inject(SalesService);
  private reportsApi = inject(ReportService);
  date = localDay();
  readonly today = localDay();
  content = "";
  readonly summaries = signal<Summary[]>([]);
  readonly reports = signal<Report[]>([]);
  readonly members = signal<Member[]>([]);
  readonly error = signal("");
  readonly success = signal("");
  readonly loading = signal(false);
  readonly busy = signal(false);
  readonly labels = goalLabels;
  goalLabel(code: string) {
    return (
      this.summaries().find((s) => s.goalLabels?.[code])?.goalLabels[code] ||
      this.labels[code] ||
      code
    );
  }
  readonly label = statusLabel;
  daily() {
    return this.reports().filter(
      (r) => r.reportDate === this.date && r.status !== "DRAFT",
    );
  }
  supervisorCount() {
    return this.members().filter((m) => m.role === "SUPERVISEUR" && m.enabled)
      .length;
  }
  missingSupervisors() {
    return this.members()
      .filter(
        (m) =>
          m.role === "SUPERVISEUR" &&
          m.enabled &&
          !this.summaries().some((s) => s.supervisorId === m.id),
      )
      .map((m) => m.name);
  }
  missingCommercials() {
    return this.members()
      .filter(
        (m) =>
          m.role === "COMMERCIAL" &&
          m.enabled &&
          !this.daily().some((r) => r.authorId === m.id),
      )
      .map((m) => m.name);
  }
  includedCount() {
    return this.summaries().reduce((n, s) => n + s.reportIds.length, 0);
  }
  total(team: string) {
    const totals: Record<string, number> = {};
    for (const s of this.summaries().filter((s) => s.team === team))
      for (const [k, v] of Object.entries(s.totals))
        totals[k] = (totals[k] || 0) + v;
    return totals;
  }
  alreadySent() {
    return this.summaries().some(
      (s) =>
        s.supervisorId === this.auth.user()?.id &&
        s.team === this.auth.user()?.team,
    );
  }
  ready() {
    const own = this.daily().filter(
      (r) =>
        r.supervisorId === this.auth.user()?.id &&
        r.team === this.auth.user()?.team,
    );
    return own.length > 0 && own.every((r) => r.status === "CONFIRMED");
  }
  constructor() {
    this.load();
  }
  load() {
    if (!this.date) return;
    this.loading.set(true);
    this.error.set("");
    forkJoin({
      summaries: this.api.summaries(this.date),
      reports: this.reportsApi.list(),
      members: this.api.members(),
    })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (r) => {
          this.summaries.set(r.summaries);
          this.reports.set(r.reports);
          this.members.set(r.members);
        },
        error: (e) => this.error.set(apiError(e)),
      });
  }
  send() {
    if (this.busy() || !this.content.trim() || !this.ready()) return;
    this.busy.set(true);
    this.error.set("");
    this.success.set("");
    this.auth
      .csrf()
      .pipe(
        switchMap(() => this.api.send(this.date, this.content)),
        finalize(() => this.busy.set(false)),
      )
      .subscribe({
        next: () => {
          this.content = "";
          this.success.set("Synthèse transmise au directeur commercial.");
          this.load();
        },
        error: (e) => this.error.set(apiError(e)),
      });
  }
}
