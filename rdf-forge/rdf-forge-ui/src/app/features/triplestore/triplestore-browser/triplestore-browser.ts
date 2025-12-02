import { Component, inject, OnInit, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { SelectModule } from 'primeng/select';
import { CardModule } from 'primeng/card';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { TextareaModule } from 'primeng/textarea';
import { InputTextModule } from 'primeng/inputtext';
import { ToastModule } from 'primeng/toast';
import { TooltipModule } from 'primeng/tooltip';
import { DialogModule } from 'primeng/dialog';
import { TagModule } from 'primeng/tag';
import { ProgressBarModule } from 'primeng/progressbar';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { TabsModule } from 'primeng/tabs';
import { SplitterModule } from 'primeng/splitter';
import { DividerModule } from 'primeng/divider';
import { MessageService, ConfirmationService } from 'primeng/api';
import { TriplestoreService } from '../../../core/services';
import {
  TriplestoreConnection,
  ConnectionCreateRequest,
  Graph,
  Resource,
  QueryResult,
  TriplestoreType,
  AuthType,
  RdfFormat
} from '../../../core/models';

interface QueryTemplate {
  name: string;
  query: string;
  description: string;
}

@Component({
  selector: 'app-triplestore-browser',
  imports: [
    CommonModule,
    FormsModule,
    SelectModule,
    CardModule,
    TableModule,
    ButtonModule,
    TextareaModule,
    InputTextModule,
    ToastModule,
    TooltipModule,
    DialogModule,
    TagModule,
    ProgressBarModule,
    ConfirmDialogModule,
    TabsModule,
    SplitterModule,
    DividerModule
  ],
  providers: [MessageService, ConfirmationService],
  templateUrl: './triplestore-browser.html',
  styleUrl: './triplestore-browser.scss',
})
export class TriplestoreBrowser implements OnInit {
  private readonly triplestoreService = inject(TriplestoreService);
  private readonly messageService = inject(MessageService);
  private readonly confirmationService = inject(ConfirmationService);

  // Core data
  connections = signal<TriplestoreConnection[]>([]);
  selectedConnection = signal<TriplestoreConnection | null>(null);
  graphs = signal<Graph[]>([]);
  selectedGraph = signal<Graph | null>(null);

  // Query
  sparqlQuery = signal('SELECT ?s ?p ?o WHERE { ?s ?p ?o } LIMIT 100');
  queryResult = signal<QueryResult | null>(null);

  // Resources
  resources = signal<Resource[]>([]);
  selectedResource = signal<Resource | null>(null);
  resourceSearchQuery = signal('');

  // Loading states
  loading = signal(true);
  queryLoading = signal(false);
  graphsLoading = signal(false);
  resourcesLoading = signal(false);
  testingConnection = signal(false);
  uploadingRdf = signal(false);

  // Dialogs
  connectionDialogVisible = signal(false);
  editConnectionDialogVisible = signal(false);
  resourceDialogVisible = signal(false);
  uploadDialogVisible = signal(false);
  graphDetailsDialogVisible = signal(false);

  // Forms
  newConnection = signal<ConnectionCreateRequest>({
    name: '',
    type: 'fuseki',
    url: '',
    defaultGraph: '',
    authType: 'none',
    authConfig: {},
    isDefault: false
  });

  editConnection = signal<Partial<TriplestoreConnection & ConnectionCreateRequest>>({});

  uploadForm = signal<{ graphUri: string; content: string; format: RdfFormat }>({
    graphUri: '',
    content: '',
    format: 'turtle'
  });

  // Options
  triplestoreTypes: { label: string; value: TriplestoreType }[] = [
    { label: 'Apache Jena Fuseki', value: 'fuseki' },
    { label: 'Stardog', value: 'stardog' },
    { label: 'GraphDB', value: 'graphdb' },
    { label: 'Amazon Neptune', value: 'neptune' },
    { label: 'Virtuoso', value: 'virtuoso' }
  ];

  authTypes: { label: string; value: AuthType }[] = [
    { label: 'None', value: 'none' },
    { label: 'Basic Auth', value: 'basic' },
    { label: 'API Key', value: 'apikey' },
    { label: 'OAuth 2.0', value: 'oauth2' }
  ];

  rdfFormats: { label: string; value: RdfFormat }[] = [
    { label: 'Turtle', value: 'turtle' },
    { label: 'RDF/XML', value: 'rdfxml' },
    { label: 'N-Triples', value: 'ntriples' },
    { label: 'JSON-LD', value: 'jsonld' }
  ];

  queryTemplates: QueryTemplate[] = [
    {
      name: 'All Triples',
      query: 'SELECT ?s ?p ?o WHERE { ?s ?p ?o } LIMIT 100',
      description: 'Get first 100 triples'
    },
    {
      name: 'Count Triples',
      query: 'SELECT (COUNT(*) AS ?count) WHERE { ?s ?p ?o }',
      description: 'Count total triples'
    },
    {
      name: 'List Classes',
      query: 'SELECT DISTINCT ?class WHERE { ?s a ?class } ORDER BY ?class',
      description: 'List all RDF classes'
    },
    {
      name: 'List Properties',
      query: 'SELECT DISTINCT ?property WHERE { ?s ?property ?o } ORDER BY ?property',
      description: 'List all properties'
    },
    {
      name: 'Named Graphs',
      query: 'SELECT DISTINCT ?g WHERE { GRAPH ?g { ?s ?p ?o } }',
      description: 'List all named graphs'
    },
    {
      name: 'Instances of Class',
      query: 'SELECT ?instance ?label WHERE {\n  ?instance a <CLASS_URI> .\n  OPTIONAL { ?instance rdfs:label ?label }\n} LIMIT 100',
      description: 'Find instances of a specific class'
    },
    {
      name: 'DESCRIBE Resource',
      query: 'DESCRIBE <RESOURCE_URI>',
      description: 'Get all triples about a resource'
    },
    {
      name: 'CONSTRUCT Graph',
      query: 'CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o } LIMIT 100',
      description: 'Construct an RDF graph'
    }
  ];

  // Computed
  totalTriples = computed(() => this.graphs().reduce((sum, g) => sum + g.tripleCount, 0));
  connectionStatus = computed(() => {
    const conn = this.selectedConnection();
    return conn?.healthStatus || 'unknown';
  });

  ngOnInit(): void {
    this.loadConnections();
  }

  loadConnections(): void {
    this.loading.set(true);
    this.triplestoreService.list().subscribe({
      next: (data) => {
        this.connections.set(data);
        if (data.length > 0) {
          const defaultConn = data.find(c => c.isDefault) || data[0];
          this.selectConnection(defaultConn);
        }
        this.loading.set(false);
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Error', detail: 'Failed to load connections' });
        this.loading.set(false);
      }
    });
  }

  selectConnection(connection: TriplestoreConnection): void {
    this.selectedConnection.set(connection);
    this.selectedGraph.set(null);
    this.queryResult.set(null);
    this.resources.set([]);
    this.loadGraphs(connection.id);
  }

  loadGraphs(connectionId: string): void {
    this.graphsLoading.set(true);
    this.triplestoreService.getGraphs(connectionId).subscribe({
      next: (data) => {
        this.graphs.set(data);
        this.graphsLoading.set(false);
      },
      error: () => {
        this.graphs.set([]);
        this.graphsLoading.set(false);
      }
    });
  }

  selectGraph(graph: Graph): void {
    this.selectedGraph.set(graph);
    this.loadResources(graph.uri);
  }

  loadResources(graphUri: string): void {
    const conn = this.selectedConnection();
    if (!conn) return;

    this.resourcesLoading.set(true);
    this.triplestoreService.getGraphResources(conn.id, graphUri, { limit: 100, offset: 0 }).subscribe({
      next: (data) => {
        this.resources.set(data);
        this.resourcesLoading.set(false);
      },
      error: () => {
        this.resources.set([]);
        this.resourcesLoading.set(false);
      }
    });
  }

  searchResources(): void {
    const conn = this.selectedConnection();
    const graph = this.selectedGraph();
    const query = this.resourceSearchQuery();
    if (!conn || !graph || !query) return;

    this.resourcesLoading.set(true);
    this.triplestoreService.searchResources(conn.id, graph.uri, query).subscribe({
      next: (data) => {
        this.resources.set(data);
        this.resourcesLoading.set(false);
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Error', detail: 'Search failed' });
        this.resourcesLoading.set(false);
      }
    });
  }

  viewResource(resource: Resource): void {
    const conn = this.selectedConnection();
    const graph = this.selectedGraph();
    if (!conn || !graph) return;

    this.triplestoreService.getResource(conn.id, graph.uri, resource.uri).subscribe({
      next: (data) => {
        this.selectedResource.set(data);
        this.resourceDialogVisible.set(true);
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Error', detail: 'Failed to load resource' });
      }
    });
  }

  // Connection Management
  openConnectionDialog(): void {
    this.newConnection.set({
      name: '',
      type: 'fuseki',
      url: 'http://localhost:3030/',
      defaultGraph: '',
      authType: 'none',
      authConfig: {},
      isDefault: false
    });
    this.connectionDialogVisible.set(true);
  }

  openEditConnectionDialog(conn: TriplestoreConnection): void {
    this.editConnection.set({ ...conn });
    this.editConnectionDialogVisible.set(true);
  }

  createConnection(): void {
    const conn = this.newConnection();
    if (!conn.name || !conn.url) {
      this.messageService.add({ severity: 'warn', summary: 'Warning', detail: 'Name and URL are required' });
      return;
    }

    this.triplestoreService.create(conn).subscribe({
      next: () => {
        this.messageService.add({ severity: 'success', summary: 'Created', detail: 'Connection created successfully' });
        this.connectionDialogVisible.set(false);
        this.loadConnections();
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Error', detail: 'Failed to create connection' });
      }
    });
  }

  saveConnection(): void {
    const conn = this.editConnection();
    if (!conn.id) return;

    this.triplestoreService.update(conn.id, conn).subscribe({
      next: () => {
        this.messageService.add({ severity: 'success', summary: 'Updated', detail: 'Connection updated successfully' });
        this.editConnectionDialogVisible.set(false);
        this.loadConnections();
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Error', detail: 'Failed to update connection' });
      }
    });
  }

  confirmDeleteConnection(conn: TriplestoreConnection): void {
    this.confirmationService.confirm({
      message: `Are you sure you want to delete "${conn.name}"?`,
      header: 'Confirm Delete',
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => {
        this.deleteConnection(conn);
      }
    });
  }

  deleteConnection(conn: TriplestoreConnection): void {
    this.triplestoreService.delete(conn.id).subscribe({
      next: () => {
        this.messageService.add({ severity: 'success', summary: 'Deleted', detail: 'Connection deleted successfully' });
        if (this.selectedConnection()?.id === conn.id) {
          this.selectedConnection.set(null);
        }
        this.loadConnections();
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Error', detail: 'Failed to delete connection' });
      }
    });
  }

  testConnection(conn: TriplestoreConnection): void {
    this.testingConnection.set(true);
    this.triplestoreService.test(conn.id).subscribe({
      next: (result) => {
        if (result.success) {
          this.messageService.add({
            severity: 'success',
            summary: 'Connected',
            detail: `Connection successful (${result.latencyMs}ms)`
          });
        } else {
          this.messageService.add({
            severity: 'error',
            summary: 'Failed',
            detail: result.message || 'Connection failed'
          });
        }
        this.testingConnection.set(false);
        this.loadConnections();
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Error', detail: 'Connection test failed' });
        this.testingConnection.set(false);
      }
    });
  }

  // Query Execution
  executeQuery(): void {
    const conn = this.selectedConnection();
    if (!conn) return;

    this.queryLoading.set(true);
    this.triplestoreService.executeSparql(conn.id, this.sparqlQuery(), this.selectedGraph()?.uri).subscribe({
      next: (result) => {
        this.queryResult.set(result);
        this.queryLoading.set(false);
        this.messageService.add({
          severity: 'success',
          summary: 'Query Executed',
          detail: `${result.bindings.length} results in ${result.executionTime}ms`
        });
      },
      error: (err) => {
        this.messageService.add({
          severity: 'error',
          summary: 'Query Error',
          detail: err.error?.message || 'Query execution failed'
        });
        this.queryLoading.set(false);
      }
    });
  }

  applyTemplate(template: QueryTemplate): void {
    this.sparqlQuery.set(template.query);
  }

  // Graph Management
  openUploadDialog(): void {
    const graph = this.selectedGraph();
    this.uploadForm.set({
      graphUri: graph?.uri || '',
      content: '',
      format: 'turtle'
    });
    this.uploadDialogVisible.set(true);
  }

  uploadRdf(): void {
    const conn = this.selectedConnection();
    const form = this.uploadForm();
    if (!conn || !form.graphUri || !form.content) {
      this.messageService.add({ severity: 'warn', summary: 'Warning', detail: 'Graph URI and content are required' });
      return;
    }

    this.uploadingRdf.set(true);
    this.triplestoreService.uploadRdf(conn.id, form.graphUri, form.content, form.format).subscribe({
      next: (result) => {
        this.messageService.add({
          severity: 'success',
          summary: 'Uploaded',
          detail: `${result.triplesLoaded} triples loaded`
        });
        this.uploadingRdf.set(false);
        this.uploadDialogVisible.set(false);
        this.loadGraphs(conn.id);
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Error', detail: 'Failed to upload RDF' });
        this.uploadingRdf.set(false);
      }
    });
  }

  confirmDeleteGraph(graph: Graph): void {
    this.confirmationService.confirm({
      message: `Are you sure you want to delete graph "${graph.uri}"? This will remove ${this.formatNumber(graph.tripleCount)} triples.`,
      header: 'Confirm Delete',
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => {
        this.deleteGraph(graph);
      }
    });
  }

  deleteGraph(graph: Graph): void {
    const conn = this.selectedConnection();
    if (!conn) return;

    this.triplestoreService.deleteGraph(conn.id, graph.uri).subscribe({
      next: () => {
        this.messageService.add({ severity: 'success', summary: 'Deleted', detail: 'Graph deleted successfully' });
        if (this.selectedGraph()?.uri === graph.uri) {
          this.selectedGraph.set(null);
        }
        this.loadGraphs(conn.id);
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Error', detail: 'Failed to delete graph' });
      }
    });
  }

  exportGraph(graph: Graph, format: RdfFormat = 'turtle'): void {
    const conn = this.selectedConnection();
    if (!conn) return;

    this.triplestoreService.exportGraph(conn.id, graph.uri, format).subscribe({
      next: (content) => {
        const blob = new Blob([content], { type: 'text/plain' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        const ext = format === 'turtle' ? 'ttl' : format === 'rdfxml' ? 'rdf' : format === 'jsonld' ? 'jsonld' : 'nt';
        a.download = `graph-export.${ext}`;
        a.click();
        URL.revokeObjectURL(url);
        this.messageService.add({ severity: 'success', summary: 'Exported', detail: 'Graph exported successfully' });
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Error', detail: 'Failed to export graph' });
      }
    });
  }

  viewGraphDetails(graph: Graph): void {
    this.selectedGraph.set(graph);
    this.graphDetailsDialogVisible.set(true);
  }

  // Helpers
  getStatusSeverity(status: string): 'success' | 'danger' | 'warn' | 'secondary' {
    switch (status) {
      case 'healthy': return 'success';
      case 'unhealthy': return 'danger';
      default: return 'secondary';
    }
  }

  getStatusIcon(status: string): string {
    switch (status) {
      case 'healthy': return 'pi pi-check-circle';
      case 'unhealthy': return 'pi pi-times-circle';
      default: return 'pi pi-question-circle';
    }
  }

  getTypeIcon(type: string): string {
    switch (type) {
      case 'fuseki': return 'pi pi-server';
      case 'stardog': return 'pi pi-star';
      case 'graphdb': return 'pi pi-sitemap';
      case 'neptune': return 'pi pi-cloud';
      case 'virtuoso': return 'pi pi-database';
      default: return 'pi pi-database';
    }
  }

  formatNumber(n: number): string {
    if (n >= 1000000) return `${(n / 1000000).toFixed(1)}M`;
    if (n >= 1000) return `${(n / 1000).toFixed(1)}K`;
    return n.toLocaleString();
  }

  formatDate(date: Date | undefined): string {
    if (!date) return '-';
    return new Date(date).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  }

  copyToClipboard(text: string, label: string): void {
    navigator.clipboard.writeText(text).then(() => {
      this.messageService.add({ severity: 'info', summary: 'Copied', detail: `${label} copied to clipboard` });
    });
  }

  shortenUri(uri: string, maxLength: number = 50): string {
    if (uri.length <= maxLength) return uri;
    const lastSlash = uri.lastIndexOf('/');
    const lastHash = uri.lastIndexOf('#');
    const splitPoint = Math.max(lastSlash, lastHash);
    if (splitPoint > 0 && uri.length - splitPoint < maxLength - 10) {
      return '...' + uri.substring(splitPoint);
    }
    return uri.substring(0, maxLength - 3) + '...';
  }

  // Upload form helpers
  updateUploadGraphUri(value: string): void {
    this.uploadForm.update(f => ({ ...f, graphUri: value }));
  }

  updateUploadContent(value: string): void {
    this.uploadForm.update(f => ({ ...f, content: value }));
  }

  updateUploadFormat(value: RdfFormat): void {
    this.uploadForm.update(f => ({ ...f, format: value }));
  }

  // Edit connection helpers
  updateEditConnectionName(value: string): void {
    this.editConnection.update(c => ({ ...c, name: value }));
  }

  updateEditConnectionType(value: TriplestoreType): void {
    this.editConnection.update(c => ({ ...c, type: value }));
  }

  updateEditConnectionAuthType(value: AuthType): void {
    this.editConnection.update(c => ({ ...c, authType: value }));
  }

  updateEditConnectionUrl(value: string): void {
    this.editConnection.update(c => ({ ...c, url: value }));
  }

  updateEditConnectionDefaultGraph(value: string): void {
    this.editConnection.update(c => ({ ...c, defaultGraph: value }));
  }

  // New connection helpers
  updateNewConnectionName(value: string): void {
    this.newConnection.update(c => ({ ...c, name: value }));
  }

  updateNewConnectionType(value: TriplestoreType): void {
    this.newConnection.update(c => ({ ...c, type: value }));
  }

  updateNewConnectionAuthType(value: AuthType): void {
    this.newConnection.update(c => ({ ...c, authType: value }));
  }

  updateNewConnectionUrl(value: string): void {
    this.newConnection.update(c => ({ ...c, url: value }));
  }

  updateNewConnectionDefaultGraph(value: string): void {
    this.newConnection.update(c => ({ ...c, defaultGraph: value }));
  }

  updateNewConnectionAuthUsername(value: string): void {
    this.newConnection.update(c => ({
      ...c,
      authConfig: { ...c.authConfig, username: value }
    }));
  }

  updateNewConnectionAuthPassword(value: string): void {
    this.newConnection.update(c => ({
      ...c,
      authConfig: { ...c.authConfig, password: value }
    }));
  }

  updateNewConnectionAuthApiKey(value: string): void {
    this.newConnection.update(c => ({
      ...c,
      authConfig: { ...c.authConfig, apiKey: value }
    }));
  }

  // Helper for JSON.stringify in templates
  getResultsJson(): string {
    const result = this.queryResult();
    return result ? JSON.stringify(result.bindings, null, 2) : '';
  }
}
