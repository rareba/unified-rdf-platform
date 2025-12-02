export type JobStatus = 'pending' | 'running' | 'completed' | 'failed' | 'cancelled';
export type JobTrigger = 'manual' | 'schedule' | 'api';
export type LogLevel = 'debug' | 'info' | 'warn' | 'error';

export interface Job {
  id: string;
  pipelineId: string;
  pipelineName: string;
  pipelineVersion: number;
  status: JobStatus;
  progress: number;
  variables: Record<string, unknown>;
  triggeredBy: JobTrigger;
  startedAt?: Date;
  completedAt?: Date;
  duration?: number;
  metrics?: {
    rowsProcessed: number;
    quadsGenerated: number;
  };
  errorMessage?: string;
  errorDetails?: {
    stackTrace?: string;
    context?: Record<string, unknown>;
  };
  outputGraph?: string;
  steps?: JobStep[];
  createdBy: string;
  createdAt: Date;
}

export interface JobStep {
  id: string;
  name: string;
  status: 'pending' | 'running' | 'completed' | 'failed' | 'skipped';
  duration?: number;
  metrics?: {
    rowsProcessed: number;
    quadsGenerated?: number;
  };
  error?: string;
}

export interface JobLog {
  id: string;
  timestamp: Date;
  level: LogLevel;
  step?: string;
  message: string;
  details?: Record<string, unknown>;
}

export interface JobSchedule {
  id: string;
  pipelineId: string;
  cronExpression: string;
  variables: Record<string, unknown>;
  isActive: boolean;
  lastRun?: Date;
  nextRun?: Date;
}

export interface JobMetrics {
  rowsProcessed: number;
  quadsGenerated: number;
  duration: number;
}
