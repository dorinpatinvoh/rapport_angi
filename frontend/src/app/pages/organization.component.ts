import { Component, inject, signal } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { RouterLink } from "@angular/router";
import { finalize, switchMap } from "rxjs";
import { AuthService } from "../core/auth.service";
import { SalesService, Member } from "../core/sales.service";
import { apiError } from "../core/report.service";
@Component({
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <div class="page-heading">
      <div>
        <p class="eyebrow">Direction commerciale</p>
        <h1>Organisation et zones</h1>
        <p class="text-secondary">
          Chaque commercial dépend d'un superviseur de la même équipe. Le
          superviseur porte la zone.
        </p>
      </div>
      @if (auth.isDirector()) {
        <a routerLink="/commerciaux" class="btn btn-primary"
          >Créer / activer un compte</a
        >
      }
    </div>
    @if (error()) {
      <div role="alert" class="alert alert-danger">{{ error() }}</div>
    }
    @if (success()) {
      <div role="status" class="alert alert-success">{{ success() }}</div>
    }
    @if (auth.isDirector()) {
      <details class="panel mb-4">
        <summary class="fw-semibold">Créer un responsable</summary>
        <p class="text-secondary mt-3">
          Le compte sera actif immédiatement. Pour créer un commercial, utilisez
          la page Commerciaux.
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
              <label for="managerTeam" class="form-label"
                >Équipe du responsable</label
              ><select
                id="managerTeam"
                name="managerTeam"
                [(ngModel)]="newAccount.team"
                class="form-select"
                required
              >
                <option value="AZO">AZO</option>
                <option value="ZEHAMI">Zehami</option>
              </select>
            </div>
            @if (newRole === "SUPERVISEUR") {
              <div class="col-12">
                <label for="managerZone" class="form-label"
                  >Zone du nouveau superviseur</label
                ><input
                  id="managerZone"
                  name="managerZone"
                  [(ngModel)]="newZone"
                  class="form-control"
                  required
                  pattern=".*\\S.*"
                  maxlength="150"
                />
              </div>
            }
          </div>
          <button
            class="btn btn-primary mt-3"
            [disabled]="createForm.invalid || busy()"
          >
            Créer le responsable
          </button>
        </form>
      </details>
    }
    @if (loading()) {
      <p role="status">Chargement…</p>
    }
    <div class="report-grid">
      @for (m of members(); track m.id) {
        <article class="panel">
          <h2 class="h5">{{ m.name }}</h2>
          <p class="text-break">{{ m.email }}</p>
          <p>
            <strong>{{ roleLabel(m.role) }}</strong> ·
            {{ m.team || "Équipe à affecter" }} ·
            {{ m.enabled ? "Actif" : "En attente" }}
          </p>
          <p>Zone : {{ m.zone || "Non affectée" }}</p>
          @if (m.role === "COMMERCIAL") {
            <p>Superviseur : {{ supervisorName(m.supervisorId) }}</p>
          }
          @if (auth.isDirector() && editable(m)) {
            <button
              class="btn btn-outline-primary"
              (click)="edit(m)"
              [disabled]="busy()"
            >
              Affecter / nommer
            </button>
          }
        </article>
      }
    </div>
    @if (selected(); as m) {
      <section class="panel mt-4" id="assignment-form">
        <h2 class="h4">Affectation de {{ m.name }}</h2>
        <form (ngSubmit)="save()" #f="ngForm">
          <div class="row g-3">
            <div class="col-md-6">
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
            <div class="col-md-6">
              <label for="assignmentTeam" class="form-label">Équipe</label
              ><select
                id="assignmentTeam"
                name="team"
                [(ngModel)]="team"
                (ngModelChange)="supervisorId = null"
                class="form-select"
                required
              >
                <option value="AZO">AZO</option>
                <option value="ZEHAMI">Zehami</option>
              </select>
            </div>
            @if (role === "SUPERVISEUR") {
              <div class="col-12">
                <label for="assignmentZone" class="form-label"
                  >Zone du superviseur</label
                ><input
                  id="assignmentZone"
                  name="zone"
                  [(ngModel)]="zone"
                  maxlength="150"
                  required
                  class="form-control"
                  placeholder="Ex. Cotonou Nord"
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
          <p class="small text-secondary mt-3">
            L'affectation déconnecte ce compte pour appliquer ses nouveaux
            droits. Les rapports déjà envoyés conservent leur superviseur et
            leur zone.
          </p>
          <button class="btn btn-primary me-2" [disabled]="f.invalid || busy()">
            Enregistrer l'affectation</button
          ><button
            type="button"
            class="btn btn-outline-secondary"
            (click)="selected.set(null)"
          >
            Annuler
          </button>
        </form>
      </section>
    }
  `,
})
export class OrganizationComponent {
  readonly auth = inject(AuthService);
  private api = inject(SalesService);
  readonly members = signal<Member[]>([]);
  readonly selected = signal<Member | null>(null);
  readonly error = signal("");
  readonly success = signal("");
  readonly busy = signal(false);
  readonly loading = signal(false);
  role = "COMMERCIAL";
  team = "AZO";
  zone = "";
  supervisorId: number | null = null;
  newAccount = {
    firstName: "",
    lastName: "",
    email: "",
    password: "",
    team: "AZO",
  };
  newRole = "SUPERVISEUR";
  newZone = "";
  createManager() {
    if (this.busy()) return;
    this.busy.set(true);
    this.error.set("");
    this.success.set("");
    this.auth
      .csrf()
      .pipe(
        switchMap(() =>
          this.api.createMember({
            account: this.newAccount,
            role: this.newRole,
            zone: this.newRole === "SUPERVISEUR" ? this.newZone : "",
          }),
        ),
        finalize(() => this.busy.set(false)),
      )
      .subscribe({
        next: (member) => {
          this.newAccount = {
            firstName: "",
            lastName: "",
            email: "",
            password: "",
            team: "AZO",
          };
          this.newZone = "";
          this.success.set(
            `${this.roleLabel(member.role)} créé et activé. Transmettez-lui personnellement ses identifiants.`,
          );
          this.load();
        },
        error: (e) => this.error.set(apiError(e)),
      });
  }
  constructor() {
    this.load();
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
        m.id !== this.selected()?.id,
    );
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
  edit(m: Member) {
    this.selected.set(m);
    this.role = m.role;
    this.team = m.team || "AZO";
    this.zone = m.zone || "";
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
            team: this.team,
            zone: this.zone,
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
