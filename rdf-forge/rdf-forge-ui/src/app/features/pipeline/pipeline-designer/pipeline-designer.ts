import { Component, inject, OnInit, OnDestroy, AfterViewInit, signal, computed, ViewChild, ElementRef, ChangeDetectionStrategy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { CommonModule, KeyValuePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialogModule } from '@angular/material/dialog';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDividerModule } from '@angular/material/divider';
import { MatTabsModule } from '@angular/material/tabs';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
// ngx-graph removed until @swimlane/ngx-graph ships Angular 21 support.
// The designer now renders the DAG as a vertical step list with dependency
// badges. See docs/PIPELINE_DESIGNER_MIGRATION.md for the migration plan.
import { Subject, Observable } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { PipelineService } from '../../../core/services';
import { ConfirmationService } from '../../../core/services/confirmation.service';
import { LoggerService } from '../../../core/services/logger.service';
import {
  Pipeline,
  PipelineCreateRequest,
  Operation,
  OperationType,
  OperationParameter,
  PipelineDefinition
} from '../../../core/models';
// dagre import removed - ngx-graph handles layout internally via [layout]="'dagre'"

interface OperationGroup {
  type: OperationType;
  label: string;
  icon: string;
  operations: Operation[];
}

interface PipelineTemplate {
  id: string;
  name: string;
  description: string;
  icon: string;
  category: 'cube' | 'validation' | 'etl' | 'publish';
  steps: { operation: string; params: Record<string, unknown> }[];
}

// Graph node structure for ngx-graph
interface GraphNode {
  id: string;
  label?: string;
  operationId: string;
  operationName: string;
  operationType: OperationType;
  params: Record<string, unknown>;
  dimension?: { width: number; height: number };
  x?: number;
  y?: number;
}

// Graph link structure for ngx-graph
interface GraphLink {
  id: string;
  source: string;
  target: string;
  label?: string;
}

@Component({
  selector: 'app-pipeline-designer',
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatCheckboxModule,
    MatDialogModule,
    MatExpansionModule,
    MatTooltipModule,
    MatDividerModule,
    MatTabsModule,
    MatIconModule,
    MatFormFieldModule,
    MatMenuModule,
    MatProgressSpinnerModule,
    MatToolbarModule,
    MatSidenavModule,
    MatListModule,
    KeyValuePipe
  ],
  templateUrl: './pipeline-designer.html',
  styleUrl: './pipeline-designer.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class PipelineDesigner implements OnInit, OnDestroy, AfterViewInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly pipelineService = inject(PipelineService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly confirmationService = inject(ConfirmationService);
  private readonly logger = inject(LoggerService);
  private readonly destroy$ = new Subject<void>();

  // @ViewChild('graph') graphComponent!: GraphComponent;
  //   ngx-graph removed — see comment at top of imports block.
  @ViewChild('graphContainer', { static: false }) graphContainer!: ElementRef<HTMLDivElement>;

  // State
  loading = signal(false);
  saving = signal(false);
  isNew = signal(true);
  pipelineId = signal<string | null>(null);

  // Pipeline Info
  name = signal('New Pipeline');
  description = signal('');
  tags = signal<string[]>([]);

  // Operations
  availableOperations = signal<Operation[]>([]);
  operationGroups = computed<OperationGroup[]>(() => {
    const ops = this.availableOperations();
    const groups: OperationGroup[] = [
      { type: 'SOURCE', label: 'Data Sources', icon: 'download', operations: [] },
      { type: 'TRANSFORM', label: 'Transformations', icon: 'sync', operations: [] },
      { type: 'CUBE', label: 'Cube Operations', icon: 'grid_view', operations: [] },
      { type: 'VALIDATION', label: 'Validation', icon: 'check_circle', operations: [] },
      { type: 'OUTPUT', label: 'Outputs', icon: 'upload', operations: [] }
    ];
    ops.forEach(op => {
      const group = groups.find(g => g.type === op.type);
      if (group) group.operations.push(op);
    });
    return groups.filter(g => g.operations.length > 0);
  });

  // Search and filter
  operationSearch = signal('');
  filteredOperationGroups = computed(() => {
    const search = this.operationSearch().toLowerCase();
    const groups = this.operationGroups();
    if (!search) return groups;
    return groups.map(g => ({
      ...g,
      operations: g.operations.filter(op =>
        op.name.toLowerCase().includes(search) ||
        op.description.toLowerCase().includes(search) ||
        op.id.toLowerCase().includes(search)
      )
    })).filter(g => g.operations.length > 0);
  });

  // Graph Data for ngx-graph
  nodes = signal<GraphNode[]>([]);
  links = signal<GraphLink[]>([]);
  selectedNode = signal<GraphNode | null>(null);
  selectedOperation = signal<Operation | null>(null);

  // Graph configuration - dynamically sized in ngAfterViewInit
  graphWidth = 1200;
  graphHeight = 800;
  panningEnabled = true;
  zoomLevel = signal(1);

  // Legacy graph observables — kept as no-op subjects so any lingering
  // code paths that `.next()` through them don't blow up.
  center$: Subject<boolean> = new Subject();
  zoomToFit$: Subject<unknown> = new Subject<unknown>();
  update$: Subject<boolean> = new Subject();

  // Dialogs
  runDialogVisible = signal(false);
  jsonDialogVisible = signal(false);
  templatesDialogVisible = signal(false);
  propertiesPanelOpen = signal(false);

  // Drag state
  private draggedOp: Operation | null = null;

  // Run variables
  runVariables = signal<Record<string, string>>({});
  newVarKey = signal('');
  newVarValue = signal('');

  // Pipeline templates for common cube workflows
  pipelineTemplates: PipelineTemplate[] = [
    {
      id: 'csv-to-cube',
      name: 'CSV to RDF Cube',
      description: 'Transform CSV data into a complete RDF Data Cube with validation',
      icon: 'table_chart',
      category: 'cube',
      steps: [
        { operation: 'load-csv', params: { hasHeader: true } },
        { operation: 'create-observation', params: { cubeUri: 'https://example.org/cube/my-cube' } },
        { operation: 'build-cube-shape', params: {} },
        { operation: 'validate-shacl', params: { onViolation: 'WARN' } },
        { operation: 'graph-store-put', params: { graph: 'https://example.org/graph/my-cube' } }
      ]
    },
    {
      id: 'validate-cube-link',
      name: 'Validate Against cube-link',
      description: 'Fetch and validate an existing cube against cube-link profiles',
      icon: 'verified',
      category: 'validation',
      steps: [
        { operation: 'fetch-cube', params: { endpoint: '' } },
        { operation: 'validate-shacl', params: { onViolation: 'FAIL' } }
      ]
    },
    {
      id: 'cube-to-graphdb',
      name: 'Publish Cube to GraphDB',
      description: 'Load RDF cube data and publish to GraphDB triplestore',
      icon: 'cloud_upload',
      category: 'publish',
      steps: [
        { operation: 'fetch-cube', params: {} },
        { operation: 'graph-store-put', params: { endpoint: '' } }
      ]
    },
    {
      id: 'full-etl',
      name: 'Full ETL Pipeline',
      description: 'Complete ETL: Load CSV, create observations, build shape, validate, publish',
      icon: 'sync_alt',
      category: 'etl',
      steps: [
        { operation: 'load-csv', params: { hasHeader: true } },
        { operation: 'map-to-rdf', params: { baseUri: 'https://example.org/' } },
        { operation: 'create-observation', params: {} },
        { operation: 'build-cube-shape', params: {} },
        { operation: 'validate-shacl', params: {} },
        { operation: 'graph-store-put', params: {} }
      ]
    },
    {
      id: 'fetch-validate',
      name: 'Fetch and Validate',
      description: 'Fetch cube from SPARQL endpoint and run SHACL validation',
      icon: 'fact_check',
      category: 'validation',
      steps: [
        { operation: 'fetch-metadata', params: {} },
        { operation: 'fetch-observations', params: {} },
        { operation: 'validate-shacl', params: {} }
      ]
    }
  ];

  // JSON view
  pipelineJson = computed(() => {
    const definition: PipelineDefinition = {
      steps: this.nodes().map(node => ({
        id: node.id,
        operation: node.operationId,
        params: node.params
      }))
    };
    return JSON.stringify(definition, null, 2);
  });

  // Validation
  validationErrors = computed(() => {
    const errors: string[] = [];
    const nodeList = this.nodes();
    const ops = this.availableOperations();

    if (nodeList.length === 0) {
      errors.push('Pipeline has no steps');
      return errors;
    }

    const hasSource = nodeList.some(n => n.operationType === 'SOURCE');
    if (!hasSource) {
      errors.push('Pipeline should start with a source operation');
    }

    const hasOutput = nodeList.some(n => n.operationType === 'OUTPUT');
    if (!hasOutput) {
      errors.push('Pipeline should have at least one output operation');
    }

    // Check for disconnected nodes
    const connectedNodeIds = new Set<string>();
    this.links().forEach(link => {
      connectedNodeIds.add(link.source);
      connectedNodeIds.add(link.target);
    });
    
    nodeList.forEach(node => {
      // Check if node has connections (except for single node pipelines)
      if (nodeList.length > 1 && !connectedNodeIds.has(node.id)) {
        errors.push(`${node.operationName}: Node is not connected to the pipeline`);
      }

      // Check required parameters
      const op = ops.find(o => o.id === node.operationId);
      if (op) {
        Object.entries(op.parameters).forEach(([key, param]) => {
          if (param.required) {
            const value = node.params[key];
            if (value === undefined || value === null || value === '') {
              errors.push(`${node.operationName}: Missing required parameter "${param.name}"`);
            }
          }
        });
      }
    });

    return errors;
  });

  isValid = computed(() => this.validationErrors().length === 0);

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id && id !== 'new') {
      this.isNew.set(false);
      this.pipelineId.set(id);
      this.loadOperationsAndPipeline(id);
    } else {
      this.loadOperations();
    }
  }

  ngAfterViewInit(): void {
    // Size the graph to the actual container dimensions
    if (this.graphContainer) {
      const rect = this.graphContainer.nativeElement.getBoundingClientRect();
      if (rect.width > 0 && rect.height > 0) {
        this.graphWidth = rect.width;
        this.graphHeight = rect.height;
      }
    }
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    this.center$.complete();
    this.zoomToFit$.complete();
    this.update$.complete();
  }

  loadOperations(): void {
    this.pipelineService.getOperations().pipe(takeUntil(this.destroy$)).subscribe({
      next: (ops) => this.availableOperations.set(ops),
      error: () => this.snackBar.open('Failed to load operations', 'Close', { duration: 3000 })
    });
  }

  loadOperationsAndPipeline(id: string): void {
    this.loading.set(true);
    this.pipelineService.getOperations().pipe(takeUntil(this.destroy$)).subscribe({
      next: (ops) => {
        this.availableOperations.set(ops);
        this.loadPipeline(id);
      },
      error: () => {
        this.snackBar.open('Failed to load operations', 'Close', { duration: 3000 });
        this.loading.set(false);
      }
    });
  }

  loadPipeline(id: string): void {
    this.loading.set(true);
    this.pipelineService.get(id).pipe(takeUntil(this.destroy$)).subscribe({
      next: (pipeline) => {
        this.name.set(pipeline.name);
        this.description.set(pipeline.description || '');
        this.tags.set(pipeline.tags || []);
        this.parsePipelineDefinition(pipeline.definition);
        this.loading.set(false);
      },
      error: () => {
        this.snackBar.open('Failed to load pipeline', 'Close', { duration: 3000 });
        this.loading.set(false);
      }
    });
  }

  parsePipelineDefinition(definition: string): void {
    try {
      const parsed = JSON.parse(definition);
      const steps = parsed.steps || [];

      const loadedNodes: GraphNode[] = steps.map((step: any, index: number) => {
        const op = this.getOperationById(step.operation);
        const opName = op?.name || step.operation;
        const node: GraphNode = {
          id: step.id || `step-${index}`,
          label: opName,
          operationId: step.operation,
          operationName: opName,
          operationType: op?.type || 'TRANSFORM',
          params: step.params || step.parameters || {},
          dimension: { width: 200, height: 100 }
        };
        return node;
      });

      this.nodes.set(loadedNodes);

      // Create sequential edges if no explicit connections
      const loadedLinks: GraphLink[] = [];
      for (let i = 1; i < loadedNodes.length; i++) {
        loadedLinks.push({
          id: `edge-${i}`,
          source: loadedNodes[i - 1].id,
          target: loadedNodes[i].id
        });
      }
      this.links.set(loadedLinks);

      // Auto layout after loading
      setTimeout(() => this.autoLayout(), 100);
    } catch (e) {
      this.logger.warn('Failed to parse pipeline definition', e);
    }
  }

  getOperationById(id: string): Operation | undefined {
    const normalizedId = id.startsWith('op:') ? id.substring(3) : id;
    return this.availableOperations().find(o => o.id === normalizedId || o.id === id);
  }

  // Drag operation from palette
  onDragStart(event: DragEvent, op: Operation): void {
    this.draggedOp = op;
    event.dataTransfer?.setData('application/json', JSON.stringify(op));
    event.dataTransfer!.effectAllowed = 'copy';
  }

  onDragEnd(): void {
    this.draggedOp = null;
  }

  // Handle drop on graph
  onGraphDrop(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();

    let op: Operation | null = this.draggedOp;

    if (!op) {
      const data = event.dataTransfer?.getData('application/json');
      if (data) {
        try {
          op = JSON.parse(data);
        } catch (e) {
          this.logger.warn('Failed to parse dropped operation data:', e);
          return;
        }
      }
    }

    if (!op) return;

    // Get drop position relative to graph container
    if (this.graphContainer) {
      const rect = this.graphContainer.nativeElement.getBoundingClientRect();
      const x = (event.clientX - rect.left) / this.zoomLevel();
      const y = (event.clientY - rect.top) / this.zoomLevel();
      this.addNode(op, x, y);
    } else {
      // Fallback: add at center
      this.addNode(op, 400, 300);
    }

    this.draggedOp = null;
  }

  addNode(op: Operation, x: number, y: number): void {
    const newNode: GraphNode = {
      id: `node-${Date.now()}`,
      label: op.name,
      operationId: op.id,
      operationName: op.name,
      operationType: op.type,
      params: this.getDefaultParams(op),
      dimension: { width: 200, height: 100 },
      x,
      y
    };

    this.nodes.update(n => [...n, newNode]);

    // Auto-connect to last node if exists
    const nodeList = this.nodes();
    if (nodeList.length > 1) {
      const lastNode = nodeList[nodeList.length - 2];
      this.addLink(lastNode.id, newNode.id);
    }

    this.update$.next(true);
  }

  getDefaultParams(op: Operation): Record<string, unknown> {
    const params: Record<string, unknown> = {};
    if (op.parameters) {
      Object.entries(op.parameters).forEach(([key, param]) => {
        if (param.defaultValue !== null && param.defaultValue !== undefined) {
          params[key] = param.defaultValue;
        }
      });
    }
    return params;
  }

  addLink(sourceId: string, targetId: string): void {
    // Prevent duplicate links
    const exists = this.links().some(l => l.source === sourceId && l.target === targetId);
    if (exists) return;

    // Prevent self-loops
    if (sourceId === targetId) return;

    const newLink: GraphLink = {
      id: `link-${Date.now()}`,
      source: sourceId,
      target: targetId
    };

    this.links.update(l => [...l, newLink]);
    this.update$.next(true);
  }

  removeNode(id: string): void {
    const node = this.nodes().find(n => n.id === id);
    if (!node) return;

    this.nodes.update(n => n.filter(n => n.id !== id));
    this.links.update(l => l.filter(link => link.source !== id && link.target !== id));

    if (this.selectedNode()?.id === id) {
      this.selectedNode.set(null);
      this.propertiesPanelOpen.set(false);
    }

    this.update$.next(true);
  }

  removeLink(id: string): void {
    this.links.update(l => l.filter(link => link.id !== id));
    this.update$.next(true);
  }

  /** Upstream node ids (i.e. dependencies) for a given node in the DAG. */
  upstreamOf(nodeId: string): string[] {
    return this.links()
      .filter(link => link.target === nodeId)
      .map(link => link.source);
  }

  /** Downstream node ids (i.e. dependents) for a given node in the DAG. */
  downstreamOf(nodeId: string): string[] {
    return this.links()
      .filter(link => link.source === nodeId)
      .map(link => link.target);
  }

  /** Move a node earlier in the linear pipeline order. */
  moveNodeUp(nodeId: string): void {
    this.nodes.update(nodes => {
      const idx = nodes.findIndex(n => n.id === nodeId);
      if (idx <= 0) return nodes;
      const copy = [...nodes];
      const [n] = copy.splice(idx, 1);
      copy.splice(idx - 1, 0, n);
      return copy;
    });
    this.update$.next(true);
  }

  /** Move a node later in the linear pipeline order. */
  moveNodeDown(nodeId: string): void {
    this.nodes.update(nodes => {
      const idx = nodes.findIndex(n => n.id === nodeId);
      if (idx < 0 || idx >= nodes.length - 1) return nodes;
      const copy = [...nodes];
      const [n] = copy.splice(idx, 1);
      copy.splice(idx + 1, 0, n);
      return copy;
    });
    this.update$.next(true);
  }

  /** Stable 1-based step position for a node in the current ordering. */
  stepIndex(nodeId: string): number {
    return this.nodes().findIndex(n => n.id === nodeId) + 1;
  }

  // Node selection
  onNodeSelect(node: GraphNode): void {
    this.selectedNode.set({ ...node });
    const op = this.getOperationById(node.operationId);
    this.selectedOperation.set(op || null);
    this.propertiesPanelOpen.set(true);
  }

  onBackgroundClick(): void {
    this.selectedNode.set(null);
    this.propertiesPanelOpen.set(false);
  }

  // Parameter editing
  updateNodeParam(key: string, value: unknown): void {
    const node = this.selectedNode();
    if (!node) return;

    const updatedNode = { ...node, params: { ...node.params, [key]: value } };
    this.nodes.update(nodes => nodes.map(n => n.id === node.id ? updatedNode : n));
    this.selectedNode.set(updatedNode);
  }

  // Type helpers
  getParamType(type: string): 'text' | 'number' | 'boolean' | 'map' | 'char' {
    if (type === 'java.lang.Boolean' || type === 'boolean') return 'boolean';
    if (type === 'java.lang.Integer' || type === 'java.lang.Long' ||
      type === 'java.lang.Double' || type === 'int' || type === 'long' || type === 'double') return 'number';
    if (type === 'java.util.Map') return 'map';
    if (type === 'java.lang.Character' || type === 'char') return 'char';
    return 'text';
  }

  getMapValue(key: string): string {
    const node = this.selectedNode();
    if (!node) return '';
    const val = node.params[key];
    if (val && typeof val === 'object') {
      return JSON.stringify(val, null, 2);
    }
    return '';
  }

  updateMapParam(key: string, jsonStr: string): void {
    try {
      const value = JSON.parse(jsonStr);
      this.updateNodeParam(key, value);
    } catch {
      // Invalid JSON - don't update
    }
  }

  // Auto layout: ngx-graph already computes dagre layout via [layout]="'dagre'",
  // so we just need to trigger an update and fit the result to screen.
  autoLayout(): void {
    if (this.nodes().length === 0) return;

    // Trigger ngx-graph to re-run its built-in dagre layout
    this.update$.next(true);
    // After layout completes, fit the graph to the visible area
    setTimeout(() => this.zoomToFit$.next({}), 50);
  }

  // Custom color function for ngx-graph to prevent ColorHelper null errors
  nodeColorFn = (label: string) => '#6b7280';

  // Type colors
  getTypeColor(type: OperationType | undefined | null): string {
    if (!type) return '#6b7280';
    switch (type) {
      case 'SOURCE': return '#3b82f6';
      case 'TRANSFORM': return '#22c55e';
      case 'CUBE': return '#f59e0b';
      case 'VALIDATION': return '#6b7280';
      case 'OUTPUT': return '#ef4444';
      default: return '#6b7280';
    }
  }

  getTypeBgColor(type: OperationType | undefined | null): string {
    if (!type) return '#f3f4f6';
    switch (type) {
      case 'SOURCE': return '#e0f2fe';
      case 'TRANSFORM': return '#dcfce7';
      case 'CUBE': return '#ffedd5';
      case 'VALIDATION': return '#f3f4f6';
      case 'OUTPUT': return '#fee2e2';
      default: return '#f3f4f6';
    }
  }

  // Save & Run
  save(): void {
    const pipelineName = this.name().trim();
    if (!pipelineName) {
      this.snackBar.open('Pipeline name is required', 'Close', { duration: 3000 });
      return;
    }

    const definition: PipelineDefinition = {
      steps: this.nodes().map(node => ({
        id: node.id,
        operation: node.operationId,
        params: node.params
      }))
    };

    const data: PipelineCreateRequest = {
      name: this.name(),
      description: this.description(),
      definition: JSON.stringify(definition),
      definitionFormat: 'JSON',
      tags: this.tags()
    };

    this.saving.set(true);
    const request = this.isNew()
      ? this.pipelineService.create(data)
      : this.pipelineService.update(this.pipelineId()!, data);

    request.pipe(takeUntil(this.destroy$)).subscribe({
      next: (result) => {
        this.snackBar.open('Pipeline saved successfully', 'Close', { duration: 3000 });
        this.saving.set(false);
        if (this.isNew()) {
          this.isNew.set(false);
          this.pipelineId.set(result.id);
          this.router.navigate(['/pipelines', result.id], { replaceUrl: true });
        }
      },
      error: (err) => {
        this.snackBar.open(err?.error?.message || 'Failed to save pipeline', 'Close', { duration: 3000 });
        this.saving.set(false);
      }
    });
  }

  openRunDialog(): void {
    this.runVariables.set({});
    this.runDialogVisible.set(true);
  }

  run(): void {
    const id = this.pipelineId();
    if (!id) {
      this.snackBar.open('Save pipeline first', 'Close', { duration: 3000 });
      return;
    }

    this.pipelineService.run(id, this.runVariables()).pipe(takeUntil(this.destroy$)).subscribe({
      next: (result) => {
        this.snackBar.open(`Job started: ${result.jobId}`, 'Close', { duration: 3000 });
        this.runDialogVisible.set(false);
        this.router.navigate(['/jobs']);
      },
      error: (err) => {
        this.snackBar.open(err?.error?.message || 'Failed to run pipeline', 'Close', { duration: 3000 });
      }
    });
  }

  validate(): void {
    const errors = this.validationErrors();
    if (errors.length === 0) {
      this.snackBar.open('Pipeline is valid and ready to run', 'Close', { duration: 3000 });
    } else {
      this.snackBar.open(`Validation Failed: ${errors.join(', ')}`, 'Close', { duration: 10000 });
    }
  }

  showJson(): void {
    this.jsonDialogVisible.set(true);
  }

  importPipelineJson(json: string): void {
    this.parsePipelineDefinition(json);
    this.jsonDialogVisible.set(false);
    this.snackBar.open('Pipeline definition imported', 'Close', { duration: 3000 });
  }

  copyPipelineJson(): void {
    navigator.clipboard.writeText(this.pipelineJson()).then(() => {
      this.snackBar.open('Pipeline JSON copied to clipboard', 'Close', { duration: 3000 });
    });
  }

  addRunVariable(): void {
    const key = this.newVarKey();
    const value = this.newVarValue();
    if (key && value) {
      this.runVariables.update(v => ({ ...v, [key]: value }));
      this.newVarKey.set('');
      this.newVarValue.set('');
    }
  }

  removeRunVariable(key: string): void {
    this.runVariables.update(vars => {
      const copy = { ...vars };
      delete copy[key];
      return copy;
    });
  }

  clearCanvas(): void {
    this.confirmationService.confirm({
      title: 'Clear Canvas',
      message: 'Clear all nodes from the canvas?',
      confirmText: 'Clear',
      confirmColor: 'warn'
    }).subscribe(confirmed => {
      if (confirmed) {
        this.nodes.set([]);
        this.links.set([]);
        this.selectedNode.set(null);
        this.propertiesPanelOpen.set(false);
        this.update$.next(true);
      }
    });
  }

  openTemplates(): void {
    this.templatesDialogVisible.set(true);
  }

  applyTemplate(template: PipelineTemplate): void {
    if (this.nodes().length > 0) {
      this.confirmationService.confirm({
        title: 'Replace Pipeline',
        message: 'This will replace your current pipeline. Continue?',
        confirmText: 'Replace',
        confirmColor: 'warn'
      }).subscribe(confirmed => {
        if (confirmed) {
          this.doApplyTemplate(template);
        }
      });
      return;
    }

    this.doApplyTemplate(template);
  }

  private doApplyTemplate(template: PipelineTemplate): void {

    const ops = this.availableOperations();
    const newNodes: GraphNode[] = [];
    const newLinks: GraphLink[] = [];

    template.steps.forEach((step, index) => {
      const op = ops.find(o => o.id === step.operation);
      if (op) {
        const node: GraphNode = {
          id: `node-${Date.now()}-${index}`,
          label: op.name,
          operationId: op.id,
          operationName: op.name,
          operationType: op.type,
          params: { ...this.getDefaultParams(op), ...step.params },
          dimension: { width: 200, height: 100 }
        };
        newNodes.push(node);
      }
    });

    // Create sequential links
    for (let i = 1; i < newNodes.length; i++) {
      newLinks.push({
        id: `link-${Date.now()}-${i}`,
        source: newNodes[i - 1].id,
        target: newNodes[i].id
      });
    }

    this.nodes.set(newNodes);
    this.links.set(newLinks);
    this.name.set(template.name);
    this.description.set(template.description);
    this.templatesDialogVisible.set(false);

    // Auto layout after applying template
    setTimeout(() => this.autoLayout(), 100);

    this.snackBar.open(`Applied template: ${template.name}`, 'Close', { duration: 3000 });
  }

  zoomIn(): void {
    this.zoomLevel.update(z => Math.min(z + 0.1, 2));
  }

  zoomOut(): void {
    this.zoomLevel.update(z => Math.max(z - 0.1, 0.5));
  }

  resetZoom(): void {
    this.zoomLevel.set(1);
    this.center$.next(true);
  }

  fitToScreen(): void {
    this.zoomToFit$.next({});
  }

  cancel(): void {
    this.router.navigate(['/pipelines']);
  }

  isCubeLinkOperation(opId: string | undefined | null): boolean {
    if (!opId) return false;
    return ['fetch-cube', 'fetch-metadata', 'fetch-observations', 'fetch-constraint',
            'build-cube-shape', 'create-observation', 'validate-shacl'].includes(opId);
  }

  getTemplateCategoryColor(category: string): string {
    switch (category) {
      case 'cube': return '#f59e0b';
      case 'validation': return '#22c55e';
      case 'etl': return '#3b82f6';
      case 'publish': return '#8b5cf6';
      default: return '#64748b';
    }
  }

  // Helper methods for operation parameters
  getRequiredParams(op: Operation): { key: string; value: OperationParameter }[] {
    if (!op.parameters) return [];
    return Object.entries(op.parameters)
      .filter(([, param]) => param.required)
      .map(([key, value]) => ({ key, value }));
  }

  getOptionalParams(op: Operation): { key: string; value: OperationParameter }[] {
    if (!op.parameters) return [];
    return Object.entries(op.parameters)
      .filter(([, param]) => !param.required)
      .map(([key, value]) => ({ key, value }));
  }

  getOperationExample(operationId: string): string {
    const op = this.availableOperations().find(o => o.id === operationId);
    if (!op) return '';

    const examples: Record<string, string> = {
      'csv-source': 'source: data/input.csv',
      'sparql-source': 'CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o }',
      'rdf-source': 'source: data/input.ttl',
      'csv-to-rdf': 'Transform CSV rows to RDF triples',
      'shacl-validate': 'shapes: shapes/my-shape.ttl',
      'cube-generate': 'Create qb:DataSet structure',
      'turtle-output': 'output: results/output.ttl',
      'graphdb-output': 'graph: http://example.org/my-graph'
    };

    return examples[operationId] || 'Configure parameters below';
  }

  // Handle zoom change from graph
  onZoomChange(zoom: number): void {
    this.zoomLevel.set(zoom);
  }
}