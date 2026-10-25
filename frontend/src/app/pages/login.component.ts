import { Component, inject, signal } from "@angular/core";
import { FormBuilder, ReactiveFormsModule, Validators } from "@angular/forms";
import { Router, RouterLink } from "@angular/router";
import { finalize } from "rxjs";
import { AuthService } from "../core/auth.service";
@Component({
  selector: "app-login",
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  template: ` <div class="panel mx-auto" style="max-width:460px">
    <h1 class="h3 fw-bold mb-3">Connexion</h1>
    <p class="text-secondary">Accédez à votre espace ANGI DSN PRO.</p>
    <form [formGroup]="form" (ngSubmit)="submit()">
      <div class="mb-3">
        <label for="email" class="form-label">E-mail</label
        ><input
          id="email"
          class="form-control"
          type="email"
          autocomplete="username"
          formControlName="email"
          required
        />
      </div>
      <div class="mb-3">
        <label for="password" class="form-label">Mot de passe</label
        ><input
          id="password"
          class="form-control"
          type="password"
          autocomplete="current-password"
          formControlName="password"
          required
        />
      </div>
      @if (error()) {
        <div class="alert alert-danger" role="alert">{{ error() }}</div>
      }
      <button
        class="btn btn-primary w-100"
        [disabled]="form.invalid || busy()"
        type="submit"
      >
        {{ busy() ? "Connexion…" : "Se connecter" }}
      </button>
      <p class="text-center mt-3 mb-0">
        Nouveau commercial ? <a routerLink="/inscription">Créer mon compte</a>
      </p>
    </form>
  </div>`,
})
export class LoginComponent {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);
  private router = inject(Router);
  readonly busy = signal(false);
  readonly error = signal("");
  readonly form = this.fb.nonNullable.group({
    email: ["", [Validators.required, Validators.email]],
    password: ["", Validators.required],
  });
  submit() {
    if (this.form.invalid || this.busy()) return;
    this.busy.set(true);
    this.error.set("");
    const { email, password } = this.form.getRawValue();
    this.auth
      .login(email.trim(), password)
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: () => {
          this.form.controls.password.reset();
          void this.router.navigateByUrl("/dashboard");
        },
        error: (e) =>
          this.error.set(
            e.status === 401
              ? "E-mail ou mot de passe incorrect, ou compte désactivé."
              : "Connexion impossible. Vérifiez que le serveur est disponible.",
          ),
      });
  }
}
