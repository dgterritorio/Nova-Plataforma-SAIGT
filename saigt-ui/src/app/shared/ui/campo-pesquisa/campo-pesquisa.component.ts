import { ChangeDetectionStrategy, Component, DestroyRef, forwardRef, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { Subject, debounceTime } from 'rxjs';

export const ESPERA_PESQUISA_MS = 300;

@Component({
  selector: 'app-campo-pesquisa',
  templateUrl: './campo-pesquisa.component.html',
  styleUrl: './campo-pesquisa.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => CampoPesquisaComponent), multi: true }],
  host: { '[class]': "'campo-pesquisa campo-pesquisa-' + largura()" },
})
export class CampoPesquisaComponent implements ControlValueAccessor {
  readonly placeholder = input('Pesquisar');
  readonly largura = input<'total' | 'cabecalho'>('total');
  readonly pesquisar = output<string>();

  readonly valor = signal('');
  readonly desativado = signal(false);

  private readonly escrito = new Subject<string>();
  private aoMudar: (valor: string) => void = () => undefined;
  private aoTocar: () => void = () => undefined;

  constructor() {
    this.escrito
      .pipe(debounceTime(ESPERA_PESQUISA_MS), takeUntilDestroyed(inject(DestroyRef)))
      .subscribe((termo) => this.pesquisar.emit(termo));
  }

  escrever(valor: string): void {
    this.valor.set(valor);
    this.aoMudar(valor);
    this.escrito.next(valor);
  }

  tocar(): void {
    this.aoTocar();
  }

  writeValue(valor: string | null): void {
    this.valor.set(valor ?? '');
  }

  registerOnChange(fn: (valor: string) => void): void {
    this.aoMudar = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.aoTocar = fn;
  }

  setDisabledState(desativado: boolean): void {
    this.desativado.set(desativado);
  }
}
