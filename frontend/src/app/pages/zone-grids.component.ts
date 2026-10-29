import { Component, effect, inject, signal, untracked } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { finalize } from "rxjs";
import { AuthService } from "../core/auth.service";
import { CommunicationService } from "../core/communication.service";
import { SalesService, ZoneGrid, Goal } from "../core/sales.service";
import { apiError } from "../core/report.service";
@Component({
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="page-heading">
      <div>
        <p class="eyebrow">Des objectifs adaptés au terrain</p>
        <h1>Grilles d’objectifs</h1>
        <p class="text-secondary">
          Définissez vos cibles mensuelles par zone. Elles s’appliquent
          automatiquement aux commerciaux rattachés à cette zone.
        </p>
      </div>
      <button class="btn btn-primary" (click)="start()">
        <i class="bi bi-plus-lg" aria-hidden="true"></i>Nouvelle grille
      </button>
    </div>
    @if (error()) {
      <div role="alert" class="alert alert-danger">{{ error() }}</div>
    }
    @if (success()) {
      <div role="status" class="alert alert-success">{{ success() }}</div>
    }
    @if (open) {
      <section class="panel mb-4" id="zone-grid-form">
        <h2 class="h4">
          {{ editing ? "Modifier la grille" : "Créer une grille" }}
        </h2>
        <p class="form-text">
          Les objectifs sont mensuels et individuels. L’effectif prévu est
          indicatif ; le total de zone est calculé à partir de cet effectif. Un
          objectif à zéro signifie aucun minimum.
        </p>
        <form #f="ngForm" (ngSubmit)="save()">
          <div class="row g-3">
            <div class="col-sm-6">
              <label for="zoneCountry" class="form-label">Pays de la zone</label
              ><select
                id="zoneCountry"
                name="country"
                [(ngModel)]="country"
                [disabled]="!!editing"
                (ngModelChange)="parentId = null"
                required
                class="form-select"
              >
                @for (c of countries(); track c.code) {
                  @if (auth.isSuperAdmin() || c.code === auth.user()?.country) {
                    <option [value]="c.code">{{ c.name }}</option>
                  }
                }
              </select>
            </div>
            <div class="col-sm-6">
              <label for="zoneProject" class="form-label"
                >Projet de la zone</label
              ><select
                id="zoneProject"
                name="project"
                [(ngModel)]="project"
                [disabled]="!!editing"
                (ngModelChange)="resetMetrics(); parentId = null"
                required
                class="form-select"
              >
                @for (p of projects(); track p) {
                  <option [value]="p">{{ p }}</option>
                }
              </select>
            </div>
            <div class="col-sm-6">
              <label for="zoneLevel" class="form-label">Niveau de la zone</label
              ><select
                id="zoneLevel"
                name="level"
                [(ngModel)]="level"
                [disabled]="!!editing"
                (ngModelChange)="parentId = null; inheritGoals = false"
                required
                class="form-select"
              >
                <option value="DEPARTMENT">Département</option>
                <option value="COMMUNE">Commune</option>
              </select>
            </div>
            <div class="col-sm-6">
              <label for="zoneName" class="form-label">Nom de la zone</label
              ><input
                id="zoneName"
                name="name"
                [(ngModel)]="name"
                [readOnly]="!!editing"
                class="form-control"
                required
                pattern=".*\\S.*"
                maxlength="150"
                placeholder="Ex. Littoral"
              />
            </div>
            @if (level === "COMMUNE") {
              <div class="col-sm-6">
                <label for="zoneParent" class="form-label"
                  >Département de rattachement</label
                ><select
                  id="zoneParent"
                  name="parentId"
                  [(ngModel)]="parentId"
                  (ngModelChange)="inheritGoals = $event ? inheritGoals : false"
                  class="form-select"
                >
                  <option [ngValue]="null">Sans département</option>
                  @for (d of parents(); track d.id) {
                    <option [ngValue]="d.id">{{ d.name }}</option>
                  }
                </select>
              </div>
              <div class="col-sm-6 align-self-center">
                <label class="check-chip"
                  ><input
                    name="inherit"
                    type="checkbox"
                    [(ngModel)]="inheritGoals"
                    [disabled]="!parentId"
                  />Utiliser la grille du département</label
                >
              </div>
            }
            <div class="col-sm-6">
              <label for="zoneStaff" class="form-label"
                >Effectif commercial prévu</label
              ><input
                id="zoneStaff"
                name="staff"
                [(ngModel)]="plannedStaff"
                type="number"
                min="0"
                max="10000"
                step="1"
                pattern="[0-9]+"
                required
                class="form-control"
              />
            </div>
          </div>
          @if (inheritGoals && parentId) {
            <div class="stats-box mt-4">
              <h3 class="h6">Objectifs hérités</h3>
              @for (g of parentGoals(); track g.code) {
                <p class="mb-1">
                  {{ g.label }} : {{ g.target }} par commercial / mois
                </p>
              }
              <p class="form-text mb-0">
                Toute modification de la grille du département sera répercutée
                ici.
              </p>
            </div>
          } @else {
            <div class="mt-4">
              <h3 class="h5">Critères et objectifs mensuels</h3>
              @for (g of metrics; track $index; let i = $index) {
                <div class="zone-metric">
                  <div>
                    <label [for]="'metricLabel' + i" class="form-label"
                      >Libellé du critère {{ i + 1 }}</label
                    ><input
                      [id]="'metricLabel' + i"
                      [name]="'metricLabel' + i"
                      [(ngModel)]="g.label"
                      (ngModelChange)="updateCode(i)"
                      required
                      maxlength="120"
                      class="form-control"
                    />
                  </div>
                  <div>
                    <label [for]="'metricTarget' + i" class="form-label"
                      >Objectif mensuel {{ i + 1 }}</label
                    ><input
                      [id]="'metricTarget' + i"
                      [name]="'metricTarget' + i"
                      [(ngModel)]="g.target"
                      type="number"
                      min="0"
                      max="1000000"
                      step="1"
                      pattern="[0-9]+"
                      required
                      class="form-control"
                    />
                  </div>
                  <button
                    class="btn btn-outline-danger"
                    type="button"
                    [attr.aria-label]="'Retirer le critère ' + (i + 1)"
                    (click)="remove(i)"
                  >
                    <i class="bi bi-trash" aria-hidden="true"></i>
                  </button>
                </div>
              }
              <button
                type="button"
                class="btn btn-outline-primary mt-2"
                (click)="add()"
                [disabled]="metrics.length >= 30"
              >
                Ajouter un critère
              </button>
            </div>
          }
          <div class="d-flex gap-2 flex-wrap mt-4">
            <button
              class="btn btn-primary"
              [disabled]="
                f.invalid ||
                busy() ||
                (!inheritGoals && !metrics.length) ||
                (inheritGoals && !parentId)
              "
            >
              {{ busy() ? "Enregistrement…" : "Enregistrer la grille" }}</button
            ><button
              class="btn btn-outline-secondary"
              type="button"
              (click)="open = false"
            >
              Annuler
            </button>
          </div>
        </form>
      </section>
    }
    <div class="filter-bar">
      <div class="search-field">
        <i class="bi bi-search" aria-hidden="true"></i
        ><input
          aria-label="Rechercher une zone"
          [(ngModel)]="query"
          placeholder="Rechercher une zone ou un projet"
        />
      </div>
    </div>
    @if (loading()) {
      <p role="status">Chargement des grilles…</p>
    }
    <div class="report-grid">
      @for (z of filtered(); track z.id) {
        <article class="panel">
          <div class="d-flex gap-2 flex-wrap mb-3">
            <span class="team-pill">{{ z.project }}</span
            ><span class="small text-secondary"
              >{{ countryName(z.country) }} ·
              {{ z.level === "DEPARTMENT" ? "Département" : "Commune" }}</span
            >
          </div>
          <h2 class="h4">{{ z.name }}</h2>
          <p class="small text-secondary">
            {{ z.assignedStaff }} commercial(aux) actif(s) rattaché(s) ·
            {{ z.plannedStaff }} prévu(s)
          </p>
          @if (z.inheritGoals) {
            <p class="small">Grille héritée de {{ parentName(z.parentId) }}</p>
          }
          <div class="goal-grid-table">
            <div class="goal-grid-head">
              <span>Critère</span><span>Par commercial</span
              ><span>Total prévu</span>
            </div>
            @for (g of z.effectiveGoals; track g.code) {
              <div>
                <strong>{{ g.label }}</strong
                ><span>{{ g.target }}</span
                ><span>{{ g.target * z.plannedStaff }}</span>
              </div>
            }
          </div>
          <button class="btn btn-outline-primary mt-3" (click)="edit(z)">
            Modifier la grille
          </button>
        </article>
      } @empty {
        <div class="panel empty">
          <h2 class="h5">Vos grilles se configurent ici</h2>
          <p>
            Créez un département ou une commune, puis affectez cette zone à un
            superviseur dans Organisation et zones.
          </p>
        </div>
      }
    </div>
  `,
})
export class ZoneGridsComponent {
  readonly auth = inject(AuthService);
  private api = inject(SalesService);
  private comms = inject(CommunicationService);
  readonly zones = signal<ZoneGrid[]>([]);
  readonly countries = signal<{ code: string; name: string }[]>([]);
  readonly error = signal("");
  readonly success = signal("");
  readonly busy = signal(false);
  readonly loading = signal(false);
  open = false;
  editing: ZoneGrid | null = null;
  country = "BJ";
  project = "AZO";
  level = "DEPARTMENT";
  name = "";
  parentId: number | null = null;
  plannedStaff = 0;
  inheritGoals = false;
  metrics: (Goal & { fixed?: boolean })[] = [];
  query = "";
  private lastEvent = 0;
  constructor() {
    this.country = this.auth.user()?.country || "BJ";
    this.project = this.projects()[0] || "AZO";
    this.resetMetrics();
    this.load();
    this.api.countries().subscribe({
      next: (c) => this.countries.set(c),
      error: (e) => this.error.set(apiError(e)),
    });
    effect(() => {
      const id = this.comms.events()[0]?.id || 0;
      if (id && id !== this.lastEvent) {
        this.lastEvent = id;
        untracked(() => this.load());
      }
    });
  }
  projects() {
    return this.auth.isSuperAdmin()
      ? ["AZO", "ZEHAMI"]
      : this.auth.user()?.projects || [];
  }
  countryName(code: string) {
    return this.countries().find((c) => c.code === code)?.name || code;
  }
  parentName(id: number | null) {
    return this.zones().find((z) => z.id === id)?.name || "son département";
  }
  parents() {
    return this.zones().filter(
      (z) =>
        z.level === "DEPARTMENT" &&
        z.country === this.country &&
        z.project === this.project,
    );
  }
  parentGoals() {
    return (
      this.zones().find((z) => z.id === this.parentId)?.effectiveGoals || []
    );
  }
  resetMetrics() {
    this.metrics = (
      this.project === "AZO"
        ? [
            ["AGENCES_FLOTTE", "Agences de flotte"],
            ["SPEEDERS_INDEPENDANTS", "Speeders indépendants"],
            ["CLIENTS", "Clients"],
          ]
        : [
            ["PROFESSIONNELS", "Professionnels"],
            ["UTILISATEURS", "Utilisateurs"],
          ]
    ).map(([code, label]) => ({ code, label, target: 0, fixed: true }));
  }
  start() {
    this.editing = null;
    this.open = true;
    this.name = "";
    this.parentId = null;
    this.inheritGoals = false;
    this.plannedStaff = 0;
    this.level = "DEPARTMENT";
    this.country = this.auth.user()?.country || "BJ";
    this.project = this.projects()[0] || "AZO";
    this.resetMetrics();
    this.error.set("");
    this.success.set("");
    this.scroll();
  }
  edit(z: ZoneGrid) {
    this.editing = z;
    this.open = true;
    this.country = z.country;
    this.project = z.project;
    this.level = z.level;
    this.name = z.name;
    this.parentId = z.parentId;
    this.plannedStaff = z.plannedStaff;
    this.inheritGoals = z.inheritGoals;
    this.metrics = z.goals.map((g) => ({ ...g, fixed: true }));
    this.error.set("");
    this.scroll();
  }
  scroll() {
    setTimeout(() =>
      document
        .getElementById("zone-grid-form")
        ?.scrollIntoView({ behavior: "smooth" }),
    );
  }
  add() {
    this.metrics.push({ code: "", label: "", target: 0 });
  }
  remove(i: number) {
    this.metrics.splice(i, 1);
  }
  updateCode(i: number) {
    const g = this.metrics[i];
    if (g.fixed) return;
    let code = g.label
      .normalize("NFD")
      .replace(/[\u0300-\u036f]/g, "")
      .toUpperCase()
      .replace(/[^A-Z0-9]+/g, "_")
      .replace(/^_+|_+$/g, "");
    if (!/^[A-Z]/.test(code)) code = "CRITERE_" + code;
    g.code = code.slice(0, 50);
  }
  filtered() {
    const q = this.query.trim().toLowerCase();
    return this.zones().filter((z) =>
      (z.name + " " + z.project + " " + this.countryName(z.country))
        .toLowerCase()
        .includes(q),
    );
  }
  load() {
    this.loading.set(true);
    this.api
      .zones()
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (z) => this.zones.set(z),
        error: (e) => this.error.set(apiError(e)),
      });
  }
  save() {
    if (this.busy()) return;
    this.busy.set(true);
    this.error.set("");
    this.success.set("");
    this.api
      .saveZone(this.editing?.id ?? null, {
        country: this.country,
        project: this.project,
        level: this.level,
        name: this.name,
        parentId: this.level === "COMMUNE" ? this.parentId : null,
        plannedStaff: this.plannedStaff,
        inheritGoals: this.inheritGoals,
        goals: this.inheritGoals
          ? []
          : this.metrics.map(({ code, label, target }) => ({
              code,
              label,
              target,
            })),
        version: this.editing?.version ?? null,
      })
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: () => {
          this.open = false;
          this.success.set(
            "Grille enregistrée. Les objectifs des commerciaux sont actualisés.",
          );
          this.load();
        },
        error: (e) => this.error.set(apiError(e)),
      });
  }
}
