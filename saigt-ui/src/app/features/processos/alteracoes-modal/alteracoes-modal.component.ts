import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Observable, map } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { ApiError } from '../../../core/models/error-response.model';
import { AcaoProcesso, Processo, ProcessoDetalhe } from '../../../models/processo.model';
import { Utilizador } from '../../../models/utilizador.model';
import { ProcessoService } from '../../../services/processo.service';
import { UtilizadorService } from '../../../services/utilizador.service';
import { ErrorBannerComponent } from '../../../shared/components/error-banner.component';
import { EstadoProcessoBadgeComponent, rotuloEstado } from '../../../shared/components/estado-processo-badge.component';
import { BotaoComponent } from '../../../shared/ui/botao/botao.component';
import { LarguraModal, ModalComponent } from '../../../shared/ui/modal/modal.component';
import { OpcaoSeletor, SeletorComponent } from '../../../shared/ui/seletor/seletor.component';

/**
 * Ações de "Alterar estado", cada uma com o estado que o backend lhe dá como destino. Nunca `enviar-pcm`: não muda o
 * estado e tem ponto de entrada próprio, no Resumo Validados.
 */
const ESTADO_DESTINO_DA_ACAO = {
  capturar: 'EM_VALIDACAO_DGT',
  validar: 'VALIDADO_DGT',
  'enviar-incm': 'EM_VALIDACAO_INCM',
} as const satisfies Partial<Record<AcaoProcesso, string>>;

type AcaoTransicao = keyof typeof ESTADO_DESTINO_DA_ACAO;

interface TransicaoEstado {
  estadoDestino: string;
  acao: AcaoTransicao;
}
/** Uma página chega: são os utilizadores de uma só entidade. */
const TAMANHO_PAGINA_CANDIDATOS = 100;

/** A mesma mensagem do 409 do backend, para o Master ver logo porque não pode transferir. */
const MSG_OWNER_NAO_TRANSFERIVEL = 'O processo não pode ser transferido neste estado.';

/** A resposta dos `PATCH` é só `ProcessoResponse`; a reatribuição junta o nome do novo autor. */
export type ProcessoAlterado = Processo & Partial<Pick<ProcessoDetalhe, 'autorNome'>>;

export type AcaoAlteracao = 'OWNER' | 'ESTADO' | 'ANULAR';

interface CartaoAlteracao {
  acao: AcaoAlteracao;
  titulo: string;
  contexto: string;
  /** Preenchido quando o estado não permite a ação: o cartão aparece desativado. A falta de perfil esconde-o. */
  motivoDesativado: string | null;
}

function alternar(controlo: FormControl, desativado: boolean): void {
  if (desativado) {
    controlo.disable({ emitEvent: false });
  } else {
    controlo.enable({ emitEvent: false });
  }
}

/**
 * Ações da DGT sobre o Processo, em dois passos: escolher um cartão e Confirmar abre o sub-ecrã da ação.
 * O botão primário diz "Confirmar": o "Criar notificação" do Figma foi copiado de outro ecrã.
 * Como no Figma, os cartões abrem a 672px (UI-068) e os sub-ecrãs a 503px (UI-069).
 *
 * Um 409 em qualquer sub-ecrã mostra a mensagem do backend e oferece Recarregar, sem repetir a ação.
 *
 * Os candidatos a novo owner são os utilizadores com vínculo à entidade do Instrumento
 * (`GET /utilizadores?entidadeId=`), nunca quem só atuou sobre ela. A transferência não leva motivo.
 * Cartões e transições vêm de `acoesDisponiveis`: o ecrã não deduz do estado nem do perfil o que o backend recusa.
 * Só o Alterar Owner aparece desativado ao Master fora dos estados em que o owner age, com a razão.
 */
@Component({
  selector: 'app-alteracoes-modal',
  imports: [BotaoComponent, ErrorBannerComponent, EstadoProcessoBadgeComponent, ModalComponent, ReactiveFormsModule, SeletorComponent],
  templateUrl: './alteracoes-modal.component.html',
  styleUrl: './alteracoes-modal.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AlteracoesModalComponent {
  private readonly processoService = inject(ProcessoService);
  private readonly utilizadorService = inject(UtilizadorService);
  private readonly authService = inject(AuthService);
  private readonly elemento = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  readonly aberto = input(false);
  readonly processo = input.required<ProcessoDetalhe>();

  readonly fechar = output<void>();
  /** O detalhe funde a resposta no `ProcessoDetalhe` e fecha o modal. */
  readonly alterado = output<ProcessoAlterado>();
  /** Pedido depois de um 409. */
  readonly recarregar = output<void>();
  /** A `versao` enviada já não é a do processo: o detalhe mostra o aviso, fecha o modal e relê o processo. */
  readonly desatualizado = output<ApiError>();

  /** Cartão escolhido no primeiro passo, ainda não confirmado. */
  readonly acaoSelecionada = signal<AcaoAlteracao | null>(null);
  /** Sub-ecrã em que o modal está, depois de Confirmar; nulo no passo dos cartões. */
  readonly acaoAberta = signal<AcaoAlteracao | null>(null);

  readonly transicaoEscolhida = signal<TransicaoEstado | null>(null);
  readonly motivoAnulacao = signal('');
  /** Nulo até o sub-ecrã Alterar Owner os carregar. Sem o autor atual. */
  readonly candidatos = signal<Utilizador[] | null>(null);
  readonly falhaCandidatos = signal(false);
  readonly novoOwnerId = signal<string | null>(null);
  readonly aProcessar = signal(false);
  readonly erro = signal<ApiError | null>(null);

  readonly controloEstado = new FormControl<string | null>(null);
  readonly controloOwner = new FormControl<string | null>(null);

  readonly largura = computed<LarguraModal>(() => (this.acaoAberta() === null ? 'lg' : 'md'));

  readonly cartoes = computed<CartaoAlteracao[]>(() => {
    const processo = this.processo();
    const acoes = processo.acoesDisponiveis;
    const cartoes: CartaoAlteracao[] = [];
    const podeReatribuir = acoes.includes('reatribuir-autor');
    if (podeReatribuir || this.authService.perfilInterno() === 'MASTER') {
      cartoes.push({
        acao: 'OWNER',
        titulo: 'Alterar Owner',
        contexto: `Owner atual: ${processo.autorNome}`,
        motivoDesativado: podeReatribuir ? null : MSG_OWNER_NAO_TRANSFERIVEL,
      });
    }
    cartoes.push({
      acao: 'ESTADO',
      titulo: 'Alterar estado',
      contexto: `Estado atual: ${rotuloEstado(processo.estado)}`,
      motivoDesativado: this.opcoesEstado().length > 0 ? null : 'Sem transições disponíveis no estado atual.',
    });
    if (acoes.includes('anular')) {
      cartoes.push({
        acao: 'ANULAR',
        titulo: 'Anular processo',
        contexto: 'Ação irreversível. Requer motivo obrigatório.',
        motivoDesativado: null,
      });
    }
    return cartoes;
  });

  readonly estadoAtual = computed(() => this.processo().estado);
  readonly ownerAtual = computed(() => this.processo().autorNome);
  readonly semCandidatos = computed(() => this.candidatos()?.length === 0);
  readonly opcoesEstado = computed<TransicaoEstado[]>(() => {
    const acoes = this.processo().acoesDisponiveis;
    return (Object.keys(ESTADO_DESTINO_DA_ACAO) as AcaoTransicao[])
      .filter((acao) => acoes.includes(acao))
      .map((acao) => ({ acao, estadoDestino: ESTADO_DESTINO_DA_ACAO[acao] }));
  });
  /** Os 409 chegam como `ESTADO_INVALIDO`: o `ApiError` não traz o código HTTP. */
  readonly conflito = computed(() => this.erro()?.codigo === 'ESTADO_INVALIDO');
  /** O 400 de um novo owner sem vínculo à entidade fica junto ao seletor (`aria-describedby`). */
  readonly erroNovoOwner = computed(
    () => this.erro()?.campos?.find((campo) => campo.path === 'novoUtilizadorId')?.erro ?? null,
  );

  readonly opcoesSeletorEstado = computed<OpcaoSeletor[]>(() =>
    this.opcoesEstado().map((t) => ({ valor: t.estadoDestino, rotulo: rotuloEstado(t.estadoDestino) })),
  );
  readonly opcoesSeletorOwner = computed<OpcaoSeletor[]>(() =>
    (this.candidatos() ?? []).map((c) => ({ valor: c.id, rotulo: `${c.nome ?? '—'} · ${c.email ?? '—'}` })),
  );
  readonly placeholderOwner = computed(() =>
    this.candidatos() !== null ? 'Selecione…' : this.falhaCandidatos() ? 'Indisponível' : 'A carregar…',
  );

  readonly podeConfirmar = computed(() => {
    switch (this.acaoAberta()) {
      case null:
        return this.acaoSelecionada() !== null;
      case 'ESTADO':
        return this.transicaoEscolhida() !== null && !this.aProcessar() && !this.conflito();
      case 'ANULAR':
        return this.motivoAnulacao().trim() !== '' && !this.aProcessar() && !this.conflito();
      case 'OWNER':
        return this.novoOwnerId() !== null && !this.aProcessar() && !this.conflito();
      default:
        // Sub-ecrãs ainda por implementar.
        return false;
    }
  });

  constructor() {
    // Cada abertura recomeça no passo dos cartões: Cancelar não deixa estado para a próxima vez.
    effect(() => {
      if (this.aberto()) {
        untracked(() => {
          this.acaoSelecionada.set(null);
          this.acaoAberta.set(null);
          this.limparSubEcra();
        });
      }
    });
    effect(() => {
      alternar(this.controloEstado, this.aProcessar());
      alternar(this.controloOwner, this.aProcessar() || this.candidatos() === null || this.semCandidatos());
    });
    this.controloEstado.valueChanges.pipe(takeUntilDestroyed()).subscribe((valor) => this.escolherNovoEstado(valor));
    this.controloOwner.valueChanges.pipe(takeUntilDestroyed()).subscribe((valor) => {
      this.novoOwnerId.set(valor || null);
      this.erro.set(null);
    });
  }

  tituloDe(acao: AcaoAlteracao): string {
    return this.cartoes().find((c) => c.acao === acao)?.titulo ?? '';
  }

  selecionar(cartao: CartaoAlteracao): void {
    if (cartao.motivoDesativado === null) {
      this.acaoSelecionada.set(cartao.acao);
    }
  }

  confirmar(): void {
    if (!this.podeConfirmar()) {
      return;
    }
    if (this.acaoAberta() === null) {
      this.acaoAberta.set(this.acaoSelecionada());
      if (this.acaoAberta() === 'OWNER') {
        this.carregarCandidatos();
      }
      return;
    }
    const processo = this.processo();
    if (this.acaoAberta() === 'ANULAR') {
      this.executar(this.processoService.anular(processo.id, processo.versao, this.motivoAnulacao().trim()));
      return;
    }
    if (this.acaoAberta() === 'OWNER') {
      this.reatribuirAutor(processo);
      return;
    }
    const transicao = this.transicaoEscolhida();
    if (transicao) {
      this.executar(this.pedidoDaTransicao(processo, transicao));
    }
  }

  /** O foco volta ao cartão de onde se saiu, para quem usa só o teclado não recomeçar do topo. */
  voltar(): void {
    const aberta = this.acaoAberta();
    this.acaoAberta.set(null);
    this.limparSubEcra();
    afterNextRender(
      () => this.elemento.nativeElement.ownerDocument.querySelector<HTMLElement>(`.cartao[data-acao="${aberta}"]`)?.focus(),
      { injector: this.injector },
    );
  }

  private escolherNovoEstado(destino: string | null): void {
    const transicao = this.opcoesEstado().find((t) => t.estadoDestino === destino) ?? null;
    this.transicaoEscolhida.set(transicao);
    this.erro.set(null);
  }

  escreverMotivoAnulacao(evento: Event): void {
    this.motivoAnulacao.set((evento.target as HTMLTextAreaElement).value);
  }

  carregarCandidatos(): void {
    const processo = this.processo();
    this.falhaCandidatos.set(false);
    this.erro.set(null);
    this.utilizadorService
      .listar({ entidadeId: processo.entidadeId }, 0, TAMANHO_PAGINA_CANDIDATOS)
      .subscribe({
        next: (pagina) => this.candidatos.set(pagina.content.filter((u) => u.id !== processo.criadoPorId)),
        error: (erro: ApiError) => {
          this.falhaCandidatos.set(true);
          this.erro.set(erro);
        },
      });
  }

  private reatribuirAutor(processo: ProcessoDetalhe): void {
    const novoOwner = this.candidatos()?.find((c) => c.id === this.novoOwnerId());
    if (!novoOwner) {
      return;
    }
    const autorNome = novoOwner.nome ?? novoOwner.email ?? '—';
    this.executar(
      this.processoService
        .reatribuirAutor(processo.id, processo.versao, novoOwner.id)
        .pipe(map((atualizado): ProcessoAlterado => ({ ...atualizado, autorNome }))),
    );
  }

  private executar(pedido: Observable<ProcessoAlterado>): void {
    this.erro.set(null);
    this.aProcessar.set(true);
    pedido.subscribe({
      next: (atualizado) => {
        this.aProcessar.set(false);
        this.alterado.emit(atualizado);
      },
      error: (erro: ApiError) => {
        this.aProcessar.set(false);
        if (erro.codigo === 'VERSAO_DESATUALIZADA') {
          this.desatualizado.emit(erro);
          return;
        }
        this.erro.set(erro);
      },
    });
  }

  private pedidoDaTransicao(processo: ProcessoDetalhe, transicao: TransicaoEstado): Observable<Processo> {
    switch (transicao.acao) {
      case 'capturar':
        return this.processoService.capturar(processo.id, processo.versao);
      case 'validar':
        return this.processoService.validar(processo.id, processo.versao);
      case 'enviar-incm':
        return this.processoService.enviarIncm(processo.id, processo.versao);
    }
  }

  private limparSubEcra(): void {
    this.transicaoEscolhida.set(null);
    this.motivoAnulacao.set('');
    this.candidatos.set(null);
    this.falhaCandidatos.set(false);
    this.novoOwnerId.set(null);
    this.erro.set(null);
    this.aProcessar.set(false);
    for (const controlo of [this.controloEstado, this.controloOwner]) {
      controlo.reset(null, { emitEvent: false });
    }
  }
}
