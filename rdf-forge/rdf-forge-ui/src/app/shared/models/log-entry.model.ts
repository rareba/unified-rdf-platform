export type LogLevel = 'DEBUG' | 'INFO' | 'WARN' | 'ERROR';

export interface LogEntry {
  timestamp: Date;
  level: LogLevel;
  message: string;
  source?: string;
  thread?: string;
  logger?: string;
}

export interface LogFilter {
  levels: LogLevel[];
  searchTerm: string;
  source?: string;
  timeRange?: {
    from: Date;
    to: Date;
  };
}

export const LOG_LEVEL_ORDER: Record<LogLevel, number> = {
  'DEBUG': 0,
  'INFO': 1,
  'WARN': 2,
  'ERROR': 3
};

export const LOG_LEVEL_COLORS: Record<LogLevel, string> = {
  'DEBUG': '#6c757d',
  'INFO': '#0d6efd',
  'WARN': '#fd7e14',
  'ERROR': '#dc3545'
};

export function parseLogLevel(level: string): LogLevel {
  const upperLevel = level.toUpperCase();
  if (upperLevel === 'DEBUG' || upperLevel === 'INFO' || 
      upperLevel === 'WARN' || upperLevel === 'ERROR') {
    return upperLevel as LogLevel;
  }
  return 'INFO';
}

export function formatLogEntry(entry: LogEntry): string {
  const timestamp = entry.timestamp.toISOString();
  return `[${timestamp}] [${entry.level}] ${entry.message}`;
}

export function downloadLogsAsText(entries: LogEntry[], filename: string): void {
  const content = entries.map(formatLogEntry).join('\n');
  const blob = new Blob([content], { type: 'text/plain;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}
