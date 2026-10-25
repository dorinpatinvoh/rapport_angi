import { Component, inject, signal } from "@angular/core";
import { FormBuilder, ReactiveFormsModule, Validators } from "@angular/forms";
import { finalize, switchMap } from "rxjs";
import { AccountService, Commercial } from "../core/account.service";
import { AuthService } from "../core/auth.service";
import { apiError } from "../core/report.service";
@Component({
  selector: "app-commercials",
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: "./commercials.component.html",
})
export class CommercialsComponent {
  private fb = inject(FormBuilder);
  private accounts = inject(AccountService);
  private auth = inject(AuthService);
  readonly users = signal<Commercial[]>([]);
  readonly busy = signal(false);
  readonly loading = signal(false);
  readonly error = signal("");
  readonly success = signal("");
  readonly form = this.fb.nonNullable.group({
    firstName: [
      "",
      [
        Validators.required,
        Validators.pattern(/\S/),
        Validators.maxLength(100),
      ],
    ],
    lastName: [
      "",
      [
        Validators.required,
        Validators.pattern(/\S/),
        Validators.maxLength(100),
      ],
    ],
    email: [
      "",
      [Validators.required, Validators.email, Validators.maxLength(150)],
    ],
    password: [
      "",
      [Validators.required, Validators.minLength(12), Validators.maxLength(72)],
    ],
    team: ["AZO", Validators.required],
    referralCode: ["", Validators.maxLength(100)],
  });
  constructor() {
    this.load();
  }
  load() {
    this.loading.set(true);
    this.accounts
      .list()
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (r) => this.users.set(r),
        error: (e) => this.error.set(apiError(e)),
      });
  }
  create() {
    if (this.busy() || this.form.invalid) return;
    this.busy.set(true);
    this.error.set("");
    this.success.set("");
    this.auth
      .csrf()
      .pipe(
        switchMap(() => this.accounts.create(this.form.getRawValue())),
        finalize(() => this.busy.set(false)),
      )
      .subscribe({
        next: (r) => {
          this.form.reset({ team: "AZO" });
          this.success.set(
            `Compte de ${r.firstName} ${r.lastName} créé et activé. Transmettez-lui personnellement ses identifiants.`,
          );
          this.load();
        },
        error: (e) => this.error.set(apiError(e)),
      });
  }
  update(u: Commercial, team: string, code: string, enabled: boolean) {
    if (this.busy()) return;
    this.busy.set(true);
    this.error.set("");
    this.success.set("");
    this.auth
      .csrf()
      .pipe(
        switchMap(() => this.accounts.update(u.id, team, code, enabled)),
        finalize(() => this.busy.set(false)),
      )
      .subscribe({
        next: () => {
          this.success.set("Compte commercial mis à jour.");
          this.load();
        },
        error: (e) => this.error.set(apiError(e)),
      });
  }
}
