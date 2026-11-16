import { DatePipe } from '@angular/common';
import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { AppShellComponent } from '../../../shared/components/app-shell.component';
import { PosicaoMenu, posicaoMenu } from '../posicao-menu';
import { ErrorBannerComponent } from '../../../shared/components/error-banner.component';
import { ConfirmacaoModalComponent } from '../../../shared/components/confirmacao-modal.component';
import { ContactoModalComponent } from '../../../shared/components/contacto-modal.component';
import { EntidadeModalComponent, ModoEntidadeModal } from '../../../shared/components/entidade-modal.component';
import { AuthService } from '../../../core/auth/auth.service';
import { ApiError } from '../../../core/models/error-response.model';
import {
  AtualizarContactoRequest,
  Contacto,
  Entidade,
  TIPOS_ENTIDADE,
  TipoEntidade,
  rotuloTipoEntidade,
} from '../../../models/entidade.model';
import { EntidadeService } from '../../../services/entidade.service';
import { AlertaComponent } from '../../../shared/ui/alerta/alerta.component';
import { BotaoComponent } from '../../../shared/ui/botao/botao.component';
import { CampoPesquisaComponent } from '../../../shared/ui/campo-pesquisa/campo-pesquisa.component';
import { CartaoComponent } from '../../../shared/ui/cartao/cartao.component';
import { ModalComponent } from '../../../shared/ui/modal/modal.component';
import { PaginacaoComponent } from '../../../shared/ui/paginacao/paginacao.component';
import { OpcaoSeletor, SeletorComponent } from '../../../shared/ui/seletor/seletor.component';
import { SeloComponent } from '../../../shared/ui/selo/selo.component';
import { CelulaTabelaDirective } from '../../../shared/ui/tabela/celula-tabela.directive';
import { ColunaTabela, TabelaComponent } from '../../../shared/ui/tabela/tabela.component';

const TAMANHO_PAGINA = 20;
const DURACAO_BANNER_ACAO_MS = 4000;

type FiltroEstado = 'todas' | 'ativas' | 'inativas';

let proximoId = 0;

/**
 * Gestão de entidades. "Contacto Institucional" mostra o `telefone` do contacto principal
 * (decisão de produto); a `descricao` não aparece mas o valor gravado mantém-se.
 */
@Component({
  selector: 'app-entidades',
  standalone: true,
  imports: [
    AppShellComponent,
    ErrorBannerComponent,
    ConfirmacaoModalComponent,
    ContactoModalComponent,
    EntidadeModalComponent,
    AlertaComponent,
    BotaoComponent,
    CampoPesquisaComponent,
    CartaoComponent,
    ModalComponent,
    PaginacaoComponent,
    SeletorComponent,
    SeloComponent,
    TabelaComponent,
    CelulaTabelaDirective,
    DatePipe,
    ReactiveFormsModule,
  ],
  templateUrl: './entidades.component.html',
  styleUrls: ['../backoffice.css', './entidades.component.css'],
  host: { '(document:click)': 'fecharMenu()', '(window:scroll)': 'fecharMenu()', '(window:resize)': 'fecharMenu()' },
})
export class EntidadesComponent implements OnInit, OnDestroy {
  private readonly entidadeService = inject(EntidadeService);
  private readonly authService = inject(AuthService);
  readonly tamanhoPagina = TAMANHO_PAGINA;
  readonly idExplicacaoContactos = `contactos-explicacao-${++proximoId}`;
  readonly opcoesEstado: { valor: FiltroEstado; rotulo: string }[] = [
    { valor: 'todas', rotulo: 'Todas' },
    { valor: 'ativas', rotulo: 'Ativas' },
    { valor: 'inativas', rotulo: 'Inativas' },
  ];
  readonly opcoesTipo: OpcaoSeletor[] = TIPOS_ENTIDADE.map((tipo) => ({ valor: tipo.valor, rotulo: tipo.rotulo }));
  readonly colunas: ColunaTabela[] = [
    { chave: 'nome', titulo: 'Entidade', largura: '28%' },
    { chave: 'contacto', titulo: 'Contacto Institucional', largura: '22%' },
    { chave: 'email', titulo: 'E-mail', largura: '20%' },
    { chave: 'atualizadoEm', titulo: 'Última atualização', largura: '14%' },
    { chave: 'estado', titulo: 'Estado', largura: '10%' },
    { chave: 'acoes', titulo: 'Ações', largura: '6%' },
  ];
  readonly colunasContactos: ColunaTabela[] = [
    { chave: 'email', titulo: 'E-mail', largura: '27%' },
    { chave: 'descricao', titulo: 'Descrição', largura: '18%' },
    { chave: 'telefone', titulo: 'Telefone', largura: '14%' },
    { chave: 'principal', titulo: 'Principal', largura: '12%' },
    { chave: 'estado', titulo: 'Estado', largura: '11%' },
    { chave: 'acoes', titulo: 'Ações', largura: '18%' },
  ];

  readonly filtroEstado = signal<FiltroEstado>('todas');
  readonly filtroTipo = signal<TipoEntidade | null>(null);
  readonly filtroTipoControlo = new FormControl<string | null>(null);
  readonly entidades = signal<Entidade[]>([]);
  readonly pagina = signal(0);
  readonly totalPaginas = signal(0);
  readonly totalElementos = signal(0);
  readonly loading = signal(true);
  /** Erro de carregar a listagem: esconde a tabela. */
  readonly erro = signal<ApiError | null>(null);
  /** Erro de uma ação: a tabela mantém-se visível e o banner desaparece sozinho. */
  readonly erroAcao = signal<ApiError | null>(null);
  readonly sucesso = signal<string | null>(null);
  readonly menuAbertoId = signal<number | null>(null);
  readonly menuPosicao = signal<PosicaoMenu>({ topo: 0, direita: 0 });

  readonly entidadeModalAberto = signal(false);
  readonly modoModal = signal<ModoEntidadeModal>('criar');
  readonly entidadeEmEdicao = signal<Entidade | null>(null);

  readonly entidadeAEliminar = signal<Entidade | null>(null);
  readonly aEliminar = signal(false);
  readonly mensagemEliminar = computed(
    () => `Eliminar a entidade ${this.descricao(this.entidadeAEliminar())}? Esta ação não pode ser desfeita.`,
  );

  readonly entidadeContactos = signal<Entidade | null>(null);
  readonly contactos = signal<Contacto[]>([]);
  readonly contactosACarregar = signal(false);
  readonly contactosErro = signal<ApiError | null>(null);
  readonly contactosErroAcao = signal<string | null>(null);
  readonly contactoModalAberto = signal(false);
  readonly contactoEmEdicao = signal<Contacto | null>(null);
  readonly contactoADesativar = signal<Contacto | null>(null);
  readonly aProcessarContacto = signal(false);

  private termoPesquisa = '';
  private temporizadorErroAcao: ReturnType<typeof setTimeout> | undefined;
  private temporizadorSucesso: ReturnType<typeof setTimeout> | undefined;

  private readonly ativoFiltro = computed<boolean | undefined>(() => {
    const estado = this.filtroEstado();
    if (estado === 'ativas') return true;
    if (estado === 'inativas') return false;
    return undefined;
  });

  constructor() {
    this.filtroTipoControlo.valueChanges.pipe(takeUntilDestroyed()).subscribe((tipo) => {
      this.filtroTipo.set((tipo as TipoEntidade | null) ?? null);
      this.pagina.set(0);
      this.carregar();
    });
  }

  ngOnInit(): void {
    this.carregar();
  }

  ngOnDestroy(): void {
    clearTimeout(this.temporizadorErroAcao);
    clearTimeout(this.temporizadorSucesso);
  }

  souAdmin(): boolean {
    return this.authService.perfilInterno() === 'MASTER';
  }

  descricao(entidade: Entidade | null): string {
    return entidade ? (entidade.descricaoSaigt ?? entidade.descricaoIncm) : '';
  }

  /** «{código} · {tipo}»; `null` quando falta algum dos dois. */
  linhaSecundaria(entidade: Entidade): string | null {
    const tipo = rotuloTipoEntidade(entidade.tipoEntidade);
    return entidade.codigo && tipo ? `${entidade.codigo} · ${tipo}` : null;
  }

  /** Chega já com a espera do `app-campo-pesquisa`: um pedido por pausa na escrita, não por tecla. */
  aoMudarPesquisa(valor: string): void {
    this.termoPesquisa = valor.trim();
    this.pagina.set(0);
    this.carregar();
  }

  mudarFiltroEstado(estado: FiltroEstado): void {
    this.filtroEstado.set(estado);
    this.pagina.set(0);
    this.carregar();
  }

  irParaPagina(pagina: number): void {
    this.pagina.set(pagina);
    this.carregar();
  }

  alternarMenu(entidadeId: number, evento: Event): void {
    evento.stopPropagation();
    this.menuPosicao.set(posicaoMenu(evento));
    this.menuAbertoId.set(this.menuAbertoId() === entidadeId ? null : entidadeId);
  }

  fecharMenu(): void {
    this.menuAbertoId.set(null);
  }

  /** O item do menu desaparece ao fechar; o «⋯» fica com o foco para o modal o devolver a ele. */
  private sairDoMenu(origem?: HTMLElement): void {
    this.menuAbertoId.set(null);
    origem?.focus();
  }

  abrirCriar(): void {
    this.modoModal.set('criar');
    this.entidadeEmEdicao.set(null);
    this.entidadeModalAberto.set(true);
  }

  abrirEditar(entidade: Entidade, origem?: HTMLElement): void {
    this.sairDoMenu(origem);
    this.modoModal.set('editar');
    this.entidadeEmEdicao.set(entidade);
    this.entidadeModalAberto.set(true);
  }

  fecharEntidadeModal(): void {
    this.entidadeModalAberto.set(false);
    this.entidadeEmEdicao.set(null);
  }

  aoGravarEntidade(): void {
    const mensagem = this.modoModal() === 'criar' ? 'Entidade criada.' : 'Entidade atualizada.';
    this.fecharEntidadeModal();
    this.mostrarSucesso(mensagem);
    this.carregar(true);
  }

  pedirEliminar(entidade: Entidade, origem?: HTMLElement): void {
    this.sairDoMenu(origem);
    this.entidadeAEliminar.set(entidade);
  }

  confirmarEliminar(): void {
    const entidade = this.entidadeAEliminar();
    if (!entidade || this.aEliminar()) {
      return;
    }
    this.aEliminar.set(true);
    this.entidadeService.eliminar(entidade.id).subscribe({
      next: () => {
        this.aEliminar.set(false);
        this.entidadeAEliminar.set(null);
        this.mostrarSucesso('Entidade eliminada.');
        this.carregar(true);
      },
      error: (erro: ApiError) => {
        this.aEliminar.set(false);
        this.entidadeAEliminar.set(null);
        this.mostrarErroAcao(erro);
      },
    });
  }

  abrirContactos(entidade: Entidade, origem?: HTMLElement): void {
    this.sairDoMenu(origem);
    this.entidadeContactos.set(entidade);
    this.contactos.set([]);
    this.contactosErroAcao.set(null);
    this.carregarContactos();
  }

  fecharContactos(): void {
    this.entidadeContactos.set(null);
    this.contactos.set([]);
    this.contactosErro.set(null);
    this.contactosErroAcao.set(null);
  }

  abrirCriarContacto(): void {
    this.contactoEmEdicao.set(null);
    this.contactosErroAcao.set(null);
    this.contactoModalAberto.set(true);
  }

  abrirEditarContacto(contacto: Contacto): void {
    this.contactoEmEdicao.set(contacto);
    this.contactosErroAcao.set(null);
    this.contactoModalAberto.set(true);
  }

  fecharContactoModal(): void {
    this.contactoModalAberto.set(false);
    this.contactoEmEdicao.set(null);
  }

  aoGravarContacto(): void {
    this.fecharContactoModal();
    this.aposAlterarContactos();
  }

  confirmarDesativar(): void {
    const contacto = this.contactoADesativar();
    if (contacto) {
      this.alterarContacto(contacto, { ativo: false }, () => this.contactoADesativar.set(null));
    }
  }

  /** Marcar ou retirar o principal e desativar ou reativar: um `PATCH` só com esse campo. O 409 fica no modal. */
  alterarContacto(contacto: Contacto, alteracao: AtualizarContactoRequest, aoTerminar: () => void = () => undefined): void {
    const entidade = this.entidadeContactos();
    if (!entidade || this.aProcessarContacto()) {
      return;
    }
    this.contactosErroAcao.set(null);
    this.aProcessarContacto.set(true);
    this.entidadeService.editarContacto(entidade.id, contacto.id, alteracao).subscribe({
      next: () => {
        this.aProcessarContacto.set(false);
        aoTerminar();
        this.aposAlterarContactos();
      },
      error: (erro: ApiError) => {
        this.aProcessarContacto.set(false);
        aoTerminar();
        this.contactosErroAcao.set(erro.mensagem);
      },
    });
  }

  /** O contacto principal também aparece na listagem das entidades. */
  private aposAlterarContactos(): void {
    this.carregarContactos();
    this.carregar(true);
  }

  private carregarContactos(): void {
    const entidade = this.entidadeContactos();
    if (!entidade) {
      return;
    }
    this.contactosACarregar.set(true);
    this.contactosErro.set(null);
    this.entidadeService.listarContactos(entidade.id).subscribe({
      next: (contactos) => {
        this.contactos.set(contactos);
        this.contactosACarregar.set(false);
      },
      error: (erro: ApiError) => {
        this.contactosErro.set(erro);
        this.contactosACarregar.set(false);
      },
    });
  }

  private mostrarSucesso(mensagem: string): void {
    clearTimeout(this.temporizadorSucesso);
    this.sucesso.set(mensagem);
    this.temporizadorSucesso = setTimeout(() => this.sucesso.set(null), DURACAO_BANNER_ACAO_MS);
  }

  private mostrarErroAcao(erro: ApiError): void {
    clearTimeout(this.temporizadorErroAcao);
    this.erroAcao.set(erro);
    this.temporizadorErroAcao = setTimeout(() => this.erroAcao.set(null), DURACAO_BANNER_ACAO_MS);
  }

  /**
   * Depois de uma ação, as linhas ficam até chegarem as novas (sem esqueleto): o «⋯» que abriu o modal
   * continua no DOM e recebe o foco de volta.
   */
  private carregar(manterLinhas = false): void {
    this.loading.set(!manterLinhas);
    this.erro.set(null);

    this.entidadeService
      .listar(
        { ativo: this.ativoFiltro(), nome: this.termoPesquisa || undefined, tipoEntidade: this.filtroTipo() ?? undefined },
        this.pagina(),
        TAMANHO_PAGINA,
      )
      .subscribe({
        next: (paginaResposta) => {
          this.entidades.set(paginaResposta.content);
          this.totalPaginas.set(paginaResposta.totalPages);
          this.totalElementos.set(paginaResposta.totalElements);
          this.loading.set(false);
        },
        error: (erro: ApiError) => {
          this.erro.set(erro);
          this.loading.set(false);
        },
      });
  }
}
