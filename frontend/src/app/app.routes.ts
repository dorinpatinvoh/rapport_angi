import { Routes } from "@angular/router";
import { DashboardComponent } from "./pages/dashboard.component";
import { ReportsComponent } from "./pages/reports.component";
import { LoginComponent } from "./pages/login.component";
import { CommercialsComponent } from "./pages/commercials.component";
import { authGuard, directorGuard, managementGuard } from "./core/auth.guard";
import { PasswordResetComponent } from "./pages/password-reset.component";
import { OrganizationComponent } from "./pages/organization.component";
import { SummariesComponent } from "./pages/summaries.component";
import {
  CommunicationsComponent,
  NotificationsComponent,
} from "./pages/communications.component";
import { ZoneGridsComponent } from "./pages/zone-grids.component";
export const routes: Routes = [
  { path: "", redirectTo: "dashboard", pathMatch: "full" },
  {
    path: "dashboard",
    component: DashboardComponent,
    canActivate: [authGuard],
  },
  { path: "rapports", component: ReportsComponent, canActivate: [authGuard] },
  {
    path: "organisation",
    component: OrganizationComponent,
    canActivate: [managementGuard],
  },
  {
    path: "syntheses",
    component: SummariesComponent,
    canActivate: [managementGuard],
  },
  {
    path: "grilles-objectifs",
    component: ZoneGridsComponent,
    canActivate: [directorGuard],
  },
  { path: "inscription", redirectTo: "connexion", pathMatch: "full" },
  {
    path: "communiques",
    component: CommunicationsComponent,
    canActivate: [authGuard],
  },
  {
    path: "notifications",
    component: NotificationsComponent,
    canActivate: [authGuard],
  },
  {
    path: "commerciaux",
    component: CommercialsComponent,
    canActivate: [directorGuard],
  },
  { path: "mot-de-passe-oublie", component: PasswordResetComponent },
  { path: "reinitialiser-mot-de-passe", component: PasswordResetComponent },
  { path: "connexion", component: LoginComponent },
  { path: "**", redirectTo: "dashboard" },
];
