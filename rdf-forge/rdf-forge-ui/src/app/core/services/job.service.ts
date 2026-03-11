import { Injectable, inject, NgZone, OnDestroy } from '@angular/core';
import { Observable, BehaviorSubject, Subject } from 'rxjs';
import { ApiService } from './api.service';
import { Job, JobLog, JobMetrics, JobSchedule } from '../models';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import { LoggerService } from './logger.service';
import SockJS from 'sockjs-client';

export interface JobListParams {
  status?: string;
  pipelineId?: string;
  page?: number;
  size?: number;
  limit?: number;
  sort?: string;
}

export interface JobLogParams {
  level?: string;
  limit?: number;
  offset?: number;
}

export interface LogStreamMessage {
  type: 'log' | 'status' | 'completion' | 'historical' | 'subscription';
  timestamp?: string;
  level?: string;
  step?: string;
  message?: string;
  details?: Record<string, unknown>;
  status?: string;
  progress?: number;
  success?: boolean;
  errorMessage?: string;
  historicalLogs?: JobLog[];
  logCount?: number;
}

export interface ConnectionStatus {
  connected: boolean;
  reconnecting: boolean;
  error?: string;
}

@Injectable({
  providedIn: 'root'
})
export class JobService implements OnDestroy {
  private readonly api = inject(ApiService);
  private readonly ngZone = inject(NgZone);
  private readonly logger = inject(LoggerService);

  // WebSocket client
  private stompClient: Client | null = null;
  private currentSubscription: StompSubscription | null = null;
  private currentJobId: string | null = null;

  // Reconnection logic
  private reconnectAttempts = 0;
  private maxReconnectAttempts = 10;
  private baseReconnectDelay = 1000;
  private maxReconnectDelay = 30000;
  private reconnectTimeout: ReturnType<typeof setTimeout> | null = null;
  private shouldReconnect = false;

  // Subjects for reactive streams
  private logStreamSubject = new Subject<LogStreamMessage>();
  private connectionStatusSubject = new BehaviorSubject<ConnectionStatus>({
    connected: false,
    reconnecting: false
  });

  // Public observables
  public readonly logStream$ = this.logStreamSubject.asObservable();
  public readonly connectionStatus$ = this.connectionStatusSubject.asObservable();

  // API Methods

  list(params?: JobListParams): Observable<Job[]> {
    return this.api.getArray<Job>('/jobs', params as Record<string, unknown>);
  }

  get(id: string): Observable<Job> {
    return this.api.get<Job>(`/jobs/${id}`);
  }

  create(pipelineId: string, variables?: Record<string, unknown>, priority?: number): Observable<Job> {
    return this.api.post<Job>('/jobs', { pipelineId, variables, priority });
  }

  cancel(id: string): Observable<void> {
    return this.api.delete<void>(`/jobs/${id}`);
  }

  retry(id: string): Observable<Job> {
    return this.api.post<Job>(`/jobs/${id}/retry`, {});
  }

  delete(id: string): Observable<void> {
    return this.api.delete<void>(`/jobs/${id}`);
  }

  getLogs(id: string, params?: JobLogParams): Observable<JobLog[]> {
    return this.api.getArray<JobLog>(`/jobs/${id}/logs`, params as Record<string, unknown>);
  }

  getMetrics(id: string): Observable<JobMetrics> {
    return this.api.get<JobMetrics>(`/jobs/${id}/metrics`);
  }

  // Schedule endpoints
  getSchedules(): Observable<JobSchedule[]> {
    return this.api.getArray<JobSchedule>('/schedules');
  }

  createSchedule(pipelineId: string, cronExpression: string, variables?: Record<string, unknown>): Observable<JobSchedule> {
    return this.api.post<JobSchedule>('/schedules', { pipelineId, cronExpression, variables });
  }

  updateSchedule(id: string, data: Partial<JobSchedule>): Observable<JobSchedule> {
    return this.api.post<JobSchedule>(`/schedules/${id}`, data);
  }

  deleteSchedule(id: string): Observable<void> {
    return this.api.delete<void>(`/schedules/${id}`);
  }

  // WebSocket Methods

  /**
   * Connect to WebSocket and subscribe to job logs.
   * @param jobId The job ID to subscribe to
   */
  connectToJobLogs(jobId: string): void {
    if (this.currentJobId === jobId && this.stompClient?.active) {
      // Already connected to this job
      return;
    }

    // Disconnect from any previous job
    this.disconnect();

    this.currentJobId = jobId;
    this.shouldReconnect = true;
    this.reconnectAttempts = 0;

    this.connect();
  }

  /**
   * Disconnect from WebSocket.
   */
  disconnect(): void {
    this.shouldReconnect = false;

    if (this.reconnectTimeout) {
      clearTimeout(this.reconnectTimeout);
      this.reconnectTimeout = null;
    }

    if (this.currentSubscription) {
      this.currentSubscription.unsubscribe();
      this.currentSubscription = null;
    }

    if (this.stompClient) {
      this.stompClient.deactivate();
      this.stompClient = null;
    }

    this.currentJobId = null;
    this.reconnectAttempts = 0;

    this.ngZone.run(() => {
      this.connectionStatusSubject.next({ connected: false, reconnecting: false });
    });
  }

  /**
   * Check if currently connected to WebSocket.
   */
  isConnected(): boolean {
    return this.stompClient?.active ?? false;
  }

  private connect(): void {
    if (!this.currentJobId) {
      return;
    }

    const wsUrl = this.getWebSocketUrl();

    this.stompClient = new Client({
      webSocketFactory: () => new SockJS(wsUrl),
      reconnectDelay: 0, // We handle reconnection manually for exponential backoff
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      debug: (msg) => {
        // Only log in development
        if (typeof window !== 'undefined' && (window as unknown as { __DEBUG_WS__: boolean }).__DEBUG_WS__) {
          console.debug('[STOMP]', msg);
        }
      },
      onConnect: (frame) => {
        this.ngZone.run(() => {
          this.handleConnect();
        });
      },
      onDisconnect: () => {
        this.ngZone.run(() => {
          this.handleDisconnect();
        });
      },
      onStompError: (frame) => {
        this.ngZone.run(() => {
          this.handleError(frame.headers['message'] || 'STOMP error');
        });
      },
      onWebSocketError: (event) => {
        this.ngZone.run(() => {
          this.handleError('WebSocket connection failed');
        });
      }
    });

    this.stompClient.activate();
  }

  private handleConnect(): void {
    this.reconnectAttempts = 0;

    this.ngZone.run(() => {
      this.connectionStatusSubject.next({ connected: true, reconnecting: false });
    });

    if (this.currentJobId && this.stompClient) {
      // Subscribe to job logs topic
      this.currentSubscription = this.stompClient.subscribe(
        `/topic/jobs/${this.currentJobId}/logs`,
        (message: IMessage) => {
          this.ngZone.run(() => {
            this.handleMessage(message);
          });
        }
      );
    }
  }

  private handleDisconnect(): void {
    this.ngZone.run(() => {
      this.connectionStatusSubject.next({
        connected: false,
        reconnecting: this.shouldReconnect
      });
    });

    if (this.shouldReconnect) {
      this.scheduleReconnect();
    }
  }

  private handleError(errorMessage: string): void {
    this.logger.error('[JobService] WebSocket error:', errorMessage);

    this.ngZone.run(() => {
      this.connectionStatusSubject.next({
        connected: false,
        reconnecting: this.shouldReconnect,
        error: errorMessage
      });
    });

    // Error will trigger disconnect, which will schedule reconnect
  }

  private handleMessage(message: IMessage): void {
    try {
      const body: LogStreamMessage = JSON.parse(message.body);
      this.logStreamSubject.next(body);
    } catch (error) {
      this.logger.error('[JobService] Failed to parse WebSocket message:', error);
    }
  }

  private scheduleReconnect(): void {
    if (this.reconnectAttempts >= this.maxReconnectAttempts) {
      this.logger.error('[JobService] Max reconnection attempts reached');
      this.ngZone.run(() => {
        this.connectionStatusSubject.next({
          connected: false,
          reconnecting: false,
          error: 'Max reconnection attempts reached. Please refresh to retry.'
        });
      });
      return;
    }

    this.reconnectAttempts++;

    // Exponential backoff with jitter
    const delay = Math.min(
      this.baseReconnectDelay * Math.pow(2, this.reconnectAttempts - 1),
      this.maxReconnectDelay
    );
    const jitter = Math.random() * 1000;
    const finalDelay = delay + jitter;

    this.ngZone.run(() => {
      this.connectionStatusSubject.next({
        connected: false,
        reconnecting: true,
        error: `Reconnecting... (attempt ${this.reconnectAttempts}/${this.maxReconnectAttempts})`
      });
    });

    this.reconnectTimeout = setTimeout(() => {
      if (this.shouldReconnect) {
        this.connect();
      }
    }, finalDelay);
  }

  private getWebSocketUrl(): string {
    // Get the base API URL from the current window location or use default
    const protocol = typeof window !== 'undefined' ? window.location.protocol : 'http:';
    const host = typeof window !== 'undefined' ? window.location.host : 'localhost:4200';

    // Use the gateway URL with WebSocket endpoint
    // In development, the proxy will route this correctly
    return `${protocol}//${host}/api/v1/ws`;
  }

  ngOnDestroy(): void {
    this.disconnect();
    this.logStreamSubject.complete();
    this.connectionStatusSubject.complete();
  }
}
