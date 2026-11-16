import { DatePipe, NgTemplateOutlet, formatDate } from '@angular/common';
import {
  Component,
  DestroyRef,
  Injector,
  LOCALE_ID,
  OnDestroy,
  OnInit,
  afterNextRender,
  computed,
  effect,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormGroup } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { catchError, forkJoin, of } from 'rxjs';
import { AppShellComponent } from '../../../shared/components/app-shell.component';
import { ConfirmacaoModalComponent } from '../../../shared/components/confirmacao-modal.component';
import { ErrorBannerComponent } from '../../../shared/components/error-banner.component';
import { EstadoProcessoBadgeComponent, rotuloEstado } from '../../../shared/components/estado-processo-badge.component';
import { FicheirosProcessoComponent } from '../../../shared/components/ficheiros-processo.component';
import { HistoricoDocumentalComponent } from '../../../shared/components/historico-documental.component';
import { AuthService } from '../../../core/auth/auth.service';
import { ApiError, CampoErro } from '../../../core/models/error-response.model';
import {
  AcaoProcesso,
  AtualizarDadosFormularioRequest,
  DestinoProcesso,
  FormularioFixo,
  Processo,
  ProcessoDetalhe,
  RedeDefesa,
  ROTULO_DESTINO,
} from '../../../models/processo.model';
import { InstrumentoService } from '../../../services/instrumento.service';
import { ProcessoService } from '../../../services/processo.service';
import { ReferenciaSnitService } from '../../../services/referencia-snit.service';
import { AlertaComponent } from '../../../shared/ui/alerta/alerta.component';
import { BotaoComponent } from '../../../shared/ui/botao/botao.component';
import { CartaoComponent } from '../../../shared/ui/cartao/cartao.component';
import { SeloComponent } from '../../../shared/ui/selo/selo.component';
import { linkDrSeguro } from '../../../shared/link-dr';
import { ROUTES } from '../../../shared/routes';
import { resumoAtoReferido } from '../ato-referido';
import { AlteracoesModalComponent, ProcessoAlterado } from '../alteracoes-modal/alteracoes-modal.component';
import {
  CriarNotificacaoModalComponent,
  ProcessoFixo,
} from '../../notificacoes/criar-notificacao-modal/criar-notificacao-modal.component';
import { AVISO_SEM_FORMULARIO, ESTADOS_COM_FORMULARIO_EDITAVEL, pedeElementosAfetadosNoFormulario } from '../formulario-fixo';
import { CODIGO_CAMPO_BLOQUEADO, InformacaoBaseAlterada, informacaoBaseDe, separarErrosInformacaoBase } from '../informacao-base';
import { ProcessoDocumentosComponent } from '../processo-documentos/processo-documentos.component';
import {
  FormularioInicialValores,
  ProcessoFormularioInicialComponent,
  caminhoDoControlo,
  comErroNoTipoAto,
  rotuloConcelhos,
} from '../processo-formulario-inicial/processo-formulario-inicial.component';
import { ProcessoFormularioSrupComponent } from '../processo-formulario-srup/processo-formulario-srup.component';
import { CAMPO_MODALIDADE_RATIFICACAO, FormularioRatificacaoComponent } from '../formularios/formulario-ratificacao.component';
import { FormularioSuspensaoCcdrComponent } from '../formularios/formulario-suspensao-ccdr.component';
import { FormularioGovernoComponent } from '../formularios/formulario-governo.component';
import { ProcessoRecusasComponent } from '../processo-recusas/processo-recusas.component';
import { DesbloquearCampoModalComponent } from '../desbloquear-campo/desbloquear-campo-modal.component';
import { AlvoDesbloqueio, DesbloqueioCampos } from '../desbloquear-campo/desbloqueio-campos';

const DURACAO_BANNER_ACAO_MS = 4000;
/** Os formulários que mostram o nome dos tipos de Instrumento, lido do catálogo ao abrir a edição. */
const FORMULARIOS_COM_NOMES_DOS_TIPOS: readonly FormularioFixo[] = [
  'SRUP',
  'RATIFICACAO',
  'SUSPENSAO_CCDR',
  'SUSPENSAO_MP_GOVERNO',
  'PRORROGACAO_GOVERNO',
];
const CAMPO_DINAMICA_ASSOCIADA = 'dinamicaAssociadaId';
/** Texto do alerta da PCM no processo que a DGT lhe atribuiu; a EF e o LLD não o fixam. */
export const TEXTO_ALERTA_PCM =
  'A DGT enviou-lhe este processo para completar. Confirme os campos já preenchidos, anexe o texto final da RCM ' +
  'e os documentos que lhe competem e, no fim, use «Submeter à DGT». Os ficheiros já carregados não podem ser ' +
  'substituídos nem removidos.';
export const TEXTO_REVALIDACAO_PCM = 'Valide os documentos acrescentados pela PCM antes de enviar à INCM.';

/**
 * Do mockup só se constrói o que tem dados reais. Ficam de fora, por não haver fonte: prazo legal
 * (`prazo_dias` sempre NULL), NUT III, entidades responsável/validadora, separadores Dinâmicas e
 * Comunicações e "Exportar". "Fase Atual" seria o mesmo `estado` do badge: o modelo não distingue fase de estado.
 *
 * A edição lê `editavel` e as ações `acoesDisponiveis`: é o backend que diz o que o chamador pode fazer agora.
 */
/** A resposta do `PUT` não traz o nome: lê-se das opções do detalhe. */
function nomeDoTipoAto(processo: ProcessoDetalhe, tipoAtoId: number | null | undefined): string | null {
  if (tipoAtoId == null || tipoAtoId === processo.tipoAtoId) {
    return processo.tipoAtoNome;
  }
  return processo.tiposAtoDisponiveis?.find((tipo) => tipo.id === tipoAtoId)?.nome ?? processo.tipoAtoNome;
}

/** Omitido no pedido, fica a descrição lida; senão, a da opção escolhida. */
function descricaoDaOpcao(
  opcoes: { codigo: string; descricao: string }[] | null | undefined,
  codigo: string | null | undefined,
  lida: string | null | undefined,
): string | null {
  if (codigo === undefined) {
    return lida ?? null;
  }
  return opcoes?.find((opcao) => opcao.codigo === codigo)?.descricao ?? null;
}

@Component({
  selector: 'app-processo-detalhe',
  standalone: true,
  imports: [
    AlertaComponent,
    AlteracoesModalComponent,
    BotaoComponent,
    CartaoComponent,
    AppShellComponent,
    ConfirmacaoModalComponent,
    CriarNotificacaoModalComponent,
    DatePipe,
    ErrorBannerComponent,
    EstadoProcessoBadgeComponent,
    FicheirosProcessoComponent,
    HistoricoDocumentalComponent,
    NgTemplateOutlet,
    ProcessoDocumentosComponent,
    ProcessoFormularioInicialComponent,
    ProcessoFormularioSrupComponent,
    FormularioRatificacaoComponent,
    FormularioSuspensaoCcdrComponent,
    FormularioGovernoComponent,
    ProcessoRecusasComponent,
    RouterLink,
    SeloComponent,
    DesbloquearCampoModalComponent,
  ],
  providers: [DesbloqueioCampos],
  templateUrl: './processo-detalhe.component.html',
  styleUrl: './processo-detalhe.component.css',
})
export class ProcessoDetalheComponent implements OnInit, OnDestroy {
  private readonly processoService = inject(ProcessoService);
  private readonly referenciaSnitService = inject(ReferenciaSnitService);

  /** Nomes da Região e do Município gravados no formulário `REN`; sem nome, o ecrã mostra o código. */
  readonly territorioRen = signal<{ regiao: string | null; municipio: string | null }>({ regiao: null, municipio: null });
  private readonly resolverTerritorioRen = effect(() => {
    const processo = this.processo();
    const regiao = processo?.formulario === 'REN' ? (processo.regiaoCodigo ?? null) : null;
    const municipio = processo?.formulario === 'REN' ? (processo.municipioCodigo ?? null) : null;
    if (!regiao) {
      this.territorioRen.set({ regiao: null, municipio: null });
      return;
    }
    forkJoin([
      this.referenciaSnitService.nutsII().pipe(catchError(() => of([]))),
      this.referenciaSnitService.concelhos(regiao).pipe(catchError(() => of([]))),
    ]).subscribe(([regioes, concelhos]) =>
      this.territorioRen.set({
        regiao: regioes.find((item) => item.codigo === regiao)?.nome ?? null,
        municipio: concelhos.find((item) => item.codigo === municipio)?.nome ?? null,
      }),
    );
  });
  private readonly instrumentoService = inject(InstrumentoService);
  private readonly authService = inject(AuthService);
  private readonly activatedRoute = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly injector = inject(Injector);
  private readonly destroyRef = inject(DestroyRef);
  private readonly desbloqueio = inject(DesbloqueioCampos);
  private temporizadorBannerAcao: ReturnType<typeof setTimeout> | undefined;

  readonly rotas = ROUTES;
  readonly rotuloConcelhos = rotuloConcelhos;

  readonly processo = signal<ProcessoDetalhe | null>(null);
  readonly aCarregar = signal(true);
  readonly erro = signal<ApiError | null>(null);

  readonly emEdicao = signal(false);
  private readonly alvoDesbloqueio = computed<AlvoDesbloqueio | null>(() => {
    const processo = this.processo();
    return processo && this.emEdicao() && processo.acoesDisponiveis.includes('desbloquear-campo')
      ? { processoId: processo.id, versao: processo.versao }
      : null;
  });
  readonly aGuardar = signal(false);
  readonly aResgatar = signal(false);
  readonly aCorrigir = signal(false);
  readonly confirmacaoCorrigirAberta = signal(false);
  readonly confirmacaoResgatarAberta = signal(false);
  readonly alteracoesAbertas = signal(false);
  /** Confirmação passageira do Guardar; desativa o botão enquanto está visível. */
  readonly mensagemSucesso = signal<string | null>(null);

  readonly formularioRef = viewChild(ProcessoFormularioInicialComponent);
  readonly ratificacaoRef = viewChild(FormularioRatificacaoComponent);
  /** A recusa do servidor em `modalidadeRatificacao` fica junto do grupo, fora do cartão Informação Geral. */
  readonly erroModalidadeRatificacao = signal<string | null>(null);
  readonly documentosRef = viewChild(ProcessoDocumentosComponent);
  readonly rotulosDestino = ROTULO_DESTINO;

  /** O pedido do formulário como está: o destino grava-se com ele, porque o `PUT` substitui o que se omite. */
  readonly pedidoRascunho = (): Omit<AtualizarDadosFormularioRequest, 'versao'> | null => {
    const formulario = this.formularioRef();
    if (!formulario) {
      return null;
    }
    const informacaoBase = this.informacaoBaseEmEdicao() ?? this.informacaoBaseGravada();
    return {
      ...formulario.dados(),
      ...formulario.dadosFormularioPedido(),
      ...(this.ratificacaoRef()?.dados() ?? {}),
      dataAssinatura: formulario.dataAssinaturaPedido(informacaoBase.dataAssinatura),
      informacaoBase: informacaoBase.informacaoBase,
    };
  };

  /** O destino já ficou gravado com o formulário: o processo segue com a `versao` nova. */
  aoGravarDestino(gravado: { versao: number; destino: DestinoProcesso }): void {
    this.processo.update((atual) => (atual ? { ...atual, versao: gravado.versao, destino: gravado.destino } : atual));
  }
  /** Fotografia ao abrir a edição: repor a partir de `processo()` apagaria o que se escreve ao fundir respostas. */
  readonly valoresIniciais = signal<FormularioInicialValores | null>(null);
  /** Fotografia ao abrir a edição, como `valoresIniciais`; o que se escreve no bloco vai para `informacaoBaseEmEdicao`. */
  readonly informacaoBaseGravada = signal<InformacaoBaseAlterada>({ informacaoBase: null, dataAssinatura: null });
  /** Nula enquanto não se escreve nada, e então grava-se o que já estava gravado. */
  readonly informacaoBaseEmEdicao = signal<InformacaoBaseAlterada | null>(null);
  readonly errosInformacaoBase = signal<Record<string, string>>({});

  readonly anuladoIncm = computed(() => this.processo()?.estado === 'ANULADO_INCM');
  /**
   * As recusas persistem depois de `corrigir` e de nova submissão. Em `EM_SUBMISSAO` só conta a mais recente se
   * for posterior à última submissão: é a que originou a correção em curso (depois de um `resgatar` não há nenhuma).
   */
  readonly recusaVisivel = computed<'DGT' | 'INCM' | null>(() => {
    const processo = this.processo();
    if (!processo) {
      return null;
    }
    if (processo.estado === 'REJEITADO_DGT') {
      return 'DGT';
    }
    if (processo.estado === 'ANULADO_INCM') {
      return 'INCM';
    }
    if (processo.estado !== 'EM_SUBMISSAO' || !processo.dataSubmissao) {
      return null;
    }
    const submissao = Date.parse(processo.dataSubmissao);
    const dgt = processo.ultimaRejeicaoDgt ? Date.parse(processo.ultimaRejeicaoDgt.data) : Number.NEGATIVE_INFINITY;
    const incm = processo.ultimaRejeicaoIncm ? Date.parse(processo.ultimaRejeicaoIncm.data) : Number.NEGATIVE_INFINITY;
    if (Math.max(dgt, incm) <= submissao) {
      return null;
    }
    return incm > dgt ? 'INCM' : 'DGT';
  });

  readonly podeEditar = computed(() => this.processo()?.editavel === true);

  /** A PCM no `VALIDADO_DGT` que a DGT lhe atribuiu: o ecrã mostra o alerta e só a zona de carregamento (a API decide). */
  readonly pcmPodeAcrescentar = computed(() => {
    const processo = this.processo();
    const utilizadorId = this.authService.utilizadorId();
    return (
      this.authService.ator() === 'PCM' &&
      processo?.estado === 'VALIDADO_DGT' &&
      utilizadorId !== null &&
      processo.pcmAtribuidoAId === utilizadorId
    );
  });
  readonly textoAlertaPcm = TEXTO_ALERTA_PCM;
  /** A região existe desde o início: um leitor de ecrã só anuncia o que muda dentro de uma região já presente. */
  readonly anuncioPcm = computed(() => {
    if (this.pcmPodeAcrescentar()) {
      return TEXTO_ALERTA_PCM;
    }
    const devolucao = this.textoDevolucaoPcm();
    return this.revalidacaoPcm() ? `${devolucao} ${TEXTO_REVALIDACAO_PCM}` : devolucao;
  });
  private readonly locale = inject(LOCALE_ID);
  /** Para os perfis internos, a data da devolução pela PCM enquanto o processo está em `VALIDADO_DGT`. */
  readonly devolucaoPcm = computed(() => {
    const processo = this.processo();
    return this.authService.perfilInterno() !== null && processo?.estado === 'VALIDADO_DGT' && processo.pcmDevolvidoEm
      ? processo.pcmDevolvidoEm
      : null;
  });
  readonly revalidacaoPcm = computed(() => this.devolucaoPcm() !== null && this.processo()?.revalidacaoPcm === true);
  readonly textoDevolucaoPcm = computed(() => {
    const devolvidoEm = this.devolucaoPcm();
    return devolvidoEm ? `Devolvido pela PCM em ${formatDate(devolvidoEm, 'dd/MM/yyyy HH:mm', this.locale)}.` : '';
  });
  readonly textoRevalidacaoPcm = TEXTO_REVALIDACAO_PCM;
  readonly devolverDgtDisponivel = computed(() => this.temAcao('devolver-dgt'));
  readonly aDevolver = signal(false);
  readonly confirmacaoDevolverAberta = signal(false);

  pedeElementosAfetados(processo: ProcessoDetalhe): boolean {
    return pedeElementosAfetadosNoFormulario(processo.formulario, processo.exigeElementosAfetados);
  }

  /** O detalhe só traz o código do tipo; no formulário SRUP mostra-se o nome, lido do catálogo ao abrir a edição. */
  readonly nomesTiposInstrumento = signal<Record<string, string>>({});

  nomeTipoInstrumento(codigo: string): string {
    return this.nomesTiposInstrumento()[codigo] ?? codigo;
  }

  private lerNomesTiposInstrumento(): void {
    if (Object.keys(this.nomesTiposInstrumento()).length > 0) {
      return;
    }
    // Sem o catálogo fica o código: é um rótulo só de leitura e não trava a edição.
    this.instrumentoService.listarTipos().subscribe({
      next: (tipos) => this.nomesTiposInstrumento.set(Object.fromEntries(tipos.map((tipo) => [tipo.codigo, tipo.nome]))),
      error: () => undefined,
    });
  }

  readonly avisoSemFormulario = AVISO_SEM_FORMULARIO;
  readonly mostrarAvisoSemFormulario = computed(() => {
    const processo = this.processo();
    return (
      processo !== null &&
      processo.formulario === null &&
      ESTADOS_COM_FORMULARIO_EDITAVEL.includes(processo.estado) &&
      this.authService.ator() === 'ENTIDADE'
    );
  });
  readonly podeCorrigir = computed(() => this.temAcao('corrigir'));
  readonly submeterDisponivel = computed(() => this.temAcao('submeter'));
  /** Os 409 chegam como `ESTADO_INVALIDO`: oferece-se Recarregar, nunca repetir a ação. */
  readonly conflito = computed(() => this.erro()?.codigo === 'ESTADO_INVALIDO' && this.processo() !== null);
  readonly podeResgatar = computed(() => this.temAcao('resgatar'));

  /** `MASTER` e `TECNICO` têm ator `DGT`; `VISITANTE` não. As ações do modal vêm de `acoesDisponiveis`. */
  readonly podeAlterar = computed(() => this.authService.ator() === 'DGT');
  /** Quem vê o detalhe vê o processo; o pedido de apoio é do utilizador externo (a API recusa os internos). */
  readonly podePedirApoio = computed(() => this.processo() !== null && this.authService.perfilInterno() === null);
  readonly pedirApoioAberto = signal(false);
  readonly processoDoPedidoApoio = computed<ProcessoFixo | null>(() => {
    const p = this.processo();
    return p
      ? { id: p.id, rotulo: [p.numeroProcesso, p.designacaoInstrumento, p.nomeProcedimento].filter(Boolean).join(' — ') }
      : null;
  });
  /** A mesma regra que a lista usa para abrir a validação em vez do detalhe. */
  readonly podeValidar = computed(
    () => this.authService.ator() === 'DGT' && this.processo()?.estado === 'EM_VALIDACAO_DGT',
  );
  readonly linkDr = computed(() => linkDrSeguro(this.processo()?.linkDr ?? null));
  readonly igtAssociado = computed(() => {
    const processo = this.processo();
    const dinamica = processo?.dinamicaAssociada;
    // Depois de gravar só se conhece o id novo: a Dinâmica antiga já não é a associada.
    return dinamica && dinamica.dinamicaId === processo.dinamicaAssociadaId ? `${dinamica.numeroDinamica} · ${dinamica.instrumento?.designacao ?? dinamica.descritivo}` : null;
  });

  private temAcao(acao: AcaoProcesso): boolean {
    return this.processo()?.acoesDisponiveis.includes(acao) === true;
  }

  ngOnInit(): void {
    this.desbloqueio.ligar(this.alvoDesbloqueio, (desbloqueado) => this.aoDesbloquearCampo(desbloqueado));
    this.activatedRoute.paramMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((parametros) => this.carregar(parametros.get('processoId')));
  }

  /** «Ato a que respeita» liga a outro processo na mesma rota: o componente é reaproveitado e lê o novo. */
  private carregar(id: string | null): void {
    if (!id) {
      this.aCarregar.set(false);
      return;
    }
    if (this.processo() && this.processo()?.id !== id) {
      this.processo.set(null);
      this.erro.set(null);
      this.aCarregar.set(true);
    }
    this.processoService.obter(id).subscribe({
      next: (processo) => {
        this.processo.set(processo);
        this.aCarregar.set(false);
        // A PCM abre o processo para o completar: o formulário e os itens documentais ficam logo em edição.
        if (this.pcmPodeAcrescentar() && this.podeEditar()) {
          this.iniciarEdicao();
        }
      },
      error: (erro: ApiError) => {
        this.erro.set(erro);
        this.aCarregar.set(false);
      },
    });
  }

  resumoAtoReferido(processo: ProcessoDetalhe): string {
    return resumoAtoReferido(processo.numeroProcessoReferido ?? '', processo.tipoProcedimentoReferido);
  }

  ngOnDestroy(): void {
    clearTimeout(this.temporizadorBannerAcao);
  }

  /**
   * Fundida, nunca a substituir: a resposta é só `ProcessoResponse`, mais o `autorNome` numa reatribuição.
   * O estado que o backend devolve pode não ser o do botão (o envio à INCM acaba em `EM_VALIDACAO_INCM` ou
   * `ANULADO_INCM`): a mensagem diz o estado a que o processo chegou.
   */
  aoAlterar(atualizado: ProcessoAlterado): void {
    const estadoAnterior = this.processo()?.estado;
    this.alteracoesAbertas.set(false);
    this.aplicarTransicao(atualizado);
    if (atualizado.estado !== estadoAnterior) {
      this.mostrarSucessoPassageiro(`Estado alterado para «${rotuloEstado(atualizado.estado)}».`);
    }
  }

  /**
   * A resposta de um `PATCH` não traz `editavel`, `acoesDisponiveis` nem `podeSubmeter`, e depois de uma transição
   * os de antes já não valem: esconde-os até o detalhe voltar a ser lido.
   */
  private aplicarTransicao(atualizado: Partial<ProcessoDetalhe>, depois?: () => void): void {
    this.processo.update((atual) =>
      atual ? { ...atual, ...atualizado, editavel: false, acoesDisponiveis: [], podeSubmeter: false } : atual,
    );
    this.lerDetalhe(depois);
  }

  /** Depois de um 409 fecha o modal, porque o estado novo pode oferecer outras ações. */
  recarregar(): void {
    if (!this.processo()) {
      return;
    }
    this.alteracoesAbertas.set(false);
    this.erro.set(null);
    this.lerDetalhe();
  }

  /** Os Instrumentos associados mudam `instrumentosAbrangidos` e `podeSubmeter`: relê-se o detalhe. */
  aoAssociarInstrumentos(): void {
    this.lerDetalhe();
  }

  /** O detalhe lido está velho: mostra-se o aviso e relê-se, em vez de repetir a escrita com a mesma `versao`. */
  aoDesatualizar(erro: ApiError): void {
    this.falhar(erro);
  }

  private lerDetalhe(depois?: () => void): void {
    const processo = this.processo();
    if (!processo) {
      return;
    }
    this.processoService.obter(processo.id).subscribe({
      next: (recarregado) => {
        this.processo.set(recarregado);
        depois?.();
      },
      error: (erro: ApiError) => this.erro.set(erro),
    });
  }

  /** A edição fecha: o formulário só se preenche ao abrir, e reabri-lo parte do detalhe já recarregado. */
  private falhar(erro: ApiError): void {
    this.erro.set(erro);
    if (erro.codigo !== 'VERSAO_DESATUALIZADA' && erro.codigo !== CODIGO_CAMPO_BLOQUEADO) {
      return;
    }
    this.alteracoesAbertas.set(false);
    this.confirmacaoResgatarAberta.set(false);
    this.confirmacaoCorrigirAberta.set(false);
    this.emEdicao.set(false);
    this.lerDetalhe();
  }

  /** Só a `versao` e os bloqueados: o resto do formulário continua como o autor o deixou. */
  private aoDesbloquearCampo(desbloqueado: Processo): void {
    this.processo.update((atual) =>
      atual ? { ...atual, versao: desbloqueado.versao, camposBloqueados: desbloqueado.camposBloqueados } : atual,
    );
  }

  iniciarEdicao(): void {
    const processo = this.processo();
    this.valoresIniciais.set(processo);
    this.reporInformacaoBase(processo ? informacaoBaseDe(processo) : { informacaoBase: null, dataAssinatura: null });
    if (processo?.formulario && FORMULARIOS_COM_NOMES_DOS_TIPOS.includes(processo.formulario)) {
      this.lerNomesTiposInstrumento();
    }
    this.emEdicao.set(true);
  }

  private reporInformacaoBase(valores: InformacaoBaseAlterada): void {
    this.informacaoBaseGravada.set(valores);
    this.informacaoBaseEmEdicao.set(null);
    this.errosInformacaoBase.set({});
  }

  cancelarEdicao(): void {
    this.emEdicao.set(false);
  }

  /**
   * A série decide os tipos de ato: grava-se o rascunho e relê-se o detalhe, que traz `tiposAtoDisponiveis` da série
   * nova (e sem o tipo de ato, se o servidor o limpou por não existir nela). O cartão reparte do detalhe relido.
   */
  aoEscolherSerie(): void {
    this.gravarRascunho(() => this.lerDetalhe(() => this.valoresIniciais.set(this.processo())));
  }

  /** Volta à lista depois da confirmação passageira. A resposta não traz os campos INCM: fundem-se os enviados. */
  guardarRascunho(): void {
    if (this.pcmPodeAcrescentar()) {
      // A PCM fica no processo: depois de guardar ainda acrescenta ficheiros e submete à DGT.
      this.gravarRascunho(() => this.mostrarSucessoPassageiro('Alterações guardadas.'));
      return;
    }
    this.gravarRascunho(() => {
      clearTimeout(this.temporizadorBannerAcao);
      this.mensagemSucesso.set('Rascunho guardado com sucesso.');
      this.temporizadorBannerAcao = setTimeout(() => this.router.navigate([this.rotas.PROCESSOS]), DURACAO_BANNER_ACAO_MS);
    });
  }

  /** Grava e abre o wizard no passo 3 (Publicar), que lê do backend o que acabou de ficar gravado. */
  avancarParaPublicar(): void {
    if (this.formularioRef()?.validarObrigatorios() === false) {
      return;
    }
    this.gravarRascunho((processoId) =>
      this.router.navigate([this.rotas.PROCESSOS_CRIAR], { queryParams: { processoId } }),
    );
  }

  /** O pedido leva só os códigos; o detalhe guarda as redes com o nome, que vêm das disponíveis. */
  private redesGravadas(atual: ProcessoDetalhe, codigos: string[] | undefined): RedeDefesa[] | null | undefined {
    if (!codigos) {
      return atual.redesDefesa;
    }
    return (atual.redesDefesaDisponiveis ?? []).filter((rede) => codigos.includes(rede.codigo));
  }

  private gravarRascunho(depois: (processoId: string) => void): void {
    const processo = this.processo();
    const formulario = this.formularioRef();
    if (!processo || !formulario) {
      return;
    }

    const dadosFormulario = { ...formulario.dados(), ...(this.ratificacaoRef()?.dados() ?? {}) };
    const informacaoBaseDoBloco = this.informacaoBaseEmEdicao() ?? this.informacaoBaseGravada();
    const informacaoBase = {
      ...informacaoBaseDoBloco,
      dataAssinatura: formulario.dataAssinaturaPedido(informacaoBaseDoBloco.dataAssinatura),
    };
    this.erro.set(null);
    this.erroModalidadeRatificacao.set(null);
    this.aGuardar.set(true);
    this.processoService
      .guardarRascunho(processo.id, {
        versao: processo.versao,
        ...dadosFormulario,
        ...formulario.dadosFormularioPedido(),
        dataAssinatura: informacaoBase.dataAssinatura,
        informacaoBase: informacaoBase.informacaoBase,
      })
      .subscribe({
        next: (atualizado) => {
          this.processo.update((atual) =>
            atual
              ? {
                  ...atual,
                  ...atualizado,
                  ...dadosFormulario,
                  tipoAtoNome: nomeDoTipoAto(atual, dadosFormulario.tipoAtoId),
                  redesDefesa: this.redesGravadas(atual, dadosFormulario.redesDefesa),
                  regiaoDescricao: descricaoDaOpcao(atual.opcoesRegiao, dadosFormulario.regiao, atual.regiaoDescricao),
                  cimAmDescricao: descricaoDaOpcao(atual.opcoesCimAm, dadosFormulario.cimAm, atual.cimAmDescricao),
                  localizacao: atual.localizacao,
                  motivosAlteracao: atual.motivosAlteracao,
                  dataAssinatura: informacaoBase.dataAssinatura,
                }
              : atual,
          );
          this.reporInformacaoBase(informacaoBase);
          const documentos = this.documentosRef();
          // As Observações gravam-se por item, depois do rascunho; um erro fica junto da caixa e não se avança.
          (documentos?.gravarObservacoes() ?? of(true)).subscribe((gravadas) => {
            this.aGuardar.set(false);
            documentos?.recarregar();
            if (gravadas) {
              depois(processo.id);
            }
          });
        },
        error: (erro: ApiError) => {
          this.aGuardar.set(false);
          this.tratarErroDeGravacao(erro, formulario.form);
          if (erro.campos?.some((campo) => campo.path === CAMPO_DINAMICA_ASSOCIADA)) {
            formulario.focarDinamicaAssociada();
          }
        },
      });
  }

  /** Os erros do bloco ficam junto dos campos e não passam pelo banner; os do cartão seguem como antes. */
  private tratarErroDeGravacao(erroRecebido: ApiError, formFormulario: FormGroup): void {
    const erro = comErroNoTipoAto(erroRecebido);
    if (erro.codigo === CODIGO_CAMPO_BLOQUEADO) {
      this.falhar(erro);
      return;
    }
    const { doBloco, restantes: doCartao } = separarErrosInformacaoBase(erro);
    const restantes = this.separarErroModalidade(doCartao);
    this.errosInformacaoBase.set(doBloco);
    const temErrosDoBloco = Object.keys(doBloco).length > 0 || restantes.length < doCartao.length;
    const tratado =
      restantes.length === 0
        ? temErrosDoBloco
        : this.aplicarErrosDeCampo(new ApiError(erro.codigo, erro.mensagem, erro.traceId, restantes), formFormulario);
    if (!tratado) {
      this.falhar(erro);
    }
  }

  /** Tira o erro da modalidade da Ratificação e mostra-o junto do grupo; devolve os restantes. */
  private separarErroModalidade(campos: CampoErro[]): CampoErro[] {
    const erroModalidade = campos.find((campo) => campo.path === CAMPO_MODALIDADE_RATIFICACAO);
    if (!erroModalidade) {
      return campos;
    }
    this.erroModalidadeRatificacao.set(erroModalidade.erro);
    this.ratificacaoRef()?.aplicarErro(erroModalidade.erro);
    return campos.filter((campo) => campo !== erroModalidade);
  }

  /** Desfaz a submissão e deixa logo o formulário editável. */
  resgatar(): void {
    const processo = this.processo();
    if (!processo) {
      return;
    }

    this.erro.set(null);
    this.aResgatar.set(true);
    this.processoService.resgatar(processo.id, processo.versao).subscribe({
      next: (atualizado) => {
        this.aResgatar.set(false);
        this.confirmacaoResgatarAberta.set(false);
        this.aplicarTransicao(atualizado, () => this.iniciarEdicao());
      },
      error: (erro: ApiError) => {
        this.aResgatar.set(false);
        this.confirmacaoResgatarAberta.set(false);
        this.falhar(erro);
      },
    });
  }

  /** Devolve o processo a `EM_SUBMISSAO` e abre logo o formulário, com o foco no título. */
  corrigir(): void {
    const processo = this.processo();
    if (!processo) {
      return;
    }

    this.erro.set(null);
    this.aCorrigir.set(true);
    this.processoService.corrigir(processo.id, processo.versao).subscribe({
      next: (atualizado) => {
        this.aCorrigir.set(false);
        this.confirmacaoCorrigirAberta.set(false);
        this.aplicarTransicao(atualizado, () => {
          this.iniciarEdicao();
          afterNextRender(() => this.formularioRef()?.focarTitulo(), { injector: this.injector });
          this.mostrarSucessoPassageiro('Processo em correção. Corrija e volte a submeter.');
        });
      },
      error: (erro: ApiError) => {
        this.aCorrigir.set(false);
        this.confirmacaoCorrigirAberta.set(false);
        this.falhar(erro);
      },
    });
  }

  /** Na edição grava primeiro o formulário, como «Avançar para Submissão», e devolve com a `versao` nova. */
  confirmarDevolucao(): void {
    this.confirmacaoDevolverAberta.set(false);
    if (this.emEdicao()) {
      this.gravarRascunho(() => this.devolverDgt());
      return;
    }
    this.devolverDgt();
  }

  /** Sem a atribuição a PCM deixa de ver o processo: mostra a confirmação e volta à lista «Para Correção». */
  private devolverDgt(): void {
    const processo = this.processo();
    if (!processo) {
      return;
    }
    this.erro.set(null);
    this.aDevolver.set(true);
    this.processoService.devolverDgt(processo.id, processo.versao).subscribe({
      next: (atualizado) => {
        this.aDevolver.set(false);
        this.emEdicao.set(false);
        this.processo.update((atual) =>
          atual ? { ...atual, ...atualizado, editavel: false, acoesDisponiveis: [], podeSubmeter: false } : atual,
        );
        clearTimeout(this.temporizadorBannerAcao);
        this.mensagemSucesso.set('Processo submetido à DGT.');
        this.temporizadorBannerAcao = setTimeout(
          () => this.router.navigate([this.rotas.PROCESSOS_CORRIGIR]),
          DURACAO_BANNER_ACAO_MS,
        );
      },
      error: (erro: ApiError) => {
        this.aDevolver.set(false);
        this.falhar(erro);
      },
    });
  }

  pedidoApoioEnviado(): void {
    this.pedirApoioAberto.set(false);
    this.mostrarSucessoPassageiro('Pedido enviado à DGT.');
  }

  private mostrarSucessoPassageiro(mensagem: string): void {
    this.mensagemSucesso.set(mensagem);
    clearTimeout(this.temporizadorBannerAcao);
    this.temporizadorBannerAcao = setTimeout(() => this.mensagemSucesso.set(null), DURACAO_BANNER_ACAO_MS);
  }

  /** `false` se algum campo do erro não existe no formulário (ou não há campos): fica também para o banner. */
  private aplicarErrosDeCampo(erro: ApiError, form: FormGroup): boolean {
    const campos = erro.campos ?? [];
    let todosAplicados = campos.length > 0;
    for (const campo of campos) {
      const controlo = form.get(caminhoDoControlo(campo.path));
      if (controlo) {
        controlo.setErrors({ servidor: campo.erro });
      } else {
        todosAplicados = false;
      }
    }
    return todosAplicados;
  }
}
