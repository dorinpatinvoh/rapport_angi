import { Component, inject, signal, computed } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { RouterLink } from "@angular/router";
import { finalize, switchMap } from "rxjs";
import { AuthService } from "../core/auth.service";
import { SalesService, Member, ZoneGrid } from "../core/sales.service";
import { apiError } from "../core/report.service";
@Component({
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <div class="page-heading">
      <div>
        <p class="eyebrow">Les bonnes personnes, au bon endroit</p>
        <h1>Organisation et zones</h1>
        <p class="text-secondary">
          Un directeur par pays. Des projets et des équipes clairement
          rattachés.
        </p>
      </div>
      @if (auth.isDirector()) {
        <a routerLink="/commerciaux" class="btn btn-primary"
          ><i class="bi bi-person-plus" aria-hidden="true"></i>Créer / activer
          un compte</a
        >
      }
    </div>
    @if (error()) {
      <div role="alert" class="alert alert-danger">{{ error() }}</div>
    }
    @if (success()) {
      <div role="status" class="alert alert-success">{{ success() }}</div>
    }
    @if (members().some(unconfigured)) {
      <p class="alert alert-warning">
        Un directeur doit encore recevoir son pays et ses projets. Le super
        administrateur peut le configurer avec Affecter / nommer.
      </p>
    }
    <div class="overview-strip">
      <div>
        <strong>{{ members().length }}</strong
        ><span>Comptes accessibles</span>
      </div>
      <div>
        <strong>{{ count("DIRECTEUR_COMMERCIAL") }}</strong
        ><span>Direction</span>
      </div>
      <div>
        <strong>{{ count("SUPERVISEUR") }}</strong
        ><span>Supervision</span>
      </div>
      <div>
        <strong>{{ count("COMMERCIAL") }}</strong
        ><span>Commerciaux</span>
      </div>
    </div>
    @if (auth.isDirector()) {
      <details class="panel mb-4 manager-create">
        <summary class="fw-semibold">
          <i class="bi bi-person-badge" aria-hidden="true"></i>Créer un
          responsable
        </summary>
        <p class="text-secondary mt-3">
          Le compte sera actif immédiatement. Un directeur peut suivre plusieurs
          projets de son pays.
        </p>
        <form (ngSubmit)="createManager()" #createForm="ngForm">
          <div class="row g-3">
            <div class="col-sm-6">
              <label for="managerFirst" class="form-label"
                >Prénom du responsable</label
              ><input
                id="managerFirst"
                name="managerFirst"
                [(ngModel)]="newAccount.firstName"
                class="form-control"
                required
                pattern=".*\\S.*"
                maxlength="100"
              />
            </div>
            <div class="col-sm-6">
              <label for="managerLast" class="form-label"
                >Nom du responsable</label
              ><input
                id="managerLast"
                name="managerLast"
                [(ngModel)]="newAccount.lastName"
                class="form-control"
                required
                pattern=".*\\S.*"
                maxlength="100"
              />
            </div>
            <div class="col-sm-6">
              <label for="managerEmail" class="form-label"
                >Email du responsable</label
              ><input
                id="managerEmail"
                name="managerEmail"
                [(ngModel)]="newAccount.email"
                class="form-control"
                type="email"
                email
                required
                maxlength="150"
                autocomplete="off"
              />
            </div>
            <div class="col-sm-6">
              <label for="managerPassword" class="form-label"
                >Mot de passe du responsable</label
              ><input
                id="managerPassword"
                name="managerPassword"
                [(ngModel)]="newAccount.password"
                class="form-control"
                type="password"
                required
                minlength="12"
                maxlength="72"
                autocomplete="new-password"
              /><span class="form-text"
                >Au moins 12 caractères, limité à 72 octets UTF-8.</span
              >
            </div>
            <div class="col-sm-6">
              <label for="managerRole" class="form-label"
                >Fonction du responsable</label
              ><select
                id="managerRole"
                name="managerRole"
                [(ngModel)]="newRole"
                class="form-select"
                required
              >
                <option value="SUPERVISEUR">Superviseur</option>
                @if (auth.isSuperAdmin()) {
                  <option value="DIRECTEUR_COMMERCIAL">
                    Directeur commercial
                  </option>
                }
              </select>
            </div>
            <div class="col-sm-6">
              <label for="managerCountry" class="form-label"
                >Pays du responsable</label
              ><select
                id="managerCountry"
                name="managerCountry"
                [(ngModel)]="newAccount.country"
                (ngModelChange)="newZoneConfigId = null"
                class="form-select"
                required
              >
                @for (c of availableCountries(); track c.code) {
                  <option [value]="c.code">{{ c.name }}</option>
                }
              </select>
            </div>
            @if (newRole === "DIRECTEUR_COMMERCIAL") {
              <fieldset class="col-12">
                <legend class="form-label">Projets du directeur</legend>
                @for (p of allProjects; track p) {
                  <label class="check-chip"
                    ><input
                      type="checkbox"
                      [checked]="newProjects.includes(p)"
                      (change)="toggleNew(p)"
                    />{{ p }}</label
                  >
                }
                <p class="form-text">
                  Un seul directeur par pays, même s’il gère plusieurs projets.
                </p>
              </fieldset>
            } @else {
              <div class="col-sm-6">
                <label for="managerTeam" class="form-label"
                  >Équipe du responsable</label
                ><select
                  id="managerTeam"
                  name="managerTeam"
                  [(ngModel)]="newAccount.team"
                  (ngModelChange)="newZoneConfigId = null"
                  class="form-select"
                  required
                >
                  @for (p of allowedProjects(); track p) {
                    <option [value]="p">{{ p }}</option>
                  }
                </select>
              </div>
              <div class="col-sm-6">
                <label for="managerLevel" class="form-label"
                  >Niveau de la nouvelle zone</label
                ><select
                  id="managerLevel"
                  name="managerLevel"
                  [(ngModel)]="newLevel"
                  [disabled]="newZoneConfigId !== null"
                  class="form-select"
                  required
                >
                  <option value="DEPARTMENT">Département</option>
                  <option value="COMMUNE">Commune</option>
                </select>
              </div>
              <div class="col-12">
                <label for="managerGrid" class="form-label"
                  >Zone et grille d’objectifs du superviseur</label
                ><select
                  id="managerGrid"
                  name="managerGrid"
                  [(ngModel)]="newZoneConfigId"
                  (ngModelChange)="selectNewZone($event)"
                  class="form-select"
                >
                  <option [ngValue]="null">Zone libre (grille standard)</option>
                  @for (
                    z of zoneOptions(newAccount.country, newAccount.team);
                    track z.id
                  ) {
                    <option [ngValue]="z.id">
                      {{ z.name }} ·
                      {{ z.level === "DEPARTMENT" ? "Département" : "Commune" }}
                    </option>
                  }</select
                ><a
                  routerLink="/grilles-objectifs"
                  class="form-text d-inline-block"
                  >Configurer les grilles par zone</a
                >
              </div>
              <div class="col-12">
                <label for="managerZone" class="form-label"
                  >Zone du nouveau superviseur</label
                ><input
                  id="managerZone"
                  name="managerZone"
                  [(ngModel)]="newZone"
                  [readOnly]="newZoneConfigId !== null"
                  class="form-control"
                  required
                  pattern=".*\\S.*"
                  maxlength="150"
                  placeholder="Nom du département ou de la commune"
                />
              </div>
            }
          </div>
          <button
            class="btn btn-primary mt-3"
            [disabled]="
              createForm.invalid ||
              busy() ||
              (newRole === 'DIRECTEUR_COMMERCIAL' && !newProjects.length)
            "
          >
            {{ busy() ? "Enregistrement…" : "Créer le responsable" }}
          </button>
        </form>
      </details>
      @if (auth.isSuperAdmin()) {
        <details class="panel mb-4">
          <summary class="fw-semibold">Ajouter un pays</summary>
          <form
            class="row g-3 mt-1"
            #countryForm="ngForm"
            (ngSubmit)="addCountry()"
          >
            <div class="col-sm-3">
              <label for="countryCode" class="form-label">Code pays ISO</label
              ><input
                id="countryCode"
                name="countryCode"
                [(ngModel)]="countryCode"
                (ngModelChange)="countryCode = $event.toUpperCase()"
                required
                pattern="[A-Z]{2}"
                maxlength="2"
                class="form-control"
                placeholder="Ex. NE"
              />
            </div>
            <div class="col-sm-6">
              <label for="countryName" class="form-label">Nom du pays</label
              ><input
                id="countryName"
                name="countryName"
                [(ngModel)]="countryName"
                required
                maxlength="100"
                class="form-control"
              />
            </div>
            <div class="col-sm-3 align-self-end">
              <button
                class="btn btn-outline-primary"
                [disabled]="countryForm.invalid || busy()"
              >
                Ajouter le pays
              </button>
            </div>
          </form>
        </details>
      }
    }
    <div class="filter-bar">
      <div class="search-field">
        <i class="bi bi-search" aria-hidden="true"></i
        ><input
          aria-label="Rechercher un membre"
          [(ngModel)]="query"
          placeholder="Rechercher un nom ou un email"
        />
      </div>
      <select
        aria-label="Filtrer par rôle"
        class="form-select"
        [(ngModel)]="roleFilter"
      >
        <option value="">Tous les rôles</option>
        <option value="COMMERCIAL">Commerciaux</option>
        <option value="SUPERVISEUR">Superviseurs</option>
        <option value="DIRECTEUR_COMMERCIAL">Directeurs</option>
      </select>
      @if (auth.isSuperAdmin()) {
        <select
          aria-label="Filtrer par pays"
          class="form-select"
          [(ngModel)]="countryFilter"
        >
          <option value="">Tous les pays</option>
          @for (c of countries(); track c.code) {
            <option [value]="c.code">{{ c.name }}</option>
          }
        </select>
      }
    </div>
    @if (loading()) {
      <p role="status">Chargement…</p>
    }
    <div class="report-grid">
      @for (m of filtered(); track m.id) {
        <article class="panel member-card">
          <div class="member-avatar">{{ m.name.slice(0, 1) }}</div>
          <h2 class="h5">{{ m.name }}</h2>
          <p class="text-break text-secondary small">{{ m.email }}</p>
          <p>
            <span class="team-pill">{{ roleLabel(m.role) }}</span>
            <span class="small" [class.text-success]="m.enabled">{{
              m.enabled ? "Actif" : "Inactif"
            }}</span>
          </p>
          <p class="small text-secondary mb-2">
            {{ countryLabel(m.country) }} ·
            {{
              m.role === "DIRECTEUR_COMMERCIAL"
                ? m.projects.join(" / ") || "Sans affectation"
                : m.team || "Projet à affecter"
            }}
          </p>
          @if (m.role === "SUPERVISEUR") {
            <p class="small">
              {{
                m.zoneLevel === "DEPARTMENT"
                  ? "Département"
                  : m.zoneLevel === "COMMUNE"
                    ? "Commune"
                    : "Zone à préciser"
              }}
              : {{ m.zone || "Non affectée" }}
            </p>
          }
          @if (m.role === "COMMERCIAL") {
            <p class="small">
              Superviseur : {{ supervisorName(m.supervisorId) }}
            </p>
          }
          @if (auth.isDirector() && editable(m)) {
            <button
              class="btn btn-outline-primary mt-auto"
              (click)="edit(m)"
              [disabled]="busy()"
            >
              Affecter / nommer
            </button>
          }
          @if (statusAllowed(m)) {
            <button
              class="btn mt-2"
              [class.btn-outline-danger]="m.enabled"
              [class.btn-outline-primary]="!m.enabled"
              (click)="statusTarget.set(m)"
              [disabled]="busy()"
            >
              {{ m.enabled ? "Désactiver le compte" : "Réactiver le compte" }}
            </button>
          }
          @if (
            auth.isSuperAdmin() &&
            m.role === "DIRECTEUR_COMMERCIAL" &&
            m.configured
          ) {
            <button
              class="btn btn-outline-primary mt-2"
              (click)="replace(m)"
              [disabled]="busy()"
            >
              Remplacer le directeur
            </button>
          }
        </article>
      } @empty {
        <div class="panel empty">
          Aucun membre ne correspond à votre recherche.
        </div>
      }
    </div>
    @if (statusTarget(); as m) {
      <section class="panel mt-4" aria-label="Confirmation du statut">
        <h2 class="h4">
          {{ m.enabled ? "Désactiver" : "Réactiver" }} {{ m.name }}
        </h2>
        <p>
          {{
            m.enabled
              ? "Le compte ne pourra plus se connecter. Ses rapports et son historique sont conservés. Un superviseur doit avoir réaffecté ses commerciaux et terminé ses synthèses."
              : "Le compte pourra de nouveau se connecter avec son mot de passe actuel."
          }}
        </p>
        <div class="d-flex gap-2 flex-wrap">
          <button
            class="btn"
            [class.btn-danger]="m.enabled"
            [class.btn-primary]="!m.enabled"
            (click)="confirmStatus()"
            [disabled]="busy()"
          >
            {{
              m.enabled
                ? "Confirmer la désactivation"
                : "Confirmer la réactivation"
            }}</button
          ><button
            class="btn btn-outline-secondary"
            (click)="statusTarget.set(null)"
          >
            Annuler
          </button>
        </div>
      </section>
    }
    @if (replacement(); as m) {
      <section class="panel mt-4" id="director-replacement">
        <p class="eyebrow">Administration globale</p>
        <h2 class="h4">
          Remplacer le directeur de {{ countryLabel(m.country) }}
        </h2>
        <p>
          Le nouveau responsable reprend les projets
          {{ m.projects.join(" / ") }}. Le directeur actuel sera désactivé, avec
          conservation de son compte et de son historique. En cas de refus, le
          directeur actuel reste en place.
        </p>
        <label for="directorCandidate" class="form-label"
          >Nouveau directeur</label
        ><select
          id="directorCandidate"
          [(ngModel)]="candidateId"
          class="form-select"
        >
          <option [ngValue]="null">Choisir un compte du même pays</option>
          @for (c of candidates(); track c.id) {
            <option [ngValue]="c.id">{{ c.name }} · {{ c.email }}</option>
          }
        </select>
        <p class="form-text">
          Créez d’abord son compte dans Commerciaux si nécessaire. Un
          superviseur doit transmettre ses synthèses et réaffecter ses
          commerciaux avant sa nomination.
        </p>
        <div class="d-flex gap-2 flex-wrap">
          <button
            class="btn btn-primary"
            (click)="confirmReplacement()"
            [disabled]="!candidateId || busy()"
          >
            Nommer le remplaçant</button
          ><button
            class="btn btn-outline-secondary"
            (click)="replacement.set(null)"
          >
            Annuler
          </button>
        </div>
      </section>
    }
    @if (selected(); as m) {
      <section class="panel mt-4 assignment-panel" id="assignment-form">
        <p class="eyebrow">Organisation de l’équipe</p>
        <h2 class="h4">Affectation de {{ m.name }}</h2>
        <form (ngSubmit)="save()" #f="ngForm">
          <div class="row g-3">
            <div class="col-sm-6">
              <label for="assignmentRole" class="form-label">Fonction</label
              ><select
                id="assignmentRole"
                name="role"
                [(ngModel)]="role"
                class="form-select"
                required
              >
                <option value="COMMERCIAL">Commercial</option>
                <option value="SUPERVISEUR">Superviseur</option>
                @if (auth.isSuperAdmin()) {
                  <option value="DIRECTEUR_COMMERCIAL">
                    Directeur commercial
                  </option>
                }
              </select>
            </div>
            <div class="col-sm-6">
              <label for="assignmentCountry" class="form-label"
                >Pays d’affectation</label
              ><select
                id="assignmentCountry"
                name="country"
                [(ngModel)]="country"
                (ngModelChange)="supervisorId = null; zoneConfigId = null"
                class="form-select"
                required
              >
                @for (c of availableCountries(); track c.code) {
                  <option [value]="c.code">{{ c.name }}</option>
                }
              </select>
            </div>
            @if (role === "DIRECTEUR_COMMERCIAL") {
              <fieldset class="col-12">
                <legend class="form-label">Projets affectés</legend>
                @for (p of allProjects; track p) {
                  <label class="check-chip"
                    ><input
                      type="checkbox"
                      [checked]="projects.includes(p)"
                      (change)="toggleProject(p)"
                    />{{ p }}</label
                  >
                }
              </fieldset>
            } @else {
              <div class="col-sm-6">
                <label for="assignmentTeam" class="form-label">Équipe</label
                ><select
                  id="assignmentTeam"
                  name="team"
                  [(ngModel)]="team"
                  (ngModelChange)="supervisorId = null; zoneConfigId = null"
                  class="form-select"
                  required
                >
                  @for (p of allowedProjects(); track p) {
                    <option [value]="p">{{ p }}</option>
                  }
                </select>
              </div>
            }
            @if (role === "SUPERVISEUR") {
              <div class="col-sm-6">
                <label for="assignmentLevel" class="form-label"
                  >Niveau géographique</label
                ><select
                  id="assignmentLevel"
                  name="zoneLevel"
                  [(ngModel)]="zoneLevel"
                  [disabled]="zoneConfigId !== null"
                  class="form-select"
                  required
                >
                  <option value="DEPARTMENT">Département</option>
                  <option value="COMMUNE">Commune</option>
                </select>
              </div>
              <div class="col-12">
                <label for="assignmentGrid" class="form-label"
                  >Zone et grille d’objectifs</label
                ><select
                  id="assignmentGrid"
                  name="assignmentGrid"
                  [(ngModel)]="zoneConfigId"
                  (ngModelChange)="selectZone($event)"
                  class="form-select"
                >
                  <option [ngValue]="null">Zone libre (grille standard)</option>
                  @for (z of zoneOptions(country, team); track z.id) {
                    <option [ngValue]="z.id">
                      {{ z.name }} ·
                      {{ z.level === "DEPARTMENT" ? "Département" : "Commune" }}
                    </option>
                  }
                </select>
              </div>
              <div class="col-12">
                <label for="assignmentZone" class="form-label"
                  >Zone du superviseur</label
                ><input
                  id="assignmentZone"
                  name="zone"
                  [(ngModel)]="zone"
                  [readOnly]="zoneConfigId !== null"
                  required
                  maxlength="150"
                  class="form-control"
                />
              </div>
            }
            @if (role === "COMMERCIAL") {
              <div class="col-12">
                <label for="assignmentSupervisor" class="form-label"
                  >Superviseur responsable</label
                ><select
                  id="assignmentSupervisor"
                  name="supervisor"
                  [(ngModel)]="supervisorId"
                  class="form-select"
                >
                  <option [ngValue]="null">
                    À affecter (envoi des rapports bloqué)
                  </option>
                  @for (s of supervisors(); track s.id) {
                    <option [ngValue]="s.id">
                      {{ s.name }} · {{ s.zone }}
                    </option>
                  }
                </select>
              </div>
            }
          </div>
          <p class="form-text mt-3">
            Les nouveaux droits s’appliquent à la prochaine connexion. Les
            rapports envoyés gardent leur périmètre historique.
          </p>
          <div class="d-flex gap-2 flex-wrap">
            <button
              class="btn btn-primary"
              [disabled]="
                f.invalid ||
                busy() ||
                (role === 'DIRECTEUR_COMMERCIAL' && !projects.length)
              "
            >
              Enregistrer l'affectation</button
            ><button
              class="btn btn-outline-secondary"
              type="button"
              (click)="selected.set(null)"
            >
              Annuler
            </button>
          </div>
        </form>
      </section>
    }
  `,
})
export class OrganizationComponent {
  readonly auth = inject(AuthService);
  private api = inject(SalesService);
  readonly members = signal<Member[]>([]);
  readonly countries = signal<{ code: string; name: string }[]>([]);
  readonly selected = signal<Member | null>(null);
  readonly error = signal("");
  readonly success = signal("");
  readonly busy = signal(false);
  readonly loading = signal(false);
  readonly allProjects = ["AZO", "ZEHAMI"];
  query = "";
  roleFilter = "";
  countryFilter = "";
  role = "COMMERCIAL";
  team = "AZO";
  zone = "";
  country = "BJ";
  zoneLevel = "COMMUNE";
  projects: string[] = [];
  supervisorId: number | null = null;
  newAccount = {
    firstName: "",
    lastName: "",
    email: "",
    password: "",
    team: "AZO",
    country: "BJ",
  };
  newRole = "SUPERVISEUR";
  newZone = "";
  newLevel = "COMMUNE";
  newProjects = ["AZO", "ZEHAMI"];
  countryCode = "";
  countryName = "";
  readonly unconfigured = (m: Member) => m.enabled && !m.configured;
  readonly zones = signal<ZoneGrid[]>([]);
  readonly statusTarget = signal<Member | null>(null);
  readonly replacement = signal<Member | null>(null);
  candidateId: number | null = null;
  zoneConfigId: number | null = null;
  newZoneConfigId: number | null = null;
  zoneOptions(country: string, project: string) {
    return this.zones().filter(
      (z) => z.country === country && z.project === project,
    );
  }
  selectNewZone(id: number | null) {
    const z = this.zones().find((z) => z.id === id);
    if (z) {
      this.newZone = z.name;
      this.newLevel = z.level;
    }
  }
  selectZone(id: number | null) {
    const z = this.zones().find((z) => z.id === id);
    if (z) {
      this.zone = z.name;
      this.zoneLevel = z.level;
    }
  }
  statusAllowed(m: Member) {
    return (
      this.auth.isDirector() &&
      m.id !== this.auth.user()?.id &&
      ["COMMERCIAL", "SUPERVISEUR"].includes(m.role)
    );
  }
  confirmStatus() {
    const m = this.statusTarget();
    if (!m || this.busy()) return;
    this.busy.set(true);
    this.error.set("");
    this.api
      .status(m, !m.enabled)
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: () => {
          this.statusTarget.set(null);
          this.success.set(
            m.enabled
              ? "Compte désactivé. Historique conservé."
              : "Compte réactivé.",
          );
          this.load();
        },
        error: (e) => this.error.set(apiError(e)),
      });
  }
  replace(m: Member) {
    this.replacement.set(m);
    this.candidateId = null;
    this.error.set("");
    setTimeout(() =>
      document
        .getElementById("director-replacement")
        ?.scrollIntoView({ behavior: "smooth" }),
    );
  }
  candidates() {
    const m = this.replacement();
    return this.members().filter(
      (c) =>
        m &&
        c.id !== m.id &&
        c.country === m.country &&
        !["SUPER_ADMIN", "ADMIN"].includes(c.role) &&
        (c.role !== "DIRECTEUR_COMMERCIAL" || !c.configured) &&
        (c.enabled || (c.role === "DIRECTEUR_COMMERCIAL" && !c.configured)),
    );
  }
  confirmReplacement() {
    const m = this.replacement();
    if (!m || !this.candidateId || this.busy()) return;
    this.busy.set(true);
    this.error.set("");
    this.api
      .replaceDirector(m, this.candidateId)
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: (r) => {
          this.replacement.set(null);
          this.selected.set(null);
          this.success.set(
            r.name +
              " est le nouveau directeur. Le compte précédent est désactivé et ses données sont conservées.",
          );
          this.load();
        },
        error: (e) => this.error.set(apiError(e)),
      });
  }

  constructor() {
    this.newAccount.country = this.auth.user()?.country || "BJ";
    this.newAccount.team = this.allowedProjects()[0] || "AZO";
    if (this.auth.isDirector())
      this.api.zones().subscribe({
        next: (z) => this.zones.set(z),
        error: (e) => this.error.set(apiError(e)),
      });
    this.loadCountries();
    this.load();
  }
  allowedProjects() {
    return this.auth.isSuperAdmin()
      ? this.allProjects
      : this.auth.user()?.projects || [];
  }
  availableCountries() {
    return this.countries().filter(
      (c) => this.auth.isSuperAdmin() || c.code === this.auth.user()?.country,
    );
  }
  countryLabel(code: string) {
    return this.countries().find((c) => c.code === code)?.name || code;
  }
  roleLabel(role: string) {
    return (
      (
        {
          COMMERCIAL: "Commercial",
          SUPERVISEUR: "Superviseur",
          DIRECTEUR_COMMERCIAL: "Directeur commercial",
          SUPER_ADMIN: "Super administrateur",
          ADMIN: "Administrateur",
        } as Record<string, string>
      )[role] || role
    );
  }
  count(role: string) {
    return this.members().filter((m) => m.role === role).length;
  }
  filtered() {
    const q = this.query.trim().toLowerCase();
    return this.members().filter(
      (m) =>
        (!q || (m.name + " " + m.email).toLowerCase().includes(q)) &&
        (!this.roleFilter || m.role === this.roleFilter) &&
        (!this.countryFilter || m.country === this.countryFilter),
    );
  }
  toggleNew(p: string) {
    this.newProjects = this.newProjects.includes(p)
      ? this.newProjects.filter((x) => x !== p)
      : [...this.newProjects, p];
  }
  toggleProject(p: string) {
    this.projects = this.projects.includes(p)
      ? this.projects.filter((x) => x !== p)
      : [...this.projects, p];
  }
  supervisorName(id: number | null) {
    return this.members().find((m) => m.id === id)?.name || "À affecter";
  }
  editable(m: Member) {
    return (
      m.id !== this.auth.user()?.id &&
      !["SUPER_ADMIN", "ADMIN"].includes(m.role) &&
      (this.auth.isSuperAdmin() || m.role !== "DIRECTEUR_COMMERCIAL")
    );
  }
  supervisors() {
    return this.members().filter(
      (m) =>
        m.role === "SUPERVISEUR" &&
        m.enabled &&
        m.team === this.team &&
        m.country === this.country &&
        m.id !== this.selected()?.id,
    );
  }
  loadCountries() {
    this.api.countries().subscribe({
      next: (c) => this.countries.set(c),
      error: (e) => this.error.set(apiError(e)),
    });
  }
  load() {
    this.loading.set(true);
    this.api
      .members()
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (m) => this.members.set(m),
        error: (e) => this.error.set(apiError(e)),
      });
  }
  createManager() {
    if (this.busy()) return;
    this.busy.set(true);
    this.error.set("");
    this.success.set("");
    const account = {
      ...this.newAccount,
      team:
        this.newRole === "DIRECTEUR_COMMERCIAL"
          ? this.newProjects[0] || "AZO"
          : this.newAccount.team,
    };
    this.auth
      .csrf()
      .pipe(
        switchMap(() =>
          this.api.createMember({
            account,
            role: this.newRole,
            zone: this.newRole === "SUPERVISEUR" ? this.newZone : "",
            projects: this.newProjects,
            zoneLevel: this.newLevel,
            zoneConfigId:
              this.newRole === "SUPERVISEUR" ? this.newZoneConfigId : null,
          }),
        ),
        finalize(() => this.busy.set(false)),
      )
      .subscribe({
        next: (m) => {
          this.newAccount = {
            firstName: "",
            lastName: "",
            email: "",
            password: "",
            team: this.allowedProjects()[0] || "AZO",
            country: this.auth.user()?.country || "BJ",
          };
          this.newZone = "";
          this.newZoneConfigId = null;
          this.success.set(
            this.roleLabel(m.role) +
              " créé et activé. Transmettez-lui personnellement ses identifiants.",
          );
          this.load();
        },
        error: (e) => this.error.set(apiError(e)),
      });
  }
  addCountry() {
    if (this.busy()) return;
    this.busy.set(true);
    this.error.set("");
    this.auth
      .csrf()
      .pipe(
        switchMap(() =>
          this.api.addCountry(this.countryCode, this.countryName),
        ),
        finalize(() => this.busy.set(false)),
      )
      .subscribe({
        next: () => {
          this.countryCode = "";
          this.countryName = "";
          this.success.set("Pays ajouté.");
          this.loadCountries();
        },
        error: (e) => this.error.set(apiError(e)),
      });
  }
  edit(m: Member) {
    this.selected.set(m);
    this.role = m.role;
    this.team = m.team || this.allowedProjects()[0] || "AZO";
    this.zone = m.zone || "";
    this.zoneConfigId = m.zoneConfigId;
    this.country = m.country;
    this.zoneLevel = m.zoneLevel || "COMMUNE";
    this.projects = [...m.projects];
    this.supervisorId = m.supervisorId;
    this.error.set("");
    setTimeout(() =>
      document
        .getElementById("assignment-form")
        ?.scrollIntoView({ behavior: "smooth" }),
    );
  }
  save() {
    const m = this.selected();
    if (!m || this.busy()) return;
    this.busy.set(true);
    this.error.set("");
    this.success.set("");
    this.auth
      .csrf()
      .pipe(
        switchMap(() =>
          this.api.assign(m.id, {
            role: this.role,
            team:
              this.role === "DIRECTEUR_COMMERCIAL"
                ? this.projects[0] || "AZO"
                : this.team,
            zone: this.zone,
            country: this.country,
            projects: this.projects,
            zoneLevel: this.zoneLevel,
            zoneConfigId:
              this.role === "SUPERVISEUR" ? this.zoneConfigId : null,
            supervisorId: this.role === "COMMERCIAL" ? this.supervisorId : null,
          }),
        ),
        finalize(() => this.busy.set(false)),
      )
      .subscribe({
        next: () => {
          this.selected.set(null);
          this.success.set(
            "Affectation enregistrée. Le compte doit se reconnecter.",
          );
          this.load();
        },
        error: (e) => this.error.set(apiError(e)),
      });
  }
}
