import { AuthService } from "./core/auth.service";
import { Component, inject, signal } from "@angular/core";
import {
  Router,
  RouterLink,
  RouterLinkActive,
  RouterOutlet,
} from "@angular/router";
@Component({
  selector: "app-root",
  standalone: true,
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  template: ` <div class="app-shell">
    <aside class="side">
      <a routerLink="/dashboard" class="brand-link"
        ><img src="/api/branding/angi.jpeg" alt="ANGI DSN PRO SARL" /><span
          >ANGI DSN PRO<small>Suivi commercial</small></span
        ></a
      >
      <nav aria-label="Navigation principale">
        <a routerLink="/dashboard" routerLinkActive="active"
          ><i aria-hidden="true" class="bi bi-grid"></i>Tableau de bord</a
        ><a routerLink="/rapports" routerLinkActive="active"
          ><i aria-hidden="true" class="bi bi-file-earmark-text"></i>Rapports</a
        >
        @if (auth.isSuperAdmin()) {
          <a routerLink="/commerciaux" routerLinkActive="active"
            ><i aria-hidden="true" class="bi bi-people"></i>Commerciaux</a
          >
        }
        @if (!auth.user()) {
          <a routerLink="/connexion" routerLinkActive="active">Connexion</a
          ><a routerLink="/inscription" routerLinkActive="active"
            >Inscription</a
          >
        }
      </nav>
      @if (auth.user(); as u) {
        <div class="user-block">
          <strong>{{ u.name }}</strong
          ><span>{{
            auth.isSuperAdmin()
              ? "Super administrateur"
              : u.team || "Équipe à affecter"
          }}</span
          ><button
            class="btn btn-outline-light mt-3"
            [disabled]="busy()"
            (click)="logout()"
          >
            Déconnexion
          </button>
        </div>
      }
      @if (error()) {
        <p role="alert">{{ error() }}</p>
      }
    </aside>
    <div class="workspace">
      <header class="topbar">
        <span>Rapports terrain</span
        ><span class="header-tag">ANGI DSN PRO SARL</span>
      </header>
      <main class="content"><router-outlet /></main>
    </div>
  </div>`,
})
export class AppComponent {
  readonly auth = inject(AuthService);
  private router = inject(Router);
  readonly busy = signal(false);
  readonly error = signal("");
  logout() {
    this.busy.set(true);
    this.error.set("");
    this.auth.logout().subscribe({
      next: () => {
        this.busy.set(false);
        void this.router.navigateByUrl("/connexion");
      },
      error: () => {
        this.busy.set(false);
        this.error.set("Déconnexion impossible. Réessayez.");
      },
    });
  }
}
