import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { EMPTY, Observable, Subscription, expand, forkJoin, map, reduce } from 'rxjs';
import { PageResponse } from '../../../core/models/page-response.model';
import { ApiError } from '../../../core/models/error-response.model';
import {
  DinamicaConfig,
  ProcedimentoConfig,
  RegraConfig,
  TipoDinamicaConfig,
  TipoInstrumentoConfig,
  TipoProcedimentoConfig,
  pedidoDaRegra,
} from '../../../models/configuracao.model';
import { ConfiguracaoService } from '../../../services/configuracao.service';
import { ConfirmacaoModalComponent } from '../../../shared/components/confirmacao-modal.component';
import { ErrorBannerComponent } from '../../../shared/components/error-banner.component';
import { AlertaComponent } from '../../../shared/ui/alerta/alerta.component';
import { BotaoComponent } from '../../../shared/ui/botao/botao.component';
import { CartaoComponent } from '../../../shared/ui/cartao/cartao.component';
import { OpcaoSeletor, SeletorComponent } from '../../../shared/ui/seletor/seletor.component';
import { SeloComponent } from '../../../shared/ui/selo/selo.component';
import { CelulaTabelaDirective } from '../../../shared/ui/tabela/celula-tabela.directive';
import { ColunaTabela, TabelaComponent } from '../../../shared/ui/tabela/tabela.component';
import { descreverRegra } from './descrever-regra';
import { AlvoRegra, RegraEditorModalComponent } from './regra-editor-modal.component';

/** O máximo que a API devolve por página. */
const TAMANHO_PAGINA = 100;
const DURACAO_MENSAGEM_MS = 4000;

export const SEM_REGRAS_DINAMICA = 'Sem regras: a Dinâmica pode ser iniciada sempre que estiver ativa e houver permissão.';
export const SEM_REGRA_PROCEDIMENTO = 'Sem regra: segue a ordem dos Procedimentos';

interface LinhaExcecao {
  procedimento: ProcedimentoConfig;
  regra: RegraConfig | null;
}

interface EditorRegra {
  alvo: AlvoRegra;
  regra: RegraConfig | null;
}

/** Regras de elegibilidade de uma Dinâmica e exceções ao sequenciamento dos seus Procedimentos. */
@Component({
  selector: 'app-configuracao-regras',
  imports: [
    AlertaComponent,
    BotaoComponent,
    CartaoComponent,
    CelulaTabelaDirective,
    ConfirmacaoModalComponent,
    ErrorBannerComponent,
    ReactiveFormsModule,
    RegraEditorModalComponent,
    SeletorComponent,
    SeloComponent,
    TabelaComponent,
  ],
  templateUrl: './configuracao-regras.component.html',
  styleUrls: ['./configuracao.css', './configuracao-regras.component.css'],
})
export class ConfiguracaoRegrasComponent implements OnInit, OnDestroy {
  private readonly configuracaoService = inject(ConfiguracaoService);

  readonly semRegrasDinamica = SEM_REGRAS_DINAMICA;
  readonly semRegraProcedimento = SEM_REGRA_PROCEDIMENTO;
  readonly colunas: ColunaTabela[] = [
    { chave: 'ordem', titulo: 'Ordem', largura: '7%' },
    { chave: 'procedimento', titulo: 'Procedimento', largura: '20%' },
    { chave: 'regra', titulo: 'Regra' },
    { chave: 'efeito', titulo: 'Efeito', largura: '13%' },
    { chave: 'estado', titulo: 'Estado', largura: '10%' },
    { chave: 'acoes', titulo: 'Ações', largura: '17%' },
  ];

  readonly tiposInstrumento = signal<TipoInstrumentoConfig[]>([]);
  readonly tipoInstrumento = new FormControl<string | null>(null);
  readonly tipoInstrumentoId = signal<number | null>(null);
  readonly dinamicas = signal<DinamicaConfig[]>([]);
  readonly dinamica = new FormControl<string | null>(null);
  readonly dinamicaConfigId = signal<number | null>(null);
  readonly regrasDinamica = signal<RegraConfig[]>([]);
  readonly procedimentos = signal<ProcedimentoConfig[]>([]);
  /** Por `instrumentoProcedimentoConfigId`: o servidor só admite uma regra por Procedimento. */
  readonly regrasProcedimentos = signal<ReadonlyMap<number, RegraConfig>>(new Map());
  readonly tiposDinamica = signal<TipoDinamicaConfig[]>([]);
  readonly tiposProcedimento = signal<TipoProcedimentoConfig[]>([]);
  readonly aCarregar = signal(true);
  readonly erro = signal<ApiError | null>(null);
  readonly erroAcao = signal<ApiError | null>(null);
  readonly mensagemSucesso = signal<string | null>(null);
  readonly editor = signal<EditorRegra | null>(null);
  readonly confirmacao = signal<RegraConfig | null>(null);
  readonly aProcessar = signal(false);

  readonly opcoesTipoInstrumento = computed<OpcaoSeletor[]>(() =>
    this.tiposInstrumento().map((tipo) => ({
      valor: String(tipo.id),
      rotulo: `${tipo.codigo} — ${tipo.nome}${tipo.ativo ? '' : ' (inativo)'}`,
    })),
  );
  readonly opcoesDinamica = computed<OpcaoSeletor[]>(() =>
    this.dinamicas().map((dinamica) => ({
      valor: String(dinamica.id),
      rotulo: `${dinamica.tipoDinamica.nome}${dinamica.ativo ? '' : ' (inativa)'}`,
    })),
  );
  readonly dinamicaEscolhida = computed(() => this.dinamicas().find((d) => d.id === this.dinamicaConfigId()) ?? null);
  readonly contexto = computed(() => {
    const dinamica = this.dinamicaEscolhida();
    return dinamica ? `${dinamica.tipoInstrumento.codigo} — ${dinamica.tipoDinamica.nome}` : '';
  });
  readonly linhasExcecoes = computed<LinhaExcecao[]>(() =>
    this.procedimentos().map((procedimento) => ({ procedimento, regra: this.regrasProcedimentos().get(procedimento.id) ?? null })),
  );
  readonly tituloConfirmacao = computed(() => (this.confirmacao()?.ativo ? 'Desativar regra' : 'Reativar regra'));
  readonly mensagemConfirmacao = computed(() => {
    const regra = this.confirmacao();
    if (!regra) {
      return '';
    }
    const nome = regra.descricao?.trim() || this.resumo(regra);
    return regra.ativo
      ? `Desativar a regra ${nome}? Deixa de ser avaliada de imediato.`
      : `Reativar a regra ${nome}? Volta a ser avaliada de imediato.`;
  });

  private temporizadorMensagem: ReturnType<typeof setTimeout> | undefined;
  private carregamento: Subscription | undefined;
  private listagemDinamicas: Subscription | undefined;

  ngOnInit(): void {
    this.tipoInstrumento.valueChanges.subscribe((valor) => {
      this.tipoInstrumentoId.set(valor ? Number(valor) : null);
      this.carregarDinamicas();
    });
    this.dinamica.valueChanges.subscribe((valor) => {
      this.dinamicaConfigId.set(valor ? Number(valor) : null);
      this.carregarRegras();
    });
    this.configuracaoService.listarTiposInstrumento({}, 0, TAMANHO_PAGINA).subscribe({
      next: (resposta) => {
        const tipos = [...resposta.content].sort((a, b) => a.codigo.localeCompare(b.codigo));
        this.tiposInstrumento.set(tipos);
        if (tipos.length > 0) {
          this.tipoInstrumento.setValue(String(tipos[0].id));
        } else {
          this.aCarregar.set(false);
        }
      },
      error: (erro: ApiError) => {
        this.erro.set(erro);
        this.aCarregar.set(false);
      },
    });
    todasAsPaginas((pagina) => this.configuracaoService.listarTiposDinamica({}, pagina, TAMANHO_PAGINA)).subscribe({
      next: (tipos) => this.tiposDinamica.set(tipos),
      error: (erro: ApiError) => this.erroAcao.set(erro),
    });
    todasAsPaginas((pagina) => this.configuracaoService.listarTiposProcedimento({}, pagina, TAMANHO_PAGINA)).subscribe({
      next: (tipos) => this.tiposProcedimento.set(tipos),
      error: (erro: ApiError) => this.erroAcao.set(erro),
    });
  }

  ngOnDestroy(): void {
    clearTimeout(this.temporizadorMensagem);
    this.carregamento?.unsubscribe();
    this.listagemDinamicas?.unsubscribe();
  }

  resumo(regra: RegraConfig): string {
    return descreverRegra(regra, { tiposDinamica: this.tiposDinamica(), tiposProcedimento: this.tiposProcedimento() });
  }

  efeito(regra: RegraConfig): string {
    return regra.substituiSequenciamento ? 'Substitui a ordem' : 'Soma-se à ordem';
  }

  nomeRegra(regra: RegraConfig): string {
    return regra.descricao?.trim() || this.resumo(regra);
  }

  adicionarRegraDinamica(): void {
    const dinamica = this.dinamicaEscolhida();
    if (dinamica) {
      this.abrirEditor({ tipo: 'DINAMICA', id: dinamica.id, nome: dinamica.tipoDinamica.nome, contexto: this.contexto() }, null);
    }
  }

  editarRegraDinamica(regra: RegraConfig): void {
    const dinamica = this.dinamicaEscolhida();
    if (dinamica) {
      this.abrirEditor({ tipo: 'DINAMICA', id: dinamica.id, nome: dinamica.tipoDinamica.nome, contexto: this.contexto() }, regra);
    }
  }

  editarExcecao(linha: LinhaExcecao): void {
    const procedimento = linha.procedimento;
    this.abrirEditor(
      { tipo: 'PROCEDIMENTO', id: procedimento.id, nome: procedimento.tipoProcedimento.nome, contexto: this.contexto() },
      linha.regra,
    );
  }

  fecharEditor(): void {
    this.editor.set(null);
  }

  aoGravar(): void {
    const nova = this.editor()?.regra === null;
    this.editor.set(null);
    this.mostrarMensagem(nova ? 'Regra criada.' : 'Regra atualizada.');
    this.carregarRegras(true);
  }

  pedirAlternarEstado(regra: RegraConfig): void {
    this.confirmacao.set(regra);
  }

  cancelarConfirmacao(): void {
    this.confirmacao.set(null);
  }

  confirmarAlternarEstado(): void {
    const regra = this.confirmacao();
    if (!regra || this.aProcessar()) {
      return;
    }
    this.aProcessar.set(true);
    this.erroAcao.set(null);
    this.configuracaoService.atualizarRegra(regra.id, { ...pedidoDaRegra(regra), ativo: !regra.ativo }).subscribe({
      next: () => {
        this.aProcessar.set(false);
        this.confirmacao.set(null);
        this.mostrarMensagem(regra.ativo ? 'Regra desativada.' : 'Regra reativada.');
        this.carregarRegras(true);
      },
      error: (erro: ApiError) => {
        this.aProcessar.set(false);
        this.confirmacao.set(null);
        this.erroAcao.set(erro);
      },
    });
  }

  private abrirEditor(alvo: AlvoRegra, regra: RegraConfig | null): void {
    this.erroAcao.set(null);
    this.editor.set({ alvo, regra });
  }

  private mostrarMensagem(mensagem: string): void {
    clearTimeout(this.temporizadorMensagem);
    this.mensagemSucesso.set(mensagem);
    this.temporizadorMensagem = setTimeout(() => this.mensagemSucesso.set(null), DURACAO_MENSAGEM_MS);
  }

  /** Ativas e inativas; abre na primeira que a API devolve. */
  private carregarDinamicas(): void {
    const tipoInstrumentoId = this.tipoInstrumentoId();
    this.listagemDinamicas?.unsubscribe();
    this.carregamento?.unsubscribe();
    this.dinamicas.set([]);
    this.limparRegras();
    if (tipoInstrumentoId === null) {
      return;
    }
    this.aCarregar.set(true);
    this.erro.set(null);
    this.listagemDinamicas = this.configuracaoService.listarDinamicasConfig({ tipoInstrumentoId }, 0, TAMANHO_PAGINA).subscribe({
      next: (resposta) => {
        this.dinamicas.set(resposta.content);
        const primeira = resposta.content[0];
        this.dinamica.setValue(primeira ? String(primeira.id) : null);
        if (!primeira) {
          this.aCarregar.set(false);
        }
      },
      error: (erro: ApiError) => {
        this.erro.set(erro);
        this.aCarregar.set(false);
      },
    });
  }

  /**
   * As três listas em paralelo: as regras da Dinâmica, os seus Procedimentos e as regras desses Procedimentos.
   * `manterConteudo` (depois de gravar): sem o esqueleto, os botões continuam no ecrã e o modal devolve-lhes o foco.
   */
  private carregarRegras(manterConteudo = false): void {
    const instrumentoDinamicaConfigId = this.dinamicaConfigId();
    this.carregamento?.unsubscribe();
    if (instrumentoDinamicaConfigId === null) {
      this.limparRegras();
      this.aCarregar.set(false);
      return;
    }
    if (!manterConteudo) {
      this.limparRegras();
    }
    this.aCarregar.set(!manterConteudo);
    this.erro.set(null);
    this.carregamento = forkJoin({
      regrasDinamica: this.configuracaoService.listarRegras({ instrumentoDinamicaConfigId }, 0, TAMANHO_PAGINA),
      procedimentos: this.configuracaoService.listarProcedimentosConfig({ instrumentoDinamicaConfigId }, 0, TAMANHO_PAGINA),
      regrasProcedimentos: this.configuracaoService.listarRegras({ dinamicaDosProcedimentosId: instrumentoDinamicaConfigId }, 0, TAMANHO_PAGINA),
    }).subscribe({
      next: ({ regrasDinamica, procedimentos, regrasProcedimentos }) => {
        this.regrasDinamica.set(regrasDinamica.content);
        this.procedimentos.set([...procedimentos.content].sort((a, b) => a.ordem - b.ordem));
        const porProcedimento = new Map<number, RegraConfig>();
        for (const regra of regrasProcedimentos.content) {
          if (regra.instrumentoProcedimentoConfigId !== null) {
            porProcedimento.set(regra.instrumentoProcedimentoConfigId, regra);
          }
        }
        this.regrasProcedimentos.set(porProcedimento);
        this.aCarregar.set(false);
      },
      error: (erro: ApiError) => {
        this.erro.set(erro);
        this.aCarregar.set(false);
      },
    });
  }

  private limparRegras(): void {
    this.regrasDinamica.set([]);
    this.procedimentos.set([]);
    this.regrasProcedimentos.set(new Map());
  }
}

/** Os catálogos de tipos podem passar das 100 linhas de uma página. */
function todasAsPaginas<T>(pedir: (pagina: number) => Observable<PageResponse<T>>): Observable<T[]> {
  return pedir(0).pipe(
    expand((resposta) => (resposta.page + 1 < resposta.totalPages ? pedir(resposta.page + 1) : EMPTY)),
    map((resposta) => resposta.content),
    reduce((todos, pagina) => [...todos, ...pagina], [] as T[]),
  );
}
