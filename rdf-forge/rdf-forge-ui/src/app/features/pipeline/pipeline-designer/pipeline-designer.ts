import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DragDropModule, CdkDragDrop, moveItemInArray } from '@angular/cdk/drag-drop';
import { CardModule } from 'primeng/card';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { SelectModule } from 'primeng/select';
import { ButtonModule } from 'primeng/button';
import { ToastModule } from 'primeng/toast';
import { ToggleButtonModule } from 'primeng/togglebutton';
import { DialogModule } from 'primeng/dialog';
import { PanelModule } from 'primeng/panel';
import { MessageService } from 'primeng/api';
import { PipelineService } from '../../../core/services';
import { PipelineCreateRequest, Operation } from '../../../core/models';

interface PipelineStep {
  id: string;
  operationId: string;
  operationName: string;
  params: Record<string, unknown>;
}

@Component({
  selector: 'app-pipeline-designer',
  imports: [
    CommonModule,
    FormsModule,
    DragDropModule,
    CardModule,
    InputTextModule,
    TextareaModule,
    SelectModule,
    ButtonModule,
    ToastModule,
    ToggleButtonModule,
    DialogModule,
    PanelModule
  ],
  providers: [MessageService],
  templateUrl: './pipeline-designer.html',
  styleUrl: './pipeline-designer.scss',
})
export class PipelineDesigner implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly pipelineService = inject(PipelineService);
  private readonly messageService = inject(MessageService);

  loading = signal(false);
  saving = signal(false);
  isNew = signal(true);
  pipelineId = signal<string | null>(null);
  
  // Basic Info
  name = signal('');
  description = signal('');
  tags = signal<string[]>([]);
  
  // Editor Mode
  visualMode = signal(true);
  definition = signal('');
  definitionFormat = signal<'yaml' | 'turtle'>('yaml');
  
  // Visual Editor Data
  availableOperations = signal<Operation[]>([]);
  pipelineSteps = signal<PipelineStep[]>([]);
  selectedStep = signal<PipelineStep | null>(null);
  configDialogVisible = signal(false);

  formatOptions = [
    { label: 'YAML', value: 'yaml' },
    { label: 'Turtle (RDF)', value: 'turtle' }
  ];

  ngOnInit(): void {
    this.loadOperations();
    
    const id = this.route.snapshot.paramMap.get('id');
    if (id && id !== 'new') {
      this.isNew.set(false);
      this.pipelineId.set(id);
      this.loadPipeline(id);
    }
  }

  loadOperations(): void {
    this.pipelineService.getOperations().subscribe({
      next: (ops) => this.availableOperations.set(ops),
      error: () => console.error('Failed to load operations')
    });
  }

  loadPipeline(id: string): void {
    this.loading.set(true);
    this.pipelineService.get(id).subscribe({
      next: (pipeline) => {
        this.name.set(pipeline.name);
        this.description.set(pipeline.description);
        this.definition.set(pipeline.definition);
        this.definitionFormat.set(pipeline.definitionFormat);
        this.tags.set(pipeline.tags);
        
        // Try to parse definition to visual steps if simple YAML
        // This is a simplification; real parsing of Barnard59 is complex
        this.loading.set(false);
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Error', detail: 'Failed to load pipeline' });
        this.loading.set(false);
      }
    });
  }

  save(): void {
    // If in visual mode, generate definition
    if (this.visualMode()) {
      this.generateDefinition();
    }

    const data: PipelineCreateRequest = {
      name: this.name(),
      description: this.description(),
      definition: this.definition(),
      definitionFormat: this.definitionFormat(),
      tags: this.tags()
    };

    this.saving.set(true);
    const request = this.isNew()
      ? this.pipelineService.create(data)
      : this.pipelineService.update(this.pipelineId()!, data);

    request.subscribe({
      next: () => {
        this.messageService.add({ severity: 'success', summary: 'Saved', detail: 'Pipeline saved successfully' });
        this.saving.set(false);
        this.router.navigate(['/pipelines']);
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Error', detail: 'Failed to save pipeline' });
        this.saving.set(false);
      }
    });
  }

  validate(): void {
    if (this.visualMode()) {
      this.generateDefinition();
    }
    
    this.pipelineService.validate(this.definition(), this.definitionFormat()).subscribe({
      next: (result) => {
        if (result.valid) {
          this.messageService.add({ severity: 'success', summary: 'Valid', detail: 'Pipeline definition is valid' });
        } else {
          this.messageService.add({ severity: 'error', summary: 'Invalid', detail: result.errors.map(e => e.message).join(', ') });
        }
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Error', detail: 'Validation failed' });
      }
    });
  }

  cancel(): void {
    this.router.navigate(['/pipelines']);
  }

  // Drag and Drop Logic
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  drop(event: CdkDragDrop<any[]>): void {
    if (event.previousContainer === event.container) {
      // Reordering
      const steps = [...this.pipelineSteps()];
      moveItemInArray(steps, event.previousIndex, event.currentIndex);
      this.pipelineSteps.set(steps);
    } else {
      // Adding from available operations
      const operation = event.previousContainer.data[event.previousIndex] as Operation;
      const newStep: PipelineStep = {
        id: crypto.randomUUID(),
        operationId: operation.id,
        operationName: operation.name,
        params: {}
      };
      
      const steps = [...this.pipelineSteps()];
      steps.splice(event.currentIndex, 0, newStep);
      this.pipelineSteps.set(steps);
    }
  }

  removeStep(index: number): void {
    const steps = [...this.pipelineSteps()];
    steps.splice(index, 1);
    this.pipelineSteps.set(steps);
  }

  configureStep(step: PipelineStep): void {
    this.selectedStep.set({ ...step }); // Copy to avoid direct mutation
    this.configDialogVisible.set(true);
  }

  saveStepConfig(): void {
    const updatedStep = this.selectedStep();
    if (updatedStep) {
      const steps = this.pipelineSteps().map(s => s.id === updatedStep.id ? updatedStep : s);
      this.pipelineSteps.set(steps);
    }
    this.configDialogVisible.set(false);
  }

  getOperationById(id: string): Operation | undefined {
    return this.availableOperations().find(op => op.id === id);
  }

  // Prevent items from being dropped back into the operations palette
  noEnter = () => false;

  generateDefinition(): void {
    // Simple YAML generation from steps
    // This is a placeholder for actual Barnard59 logic
    const steps = this.pipelineSteps().map(step => {
      return {
        operation: step.operationId,
        params: step.params
      };
    });
    
    this.definition.set(JSON.stringify({ steps }, null, 2)); // Using JSON as simple "YAML" for now or need a yaml dumper
    // Ideally we use a library like 'js-yaml' but I don't want to add dependencies blindly
    // For now, I'll default to 'yaml' format but output JSON which is valid YAML 1.2
    this.definitionFormat.set('yaml');
  }
}
