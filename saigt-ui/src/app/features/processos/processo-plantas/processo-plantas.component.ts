import { NgTemplateOutlet } from '@angular/common';
import { Component, Input, OnInit, computed, inject, signal } from '@angular/core';
import { Observable, catchError, map, of } from 'rxjs';
import { ApiError } from '../../../core/models/error-response.model';
import { ItemPlantas, LinhaPlanta, Plantas } from '../../../models/plantas.model';
import { DocumentoService } from '../../../services/documento.service';
import { PlantasService } from '../../../services/plantas.service';

const FORMATO_TXT = [
  'Ordenamento; Planta de Ordenamento – Uso do Solo; A',
  'Ordenamento; Planta de Ordenamento – Uso do Solo; B',
  'Condicionantes; Planta de Condicionantes – RAN; A',
  'Condicionantes; Planta de Condicionantes – RAN; B',
];
const CODIGO_TXT = 'PLANTAS_TXT_NOMES_DEP';
const CODIGO_CARTOGRAMA = 'PLANTAS_CARTOGRAMA_DEP';

/**
 * Página das Plantas de um processo sem GeoConform (EF v1.5 §3.1.2; Figma «5.1.Preencher Plantas»): ZIP de cada
 * planta, TXT dos nomes, associação de cada linha a um TIFF e cartograma. Os ficheiros gravam-se logo; a associação
 * grava-se com `gravar()`, que o assistente chama ao mudar de página.
 */
@Component({
  selector: 'app-processo-plantas',
  imports: [NgTemplateOutlet],
  standalone: true,
  styleUrl: './processo-plantas.component.css',
  template: `
    <section class="cartao" aria-labelledby="titulo-plantas">
      <h2 class="cabecalho" id="titulo-plantas">Plantas</h2>
      <div class="corpo">
        @if (aCarregar()) {
          <p class="estado">A carregar…</p>
        } @else if (plantas(); as p) {
          @if (pendenciasSubmissao().length > 0) {
            <ul class="pendencias" role="alert">
              @for (pendencia of pendenciasSubmissao(); track pendencia) {
                <li>{{ pendencia }}</li>
              }
            </ul>
          }
          <ol class="passos">
            <li class="passo" [class.passo-concluido]="zipsCarregados()">
              <span class="marcador" aria-hidden="true">
                @if (zipsCarregados()) { <svg viewBox="0 0 16 16"><path d="M4 8.5l2.5 2.5L12 5.5" /></svg> } @else { 1 }
              </span>
              <div class="conteudo">
                <h3 class="titulo-passo">Carregar ficheiro ZIP com as plantas</h3>
                <p class="subtitulo">Formato: geotiff ou tif+tfw</p>
                <div class="grelha-zips">
                  @for (item of zips(); track item.documentoConfigId) {
                    <div class="zip">
                      <span class="rotulo-zip" [id]="'rotulo-' + item.codigo">{{ item.nome }}</span>
                      <ng-container *ngTemplateOutlet="zona; context: { $implicit: item, dica: 'geotiff ou tif+tfw', vazio: 'ZIP Plantas' }" />
                    </div>
                  }
                </div>
              </div>
            </li>

            <li class="passo" [class.passo-concluido]="txtCarregado()">
              <span class="marcador" aria-hidden="true">
                @if (txtCarregado()) { <svg viewBox="0 0 16 16"><path d="M4 8.5l2.5 2.5L12 5.5" /></svg> } @else { 2 }
              </span>
              <div class="conteudo">
                <h3 class="titulo-passo">Carregar ficheiro TXT com nome das plantas</h3>
                <p class="subtitulo">De acordo com o regulamento</p>
                <div class="formato">
                  <span class="rotulo-formato">Exemplo do formato</span>
                  @for (linha of formatoTxt; track linha) {
                    <code>{{ linha }}</code>
                  }
                </div>
                @if (itemTxt(); as item) {
                  <ng-container *ngTemplateOutlet="zona; context: { $implicit: item, dica: 'Tipo; Nome; Folha', vazio: 'TXT com os nomes' }" />
                }
                @for (erro of p.errosTxt; track erro) {
                  <p class="erro" role="alert">{{ erro }}</p>
                }
              </div>
            </li>

            <li class="passo" [class.passo-concluido]="todasAssociadas()">
              <span class="marcador" aria-hidden="true">
                @if (todasAssociadas()) { <svg viewBox="0 0 16 16"><path d="M4 8.5l2.5 2.5L12 5.5" /></svg> } @else { 3 }
              </span>
              <div class="conteudo">
                <h3 class="titulo-passo">Associar plantas ao nome Tiff</h3>
                <p class="subtitulo">Selecione o ficheiro Tiff por planta</p>
                @if (p.linhas.length === 0) {
                  <p class="estado">Carregue o TXT com os nomes para associar as plantas.</p>
                } @else {
                  <table class="tabela">
                    <thead>
                      <tr>
                        <th scope="col">Nome planta</th>
                        <th scope="col" class="coluna-folha">N.º folha</th>
                        <th scope="col">Nome Tiff</th>
                      </tr>
                    </thead>
                    <tbody>
                      @for (linha of p.linhas; track linha.ordem) {
                        <tr [class.linha-associada]="!!escolhas()[linha.ordem]">
                          <td>{{ linha.nome }}</td>
                          <td class="coluna-folha">
                            @if (linha.folha) { <span class="folha">{{ linha.folha }}</span> }
                          </td>
                          <td>
                            <select
                              class="seletor-tiff"
                              [class.seletor-escolhido]="!!escolhas()[linha.ordem]"
                              [attr.aria-label]="'Ficheiro Tiff de ' + linha.nome + (linha.folha ? ', folha ' + linha.folha : '')"
                              [disabled]="!editavel || aGravar()"
                              (change)="escolher(linha, $event)"
                            >
                              <option value="" [selected]="!escolhas()[linha.ordem]">— {{ linha.tipo }} —</option>
                              @for (tiff of opcoesDe(linha); track tiff) {
                                <option [value]="tiff" [selected]="escolhas()[linha.ordem] === tiff">{{ tiff }}</option>
                              }
                            </select>
                          </td>
                        </tr>
                      }
                    </tbody>
                  </table>
                }
                @if (erroGravacao(); as mensagem) {
                  <p class="erro" role="alert">{{ mensagem }}</p>
                }
              </div>
            </li>

            @if (itemCartograma(); as item) {
              <li class="passo" [class.passo-concluido]="!!item.ficheiro">
                <span class="marcador" aria-hidden="true">
                  @if (item.ficheiro) { <svg viewBox="0 0 16 16"><path d="M4 8.5l2.5 2.5L12 5.5" /></svg> } @else { 4 }
                </span>
                <div class="conteudo">
                  <h3 class="titulo-passo">Cartograma das folhas</h3>
                  <p class="subtitulo">
                    {{ p.exigeCartograma ? 'Obrigatório: há plantas com mais de uma folha' : 'Obrigatório quando uma planta tem mais de uma folha' }}
                  </p>
                  <ng-container *ngTemplateOutlet="zona; context: { $implicit: item, dica: item.extensoesAceites.join(', '), vazio: 'Cartograma' }" />
                  @if (pdfPossivel()) {
                    <button type="button" class="ver-pdf" [disabled]="aAbrirPdf() || aGravar()" (click)="verPdf()">
                      {{ aAbrirPdf() ? 'A gerar o PDF…' : 'Ver PDF do cartograma' }}
                      <span class="visualmente-oculto">(abre num separador novo)</span>
                    </button>
                  }
                  @if (erroPdf(); as mensagem) {
                    <p class="erro" role="alert">{{ mensagem }}</p>
                  }
                </div>
              </li>
            }
          </ol>
        } @else if (erroLeitura()) {
          <p class="erro" role="alert">{{ erroLeitura() }}</p>
        }
      </div>
    </section>

    <ng-template #zona let-item let-dica="dica" let-vazio="vazio">
      @if (item.ficheiro; as ficheiro) {
        <div class="zona zona-carregada">
          <span class="icone-ok" aria-hidden="true"><svg viewBox="0 0 16 16"><path d="M4 8.5l2.5 2.5L12 5.5" /></svg></span>
          <span class="nome-ficheiro">{{ ficheiro.nomeFicheiro }}</span>
          @if (editavel) {
            <button type="button" class="remover" [disabled]="emOperacao() !== null" (click)="remover(item)">
              {{ emOperacao() === item.codigo ? 'A remover…' : 'Remover' }}
            </button>
          }
        </div>
      } @else {
        <label class="zona" [class.zona-desativada]="!editavel">
          <input
            class="ficheiro-input"
            type="file"
            [accept]="aceites(item)"
            [disabled]="!editavel || emOperacao() !== null"
            [attr.aria-label]="item.nome"
            [attr.aria-describedby]="errosItem()[item.codigo] ? 'erro-' + item.codigo : null"
            (change)="carregar(item, $event)"
          />
          <span class="icone-carregar" aria-hidden="true">
            <svg viewBox="0 0 16 16"><path d="M2.67 10.67v.66a2 2 0 0 0 2 2h6.66a2 2 0 0 0 2-2v-.66M5.33 5.33 8 2.67l2.67 2.66M8 2.67v8" /></svg>
          </span>
          <span class="zona-texto">{{ emOperacao() === item.codigo ? 'A carregar…' : vazio }}</span>
          <span class="zona-dica">{{ dica }}</span>
        </label>
      }
      @if (errosItem()[item.codigo]; as mensagem) {
        <p class="erro" role="alert" [id]="'erro-' + item.codigo">{{ mensagem }}</p>
      }
    </ng-template>
  `,

})
export class ProcessoPlantasComponent implements OnInit {
  private readonly plantasService = inject(PlantasService);
  private readonly documentoService = inject(DocumentoService);

  @Input({ required: true }) processoId!: string;
  @Input() editavel = true;
  /** Recusa da submissão (`plantas.…`): o que falta mostra-se no topo da página. */
  @Input() set errosSubmissao(campos: { path: string; erro: string }[] | null) {
    this.pendenciasSubmissao.set((campos ?? []).filter((campo) => campo.path.startsWith('plantas.')).map((campo) => campo.erro));
  }

  readonly pendenciasSubmissao = signal<string[]>([]);

  readonly formatoTxt = FORMATO_TXT;
  readonly plantas = signal<Plantas | null>(null);
  readonly aCarregar = signal(true);
  readonly aGravar = signal(false);
  readonly erroLeitura = signal<string | null>(null);
  readonly erroGravacao = signal<string | null>(null);
  readonly emOperacao = signal<string | null>(null);
  readonly aAbrirPdf = signal(false);
  readonly erroPdf = signal<string | null>(null);
  readonly errosItem = signal<Record<string, string>>({});
  /** O TIFF escolhido por linha do TXT (`ordem`), gravado ou ainda por gravar. */
  readonly escolhas = signal<Record<number, string>>({});

  readonly zips = computed(() =>
    (this.plantas()?.itens ?? []).filter((item) => item.codigo !== CODIGO_TXT && item.codigo !== CODIGO_CARTOGRAMA),
  );
  readonly itemTxt = computed(() => this.plantas()?.itens.find((item) => item.codigo === CODIGO_TXT) ?? null);
  readonly itemCartograma = computed(() => this.plantas()?.itens.find((item) => item.codigo === CODIGO_CARTOGRAMA) ?? null);
  readonly zipsCarregados = computed(() => this.zips().length > 0 && this.zips().every((item) => !!item.ficheiro));
  readonly txtCarregado = computed(() => !!this.itemTxt()?.ficheiro && (this.plantas()?.errosTxt.length ?? 0) === 0);
  readonly todasAssociadas = computed(() => {
    const linhas = this.plantas()?.linhas ?? [];
    return linhas.length > 0 && linhas.every((linha) => !!this.escolhas()[linha.ordem]);
  });
  /** Com as escolhas ainda por gravar também se mostra: `verPdf()` grava-as antes de pedir o PDF. */
  readonly pdfPossivel = computed(() => !!this.itemCartograma()?.ficheiro && this.todasAssociadas());

  ngOnInit(): void {
    this.recarregar();
  }

  recarregar(): void {
    this.aCarregar.set(this.plantas() === null);
    this.plantasService.obterPlantas(this.processoId).subscribe({
      next: (plantas) => this.aplicar(plantas, true),
      error: (erro: ApiError) => {
        this.aCarregar.set(false);
        this.erroLeitura.set(erro.mensagem);
      },
    });
  }

  /** As opções vão desaparecendo à medida que se escolhem (EF v1.5 §3.1.2); a da própria linha fica. */
  opcoesDe(linha: LinhaPlanta): string[] {
    const escolhas = this.escolhas();
    const usadas = new Set(
      Object.entries(escolhas)
        .filter(([ordem]) => Number(ordem) !== linha.ordem)
        .map(([, tiff]) => tiff),
    );
    return linha.opcoes.filter((tiff) => !usadas.has(tiff));
  }

  escolher(linha: LinhaPlanta, evento: Event): void {
    const valor = (evento.target as HTMLSelectElement).value;
    this.escolhas.update((atual) => {
      const novas = { ...atual };
      if (valor) {
        novas[linha.ordem] = valor;
      } else {
        delete novas[linha.ordem];
      }
      return novas;
    });
  }

  aceites(item: ItemPlantas): string {
    return item.extensoesAceites.map((extensao) => '.' + extensao).join(',');
  }

  carregar(item: ItemPlantas, evento: Event): void {
    const input = evento.target as HTMLInputElement;
    const ficheiro = input.files?.[0];
    input.value = '';
    if (!ficheiro) {
      return;
    }
    this.limparErro(item.codigo);
    this.emOperacao.set(item.codigo);
    this.documentoService.carregar(this.processoId, ficheiro, item.categoria, item.documentoConfigId).subscribe({
      next: () => {
        this.emOperacao.set(null);
        this.recarregar();
      },
      error: (erro: ApiError) => {
        this.emOperacao.set(null);
        this.errosItem.update((erros) => ({ ...erros, [item.codigo]: erro.campos?.[0]?.erro ?? erro.mensagem }));
      },
    });
  }

  remover(item: ItemPlantas): void {
    if (!item.ficheiro) {
      return;
    }
    this.limparErro(item.codigo);
    this.emOperacao.set(item.codigo);
    this.documentoService.remover(this.processoId, item.ficheiro.id).subscribe({
      next: () => {
        this.emOperacao.set(null);
        this.recarregar();
      },
      error: (erro: ApiError) => {
        this.emOperacao.set(null);
        this.errosItem.update((erros) => ({ ...erros, [item.codigo]: erro.mensagem }));
      },
    });
  }

  /**
   * Abre o PDF num separador novo. O separador abre-se já, no clique, para o browser não o bloquear; o PDF vem com
   * a autenticação do pedido e entra depois como `blob:`.
   */
  verPdf(): void {
    const separador = window.open('', '_blank');
    this.erroPdf.set(null);
    this.aAbrirPdf.set(true);
    const falhou = (mensagem: string): void => {
      separador?.close();
      this.aAbrirPdf.set(false);
      this.erroPdf.set(mensagem);
    };
    this.gravar().subscribe((gravou) => {
      if (!gravou) {
        falhou('Corrija a associação das plantas antes de ver o PDF.');
        return;
      }
      this.plantasService.cartogramaPdf(this.processoId).subscribe({
        next: (pdf) => {
          this.aAbrirPdf.set(false);
          const endereco = URL.createObjectURL(pdf);
          if (separador) {
            separador.location.href = endereco;
          } else {
            window.location.assign(endereco);
          }
          setTimeout(() => URL.revokeObjectURL(endereco), 60_000);
        },
        error: (erro: ApiError) => falhou(erro.campos?.[0]?.erro ?? erro.mensagem),
      });
    });
  }

  /** `true` quando gravou (ou não havia nada para gravar); um erro fica junto da tabela. */
  gravar(): Observable<boolean> {
    const plantas = this.plantas();
    if (!plantas || !this.editavel || plantas.linhas.length === 0) {
      return of(true);
    }
    const associacoes = plantas.linhas
      .filter((linha) => !!this.escolhas()[linha.ordem])
      .map((linha) => ({ ordem: linha.ordem, nomeTiff: this.escolhas()[linha.ordem] }));
    this.erroGravacao.set(null);
    this.aGravar.set(true);
    return this.plantasService.gravarPlantas(this.processoId, { associacoes }).pipe(
      map((gravadas) => {
        this.aGravar.set(false);
        this.aplicar(gravadas, false);
        return true;
      }),
      catchError((erro: ApiError) => {
        this.aGravar.set(false);
        this.erroGravacao.set(erro.campos?.[0]?.erro ?? erro.mensagem);
        return of(false);
      }),
    );
  }

  /** Ao reler, guarda as escolhas por gravar que continuam válidas: só o servidor sabe os TIFF de cada ZIP. */
  private aplicar(plantas: Plantas, manterPorGravar: boolean): void {
    const anteriores = this.escolhas();
    const escolhas: Record<number, string> = {};
    for (const linha of plantas.linhas) {
      const porGravar = manterPorGravar ? anteriores[linha.ordem] : undefined;
      const escolha = porGravar && linha.opcoes.includes(porGravar) ? porGravar : linha.nomeTiff;
      if (escolha) {
        escolhas[linha.ordem] = escolha;
      }
    }
    this.plantas.set(plantas);
    this.escolhas.set(escolhas);
    this.aCarregar.set(false);
    this.erroLeitura.set(null);
  }

  private limparErro(codigo: string): void {
    this.errosItem.update((erros) => {
      const novos = { ...erros };
      delete novos[codigo];
      return novos;
    });
  }
}
