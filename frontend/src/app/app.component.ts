import { AuthService } from './core/auth.service';
import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
@Component({selector:'app-root',standalone:true,imports:[RouterLink,RouterLinkActive,RouterOutlet],template:`
<div class="container-fluid"><div class="row">
<aside class="col-md-3 col-lg-2 side p-3"><div class="brand fs-5 mb-4">ANGI DSN PRO</div>
<nav aria-label="Navigation principale" class="d-flex flex-row flex-md-column flex-wrap gap-1">
<a routerLink="/dashboard" routerLinkActive="active"><i class="bi bi-grid me-2"></i>Tableau de bord</a>
<a routerLink="/rapports" routerLinkActive="active"><i class="bi bi-file-earmark-text me-2"></i>Rapports</a>
@if(!auth.user()){<a routerLink="/connexion" routerLinkActive="active"><i class="bi bi-person me-2"></i>Connexion</a>} @else {<span class="small p-2">{{auth.user()?.name}}</span><button class="btn btn-outline-light mt-2" [disabled]="busy()" (click)="logout()">Déconnexion</button>} @if(error()){<p role="alert">{{error()}}</p>}</nav>
</aside><div class="col-md-9 col-lg-10 p-0"><header class="topbar d-flex justify-content-between"><span class="fw-semibold">Gestion des rapports</span><span class="badge text-bg-light border">ANGI</span></header><main class="content"><router-outlet /></main></div>
</div></div>`})
export class AppComponent {
  readonly auth=inject(AuthService); private router=inject(Router);readonly busy=signal(false);readonly error=signal('');
  logout(){this.busy.set(true);this.error.set('');this.auth.logout().subscribe({next:()=>{this.busy.set(false);void this.router.navigateByUrl('/connexion');},error:()=>{this.busy.set(false);this.error.set('Déconnexion impossible. Réessayez.');}});}
}
