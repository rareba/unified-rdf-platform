import { Injectable } from '@angular/core';
import Keycloak, { KeycloakProfile } from 'keycloak-js';
import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private keycloak: Keycloak | undefined;
  private _isAuthenticated = false;
  private _userProfile: KeycloakProfile | undefined;

  async init(): Promise<boolean> {
    if (!environment.auth.enabled) {
      this._isAuthenticated = true;
      this._userProfile = {
        username: 'offline-user',
        firstName: 'Offline',
        lastName: 'User',
        email: 'offline@local'
      };
      return true;
    }

    if (environment.auth.keycloak) {
      this.keycloak = new Keycloak({
        url: environment.auth.keycloak.url,
        realm: environment.auth.keycloak.realm,
        clientId: environment.auth.keycloak.clientId
      });

      try {
        const authenticated = await this.keycloak.init({
          onLoad: 'login-required',
          checkLoginIframe: false
        });

        this._isAuthenticated = authenticated;

        if (authenticated) {
          this._userProfile = await this.keycloak.loadUserProfile();
        }

        return authenticated;
      } catch (error) {
        console.error('Failed to initialize Keycloak', error);
        return false;
      }
    }

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