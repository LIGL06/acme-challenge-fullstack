import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet],
  template: `
    <header class="app-header">
      <div class="container">
        <h1>Peek Calendar</h1>
      </div>
    </header>
    <main class="app-main">
      <router-outlet></router-outlet>
    </main>
  `,
  styles: [`
    .app-header {
      background: #3b82f6;
      color: white;
      padding: 1rem 0;
      
      h1 {
        font-size: 1.5rem;
        font-weight: 600;
      }
    }
    
    .app-main {
      padding: 2rem 0;
    }
  `]
})
export class AppComponent {
  title = 'Peek Challenge';
}

