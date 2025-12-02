import { Component, DestroyRef, Signal, computed, effect, inject, input, output, signal } from '@angular/core';

import { MatListModule } from '@angular/material/list';
import { MatFormField } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';

import { ObFormFieldModule, ObIconModule, ObSpinnerModule, ObSpinnerService } from '@oblique/oblique';

import { CubeItem } from '../model/cube-item';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { debounceTime, distinctUntilChanged } from 'rxjs';
import { ListItemComponent } from "../list-item/list-item.component";
import { TranslateModule } from '@ngx-translate/core';
import { FadeInOut } from '../../../core/animation/fade-in-out';

@Component({
  selector: 'cube-item-list',
  standalone: true,
  templateUrl: './item-list.component.html',
  styleUrl: './item-list.component.scss',
  imports: [
    ReactiveFormsModule,
    MatListModule,
    MatFormField,
    MatIconModule,
    MatInputModule,
    ObIconModule,
    ObFormFieldModule,
    ListItemComponent,
    TranslateModule,
    ObSpinnerModule
  ],
  animations: [FadeInOut(300, 200)],

})
export class ItemListComponent {
  items = input.required<CubeItem[]>();
  selected = output<string>();

  isLoading = input<boolean>(false);

  private readonly destroyRef = inject(DestroyRef);
  private readonly spinnerService = inject(ObSpinnerService);

  filterTerm: Signal<string | null | undefined>;

  filteredItems = computed<CubeItem[]>(() => {
    const items = this.items();
    const filterTerm = this.filterTerm();

    if (!filterTerm || filterTerm.length < 3) {
      return items;
    }
    const lowercaseFilterTerm = filterTerm.toLocaleLowerCase().trim();
    return this.items().filter(item => item.searchField.includes(lowercaseFilterTerm));
  });

  searchFormControl = new FormControl<string>('');

  constructor() {
    const observeSearchTermInput = this.searchFormControl.valueChanges.pipe(
      takeUntilDestroyed(this.destroyRef),
      debounceTime(500),
      distinctUntilChanged(),
    );

    this.filterTerm = toSignal(observeSearchTermInput);

    effect(() => {
      const isLoading = this.isLoading();

      if (isLoading) {
        this.spinnerService.activate('cube_list');
      } else {
        this.spinnerService.deactivate('cube_list');
      }
    });
  }

  selectItem(iri: string) {
    this.selected.emit(iri);
  }
}
