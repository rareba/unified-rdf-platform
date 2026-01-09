import { Component, inject, OnInit, signal, computed, ElementRef, ViewChild, HostListener } from '@angular/core';
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
import { PipelineService } from '../../../core/services';
import {
  Pipeline,
  PipelineCreateRequest,
  Operation,
  OperationType,
  OperationParameter,
  PipelineNode,
  PipelineEdge,
  PipelineDefinition
} from '../../../core/models';

interface OperationGroup {
  type: OperationType;
  label: string;
  icon: string;
  operations: Operation[];
}

interface RunVariable {
  key: string;
  value: string;
}

interface PipelineTemplate {
  id: string;
  name: string;
  description: string;
  icon: string;
  category: 'cube' | 'validation' | 'etl' | 'publish';
  steps: { operation: string; params: Record<string, unknown> }[];
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
    KeyValuePipe
  ],
  templateUrl: './pipeline-designer.html',
  styleUrl: './pipeline-designer.scss',
})
export class PipelineDesigner implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly pipelineService = inject(PipelineService);
  private readonly snackBar = inject(MatSnackBar);

  @ViewChild('canvas') canvasRef!: ElementRef<HTMLDivElement>;

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

  // Visual Editor
  nodes = signal<PipelineNode[]>([]);
  edges = signal<PipelineEdge[]>([]);
  selectedNode = signal<PipelineNode | null>(null);
  selectedOperation = signal<Operation | null>(null);

  // Dialogs
  configDialogVisible = signal(false);
  runDialogVisible = signal(false);
  jsonDialogVisible = signal(false);

  // Drag state
  isDraggingNode = false;
  draggedNodeId: string | null = null;
  dragOffset = { x: 0, y: 0 };
  private draggedOp: any = null;

  // Edge drawing state
  isDrawingEdge = false;
  edgeStartNodeId: string | null = null;
  mousePos = { x: 0, y: 0 };

  // Run variables
  runVariables = signal<Record<string, string>>({});
  newVarKey = signal('');
  newVarValue = signal('');

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

  // Zoom and pan
  zoom = signal(1);
  panOffset = signal({ x: 0, y: 0 });

  // Templates dialog
  templatesDialogVisible = signal(false);

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
        { operation: 'fetch-cube', params: { endpoint: 'http://localhost:7200/repositories/rdf-forge' } },
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
        { operation: 'graph-store-put', params: { endpoint: 'http://localhost:7200/repositories/rdf-forge' } }
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

    nodeList.forEach(node => {
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

  // For template access to navigator
  navigator = navigator;

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
      error: () => this.snackBar.open('Failed to load operations', 'Close', { duration: 3000 })
    });
  }

  loadPipeline(id: string): void {
    this.loading.set(true);
    this.pipelineService.get(id).subscribe({
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
      const ops = this.availableOperations();

      const loadedNodes: PipelineNode[] = steps.map((step: any, index: number) => {
        const op = ops.find(o => o.id === step.operation);
        return {
          id: step.id || `step-${index}`,
          operationId: step.operation,
          operationName: op?.name || step.operation,
          operationType: op?.type || 'TRANSFORM',
          x: step.ui?.x ?? (100 + (index % 3) * 320),
          y: step.ui?.y ?? (100 + Math.floor(index / 3) * 160),
          params: step.params || step.parameters || {}
        };
      });

      this.nodes.set(loadedNodes);

      // Create sequential edges if no explicit connections
      const loadedEdges: PipelineEdge[] = [];
      for (let i = 1; i < loadedNodes.length; i++) {
        loadedEdges.push({
          id: `edge-${i}`,
          sourceId: loadedNodes[i - 1].id,
          targetId: loadedNodes[i].id
        });
      }
      this.edges.set(loadedEdges);
    } catch (e) {
      console.warn('Failed to parse pipeline definition', e);
    }
  }

  getOperationById(id: string): Operation | undefined {
    return this.availableOperations().find(o => o.id === id);
  }

  // Drag operation from palette to canvas
  onDragStart(event: DragEvent, op: Operation): void {
    this.draggedOp = op;
    event.dataTransfer?.setData('application/json', JSON.stringify(op));
    event.dataTransfer!.effectAllowed = 'copy';
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    event.dataTransfer!.dropEffect = 'copy';
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();

    let op: any = this.draggedOp;

    if (!op) {
      const data = event.dataTransfer?.getData('application/json');
      if (data) {
        try {
          op = JSON.parse(data);
        } catch (e) {
          console.warn('Failed to parse dropped operation data:', e);
          this.snackBar.open('Invalid operation data', 'Close', { duration: 3000 });
          return;
        }
      }
    }

    if (!op) return;

    const rect = this.canvasRef.nativeElement.getBoundingClientRect();
    const x = event.clientX - rect.left;
    const y = event.clientY - rect.top;

    this.addNode(op, x, y);
    this.draggedOp = null; // Reset
  }

  addNode(op: Operation, x: number, y: number): void {
    const newNode: PipelineNode = {
      id: `node-${Date.now()}`,
      operationId: op.id,
      operationName: op.name,
      operationType: op.type,
      x,
      y,
      params: this.getDefaultParams(op)
    };

    this.nodes.update(n => [...n, newNode]);

    // Auto-connect to last node
    const nodeList = this.nodes();
    if (nodeList.length > 1) {
      const lastNode = nodeList[nodeList.length - 2];
      this.addEdge(lastNode.id, newNode.id);
    }
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

  // Node interaction
  configureNode(node: PipelineNode, event?: Event): void {
    event?.stopPropagation();
    this.selectedNode.set({ ...node });
    const op = this.availableOperations().find(o => o.id === node.operationId);
    this.selectedOperation.set(op || null);
    this.configDialogVisible.set(true);
  }

  removeNode(id: string, event: Event): void {
    event.stopPropagation();
    const node = this.nodes().find(n => n.id === id);
    if (!node) return;

    // Remove the node and its connections
    this.nodes.update(n => n.filter(n => n.id !== id));
    this.edges.update(e => e.filter(edge => edge.sourceId !== id && edge.targetId !== id));

    if (this.selectedNode()?.id === id) {
      this.selectedNode.set(null);
      this.configDialogVisible.set(false);
    }
  }

  // Node dragging
  startNodeDrag(event: MouseEvent, node: PipelineNode): void {
    if ((event.target as HTMLElement).classList.contains('node-connector')) return;
    event.stopPropagation();
    this.isDraggingNode = true;
    this.draggedNodeId = node.id;
    const rect = (event.target as HTMLElement).closest('.pipeline-node')?.getBoundingClientRect();
    if (rect) {
      this.dragOffset = {
        x: event.clientX - rect.left,
        y: event.clientY - rect.top
      };
    }
  }

  @HostListener('document:mousemove', ['$event'])
  onMouseMove(event: MouseEvent): void {
    if (this.isDraggingNode && this.draggedNodeId && this.canvasRef) {
      const rect = this.canvasRef.nativeElement.getBoundingClientRect();
      const x = event.clientX - rect.left - this.dragOffset.x;
      const y = event.clientY - rect.top - this.dragOffset.y;

      this.nodes.update(nodes => nodes.map(n =>
        n.id === this.draggedNodeId ? { ...n, x: Math.max(0, x), y: Math.max(0, y) } : n
      ));
    }

    if (this.isDrawingEdge && this.canvasRef) {
      const rect = this.canvasRef.nativeElement.getBoundingClientRect();
      this.mousePos = {
        x: event.clientX - rect.left,
        y: event.clientY - rect.top
      };
    }
  }

  @HostListener('document:mouseup')
  onMouseUp(): void {
    this.isDraggingNode = false;
    this.draggedNodeId = null;
    if (this.isDrawingEdge) {
      this.isDrawingEdge = false;
      this.edgeStartNodeId = null;
    }
  }

  // Edge creation
  startEdgeDraw(event: MouseEvent, nodeId: string): void {
    event.stopPropagation();
    event.preventDefault();
    this.isDrawingEdge = true;
    this.edgeStartNodeId = nodeId;
    const rect = this.canvasRef.nativeElement.getBoundingClientRect();
    this.mousePos = {
      x: event.clientX - rect.left,
      y: event.clientY - rect.top
    };
  }

  finishEdgeDraw(event: MouseEvent, targetNodeId: string): void {
    event.stopPropagation();
    if (this.isDrawingEdge && this.edgeStartNodeId && this.edgeStartNodeId !== targetNodeId) {
      this.addEdge(this.edgeStartNodeId, targetNodeId);
    }
    this.isDrawingEdge = false;
    this.edgeStartNodeId = null;
  }

  addEdge(sourceId: string, targetId: string): void {
    const exists = this.edges().some(e => e.sourceId === sourceId && e.targetId === targetId);
    if (exists) return;

    this.edges.update(e => [...e, {
      id: `edge-${Date.now()}`,
      sourceId,
      targetId
    }]);
  }

  removeEdge(id: string, event: Event): void {
    event.stopPropagation();
    this.edges.update(e => e.filter(edge => edge.id !== id));
  }

  // Parameter editing
  updateNodeParam(key: string, value: unknown): void {
    const node = this.selectedNode();
    if (!node) return;

    const updatedNode = { ...node, params: { ...node.params, [key]: value } };
    this.nodes.update(nodes => nodes.map(n => n.id === node.id ? updatedNode : n));
    this.selectedNode.set(updatedNode);
  }

  saveNodeConfig(): void {
    this.configDialogVisible.set(false);
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

  // Path calculations
  getNodeCenter(nodeId: string): { x: number; y: number } {
    const node = this.nodes().find(n => n.id === nodeId);
    if (!node) return { x: 0, y: 0 };
    return { x: node.x + 150, y: node.y + 50 };
  }

  getPathForEdge(edge: PipelineEdge): string {
    const start = this.getNodeCenter(edge.sourceId);
    const end = this.getNodeCenter(edge.targetId);
    const dx = Math.abs(end.x - start.x) * 0.4;
    return `M ${start.x} ${start.y} C ${start.x + dx} ${start.y}, ${end.x - dx} ${end.y}, ${end.x} ${end.y}`;
  }

  getDrawingPath(): string {
    if (!this.isDrawingEdge || !this.edgeStartNodeId) return '';
    const start = this.getNodeCenter(this.edgeStartNodeId);
    const end = this.mousePos;
    const dx = Math.abs(end.x - start.x) * 0.4;
    return `M ${start.x} ${start.y} C ${start.x + dx} ${start.y}, ${end.x - dx} ${end.y}, ${end.x} ${end.y}`;
  }

  // Type colors
  getTypeColor(type: OperationType): 'success' | 'info' | 'warn' | 'danger' | 'secondary' {
    switch (type) {
      case 'SOURCE': return 'info';
      case 'TRANSFORM': return 'success';
      case 'CUBE': return 'warn';
      case 'VALIDATION': return 'secondary';
      case 'OUTPUT': return 'danger';
      default: return 'secondary';
    }
  }

  getNodeBorderColor(type: OperationType): string {
    switch (type) {
      case 'SOURCE': return '#3b82f6';
      case 'TRANSFORM': return '#22c55e';
      case 'CUBE': return '#f59e0b';
      case 'VALIDATION': return '#6b7280';
      case 'OUTPUT': return '#ef4444';
      default: return '#6b7280';
    }
  }

  // Save & Run
  save(): void {
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

    request.subscribe({
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

    this.pipelineService.run(id, this.runVariables()).subscribe({
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
    if (confirm('Clear all nodes from the canvas?')) {
      this.nodes.set([]);
      this.edges.set([]);
      this.selectedNode.set(null);
    }
  }

  // Template methods
  openTemplates(): void {
    this.templatesDialogVisible.set(true);
  }

  applyTemplate(template: PipelineTemplate): void {
    if (this.nodes().length > 0) {
      if (!confirm('This will replace your current pipeline. Continue?')) {
        return;
      }
    }

    const ops = this.availableOperations();
    const newNodes: PipelineNode[] = [];
    const newEdges: PipelineEdge[] = [];

    template.steps.forEach((step, index) => {
      const op = ops.find(o => o.id === step.operation);
      if (op) {
        const node: PipelineNode = {
          id: `node-${Date.now()}-${index}`,
          operationId: op.id,
          operationName: op.name,
          operationType: op.type,
          x: 150 + (index % 3) * 350,
          y: 100 + Math.floor(index / 3) * 180,
          params: { ...this.getDefaultParams(op), ...step.params }
        };
        newNodes.push(node);
      }
    });

    // Create sequential edges
    for (let i = 1; i < newNodes.length; i++) {
      newEdges.push({
        id: `edge-${Date.now()}-${i}`,
        sourceId: newNodes[i - 1].id,
        targetId: newNodes[i].id
      });
    }

    this.nodes.set(newNodes);
    this.edges.set(newEdges);
    this.name.set(template.name);
    this.description.set(template.description);
    this.templatesDialogVisible.set(false);
    this.snackBar.open(`Applied template: ${template.name}`, 'Close', { duration: 3000 });
  }

  // Zoom methods
  zoomIn(): void {
    this.zoom.update(z => Math.min(z + 0.1, 2));
  }

  zoomOut(): void {
    this.zoom.update(z => Math.max(z - 0.1, 0.5));
  }

  resetZoom(): void {
    this.zoom.set(1);
    this.panOffset.set({ x: 0, y: 0 });
  }

  // Check if operation is cube-link related
  isCubeLinkOperation(opId: string): boolean {
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

  cancel(): void {
    this.router.navigate(['/pipelines']);
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
}
