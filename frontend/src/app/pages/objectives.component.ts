import { Component, inject, signal } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { finalize } from "rxjs";
import { SalesService, Monthly, localDay } from "../core/sales.service";
import { apiError } from "../core/report.service";
@Component({
  selector: "app-objectives",
  standalone: true,
  imports: [FormsModule],
  template: ` <section class="mb-4">
    <div class="d-flex justify-content-between flex-wrap gap-3 mb-3">
      <div>
        <h2 class="h4">Objectifs mensuels</h2>
        <p class="small text-secondary mb-0">
          Réalisations des rapports validés uniquement. Le reste à atteindre se
          recalcule automatiquement.
        </p>
      </div>
      <div>
        <label for="goalsMonth" class="form-label">Mois</label
        ><input
          id="goalsMonth"
          type="month"
          class="form-control"
          [(ngModel)]="month"
          (change)="load()"
        />
      </div>
    </div>
    @if (error()) {
      <div class="alert alert-danger" role="alert">
        {{ error() }}
        <button class="btn btn-outline-danger btn-sm" (click)="load()">
          Réessayer
        </button>
      </div>
    }
    @if (loading()) {
      <p role="status">Chargement des objectifs…</p>
    } @else {
      <div class="report-grid">
        @for (row of rows(); track row.commercialId) {
          <article class="panel">
            <h3 class="h5">{{ row.name }}</h3>
            <p class="small text-secondary">
              {{ row.team }} · {{ row.zone || "Zone à affecter" }}
            </p>
            @for (g of row.goals; track g.code) {
              <div class="mb-3">
                <div class="d-flex justify-content-between flex-wrap gap-1">
                  <strong>{{ g.label }}</strong
                  ><span>{{ g.achieved }} / {{ g.target }}</span>
                </div>
                <div
                  class="progress my-2"
                  role="progressbar"
                  [attr.aria-label]="g.label"
                  [attr.aria-valuenow]="g.percent"
                  aria-valuemin="0"
                  aria-valuemax="100"
                >
                  <div class="progress-bar" [style.width.%]="g.percent"></div>
                </div>
                <span class="small"
                  >{{
                    g.remaining === 0
                      ? "Objectif atteint !"
                      : g.remaining + " à atteindre"
                  }}
                  · {{ g.percent }} %</span
                >
              </div>
            }
            @if (row.pendingReports) {
              <p class="small text-secondary">
                {{ row.pendingReports }} rapport(s) en attente de validation /
                correction.
              </p>
            }
            @if (row.unquantifiedReports) {
              <p class="small text-warning-emphasis">
                {{ row.unquantifiedReports }} rapport(s) validé(s) sans
                répartition des objectifs : bilan incomplet.
              </p>
            }
          </article>
        } @empty {
          <div class="panel empty">
            Aucun commercial dans votre périmètre. Les affectations se font dans
            Organisation et zones.
          </div>
        }
      </div>
    }
  </section>`,
})
export class ObjectivesComponent {
  private api = inject(SalesService);
  month = localDay().slice(0, 7);
  readonly rows = signal<Monthly[]>([]);
  readonly error = signal("");
  readonly loading = signal(false);
  constructor() {
    this.load();
  }
  load() {
    if (!this.month) return;
    this.loading.set(true);
    this.error.set("");
    this.api
      .objectives(this.month)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (r) => this.rows.set(r),
        error: (e) => this.error.set(apiError(e)),
      });
  }
}
