import { Component, DestroyRef, Signal, computed, inject, output, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators, } from '@angular/forms';

import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';


import { ObDatePipe, ObFormFieldModule, ObIconModule, ObLanguageService } from '@oblique/oblique';
import { EndpointService } from '../../../core/service/endpoint/endpoint.service';
import { catchError, map, switchMap } from 'rxjs/operators';
import { of } from 'rxjs';
import { MatIconModule } from '@angular/material/icon';
import { MultiLanguageCubeItem } from '../../../core/service/endpoint/model/cube-item';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { CubeItem } from '../model/cube-item';
import { ItemListComponent } from "../item-list/item-list.component";
import { CubeInfo } from '../cube-selector/cube-selector.component';

const DEFAULT_ENDPOINT = 'https://lindas.admin.ch/query';

@Component({
  selector: 'cube-validator-input',
  standalone: true,
  templateUrl: './validator-input.component.html',
  styleUrl: './validator-input.component.scss',
  imports: [
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatIconModule,
    ObIconModule,
    ObFormFieldModule,
    ItemListComponent
  ]
})
export class ValidatorInputComponent {
  selected = output<CubeInfo>();

  private readonly destroyRef = inject(DestroyRef);
  private readonly endpointService = inject(EndpointService);
  private readonly languageService = inject(ObLanguageService);

  isLoading = signal<boolean>(true);

  multiLanguageCubes = signal<MultiLanguageCubeItem[]>([]);
  language: Signal<string | undefined>;
  areCubesLoading = signal<boolean>(false);
  isEndpointOnline = signal<boolean>(false);
  endpointFormControl = new FormControl<string>(this.endpointService.lastUsedEndpoint ? this.endpointService.lastUsedEndpoint : DEFAULT_ENDPOINT, [Validators.required, urlValidator])

  cubes = computed<CubeItem[]>(() => {
    const lang = this.language() ?? 'de';
    const cubes = this.multiLanguageCubes();
    const datePipe = new ObDatePipe(this.languageService);


    // create a language specific cube items
    const items = cubes.map(cube => {
      if (lang === 'de') {
        return {
          name: cube.nameDE ?? cube.name ?? cube.nameEN ?? cube.nameFR ?? cube.nameIT ?? 'kein Name',
          iri: cube.iri,
          description: cube.descriptionDE ?? cube.description ?? cube.descriptionEN ?? cube.descriptionFR ?? cube.descriptionIT ?? 'Keine Beschreibung',
          datePublished: cube.datePublished ? datePipe.transform(cube.datePublished).split(' ')[0] : 'Kein Datum',
          searchField: this.#createSearchField(cube)
        }
      }
      if (lang === 'fr') {
        return {
          name: cube.nameFR ?? cube.name ?? cube.nameEN ?? cube.nameDE ?? cube.nameIT ?? 'Pas de nom',
          iri: cube.iri,
          description: cube.descriptionFR ?? cube.description ?? cube.descriptionEN ?? cube.descriptionDE ?? cube.descriptionIT ?? 'Pas de description',
          datePublished: cube.datePublished ? datePipe.transform(cube.datePublished).split(' ')[0] : 'Pas de date',
          searchField: this.#createSearchField(cube)
        }
      }
      if (lang === 'it') {
        return {
          name: cube.nameIT ?? cube.name ?? cube.nameEN ?? cube.nameDE ?? cube.nameFR ?? 'Nessun nome',
          iri: cube.iri,
          description: cube.descriptionIT ?? cube.description ?? cube.descriptionEN ?? cube.descriptionDE ?? cube.descriptionFR ?? 'Nessuna descrizione',
          datePublished: cube.datePublished ? datePipe.transform(cube.datePublished).split(' ')[0] : 'Nessuna data',
          searchField: this.#createSearchField(cube)
        }
      }
      // default to english
      return {
        name: cube.nameEN ?? cube.name ?? cube.nameDE ?? cube.nameFR ?? cube.nameIT ?? 'No name',
        iri: cube.iri,
        description: cube.descriptionEN ?? cube.description ?? cube.descriptionDE ?? cube.descriptionFR ?? cube.descriptionFR ?? 'No description',
        datePublished: cube.datePublished ? datePipe.transform(cube.datePublished).split(' ')[0] : 'No date',
        searchField: this.#createSearchField(cube)
      }
    });

    // sort by name 
    items.sort((a, b) => a.name.localeCompare(b.name));

    return items;

  });



  constructor() {
    this.language = toSignal<string>(this.languageService.locale$.pipe(
      takeUntilDestroyed(this.destroyRef),
      map(locale => locale.split('-')[0] ?? 'de')
    )
    );

    this.endpointService.isOnline(DEFAULT_ENDPOINT).subscribe(
      {
        next: isOnline => this.isEndpointOnline.set(isOnline),
        error: () => this.isEndpointOnline.set(false)
      }
    );

    this.loadCubes();

  }

  loadCubes(): void {
    if (this.endpointFormControl.invalid || this.endpointFormControl.value === null) {
      console.log('invalid endpoint', this.endpointFormControl.invalid, this.endpointFormControl.value);
      return;
    }

    this.isLoading.set(true);
    const endpoint = this.endpointFormControl.value;
    this.isEndpointOnline.set(true);
    this.endpointService.isOnline(endpoint).pipe(
      catchError(() => {
        console.log('error');
        this.isEndpointOnline.set(false);
        return of(false)
      }),
      switchMap(isOnline => isOnline ? this.endpointService.getCubes(endpoint) : of([]))
    ).subscribe({
      next: cubes => {
        this.areCubesLoading.set(false);
        this.multiLanguageCubes.set(cubes);
        this.isLoading.set(false);
      },
      error: (err) => {
        console.error(err);
        this.isEndpointOnline.set(false);
        this.multiLanguageCubes.set([]);
        this.isLoading.set(false);
      }
    });
  }

  emitSelected(cubeIri: string): void {
    this.selected.emit({ cubeIri, endpoint: this.endpointFormControl.value ?? '' });
  }

  #createSearchField(cube: MultiLanguageCubeItem): string {
    return [
      cube.iri, cube.name ?? '', 
      cube.nameDE ?? '', 
      cube.nameEN ?? '', 
      cube.nameFR ?? '', 
      cube.nameIT ?? '', 
      cube.iri, 
      cube.description ?? '', 
      cube.descriptionDE ?? '',
      cube.descriptionEN  ?? '',
      cube.descriptionFR ?? '',
      cube.descriptionIT ?? '',
      cube.datePublished].join(' ').toLocaleLowerCase()
  }


}


function urlValidator(control: FormControl): { [key: string]: any } | null {
  const urlPattern = /^(https?|http):\/\/[^\s/$.?#].[^\s]*$/;
  const isValid = urlPattern.test(control.value);
  return isValid ? null : { invalidUrl: true };
}
