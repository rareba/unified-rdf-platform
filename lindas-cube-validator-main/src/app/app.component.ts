import { Component, inject } from '@angular/core';
import { ObMasterLayoutConfig } from '@oblique/oblique';

@Component({
  selector: 'cube-root',
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent {
  title = 'cube-validator';
  private readonly obMasterLayoutConfig = inject(ObMasterLayoutConfig);
  constructor() {
    this.obMasterLayoutConfig.locale.locales = ['de-CH', 'fr-CH', 'it-CH', 'en-GB']; //  'en-GB',
    this.obMasterLayoutConfig.homePageRoute = '/';

  }


}
