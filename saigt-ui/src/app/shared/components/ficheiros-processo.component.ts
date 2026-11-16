import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { EMPTY, Subject, catchError, switchMap } from 'rxjs';
import { ApiError } from '../../core/models/error-response.model';
import { CategoriaDocumento, DocumentoProcesso, FiltroDocumentos } from '../../models/documento.model';
import { EstadoValidacao } from '../../models/processo.model';
import { DocumentoService } from '../../services/documento.service';
import { BotaoComponent } from '../ui/botao/botao.component';
import { CartaoComponent } from '../ui/cartao/cartao.component';
import { PaginacaoComponent } from '../ui/paginacao/paginacao.component';
import { OpcaoSeletor, SeletorComponent } from '../ui/seletor/seletor.component';
import { SeloComponent, TomSelo } from '../ui/selo/selo.component';
import { CelulaTabelaDirective } from '../ui/tabela/celula-tabela.directive';
import { ColunaTabela, TabelaComponent } from '../ui/tabela/tabela.component';
import { ErrorBannerComponent } from './error-banner.component';
import { textoTotalFicheiros } from './ficheiros-remetidos.component';

const TAMANHO_PAGINA = 20;

/** Valor da opção «sem filtro»: o `<select>` do seletor não deixa voltar ao placeholder. */
const TODOS = 'TODOS';

export const ROTULO_CAIXA: Record<CategoriaDocumento, string> = { PUBLICACAO: 'Publicação', DEPOSITO: 'Depósito' };

export const VALIDACAO: Record<EstadoValidacao, { rotulo: string; tom: TomSelo }> = {
  POR_VALIDAR: { rotulo: 'Por validar', tom: 'neutro' },
  VALIDADO: { rotulo: 'Validado', tom: 'sucesso' },
  REJEITADO: { rotulo: 'Rejeitado', tom: 'erro' },
};

const OPCOES_CAIXA: OpcaoSeletor[] = [
  { valor: TODOS, rotulo: 'Todas' },
  { valor: 'PUBLICACAO', rotulo: ROTULO_CAIXA.PUBLICACAO },
  { valor: 'DEPOSITO', rotulo: ROTULO_CAIXA.DEPOSITO },
];

const OPCOES_VALIDACAO: OpcaoSeletor[] = [
  { valor: TODOS, rotulo: 'Todas' },
  ...(Object.keys(VALIDACAO) as EstadoValidacao[]).map((estado) => ({ valor: estado, rotulo: VALIDACAO[estado].rotulo })),
];

const COLUNAS: ColunaTabela[] = [
  { chave: 'nomeFicheiro', titulo: 'Nome do ficheiro', largura: '27%' },
  { chave: 'extensao', titulo: 'Tipo', largura: '9%' },
  { chave: 'categoria', titulo: 'Caixa', largura: '13%' },
  { chave: 'item', titulo: 'Item', largura: '18%' },
  { chave: 'criadoEm', titulo: 'Data de envio', largura: '19%' },
  { chave: 'estadoValidacao', titulo: 'Validação', largura: '14%' },
];

interface ItemFiltro {
  documentoConfigId: number;
  nome: string;
  categoria: CategoriaDocumento;
}

/** Sem resposta do servidor o interceptor só tem o texto técnico do Angular; o do backend já vem legível. */
function comMensagemLegivel(erro: ApiError): ApiError {
  return erro.codigo === 'ERRO_DESCONHECIDO'
    ? new ApiError(erro.codigo, 'Não foi possível carregar os ficheiros. Tente novamente.', erro.traceId, erro.campos)
    : erro;
}

function valorDoFiltro(valor: string | null): string | undefined {
  return !valor || valor === TODOS ? undefined : valor;
}

/** Ficheiros do processo com filtros e paginação do servidor, para o detalhe. O Resumo e o recibo usam `app-ficheiros-remetidos`. */
@Component({
  selector: 'app-ficheiros-processo',
  imports: [
    BotaoComponent,
    CartaoComponent,
    CelulaTabelaDirective,
    DatePipe,
    ErrorBannerComponent,
    PaginacaoComponent,
    ReactiveFormsModule,
    SeletorComponent,
    SeloComponent,
    TabelaComponent,
  ],
  templateUrl: './ficheiros-processo.component.html',
  styleUrls: ['./ficheiros-remetidos.component.css', './ficheiros-processo.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FicheirosProcessoComponent implements OnInit {
  private readonly documentoService = inject(DocumentoService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly consulta = new Subject<void>();

  readonly processoId = input.required<string>();

  readonly colunas = COLUNAS;
  readonly opcoesCaixa = OPCOES_CAIXA;
  readonly opcoesValidacao = OPCOES_VALIDACAO;
  readonly tamanhoPagina = TAMANHO_PAGINA;

  readonly controloCaixa = new FormControl<string | null>(TODOS);
  readonly controloItem = new FormControl<string | null>(TODOS);
  readonly controloValidacao = new FormControl<string | null>(TODOS);

  readonly documentos = signal<DocumentoProcesso[]>([]);
  readonly total = signal(0);
  readonly totalPaginas = signal(0);
  readonly pagina = signal(0);
  readonly filtro = signal<FiltroDocumentos>({});
  readonly itens = signal<ItemFiltro[]>([]);
  readonly carregado = signal(false);
  readonly aCarregar = signal(true);
  readonly erro = signal<ApiError | null>(null);

  readonly temFiltro = computed(() => Object.keys(this.filtro()).length > 0);
  readonly textoTotal = computed(() => textoTotalFicheiros(this.total()));
  readonly mensagemVazia = computed(() =>
    this.temFiltro() ? 'Sem ficheiros para os filtros indicados.' : 'Sem ficheiros remetidos.',
  );
  readonly nomesItens = computed(() => new Map(this.itens().map((item) => [item.documentoConfigId, item.nome])));
  readonly opcoesItem = computed<OpcaoSeletor[]>(() => {
    const caixa = this.filtro().categoria;
    const itens = this.itens()
      .filter((item) => !caixa || item.categoria === caixa)
      .map((item) => ({ valor: String(item.documentoConfigId), rotulo: `${item.nome} — ${ROTULO_CAIXA[item.categoria]}` }));
    return [{ valor: TODOS, rotulo: 'Todos' }, ...itens];
  });
  /** Sem itens na estrutura (só traz os dos elementos afetados) não há o que filtrar. */
  readonly mostraFiltroItem = computed(() => this.opcoesItem().length > 1);
  readonly anuncio = computed(() =>
    this.aCarregar() || this.erro()
      ? ''
      : `${textoTotalFicheiros(this.total())}, página ${this.pagina() + 1} de ${Math.max(1, this.totalPaginas())}.`,
  );

  ngOnInit(): void {
    this.consulta
      .pipe(
        switchMap(() => {
          this.aCarregar.set(true);
          this.erro.set(null);
          return this.documentoService.listar(this.processoId(), this.filtro(), this.pagina(), TAMANHO_PAGINA).pipe(
            catchError((erro: ApiError) => {
              this.erro.set(comMensagemLegivel(erro));
              this.aCarregar.set(false);
              return EMPTY;
            }),
          );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((resposta) => {
        this.documentos.set(resposta.content);
        this.total.set(resposta.totalElements);
        this.totalPaginas.set(resposta.totalPages);
        this.carregado.set(true);
        this.aCarregar.set(false);
      });

    this.controloCaixa.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.largarItemDeOutraCaixa();
      this.aplicarFiltros();
    });
    this.controloItem.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.aplicarFiltros());
    this.controloValidacao.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.aplicarFiltros());

    this.documentoService.estrutura(this.processoId()).subscribe({
      next: (estrutura) =>
        this.itens.set(
          estrutura.separadores
            .flatMap((separador) => [...separador.publicacao, ...separador.deposito])
            .map((item) => ({ documentoConfigId: item.documentoConfigId, nome: item.nome, categoria: item.categoria })),
        ),
      error: () => this.itens.set([]),
    });

    this.consulta.next();
  }

  tentarNovamente(): void {
    this.consulta.next();
  }

  irParaPagina(pagina: number): void {
    this.pagina.set(pagina);
    this.consulta.next();
  }

  limparFiltros(): void {
    for (const controlo of [this.controloCaixa, this.controloItem, this.controloValidacao]) {
      controlo.setValue(TODOS, { emitEvent: false });
    }
    this.aplicarFiltros();
  }

  /** Sem item na estrutura (ou sem estrutura) o nome não se conhece; sem `documentoConfigId` é do balde «Outros». */
  nomeItem(documento: DocumentoProcesso): string {
    if (documento.documentoConfigId === null) {
      return 'Outros';
    }
    return this.nomesItens().get(documento.documentoConfigId) ?? '—';
  }

  rotuloCaixa(categoria: CategoriaDocumento): string {
    return ROTULO_CAIXA[categoria];
  }

  validacao(estado: EstadoValidacao): { rotulo: string; tom: TomSelo } {
    return VALIDACAO[estado];
  }

  private aplicarFiltros(): void {
    const filtro: FiltroDocumentos = {};
    const caixa = valorDoFiltro(this.controloCaixa.value);
    const item = valorDoFiltro(this.controloItem.value);
    const estado = valorDoFiltro(this.controloValidacao.value);
    if (caixa) filtro.categoria = caixa as CategoriaDocumento;
    if (item) filtro.documentoConfigId = Number(item);
    if (estado) filtro.estadoValidacao = estado as EstadoValidacao;
    this.filtro.set(filtro);
    this.pagina.set(0);
    this.consulta.next();
  }

  private largarItemDeOutraCaixa(): void {
    const caixa = valorDoFiltro(this.controloCaixa.value);
    const item = valorDoFiltro(this.controloItem.value);
    if (!caixa || !item) {
      return;
    }
    const escolhido = this.itens().find((candidato) => String(candidato.documentoConfigId) === item);
    if (escolhido && escolhido.categoria !== caixa) {
      this.controloItem.setValue(TODOS, { emitEvent: false });
    }
  }
}
