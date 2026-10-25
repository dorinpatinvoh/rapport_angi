import { Component, computed, inject, signal } from "@angular/core";
import { DatePipe } from "@angular/common";
import { HttpClient } from "@angular/common/http";
import { FormBuilder, ReactiveFormsModule, Validators } from "@angular/forms";
import { finalize, switchMap } from "rxjs";
import { AuthService } from "../core/auth.service";
import {
  apiError,
  Report,
  ReportService,
  statusLabel,
} from "../core/report.service";
@Component({
  selector: "app-reports",
  standalone: true,
  imports: [DatePipe, ReactiveFormsModule],
  templateUrl: "./reports.component.html",
})
export class ReportsComponent {
  private api = inject(ReportService);
  readonly auth = inject(AuthService);
  private fb = inject(FormBuilder);
  private http = inject(HttpClient);
  readonly reports = signal<Report[]>([]);
  readonly error = signal("");
  readonly success = signal("");
  readonly loading = signal(false);
  readonly busy = signal(false);
  readonly formVisible = signal(false);
  readonly editing = signal<Report | null>(null);
  readonly detail = signal<Report | null>(null);
  readonly pendingDelete = signal<Report | null>(null);
  readonly query = signal("");
  readonly statusFilter = signal("");
  readonly teamFilter = signal("");
  readonly integrationReady = signal(false);
  readonly label = statusLabel;
  readonly today = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Africa/Lagos",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date());
  readonly filtered = computed(() =>
    this.reports().filter(
      (r) =>
        (!this.statusFilter() || r.status === this.statusFilter()) &&
        (!this.teamFilter() || r.team === this.teamFilter()) &&
        `${r.title} ${r.content} ${r.authorName}`
          .toLocaleLowerCase()
          .includes(this.query().toLocaleLowerCase()),
    ),
  );
  readonly form = this.fb.nonNullable.group({
    title: [
      "",
      [
        Validators.required,
        Validators.pattern(/\S/),
        Validators.maxLength(200),
      ],
    ],
    content: ["", Validators.maxLength(50000)],
    status: ["DRAFT", Validators.required],
    team: ["AZO", Validators.required],
    reportDate: [this.today, Validators.required],
    mode: ["WRITTEN", Validators.required],
    prospectsContacted: [
      0,
      [
        Validators.required,
        Validators.min(0),
        Validators.max(1000000),
        Validators.pattern(/^\d+$/),
      ],
    ],
    registrations: [
      0,
      [
        Validators.required,
        Validators.min(0),
        Validators.max(1000000),
        Validators.pattern(/^\d+$/),
      ],
    ],
    syncStats: [false],
  });
  constructor() {
    this.load();
    this.http
      .get<{ configured: boolean }>("/api/integrations/status")
      .subscribe({
        next: (r) => this.integrationReady.set(r.configured),
        error: () => this.integrationReady.set(false),
      });
  }
  load(clearNotice = false) {
    if (clearNotice) this.success.set("");
    this.loading.set(true);
    this.error.set("");
    this.api
      .list()
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (r) => {
          this.reports.set(r);
          const opened = this.detail();
          if (opened)
            this.detail.set(r.find((item) => item.id === opened.id) ?? null);
        },
        error: (e) => this.error.set(apiError(e)),
      });
  }
  canEdit(r: Report) {
    return (
      r.authorId === this.auth.user()?.id &&
      ["DRAFT", "RETURNED"].includes(r.status)
    );
  }
  canReview(r: Report) {
    return (
      this.auth.isSuperAdmin() &&
      r.status === "SUBMITTED" &&
      r.authorId !== this.auth.user()?.id
    );
  }
  read(r: Report) {
    this.detail.set(r);
    this.formVisible.set(false);
    this.error.set("");
    this.jump("report-detail");
  }
  edit(r?: Report) {
    this.editing.set(r ?? null);
    this.form.reset({
      title: r?.title ?? "",
      content: r?.content ?? "",
      status: r?.status === "SUBMITTED" ? "SUBMITTED" : "DRAFT",
      team: r?.team ?? this.auth.user()?.team ?? "AZO",
      reportDate: r?.reportDate ?? this.today,
      mode: r?.mode ?? "WRITTEN",
      prospectsContacted: r?.prospectsContacted ?? 0,
      registrations: r?.registrations ?? 0,
      syncStats: false,
    });
    this.formVisible.set(true);
    this.detail.set(null);
    this.error.set("");
    this.success.set("");
    this.jump("report-form");
  }
  save() {
    if (this.form.invalid || this.busy()) return;
    const data = {
      ...this.form.getRawValue(),
      version: this.editing()?.version ?? null,
    };
    if (data.mode === "WRITTEN" && !data.content.trim()) {
      this.error.set("Décrivez votre journée avant d’enregistrer.");
      return;
    }
    this.busy.set(true);
    this.error.set("");
    this.success.set("");
    const r = this.editing();
    this.auth
      .csrf()
      .pipe(
        switchMap(() =>
          r ? this.api.update(r.id, data) : this.api.create(data),
        ),
        finalize(() => this.busy.set(false)),
      )
      .subscribe({
        next: (report) => {
          this.formVisible.set(false);
          this.detail.set(report);
          this.success.set(
            report.status === "SUBMITTED"
              ? "Rapport soumis au super administrateur."
              : "Brouillon enregistré.",
          );
          this.load();
          this.jump("report-detail");
        },
        error: (e) => this.error.set(apiError(e)),
      });
  }
  previewStats() {
    if (this.busy() || !this.form.controls.reportDate.value) return;
    this.busy.set(true);
    this.error.set("");
    this.http
      .get<{ prospectsContacted: number; registrations: number }>(
        "/api/integrations/my-stats",
        { params: { date: this.form.controls.reportDate.value } },
      )
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: (r) => {
          this.form.patchValue({
            prospectsContacted: r.prospectsContacted,
            registrations: r.registrations,
          });
          this.success.set(
            "Aperçu chargé. Les statistiques seront relues côté serveur à l’enregistrement.",
          );
        },
        error: (e) => this.error.set(apiError(e)),
      });
  }
  review(r: Report, action: string, comment: string) {
    if (this.busy()) return;
    if (action === "RETURNED" && !comment.trim()) {
      this.error.set("Précisez les informations à confirmer ou corriger.");
      return;
    }
    this.busy.set(true);
    this.error.set("");
    this.success.set("");
    this.auth
      .csrf()
      .pipe(
        switchMap(() => this.api.review(r, action, comment)),
        finalize(() => this.busy.set(false)),
      )
      .subscribe({
        next: (value) => {
          this.detail.set(value);
          this.success.set(
            action === "RETURNED"
              ? "Rapport renvoyé au commercial."
              : "Rapport confirmé.",
          );
          this.load();
        },
        error: (e) => this.error.set(apiError(e)),
      });
  }
  remove(r: Report) {
    if (this.busy()) return;
    this.busy.set(true);
    this.error.set("");
    this.auth
      .csrf()
      .pipe(
        switchMap(() => this.api.delete(r.id)),
        finalize(() => this.busy.set(false)),
      )
      .subscribe({
        next: () => {
          this.pendingDelete.set(null);
          if (this.detail()?.id === r.id) this.detail.set(null);
          this.success.set("Brouillon supprimé.");
          this.load();
        },
        error: (e) => this.error.set(apiError(e)),
      });
  }
  download(r: Report) {
    if (this.busy()) return;
    this.busy.set(true);
    this.error.set("");
    this.api
      .pdf(r.id)
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: (blob) => {
          const url = URL.createObjectURL(blob),
            a = document.createElement("a");
          a.href = url;
          a.download = `rapport-${r.team ?? "angi"}-${r.reportDate}-${r.id}.pdf`;
          a.click();
          setTimeout(() => URL.revokeObjectURL(url), 30000);
        },
        error: () =>
          this.error.set(
            "Téléchargement impossible. Vérifiez votre session et réessayez.",
          ),
      });
  }
  private jump(id: string) {
    setTimeout(() =>
      document
        .getElementById(id)
        ?.scrollIntoView({ behavior: "smooth", block: "start" }),
    );
  }
}
