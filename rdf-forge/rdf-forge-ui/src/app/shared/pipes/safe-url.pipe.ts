import type { PipeTransform } from '@angular/core';
import { Pipe } from '@angular/core';
import type { SafeResourceUrl } from '@angular/platform-browser';
import { DomSanitizer } from '@angular/platform-browser';

/**
 * Pipe to bypass security and mark a URL as safe for use in resource URLs.
 * Use with caution - only for trusted content.
 */
@Pipe({
  name: 'safeUrl',
  standalone: true
})
export class SafeUrlPipe implements PipeTransform {
  constructor(private readonly sanitizer: DomSanitizer) {}

  transform(value: string): SafeResourceUrl {
    return this.sanitizer.bypassSecurityTrustResourceUrl(value);
  }
}
