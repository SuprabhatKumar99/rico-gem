import { CommonModule } from "@angular/common";
import { Component, OnInit } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { ApiService } from "../core/api.service";
import { User } from "../models";

interface RoleCapability {
  label: string;
  grants: string[];
}

const ROLE_CAPABILITIES: RoleCapability[] = [
  { label: "write and edit articles", grants: ["ADMIN", "EDITOR"] },
  { label: "approve or reject articles in the review queue", grants: ["ADMIN", "REVIEWER"] },
  {
    label: "manage users, categories, branding, synonyms, webhooks and analytics",
    grants: ["ADMIN"]
  },
  { label: "search the knowledge base through Agent access", grants: ["ADMIN", "AGENT_VIEWER"] }
];

type StrengthLevel = "none" | "weak" | "medium" | "strong";

@Component({
  selector: "rk-users",
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div>
      <p class="text-sm font-semibold text-slate-500">Administration</p>
      <h1 class="mt-1 text-3xl font-bold tracking-tight">Users</h1>
      <p class="mt-2 text-sm text-slate-500">Manage tenant users and their backend roles.</p>

      <div class="mt-7 grid gap-6 xl:grid-cols-[380px_1fr]">
        <form class="rk-card p-6" (ngSubmit)="create()">
          <h2 class="font-bold">Create user</h2>

          <div class="mt-5 space-y-4">
            <div>
              <label class="rk-label" for="name">Name</label>
              <input id="name" class="rk-input" [(ngModel)]="form.name" name="name" required />
            </div>

            <div>
              <label class="rk-label" for="email">Email</label>
              <input id="email" class="rk-input" type="email" [(ngModel)]="form.email" name="email" required />
            </div>

            <div>
              <label class="rk-label" for="password">Password</label>
              <div class="flex gap-2">
                <input
                  id="password"
                  class="rk-input"
                  type="password"
                  [(ngModel)]="form.password"
                  name="password"
                  minlength="12"
                  required
                />
                <button
                  type="button"
                  class="rk-btn rk-btn-secondary shrink-0"
                  (click)="generatePassword()"
                >
                  Generate
                </button>
                <button
                  type="button"
                  class="grid h-10 w-10 shrink-0 place-items-center rounded-xl border border-slate-200 text-slate-500 hover:bg-slate-50"
                  [attr.aria-label]="copied ? 'Password copied' : 'Copy password'"
                  [disabled]="!form.password"
                  (click)="copyPassword()"
                >
                  {{ copied ? "✓" : "⧉" }}
                </button>
              </div>

              <div *ngIf="passwordStrength !== 'none'" class="mt-2">
                <div class="h-1.5 w-full overflow-hidden rounded-full bg-slate-200">
                  <div
                    class="h-full rounded-full transition-all duration-200"
                    [ngClass]="strengthBarClass"
                    [style.width.%]="strengthPercent"
                  ></div>
                </div>
                <p class="mt-1.5 text-xs font-semibold" [ngClass]="strengthTextClass">
                  Password strength: {{ passwordStrength }}
                </p>
              </div>
            </div>

            <fieldset>
              <legend class="rk-label">Roles</legend>
              <div class="grid grid-cols-2 gap-2">
                <label *ngFor="let role of roles" class="flex cursor-pointer items-center gap-2 rounded-xl border border-slate-200 p-3 text-sm">
                  <input
                    type="checkbox"
                    [checked]="form.roles.includes(role)"
                    (change)="toggleRole(role)"
                  />
                  {{ role }}
                </label>
              </div>

              <div class="mt-3 rounded-xl border border-slate-200 bg-slate-50 p-3">
                <p class="text-[11px] font-bold uppercase tracking-wide text-slate-400">What this person can do</p>
                <ul class="mt-2 space-y-1.5">
                  <li *ngIf="!form.roles.length" class="text-xs text-slate-500">
                    No roles ticked, so this user will not be able to do anything yet.
                  </li>
                  <li
                    *ngFor="let line of roleSummary"
                    [class.text-emerald-700]="line.allowed"
                    [class.text-slate-600]="!line.allowed"
                    class="flex items-start gap-2 text-xs leading-5"
                  >
                    <span class="mt-0.5 font-bold">{{ line.allowed ? "✓" : "✕" }}</span>
                    <span>{{ line.text }}</span>
                  </li>
                </ul>
              </div>
            </fieldset>

            <button class="rk-btn rk-btn-primary w-full">Create user</button>
          </div>
        </form>

        <section class="space-y-6">
          <form class="rk-card p-6" (ngSubmit)="invite()">
            <h2 class="font-bold">Invite user</h2>
            <p class="mt-1 text-xs text-slate-500">Use the backend invitation flow instead of provisioning a password locally.</p>
            <div class="mt-4 space-y-3">
              <input class="rk-input" [(ngModel)]="inviteForm.name" name="inviteName" placeholder="Name" required />
              <input class="rk-input" type="email" [(ngModel)]="inviteForm.email" name="inviteEmail" placeholder="Email" required />
              <div class="grid grid-cols-2 gap-2">
                <label *ngFor="let role of roles" class="flex items-center gap-2 rounded-xl border border-slate-200 p-2 text-xs"><input type="checkbox" [checked]="inviteForm.roles.includes(role)" (change)="toggleInviteRole(role)" />{{ role }}</label>
              </div>
              <button class="rk-btn rk-btn-secondary w-full" [disabled]="!inviteForm.email.trim()">Send invitation</button>
            </div>
          </form>

          <section *ngIf="editing" class="rk-card p-6">
            <div class="flex items-center justify-between"><h2 class="font-bold">Edit user</h2><button type="button" class="text-slate-400" (click)="editing = null">✕</button></div>
            <div class="mt-4 space-y-3">
              <input class="rk-input" [(ngModel)]="editForm.name" [ngModelOptions]="{ standalone: true }" placeholder="Name" />
              <input class="rk-input" type="email" [(ngModel)]="editForm.email" [ngModelOptions]="{ standalone: true }" placeholder="Email" />
              <div class="grid grid-cols-2 gap-2"><label *ngFor="let role of roles" class="flex items-center gap-2 rounded-xl border border-slate-200 p-2 text-xs"><input type="checkbox" [checked]="editForm.roles.includes(role)" (change)="toggleEditRole(role)" />{{ role }}</label></div>
              <button class="rk-btn rk-btn-primary w-full" (click)="saveEdit()">Save changes</button>
            </div>
          </section>
        </section>

        <section class="rk-card overflow-hidden">
          <div class="grid gap-3 border-b border-slate-200 p-4 md:grid-cols-[1fr_170px_170px]">
            <input
              class="rk-input"
              placeholder="Search by name or email..."
              [(ngModel)]="userSearch"
              [ngModelOptions]="{ standalone: true }"
              aria-label="Search users by name or email"
            />

            <select
              class="rk-select"
              [(ngModel)]="roleFilter"
              [ngModelOptions]="{ standalone: true }"
              aria-label="Filter by role"
            >
              <option value="">All roles</option>
              <option *ngFor="let role of roles" [value]="role">{{ role }}</option>
            </select>

            <select
              class="rk-select"
              [(ngModel)]="statusFilter"
              [ngModelOptions]="{ standalone: true }"
              aria-label="Filter by status"
            >
              <option value="">All statuses</option>
              <option value="ACTIVE">Active</option>
              <option value="DISABLED">Disabled</option>
            </select>
          </div>

          <div *ngIf="!filteredUsers.length" class="px-5 py-12 text-center text-sm text-slate-500">
            No users match your search or filters.
          </div>

          <div class="overflow-x-auto">
            <table class="w-full min-w-[720px] text-left text-sm">
              <thead class="bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
                <tr>
                  <th class="px-5 py-3">User</th>
                  <th class="px-5 py-3">Status</th>
                  <th class="px-5 py-3">Roles</th>
                  <th class="px-5 py-3"></th>
                </tr>
              </thead>
              <tbody class="divide-y divide-slate-100">
                <tr *ngFor="let user of filteredUsers">
                  <td class="px-5 py-4">
                    <p class="font-semibold">{{ user.name }}</p>
                    <p class="mt-1 text-xs text-slate-500">{{ user.email }}</p>
                  </td>
                  <td class="px-5 py-4">{{ user.status }}</td>
                  <td class="px-5 py-4 text-slate-600">{{ user.roles.join(", ") }}</td>
                  <td class="px-5 py-4 text-right">
                    <div class="flex flex-wrap justify-end gap-2">
                      <button class="rk-btn rk-btn-secondary" (click)="edit(user)">Edit</button>
                      <button *ngIf="user.status === 'ACTIVE'" class="rk-btn rk-btn-danger" (click)="disable(user)">Disable</button>
                      <button *ngIf="user.status !== 'ACTIVE'" class="rk-btn rk-btn-secondary" (click)="enable(user)">Enable</button>
                      <button class="rk-btn rk-btn-secondary" (click)="revokeSessions(user)">Revoke sessions</button>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
      </div>
    </div>
  `
})
export class UsersComponent implements OnInit {
  readonly roles = ["ADMIN", "EDITOR", "REVIEWER", "AGENT_VIEWER"];
  users: User[] = [];

  userSearch = "";
  roleFilter = "";
  statusFilter = "";

  copied = false;

  form = {
    name: "",
    email: "",
    password: "",
    roles: [] as string[]
  };

  inviteForm = { name: "", email: "", roles: [] as string[] };
  editing: User | null = null;
  editForm = { name: "", email: "", roles: [] as string[] };

  constructor(private readonly api: ApiService) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.api.users().subscribe((items) => {
      this.users = items;
    });
  }

  get filteredUsers(): User[] {
    const term = this.userSearch.trim().toLowerCase();

    return this.users.filter((user) => {
      const matchesTerm =
        !term ||
        user.name?.toLowerCase().includes(term) ||
        user.email?.toLowerCase().includes(term);

      const matchesRole = !this.roleFilter || user.roles.includes(this.roleFilter);
      const matchesStatus = !this.statusFilter || user.status === this.statusFilter;

      return matchesTerm && matchesRole && matchesStatus;
    });
  }

  get roleSummary(): { allowed: boolean; text: string }[] {
    if (!this.form.roles.length) {
      return [];
    }

    return ROLE_CAPABILITIES.map((capability) => {
      const allowed = capability.grants.some((role) => this.form.roles.includes(role));

      return {
        allowed,
        text: allowed
          ? `This person can ${capability.label}.`
          : `This person cannot ${capability.label}.`
      };
    });
  }

  get passwordStrength(): StrengthLevel {
    const value = this.form.password;

    if (!value) {
      return "none";
    }

    let score = 0;

    if (value.length >= 12) score += 2;
    else if (value.length >= 8) score += 1;

    if (value.length >= 16) score += 1;
    if (value.length >= 20) score += 1;
    if (/[a-z]/.test(value) && /[A-Z]/.test(value)) score += 1;
    if (/\d/.test(value)) score += 1;
    if (/[^A-Za-z0-9]/.test(value)) score += 1;
    if (/^(.)\1+$/.test(value) || /^(123|abc|qwerty|password)/i.test(value)) score -= 3;

    if (score <= 2) return "weak";
    if (score <= 4) return "medium";
    return "strong";
  }

  get strengthPercent(): number {
    switch (this.passwordStrength) {
      case "weak":
        return 33;
      case "medium":
        return 66;
      case "strong":
        return 100;
      default:
        return 0;
    }
  }

  get strengthBarClass(): string {
    switch (this.passwordStrength) {
      case "weak":
        return "bg-rose-500";
      case "medium":
        return "bg-amber-500";
      default:
        return "bg-emerald-500";
    }
  }

  get strengthTextClass(): string {
    switch (this.passwordStrength) {
      case "weak":
        return "text-rose-600";
      case "medium":
        return "text-amber-600";
      default:
        return "text-emerald-600";
    }
  }

  generatePassword(): void {
    const lower = "abcdefghijkmnopqrstuvwxyz";
    const upper = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    const digits = "23456789";
    const symbols = "!@#$%^&*?-_";
    const all = lower + upper + digits + symbols;
    const required = [lower, upper, digits, symbols];

    const pick = (chars: string): string => {
      const index = Math.floor(this.secureRandom() * chars.length);
      return chars[Math.min(index, chars.length - 1)];
    };

    const characters = required.map(pick);

    for (let i = characters.length; i < 20; i++) {
      characters.push(pick(all));
    }

    for (let i = characters.length - 1; i > 0; i--) {
      const j = Math.floor(this.secureRandom() * (i + 1));
      [characters[i], characters[j]] = [characters[j], characters[i]];
    }

    this.form.password = characters.join("");
    this.copied = false;
  }

  copyPassword(): void {
    const value = this.form.password;

    if (!value) {
      return;
    }

    navigator.clipboard
      ?.writeText(value)
      .then(() => {
        this.copied = true;
        setTimeout(() => (this.copied = false), 1800);
      })
      .catch(() => (this.copied = false));
  }

  private secureRandom(): number {
    const buffer = new Uint32Array(1);

    if (typeof crypto !== "undefined" && crypto.getRandomValues) {
      crypto.getRandomValues(buffer);
      return buffer[0] / 4294967296;
    }

    return Math.random();
  }

  toggleRole(role: string): void {
    this.form.roles = this.form.roles.includes(role)
      ? this.form.roles.filter((item) => item !== role)
      : [...this.form.roles, role];
  }

  create(): void {
    this.api.createUser(this.form).subscribe(() => {
      this.form = {
        name: "",
        email: "",
        password: "",
        roles: []
      };
      this.copied = false;
      this.load();
    });
  }

  toggleInviteRole(role: string): void { this.inviteForm.roles = this.inviteForm.roles.includes(role) ? this.inviteForm.roles.filter(x => x !== role) : [...this.inviteForm.roles, role]; }
  invite(): void { this.api.inviteUser({ name: this.inviteForm.name.trim(), email: this.inviteForm.email.trim().toLowerCase(), roles: this.inviteForm.roles }).subscribe({ next: () => { this.inviteForm = { name: "", email: "", roles: [] }; this.load(); }, error: e => alert(e?.error?.message || "Could not send invitation.") }); }
  edit(user: User): void { this.editing = user; this.editForm = { name: user.name, email: user.email, roles: [...user.roles] }; }
  toggleEditRole(role: string): void { this.editForm.roles = this.editForm.roles.includes(role) ? this.editForm.roles.filter(x => x !== role) : [...this.editForm.roles, role]; }
  saveEdit(): void { if (!this.editing) return; this.api.updateUser(this.editing.id, { name: this.editForm.name.trim(), email: this.editForm.email.trim().toLowerCase(), roles: this.editForm.roles }).subscribe({ next: () => { this.editing = null; this.load(); }, error: e => alert(e?.error?.message || "Could not update user.") }); }

  disable(user: User): void {
    if (!confirm(`Disable ${user.email}?`)) {
      return;
    }

    this.api.disableUser(user.id).subscribe(() => this.load());
  }

  enable(user: User): void {
    if (!confirm(`Enable ${user.email}?`)) return;
    this.api.enableUser(user.id).subscribe(() => this.load());
  }

  revokeSessions(user: User): void {
    if (!confirm(`Revoke all active sessions for ${user.email}?`)) return;
    this.api.revokeUserSessions(user.id).subscribe(() => this.load());
  }
}
