import { Injectable, inject } from '@angular/core';
import Keycloak, { KeycloakProfile } from 'keycloak-js';
import { environment } from '../../../environments/environment';
import { LoggerService } from './logger.service';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly logger = inject(LoggerService);
  private keycloak: Keycloak | undefined;
  private _isAuthenticated = false;
  private _userProfile: KeycloakProfile | undefined;
  private _initPromise: Promise<boolean> | undefined;
  private _initialized = false;

  async init(): Promise<boolean> {
    // Return cached result if already initialized
    if (this._initialized) {
      return this._isAuthenticated;
    }

    // Return existing promise if initialization is in progress
    if (this._initPromise) {
      return this._initPromise;
    }

    this._initPromise = this._doInit();
    return this._initPromise;
  }

  private async _doInit(): Promise<boolean> {
    if (!environment.auth.enabled) {
      this._isAuthenticated = true;
      this._initialized = true;
      this._userProfile = {
        username: 'offline-user',
        firstName: 'Offline',
        lastName: 'User',
        email: 'offline@local'
      };
      return true;
    }

    if (environment.auth.keycloak) {
      // Only create Keycloak instance once
      if (!this.keycloak) {
        this.keycloak = new Keycloak({
          url: environment.auth.keycloak.url,
          realm: environment.auth.keycloak.realm,
          clientId: environment.auth.keycloak.clientId
        });
      }

      try {
        // Use check-sso to process any callback or check existing session
        // without automatically redirecting to login
        const authenticated = await this.keycloak.init({
          onLoad: 'check-sso',
          checkLoginIframe: false,
          silentCheckSsoRedirectUri: undefined
        });

        this._isAuthenticated = authenticated;
        this._initialized = true;

        if (authenticated) {
          // Try to load user profile, but don't fail auth if it fails
          try {
            this._userProfile = await this.keycloak.loadUserProfile();
          } catch (profileError) {
            this.logger.warn('Failed to load user profile, using token claims instead', profileError);
            // Extract basic profile from token if available
            const tokenParsed = this.keycloak.tokenParsed;
            if (tokenParsed) {
              this._userProfile = {
                username: tokenParsed['preferred_username'],
                firstName: tokenParsed['given_name'],
                lastName: tokenParsed['family_name'],
                email: tokenParsed['email']
              };
            }
          }
          // Clear the URL hash after successful auth
          if (window.location.hash) {
            window.history.replaceState(null, '', window.location.pathname);
          }
        } else {
          // Not authenticated - redirect to login
          this.keycloak.login();
          // Return a promise that never resolves since we're redirecting
          return new Promise(() => {});
        }

        return authenticated;
      } catch (error) {
        this.logger.error('Failed to initialize Keycloak', error);
        this._initialized = true;
        return false;
      }
    }

    this._initialized = true;
    return false;
  }

  login(): void {
    this.keycloak?.login();
  }

  logout(): void {
    this.keycloak?.logout();
  }

  getToken(): string | undefined {
    return this.keycloak?.token;
  }

  /**
   * Get token with automatic refresh. If the token expires within the given
   * number of seconds, it will be refreshed before returning.
   * On refresh failure, the user is redirected to the login page.
   *
   * @param minValidity Minimum remaining validity in seconds (default: 30)
   * @returns A promise that resolves with the token string, or undefined
   */
  async getTokenRefreshed(minValidity = 30): Promise<string | undefined> {
    if (!this.keycloak || !environment.auth.enabled) {
      return this.keycloak?.token;
    }

    try {
      const refreshed = await this.keycloak.updateToken(minValidity);
      if (refreshed) {
        console.debug('Token was refreshed');
      }
      return this.keycloak.token;
    } catch (error) {
      this.logger.error('Failed to refresh token, redirecting to login', error);
      this._isAuthenticated = false;
      this.keycloak.login();
      return undefined;
    }
  }

  get isAuthenticated(): boolean {
    return this._isAuthenticated;
  }

  get userProfile(): KeycloakProfile | undefined {
    return this._userProfile;
  }

  hasRole(role: string): boolean {
    return this.keycloak?.hasRealmRole(role) ?? false;
  }

  isAdmin(): boolean {
    // In offline mode, grant admin access for development
    if (!environment.auth.enabled) {
      return true;
    }
    return this.hasRole('admin');
  }
}