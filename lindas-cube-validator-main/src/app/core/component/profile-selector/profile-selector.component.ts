import { Component, computed, effect, input, output, signal } from '@angular/core';
import { AbstractControl, FormControl, ValidationErrors, ReactiveFormsModule, FormGroup } from '@angular/forms';


import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectChange, MatSelectModule } from '@angular/material/select';
import { ObButtonModule, ObFormFieldModule, ObIconModule } from '@oblique/oblique';
import { ValidationProfile } from '../../constant/validation-profile';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { TranslateModule } from '@ngx-translate/core';


@Component({
  selector: 'cube-profile-selector',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatIconModule,
    MatButtonModule,
    ObIconModule,
    ObButtonModule,
    ObFormFieldModule,
    TranslateModule,
  ],
  templateUrl: './profile-selector.component.html',
  styleUrl: './profile-selector.component.scss'
})
export class ProfileSelectorComponent {
  profiles = input.required<ValidationProfile[]>();
  selectedProfile = input.required<ValidationProfile | undefined | null>();
  profileSelected = output<ValidationProfile>();

  private isInitialProfileChange = true;

  lastEmittedProfile: ValidationProfile | undefined = undefined;


  showList = signal<boolean>(true);


  // the url 
  manualForm = new FormGroup({
    manualProfileUrl: new FormControl('', urlValidator)
  });


  constructor() {
    effect(() => {
      const profile = this.selectedProfile();
      if (profile) {
        this.manualForm.get('manualProfileUrl')?.setValue(profile.value);
      }
    }
    );

    effect(() => {
      const profile = this.selectedProfile();
      if (profile && profile.key === 'manual') {
        this.showList.set(false);
      } else {
        this.showList.set(true);
      }
    }, { allowSignalWrites: true });
  }
  selectionChanged(event: MatSelectChange): void {
    // find the profile by value
    const profile = this.profiles().find(p => p.value === event.value);
    if (profile) {
      this.lastEmittedProfile = profile;
      this.profileSelected.emit(profile);
      return;
    }
  }

  toggleShowList(): void {
    this.showList.set(!this.showList());
  }

  manualProfileUrlChange(): void {
    if (this.manualForm.invalid || !this.manualForm.touched) {
      console.log('Form is invalid or not touched');
      return;
    }
    const value = this.manualForm.get('manualProfileUrl')?.value;
    if (!value) {
      return;
    }
    const manualProfile: ValidationProfile = {
      label: value,
      value: value,
      key: 'manual',
      workNodeIri: '',
    };
    this.lastEmittedProfile = manualProfile;
    this.profileSelected.emit(manualProfile);
  }
}

export function urlValidator(control: AbstractControl): ValidationErrors | null {
  const url = control.value;
  if (url && !url.match(/^https?:\/\/[^\s]+$/)) {
    return { invalidUrl: true };
  }
  return null;
}
