import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  OnInit,
  afterNextRender,
  computed,
  effect,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiError } from '../../../core/models/error-response.model';
import { ProcessoDetalhe } from '../../../models/processo.model';
import { Utilizador } from '../../../models/utilizador.model';
import { ProcessoService } from '../../../services/processo.service';
import { UtilizadorService } from '../../../services/utilizador.service';
import { AppShellComponent } from '../../../shared/components/app-shell.component';
import { ErrorBannerComponent } from '../../../shared/components/error-banner.component';
import { EstadoProcessoBadgeComponent } from '../../../shared/components/estado-processo-badge.component';
import { ROUTES } from '../../../shared/routes';
import { AlertaComponent } from '../../../shared/ui/alerta/alerta.component';
import { BotaoComponent } from '../../../shared/ui/botao/botao.component';
import { CampoTextoComponent } from '../../../shared/ui/campo-texto/campo-texto.component';
import { CartaoComponent } from '../../../shared/ui/cartao/cartao.component';
import { OpcaoSeletor, SeletorComponent } from '../../../shared/ui/seletor/seletor.component';

/** O mesmo padrão do filtro `numeroProcesso` da API: fora dele o pedido nem sai. */
const PADRAO_NUMERO_PROCESSO = /^[0-9]{1,20}$/;
const PADRAO_UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
/** Uma página chega: são os utilizadores de uma só entidade. */
const TAMANHO_PAGINA_CANDIDATOS = 100;

export const MSG_NUMERO_EM_FALTA = 'Indique o n.º do processo.';
export const MSG_NUMERO_INVALIDO = 'O n.º do processo tem só dígitos (1 a 20).';
export const MSG_PROCESSO_INEXISTENTE = 'Não existe nenhum processo com este número.';
export const MSG_SEM_CANDIDATOS = 'Não há outros utilizadores desta entidade.';
/** O texto da API pede para recarregar; aqui o ecrã já releu o processo. */
export const MSG_VERSAO_DESATUALIZADA = 'O processo foi alterado entretanto e os dados foram relidos. Escolha o novo owner e grave de novo.';
export const MSG_PEDIDO_APOIO = 'Transferência pedida pela entidade num pedido de apoio. O pedido fica resolvido ao gravar.';
export const MSG_PEDIDO_RESOLVIDO = 'O pedido de apoio ficou resolvido.';
export const MSG_PEDIDO_RECUSADO = 'Grave de novo para transferir sem o pedido de apoio.';

/**
 * Alteração de Owner pelo n.º do processo (backoffice, só `MASTER`). Os candidatos são os utilizadores com vínculo
 * à entidade do processo, sem o autor atual. O ecrã mostra o estado e deixa ao servidor decidir se transfere:
 * os 409 vêm com a mensagem da API.
 */
@Component({
  selector: 'app-alteracao-owner',
  imports: [
    AppShellComponent,
    AlertaComponent,
    BotaoComponent,
    CampoTextoComponent,
    CartaoComponent,
    ErrorBannerComponent,
    EstadoProcessoBadgeComponent,
    ReactiveFormsModule,
    RouterLink,
    SeletorComponent,
  ],
  templateUrl: './alteracao-owner.component.html',
  styleUrls: ['../backoffice.css', './alteracao-owner.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AlteracaoOwnerComponent implements OnInit {
  private readonly processoService = inject(ProcessoService);
  private readonly utilizadorService = inject(UtilizadorService);
  private readonly route = inject(ActivatedRoute);
  private readonly injector = inject(Injector);
  /** O processo da rota ou, num pedido geral, o primeiro que se abre. */
  private processoDoPedido: string | null = null;

  private readonly cartaoProcesso = viewChild<ElementRef<HTMLElement>>('cartaoProcesso');
  private readonly mensagens = viewChild<ElementRef<HTMLElement>>('mensagens');

  readonly controloNumero = new FormControl('', { nonNullable: true });
  readonly controloOwner = new FormControl<string | null>(null);

  readonly numero = signal('');
  readonly aPesquisar = signal(false);
  readonly processo = signal<ProcessoDetalhe | null>(null);
  /** Nulo enquanto carregam. Sem o autor atual. */
  readonly candidatos = signal<Utilizador[] | null>(null);
  readonly falhaCandidatos = signal(false);
  readonly novoOwnerId = signal<string | null>(null);
  readonly aGravar = signal(false);
  /** Vem da rota; deixa de ser enviado quando a API o recusa, quando fica resolvido ou quando se muda de processo. */
  readonly pedidoApoioId = signal<string | null>(null);
  /** 403/404/409 e o que não tem campo: vai para o `app-error-banner`. */
  readonly erro = signal<ApiError | null>(null);
  readonly erroNumero = signal<string | null>(null);
  readonly erroNovoOwner = signal<string | null>(null);
  /** A `versao` enviada já não era a do processo: o processo foi relido. */
  readonly aviso = signal<string | null>(null);
  readonly sucesso = signal<string | null>(null);
  readonly msgPedidoApoio = MSG_PEDIDO_APOIO;

  readonly rotaProcesso = computed(() => {
    const processo = this.processo();
    return processo ? ROUTES.PROCESSO_DETALHE(processo.id) : null;
  });
  readonly semCandidatos = computed(() => this.candidatos()?.length === 0);
  readonly opcoesOwner = computed<OpcaoSeletor[]>(() =>
    (this.candidatos() ?? []).map((c) => ({ valor: c.id, rotulo: `${c.nome ?? '—'} — ${c.email ?? '—'}` })),
  );
  readonly placeholderOwner = computed(() => {
    if (this.falhaCandidatos()) {
      return 'Indisponível';
    }
    if (this.candidatos() === null) {
      return 'A carregar…';
    }
    return this.semCandidatos() ? MSG_SEM_CANDIDATOS : 'Selecione…';
  });
  readonly podeGravar = computed(() => this.processo() !== null && this.novoOwnerId() !== null && !this.aGravar());

  constructor() {
    effect(() => {
      const bloqueado = this.aGravar() || this.candidatos() === null || this.semCandidatos();
      if (bloqueado) {
        this.controloOwner.disable({ emitEvent: false });
      } else {
        this.controloOwner.enable({ emitEvent: false });
      }
    });
    this.controloNumero.valueChanges.pipe(takeUntilDestroyed()).subscribe((valor) => {
      this.numero.set(valor);
      this.erroNumero.set(null);
    });
    this.controloOwner.valueChanges.pipe(takeUntilDestroyed()).subscribe((valor) => {
      this.novoOwnerId.set(valor || null);
      this.erroNovoOwner.set(null);
      this.erro.set(null);
    });
  }

  /** `processoId` na rota salta a pesquisa; um valor que não é UUID é ignorado (o 403/404 da API é a garantia). */
  ngOnInit(): void {
    const parametros = this.route.snapshot.queryParamMap;
    const processoId = parametros.get('processoId');
    const pedidoApoioId = parametros.get('pedidoApoioId');
    if (pedidoApoioId && PADRAO_UUID.test(pedidoApoioId)) {
      this.pedidoApoioId.set(pedidoApoioId);
    }
    if (processoId && PADRAO_UUID.test(processoId)) {
      this.processoDoPedido = processoId;
      this.aPesquisar.set(true);
      this.abrir(processoId);
    }
  }

  procurar(evento?: Event): void {
    evento?.preventDefault();
    if (this.aPesquisar()) {
      return;
    }
    const numero = this.numero().trim();
    this.limpar();
    if (numero === '') {
      this.erroNumero.set(MSG_NUMERO_EM_FALTA);
      return;
    }
    if (!PADRAO_NUMERO_PROCESSO.test(numero)) {
      this.erroNumero.set(MSG_NUMERO_INVALIDO);
      return;
    }
    this.aPesquisar.set(true);
    this.processoService.listar({ numeroProcesso: numero }, 0, 1).subscribe({
      next: (pagina) => {
        const encontrado = pagina.content[0];
        if (!encontrado) {
          this.aPesquisar.set(false);
          this.erroNumero.set(MSG_PROCESSO_INEXISTENTE);
          return;
        }
        this.abrir(encontrado.id);
      },
      error: (erro: ApiError) => this.falharPesquisa(erro),
    });
  }

  gravar(): void {
    const processo = this.processo();
    const novoOwner = this.candidatos()?.find((c) => c.id === this.novoOwnerId());
    if (!this.podeGravar() || !processo || !novoOwner) {
      return;
    }
    this.erro.set(null);
    this.erroNovoOwner.set(null);
    this.aviso.set(null);
    this.sucesso.set(null);
    this.aGravar.set(true);
    const pedidoApoioId = this.pedidoApoioId();
    const pedido = pedidoApoioId
      ? this.processoService.reatribuirAutor(processo.id, processo.versao, novoOwner.id, pedidoApoioId)
      : this.processoService.reatribuirAutor(processo.id, processo.versao, novoOwner.id);
    pedido.subscribe({
      next: () => {
        this.aGravar.set(false);
        const alterado = `Owner alterado para ${novoOwner.nome ?? novoOwner.email ?? '—'}.`;
        this.sucesso.set(pedidoApoioId ? `${alterado} ${MSG_PEDIDO_RESOLVIDO}` : alterado);
        this.largarPedido();
        this.focarMensagens();
        this.reler(processo.id);
      },
      error: (erro: ApiError) => {
        this.aGravar.set(false);
        this.falharGravacao(erro, processo.id);
      },
    });
  }

  private falharGravacao(erro: ApiError, processoId: string): void {
    if (erro.codigo === 'VERSAO_DESATUALIZADA') {
      this.aviso.set(MSG_VERSAO_DESATUALIZADA);
      this.focarMensagens();
      this.reler(processoId);
      return;
    }
    const erroPedido = erro.campos?.find((campo) => campo.path === 'pedidoApoioId')?.erro;
    if (erroPedido) {
      this.largarPedido();
      this.aviso.set(`${erroPedido} ${MSG_PEDIDO_RECUSADO}`);
      this.focarMensagens();
      return;
    }
    const erroCampo = erro.campos?.find((campo) => campo.path === 'novoUtilizadorId')?.erro;
    if (erroCampo) {
      this.erroNovoOwner.set(erroCampo);
      return;
    }
    this.erro.set(erro);
  }

  private abrir(processoId: string): void {
    this.processoService.obter(processoId).subscribe({
      next: (processo) => {
        this.aPesquisar.set(false);
        this.ligarPedido(processo.id);
        this.processo.set(processo);
        this.controloNumero.setValue(processo.numeroProcesso, { emitEvent: false });
        this.numero.set(processo.numeroProcesso);
        this.carregarCandidatos(processo);
        afterNextRender(() => this.cartaoProcesso()?.nativeElement.focus(), { injector: this.injector });
      },
      error: (erro: ApiError) => this.falharPesquisa(erro),
    });
  }

  /** Depois de gravar ou de um 409 de versão: o cartão passa a mostrar o owner e a `versao` atuais. */
  private reler(processoId: string): void {
    this.processoService.obter(processoId).subscribe({
      next: (processo) => {
        this.processo.set(processo);
        this.carregarCandidatos(processo);
      },
      error: (erro: ApiError) => this.erro.set(erro),
    });
  }

  private carregarCandidatos(processo: ProcessoDetalhe): void {
    this.candidatos.set(null);
    this.falhaCandidatos.set(false);
    this.novoOwnerId.set(null);
    this.controloOwner.reset(null, { emitEvent: false });
    this.utilizadorService.listar({ entidadeId: processo.entidadeId }, 0, TAMANHO_PAGINA_CANDIDATOS).subscribe({
      next: (pagina) => this.candidatos.set(pagina.content.filter((u) => u.id !== processo.criadoPorId)),
      error: (erro: ApiError) => {
        this.falhaCandidatos.set(true);
        this.erro.set(erro);
      },
    });
  }

  private falharPesquisa(erro: ApiError): void {
    this.aPesquisar.set(false);
    if (erro.codigo === 'VALIDACAO') {
      this.erroNumero.set(erro.campos?.find((campo) => campo.path === 'numeroProcesso')?.erro ?? erro.mensagem);
      return;
    }
    this.erro.set(erro);
  }

  /** O pedido de apoio é de um processo: abrir outro deixa de o enviar. */
  private ligarPedido(processoId: string): void {
    if (this.pedidoApoioId() === null) {
      return;
    }
    if (this.processoDoPedido === null) {
      this.processoDoPedido = processoId;
    } else if (this.processoDoPedido !== processoId) {
      this.largarPedido();
    }
  }

  private largarPedido(): void {
    this.pedidoApoioId.set(null);
    this.processoDoPedido = null;
  }

  private focarMensagens(): void {
    afterNextRender(() => this.mensagens()?.nativeElement.focus(), { injector: this.injector });
  }

  /** Nova pesquisa: cartão, seletor e mensagens recomeçam; o pedido de apoio só cai se o processo mudar. */
  private limpar(): void {
    this.processo.set(null);
    this.candidatos.set(null);
    this.falhaCandidatos.set(false);
    this.novoOwnerId.set(null);
    this.controloOwner.reset(null, { emitEvent: false });
    this.erro.set(null);
    this.erroNumero.set(null);
    this.erroNovoOwner.set(null);
    this.aviso.set(null);
    this.sucesso.set(null);
  }
}
