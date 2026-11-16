import { ChangeDetectionStrategy, Component, computed, forwardRef, input, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

export type TipoCampoTexto = 'text' | 'email' | 'tel' | 'number' | 'date' | 'password';

let proximoId = 0;

/**
 * `multilinha` troca o `<input>` por `<textarea>`. A ajuda e o erro ficam ligados por `aria-describedby`.
 * Um elemento com o atributo `sufixo` (por exemplo, o botão de mostrar a palavra-passe) fica dentro do campo, à direita.
 */
@Component({
  selector: 'app-campo-texto',
  templateUrl: './campo-texto.component.html',
  styleUrl: './campo-texto.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[class.campo-texto-compacto]': 'compacto()',
    '[class.campo-texto-preenchido]': 'preenchido()',
    '[class.campo-texto-rotulo-maiusculas]': 'rotuloMaiusculas()',
    '[class.campo-texto-ajuda-ao-lado]': 'ajudaAoLado()',
  },
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => CampoTextoComponent), multi: true }],
})
export class CampoTextoComponent implements ControlValueAccessor {
  readonly rotulo = input.required<string>();
  readonly obrigatorio = input(false);
  readonly ajuda = input<string | null>(null);
  readonly erro = input<string | null>(null);
  readonly placeholder = input('');
  readonly tipo = input<TipoCampoTexto>('text');
  readonly multilinha = input(false);
  readonly compacto = input(false);
  /** Fundo cinza claro, como os campos do login. */
  readonly preenchido = input(false);
  /** Rótulo pequeno em maiúsculas, como os campos do perfil. */
  readonly rotuloMaiusculas = input(false);
  readonly ajudaAoLado = input(false);
  readonly maxlength = input<number | null>(null);
  readonly autocomplete = input<string | null>(null);
  /** `inputmode` do `<input>`: o teclado do telemóvel (ex.: `numeric` para números com zeros à esquerda). */
  readonly modoEntrada = input<string | null>(null);

  readonly id = `campo-texto-${++proximoId}`;
  readonly idAjuda = `${this.id}-ajuda`;
  readonly idErro = `${this.id}-erro`;
  readonly valor = signal('');
  readonly desativado = signal(false);

  readonly descritoPor = computed(
    () => [this.ajuda() ? this.idAjuda : null, this.erro() ? this.idErro : null].filter(Boolean).join(' ') || null,
  );

  private aoMudar: (valor: string) => void = () => undefined;
  private aoTocar: () => void = () => undefined;

  writeValue(valor: string | number | null): void {
    this.valor.set(valor === null || valor === undefined ? '' : String(valor));
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

  escrever(evento: Event): void {
    const valor = (evento.target as HTMLInputElement | HTMLTextAreaElement).value;
    this.valor.set(valor);
    this.aoMudar(valor);
  }

  tocar(): void {
    this.aoTocar();
  }
}
