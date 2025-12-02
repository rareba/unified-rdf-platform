import { Routes } from "@angular/router";
import { ValidatorComponent } from "./validator/validator.component";

export const routes: Routes = [
    {
        path: ':endpoint/:cubeIri',
        component: ValidatorComponent,
    }
];