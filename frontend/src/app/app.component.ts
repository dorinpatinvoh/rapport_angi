import { CommunicationService } from "./core/communication.service";
import { apiUrl } from "./core/api-url";
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
  template: ` <a class="skip-link" href="#main-content">Aller au contenu</a>
    <div class="app-shell" [class.public-shell]="!auth.user()">
      <aside class="side">
        <div class="brand-row">
          <a routerLink="/dashboard" class="brand-link"
            ><img
              [src]="apiUrl('/api/branding/angi.jpeg')"
              alt="ANGI DSN PRO SARL"
            /><span>ANGI DSN PRO<small>Suivi commercial</small></span></a
          >
          @if (auth.user()) {
            <button
              class="mobile-menu btn btn-outline-primary"
              type="button"
              (click)="mobileMenu.set(!mobileMenu())"
              [attr.aria-expanded]="mobileMenu()"
              aria-controls="main-navigation"
            >
              <i class="bi bi-list" aria-hidden="true"></i>Menu
            </button>
          }
        </div>
        @if (!auth.user()) {
          <div class="login-intro">
            <p class="eyebrow">Le terrain, en lien</p>
            <h2>Vos équipes.<br />Vos projets.<br />Une vision claire.</h2>
            <p>
              Suivez l’activité et partagez les informations utiles, où que vous
              soyez.
            </p>
            <span class="intro-label">ANGI DSN PRO · Espace professionnel</span>
          </div>
        }

        <nav
          id="main-navigation"
          aria-label="Navigation principale"
          [class.mobile-open]="mobileMenu()"
          (click)="mobileMenu.set(false)"
        >
          @if (auth.user()) {
            <a routerLink="/dashboard" routerLinkActive="active"
              ><i aria-hidden="true" class="bi bi-grid"></i>Tableau de bord</a
            ><a routerLink="/rapports" routerLinkActive="active"
              ><i aria-hidden="true" class="bi bi-file-earmark-text"></i
              >Rapports</a
            >
            @if (auth.isDirector()) {
              <a routerLink="/commerciaux" routerLinkActive="active"
                ><i aria-hidden="true" class="bi bi-people"></i>Commerciaux</a
              >
            }
            @if (auth.isDirector()) {
              <a routerLink="/grilles-objectifs" routerLinkActive="active"
                ><i class="bi bi-bullseye" aria-hidden="true"></i>Grilles
                d’objectifs</a
              >
            }
            @if (auth.isManagement()) {
              <a routerLink="/organisation" routerLinkActive="active"
                >Organisation et zones</a
              >
              <a routerLink="/syntheses" routerLinkActive="active"
                >Synthèses et rapport général</a
              >
            }
            <a routerLink="/communiques" routerLinkActive="active"
              ><i class="bi bi-megaphone" aria-hidden="true"></i>Communiqués</a
            >
            <a
              routerLink="/notifications"
              routerLinkActive="active"
              aria-label="Notifications"
              ><i class="bi bi-bell" aria-hidden="true"></i>Notifications
              @if (comms.unread()) {
                <span class="nav-count">{{ comms.unread() }}</span>
              }
            </a>
          }
        </nav>
        @if (auth.user(); as u) {
          <div class="user-block">
            <strong>{{ u.name }}</strong
            ><span>{{
              auth.isSuperAdmin()
                ? "Super administrateur"
                : auth.isDirector()
                  ? "Directeur commercial"
                  : auth.isSupervisor()
                    ? "Superviseur · " + (u.team || "")
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
          <div>
            <span class="header-tag">ESPACE COMMERCIAL</span
            ><strong class="d-block">{{
              auth.user()
                ? "Bonjour, " + auth.user()?.name?.split(" ")?.[0]
                : "Bienvenue chez ANGI"
            }}</strong>
          </div>
          @if (auth.user()) {
            <a
              routerLink="/notifications"
              class="notification-button"
              aria-label="Voir les notifications"
              ><i class="bi bi-bell" aria-hidden="true"></i>
              @if (comms.unread()) {
                <span>{{ comms.unread() }}</span>
              }
            </a>
          }
        </header>
        <main id="main-content" class="content"><router-outlet /></main>
      </div>
    </div>`,
})
export class AppComponent {
  readonly mobileMenu = signal(false);
  readonly apiUrl = apiUrl;
  readonly comms = inject(CommunicationService);
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
