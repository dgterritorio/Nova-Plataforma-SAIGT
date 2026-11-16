import { ChangeDetectionStrategy, Component, ElementRef, Injector, afterNextRender, effect, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import {
  InstrumentoAbrangido,
  ItemTerritorio,
  ModalidadeRatificacao,
  ROTULO_MODALIDADE_RATIFICACAO,
  TerritorioInstrumento,
} from '../../../models/processo.model';
import { MENSAGEM_CAMPO_BLOQUEADO } from '../informacao-base';
import { DesbloquearCampoComponent } from '../desbloquear-campo/desbloquear-campo.component';
import { rotuloConcelhos } from '../processo-formulario-inicial/processo-formulario-inicial.component';

export const CAMPO_MODALIDADE_RATIFICACAO = 'modalidadeRatificacao';

let proximoId = 0;

/**
 * Formulário fixo `RATIFICACAO`: a secção «Ratificação», com o Instrumento a ratificar só de leitura (escolhido no
 * assistente) e o Tipo de procedimento, e por baixo o cabeçalho comum da Informação Geral, que o ecrã projeta. O
 * cartão projetado continua no ecrã que o declara; o ecrã junta `dados()` deste formulário ao `dados()` do cartão.
 */
@Component({
  selector: 'app-formulario-ratificacao',
  standalone: true,
  imports: [DesbloquearCampoComponent, ReactiveFormsModule],
  templateUrl: './formulario-ratificacao.component.html',
  styleUrl: './formulario-ratificacao.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FormularioRatificacaoComponent {
  private readonly elemento = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  /** Nome do tipo do Instrumento a ratificar (PDM ou PDIM). */
  readonly tipoInstrumento = input<string | null>(null);
  readonly territorio = input<TerritorioInstrumento | null>(null);
  readonly designacao = input<string | null>(null);
  readonly instrumentosAbrangidos = input<InstrumentoAbrangido[] | null>(null);
  /** A modalidade gravada; repõe a escolha quando muda. */
  readonly valorInicial = input<ModalidadeRatificacao | null | undefined>(null);
  /** `camposBloqueados` do processo traz `modalidadeRatificacao`. */
  readonly bloqueado = input(false);
  readonly aCarregar = input(false);
  /** Erro 400 do servidor para `modalidadeRatificacao`, vindo do ecrã. */
  readonly erroServidor = input<string | null>(null);
  /** No assistente, a secção só aparece na página do cabeçalho; o cabeçalho projetado decide o resto. */
  readonly seccaoVisivel = input(true);

  readonly opcoes = (Object.keys(ROTULO_MODALIDADE_RATIFICACAO) as ModalidadeRatificacao[]).map((valor) => ({
    valor,
    rotulo: ROTULO_MODALIDADE_RATIFICACAO[valor],
  }));
  readonly mensagemBloqueado = MENSAGEM_CAMPO_BLOQUEADO;
  private readonly sufixo = ++proximoId;
  readonly idTitulo = `ratificacao-titulo-${this.sufixo}`;
  readonly idErro = `erro-modalidade-ratificacao-${this.sufixo}`;
  readonly idBloqueado = `bloqueado-modalidade-ratificacao-${this.sufixo}`;
  readonly nomeGrupo = `modalidade-ratificacao-${this.sufixo}`;

  readonly controlo = new FormControl<ModalidadeRatificacao | null>(null);
  private readonly valor = toSignal(this.controlo.valueChanges, { initialValue: this.controlo.value });
  private readonly erroLocal = signal<string | null>(null);

  constructor() {
    effect(() => this.controlo.setValue(this.valorInicial() ?? null));
    effect(() => this.erroLocal.set(this.erroServidor()));
    effect(() => {
      if (this.bloqueado() || this.aCarregar()) {
        this.controlo.disable({ emitEvent: false });
      } else {
        this.controlo.enable({ emitEvent: false });
      }
    });
    this.controlo.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.erroLocal.set(null));
  }

  erro(): string | null {
    return this.erroLocal();
  }

  descricao(): string | null {
    const ids = [this.bloqueado() ? this.idBloqueado : null, this.erro() ? this.idErro : null].filter(Boolean);
    return ids.length > 0 ? ids.join(' ') : null;
  }

  rotuloNuts(): string {
    return this.rotulo(this.territorio()?.nutsII);
  }

  /** O concelho do PDM ou os concelhos do PDIM. */
  rotuloConcelhos(): string {
    return rotuloConcelhos(this.territorio()?.concelhos);
  }

  variosConcelhos(): boolean {
    return (this.territorio()?.concelhos.length ?? 0) > 1;
  }

  /** A do Instrumento abrangido, que na Ratificação é um só; sem ele, a do processo. */
  rotuloDesignacao(): string {
    return this.instrumentosAbrangidos()?.[0]?.designacao ?? this.designacao() ?? '—';
  }

  modalidade(): ModalidadeRatificacao | null {
    return this.valor() ?? null;
  }

  /** Para juntar ao pedido de gravação do ecrã; desativado (bloqueado) conta na mesma. */
  dados(): { modalidadeRatificacao: ModalidadeRatificacao | null } {
    return { modalidadeRatificacao: this.controlo.getRawValue() };
  }

  /** Depois de uma recusa do servidor neste campo: a mensagem fica junto do grupo e o foco vai para ele. */
  aplicarErro(mensagem: string): void {
    this.erroLocal.set(mensagem);
    // Depois da gravação: as opções voltam a estar ativas só no ciclo seguinte.
    afterNextRender(
      () => {
        const opcoes = this.elemento.nativeElement.querySelectorAll<HTMLInputElement>('input[type="radio"]');
        (Array.from(opcoes).find((opcao) => opcao.checked) ?? opcoes[0])?.focus();
      },
      { injector: this.injector },
    );
  }

  private rotulo(itens: ItemTerritorio[] | undefined): string {
    return itens?.length ? itens.map((item) => item.nome ?? item.codigo).join(', ') : '—';
  }
}
