import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { SelectModule } from 'primeng/select';
import { CardModule } from 'primeng/card';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { TextareaModule } from 'primeng/textarea';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { TriplestoreService } from '../../../core/services';
import { TriplestoreConnection, Graph, QueryResult } from '../../../core/models';

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
    ToastModule
  ],
  providers: [MessageService],
  templateUrl: './triplestore-browser.html',
  styleUrl: './triplestore-browser.scss',
})
export class TriplestoreBrowser implements OnInit {
  private readonly triplestoreService = inject(TriplestoreService);
  private readonly messageService = inject(MessageService);

  connections = signal<TriplestoreConnection[]>([]);
  selectedConnection = signal<TriplestoreConnection | null>(null);
  graphs = signal<Graph[]>([]);
  selectedGraph = signal<Graph | null>(null);
  sparqlQuery = signal('SELECT ?s ?p ?o WHERE { ?s ?p ?o } LIMIT 100');
  queryResult = signal<QueryResult | null>(null);
  loading = signal(false);
  queryLoading = signal(false);

  ngOnInit(): void {
    this.loadConnections();
  }

  loadConnections(): void {
    this.loading.set(true);
    this.triplestoreService.list().subscribe({
      next: (data) => {
        this.connections.set(data);
        if (data.length > 0) {
          this.selectConnection(data[0]);
        }
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
      }
    });
  }

  selectConnection(connection: TriplestoreConnection): void {
    this.selectedConnection.set(connection);
    this.loadGraphs(connection.id);
  }

  loadGraphs(connectionId: string): void {
    this.triplestoreService.getGraphs(connectionId).subscribe({
      next: (data) => this.graphs.set(data),
      error: () => this.graphs.set([])
    });
  }

  executeQuery(): void {
    const conn = this.selectedConnection();
    if (!conn) return;

    this.queryLoading.set(true);
    this.triplestoreService.executeSparql(conn.id, this.sparqlQuery(), this.selectedGraph()?.uri).subscribe({
      next: (result) => {
        this.queryResult.set(result);
        this.queryLoading.set(false);
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Error', detail: 'Query execution failed' });
        this.queryLoading.set(false);
      }
    });
  }

  formatNumber(n: number): string {
    return n.toLocaleString();
  }
}
