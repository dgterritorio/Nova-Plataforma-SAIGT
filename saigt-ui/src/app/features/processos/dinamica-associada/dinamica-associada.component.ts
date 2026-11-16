import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  computed,
  effect,
  forwardRef,
  inject,
  input,
  signal,
  untracked,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ControlValueAccessor, FormControl, NG_VALUE_ACCESSOR, ReactiveFormsModule } from '@angular/forms';
import { ApiError } from '../../../core/models/error-response.model';
import { DinamicaAssociavel, RespostaSimultaneoPmot } from '../../../models/processo.model';
import { ProcessoService } from '../../../services/processo.service';
import { ErrorBannerComponent } from '../../../shared/components/error-banner.component';
import { AlertaComponent } from '../../../shared/ui/alerta/alerta.component';
import { BotaoComponent } from '../../../shared/ui/botao/botao.component';
import { OpcaoSeletor, SeletorComponent } from '../../../shared/ui/seletor/seletor.component';

export const TAMANHO_PAGINA_DINAMICAS = 20;
export const PERGUNTA_SIMULTANEO_PMOT = 'Processo em simultâneo com a formação de PMOT';
export const AJUDA_DINAMICA_ASSOCIADA =
  'A REN deve ser publicada antes do IGT associado. A associação não impede o IGT de seguir para publicação.';
export const MENSAGEM_SEM_DINAMICAS = 'Não há dinâmicas de PDM, PP, PU ou PEOT em curso num concelho desta REN.';
export const MENSAGEM_DINAMICA_INDISPONIVEL =
  'A dinâmica associada já não está disponível. Escolha outra ou retire a associação antes de submeter.';

/** `<numeroDinamica> · <tipoDinamica> · <descritivo> — <designação> (<tipo>)`, com «(caducada)» no fim quando caducou. */
export function rotuloDinamicaAssociavel(dinamica: DinamicaAssociavel): string {
  const instrumento = dinamica.instrumento ? ` — ${dinamica.instrumento.designacao} (${dinamica.instrumento.tipoInstrumento})` : '';
  const caducada = dinamica.estado === 'CADUCADA' ? ' (caducada)' : '';
  return `${dinamica.numeroDinamica} · ${dinamica.tipoDinamica} · ${dinamica.descritivo}${instrumento}${caducada}`;
}

let proximoId = 0;

/**
 * Pergunta se o processo da REN corre em simultâneo com a formação de um PMOT e, com «Sim», a Dinâmica do IGT em
 * curso. O valor do controlo é o `dinamicaId` escolhido, ou `null`: a associação é opcional e nunca bloqueia.
 */
@Component({
  selector: 'app-dinamica-associada',
  standalone: true,
  imports: [AlertaComponent, BotaoComponent, ErrorBannerComponent, ReactiveFormsModule, SeletorComponent],
  templateUrl: './dinamica-associada.component.html',
  styleUrl: './dinamica-associada.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => DinamicaAssociadaComponent), multi: true }],
})
export class DinamicaAssociadaComponent implements ControlValueAccessor {
  private readonly processoService = inject(ProcessoService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly elemento = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly processoId = input.required<string>();
  readonly dinamicaInicial = input<DinamicaAssociavel | null>(null);
  /** A resposta gravada em `dadosFormulario.simultaneoComPmot`, se houver. */
  readonly respostaInicial = input<RespostaSimultaneoPmot | null>(null);
  readonly desativado = input(false);
  /** Recusa do servidor (`DINAMICA_ASSOCIADA_INVALIDA`), mostrada junto do seletor. */
  readonly erro = input<string | null>(null);

  readonly pergunta = PERGUNTA_SIMULTANEO_PMOT;
  readonly ajuda = AJUDA_DINAMICA_ASSOCIADA;
  readonly mensagemSemDinamicas = MENSAGEM_SEM_DINAMICAS;
  readonly mensagemIndisponivel = MENSAGEM_DINAMICA_INDISPONIVEL;
  readonly id = `dinamica-associada-${++proximoId}`;
  readonly idAjuda = `${this.id}-ajuda`;
  readonly idErro = `${this.id}-erro`;
  readonly idVazia = `${this.id}-vazia`;
  readonly idIndisponivel = `${this.id}-indisponivel`;

  readonly controloSeletor = new FormControl<string | null>(null);
  readonly valor = signal<string | null>(null);
  private readonly respostaEscolhida = signal<RespostaSimultaneoPmot | null>(null);
  private readonly desativadoPeloFormulario = signal(false);
  readonly bloqueado = computed(() => this.desativado() || this.desativadoPeloFormulario());

  /** Sem escolha nesta edição: «Sim» com uma Dinâmica gravada; sem ela, a resposta gravada, e «Não» por omissão. */
  readonly resposta = computed<RespostaSimultaneoPmot>(
    () =>
      this.respostaEscolhida() ??
      (this.valor() !== null || this.dinamicaInicial() !== null ? 'SIM' : (this.respostaInicial() ?? 'NAO')),
  );

  readonly dinamicas = signal<DinamicaAssociavel[]>([]);
  /** Última página lida; -1 antes do primeiro pedido. */
  readonly pagina = signal(-1);
  readonly totalPaginas = signal(0);
  readonly aCarregar = signal(false);
  readonly erroLista = signal<ApiError | null>(null);

  readonly carregada = computed(() => this.pagina() >= 0);
  readonly temMais = computed(() => this.pagina() + 1 < this.totalPaginas());
  readonly listaVazia = computed(() => this.carregada() && this.dinamicas().length === 0 && this.erroLista() === null);
  /** Só se sabe que saiu da lista depois de lidas todas as páginas. */
  readonly indisponivel = computed(() => {
    const valor = this.valor();
    return (
      valor !== null && this.carregada() && !this.temMais() && !this.dinamicas().some((dinamica) => dinamica.dinamicaId === valor)
    );
  });

  readonly opcoes = computed<OpcaoSeletor[]>(() => {
    const opcoes = this.dinamicas().map((dinamica) => ({ valor: dinamica.dinamicaId, rotulo: rotuloDinamicaAssociavel(dinamica) }));
    const valor = this.valor();
    if (valor !== null && !opcoes.some((opcao) => opcao.valor === valor)) {
      const inicial = this.dinamicaInicial();
      opcoes.push({
        valor,
        rotulo: inicial?.dinamicaId === valor ? rotuloDinamicaAssociavel(inicial) : 'Dinâmica associada',
      });
    }
    return opcoes;
  });

  readonly descritoPorSeletor = computed(
    () => [this.listaVazia() ? this.idVazia : null, this.indisponivel() ? this.idIndisponivel : null].filter(Boolean).join(' ') || null,
  );

  readonly descritoPorPergunta = computed(() => {
    const ids = [this.resposta() === 'NAO' ? this.idAjuda : null, this.resposta() === 'NAO' && this.erro() ? this.idErro : null];
    return ids.filter(Boolean).join(' ') || null;
  });

  private aoMudar: (valor: string | null) => void = () => undefined;
  private aoTocar: () => void = () => undefined;

  /** O formulário bloqueado mostra só a associação gravada: não pede a lista. */
  private readonly pedirPrimeiraPagina = effect(() => {
    if (this.resposta() === 'SIM' && !this.bloqueado() && !this.carregada() && !untracked(this.aCarregar) && !untracked(this.erroLista)) {
      untracked(() => this.carregar(0));
    }
  });

  private readonly aplicarBloqueio = effect(() => {
    if (this.bloqueado()) {
      this.controloSeletor.disable({ emitEvent: false });
    } else {
      this.controloSeletor.enable({ emitEvent: false });
    }
  });

  constructor() {
    this.controloSeletor.valueChanges.pipe(takeUntilDestroyed()).subscribe((valor) => {
      this.valor.set(valor);
      this.aoMudar(valor);
      this.aoTocar();
    });
  }

  writeValue(valor: string | null): void {
    const normalizado = valor || null;
    this.valor.set(normalizado);
    this.controloSeletor.setValue(normalizado, { emitEvent: false });
  }

  registerOnChange(fn: (valor: string | null) => void): void {
    this.aoMudar = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.aoTocar = fn;
  }

  setDisabledState(desativado: boolean): void {
    this.desativadoPeloFormulario.set(desativado);
  }

  /** «Não» retira a associação: o valor passa a `null`. */
  responder(resposta: RespostaSimultaneoPmot): void {
    if (this.bloqueado()) {
      return;
    }
    this.respostaEscolhida.set(resposta);
    if (resposta === 'NAO' && this.valor() !== null) {
      this.valor.set(null);
      this.controloSeletor.setValue(null, { emitEvent: false });
      this.aoMudar(null);
    }
    this.aoTocar();
  }

  mostrarMais(): void {
    this.carregar(this.pagina() + 1);
  }

  tentarNovamente(): void {
    this.carregar(this.pagina() + 1);
  }

  focar(): void {
    const raiz = this.elemento.nativeElement;
    (raiz.querySelector<HTMLElement>('select:not(:disabled)') ?? raiz.querySelector<HTMLElement>('input:not(:disabled)'))?.focus();
  }

  private carregar(pagina: number): void {
    this.aCarregar.set(true);
    this.erroLista.set(null);
    this.processoService
      .dinamicasAssociaveis(this.processoId(), pagina, TAMANHO_PAGINA_DINAMICAS)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (resposta) => {
          this.dinamicas.update((atuais) => (pagina === 0 ? resposta.content : [...atuais, ...resposta.content]));
          this.pagina.set(pagina);
          this.totalPaginas.set(resposta.totalPages);
          this.aCarregar.set(false);
        },
        error: (erro: ApiError) => {
          this.erroLista.set(erro);
          this.aCarregar.set(false);
        },
      });
  }
}
