import { Routes } from "@angular/router";
import { DashboardComponent } from "./pages/dashboard.component";
import { ReportsComponent } from "./pages/reports.component";
import { LoginComponent } from "./pages/login.component";
import { CommercialsComponent } from "./pages/commercials.component";
import { RegisterComponent } from "./pages/register.component";
import { authGuard, superAdminGuard } from "./core/auth.guard";
import { PasswordResetComponent } from "./pages/password-reset.component";
export const routes: Routes = [
  { path: "", redirectTo: "dashboard", pathMatch: "full" },
  {
    path: "dashboard",
    component: DashboardComponent,
    canActivate: [authGuard],
  },
  { path: "rapports", component: ReportsComponent, canActivate: [authGuard] },
  { path: "inscription", component: RegisterComponent },
  {
    path: "commerciaux",
    component: CommercialsComponent,
    canActivate: [superAdminGuard],
  },
  { path: "mot-de-passe-oublie", component: PasswordResetComponent },
  { path: "reinitialiser-mot-de-passe", component: PasswordResetComponent },
  { path: "connexion", component: LoginComponent },
  { path: "**", redirectTo: "dashboard" },
];
