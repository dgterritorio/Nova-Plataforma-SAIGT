import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { Observable, Subscription, debounceTime, distinctUntilChanged, forkJoin, map, merge, of, switchMap } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { PageResponse } from '../../../core/models/page-response.model';
import { ApiError } from '../../../core/models/error-response.model';
import { Entidade } from '../../../models/entidade.model';
import {
  CriarNotificacaoRequest,
  CriarNotificacaoResponse,
  DestinatarioNotificacao,
} from '../../../models/notificacao.model';
import { ProcessoListagem } from '../../../models/processo.model';
import { Utilizador } from '../../../models/utilizador.model';
import { EntidadeService } from '../../../services/entidade.service';
import { NotificacaoService } from '../../../services/notificacao.service';
import { ProcessoService } from '../../../services/processo.service';
import { UtilizadorService } from '../../../services/utilizador.service';
import { rotuloEstado } from '../../../shared/components/estado-processo-badge.component';
import { AlertaComponent } from '../../../shared/ui/alerta/alerta.component';
import { BotaoComponent } from '../../../shared/ui/botao/botao.component';
import { ESPERA_PESQUISA_MS } from '../../../shared/ui/campo-pesquisa/campo-pesquisa.component';
import { CampoTextoComponent } from '../../../shared/ui/campo-texto/campo-texto.component';
import { ModalComponent } from '../../../shared/ui/modal/modal.component';
import { OpcaoSeletor, SeletorComponent } from '../../../shared/ui/seletor/seletor.component';
import { AnexosNotificacaoComponent } from '../anexos-notificacao/anexos-notificacao.component';

export type TipoCriavel = 'ESTATICA' | 'RESPOSTA_OBRIGATORIA' | 'PEDIDO_APOIO' | 'REGULARIZACAO_PROCEDIMENTO';
export type ModoDestinatarios = 'TODOS' | 'ENTIDADES' | 'UTILIZADORES';
export type TipoDestinatario = 'ENTIDADE' | 'UTILIZADOR';

export const MAX_TITULO = 200;
export const MAX_TEXTO = 5000;
const MAX_ID_IGT = 50;
const MAX_DESCRICAO_IGT = 500;
const TAMANHO_PAGINA_LISTAS = 100;
const TAMANHO_PESQUISA_PROCESSOS = 20;
export const AJUDA_ANEXOS_LOTE =
  'Os documentos só podem ser anexados a uma notícia com um único destinatário ou para todos.';

export type NotificacaoCriada = CriarNotificacaoResponse & { anexosFalhados?: number };

type CampoTexto = 'titulo' | 'texto' | 'idIgtSnit' | 'descricaoIgt' | 'prazo';
const CAMPOS_TEXTO: readonly CampoTexto[] = ['titulo', 'texto', 'idIgtSnit', 'descricaoIgt', 'prazo'];

const MENSAGENS: Record<CampoTexto, string> = {
  titulo: 'Indique o título.',
  texto: 'Indique a mensagem.',
  idIgtSnit: `Máximo ${MAX_ID_IGT} caracteres.`,
  descricaoIgt: 'Indique a descrição IGT.',
  prazo: 'Data inválida.',
};

interface Escolhido {
  id: string;
  nome: string;
}

/** Processo já escolhido por quem abre o modal (o detalhe do processo): fica preenchido e não editável. */
export interface ProcessoFixo {
  id: string;
  rotulo: string;
}

export const AJUDA_REGULARIZACAO = 'Indique o motivo e a publicação no DR.';

function naoEmBranco(controlo: AbstractControl<string | null>): ValidationErrors | null {
  return (controlo.value ?? '').trim() ? null : { required: true };
}

export function fimDoDiaEmLisboa(dia: string): string {
  const [ano, mes, diaDoMes] = dia.split('-').map(Number);
  const meioDia = new Date(Date.UTC(ano, mes - 1, diaDoMes, 12));
  const nomeFuso =
    new Intl.DateTimeFormat('en-US', { timeZone: 'Europe/Lisbon', timeZoneName: 'longOffset' })
      .formatToParts(meioDia)
      .find((parte) => parte.type === 'timeZoneName')?.value ?? 'GMT';
  const desvio = nomeFuso === 'GMT' ? '+00:00' : nomeFuso.replace('GMT', '');
  return `${dia}T23:59:59.999${desvio}`;
}

function todasAsPaginas<T>(pedir: (pagina: number) => Observable<PageResponse<T>>): Observable<T[]> {
  return pedir(0).pipe(
    switchMap((primeira) => {
      const restantes = Array.from({ length: Math.max(primeira.totalPages - 1, 0) }, (_, i) => pedir(i + 1));
      return restantes.length === 0
        ? of([...primeira.content])
        : forkJoin(restantes).pipe(map((paginas) => [...primeira.content, ...paginas.flatMap((p) => p.content)]));
    }),
  );
}

function nomeUtilizador(utilizador: Utilizador): string {
  return utilizador.nome ?? utilizador.email ?? utilizador.id;
}

function rotuloProcesso(processo: ProcessoListagem): string {
  return [processo.designacaoInstrumento, processo.nomeProcedimento, processo.entidadeNome, rotuloEstado(processo.estado)].join(
    ' — ',
  );
}

@Component({
  selector: 'app-criar-notificacao-modal',
  imports: [
    AlertaComponent,
    AnexosNotificacaoComponent,
    BotaoComponent,
    CampoTextoComponent,
    ModalComponent,
    ReactiveFormsModule,
    SeletorComponent,
  ],
  templateUrl: './criar-notificacao-modal.component.html',
  styleUrl: './criar-notificacao-modal.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CriarNotificacaoModalComponent {
  private readonly notificacaoService = inject(NotificacaoService);
  private readonly entidadeService = inject(EntidadeService);
  private readonly utilizadorService = inject(UtilizadorService);
  private readonly processoService = inject(ProcessoService);
  private readonly auth = inject(AuthService);
  private pesquisaProcessos: Subscription | null = null;

  readonly aberto = input(false);
  readonly processoFixo = input<ProcessoFixo | null>(null);
  readonly fechar = output<void>();
  readonly criada = output<NotificacaoCriada>();

  readonly maxTitulo = MAX_TITULO;
  readonly maxTexto = MAX_TEXTO;
  readonly maxIdIgt = MAX_ID_IGT;
  readonly maxDescricaoIgt = MAX_DESCRICAO_IGT;

  readonly entidades = signal<Entidade[]>([]);
  readonly utilizadores = signal<Utilizador[]>([]);
  readonly processos = signal<ProcessoListagem[]>([]);
  readonly entidadesEscolhidas = signal<Escolhido[]>([]);
  readonly utilizadoresEscolhidos = signal<Escolhido[]>([]);
  readonly aGravar = signal(false);
  readonly erroGeral = signal<string | null>(null);
  readonly aConfirmarDescarte = signal(false);
  private readonly errosApi = signal<Partial<Record<CampoTexto, string>>>({});
  private readonly botaoContinuar = viewChild('continuar', { read: ElementRef<HTMLButtonElement> });
  private readonly anexos = viewChild(AnexosNotificacaoComponent);

  readonly formulario = new FormGroup({
    tipo: new FormControl<TipoCriavel>('ESTATICA', { nonNullable: true }),
    modo: new FormControl<ModoDestinatarios>('TODOS', { nonNullable: true }),
    entidadeAAdicionar: new FormControl<string | null>(null),
    utilizadorAAdicionar: new FormControl<string | null>(null),
    tipoDestinatario: new FormControl<TipoDestinatario>('ENTIDADE', { nonNullable: true }),
    destinatarioEntidade: new FormControl<string | null>(null),
    destinatarioUtilizador: new FormControl<string | null>(null),
    processoId: new FormControl<string | null>(null),
    idIgtSnit: new FormControl<string | null>(null, Validators.maxLength(MAX_ID_IGT)),
    descricaoIgt: new FormControl<string | null>(null, Validators.maxLength(MAX_DESCRICAO_IGT)),
    titulo: new FormControl<string | null>(null, [naoEmBranco, Validators.maxLength(MAX_TITULO)]),
    texto: new FormControl<string | null>(null, [naoEmBranco, Validators.maxLength(MAX_TEXTO)]),
    prazo: new FormControl<string | null>(null),
  });

  /** Fora do formulário: pesquisar não é dado da notificação. */
  readonly pesquisaProcesso = new FormControl('', { nonNullable: true });
  readonly rotuloProcessoFixo = new FormControl({ value: '', disabled: true }, { nonNullable: true });

  /** O utilizador externo pede apoio e regularizações; o Master cria as Notícias e as Respostas obrigatórias. */
  readonly daEntidade = computed(() => this.auth.perfilInterno() === null);

  /** Os eventos de cada campo: o grupo só emite o «tocado» do primeiro campo que se toca, e os erros seguintes não apareciam. */
  private readonly estadoFormulario = toSignal(
    merge(this.formulario.events, ...Object.values(this.formulario.controls).map((controlo) => controlo.events)),
    { initialValue: null },
  );

  readonly valores = computed(() => {
    this.estadoFormulario();
    return this.formulario.getRawValue();
  });

  readonly estatica = computed(() => this.valores().tipo === 'ESTATICA');
  readonly pedidoApoio = computed(() => this.valores().tipo === 'PEDIDO_APOIO');
  readonly regularizacao = computed(() => this.valores().tipo === 'REGULARIZACAO_PROCEDIMENTO');

  readonly opcoesEntidadeAAdicionar = computed<OpcaoSeletor[]>(() => {
    const escolhidas = new Set(this.entidadesEscolhidas().map((e) => e.id));
    return this.opcoesEntidade().filter((opcao) => !escolhidas.has(opcao.valor));
  });

  readonly opcoesUtilizadorAAdicionar = computed<OpcaoSeletor[]>(() => {
    const escolhidos = new Set(this.utilizadoresEscolhidos().map((u) => u.id));
    return this.opcoesUtilizador().filter((opcao) => !escolhidos.has(opcao.valor));
  });

  readonly opcoesEntidade = computed<OpcaoSeletor[]>(() =>
    this.entidades().map((entidade) => ({ valor: String(entidade.id), rotulo: entidade.nome })),
  );

  readonly opcoesUtilizador = computed<OpcaoSeletor[]>(() =>
    this.utilizadores().map((utilizador) => ({
      valor: utilizador.id,
      rotulo: [utilizador.nome, utilizador.email].filter(Boolean).join(' — ') || utilizador.id,
    })),
  );

  readonly opcoesProcesso = computed<OpcaoSeletor[]>(() =>
    this.processos().map((processo) => ({ valor: processo.id, rotulo: rotuloProcesso(processo) })),
  );

  /** Na Estática por entidades ou utilizadores, o erro aparece logo: o botão de criar está desativado por isso. */
  readonly erroDestinatarios = computed<string | null>(() => {
    const { tipo, modo } = this.valores();
    if (tipo !== 'ESTATICA') {
      return null;
    }
    if (modo === 'ENTIDADES' && this.entidadesEscolhidas().length === 0) {
      return 'Adicione pelo menos uma entidade.';
    }
    if (modo === 'UTILIZADORES' && this.utilizadoresEscolhidos().length === 0) {
      return 'Adicione pelo menos um utilizador.';
    }
    return null;
  });

  readonly erroDestinatarioUnico = computed<string | null>(() => {
    const valores = this.valores();
    if (valores.tipo !== 'RESPOSTA_OBRIGATORIA') {
      return null;
    }
    const entidade = valores.tipoDestinatario === 'ENTIDADE';
    const controlo = entidade ? this.formulario.controls.destinatarioEntidade : this.formulario.controls.destinatarioUtilizador;
    return !controlo.value && controlo.touched ? (entidade ? 'Escolha a entidade.' : 'Escolha o utilizador.') : null;
  });

  readonly erros = computed<Record<CampoTexto, string | null>>(() => {
    this.estadoFormulario();
    const daApi = this.errosApi();
    const erros = {} as Record<CampoTexto, string | null>;
    for (const campo of CAMPOS_TEXTO) {
      const controlo = this.formulario.controls[campo];
      const mostrar = controlo.invalid && (controlo.touched || controlo.dirty);
      erros[campo] = mostrar
        ? controlo.hasError('maxlength')
          ? `Máximo ${controlo.getError('maxlength').requiredLength} caracteres.`
          : MENSAGENS[campo]
        : (daApi[campo] ?? null);
    }
    return erros;
  });

  readonly destinatariosValidos = computed(() => {
    const valores = this.valores();
    if (this.daEntidade()) {
      return true;
    }
    if (valores.tipo === 'ESTATICA') {
      return this.erroDestinatarios() === null;
    }
    return valores.tipoDestinatario === 'ENTIDADE' ? !!valores.destinatarioEntidade : !!valores.destinatarioUtilizador;
  });

  readonly podeCriar = computed(() => {
    this.estadoFormulario();
    return this.formulario.valid && this.destinatariosValidos() && !this.aGravar();
  });

  /** O Pedido de apoio não tem «Documentos» na criação (anexa-se depois, na conversa). */
  readonly anexosPermitidos = computed(() => {
    const { tipo, modo } = this.valores();
    if (tipo === 'PEDIDO_APOIO') {
      return false;
    }
    if (tipo !== 'ESTATICA' || modo === 'TODOS') {
      return true;
    }
    return (modo === 'ENTIDADES' ? this.entidadesEscolhidas() : this.utilizadoresEscolhidos()).length <= 1;
  });
  readonly ajudaAnexos = computed(() => (this.anexosPermitidos() ? null : AJUDA_ANEXOS_LOTE));

  readonly contadorTitulo = computed(() => `${(this.valores().titulo ?? '').length}/${MAX_TITULO} caracteres`);
  readonly contadorTexto = computed(() => `${(this.valores().texto ?? '').length}/${MAX_TEXTO} caracteres`);
  readonly ajudaTexto = computed(() =>
    this.regularizacao() ? `${AJUDA_REGULARIZACAO} ${this.contadorTexto()}` : this.contadorTexto(),
  );

  constructor() {
    effect(() => {
      if (this.aberto()) {
        untracked(() => {
          this.limpar();
          if (!this.daEntidade()) {
            this.carregarListas();
          }
        });
      }
    });
    this.formulario.controls.tipo.valueChanges.pipe(takeUntilDestroyed()).subscribe((tipo) => this.exigirDescricao(tipo));
    this.pesquisaProcesso.valueChanges
      .pipe(debounceTime(ESPERA_PESQUISA_MS), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe((termo) => this.pesquisarProcessos(termo));
    for (const campo of CAMPOS_TEXTO) {
      this.formulario.controls[campo].valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
        if (this.errosApi()[campo]) {
          this.errosApi.update((erros) => {
            const restantes = { ...erros };
            delete restantes[campo];
            return restantes;
          });
        }
      });
    }
  }

  adicionarEntidade(): void {
    const id = this.formulario.controls.entidadeAAdicionar.value;
    const entidade = this.entidades().find((e) => String(e.id) === id);
    if (entidade) {
      this.entidadesEscolhidas.update((lista) => [...lista, { id: String(entidade.id), nome: entidade.nome }]);
      this.formulario.controls.entidadeAAdicionar.reset();
    }
  }

  adicionarUtilizador(): void {
    const id = this.formulario.controls.utilizadorAAdicionar.value;
    const utilizador = this.utilizadores().find((u) => u.id === id);
    if (utilizador) {
      this.utilizadoresEscolhidos.update((lista) => [...lista, { id: utilizador.id, nome: nomeUtilizador(utilizador) }]);
      this.formulario.controls.utilizadorAAdicionar.reset();
    }
  }

  removerEntidade(id: string): void {
    this.entidadesEscolhidas.update((lista) => lista.filter((e) => e.id !== id));
  }

  removerUtilizador(id: string): void {
    this.utilizadoresEscolhidos.update((lista) => lista.filter((u) => u.id !== id));
  }

  pesquisarProcessos(termo: string): void {
    this.pesquisaProcessos?.unsubscribe();
    const designacao = termo.trim();
    const escolhido = this.processos().find((p) => p.id === this.formulario.controls.processoId.value);
    if (!designacao) {
      this.processos.set(escolhido ? [escolhido] : []);
      return;
    }
    this.pesquisaProcessos = this.processoService
      .listar({ designacaoInstrumento: designacao }, 0, TAMANHO_PESQUISA_PROCESSOS)
      .subscribe({
        next: (pagina) => {
          const encontrados = pagina.content;
          this.processos.set(escolhido && !encontrados.some((p) => p.id === escolhido.id) ? [escolhido, ...encontrados] : encontrados);
        },
        error: (erro: ApiError) => this.erroGeral.set(erro.mensagem),
      });
  }

  pedirFecho(): void {
    if (this.aGravar()) {
      return;
    }
    if (this.preenchido() && !this.aConfirmarDescarte()) {
      this.aConfirmarDescarte.set(true);
      setTimeout(() => this.botaoContinuar()?.nativeElement.focus());
      return;
    }
    this.fechar.emit();
  }

  continuarAEditar(): void {
    this.aConfirmarDescarte.set(false);
  }

  descartar(): void {
    this.aConfirmarDescarte.set(false);
    this.fechar.emit();
  }

  criar(): void {
    if (!this.podeCriar()) {
      return;
    }
    this.errosApi.set({});
    this.erroGeral.set(null);
    this.aGravar.set(true);
    this.notificacaoService.criar(this.pedido()).subscribe({
      next: (resposta) => this.anexarDepoisDeCriar(resposta),
      error: (erro: unknown) => {
        this.aGravar.set(false);
        this.tratarErro(erro);
      },
    });
  }

  private anexarDepoisDeCriar(resposta: CriarNotificacaoResponse): void {
    const anexos = this.anexos();
    const escolhidos = anexos?.ficheiros().length ?? 0;
    if (!anexos || escolhidos === 0 || !this.anexosPermitidos() || resposta.notificacoes.length !== 1) {
      this.aGravar.set(false);
      this.criada.emit(resposta);
      return;
    }
    anexos.enviar(resposta.notificacoes[0].id).subscribe((resultados) => {
      this.aGravar.set(false);
      this.criada.emit({ ...resposta, anexosFalhados: resultados.filter((r) => r.documento === null).length });
    });
  }

  private pedido(): CriarNotificacaoRequest {
    const valores = this.formulario.getRawValue();
    const opcional = (valor: string | null) => (valor ?? '').trim() || undefined;
    if (valores.tipo === 'PEDIDO_APOIO' || valores.tipo === 'REGULARIZACAO_PROCEDIMENTO') {
      return {
        tipo: valores.tipo,
        titulo: (valores.titulo ?? '').trim(),
        texto: (valores.texto ?? '').trim(),
        processoId: valores.tipo === 'PEDIDO_APOIO' ? (valores.processoId ?? undefined) : undefined,
        idIgtSnit: opcional(valores.idIgtSnit),
        descricaoIgt: opcional(valores.descricaoIgt),
        destinatarios: [],
      };
    }
    const pedido: CriarNotificacaoRequest = {
      tipo: valores.tipo,
      titulo: (valores.titulo ?? '').trim(),
      texto: (valores.texto ?? '').trim(),
      prazo: valores.prazo ? fimDoDiaEmLisboa(valores.prazo) : undefined,
      destinatarios: this.destinatarios(),
    };
    if (valores.tipo === 'RESPOSTA_OBRIGATORIA') {
      pedido.processoId = valores.processoId ?? undefined;
      pedido.idIgtSnit = opcional(valores.idIgtSnit);
      pedido.descricaoIgt = opcional(valores.descricaoIgt);
    }
    return pedido;
  }

  private destinatarios(): DestinatarioNotificacao[] {
    const valores = this.formulario.getRawValue();
    if (valores.tipo === 'RESPOSTA_OBRIGATORIA') {
      return valores.tipoDestinatario === 'ENTIDADE'
        ? [{ entidadeId: Number(valores.destinatarioEntidade) }]
        : [{ utilizadorId: valores.destinatarioUtilizador ?? '' }];
    }
    if (valores.modo === 'ENTIDADES') {
      return this.entidadesEscolhidas().map((e) => ({ entidadeId: Number(e.id) }));
    }
    if (valores.modo === 'UTILIZADORES') {
      return this.utilizadoresEscolhidos().map((u) => ({ utilizadorId: u.id }));
    }
    return [];
  }

  private tratarErro(erro: unknown): void {
    if (!(erro instanceof ApiError)) {
      this.erroGeral.set('Não foi possível criar a notificação.');
      return;
    }
    const porCampo: Partial<Record<CampoTexto, string>> = {};
    for (const campo of erro.codigo === 'VALIDACAO' ? (erro.campos ?? []) : []) {
      if ((CAMPOS_TEXTO as readonly string[]).includes(campo.path)) {
        porCampo[campo.path as CampoTexto] = campo.erro;
      }
    }
    this.errosApi.set(porCampo);
    if (Object.keys(porCampo).length === 0) {
      this.erroGeral.set(erro.mensagem);
    }
  }

  private preenchido(): boolean {
    return (
      this.formulario.dirty ||
      this.entidadesEscolhidas().length > 0 ||
      this.utilizadoresEscolhidos().length > 0 ||
      (this.anexos()?.ficheiros().length ?? 0) > 0
    );
  }

  /** Todas as páginas: a API dá no máximo 100 por página e há mais entidades do que isso. */
  private carregarListas(): void {
    if (this.entidades().length === 0) {
      todasAsPaginas((pagina) => this.entidadeService.listar({ ativo: true }, pagina, TAMANHO_PAGINA_LISTAS)).subscribe({
        next: (entidades) => this.entidades.set(entidades.sort((a, b) => a.nome.localeCompare(b.nome, 'pt'))),
        error: (erro: ApiError) => this.erroGeral.set(erro.mensagem),
      });
    }
    if (this.utilizadores().length === 0) {
      todasAsPaginas((pagina) => this.utilizadorService.listar({}, pagina, TAMANHO_PAGINA_LISTAS)).subscribe({
        next: (utilizadores) =>
          this.utilizadores.set(utilizadores.sort((a, b) => nomeUtilizador(a).localeCompare(nomeUtilizador(b), 'pt'))),
        error: (erro: ApiError) => this.erroGeral.set(erro.mensagem),
      });
    }
  }

  /** Na regularização a «Descrição IGT» é obrigatória; nos outros tipos é opcional. */
  private exigirDescricao(tipo: TipoCriavel): void {
    const controlo = this.formulario.controls.descricaoIgt;
    controlo.setValidators(
      tipo === 'REGULARIZACAO_PROCEDIMENTO'
        ? [naoEmBranco, Validators.maxLength(MAX_DESCRICAO_IGT)]
        : Validators.maxLength(MAX_DESCRICAO_IGT),
    );
    controlo.updateValueAndValidity();
  }

  private limpar(): void {
    const fixo = this.processoFixo();
    this.formulario.reset({ tipo: this.daEntidade() ? 'PEDIDO_APOIO' : 'ESTATICA', processoId: fixo?.id ?? null });
    this.rotuloProcessoFixo.setValue(fixo?.rotulo ?? '');
    this.pesquisaProcesso.reset('', { emitEvent: false });
    this.entidadesEscolhidas.set([]);
    this.utilizadoresEscolhidos.set([]);
    this.processos.set([]);
    this.errosApi.set({});
    this.erroGeral.set(null);
    this.aConfirmarDescarte.set(false);
    this.anexos()?.limpar();
  }
}
