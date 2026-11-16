import { Directive, TemplateRef, inject, input } from '@angular/core';

/** `<ng-template appCelula="chave" let-linha>` substitui a célula da coluna com essa chave. */
@Directive({ selector: 'ng-template[appCelula]' })
export class CelulaTabelaDirective {
  readonly chave = input.required<string>({ alias: 'appCelula' });
  readonly template = inject<TemplateRef<{ $implicit: unknown }>>(TemplateRef);
}
