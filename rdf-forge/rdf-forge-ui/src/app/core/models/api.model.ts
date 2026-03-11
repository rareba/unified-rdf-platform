/** Generic API response wrapper */
export interface ApiResponse<T> {
  data?: T;
  success: boolean;
  error?: string;
  message?: string;
}
