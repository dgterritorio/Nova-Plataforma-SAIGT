import { NgTemplateOutlet } from '@angular/common';
import { Component, OnDestroy, OnInit, computed, effect, inject, isDevMode, signal, untracked, viewChild } from '@angular/core';
import { FormGroup } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Observable, catchError, forkJoin, of } from 'rxjs';
import { AppShellComponent } from '../../../shared/components/app-shell.component';
import { ConfirmacaoModalComponent } from '../../../shared/components/confirmacao-modal.component';
import { ErrorBannerComponent } from '../../../shared/components/error-banner.component';
import { ReciboSubmissaoComponent } from '../../../shared/components/recibo-submissao.component';
import { AuthService } from '../../../core/auth/auth.service';
import { ApiError, CampoErro } from '../../../core/models/error-response.model';
import {
  AtoReferivel,
  DinamicaEmCurso,
  ElaboracaoNova,
  IgtSnit,
  Instrumento,
  InstrumentoDisponibilidade,
  InstrumentoPainel,
  ProcedimentoDisponivel,
  TipoAtoDisponivel,
  TipoInstrumento,
} from '../../../models/instrumento.model';
import {
  AtualizarDadosFormularioRequest,
  CAMPO_DECLARACAO_TERMINO,
  CONFIRMACAO_CONTEUDO_PROCESSO,
  CONFIRMACAO_DECLARACAO_TERMINO,
  DestinoProcesso,
  DinamicaATerminar,
  CategoriaInstrumento,
  FormularioFixo,
  InstrumentoAbrangido,
  ItemTerritorio,
  PaginaPendencia,
  PendenciaSubmissao,
  Processo,
  ProcessoDetalhe,
  PublicacaoIncmDados,
  SubmeterProcessoRequest,
  TerritorioInstrumento,
} from '../../../models/processo.model';
import { DinamicaSemIgtEmCurso, TiposProcesso } from '../../../models/dinamica.model';
import { DinamicaService } from '../../../services/dinamica.service';
import { InstrumentoService } from '../../../services/instrumento.service';
import { ProcessoService } from '../../../services/processo.service';
import { PlantasService } from '../../../services/plantas.service';
import { ROUTES } from '../../../shared/routes';
import { AlertaComponent } from '../../../shared/ui/alerta/alerta.component';
import { BotaoComponent } from '../../../shared/ui/botao/botao.component';
import { CartaoComponent } from '../../../shared/ui/cartao/cartao.component';
import { Passo, PassosComponent } from '../../../shared/ui/passos/passos.component';
import { AVISO_SEM_FORMULARIO, pedeElementosAfetadosNoFormulario } from '../formulario-fixo';
import { CODIGO_CAMPO_BLOQUEADO, InformacaoBaseAlterada, errosPorCaminho, informacaoBaseDe, separarErrosInformacaoBase } from '../informacao-base';
import { ProcessoDocumentosComponent } from '../processo-documentos/processo-documentos.component';
import { ProcessoMetadadosComponent } from '../processo-metadados/processo-metadados.component';
import { ProcessoMetadadosMotuComponent } from '../processo-metadados-motu/processo-metadados-motu.component';
import { ProcessoPlantasComponent } from '../processo-plantas/processo-plantas.component';
import {
  FormularioInicialValores,
  ProcessoFormularioInicialComponent,
  rotuloParteDr,
  caminhoDoControlo,
  comErroNoTipoAto,
} from '../processo-formulario-inicial/processo-formulario-inicial.component';
import { ProcessoFormularioSrupComponent } from '../processo-formulario-srup/processo-formulario-srup.component';
import { CAMPO_MODALIDADE_RATIFICACAO, FormularioRatificacaoComponent } from '../formularios/formulario-ratificacao.component';
import { FormularioSuspensaoCcdrComponent } from '../formularios/formulario-suspensao-ccdr.component';
import { FormularioGovernoComponent } from '../formularios/formulario-governo.component';
import { Modo, OrigemIgt, PaginaInicial, SubpaginaFormulario } from './criar-processo.tipos';
import { procedimentoSeguinte } from './procedimento-seguinte';
import { resumoAtoReferido } from '../ato-referido';
import { CampoAtoReferidoComponent } from './paginas/campo-ato-referido.component';
import { PaginaAcaoComponent } from './paginas/pagina-acao.component';
import { PaginaContinuarDinamicaComponent } from './paginas/pagina-continuar-dinamica.component';
import { PaginaContinuarSemIgtComponent } from './paginas/pagina-continuar-sem-igt.component';
import {
  PaginaDadosDinamicaSemIgtComponent,
  configuracaoFixa,
  rotuloDescritivoSemIgt,
} from './paginas/pagina-dados-dinamica-sem-igt.component';
import { PREFIXO_IGT_SNIT, PaginaIgtExistenteComponent } from './paginas/pagina-igt-existente.component';
import { PaginaNovaDinamicaComponent } from './paginas/pagina-nova-dinamica.component';
import { PaginaNovoIgtComponent } from './paginas/pagina-novo-igt.component';
import { PaginaOrigemIgtComponent } from './paginas/pagina-origem-igt.component';
import { DeclaracaoTerminoComponent } from '../declaracao-termino/declaracao-termino.component';
import { PaginaResumoComponent } from './paginas/pagina-resumo.component';
import { PaginaTipoIgtComponent } from './paginas/pagina-tipo-igt.component';
import { DesbloquearCampoModalComponent } from '../desbloquear-campo/desbloquear-campo-modal.component';
import { AlvoDesbloqueio, DesbloqueioCampos } from '../desbloquear-campo/desbloqueio-campos';

export const PARAM_INSTRUMENTO = 'instrumentoId';
export const PARAM_DINAMICA = 'dinamicaId';
export const PARAM_PROCEDIMENTO = 'procedimentoId';

const FORMATO_UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

interface EntradaPeloPainel {
  instrumentoId: string;
  dinamicaId: string | null;
  procedimentoId: number | null;
}

const DURACAO_BANNER_ACAO_MS = 4000;
const CODIGO_OPCAO_GEOCONFORM_EM_FALTA = 'OPCAO_GEOCONFORM_EM_FALTA';
const CAMPO_COM_GEOCONFORM = 'comGeoconform';
const CODIGO_PLANTAS_METADADOS_EM_FALTA = 'PLANTAS_METADADOS_EM_FALTA';
const CODIGO_VALIDACAO = 'VALIDACAO';
const CODIGO_CONFIRMACAO_EM_FALTA = 'CONFIRMACAO_EM_FALTA';
const CODIGO_DESIGNACAO_DUPLICADA = 'DESIGNACAO_DUPLICADA';
const CODIGO_REFERENCIA_INVALIDA = 'REFERENCIA_INVALIDA';
const CAMPO_PROCESSO_REFERIDO = 'processoReferidoId';
const SEM_ATOS_REFERIVEIS: AtoReferivel[] = [];
const CAMPO_DESCRITIVO = 'descritivo';
/** Os 409 da criação: a oferta do seletor já não é a atual. `TENTATIVA_EM_CURSO` é o código anterior ao `PROCESSO_EM_CURSO`. */
const CODIGOS_DE_CONFLITO_NA_CRIACAO: readonly string[] = [
  'PROCESSO_EM_CURSO',
  'SEQUENCIAMENTO_NAO_CUMPRIDO',
  'DINAMICA_NAO_ELEGIVEL',
  'ESTADO_INVALIDO',
  'TENTATIVA_EM_CURSO',
];
/** Campos do formulário inicial que a submissão da Publicação da Alteração pode recusar. */
const CAMPOS_RECUSADOS_NA_SUBMISSAO: readonly string[] = [
  'introduzPlantaNova',
  'elementosAfetados',
  'entidadeInstrutora',
  'entidadeEmitente',
  'dinamicaAssociadaId',
  CAMPO_MODALIDADE_RATIFICACAO,
  'tipoAtoId',
];
/** Na Ratificação a data de assinatura está no cabeçalho; nos outros formulários é do cartão INCM do passo 3. */
const CAMPOS_RECUSADOS_NA_SUBMISSAO_DA_RATIFICACAO: readonly string[] = [...CAMPOS_RECUSADOS_NA_SUBMISSAO, 'dataAssinatura'];
/** Na Suspensão da CCDR todos os campos estão no cabeçalho, e a submissão recusa-os juntos. */
const CAMPOS_RECUSADOS_NA_SUBMISSAO_DA_SUSPENSAO_CCDR: readonly string[] = [
  ...CAMPOS_RECUSADOS_NA_SUBMISSAO_DA_RATIFICACAO,
  'contactoTramitacao',
  'contactoPublicacao',
  'normaLegal',
  'tipoInformacao',
  'sumario',
];
/** Nos formulários do Governo também a «Publicação» e a data do suplemento estão no cabeçalho. */
const CAMPOS_RECUSADOS_NA_SUBMISSAO_DO_GOVERNO: readonly string[] = [
  ...CAMPOS_RECUSADOS_NA_SUBMISSAO_DA_SUSPENSAO_CCDR,
  'publicarSuplemento',
  'dataSuplemento',
];
/** A recusa da Dinâmica associada tem código próprio, mas fica junto do seletor como as de `VALIDACAO`. */
const CODIGOS_RECUSA_NO_FORMULARIO: readonly string[] = [CODIGO_VALIDACAO, 'DINAMICA_ASSOCIADA_INVALIDA', 'DADOS_INCM_EM_FALTA'];

/** A mesma referência sempre, para o cartão não refazer as opções a cada deteção de mudanças. */
const SEM_TIPOS_ATO: TipoAtoDisponivel[] = [];

const SUBPAGINA_DA_PENDENCIA: Record<PaginaPendencia, SubpaginaFormulario> = {
  FORMULARIO: 'cabecalho',
  PLANTAS: 'plantas',
  DOCUMENTOS: 'documentos',
  METADADOS: 'metadados',
};

const PAGINA_DA_SUBPAGINA: Record<SubpaginaFormulario, PaginaPendencia> = {
  cabecalho: 'FORMULARIO',
  plantas: 'PLANTAS',
  documentos: 'DOCUMENTOS',
  metadados: 'METADADOS',
};

const PAGINA_ANTERIOR: Record<PaginaInicial, PaginaInicial | null> = {
  tipo: null,
  origem: 'tipo',
  novo: 'origem',
  igt: 'origem',
  acao: 'igt',
  continuar: 'acao',
  nova: 'acao',
  semIgt: 'tipo',
  continuarSemIgt: 'tipo',
};

const PASSO_DA_PAGINA: Record<PaginaInicial, number> = {
  tipo: 0,
  origem: 1,
  novo: 1,
  igt: 1,
  acao: 2,
  continuar: 2,
  nova: 3,
  semIgt: 1,
  continuarSemIgt: 1,
};
const PASSOS_SEM_IGT: readonly string[] = ['Tipo IGT', 'Dinâmica', 'Formulário'];
const TAMANHO_PESQUISA_SNIT = 100;

/**
 * O assistente tem o Figma de `Criar-Processo`: Tipo IGT, IGT Novo/Existente, Ação, Nova Dinâmica (só ao criar
 * uma) e Formulário; o Resumo fica à parte, sem passos. O `passoAtual` conta só as três fases do backend: escolher
 * (1, em várias páginas), Formulário (2) e Resumo (3). A tentativa nasce ao sair da fase 1, porque o backend exige-a
 * antes de `PUT /dados-formulario` ou `PATCH /submeter`; abandonar na fase 2 deixa um rascunho, que se retoma pelo
 * detalhe ou aqui. Um Procedimento com `tentativaEmCurso` é retomado, nunca recriado (o `POST` daria 409).
 *
 * O Formulário tem duas sub-páginas, como UI-051 e UI-052: o cabeçalho (Informação Geral) e os documentos.
 * Mudar de sub-página grava o rascunho, e o cartão do cabeçalho fica montado (escondido) para o `PUT` levar
 * sempre os dois blocos.
 *
 * Dinâmicas, Procedimentos e Tipos de Ato vêm do backend; os campos do Formulário são fixos
 * (`ProcessoFormularioInicialComponent`). Partes do mockup sem suporte real ("Pedido de Apoio",
 * nº de recibo curto) ficam de fora em vez de aparecerem inertes.
 */
@Component({
  selector: 'app-criar-processo',
  standalone: true,
  imports: [
    AppShellComponent,
    CampoAtoReferidoComponent,
    ConfirmacaoModalComponent,
    DeclaracaoTerminoComponent,
    ErrorBannerComponent,
    NgTemplateOutlet,
    PaginaAcaoComponent,
    PaginaContinuarDinamicaComponent,
    PaginaContinuarSemIgtComponent,
    PaginaDadosDinamicaSemIgtComponent,
    PaginaIgtExistenteComponent,
    PaginaNovaDinamicaComponent,
    PaginaNovoIgtComponent,
    PaginaOrigemIgtComponent,
    PaginaResumoComponent,
    PaginaTipoIgtComponent,
    ProcessoDocumentosComponent,
    ProcessoFormularioInicialComponent,
    ProcessoFormularioSrupComponent,
    FormularioRatificacaoComponent,
    FormularioSuspensaoCcdrComponent,
    FormularioGovernoComponent,
    ProcessoMetadadosComponent,
    ProcessoMetadadosMotuComponent,
    ProcessoPlantasComponent,
    ReciboSubmissaoComponent,
    AlertaComponent,
    BotaoComponent,
    CartaoComponent,
    PassosComponent,
    DesbloquearCampoModalComponent,
  ],
  providers: [DesbloqueioCampos],
  templateUrl: './criar-processo.component.html',
  styleUrl: './criar-processo.component.css',
})
export class CriarProcessoComponent implements OnInit, OnDestroy {
  private readonly instrumentoService = inject(InstrumentoService);
  private readonly dinamicaService = inject(DinamicaService);
  private readonly processoService = inject(ProcessoService);
  private readonly authService = inject(AuthService);
  private readonly desbloqueio = inject(DesbloqueioCampos);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly plantasService = inject(PlantasService);
  private temporizadorBannerAcao: ReturnType<typeof setTimeout> | undefined;

  readonly rotas = ROUTES;

  readonly passoAtual = signal<1 | 2 | 3>(1);
  readonly paginaInicial = signal<PaginaInicial>('tipo');
  readonly subpagina = signal<SubpaginaFormulario>('cabecalho');
  readonly documentosRecolhidos = signal(false);
  readonly origemIgt = signal<OrigemIgt | null>(null);
  readonly designacaoNovoIgt = signal('');
  readonly concelhoNovoIgt = signal<string | null>(null);
  readonly concelhosNovoIgt = signal<ItemTerritorio[]>([]);
  readonly aCriarIgt = signal(false);
  /** Só decide se o stepper do Formulário mostra o passo "Nova Dinâmica". */
  readonly viaNovaDinamica = signal(false);
  /** IGT criado agora: não há IGT a escolher nem ação a decidir, por isso o assistente salta para a Nova Dinâmica. */
  readonly viaNovoIgt = signal(false);
  /**
   * IGT novo de um tipo com Elaboração: o assistente vai direto para o formulário, como no Figma, e o primeiro
   * «Guardar» cria o IGT, a Elaboração e a tentativa. Os tipos sem Elaboração seguem pela página «Novo IGT».
   */
  readonly elaboracaoNova = signal<ElaboracaoNova | null>(null);
  readonly viaElaboracaoNova = signal(false);
  readonly aAbrirElaboracao = signal(false);
  private readonly designacaoElaboracao = signal<string | null>(null);
  readonly erro = signal<ApiError | null>(null);

  readonly tipos = signal<TipoInstrumento[]>([]);
  readonly nomesTiposInstrumento = computed<Record<string, string>>(() =>
    Object.fromEntries(this.tipos().map((tipo) => [tipo.codigo, tipo.nome])),
  );
  readonly instrumentos = signal<Instrumento[]>([]);
  readonly aCarregar = signal(true);
  readonly tipoEscolhidoId = signal<number | null>(null);
  readonly instrumentoEscolhidoId = signal<string | null>(null);
  /** IGT em vigor no SNIT do tipo escolhido (API-PROC-030), pesquisados uma vez por tipo. */
  readonly igtSnit = signal<IgtSnit[]>([]);
  private readonly igtSnitDoTipo = signal<number | null>(null);
  readonly aPesquisarSnit = signal(false);
  readonly erroSnit = signal<string | null>(null);
  /** IGT do SNIT que ainda não existe na SAIGT: o «Criar IGT» cria o Instrumento a partir dele. */
  readonly igtSnitEscolhido = signal<IgtSnit | null>(null);
  /** Os que já existem na SAIGT aparecem como Instrumentos, em `instrumentosDoTipo`. */
  readonly igtSnitPorCriar = computed(() => this.igtSnit().filter((igt) => !igt.instrumentoId));

  readonly disponibilidade = signal<InstrumentoDisponibilidade | null>(null);
  /** O último procedimento concluído (UI-049) só existe no painel do Instrumento. */
  readonly painel = signal<InstrumentoPainel | null>(null);
  readonly aCarregarDisponibilidade = signal(false);
  readonly modo = signal<Modo | null>(null);
  readonly dinamicaEscolhida = signal<DinamicaEmCurso | null>(null);
  readonly dinamicaNovaId = signal<number | null>(null);
  readonly descritivoNovo = signal('');
  readonly procedimentoEscolhido = signal<ProcedimentoDisponivel | null>(null);
  /** O ato publicado que o ato pontual escolhido visa; limpa-se sempre que muda o Procedimento. */
  readonly atoReferidoId = signal<string | null>(null);

  readonly confirmacaoAberta = signal(false);
  readonly aIniciar = signal(false);
  readonly aCriar = signal(false);
  readonly aGuardar = signal(false);
  readonly aSubmeter = signal(false);
  readonly aRetomar = signal(false);
  readonly avisoProcedimentoIndisponivel = signal(false);
  readonly confirmado = signal(false);
  readonly declaracaoTermino = signal(false);
  readonly mensagemSucesso = signal<string | null>(null);

  /**
   * `ProcessoDetalhe` só depois de `retomarTentativa()`. `guardarRascunho()`/`avancarParaPublicar()` fundem a
   * resposta do `PUT`, que não traz os campos do detalhe, em vez de a substituírem.
   */
  readonly processoCriado = signal<Processo | ProcessoDetalhe | null>(null);
  /** Detalhe lido logo a seguir a criar: as respostas do `POST` e do `PUT` não trazem o território, o IDUS nem a Fundamentação RJIGT. */
  private readonly detalheLido = signal<ProcessoDetalhe | null>(null);
  readonly submissaoConcluida = signal<(Processo & { idus?: string | null; submissao?: number }) | null>(null);
  /** Aberto a partir do detalhe (`?processoId=`), já no passo 3: não há escolhas do passo 1 para onde voltar. */
  readonly entradaPeloDetalhe = signal(false);

  readonly formularioRef = viewChild(ProcessoFormularioInicialComponent);
  readonly ratificacaoRef = viewChild(FormularioRatificacaoComponent);
  private readonly erroModalidadeGravacao = signal<string | null>(null);
  readonly documentosRef = viewChild(ProcessoDocumentosComponent);

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
    this.processoCriado.update((atual) => (atual ? { ...atual, versao: gravado.versao, destino: gravado.destino } : atual));
  }
  readonly plantasRef = viewChild(ProcessoPlantasComponent);
  readonly metadadosRef = viewChild(ProcessoMetadadosComponent);
  readonly metadadosMotuRef = viewChild(ProcessoMetadadosMotuComponent);
  private readonly fichaMotu = signal<{ processoId: string; aplica: boolean } | null>(null);
  /** Nos procedimentos finais com peças gráficas de publicação a ficha é a MOTU e não a ficha única; `null` até a API responder. */
  readonly aplicaFichaMotu = computed(() => {
    const ficha = this.fichaMotu();
    return ficha !== null && ficha.processoId === this.processoCriado()?.id ? ficha.aplica : null;
  });
  private readonly lerFichaMotu = effect(() => {
    const processoId = this.processoCriado()?.id;
    if (this.subpagina() === 'metadados' && processoId && untracked(this.aplicaFichaMotu) === null) {
      this.plantasService.obterMetadadosMotu(processoId).subscribe({
        next: (ficha) => this.fichaMotu.set({ processoId, aplica: ficha.aplicaFichaMotu }),
        error: (erro: ApiError) => this.erro.set(erro),
      });
    }
  });
  /** Campos da ficha de metadados em falta na submissão, para os mostrar junto de cada um. */
  readonly errosMetadados = signal<{ path: string; erro: string }[] | null>(null);

  /**
   * Só muda ao retomar ou depois de gravar: o cartão é recriado ao voltar do passo 3 e repõe-se
   * daqui, sem apagar o que se escreve entretanto.
   */
  readonly valoresIniciais = signal<FormularioInicialValores | null>(null);
  /** O que ficou gravado; é o que o passo 3 mostra e submete. */
  readonly publicacaoIncm = signal<PublicacaoIncmDados | null>(null);
  /** Como `valoresIniciais`: só muda ao retomar ou depois de gravar, para repor o bloco sem apagar o que se escreve. */
  readonly informacaoBaseGravada = signal<InformacaoBaseAlterada>({ informacaoBase: null, dataAssinatura: null });
  /** Última alteração do bloco; nula enquanto não se escreve nada, e então grava-se o que já estava gravado. */
  readonly informacaoBaseEmEdicao = signal<InformacaoBaseAlterada | null>(null);
  readonly errosInformacaoBase = signal<Record<string, string>>({});

  /** `null` enquanto se lê; sem resposta (ex.: 403 de um utilizador interno) o grupo com IGT mostra todos os tipos, como antes. */
  readonly tiposProcesso = signal<TiposProcesso | null>(null);
  private readonly estadoTiposProcesso = signal<'aCarregar' | 'lido' | 'erro'>('aCarregar');
  readonly tiposComAssociacao = computed(() => {
    const tiposProcesso = this.tiposProcesso();
    switch (this.estadoTiposProcesso()) {
      case 'aCarregar':
        return [];
      case 'erro':
        return this.tipos();
      default: {
        const ids = new Set((tiposProcesso?.comAssociacaoIgt ?? []).map((tipo) => tipo.tipoInstrumentoId));
        return this.tipos().filter((tipo) => ids.has(tipo.id));
      }
    }
  });
  readonly aCarregarTipos = computed(() => this.aCarregar() || this.estadoTiposProcesso() === 'aCarregar');
  readonly tiposSemAssociacao = computed(() => this.tiposProcesso()?.semAssociacaoIgt ?? []);
  /** Código do tipo de Dinâmica escolhido no grupo «Processo sem associação a IGT». */
  readonly tipoSemIgtEscolhido = signal<string | null>(null);
  readonly tipoSemIgt = computed(() => this.tiposSemAssociacao().find((tipo) => tipo.codigo === this.tipoSemIgtEscolhido()) ?? null);
  readonly configuracaoSemIgtId = signal<number | null>(null);
  readonly descritivoSemIgt = signal('');
  readonly dinamicasSemIgt = signal<DinamicaSemIgtEmCurso[]>([]);
  /** No caminho sem IGT: a Dinâmica escolhida vem de `GET /api/v1/dinamicas/disponivel` e não do `/disponivel` de um Instrumento. */
  readonly viaSemIgt = signal(false);
  /** O pop-up de confirmação serve o início da Dinâmica sem IGT e a criação do processo. */
  private readonly confirmacaoDoInicioSemIgt = signal(false);
  readonly erroDescritivoSemIgt = computed(() => {
    const erro = this.erro();
    if (!erro || (erro.codigo !== CODIGO_DESIGNACAO_DUPLICADA && erro.codigo !== CODIGO_VALIDACAO)) {
      return null;
    }
    const campo = erro.campos?.find((item) => item.path === CAMPO_DESCRITIVO)?.erro;
    return campo ?? (erro.codigo === CODIGO_DESIGNACAO_DUPLICADA ? erro.mensagem : null);
  });

  readonly instrumentosDoTipo = computed(() =>
    this.instrumentos().filter((instrumento) => instrumento.tipoInstrumentoId === this.tipoEscolhidoId()),
  );

  readonly tipoEscolhido = computed(() => this.tipos().find((tipo) => tipo.id === this.tipoEscolhidoId()) ?? null);
  readonly procedimentoEscolhidoValor = computed(() => {
    const procedimento = this.procedimentoEscolhido();
    return procedimento ? String(procedimento.instrumentoProcedimentoConfigId) : null;
  });

  /** Mais recente primeiro: a mais provável de ser aquela a que o utilizador quer voltar. */
  private readonly dinamicasEmCursoOrdenadas = computed(() =>
    [...(this.disponibilidade()?.dinamicasEmCurso ?? [])].sort(
      (a, b) => new Date(b.dataInicio).getTime() - new Date(a.dataInicio).getTime(),
    ),
  );
  /** Uma Dinâmica caducada só se mostra; continuá-la não tem ecrã. */
  readonly dinamicasAbertas = computed(() => this.dinamicasEmCursoOrdenadas().filter((dinamica) => dinamica.estado !== 'CADUCADA'));
  readonly dinamicasCaducadas = computed(() => this.dinamicasEmCursoOrdenadas().filter((dinamica) => dinamica.estado === 'CADUCADA'));
  readonly dinamicasProrrogaveis = computed(() =>
    this.dinamicasEmCursoOrdenadas()
      .filter((dinamica) => dinamica.estado === 'TERMINADA')
      .map((dinamica) => dinamica.dinamicaId),
  );

  readonly procedimentosDaDinamica = computed(() =>
    [...(this.dinamicaEscolhida()?.procedimentosDisponiveis ?? [])].sort((a, b) => a.ordem - b.ordem),
  );

  /** Um ato pontual com rascunho retoma-se: o ato visado já ficou gravado na criação. */
  readonly pedeAtoReferido = computed(() => {
    const procedimento = this.procedimentoEscolhido();
    return !!procedimento?.atoPontual && !procedimento.tentativaEmCurso;
  });
  readonly opcoesAtoReferido = computed<AtoReferivel[]>(() =>
    this.pedeAtoReferido() ? (this.procedimentoEscolhido()?.atosReferiveis ?? SEM_ATOS_REFERIVEIS) : SEM_ATOS_REFERIVEIS,
  );
  /** Com um só ato publicado, fica já escolhido. */
  readonly atoReferido = computed<AtoReferivel | null>(() => {
    const opcoes = this.opcoesAtoReferido();
    return opcoes.find((ato) => ato.processoId === this.atoReferidoId()) ?? (opcoes.length === 1 ? opcoes[0] : null);
  });
  /** Nomes dos tipos de Procedimento pelo código, a partir do painel e da oferta; o `AtoReferivel` só traz o código. */
  readonly nomesTiposProcedimento = computed<Record<string, string>>(() => {
    const nomes: Record<string, string> = {};
    for (const dinamica of this.painel()?.dinamicas ?? []) {
      for (const procedimento of dinamica.procedimentos) {
        nomes[procedimento.tipoProcedimento] = procedimento.nomeProcedimento;
      }
    }
    for (const procedimento of this.dinamicaEscolhida()?.procedimentosDisponiveis ?? []) {
      nomes[procedimento.tipoProcedimento] = procedimento.nomeProcedimento;
    }
    return nomes;
  });
  /** Fica num alerta próprio e não no banner: o ato pode ter deixado de ser referível. */
  readonly erroAtoReferido = computed(() => {
    const erro = this.erro();
    return erro?.codigo === CODIGO_REFERENCIA_INVALIDA
      ? (erro.campos?.find((campo) => campo.path === CAMPO_PROCESSO_REFERIDO)?.erro ?? erro.mensagem)
      : null;
  });
  /** No resumo dos passos 2 e 3: do detalhe gravado, ou do ato escolhido enquanto o detalhe não chega. */
  readonly resumoAtoReferido = computed<string | null>(() => {
    const detalhe = this.detalheDoFormularioSinal();
    if (detalhe) {
      return detalhe.atoPontual && detalhe.numeroProcessoReferido
        ? resumoAtoReferido(detalhe.numeroProcessoReferido, detalhe.tipoProcedimentoReferido, this.nomesTiposProcedimento())
        : null;
    }
    const ato = this.processoCriado()?.processoReferidoId ? this.atoReferido() : null;
    return ato ? resumoAtoReferido(ato.numeroProcesso, ato.tipoProcedimento, this.nomesTiposProcedimento()) : null;
  });

  /** Por Dinâmica, o Procedimento publicado de maior `ordem`. */
  readonly ultimosConcluidos = computed<Record<string, string>>(() => {
    const resultado: Record<string, string> = {};
    for (const dinamica of this.painel()?.dinamicas ?? []) {
      const publicados = dinamica.procedimentos.filter((procedimento) => procedimento.processos.some((p) => p.estado === 'PUBLICADO'));
      const ultimo = publicados.sort((a, b) => b.ordem - a.ordem)[0];
      if (dinamica.id && ultimo) {
        resultado[dinamica.id] = ultimo.nomeProcedimento;
      }
    }
    return resultado;
  });

  readonly mensagemConfirmacao = computed(() => {
    const tipoSemIgt = this.tipoSemIgt();
    if (this.confirmacaoDoInicioSemIgt() && tipoSemIgt) {
      const campo = rotuloDescritivoSemIgt(tipoSemIgt.codigo) === 'Designação' ? 'a designação' : 'o descritivo';
      return (
        `Pretende iniciar a dinâmica «${tipoSemIgt.nome}» com ${campo} ` +
        `«${this.descritivoSemIgt().trim()}»? Os planos ou programas abrangidos associam-se depois do formulário inicial. ` +
        'Se detetar alguma anormalidade, contacte a DGT através de um pedido de apoio.'
      );
    }
    const atoReferido = this.pedeAtoReferido() ? this.atoReferido() : null;
    if (atoReferido) {
      return (
        `Vai criar «${this.procedimentoEscolhido()?.nomeProcedimento ?? ''}» sobre o ` +
        `${resumoAtoReferido(atoReferido.numeroProcesso, atoReferido.tipoProcedimento, this.nomesTiposProcedimento())}. ` +
        'Pode contactar a DGT por um pedido de apoio se detetar alguma anomalia.'
      );
    }
    const nomeProcedimento = this.procedimentoEscolhido()?.nomeProcedimento ?? '';
    const nomeDinamica = this.dinamicaEscolhida()?.nomeDinamica ?? '';
    // O procedimento final da Elaboração, da Revisão e da Alteração tem o nome da própria dinâmica.
    const pedido = nomeDinamica && nomeProcedimento === nomeDinamica ?`${nomeDinamica} (procedimento final)` : `${nomeProcedimento} — ${nomeDinamica}`;
    return (
      `Pretende avançar com o pedido «${pedido}»? Confirme que é esta a dinâmica decidida na deliberação. ` +
      'Se detetar alguma anormalidade, contacte a DGT através de um pedido de apoio.'
    );
  });

  readonly estadoPassos = computed<{ passos: Passo[]; atual: number }>(() => {
    const pagina = this.paginaInicial();
    const noFormulario = this.passoAtual() > 1;
    const semIgt =
      noFormulario ? this.viaSemIgt() : pagina === 'semIgt' || pagina === 'continuarSemIgt' || (pagina === 'tipo' && this.tipoSemIgt() !== null);
    if (semIgt) {
      return { passos: PASSOS_SEM_IGT.map((etiqueta) => ({ etiqueta })), atual: noFormulario ? 2 : PASSO_DA_PAGINA[pagina] };
    }
    if (!noFormulario && (pagina === 'tipo' || pagina === 'origem' || pagina === 'novo')) {
      return { passos: ['Tipo IGT', 'IGT Novo/Existente', 'Formulário'].map((etiqueta) => ({ etiqueta })), atual: PASSO_DA_PAGINA[pagina] };
    }
    if (this.viaElaboracaoNova() && noFormulario) {
      return { passos: ['Tipo IGT', 'Novo IGT', 'Formulário'].map((etiqueta) => ({ etiqueta })), atual: 2 };
    }
    if (this.viaNovoIgt()) {
      return { passos: ['Tipo IGT', 'Novo IGT', 'Nova Dinâmica', 'Formulário'].map((etiqueta) => ({ etiqueta })), atual: noFormulario ? 3 : 2 };
    }
    const comNovaDinamica = noFormulario ? this.viaNovaDinamica() : pagina === 'nova';
    const etiquetas = ['Tipo IGT', 'IGT Existente', 'Ação', ...(comNovaDinamica ? ['Nova Dinâmica'] : []), 'Formulário'];
    return { passos: etiquetas.map((etiqueta) => ({ etiqueta })), atual: noFormulario ? etiquetas.length - 1 : PASSO_DA_PAGINA[pagina] };
  });

  readonly podeAvancar = computed(() => {
    switch (this.paginaInicial()) {
      case 'tipo':
        return this.tipoEscolhidoId() !== null || this.tipoSemIgt() !== null;
      case 'semIgt':
        return this.configuracaoSemIgtId() !== null && this.descritivoSemIgt().trim().length > 0 && !this.aIniciar();
      case 'continuarSemIgt':
        return !!this.procedimentoEscolhido()?.tentativaEmCurso || this.podeAvancarDoPasso1();
      case 'origem':
        return this.origemIgt() === 'existente' || (this.origemIgt() === 'novo' && this.podeNovoIgt());
      case 'novo':
        return this.designacaoNovoIgt().trim().length > 0 && this.concelhoNovoIgt() !== null && !this.aCriarIgt();
      case 'igt':
        if (this.igtSnitEscolhido()) {
          return this.podeNovoIgt() && !this.aCriarIgt();
        }
        return this.instrumentoEscolhidoId() !== null && this.disponibilidade() !== null && !this.aCarregarDisponibilidade();
      case 'acao':
        return this.modo() !== null;
      case 'continuar':
        return !!this.procedimentoEscolhido()?.tentativaEmCurso || this.podeAvancarDoPasso1();
      case 'nova':
        return this.dinamicaNovaId() !== null && this.descritivoNovo().trim().length > 0;
    }
  });

  readonly rotuloAvancar = computed(() => {
    const pagina = this.paginaInicial();
    if (pagina === 'novo' || (pagina === 'igt' && this.igtSnitEscolhido())) {
      return this.aCriarIgt() ? 'A criar…' : 'Criar IGT';
    }
    if (pagina === 'nova') {
      return this.aIniciar() ? 'A iniciar…' : 'Iniciar Dinâmica';
    }
    if ((pagina === 'continuar' || pagina === 'continuarSemIgt') && this.procedimentoEscolhido()?.tentativaEmCurso) {
      return this.aRetomar() ? 'A abrir…' : 'Retomar rascunho';
    }
    if (pagina === 'semIgt' && this.aIniciar()) {
      return 'A iniciar…';
    }
    return 'Avançar';
  });

  /** Criar o IGT e iniciar a Dinâmica têm efeitos no backend: só o botão com o nome da ação os faz, nunca o "Seguinte". */
  readonly seguinteDesativado = computed(() => {
    if (this.ocupado()) {
      return true;
    }
    const pagina = this.paginaInicial();
    return (
      this.passoAtual() === 1 &&
      (!this.podeAvancar() || pagina === 'novo' || pagina === 'nova' || (pagina === 'igt' && this.igtSnitEscolhido() !== null))
    );
  });

  readonly aCarregarAvancar = computed(() => {
    const pagina = this.paginaInicial();
    return (
      (pagina === 'origem' && this.aAbrirElaboracao()) ||
      ((pagina === 'novo' || pagina === 'igt') && this.aCriarIgt()) ||
      ((pagina === 'nova' || pagina === 'semIgt') && this.aIniciar()) ||
      ((pagina === 'continuar' || pagina === 'continuarSemIgt') && this.aRetomar())
    );
  });

  /** Diz porque é que o Avançar está desativado: sem isto, o botão cinzento parece avariado. */
  readonly dicaAvancar = computed<string | null>(() => {
    if (this.passoAtual() !== 1 || this.podeAvancar()) {
      return null;
    }
    switch (this.paginaInicial()) {
      case 'tipo':
        return 'Escolha o tipo de IGT para continuar.';
      case 'origem':
        return this.origemIgt() === 'novo' ? 'Só quem age por uma entidade pode criar um IGT.' : 'Escolha se o IGT é novo ou existente.';
      case 'novo':
        return this.designacaoNovoIgt().trim() ? 'Escolha o concelho do IGT.' : 'Indique a designação do IGT.';
      case 'igt':
        if (this.aCarregarDisponibilidade() || this.aCriarIgt()) {
          return null;
        }
        if (this.igtSnitEscolhido()) {
          return 'Só quem age por uma entidade pode criar um IGT.';
        }
        if (this.instrumentosDoTipo().length === 0 && this.igtSnitPorCriar().length === 0) {
          if (this.aPesquisarSnit()) {
            return null;
          }
          return 'Ainda não há IGT deste tipo: volte e escolha «Novo IGT».';
        }
        return this.instrumentoEscolhidoId() === null
          ? 'Escolha um IGT para continuar.'
          : 'Não foi possível carregar as dinâmicas deste IGT. Escolha outro ou volte atrás e tente de novo.';
      case 'acao':
        return this.disponibilidade() && !this.disponibilidade()?.dinamicasDisponiveis.length && !this.dinamicasAbertas().length
          ? 'Este IGT não tem dinâmicas disponíveis nem em curso.'
          : 'Escolha o que pretende fazer.';
      case 'semIgt':
        if (this.aIniciar()) {
          return null;
        }
        if (this.configuracaoSemIgtId() === null) {
          return 'Escolha o tipo de instrumento.';
        }
        return rotuloDescritivoSemIgt(this.tipoSemIgt()?.codigo) === 'Designação'
          ? 'Escreva a designação da dinâmica.'
          : 'Escreva o descritivo da dinâmica.';
      case 'continuar':
      case 'continuarSemIgt':
        if (!this.dinamicaEscolhida()) {
          return 'Escolha a dinâmica em curso.';
        }
        if (!this.procedimentoEscolhido()) {
          return 'Escolha o procedimento a iniciar.';
        }
        return this.pedeAtoReferido() && !this.atoReferido() ? 'Escolha o ato publicado a que respeita.' : null;
      case 'nova':
        return this.dinamicaNovaId() === null ? 'Escolha o tipo de dinâmica.' : 'Escreva o descritivo da dinâmica.';
    }
  });

  readonly podeNovoIgt = computed(() => this.authService.podeCriarInstrumento() && this.authService.perfilInterno() === null);
  readonly nomeEntidade = computed(() => {
    const entidade = this.authService.entidade();
    return entidade ? `${entidade.nome} (${entidade.nipc})` : '—';
  });
  readonly erroDesignacao = computed(() => this.erro()?.campos?.find((campo) => campo.path === 'designacao')?.erro ?? null);
  readonly erroConcelho = computed(() => this.erro()?.campos?.find((campo) => campo.path.startsWith('concelhos'))?.erro ?? null);

  /** A resposta do `POST` (tentativa acabada de criar pelo próprio utilizador) não traz `editavel` nem `acoesDisponiveis`. */
  readonly podeEditar = computed(() => {
    const processo = this.processoCriado();
    return processo !== null && 'editavel' in processo ? processo.editavel : true;
  });

  readonly formulario = computed<FormularioFixo | null>(() => {
    const processo = this.processoCriado();
    return processo !== null && 'formulario' in processo
      ? processo.formulario
      : (this.procedimentoEscolhido()?.formulario ?? null);
  });
  readonly temFormulario = computed(() => this.formulario() !== null);
  readonly avisoSemFormulario = AVISO_SEM_FORMULARIO;

  /** Só com o detalhe lido: a resposta do `POST` e a de um `PATCH` não trazem `acoesDisponiveis`. */
  private readonly alvoDesbloqueio = computed<AlvoDesbloqueio | null>(() => {
    const processo = this.processoCriado();
    return processo !== null && 'acoesDisponiveis' in processo && processo.acoesDisponiveis.includes('desbloquear-campo')
      ? { processoId: processo.id, versao: processo.versao }
      : null;
  });

  readonly submeterDisponivel = computed(() => {
    const processo = this.processoCriado();
    return processo !== null && 'acoesDisponiveis' in processo ? processo.acoesDisponiveis.includes('submeter') : true;
  });

  /** O passo 3 só se abre depois de reler o detalhe; sem ele, o backend ainda não disse nada e a submissão não se bloqueia. */
  readonly podeSubmeter = computed(() => {
    const processo = this.processoCriado();
    return processo !== null && 'podeSubmeter' in processo ? processo.podeSubmeter : true;
  });

  readonly pendenciasSubmissao = computed(() => {
    const processo = this.processoCriado();
    return processo !== null && 'pendenciasSubmissao' in processo ? (processo.pendenciasSubmissao ?? []) : [];
  });

  /** A pergunta do GeoConform está no formulário e grava-se com ele; a submissão usa a resposta gravada na Dinâmica. */
  readonly exigeOpcaoGeoconform = computed(
    () => this.detalheDoFormularioSinal()?.exigeOpcaoGeoconform ?? this.elaboracaoNova()?.exigeOpcaoGeoconform ?? false,
  );
  readonly comGeoconformGravado = computed(() => this.detalheDoFormularioSinal()?.comGeoconform ?? null);
  private readonly detalheDoFormularioSinal = computed(() => this.detalheCriado() ?? this.detalheLido());
  /** Na Elaboração de um IGT novo ainda não há detalhe: a Elaboração não pergunta pelos elementos afetados. */
  readonly pedeElementosAfetados = computed(() =>
    pedeElementosAfetadosNoFormulario(
      this.formulario(),
      this.detalheDoFormularioSinal()?.exigeElementosAfetados ?? this.elaboracaoNova() === null,
    ),
  );
  /** Fica junto do grupo e não no banner. */
  readonly erroOpcaoGeoconform = computed(() => {
    const erro = this.erro();
    return erro?.codigo === CODIGO_OPCAO_GEOCONFORM_EM_FALTA
      ? (erro.campos?.find((campo) => campo.path === CAMPO_COM_GEOCONFORM)?.erro ?? erro.mensagem)
      : null;
  });

  /** Na Revogação do IGT com outras Dinâmicas em curso, o passo 3 pede a declaração de término; o detalhe diz quais. */
  readonly dinamicasATerminar = computed<DinamicaATerminar[]>(() => {
    const processo = this.processoCriado();
    return processo !== null && 'dinamicasATerminar' in processo ? (processo.dinamicasATerminar ?? []) : [];
  });
  readonly exigeDeclaracaoTermino = computed(() => this.dinamicasATerminar().length > 0);

  /** Fica junto da caixa da declaração de término e não no banner. */
  readonly erroDeclaracaoTermino = computed(() => {
    const erro = this.erro();
    return erro?.codigo === CODIGO_CONFIRMACAO_EM_FALTA
      ? (erro.campos?.find((campo) => campo.path === CAMPO_DECLARACAO_TERMINO)?.erro ?? null)
      : null;
  });

  /** Fica junto da caixa de confirmação do passo 3 e não no banner. */
  readonly erroConfirmacao = computed(() => {
    const erro = this.erro();
    return erro?.codigo === CODIGO_CONFIRMACAO_EM_FALTA && !this.erroDeclaracaoTermino()
      ? (erro.campos?.find((campo) => campo.path === `confirmacoes.${CONFIRMACAO_CONTEUDO_PROCESSO}`)?.erro ?? erro.mensagem)
      : null;
  });

  readonly confirmacoesFeitas = computed(
    () => this.confirmado() && (!this.exigeDeclaracaoTermino() || this.declaracaoTermino()),
  );

  /** A recusa da submissão só por estes campos fica junto dos controlos, no passo 2, e não no banner. */
  readonly errosFormularioSubmissao = computed<CampoErro[]>(() => {
    const erro = this.erro();
    const campos = erro && CODIGOS_RECUSA_NO_FORMULARIO.includes(erro.codigo) ? (erro.campos ?? []) : [];
    const recusaveis = this.camposRecusaveisNaSubmissao();
    return campos.length > 0 && campos.every((campo) => recusaveis.includes(campo.path)) ? campos : [];
  });
  /** Da gravação ou da submissão; fica junto do grupo da secção «Ratificação». */
  readonly erroModalidadeRatificacao = computed(
    () =>
      this.erroModalidadeGravacao() ??
      this.errosFormularioSubmissao().find((campo) => campo.path === CAMPO_MODALIDADE_RATIFICACAO)?.erro ??
      null,
  );

  readonly ocupado = computed(
    () =>
      this.aCriar() ||
      this.aCriarIgt() ||
      this.aGuardar() ||
      this.aSubmeter() ||
      this.aIniciar() ||
      this.aRetomar() ||
      this.aAbrirElaboracao(),
  );

  readonly podeAvancarDoPasso1 = computed(
    () =>
      this.dinamicaEscolhida() !== null &&
      this.procedimentoEscolhido() !== null &&
      (!this.pedeAtoReferido() || this.atoReferido() !== null),
  );

  ngOnInit(): void {
    this.desbloqueio.ligar(this.alvoDesbloqueio, (desbloqueado) =>
      this.processoCriado.update((atual) =>
        atual ? { ...atual, versao: desbloqueado.versao, camposBloqueados: desbloqueado.camposBloqueados } : atual,
      ),
    );
    const processoId = this.route.snapshot.queryParamMap.get('processoId');
    if (processoId) {
      this.abrirNoPublicar(processoId);
    }
    const entrada = processoId ? null : this.lerEntradaPeloPainel();
    this.aCarregar.set(true);
    this.instrumentoService.listarTipos().subscribe({
      next: (tipos) => this.tipos.set(tipos),
      error: (erro: ApiError) => this.erro.set(erro),
    });
    if (!processoId) {
      this.carregarTiposProcesso();
      this.carregarDinamicasSemIgt();
    }
    this.instrumentoService.listar(0, 100).subscribe({
      next: (pagina) => {
        this.instrumentos.set(pagina.content);
        this.aCarregar.set(false);
        if (entrada) {
          this.entrarPeloInstrumento(entrada);
        }
      },
      error: (erro: ApiError) => {
        this.erro.set(erro);
        this.aCarregar.set(false);
      },
    });
  }

  ngOnDestroy(): void {
    clearTimeout(this.temporizadorBannerAcao);
  }

  instrumentoEscolhido(): Instrumento | null {
    return this.instrumentos().find((instrumento) => instrumento.id === this.instrumentoEscolhidoId()) ?? null;
  }

  /** Sem escolhas do passo 1 (entrada pelo detalhe), o resumo lê-se do `ProcessoDetalhe`. */
  nomeTipoInstrumento(): string {
    const codigo = this.detalheDoFormulario()?.tipoInstrumento;
    const nomePeloCodigo = codigo ? (this.tipos().find((tipo) => tipo.codigo === codigo)?.nome ?? codigo) : null;
    return this.instrumentoEscolhido()?.tipoInstrumentoNome ?? nomePeloCodigo ?? this.tipoEscolhido()?.nome ?? '—';
  }

  nomeDinamica(): string | null {
    return this.dinamicaEscolhida()?.nomeDinamica ?? this.detalheDoFormulario()?.nomeDinamica ?? null;
  }

  /** Antes de o processo existir vale a do tipo escolhido no passo 1; ao retomar, a do detalhe. */
  categoriaInstrumento(): CategoriaInstrumento | null {
    return this.detalheDoFormulario()?.categoriaInstrumento ?? this.tipoEscolhido()?.categoria ?? null;
  }

  entidadeInstrutoraObrigatoria(): boolean {
    return this.detalheDoFormulario()?.entidadeInstrutoraObrigatoria ?? this.tipoEscolhido()?.entidadeInstrutoraObrigatoria ?? true;
  }

  private camposRecusaveisNaSubmissao(): readonly string[] {
    switch (this.formulario()) {
      case 'RATIFICACAO':
        return CAMPOS_RECUSADOS_NA_SUBMISSAO_DA_RATIFICACAO;
      case 'SUSPENSAO_CCDR':
        return CAMPOS_RECUSADOS_NA_SUBMISSAO_DA_SUSPENSAO_CCDR;
      case 'SUSPENSAO_MP_GOVERNO':
      case 'PRORROGACAO_GOVERNO':
        return CAMPOS_RECUSADOS_NA_SUBMISSAO_DO_GOVERNO;
      default:
        // Sem Informação Base no Procedimento, a data está no cabeçalho, como na Ratificação.
        return this.detalheDoFormulario()?.dataAssinaturaNoFormulario
          ? CAMPOS_RECUSADOS_NA_SUBMISSAO_DA_RATIFICACAO
          : CAMPOS_RECUSADOS_NA_SUBMISSAO;
    }
  }

  tipoProcedimento(): string {
    return this.procedimentoEscolhido()?.nomeProcedimento ?? this.detalheDoFormulario()?.nomeProcedimento ?? '—';
  }

  /** Na REN o «Tipo de procedimento» do formulário é a Dinâmica (EF v1.5: «Tipo de dinâmica REN»). */
  tipoProcedimentoDoFormulario(): string {
    if (this.formulario() === 'REN') {
      return this.detalheDoFormulario()?.nomeDinamica ?? this.dinamicaEscolhida()?.nomeDinamica ?? '—';
    }
    return this.tipoProcedimento();
  }

  referenciaPlano(): string {
    return (
      this.instrumentoEscolhido()?.designacao ??
      this.detalheDoFormulario()?.designacaoInstrumento ??
      this.designacaoElaboracao() ??
      '—'
    );
  }
  
  territorio(): TerritorioInstrumento | null {
    return this.detalheDoFormulario()?.territorio ?? null;
  }

  concelhos(): string | null {
    const concelhos = this.territorio()?.concelhos ?? [];
    return concelhos.length > 0 ? concelhos.map((concelho) => concelho.nome ?? concelho.codigo).join(', ') : null;
  }

  detalheDoFormulario(): ProcessoDetalhe | null {
    return this.detalheCriado() ?? this.detalheLido();
  }

  idus(): string | null {
    return this.detalheDoFormulario()?.idus ?? null;
  }

  serieDrEscolhida(): string | null {
    return this.procedimentoEscolhido()?.serieDr ?? this.detalheDoFormulario()?.serieDr ?? null;
  }

  parteDr(): string {
    return rotuloParteDr(this.serieDrEscolhida());
  }

  /** O tipo de ato escolhe-se no cabeçalho: o nome gravado e as opções vêm do detalhe do processo. */
  nomeTipoAtoGravado(): string | null {
    const processo = this.processoCriado();
    const doDetalhe = processo && 'tipoAtoNome' in processo ? processo.tipoAtoNome : null;
    return doDetalhe ?? this.detalheDoFormularioSinal()?.tipoAtoNome ?? null;
  }

  tiposAtoDoCabecalho(): TipoAtoDisponivel[] {
    return this.detalheDoFormularioSinal()?.tiposAtoDisponiveis ?? SEM_TIPOS_ATO;
  }

  escolherTipo(tipo: TipoInstrumento): void {
    this.selecionarTipo(String(tipo.id));
  }

  selecionarTipo(valor: string | null): void {
    this.tipoSemIgtEscolhido.set(null);
    this.tipoEscolhidoId.set(valor ? Number(valor) : null);
    this.instrumentoEscolhidoId.set(null);
    this.igtSnitEscolhido.set(null);
    this.viaNovoIgt.set(false);
    this.sairDaElaboracaoNova();
    this.limparEscolhasDeDinamica();
    this.disponibilidade.set(null);
    this.painel.set(null);
  }

  /** Uma configuração só se pede quando o tipo de instrumento é escolhido (Ratificação); com uma só, fica já escolhida. */
  selecionarTipoSemIgt(codigo: string | null): void {
    this.selecionarTipo(null);
    this.tipoSemIgtEscolhido.set(codigo);
    const tipo = this.tipoSemIgt();
    const unica = tipo?.configuracoes.length === 1 ? tipo.configuracoes[0] : null;
    this.configuracaoSemIgtId.set((configuracaoFixa(tipo) ?? unica)?.instrumentoDinamicaConfigId ?? null);
    this.descritivoSemIgt.set('');
  }

  selecionarConfiguracaoSemIgt(valor: string | null): void {
    this.configuracaoSemIgtId.set(valor ? Number(valor) : null);
  }

  alterarDescritivoSemIgt(valor: string): void {
    this.descritivoSemIgt.set(valor);
    if (this.erroDescritivoSemIgt()) {
      this.erro.set(null);
    }
  }

  abrirContinuarSemIgt(): void {
    this.selecionarTipo(null);
    this.viaSemIgt.set(true);
    this.carregarConcelhosNovoIgt();
    this.paginaInicial.set('continuarSemIgt');
  }

  selecionarInstrumento(valor: string | null): void {
    const doSnit = !!valor?.startsWith(PREFIXO_IGT_SNIT);
    this.igtSnitEscolhido.set(doSnit ? (this.igtSnitPorCriar().find((igt) => PREFIXO_IGT_SNIT + igt.idIgtSnit === valor) ?? null) : null);
    this.instrumentoEscolhidoId.set(doSnit ? null : valor || null);
    this.limparEscolhasDeDinamica();
    this.disponibilidade.set(null);
    this.painel.set(null);
    if (this.instrumentoEscolhidoId()) {
      this.carregarDisponibilidade();
    }
  }

  escolherOrigem(origem: OrigemIgt): void {
    this.origemIgt.set(origem);
  }

  escolherModo(modo: Modo): void {
    this.limparEscolhasDeDinamica();
    this.modo.set(modo);
  }

  selecionarDinamica(valor: string | null): void {
    const dinamicas = this.viaSemIgt() ? this.dinamicasSemIgt() : this.dinamicasAbertas();
    const dinamica = dinamicas.find((item) => item.dinamicaId === valor) ?? null;
    this.dinamicaEscolhida.set(dinamica);
    this.procedimentoEscolhido.set(null);
    const seguinte = procedimentoSeguinte(dinamica?.procedimentosDisponiveis ?? []);
    if (seguinte) {
      this.selecionarProcedimento(String(seguinte.instrumentoProcedimentoConfigId));
    }
  }

  avancar(): void {
    if (!this.podeAvancar()) {
      return;
    }
    switch (this.paginaInicial()) {
      case 'tipo':
        if (this.tipoSemIgt()) {
          this.erro.set(null);
          this.viaSemIgt.set(true);
          this.paginaInicial.set('semIgt');
        } else {
          this.paginaInicial.set('origem');
        }
        break;
      case 'semIgt':
        this.confirmacaoDoInicioSemIgt.set(true);
        this.confirmacaoAberta.set(true);
        break;
      case 'origem':
        if (this.origemIgt() === 'novo') {
          this.abrirElaboracaoNova();
        } else {
          this.paginaInicial.set('igt');
          this.pesquisarIgtSnit();
        }
        break;
      case 'novo':
        this.criarIgt();
        break;
      case 'igt':
        if (this.igtSnitEscolhido()) {
          this.criarIgtDoSnit();
        } else {
          this.paginaInicial.set('acao');
        }
        break;
      case 'acao':
        if (this.modo() === 'nova') {
          this.abrirNovaDinamica();
        } else {
          this.paginaInicial.set('continuar');
        }
        break;
      case 'continuar':
      case 'continuarSemIgt':
        if (this.procedimentoEscolhido()?.tentativaEmCurso) {
          this.retomarTentativa();
        } else if (this.procedimentoEscolhido()?.tipoProcedimento === 'DECISAO_INICIO') {
          // Como ao criar a Dinâmica de raiz: a Decisão de início abre logo o processo.
          this.criarProcesso();
        } else {
          this.pedirConfirmacaoAvancar();
        }
        break;
      case 'nova':
        this.iniciarDinamica();
        break;
    }
  }

  alterarDesignacaoNovoIgt(valor: string): void {
    this.designacaoNovoIgt.set(valor);
  }

  alterarConcelhoNovoIgt(valor: string | null): void {
    this.concelhoNovoIgt.set(valor);
  }

  /** Uma vez por navegação; a lista não muda com o tipo de IGT. */
  private carregarConcelhosNovoIgt(): void {
    if (this.concelhosNovoIgt().length > 0) {
      return;
    }
    this.instrumentoService.listarConcelhos().subscribe({
      next: (concelhos) => this.concelhosNovoIgt.set(concelhos),
      error: (erro: ApiError) => this.erro.set(erro),
    });
  }

  /** Sem Elaboração no tipo (404), segue pela página «Novo IGT», que cria o IGT e depois a Dinâmica. */
  private abrirElaboracaoNova(): void {
    const tipoInstrumentoId = this.tipoEscolhidoId();
    if (tipoInstrumentoId === null) {
      return;
    }
    this.erro.set(null);
    this.aAbrirElaboracao.set(true);
    this.instrumentoService.elaboracaoNova(tipoInstrumentoId).subscribe({
      next: (elaboracao) => {
        this.aAbrirElaboracao.set(false);
        this.limparEscolhasDeDinamica();
        this.elaboracaoNova.set(elaboracao);
        this.viaElaboracaoNova.set(true);
        this.procedimentoEscolhido.set(elaboracao.procedimento);
        this.processoCriado.set(null);
        this.detalheLido.set(null);
        this.valoresIniciais.set(null);
        this.reporInformacaoBase({ informacaoBase: null, dataAssinatura: null });
        this.subpagina.set('cabecalho');
        this.passoAtual.set(2);
      },
      error: (erro: ApiError) => {
        this.aAbrirElaboracao.set(false);
        if (erro.codigo === 'NAO_ENCONTRADO') {
          this.paginaInicial.set('novo');
          this.carregarConcelhosNovoIgt();
        } else {
          this.erro.set(erro);
        }
      },
    });
  }

  private sairDaElaboracaoNova(): void {
    this.elaboracaoNova.set(null);
    this.viaElaboracaoNova.set(false);
    this.designacaoElaboracao.set(null);
  }

  /** O primeiro «Guardar» cria o IGT, a Elaboração e a tentativa; depois grava-se o formulário como num rascunho. */
  private criarElaboracaoEDepois(formulario: ProcessoFormularioInicialComponent, depois: () => void): void {
    const tipoInstrumentoId = this.tipoEscolhidoId();
    const dados = formulario.dadosElaboracaoNova();
    if (tipoInstrumentoId === null || !dados) {
      return;
    }
    this.erro.set(null);
    this.aGuardar.set(true);
    this.instrumentoService.criarElaboracao({ tipoInstrumentoId, ...dados }).subscribe({
      next: (criada) => {
        this.aGuardar.set(false);
        this.processoCriado.set(criada.processo);
        this.instrumentoEscolhidoId.set(criada.instrumentoId);
        this.designacaoElaboracao.set(dados.designacao);
        this.lerDetalheCriado(criada.processo.id);
        this.gravarPasso2(depois);
      },
      error: (erro: ApiError) => {
        this.aGuardar.set(false);
        if (!formulario.aplicarErrosElaboracaoNova(erro)) {
          this.tratarErroDeGravacao(erro, formulario.form);
        }
      },
    });
  }

  /** Com uma só dinâmica possível (a Elaboração de um IGT novo), fica já escolhida. */
  private abrirNovaDinamica(): void {
    this.modo.set('nova');
    const disponiveis = this.disponibilidade()?.dinamicasDisponiveis ?? [];
    if (this.dinamicaNovaId() === null && disponiveis.length === 1) {
      this.dinamicaNovaId.set(disponiveis[0].instrumentoDinamicaConfigId);
    }
    this.paginaInicial.set('nova');
  }

  /** Um IGT acabado de criar não tem dinâmicas: segue direto para a Nova Dinâmica; sem nenhuma disponível, para a lista de IGT. */
  criarIgt(): void {
    const tipoInstrumentoId = this.tipoEscolhidoId();
    const concelho = this.concelhoNovoIgt();
    const designacao = this.designacaoNovoIgt().trim();
    if (tipoInstrumentoId === null || !designacao || concelho === null || !this.podeNovoIgt()) {
      return;
    }

    this.erro.set(null);
    this.aCriarIgt.set(true);
    this.instrumentoService.criar({ tipoInstrumentoId, designacao, concelhos: [concelho] }).subscribe({
      next: (instrumento) => {
        this.aCriarIgt.set(false);
        this.instrumentos.update((lista) => [instrumento, ...lista]);
        this.designacaoNovoIgt.set('');
        this.concelhoNovoIgt.set(null);
        this.origemIgt.set('existente');
        this.instrumentoEscolhidoId.set(instrumento.id);
        this.limparEscolhasDeDinamica();
        this.disponibilidade.set(null);
        this.painel.set(null);
        this.carregarDisponibilidade(undefined, (disponibilidade) => {
          if (disponibilidade.dinamicasDisponiveis.length > 0) {
            this.viaNovoIgt.set(true);
            this.abrirNovaDinamica();
          } else {
            this.paginaInicial.set('igt');
          }
        });
        this.mostrarSucesso(`IGT ${instrumento.designacao} criado com sucesso.`);
      },
      error: (erro: ApiError) => {
        this.aCriarIgt.set(false);
        this.erro.set(erro);
      },
    });
  }

  /**
   * O SNIT é a fonte do tipo, da designação e dos concelhos; o IGT nasce em vigor, por isso segue para a Ação como
   * qualquer IGT existente, com a Alteração disponível sem Elaboração.
   */
  criarIgtDoSnit(): void {
    const tipoInstrumentoId = this.tipoEscolhidoId();
    const igt = this.igtSnitEscolhido();
    if (tipoInstrumentoId === null || !igt || !this.podeNovoIgt() || this.aCriarIgt()) {
      return;
    }

    this.erro.set(null);
    this.aCriarIgt.set(true);
    this.instrumentoService.criar({ tipoInstrumentoId, designacao: igt.designacao, idIgtSnit: igt.idIgtSnit }).subscribe({
      next: (instrumento) => {
        this.aCriarIgt.set(false);
        this.instrumentos.update((lista) => [instrumento, ...lista]);
        this.igtSnit.update((lista) =>
          lista.map((item) => (item.idIgtSnit === igt.idIgtSnit ? { ...item, instrumentoId: instrumento.id } : item)),
        );
        this.igtSnitEscolhido.set(null);
        this.instrumentoEscolhidoId.set(instrumento.id);
        this.limparEscolhasDeDinamica();
        this.disponibilidade.set(null);
        this.painel.set(null);
        this.carregarDisponibilidade(undefined, () => this.paginaInicial.set('acao'));
        this.mostrarSucesso(`IGT ${instrumento.designacao} criado com sucesso.`);
      },
      error: (erro: ApiError) => {
        this.aCriarIgt.set(false);
        this.erro.set(erro);
      },
    });
  }

  /** Uma vez por tipo. Sem o SNIT, a lista fica com os IGT da SAIGT e um aviso; voltar a esta página tenta outra vez. */
  private pesquisarIgtSnit(): void {
    const tipoInstrumentoId = this.tipoEscolhidoId();
    if (tipoInstrumentoId === null || this.igtSnitDoTipo() === tipoInstrumentoId) {
      return;
    }

    this.igtSnitDoTipo.set(tipoInstrumentoId);
    this.igtSnit.set([]);
    this.erroSnit.set(null);
    this.aPesquisarSnit.set(true);
    this.instrumentoService.pesquisarSnit(tipoInstrumentoId, 0, TAMANHO_PESQUISA_SNIT).subscribe({
      next: (pagina) => {
        if (this.igtSnitDoTipo() !== tipoInstrumentoId) {
          return;
        }
        this.igtSnit.set(pagina.content);
        this.aPesquisarSnit.set(false);
      },
      error: () => {
        if (this.igtSnitDoTipo() !== tipoInstrumentoId) {
          return;
        }
        this.igtSnitDoTipo.set(null);
        this.aPesquisarSnit.set(false);
        this.erroSnit.set('Não foi possível consultar o SNIT: os IGT que só existem no SNIT não aparecem. Tente mais tarde.');
      },
    });
  }

  /** O "Seguinte" é o Avançar da página, ou, no Formulário, grava e passa à sub-página seguinte ou ao Resumo. */
  seguinte(): void {
    if (this.passoAtual() === 1) {
      this.avancar();
      return;
    }
    this.pendenciasAoAvancar.set([]);
    switch (this.subpagina()) {
      case 'cabecalho':
        if (this.formularioRef()?.validarObrigatorios() === false) {
          break;
        }
        // A resposta do GeoConform acabou de ser gravada: só o detalhe relido diz se há Plantas.
        this.gravarPasso2(() => this.avancarSePaginaCompleta(() => this.subpagina.set(this.exigePlantasEMetadados() ? 'plantas' : 'documentos')));
        break;
      case 'plantas':
        this.gravarPlantasEDepois(() => this.avancarSePaginaCompleta(() => this.subpagina.set('documentos')));
        break;
      case 'documentos':
        if (this.exigePlantasEMetadados()) {
          this.gravarPasso2(() => this.avancarSePaginaCompleta(() => this.subpagina.set('metadados')));
        } else {
          this.avancarParaPublicar();
        }
        break;
      case 'metadados':
        this.gravarMetadadosEDepois(() => this.avancarSePaginaCompleta(() => this.passoAtual.set(3)));
        break;
    }
  }

  /** Pendências da submissão na página onde o Seguinte foi recusado; somem ao mudar de página. */
  private readonly pendenciasAoAvancar = signal<PendenciaSubmissao[]>([]);
  readonly pendenciasDaPaginaAtual = computed(() =>
    this.passoAtual() === 2 ? this.pendenciasAoAvancar().filter((pendencia) => pendencia.pagina === PAGINA_DA_SUBPAGINA[this.subpagina()]) : [],
  );

  /** Cada página só deixa avançar sem pendências suas: são as mesmas regras que a submissão aplica no fim. */
  private avancarSePaginaCompleta(depois: () => void): void {
    const pagina = PAGINA_DA_SUBPAGINA[this.subpagina()];
    this.reler(() => {
      const pendentes = this.pendenciasSubmissao().filter((pendencia) => pendencia.pagina === pagina);
      this.pendenciasAoAvancar.set(pendentes);
      if (pagina === 'DOCUMENTOS') {
        // A incoerência da Informação Base aparece também junto do campo do bloco, como na recusa da submissão.
        const doBloco = pendentes.filter((pendencia) => pendencia.campo.startsWith('informacaoBase.'));
        this.errosInformacaoBase.set(errosPorCaminho(doBloco.map((pendencia) => ({ path: pendencia.campo, erro: pendencia.erro }))));
      }
      if (pendentes.length === 0) {
        depois();
      }
    });
  }

  /** Sem GeoConform, com a página das Plantas no Procedimento (EF v1.5 §3.1.2). */
  readonly exigePlantasEMetadados = computed(() => this.detalheDoFormularioSinal()?.exigePlantasEMetadados ?? false);

  private gravarPlantasEDepois(depois: () => void): void {
    const plantas = this.plantasRef();
    if (!plantas) {
      depois();
      return;
    }
    this.aGuardar.set(true);
    plantas.gravar().subscribe((gravadas) => {
      this.aGuardar.set(false);
      if (gravadas) {
        depois();
      }
    });
  }

  private gravarMetadadosEDepois(depois: () => void): void {
    const metadados = this.metadadosMotuRef() ?? this.metadadosRef();
    if (!metadados) {
      depois();
      return;
    }
    this.aGuardar.set(true);
    metadados.gravar().subscribe((gravados) => {
      this.aGuardar.set(false);
      if (gravados) {
        this.errosMetadados.set(null);
        depois();
      }
    });
  }

  selecionarDinamicaNova(valor: string | null): void {
    this.dinamicaNovaId.set(valor ? Number(valor) : null);
  }

  alterarDescritivoNovo(valor: string): void {
    this.descritivoNovo.set(valor);
  }

  selecionarProcedimento(valor: string | null): void {
    const procedimento =
      this.dinamicaEscolhida()?.procedimentosDisponiveis.find(
        (item) => String(item.instrumentoProcedimentoConfigId) === valor,
      ) ?? null;
    this.procedimentoEscolhido.set(procedimento);
    this.atoReferidoId.set(null);
    this.avisoProcedimentoIndisponivel.set(false);
    if (this.erroAtoReferido()) {
      this.erro.set(null);
    }
  }

  escolherAtoReferido(processoId: string | null): void {
    this.atoReferidoId.set(processoId);
    if (this.erroAtoReferido()) {
      this.erro.set(null);
    }
  }

  /** Ação à parte do Avançar: os Procedimentos de uma Dinâmica nova só existem depois de iniciada (motor de regras do backend). */
  iniciarDinamica(): void {
    const instrumentoId = this.instrumentoEscolhidoId();
    const configId = this.dinamicaNovaId();
    const descritivo = this.descritivoNovo().trim();
    if (!instrumentoId || configId === null || !descritivo) {
      return;
    }

    this.erro.set(null);
    this.aIniciar.set(true);
    this.instrumentoService.iniciarDinamica(instrumentoId, { instrumentoDinamicaConfigId: configId, descritivo }).subscribe({
      next: (dinamica) => {
        this.aIniciar.set(false);
        this.dinamicaNovaId.set(null);
        this.descritivoNovo.set('');
        this.viaNovaDinamica.set(true);
        this.carregarDisponibilidade(dinamica.id);
      },
      error: (erro: ApiError) => {
        this.aIniciar.set(false);
        this.erro.set(erro);
      },
    });
  }

  pedirConfirmacaoAvancar(): void {
    this.confirmacaoDoInicioSemIgt.set(false);
    this.confirmacaoAberta.set(true);
  }

  confirmarPedido(): void {
    if (this.confirmacaoDoInicioSemIgt()) {
      this.iniciarDinamicaSemIgt();
    } else {
      this.criarProcesso();
    }
  }

  fecharConfirmacao(): void {
    this.confirmacaoAberta.set(false);
    this.confirmacaoDoInicioSemIgt.set(false);
  }

  /**
   * `POST /api/v1/dinamicas` sem Instrumentos; depois relê as Dinâmicas sem IGT e cria o processo do Procedimento de
   * menor ordem. Com um rascunho por retomar, fica na página de continuar para o retomar.
   */
  iniciarDinamicaSemIgt(): void {
    const configId = this.configuracaoSemIgtId();
    const descritivo = this.descritivoSemIgt().trim();
    if (configId === null || !descritivo) {
      return;
    }
    this.erro.set(null);
    this.aIniciar.set(true);
    this.dinamicaService.iniciar({ instrumentoDinamicaConfigId: configId, descritivo }).subscribe({
      next: (dinamica) => {
        this.carregarDinamicasSemIgt(() => {
          this.aIniciar.set(false);
          this.confirmacaoDoInicioSemIgt.set(false);
          this.descritivoSemIgt.set('');
          this.carregarConcelhosNovoIgt();
          this.paginaInicial.set('continuarSemIgt');
          this.selecionarDinamica(dinamica.id);
          const inicial = this.procedimentoInicial();
          if (inicial) {
            this.selecionarProcedimento(String(inicial.instrumentoProcedimentoConfigId));
          }
          // O tipo de ato escolhe-se depois, no cabeçalho: sem rascunho por retomar, o processo cria-se já.
          if (this.procedimentoEscolhido() && !this.procedimentoEscolhido()?.tentativaEmCurso) {
            this.criarProcesso();
          } else {
            this.confirmacaoAberta.set(false);
          }
        });
      },
      error: (erro: ApiError) => {
        this.aIniciar.set(false);
        this.fecharConfirmacao();
        this.erro.set(erro);
      },
    });
  }

  private procedimentoInicial(): ProcedimentoDisponivel | null {
    return (this.dinamicaEscolhida()?.procedimentosDisponiveis ?? [])
      .filter((procedimento) => !procedimento.atoPontual)
      .reduce<ProcedimentoDisponivel | null>((menor, procedimento) => (menor === null || procedimento.ordem < menor.ordem ? procedimento : menor), null);
  }

  private carregarTiposProcesso(): void {
    this.estadoTiposProcesso.set('aCarregar');
    this.instrumentoService.tiposProcesso().subscribe({
      next: (tiposProcesso) => {
        this.tiposProcesso.set(tiposProcesso);
        this.estadoTiposProcesso.set('lido');
      },
      error: (erro: ApiError) => {
        if (isDevMode()) {
          console.error('Tipos de processo indisponíveis no assistente', { codigo: erro?.codigo, traceId: erro?.traceId });
        }
        this.estadoTiposProcesso.set('erro');
      },
    });
  }

  /** Sem resposta, a ligação «Continuar uma dinâmica sem associação a IGT» não aparece; o resto do assistente funciona. */
  private carregarDinamicasSemIgt(depois?: () => void): void {
    this.dinamicaService.disponivel().subscribe({
      next: (resposta) => {
        this.dinamicasSemIgt.set(resposta.dinamicasEmCurso);
        depois?.();
      },
      error: (erro: ApiError) => {
        if (depois) {
          this.aIniciar.set(false);
          this.fecharConfirmacao();
          this.erro.set(erro);
        }
      },
    });
  }

  /** Só aqui a tentativa é mesmo criada. */
  criarProcesso(): void {
    const dinamica = this.dinamicaEscolhida();
    const procedimento = this.procedimentoEscolhido();
    if (!dinamica || !procedimento) {
      return;
    }

    const atoReferido = this.pedeAtoReferido() ? this.atoReferido() : null;
    if (procedimento.atoPontual && !procedimento.tentativaEmCurso && !atoReferido) {
      return;
    }

    this.erro.set(null);
    this.aCriar.set(true);
    this.processoService
      .criar(dinamica.dinamicaId, {
        instrumentoProcedimentoConfigId: procedimento.instrumentoProcedimentoConfigId,
        ...(atoReferido ? { processoReferidoId: atoReferido.processoId } : {}),
      })
      .subscribe({
        next: (processo) => {
          this.aCriar.set(false);
          this.confirmacaoAberta.set(false);
          this.processoCriado.set(processo);
          this.reporInformacaoBase({ informacaoBase: null, dataAssinatura: null });
          this.subpagina.set('cabecalho');
          this.passoAtual.set(2);
          this.lerDetalheCriado(processo.id);
        },
        error: (erro: ApiError) => {
          this.aCriar.set(false);
          this.confirmacaoAberta.set(false);
          if (erro.codigo === CODIGO_REFERENCIA_INVALIDA) {
            this.reofertarAtoPontual(dinamica.dinamicaId, procedimento.instrumentoProcedimentoConfigId, erro);
            return;
          }
          if (CODIGOS_DE_CONFLITO_NA_CRIACAO.includes(erro.codigo)) {
            if (this.viaSemIgt()) {
              this.carregarDinamicasSemIgt(() => this.selecionarDinamica(dinamica.dinamicaId));
            } else {
              // A releitura limpa o banner ao começar: o erro volta a seguir, para continuar visível.
              this.carregarDisponibilidade(dinamica.dinamicaId);
            }
          }
          this.erro.set(erro);
        },
      });
  }

  /** Relê a oferta e volta a escolher o ato pontual se ainda lá estiver; o alerta fica visível depois da releitura. */
  private reofertarAtoPontual(dinamicaId: string, configId: number, erro: ApiError): void {
    const reescolher = () => {
      if (this.procedimentosDaDinamica().some((item) => item.instrumentoProcedimentoConfigId === configId)) {
        this.selecionarProcedimento(String(configId));
      }
      this.erro.set(erro);
    };
    if (this.viaSemIgt()) {
      this.carregarDinamicasSemIgt(() => {
        this.selecionarDinamica(dinamicaId);
        reescolher();
      });
    } else {
      this.carregarDisponibilidade(dinamicaId, reescolher);
    }
    this.erro.set(erro);
  }

  aoAssociarInstrumentos(instrumentos: InstrumentoAbrangido[]): void {
    this.detalheLido.update((detalhe) => (detalhe ? { ...detalhe, instrumentosAbrangidos: instrumentos } : detalhe));
  }

  /** Sem o detalhe o cartão mostra traço no território, não mostra o IDUS e não pede a Fundamentação RJIGT; não impede de preencher o resto. */
  private lerDetalheCriado(processoId: string): void {
    this.detalheLido.set(null);
    this.processoService.obter(processoId).subscribe({
      next: (detalhe) => this.detalheLido.set(detalhe),
      error: () => this.detalheLido.set(null),
    });
  }

  retomarTentativa(): void {
    const tentativa = this.procedimentoEscolhido()?.tentativaEmCurso;
    if (!tentativa) {
      return;
    }

    this.erro.set(null);
    this.aRetomar.set(true);
    this.processoService.obter(tentativa.processoId).subscribe({
      next: (processo) => {
        this.aRetomar.set(false);
        this.processoCriado.set(processo);
        this.valoresIniciais.set(processo);
        this.reporInformacaoBase(informacaoBaseDe(processo));
        this.subpagina.set('cabecalho');
        this.passoAtual.set(2);
      },
      error: (erro: ApiError) => {
        this.aRetomar.set(false);
        this.erro.set(erro);
      },
    });
  }

  /**
   * A série decide os tipos de ato: grava-se o rascunho e relê-se o detalhe, que traz `tiposAtoDisponiveis` da série
   * nova (e sem o tipo de ato, se o servidor o limpou por não existir nela).
   */
  aoEscolherSerie(): void {
    const processo = this.processoCriado();
    if (!processo) {
      return;
    }
    this.gravarPasso2(() =>
      this.processoService.obter(processo.id).subscribe({
        next: (detalhe) => {
          this.detalheLido.set(detalhe);
          this.processoCriado.set(detalhe);
          this.valoresIniciais.set(detalhe);
        },
        error: (erro: ApiError) => this.erro.set(erro),
      }),
    );
  }

  guardarRascunho(): void {
    const sucesso = () => this.mostrarSucesso('Rascunho guardado com sucesso.');
    if (this.subpagina() === 'plantas') {
      this.gravarPlantasEDepois(sucesso);
    } else if (this.subpagina() === 'metadados') {
      this.gravarMetadadosEDepois(sucesso);
    } else {
      this.gravarPasso2(sucesso);
    }
  }

  private mostrarSucesso(mensagem: string): void {
    this.mensagemSucesso.set(mensagem);
    clearTimeout(this.temporizadorBannerAcao);
    this.temporizadorBannerAcao = setTimeout(() => this.mensagemSucesso.set(null), DURACAO_BANNER_ACAO_MS);
  }

  /** O resumo do passo 3 lê o que ficou gravado, nunca o que está só no ecrã. */
  avancarParaPublicar(): void {
    this.gravarPasso2(() => this.avancarSePaginaCompleta(() => this.passoAtual.set(3)));
  }

  definirConfirmacao(confirmado: boolean): void {
    this.confirmado.set(confirmado);
    if (confirmado && this.erroConfirmacao()) {
      this.erro.set(null);
    }
  }

  definirDeclaracaoTermino(declarado: boolean): void {
    this.declaracaoTermino.set(declarado);
    if (declarado && this.erroDeclaracaoTermino()) {
      this.erro.set(null);
    }
  }

  /** Do aviso do Resumo para a página onde se corrige; não há nada a gravar no Resumo. */
  irParaPagina(pagina: PaginaPendencia): void {
    this.erro.set(null);
    this.subpagina.set(SUBPAGINA_DA_PENDENCIA[pagina]);
    this.passoAtual.set(2);
  }

  submeter(): void {
    const processo = this.processoCriado();
    if (!processo || !this.confirmacoesFeitas() || !this.podeSubmeter()) {
      return;
    }

    const incm = this.publicacaoIncm();
    const request: SubmeterProcessoRequest = {
      versao: processo.versao,
      sumario: incm?.sumario ?? undefined,
      dataAssinatura: incm?.dataAssinatura ?? undefined,
      normaLegal: incm?.normaLegal ?? undefined,
      tipoInformacao: incm?.tipoInformacao ?? undefined,
      publicarSuplemento: incm?.publicarSuplemento ?? undefined,
      dataSuplemento: incm?.dataSuplemento ?? undefined,
      confirmacoes: this.exigeDeclaracaoTermino()
        ? [CONFIRMACAO_CONTEUDO_PROCESSO, CONFIRMACAO_DECLARACAO_TERMINO]
        : [CONFIRMACAO_CONTEUDO_PROCESSO],
    };

    this.erro.set(null);
    this.aSubmeter.set(true);
    this.processoService.submeter(processo.id, request).subscribe({
      // O IDUS e o número da submissão mudam na submissão e só vêm no detalhe; sem o detalhe, o recibo sai sem eles.
      next: (submetido) =>
        this.processoService
          .obter(submetido.id)
          .pipe(catchError(() => of(null)))
          .subscribe((detalhe) => {
            this.aSubmeter.set(false);
            this.submissaoConcluida.set(detalhe ? { ...submetido, idus: detalhe.idus, submissao: detalhe.submissao } : submetido);
          }),
      error: (erro: ApiError) => {
        this.aSubmeter.set(false);
        if (this.recusadaNaInformacaoBase(erro)) {
          return;
        }
        this.falhar(erro);
        if (this.erroDeclaracaoTermino()) {
          this.recarregarDeclaracaoTermino(processo.id);
          return;
        }
        if (erro.codigo === CODIGO_CONFIRMACAO_EM_FALTA) {
          this.confirmado.set(false);
          return;
        }
        const recusadaNoFormulario = erro.codigo === CODIGO_OPCAO_GEOCONFORM_EM_FALTA || this.errosFormularioSubmissao().length > 0;
        if (recusadaNoFormulario && this.podeEditar()) {
          this.subpagina.set('cabecalho');
          this.passoAtual.set(2);
        }
        if (erro.codigo === CODIGO_PLANTAS_METADADOS_EM_FALTA && this.podeEditar()) {
          const campos = erro.campos ?? [];
          this.errosMetadados.set(campos);
          this.subpagina.set(campos.some((campo) => campo.path.startsWith('plantas.')) ? 'plantas' : 'metadados');
          this.passoAtual.set(2);
        }
      },
    });
  }

  /** Outra Dinâmica pode ter começado entretanto: relê o detalhe para a lista da declaração ficar a atual. */
  private recarregarDeclaracaoTermino(processoId: string): void {
    this.declaracaoTermino.set(false);
    this.processoService.obter(processoId).subscribe({
      next: (recarregado) => {
        this.processoCriado.set(recarregado);
        if (!this.exigeDeclaracaoTermino()) {
          this.erro.set(null);
        }
      },
      error: (erroLeitura: ApiError) => this.erro.set(erroLeitura),
    });
  }

  /**
   * Um 400 da submissão sobre a Informação Base (ex.: a data da deliberação depois da data limite) volta aos
   * documentos, com o erro junto do campo; os outros campos do mesmo erro seguem para o banner.
   * Só `informacaoBase.*`: na submissão, `dataAssinatura` é do cartão INCM do passo 3.
   */
  private recusadaNaInformacaoBase(erro: ApiError): boolean {
    if (erro.codigo !== CODIGO_VALIDACAO || !this.podeEditar()) {
      return false;
    }
    const campos = erro.campos ?? [];
    const doBloco = campos.filter((campo) => campo.path.startsWith('informacaoBase.'));
    if (doBloco.length === 0) {
      return false;
    }
    this.errosInformacaoBase.set(errosPorCaminho(doBloco));
    const restantes = campos.filter((campo) => !doBloco.includes(campo));
    this.erro.set(restantes.length > 0 ? new ApiError(erro.codigo, erro.mensagem, erro.traceId, restantes) : null);
    this.subpagina.set('documentos');
    this.passoAtual.set(2);
    return true;
  }

  /** `VERSAO_DESATUALIZADA` e `CAMPO_BLOQUEADO`: mostra o aviso e relê o processo, para a próxima escrita partir da `versao` e dos campos bloqueados certos. */
  private falhar(erro: ApiError): void {
    this.erro.set(erro);
    const processo = this.processoCriado();
    if ((erro.codigo !== 'VERSAO_DESATUALIZADA' && erro.codigo !== CODIGO_CAMPO_BLOQUEADO) || !processo) {
      return;
    }
    this.processoService.obter(processo.id).subscribe({
      next: (recarregado) => this.processoCriado.set(recarregado),
      error: (erroLeitura: ApiError) => this.erro.set(erroLeitura),
    });
  }

  voltarPasso(): void {
    const processo = this.processoCriado();
    if (this.passoAtual() === 2 && this.subpagina() === 'metadados') {
      this.gravarMetadadosEDepois(() => this.subpagina.set('documentos'));
      return;
    }
    if (this.passoAtual() === 2 && this.subpagina() === 'documentos') {
      this.gravarPasso2(() => this.subpagina.set(this.exigePlantasEMetadados() ? 'plantas' : 'cabecalho'));
      return;
    }
    if (this.passoAtual() === 2 && this.subpagina() === 'plantas') {
      this.gravarPlantasEDepois(() => this.subpagina.set('cabecalho'));
      return;
    }
    if ((this.entradaPeloDetalhe() || this.viaElaboracaoNova()) && this.passoAtual() === 2 && processo) {
      this.router.navigateByUrl(ROUTES.PROCESSO_DETALHE(processo.id));
      return;
    }
    if (this.viaElaboracaoNova() && this.passoAtual() === 2) {
      this.sairDaElaboracaoNova();
      this.procedimentoEscolhido.set(null);
      this.passoAtual.set(1);
      return;
    }
    if (this.passoAtual() === 1) {
      const anterior = PAGINA_ANTERIOR[this.paginaInicial()];
      if (anterior) {
        if (anterior === 'acao') {
          this.viaNovoIgt.set(false);
        }
        if (anterior === 'tipo' && this.viaSemIgt()) {
          this.viaSemIgt.set(false);
          this.limparEscolhasDeDinamica();
        }
        this.paginaInicial.set(anterior);
      }
      return;
    }
    this.passoAtual.update((passo) => (passo > 1 ? ((passo - 1) as 1 | 2) : passo));
  }

  voltarAoMenu(): void {
    this.router.navigateByUrl(ROUTES.PAINEL);
  }

  private gravarPasso2(depois: () => void): void {
    const processo = this.processoCriado();
    const formulario = this.formularioRef();
    if (!processo && formulario && this.elaboracaoNova()) {
      this.criarElaboracaoEDepois(formulario, depois);
      return;
    }
    if (!processo || !formulario) {
      return;
    }

    const dadosRatificacao = this.ratificacaoRef()?.dados() ?? {};
    const dadosFormulario = formulario.dados();
    const informacaoBaseDoBloco = this.informacaoBaseEmEdicao() ?? this.informacaoBaseGravada();
    const informacaoBase = {
      ...informacaoBaseDoBloco,
      dataAssinatura: formulario.dataAssinaturaPedido(informacaoBaseDoBloco.dataAssinatura),
    };
    this.erro.set(null);
    this.erroModalidadeGravacao.set(null);
    this.aGuardar.set(true);
    this.processoService
      .guardarRascunho(processo.id, {
        ...dadosRatificacao,
        versao: processo.versao,
        ...dadosFormulario,
        ...formulario.dadosFormularioPedido(),
        dataAssinatura: informacaoBase.dataAssinatura,
        informacaoBase: informacaoBase.informacaoBase,
      })
      .subscribe({
        next: (atualizado) => {
          this.processoCriado.update((atual) => (atual ? { ...atual, ...atualizado, ...dadosRatificacao } : atualizado));
          this.publicacaoIncm.set({ ...dadosFormulario, dataAssinatura: informacaoBase.dataAssinatura });
          this.valoresIniciais.set({
            ...dadosFormulario,
            // O pedido pode não o levar (sem escolha, bloqueado ou escolhido na Elaboração): vale o gravado.
            tipoAtoId: atualizado.tipoAtoId,
            dataAssinatura: informacaoBase.dataAssinatura,
            simultaneoComPmot: formulario.respostaSimultaneoPmot(),
          });
          this.reporInformacaoBase(informacaoBase);
          this.gravarObservacoesEDepois(depois);
        },
        error: (erro: ApiError) => {
          this.aGuardar.set(false);
          this.tratarErroDeGravacao(erro, formulario.form);
          if (erro.campos?.some((campo) => campo.path === 'dinamicaAssociadaId')) {
            formulario.focarDinamicaAssociada();
          }
        },
      });
  }

  /** As Observações gravam-se por item, depois do rascunho; um erro fica junto da caixa e não se avança. */
  private gravarObservacoesEDepois(depois: () => void): void {
    const documentos = this.documentosRef();
    (documentos?.gravarObservacoes() ?? of(true)).subscribe((gravadas) => {
      this.aGuardar.set(false);
      documentos?.recarregar();
      if (gravadas) {
        depois();
      }
    });
  }

  /** A resposta do `PUT` não traz `podeSubmeter`: o resumo parte do detalhe relido. */
  private reler(depois: () => void): void {
    const processo = this.processoCriado();
    if (!processo) {
      return;
    }
    this.aGuardar.set(true);
    this.processoService.obter(processo.id).subscribe({
      next: (detalhe) => {
        this.aGuardar.set(false);
        this.processoCriado.set(detalhe);
        depois();
      },
      error: (erro: ApiError) => {
        this.aGuardar.set(false);
        this.erro.set(erro);
      },
    });
  }

  private reporInformacaoBase(valores: InformacaoBaseAlterada): void {
    this.informacaoBaseGravada.set(valores);
    this.informacaoBaseEmEdicao.set(null);
    this.errosInformacaoBase.set({});
  }

  /** Os erros do bloco ficam junto dos campos e não passam pelo banner; os do cartão seguem como antes. */
  private tratarErroDeGravacao(erroRecebido: ApiError, formFormulario: FormGroup): void {
    const erro = comErroNoTipoAto(erroRecebido);
    if (erro.codigo === CODIGO_CAMPO_BLOQUEADO) {
      this.falhar(erro);
      return;
    }
    const { doBloco, restantes: doCartao } = separarErrosInformacaoBase(erro);
    const erroModalidade = doCartao.find((campo) => campo.path === CAMPO_MODALIDADE_RATIFICACAO);
    const restantes = doCartao.filter((campo) => campo !== erroModalidade);
    if (erroModalidade) {
      this.erroModalidadeGravacao.set(erroModalidade.erro);
      this.ratificacaoRef()?.aplicarErro(erroModalidade.erro);
    }
    this.errosInformacaoBase.set(doBloco);
    const temErrosDoBloco = Object.keys(doBloco).length > 0;
    const tratado =
      restantes.length === 0
        ? temErrosDoBloco || !!erroModalidade
        : this.aplicarErrosDeCampo(new ApiError(erro.codigo, erro.mensagem, erro.traceId, restantes), formFormulario);
    if (restantes.length > 0 || erroModalidade) {
      this.subpagina.set('cabecalho');
    } else if (temErrosDoBloco) {
      this.subpagina.set('documentos');
    }
    if (!tratado) {
      this.falhar(erro);
    }
  }

  /** O rascunho já foi gravado no detalhe: o resumo e a submissão leem o que está no backend. */
  private abrirNoPublicar(processoId: string): void {
    this.aRetomar.set(true);
    this.processoService.obter(processoId).subscribe({
      next: (processo) => {
        this.aRetomar.set(false);
        this.processoCriado.set(processo);
        this.valoresIniciais.set(processo);
        this.reporInformacaoBase(informacaoBaseDe(processo));
        this.publicacaoIncm.set({
          sumario: processo.sumario,
          dataAssinatura: processo.dataAssinatura,
          normaLegal: processo.normaLegal,
          tipoInformacao: processo.tipoInformacao,
          publicarSuplemento: processo.publicarSuplemento,
          dataSuplemento: processo.dataSuplemento,
        });
        this.entradaPeloDetalhe.set(true);
        this.subpagina.set('documentos');
        this.passoAtual.set(3);
      },
      error: (erro: ApiError) => {
        this.aRetomar.set(false);
        this.erro.set(erro);
      },
    });
  }

  private lerEntradaPeloPainel(): EntradaPeloPainel | null {
    const parametros = this.route.snapshot.queryParamMap;
    const instrumentoId = parametros.get(PARAM_INSTRUMENTO);
    if (!instrumentoId || !FORMATO_UUID.test(instrumentoId)) {
      return null;
    }
    const dinamicaId = parametros.get(PARAM_DINAMICA);
    const procedimentoId = Number(parametros.get(PARAM_PROCEDIMENTO));
    const dinamicaValida = dinamicaId !== null && FORMATO_UUID.test(dinamicaId);
    return {
      instrumentoId,
      dinamicaId: dinamicaValida ? dinamicaId : null,
      procedimentoId: dinamicaValida && Number.isInteger(procedimentoId) && procedimentoId > 0 ? procedimentoId : null,
    };
  }

  /** A autorização continua no backend: um id que o `/disponivel` não oferece é ignorado. */
  private entrarPeloInstrumento(entrada: EntradaPeloPainel): void {
    const tipos$ =
      this.tipos().length > 0
        ? of(this.tipos())
        : this.instrumentoService.listarTipos().pipe(catchError(() => of<TipoInstrumento[]>([])));
    forkJoin({ painel: this.instrumentoService.painel(entrada.instrumentoId), tipos: tipos$ }).subscribe({
      next: ({ painel, tipos }) => {
        this.tipoEscolhidoId.set(painel.tipoInstrumentoId);
        this.instrumentoEscolhidoId.set(painel.id);
        this.origemIgt.set('existente');
        if (!this.instrumentos().some((instrumento) => instrumento.id === painel.id)) {
          const nomeTipo = tipos.find((tipo) => tipo.id === painel.tipoInstrumentoId)?.nome ?? '';
          this.instrumentos.update((lista) => [this.instrumentoDoPainel(painel, nomeTipo), ...lista]);
        }
        this.carregarDisponibilidade(undefined, (disponibilidade) => this.escolherDaEntrada(entrada, disponibilidade));
      },
      error: (erro: ApiError) => this.erro.set(erro),
    });
  }

  private escolherDaEntrada(entrada: EntradaPeloPainel, disponibilidade: InstrumentoDisponibilidade): void {
    const dinamica = disponibilidade.dinamicasEmCurso.find(
      (item) => item.dinamicaId === entrada.dinamicaId && item.estado !== 'CADUCADA',
    );
    if (!dinamica) {
      this.paginaInicial.set('acao');
      return;
    }
    this.modo.set('continuar');
    this.paginaInicial.set('continuar');
    this.selecionarDinamica(dinamica.dinamicaId);
    if (entrada.procedimentoId === null) {
      return;
    }
    if (dinamica.procedimentosDisponiveis.some((item) => item.instrumentoProcedimentoConfigId === entrada.procedimentoId)) {
      this.selecionarProcedimento(String(entrada.procedimentoId));
    } else {
      this.avisoProcedimentoIndisponivel.set(true);
    }
  }

  /** O painel não traz `responsavelId`; o assistente não o lê. */
  private instrumentoDoPainel(painel: InstrumentoPainel, tipoInstrumentoNome: string): Instrumento {
    return {
      id: painel.id,
      tipoInstrumentoId: painel.tipoInstrumentoId,
      tipoInstrumentoNome,
      numero: painel.numero,
      designacao: painel.designacao,
      responsavelId: '',
      emVigor: painel.emVigor,
      origemDados: painel.origemDados,
      criadoPorId: painel.criadoPorId,
      criadoEm: painel.criadoEm,
    };
  }

  private detalheCriado(): ProcessoDetalhe | null {
    const processo = this.processoCriado();
    return processo && 'tipoInstrumento' in processo ? processo : null;
  }

  private carregarDisponibilidade(selecionarDinamicaId?: string, depois?: (disponibilidade: InstrumentoDisponibilidade) => void): void {
    const instrumentoId = this.instrumentoEscolhidoId();
    if (!instrumentoId) {
      return;
    }

    this.erro.set(null);
    this.aCarregarDisponibilidade.set(true);
    forkJoin({
      disponibilidade: this.instrumentoService.disponivel(instrumentoId),
      painel: this.instrumentoService.painel(instrumentoId).pipe(catchError((erro: ApiError) => this.semPainel(erro))),
    }).subscribe({
      next: ({ disponibilidade, painel }) => {
        this.disponibilidade.set(disponibilidade);
        this.painel.set(painel);
        this.aCarregarDisponibilidade.set(false);
        if (selecionarDinamicaId) {
          this.modo.set('continuar');
          this.paginaInicial.set('continuar');
          this.selecionarDinamica(selecionarDinamicaId);
        }
        depois?.(disponibilidade);
      },
      error: (erro: ApiError) => {
        this.erro.set(erro);
        this.aCarregarDisponibilidade.set(false);
      },
    });
  }

  /** Sem o painel perde-se o "Último concluído", mas o processo cria-se na mesma. */
  private semPainel(erro: ApiError): Observable<null> {
    if (isDevMode()) {
      console.error('Painel do Instrumento indisponível no assistente', { codigo: erro?.codigo, traceId: erro?.traceId });
    }
    return of(null);
  }

  private limparEscolhasDeDinamica(): void {
    this.modo.set(null);
    this.dinamicaEscolhida.set(null);
    this.dinamicaNovaId.set(null);
    this.descritivoNovo.set('');
    this.procedimentoEscolhido.set(null);
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
