import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CardModule } from 'primeng/card';
import { StepperModule } from 'primeng/stepper';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { SelectModule } from 'primeng/select';
import { ListboxModule } from 'primeng/listbox';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { DataService, DimensionService, PipelineService } from '../../../core/services';
import { DataSource, Dimension, ColumnInfo } from '../../../core/models';

@Component({
  selector: 'app-cube-wizard',
  imports: [
    CommonModule,
    FormsModule,
    CardModule,
    StepperModule,
    ButtonModule,
    InputTextModule,
    TextareaModule,
    SelectModule,
    ListboxModule,
    ToastModule
  ],
  providers: [MessageService],
  templateUrl: './cube-wizard.html',
  styleUrl: './cube-wizard.scss',
})
export class CubeWizard implements OnInit {
  private readonly dataService = inject(DataService);
  private readonly dimensionService = inject(DimensionService);
  private readonly pipelineService = inject(PipelineService);
  private readonly messageService = inject(MessageService);

  activeStep = signal(0);
  
  // Step 1: Basic Info
  cubeName = signal('');
  cubeDescription = signal('');
  baseUri = signal('https://example.org/cube/');
  
  // Step 2: Data Source
  dataSources = signal<DataSource[]>([]);
  selectedDataSource = signal<DataSource | null>(null);
  sourceColumns = signal<ColumnInfo[]>([]);
  
  // Step 3: Dimensions
  availableDimensions = signal<Dimension[]>([]);
  selectedDimensions = signal<Dimension[]>([]);
  dimensionMappings = signal<Record<string, string>>({});
  
  // Step 4: Review
  creating = signal(false);

  ngOnInit(): void {
    this.loadDataSources();
    this.loadDimensions();
  }

  loadDataSources(): void {
    this.dataService.list().subscribe(data => this.dataSources.set(data));
  }

  loadDimensions(): void {
    this.dimensionService.list().subscribe(data => this.availableDimensions.set(data));
  }

  onDataSourceSelect(dataSource: DataSource): void {
    this.selectedDataSource.set(dataSource);
    this.dataService.analyze(dataSource.id).subscribe(result => {
      this.sourceColumns.set(result.columns);
    });
  }

  updateDimensionMapping(dimensionId: string, columnName: string): void {
    const mappings = { ...this.dimensionMappings() };
    mappings[dimensionId] = columnName;
    this.dimensionMappings.set(mappings);
  }

  nextStep(): void {
    this.activeStep.update(v => v + 1);
  }

  prevStep(): void {
    this.activeStep.update(v => v - 1);
  }

  createCube(): void {
    this.creating.set(true);
    
    const pipelineDef = {
      steps: [
        { operation: 'op:load', params: { source: this.selectedDataSource()?.id } },
        { operation: 'op:cube', params: { 
            dimensions: this.selectedDimensions()
              .filter(d => d.id)
              .map(d => ({
                id: d.id,
                column: d.id ? this.dimensionMappings()[d.id] : undefined
              }))
          } 
        }
      ]
    };

    this.pipelineService.create({
      name: `Cube Pipeline: ${this.cubeName()}`,
      description: `Pipeline to generate cube ${this.cubeName()}`,
      definition: JSON.stringify(pipelineDef, null, 2),
      definitionFormat: 'yaml',
      tags: ['cube-generation']
    }).subscribe({
      next: () => {
        this.messageService.add({ severity: 'success', summary: 'Success', detail: 'Cube generation pipeline created' });
        this.creating.set(false);
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Error', detail: 'Failed to create cube pipeline' });
        this.creating.set(false);
      }
    });
  }
}
