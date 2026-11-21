import { Component } from "@angular/core";
import { RouterOutlet } from "@angular/router";
import { ThemeService } from "./core/theme.service";
import { FeedbackFabComponent } from "./shared/feedback-fab.component";

@Component({
  selector: "app-root",
  standalone: true,
  imports: [RouterOutlet, FeedbackFabComponent],
  template: `
    <router-outlet />
    <rk-feedback-fab />
  `
})
export class AppComponent {
  constructor(_theme: ThemeService) {}
}

