import { Component, inject, OnInit, signal, computed } from '@angular/core';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TagModule } from 'primeng/tag';
import { DialogModule } from 'primeng/dialog';
import { SkeletonModule } from 'primeng/skeleton';
import { TooltipModule } from 'primeng/tooltip';
import { MessageService } from 'primeng/api';
import { ToastModule } from 'primeng/toast';
import { PipelineService } from '../../../core/services';
import { Pipeline } from '../../../core/models';

@Component({
  selector: 'app-pipeline-list',
  imports: [
    CommonModule,
    FormsModule,
    TableModule,
    ButtonModule,
    InputTextModule,
    SelectModule,
    TagModule,
    DialogModule,
    SkeletonModule,
    TooltipModule,
    ToastModule
  ],
  providers: [MessageService],
  templateUrl: './pipeline-list.html',
  styleUrl: './pipeline-list.scss',
})
export class PipelineList implements OnInit {
  private readonly router = inject(Router);
  private readonly pipelineService = inject(PipelineService);
  private readonly messageService = inject(MessageService);

  loading = signal(true);
  searchQuery = signal('');
  statusFilter = signal<string | null>(null);
  pipelines = signal<Pipeline[]>([]);
  deleteDialogVisible = signal(false);
  selectedPipeline = signal<Pipeline | null>(null);
  deleting = signal(false);

  statusOptions = [
    { label: 'All', value: null },
    { label: 'Active', value: 'active' },
    { label: 'Draft', value: 'draft' },
    { label: 'Archived', value: 'archived' }
  ];

  filteredPipelines = computed(() => {
    let result = this.pipelines();
    const query = this.searchQuery().toLowerCase();
    if (query) {
      result = result.filter(p =>
        p.name.toLowerCase().includes(query) ||
        p.description.toLowerCase().includes(query) ||
        p.tags.some(t => t.toLowerCase().includes(query))
      );
    }
    const status = this.statusFilter();
    if (status) {
      result = result.filter(p => p.status === status);
    }
    return result;
  });

  ngOnInit(): void {
    this.loadPipelines();
  }

  loadPipelines(): void {
    this.loading.set(true);
    this.pipelineService.list().subscribe({
      next: (data) => {
        this.pipelines.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Error', detail: 'Failed to load pipelines' });
        this.loading.set(false);
      }
    });
  }

  createPipeline(): void {
    this.router.navigate(['/pipelines/new']);
  }

  openPipeline(pipeline: Pipeline): void {
    this.router.navigate(['/pipelines', pipeline.id]);
  }

  runPipeline(pipeline: Pipeline, event: Event): void {
    event.stopPropagation();
    this.pipelineService.run(pipeline.id).subscribe({
      next: () => {
        this.messageService.add({ severity: 'success', summary: 'Started', detail: `Pipeline "${pipeline.name}" started` });
        this.router.navigate(['/jobs']);
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Error', detail: 'Failed to run pipeline' });
      }
    });
  }

  duplicatePipeline(pipeline: Pipeline, event: Event): void {
    event.stopPropagation();
    this.pipelineService.duplicate(pipeline.id).subscribe({
      next: () => {
        this.messageService.add({ severity: 'success', summary: 'Duplicated', detail: `Pipeline "${pipeline.name}" duplicated` });
        this.loadPipelines();
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Error', detail: 'Failed to duplicate pipeline' });
      }
    });
  }

  confirmDelete(pipeline: Pipeline, event: Event): void {
    event.stopPropagation();
    this.selectedPipeline.set(pipeline);
    this.deleteDialogVisible.set(true);
  }

  deletePipeline(): void {
    const pipeline = this.selectedPipeline();
    if (!pipeline) return;

    this.deleting.set(true);
    this.pipelineService.delete(pipeline.id).subscribe({
      next: () => {
        this.messageService.add({ severity: 'success', summary: 'Deleted', detail: `Pipeline "${pipeline.name}" deleted` });
        this.loadPipelines();
        this.deleteDialogVisible.set(false);
        this.deleting.set(false);
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Error', detail: 'Failed to delete pipeline' });
        this.deleting.set(false);
      }
    });
  }

  getStatusSeverity(status: string): 'success' | 'warn' | 'secondary' | 'info' {
    switch (status) {
      case 'active': return 'success';
      case 'draft': return 'warn';
      case 'archived': return 'secondary';
      default: return 'info';
    }
  }

  formatDate(date: Date | undefined): string {
    if (!date) return 'Never';
    return new Date(date).toLocaleDateString('en-US', {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  }
}
