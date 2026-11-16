import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { forkJoin, of } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { ApiError } from '../../../core/models/error-response.model';
import { Catalogo } from '../../../models/catalogo.model';
import { ListaValoresItem, TIPO_PAI_LISTA_VALORES, TipoListaValores } from '../../../models/lista-valores.model';
import { CatalogoService } from '../../../services/catalogo.service';
import { ListaValoresService } from '../../../services/lista-valores.service';
import { AppShellComponent } from '../../../shared/components/app-shell.component';
import { ErrorBannerComponent } from '../../../shared/components/error-banner.component';
import { CartaoComponent } from '../../../shared/ui/cartao/cartao.component';
import { SeloComponent } from '../../../shared/ui/selo/selo.component';
import { ValorListaModalComponent } from './valor-lista-modal.component';

/** A partir daqui um cartão colapsa e mostra "Mostrar mais". */
const LIMITE_ITENS_COLAPSADO = 8;

export const MENSAGEM_LISTA_VAZIA = 'Sem valores. Enquanto a lista estiver vazia, o campo não aparece nos formulários.';

interface CartaoLista {
  tipo: TipoListaValores;
  /** Por `ordem`; os inativos só para o Master. */
  valores: ListaValoresItem[];
  rotuloPai: string | null;
}

interface EdicaoValor {
  tipo: TipoListaValores;
  valor: ListaValoresItem | null;
}

@Component({
  selector: 'app-lista-valores',
  standalone: true,
  imports: [AppShellComponent, ErrorBannerComponent, CartaoComponent, SeloComponent, ValorListaModalComponent],
  styleUrls: ['../backoffice.css', './lista-valores.component.css'],
  template: `
    <app-shell breadcrumb="Listas de Valores">
      <div class="conteudo">
        <div>
          <h1>Listas de Valores</h1>
          <p class="subtitulo">Listas de valores e configurações globais</p>
        </div>

        @if (loading()) {
          <p class="estado">A carregar…</p>
        } @else {
          <app-error-banner [erro]="erro()" />

          <div class="grelha-cartoes">
            @for (catalogo of catalogos(); track catalogo.titulo) {
              <app-cartao espacamento="nenhum" class="cartao">
                <div class="cabecalho-cartao">
                  <h2>{{ catalogo.titulo }}</h2>
                </div>
                <ul class="lista-itens">
                  @for (item of itensVisiveis(catalogo.titulo, catalogo.itens); track item) {
                    <li class="item">{{ item }}</li>
                  } @empty {
                    <li class="item vazio">Sem itens.</li>
                  }
                </ul>
                @if (catalogo.itens.length > limiteItensColapsado) {
                  <button
                    type="button"
                    class="botao-expandir"
                    [attr.aria-expanded]="estaExpandido(catalogo.titulo)"
                    (click)="alternarExpansao(catalogo.titulo)"
                  >
                    {{ rotuloExpansao(catalogo.titulo, catalogo.itens.length) }}
                  </button>
                }
              </app-cartao>
            }
          </div>

          @if (cartoesListas().length > 0) {
            <section class="zona-listas" aria-labelledby="titulo-listas-valores">
              <div>
                <h2 id="titulo-listas-valores" class="titulo-zona">Listas de valores</h2>
                <p class="subtitulo">Valores oferecidos nos formulários. Um campo cuja lista está vazia não aparece.</p>
              </div>
              <app-error-banner [erro]="erroListas()" />
              @if (!valoresCarregados()) {
                @if (!erroListas()) {
                  <p class="estado">A carregar…</p>
                }
              } @else {
                <div class="grelha-cartoes">
                  @for (cartao of cartoesListas(); track cartao.tipo.tipo) {
                    <app-cartao espacamento="nenhum" class="cartao cartao-lista" [attr.data-tipo]="cartao.tipo.tipo">
                      <div class="cabecalho-cartao">
                        <h3>{{ cartao.tipo.descricao }}</h3>
                        @if (souMaster()) {
                          <button
                            type="button"
                            class="botao-acrescentar"
                            [attr.aria-label]="'Acrescentar valor a ' + cartao.tipo.descricao"
                            (click)="abrirCriacao(cartao.tipo)"
                          >
                            <svg viewBox="0 0 12.25 12.25" fill="none" stroke="currentColor" stroke-width="1.02" stroke-linecap="round" aria-hidden="true">
                              <path d="M2.55 6.125h7.15M6.125 2.55v7.15" />
                            </svg>
                          </button>
                        }
                      </div>
                      <ul class="lista-itens">
                        @for (valor of itensVisiveis(cartao.tipo.tipo, cartao.valores); track valor.id) {
                          <li class="item item-valor" [class.item-inativo]="!valor.ativo">
                            <span class="texto-valor">
                              <span>{{ valor.descricao }}</span>
                              @if (cartao.rotuloPai) {
                                <span class="pai-valor">{{ cartao.rotuloPai }}: {{ descricaoPai(valor) }}</span>
                              }
                            </span>
                            <span class="acoes-valor">
                              @if (!valor.ativo) {
                                <app-selo tom="cinza" tamanho="pequeno">Inativo</app-selo>
                              }
                              @if (souMaster()) {
                                <button
                                  type="button"
                                  class="botao-editar"
                                  [attr.aria-label]="'Editar ' + valor.descricao"
                                  (click)="abrirEdicao(cartao.tipo, valor)"
                                >
                                  Editar
                                </button>
                              }
                            </span>
                          </li>
                        } @empty {
                          <li class="item vazio">{{ mensagemListaVazia }}</li>
                        }
                      </ul>
                      @if (cartao.valores.length > limiteItensColapsado) {
                        <button
                          type="button"
                          class="botao-expandir"
                          [attr.aria-expanded]="estaExpandido(cartao.tipo.tipo)"
                          (click)="alternarExpansao(cartao.tipo.tipo)"
                        >
                          {{ rotuloExpansao(cartao.tipo.tipo, cartao.valores.length) }}
                        </button>
                      }
                    </app-cartao>
                  }
                </div>
              }
            </section>
          }
        }
      </div>
    </app-shell>

    @if (edicao(); as e) {
      <app-valor-lista-modal
        [aberto]="true"
        [tipo]="e.tipo"
        [valor]="e.valor"
        [rotuloPai]="rotuloPaiDe(e.tipo.tipo)"
        [valoresPai]="valoresPaiDe(e.tipo.tipo)"
        [ordemSugerida]="ordemSugerida(e.tipo.tipo)"
        [ultimoAtivo]="eUltimoAtivo(e.valor)"
        (fechar)="edicao.set(null)"
        (gravado)="aoGravar($event)"
      />
    }
  `,
})
export class ListaValoresComponent implements OnInit {
  private readonly catalogoService = inject(CatalogoService);
  private readonly listaValoresService = inject(ListaValoresService);
  private readonly authService = inject(AuthService);

  readonly catalogos = signal<Catalogo[]>([]);
  readonly tipos = signal<TipoListaValores[]>([]);
  /** Todos os valores, ativos e inativos, das listas geridas. */
  readonly valores = signal<ListaValoresItem[]>([]);
  readonly loading = signal(true);
  readonly erro = signal<ApiError | null>(null);
  readonly erroListas = signal<ApiError | null>(null);
  readonly valoresCarregados = signal(false);
  readonly edicao = signal<EdicaoValor | null>(null);
  private readonly expandidos = signal<ReadonlySet<string>>(new Set());

  readonly limiteItensColapsado = LIMITE_ITENS_COLAPSADO;
  readonly mensagemListaVazia = MENSAGEM_LISTA_VAZIA;

  readonly souMaster = computed(() => this.authService.perfilInterno() === 'MASTER');

  readonly cartoesListas = computed<CartaoLista[]>(() => {
    const master = this.souMaster();
    const rotulos = new Map(this.tipos().map((tipo) => [tipo.tipo, tipo.descricao]));
    return [...this.tipos()]
      .sort((a, b) => a.descricao.localeCompare(b.descricao, 'pt'))
      .map((tipo) => {
        const tipoPai = TIPO_PAI_LISTA_VALORES[tipo.tipo];
        return {
          tipo,
          valores: this.valoresDoTipo(tipo.tipo).filter((valor) => master || valor.ativo),
          rotuloPai: tipoPai ? (rotulos.get(tipoPai) ?? null) : null,
        };
      });
  });

  ngOnInit(): void {
    this.loading.set(true);
    this.erro.set(null);
    // Os catálogos globais são só do Master na API: o Técnico vê apenas as listas de valores.
    const catalogos = this.souMaster() ? this.catalogoService.listar() : of([] as Catalogo[]);
    forkJoin({ catalogos, tipos: this.listaValoresService.tipos() }).subscribe({
      next: ({ catalogos, tipos }) => {
        this.catalogos.set(catalogos);
        this.tipos.set(tipos);
        this.loading.set(false);
        this.carregarValores();
      },
      error: (erro: ApiError) => {
        this.erro.set(erro);
        this.loading.set(false);
      },
    });
  }

  itensVisiveis<T>(chave: string, itens: T[]): T[] {
    if (itens.length <= LIMITE_ITENS_COLAPSADO || this.estaExpandido(chave)) {
      return itens;
    }
    return itens.slice(0, LIMITE_ITENS_COLAPSADO);
  }

  rotuloExpansao(chave: string, total: number): string {
    return this.estaExpandido(chave) ? 'Mostrar menos' : `Mostrar mais (${total - LIMITE_ITENS_COLAPSADO})`;
  }

  estaExpandido(chave: string): boolean {
    return this.expandidos().has(chave);
  }

  alternarExpansao(chave: string): void {
    const atual = new Set(this.expandidos());
    if (atual.has(chave)) {
      atual.delete(chave);
    } else {
      atual.add(chave);
    }
    this.expandidos.set(atual);
  }

  descricaoPai(valor: ListaValoresItem): string {
    return this.valores().find((pai) => pai.id === valor.paiId)?.descricao ?? '—';
  }

  rotuloPaiDe(tipo: string): string | null {
    const tipoPai = TIPO_PAI_LISTA_VALORES[tipo];
    return tipoPai ? (this.tipos().find((t) => t.tipo === tipoPai)?.descricao ?? tipoPai) : null;
  }

  valoresPaiDe(tipo: string): ListaValoresItem[] {
    const tipoPai = TIPO_PAI_LISTA_VALORES[tipo];
    return tipoPai ? this.valoresDoTipo(tipoPai) : [];
  }

  ordemSugerida(tipo: string): number {
    return Math.max(0, ...this.valoresDoTipo(tipo).map((valor) => valor.ordem)) + 1;
  }

  eUltimoAtivo(valor: ListaValoresItem | null): boolean {
    if (!valor?.ativo) {
      return false;
    }
    return !this.valoresDoTipo(valor.tipo).some((outro) => outro.ativo && outro.id !== valor.id);
  }

  abrirCriacao(tipo: TipoListaValores): void {
    this.edicao.set({ tipo, valor: null });
  }

  abrirEdicao(tipo: TipoListaValores, valor: ListaValoresItem): void {
    this.edicao.set({ tipo, valor });
  }

  aoGravar(gravado: ListaValoresItem): void {
    this.valores.update((valores) => {
      const outros = valores.filter((valor) => valor.id !== gravado.id);
      return [...outros, gravado];
    });
    this.edicao.set(null);
  }

  private valoresDoTipo(tipo: string): ListaValoresItem[] {
    return this.valores()
      .filter((valor) => valor.tipo === tipo)
      .sort((a, b) => a.ordem - b.ordem || a.descricao.localeCompare(b.descricao, 'pt'));
  }

  private carregarValores(): void {
    this.erroListas.set(null);
    this.valoresCarregados.set(false);
    this.listaValoresService.listar().subscribe({
      next: (valores) => {
        this.valores.set(valores);
        this.valoresCarregados.set(true);
      },
      error: (erro: ApiError) => this.erroListas.set(erro),
    });
  }
}
