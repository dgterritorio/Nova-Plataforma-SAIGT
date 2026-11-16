import { ChangeDetectionStrategy, Component, computed, forwardRef, input, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

export interface OpcaoSeletor {
  valor: string;
  rotulo: string;
  /** Opções seguidas com o mesmo grupo ficam num `<optgroup>` com este rótulo. */
  grupo?: string;
}

interface GrupoSeletor {
  rotulo: string | null;
  opcoes: OpcaoSeletor[];
}

let proximoId = 0;

/** `<select>` nativo: o teclado, o leitor de ecrã e o telemóvel já sabem usá-lo. Sem escolha, o valor é `null`. */
@Component({
  selector: 'app-seletor',
  templateUrl: './seletor.component.html',
  styleUrl: './seletor.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[class.seletor-compacto]': 'compacto()' },
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => SeletorComponent), multi: true }],
})
export class SeletorComponent implements ControlValueAccessor {
  readonly rotulo = input.required<string>();
  readonly opcoes = input.required<readonly OpcaoSeletor[]>();
  readonly placeholder = input('Selecione…');
  readonly obrigatorio = input(false);
  readonly erro = input<string | null>(null);
  readonly ajuda = input<string | null>(null);
  /** Rótulo pequeno e campo de 36px, como nos modais (UI-069). */
  readonly compacto = input(false);
  /** O `placeholder` passa a opção escolhível que devolve `null` (campo opcional ou filtro «Todos»). */
  readonly permitirVazio = input(false);
  /** Rótulo só para o leitor de ecrã, quando a primeira opção já diz o que o campo é (filtros). */
  readonly rotuloOculto = input(false);
  /** Nome acessível mais completo que o rótulo visível, quando o mesmo campo se repete por linha (ex.: "Grupo da condição 2 do grupo 1"). */
  readonly rotuloAria = input<string | null>(null);
  /** Ids de avisos fora do componente que também descrevem o campo (ex.: um alerta por baixo dele). */
  readonly descritoPorAdicional = input<string | null>(null);

  readonly id = `seletor-${++proximoId}`;
  readonly idErro = `${this.id}-erro`;
  readonly idAjuda = `${this.id}-ajuda`;
  readonly valor = signal<string | null>(null);
  readonly desativado = signal(false);
  readonly temErro = computed(() => !!this.erro());
  readonly grupos = computed<GrupoSeletor[]>(() => {
    const grupos: GrupoSeletor[] = [];
    for (const opcao of this.opcoes()) {
      const rotulo = opcao.grupo ?? null;
      const ultimo = grupos.at(-1);
      if (ultimo && ultimo.rotulo === rotulo) {
        ultimo.opcoes.push(opcao);
      } else {
        grupos.push({ rotulo, opcoes: [opcao] });
      }
    }
    return grupos;
  });
  readonly descritoPor = computed(
    () =>
      [this.ajuda() ? this.idAjuda : null, this.temErro() ? this.idErro : null, this.descritoPorAdicional()].filter(Boolean).join(' ') ||
      null,
  );

  private aoMudar: (valor: string | null) => void = () => undefined;
  private aoTocar: () => void = () => undefined;

  writeValue(valor: string | number | null): void {
    this.valor.set(valor === null || valor === undefined || valor === '' ? null : String(valor));
  }

  registerOnChange(fn: (valor: string | null) => void): void {
    this.aoMudar = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.aoTocar = fn;
  }

  setDisabledState(desativado: boolean): void {
    this.desativado.set(desativado);
  }

  escolher(evento: Event): void {
    const valor = (evento.target as HTMLSelectElement).value || null;
    this.valor.set(valor);
    this.aoMudar(valor);
  }

  tocar(): void {
    this.aoTocar();
  }
}
