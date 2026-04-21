import type { PipeTransform } from '@angular/core';
import { Pipe } from '@angular/core';
import type { SafeResourceUrl } from '@angular/platform-browser';
import { DomSanitizer } from '@angular/platform-browser';

/**
 * Pipe to bypass security and mark a URL as safe for use in resource URLs.
 * Use with caution - only for trusted content.
 *
 * Applies a regex allowlist: only http(s), absolute paths, and blob: URIs
 * are accepted. Rejects data:, javascript:, file:, and other unsafe schemes.
 */
@Pipe({
  name: 'safeUrl',
  standalone: true
})
export class SafeUrlPipe implements PipeTransform {
  constructor(private readonly sanitizer: DomSanitizer) {}

  transform(value: string): SafeResourceUrl | null {
    if (!value) return null;
    const trimmed = value.trim();
    // Allow http(s) URLs, absolute paths, and blob: URIs only — reject data: / javascript: / etc.
    if (!/^(https?:\/\/|\/|blob:)/i.test(trimmed)) {
      console.warn('SafeUrlPipe rejected unsafe URL:', trimmed);
      return null;
    }
    return this.sanitizer.bypassSecurityTrustResourceUrl(trimmed);
  }
}
