import { Component, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
@Component({selector:'app-root',standalone:true,imports:[RouterLink,RouterLinkActive,RouterOutlet],template:`
<div class="container-fluid"><div class="row">
<aside class="col-md-3 col-lg-2 side p-3"><div class="brand fs-5 mb-4">ANGI DSN PRO</div>
<nav aria-label="Navigation principale" class="d-flex flex-row flex-md-column flex-wrap gap-1">
<a routerLink="/dashboard" routerLinkActive="active"><i class="bi bi-grid me-2"></i>Tableau de bord</a>
<a routerLink="/rapports" routerLinkActive="active"><i class="bi bi-file-earmark-text me-2"></i>Rapports</a>
<a routerLink="/connexion" routerLinkActive="active"><i class="bi bi-person me-2"></i>Connexion</a></nav>
</aside><div class="col-md-9 col-lg-10 p-0"><header class="topbar d-flex justify-content-between"><span class="fw-semibold">Gestion des rapports</span><span class="badge text-bg-light border">ANGI</span></header><main class="content"><router-outlet /></main></div>
</div></div>`})
export class AppComponent {}
