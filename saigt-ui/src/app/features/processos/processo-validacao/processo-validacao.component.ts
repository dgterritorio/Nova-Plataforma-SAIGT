import { DatePipe, NgTemplateOutlet } from '@angular/common';
import { Component, EventEmitter, Input, Output, computed, inject, signal, viewChild } from '@angular/core';
import { forkJoin } from 'rxjs';
import { ApiError } from '../../../core/models/error-response.model';
import { DocumentoProcesso } from '../../../models/documento.model';
import { EstadoValidacao, ProcessoDetalhe, TipoItemValidacao, ValidacaoItensResponse } from '../../../models/processo.model';
import { DescargaService } from '../../../services/descarga.service';
import { DocumentoService } from '../../../services/documento.service';
import { PlantasService } from '../../../services/plantas.service';
import { ProcessoService } from '../../../services/processo.service';
import { ErrorBannerComponent } from '../../../shared/components/error-banner.component';
import { BotaoComponent } from '../../../shared/ui/botao/botao.component';
import { informacaoBaseDe } from '../informacao-base';
import {
  ContextoAcaoItem,
  ProcessoDocumentosComponent,
  ROTULO_POR_ELEMENTO,
} from '../processo-documentos/processo-documentos.component';
import { ItemARejeitar, RejeitarItemModalComponent } from '../rejeitar-item-modal/rejeitar-item-modal.component';

const CODIGOS_DE_CONFLITO = ['VERSAO_DESATUALIZADA', 'ESTADO_INVALIDO'];

export type ResumoSeguinte = 'REJEICAO' | 'VALIDADOS';

interface ItemValidacaoEcra {
  tipo: TipoItemValidacao;
  itemId: string;
  estado: EstadoValidacao;
}

/** Ficheiro da página das Plantas: fica fora dos separadores da estrutura documental, mas a DGT valida-o como os outros. */
interface FicheiroPlantas {
  nomeItem: string;
  documento: DocumentoProcesso;
}

/** O máximo do `GET …/documentos`; os ficheiros das Plantas vêm desta página, cruzados pelo id. */
const TAMANHO_PAGINA_DOCUMENTOS = 100;

/**
 * Documentos e Informação Base do processo em leitura, com «Validar» e «Rejeitar» por item para quem tem `validar`
 * em `acoesDisponiveis`. Cada decisão grava logo esse item: não há marcas pendentes nem «Guardar validação».
 * O `PUT` não incrementa a `versao`, por isso os itens seguintes reutilizam a do detalhe.
 * Um item já decidido mostra só a decisão: o Figma não desenha a mudança de uma decisão gravada.
 * Num processo devolvido pela PCM (`revalidacaoPcm`), os documentos por validar (os que a PCM acrescentou) só têm
 * «Validar» e os restantes ficam «Validado», só de leitura; o estado do processo não muda (TR-114).
 */
@Component({
  selector: 'app-processo-validacao',
  standalone: true,
  imports: [BotaoComponent, DatePipe, ErrorBannerComponent, NgTemplateOutlet, ProcessoDocumentosComponent, RejeitarItemModalComponent],
  styles: [
    `
      .resumo {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        gap: 6px 16px;
        padding: 10px 14px;
        margin-bottom: 12px;
        border: 1px solid #e2e8f0;
        border-radius: 8px;
        background: #f8fafc;
        font-size: 13px;
        font-weight: 600;
        color: #334155;
      }

      .resumo-pronto {
        font-weight: 500;
        color: #166534;
      }

      .recarregar {
        margin: 8px 0 12px;
        padding: 8px 16px;
        border: 1px solid #e2e8f0;
        border-radius: 8px;
        background: #fff;
        color: #334155;
        font-size: 12px;
        font-weight: 600;
        cursor: pointer;
      }

      .validado {
        font-size: 12px;
        font-weight: 600;
        color: #166534;
      }

      /* «Validar» e «Rejeitar» como texto verde e vermelho (UI-034), não como botões cheios. */
      .acoes-item {
        display: inline-flex;
        align-items: center;
        gap: var(--espaco-1);
      }

      .validar,
      .rejeitar {
        padding: var(--espaco-0-5) var(--espaco-2);
        border: 0;
        border-radius: var(--raio-sm);
        background: none;
        font: inherit;
        font-size: var(--texto-sm);
        font-weight: var(--peso-medio);
        line-height: var(--texto-sm-linha);
        cursor: pointer;
      }

      .validar {
        color: var(--cor-sucesso);
      }

      .rejeitar {
        color: var(--cor-erro);
      }

      .validar:hover:not(:disabled),
      .rejeitar:hover:not(:disabled) {
        text-decoration: underline;
      }

      .validar:focus-visible,
      .rejeitar:focus-visible {
        outline: var(--anel-foco);
        outline-offset: var(--anel-foco-afastamento);
      }

      .validar:disabled,
      .rejeitar:disabled {
        opacity: 0.6;
        cursor: not-allowed;
      }

      /* UI-036. O texto usa um vermelho mais escuro que o do Figma, que não chega a 4,5:1 sobre o fundo do selo. */
      .seguinte {
        display: flex;
        justify-content: flex-end;
        margin-top: var(--espaco-4);
      }

      .rejeitado {
        display: inline-flex;
        align-items: center;
        gap: var(--espaco-0-5);
        padding: 3px 9px;
        border: var(--borda-largura) solid var(--cor-erro);
        border-radius: var(--raio-sm);
        background: var(--cor-icone-erro-fundo);
        font-size: var(--texto-sm);
        font-weight: var(--peso-medio);
        line-height: var(--texto-sm-linha);
        color: var(--cor-selo-erro);
      }

      .plantas {
        margin-top: var(--espaco-4);
      }

      .plantas h2 {
        margin: 0 0 var(--espaco-2-5);
        font-size: var(--texto-base);
        font-weight: var(--peso-semi);
        line-height: var(--texto-base-linha);
        color: var(--cor-texto);
      }

      .plantas-ficheiros {
        display: grid;
        gap: var(--espaco-2);
        margin: 0;
        padding: 0;
        list-style: none;
      }

      .plantas-ficheiro {
        display: flex;
        flex-wrap: wrap;
        justify-content: space-between;
        gap: var(--espaco-2);
        padding: var(--espaco-2) var(--espaco-2-5);
        border: var(--borda-largura) solid var(--cor-borda);
        border-radius: var(--raio-md);
        background: var(--cor-superficie);
      }

      .plantas-ficheiro.rejeitado-linha {
        border-color: var(--cor-erro);
        background: var(--cor-erro-fundo);
      }

      .plantas-info {
        display: flex;
        flex-direction: column;
        gap: var(--espaco-0-5);
        min-width: 0;
      }

      .plantas-item {
        font-size: var(--texto-xs);
        line-height: var(--texto-xs-linha);
        color: var(--cor-texto-secundario);
      }

      .plantas-nome {
        font-size: var(--texto-sm);
        font-weight: var(--peso-semi);
        line-height: var(--texto-sm-linha);
        color: var(--cor-texto);
        word-break: break-word;
      }

      .plantas-meta {
        font-size: var(--texto-xs);
        line-height: var(--texto-xs-linha);
        color: var(--cor-texto-secundario);
      }

      .plantas-acoes {
        display: flex;
        align-items: center;
        gap: var(--espaco-1-5);
      }

      .descarregar {
        padding: var(--espaco-1) var(--espaco-2-5);
        border: var(--borda-largura) solid var(--cor-borda);
        border-radius: var(--raio-sm);
        background: var(--cor-superficie);
        font: inherit;
        font-size: var(--texto-xs);
        font-weight: var(--peso-semi);
        line-height: var(--texto-xs-linha);
        color: var(--cor-texto);
        cursor: pointer;
      }

      .descarregar:focus-visible {
        outline: var(--anel-foco);
        outline-offset: var(--anel-foco-afastamento);
      }

      .descarregar:disabled {
        opacity: 0.6;
        cursor: not-allowed;
      }
    `,
  ],
  template: `
    @if (podeValidar()) {
      <div class="resumo" role="status">
        <span>{{ validados() }} de {{ total() }} itens validados</span>
        @if (revalidacao()) {
          @if (semPorValidar()) {
            <span class="resumo-pronto">Já pode enviar o processo à INCM ou de novo à PCM.</span>
          } @else {
            <span>Valide os documentos acrescentados pela PCM.</span>
          }
        } @else if (todosRevistos()) {
          <span class="resumo-pronto">Todos os itens estão validados. Carregue em «Seguinte» para abrir o resumo.</span>
        }
      </div>
      <app-error-banner [erro]="erro()" />
      @if (conflito()) {
        <button type="button" class="recarregar" (click)="recarregar()">Recarregar</button>
      }
    }

    @if (ficheirosPlantas().length > 0 || erroPlantas()) {
      <section class="plantas" aria-labelledby="validacao-plantas-titulo">
        <h2 id="validacao-plantas-titulo">Plantas</h2>
        <app-error-banner [erro]="erroPlantas()" />
        <ul class="plantas-ficheiros">
          @for (linha of ficheirosPlantas(); track linha.documento.id) {
            <li class="plantas-ficheiro" [class.rejeitado-linha]="ficheirosRejeitados().has(linha.documento.id)">
              <div class="plantas-info">
                <span class="plantas-item">{{ linha.nomeItem }}</span>
                <span class="plantas-nome">{{ linha.documento.nomeFicheiro }}</span>
                <span class="plantas-meta">
                  Carregado em {{ linha.documento.criadoEm | date: 'dd/MM/yyyy HH:mm' }} por {{ linha.documento.criadoPorNome }}
                </span>
              </div>
              <div class="plantas-acoes">
                <button
                  type="button"
                  class="descarregar"
                  [disabled]="descargaService.emCurso() === linha.documento.id"
                  [attr.aria-label]="'Descarregar ' + linha.documento.nomeFicheiro"
                  (click)="descargaService.descarregar(processoId(), linha.documento.id)"
                >
                  Descarregar
                </button>
                <ng-container *ngTemplateOutlet="acaoItem; context: { $implicit: { tipo: 'DOCUMENTO', documento: linha.documento } }" />
              </div>
            </li>
          }
        </ul>
      </section>
    }

    <app-processo-documentos
      [processoId]="processoId()"
      [editavel]="false"
      [informacaoBase]="informacaoBaseLida().informacaoBase"
      [dataAssinatura]="informacaoBaseLida().dataAssinatura"
      [acaoItem]="acaoItem"
      [ficheirosRejeitados]="ficheirosRejeitados()"
      [elementosEsperados]="elementosEsperados()"
      rotuloJustificacao="Justificação do desbloqueio"
      [processo]="processoAtual()"
      (ficheirosAlterados)="ficheiros.set($event)"
    />

    @if (podeSeguir()) {
      <div class="seguinte">
        <button type="button" app-botao (click)="seguinte.emit(haRejeitados() ? 'REJEICAO' : 'VALIDADOS')">Seguinte</button>
      </div>
    }

    <ng-template #acaoItem let-contexto>
      @if (podeValidar()) {
        @let item = itemDe(contexto);
        @if (item) {
          @let nomeFicheiro = contexto.documento?.nomeFicheiro;
          <span aria-live="polite">
            @if (item.estado === 'VALIDADO') {
              <span class="validado">Validado</span>
            } @else if (revalidacao()) {
              @if (item.tipo === 'DOCUMENTO') {
                <button
                  type="button"
                  class="validar"
                  [attr.aria-label]="'Validar ' + nomeDe(contexto)"
                  [disabled]="aValidar() !== null || conflito()"
                  (click)="validar(item)"
                >
                  Validar
                </button>
              } @else {
                <span class="validado">Validado</span>
              }
            } @else if (item.estado === 'REJEITADO') {
              <span class="rejeitado"><span aria-hidden="true">✕ </span>Rejeitado</span>
            } @else {
              <span class="acoes-item">
                <button
                  type="button"
                  class="validar"
                  [disabled]="aValidar() !== null || conflito()"
                  [attr.aria-label]="nomeFicheiro ? 'Validar ' + nomeFicheiro : null"
                  (click)="validar(item)"
                >
                  Validar
                </button>
                <button
                  type="button"
                  class="rejeitar"
                  [disabled]="aValidar() !== null || conflito()"
                  [attr.aria-label]="nomeFicheiro ? 'Rejeitar ' + nomeFicheiro : null"
                  (click)="abrirRejeicao(item, contexto)"
                >
                  Rejeitar
                </button>
              </span>
            }
          </span>
        }
      }
    </ng-template>

    @if (processoAtual(); as atual) {
      <app-rejeitar-item-modal
        [aberto]="aRejeitar() !== null"
        [processo]="atual"
        [item]="aRejeitar()"
        (fechar)="aRejeitar.set(null)"
        (rejeitado)="aposRejeitar($event)"
        (recarregar)="recarregarDepoisDaRejeicao()"
      />
    }
  `,
})
export class ProcessoValidacaoComponent {
  private readonly processoService = inject(ProcessoService);
  private readonly plantasService = inject(PlantasService);
  private readonly documentoService = inject(DocumentoService);
  protected readonly descargaService = inject(DescargaService);

  protected readonly processoAtual = signal<ProcessoDetalhe | null>(null);
  readonly documentosRef = viewChild(ProcessoDocumentosComponent);

  @Input({ required: true }) set processo(valor: ProcessoDetalhe) {
    const anterior = this.processoAtual();
    this.processoAtual.set(valor);
    if (valor.estado !== 'VALIDADO_DGT') {
      this.revalidacaoIniciada.set(false);
    } else if (valor.revalidacaoPcm) {
      this.revalidacaoIniciada.set(true);
    }
    if (anterior?.id !== valor.id) {
      this.carregarPlantas();
    }
  }

  /** Fica ligada depois do último «Validar», para a vista dizer que o processo já pode seguir. */
  private readonly revalidacaoIniciada = signal(false);
  readonly revalidacao = this.revalidacaoIniciada.asReadonly();

  /** Sem payload: o ecrã relê o detalhe, que traz as `acoesDisponiveis` e a `versao` em vigor. */
  @Output() readonly processoAlterado = new EventEmitter<void>();
  /** Com itens rejeitados abre o resumo da rejeição; com todos validados, o Resumo Validados. */
  @Output() readonly seguinte = new EventEmitter<ResumoSeguinte>();

  readonly ficheiros = signal<DocumentoProcesso[]>([]);
  /** Um item da página das Plantas sem ficheiro não aparece: não há o que validar. */
  readonly ficheirosPlantas = signal<FicheiroPlantas[]>([]);
  readonly erroPlantas = signal<ApiError | null>(null);
  /** Estado de cada item segundo a última resposta do `PUT`, que traz todos os itens do processo. */
  private readonly gravados = signal<Record<string, EstadoValidacao>>({});
  private readonly todosRevistosGravado = signal<boolean | null>(null);
  /** O item em gravação; enquanto houver um, nenhum «Validar» aceita cliques. */
  readonly aValidar = signal<string | null>(null);
  /** O item com o modal «Rejeitar» aberto. */
  readonly aRejeitar = signal<ItemARejeitar | null>(null);
  readonly erro = signal<ApiError | null>(null);

  /** Itens rejeitados no rascunho da validação, lidos do detalhe: a validação retoma onde ficou. */
  private readonly rejeitadosNoRascunho = computed(
    () => new Set((this.processoAtual()?.rejeicoesEmValidacao ?? []).map((rejeicao) => rejeicao.itemId)),
  );

  readonly processoId = computed(() => this.processoAtual()?.id ?? '');
  readonly informacaoBaseLida = computed(() => {
    const processo = this.processoAtual();
    return processo ? informacaoBaseDe(processo) : { informacaoBase: null, dataAssinatura: null };
  });
  readonly elementosEsperados = computed(() => (this.processoAtual()?.informacaoBase ?? []).map((elemento) => elemento.elemento));
  readonly podeValidar = computed(
    () => this.revalidacao() || this.processoAtual()?.acoesDisponiveis.includes('validar') === true,
  );
  readonly conflito = computed(() => CODIGOS_DE_CONFLITO.includes(this.erro()?.codigo ?? ''));

  private readonly itens = computed<ItemValidacaoEcra[]>(() => {
    const documentos = this.ficheiros().map(
      (ficheiro): ItemValidacaoEcra => ({
        tipo: 'DOCUMENTO',
        itemId: ficheiro.id,
        estado: this.estadoDe(ficheiro.id, ficheiro.estadoValidacao),
      }),
    );
    const elementos = (this.processoAtual()?.informacaoBase ?? []).map(
      (elemento): ItemValidacaoEcra => ({
        tipo: 'INFORMACAO_BASE',
        itemId: elemento.id,
        estado: this.estadoDe(elemento.id, elemento.estadoValidacao),
      }),
    );
    const dosSeparadores = new Set(documentos.map((documento) => documento.itemId));
    const plantas = this.ficheirosPlantas()
      .filter((linha) => !dosSeparadores.has(linha.documento.id))
      .map(
        ({ documento }): ItemValidacaoEcra => ({
          tipo: 'DOCUMENTO',
          itemId: documento.id,
          estado: this.estadoDe(documento.id, documento.estadoValidacao),
        }),
      );
    return [...plantas, ...documentos, ...elementos];
  });

  readonly ficheirosRejeitados = computed(
    () => new Set(this.itens().filter((item) => item.tipo === 'DOCUMENTO' && item.estado === 'REJEITADO').map((item) => item.itemId)),
  );
  readonly total = computed(() => this.itens().length);
  readonly validados = computed(() => this.itens().filter((item) => item.estado === 'VALIDADO').length);
  /**
   * A regra é a do `validar`, calculada no backend: a do detalhe, ou, depois de um `PUT`, nenhum item por validar
   * e nenhum rejeitado. Um item rejeitado leva o processo para «Confirmar Rejeição», não para validar.
   */
  readonly todosRevistos = computed(() => {
    const gravado = this.todosRevistosGravado();
    return gravado === null
      ? this.processoAtual()?.todosItensRevistos === true
      : gravado && this.itens().every((item) => item.estado === 'VALIDADO');
  });

  readonly haRejeitados = computed(() => this.itens().some((item) => item.estado === 'REJEITADO'));
  readonly semPorValidar = computed(() => this.itens().every((item) => item.estado !== 'POR_VALIDAR'));

  /** Só com todos os itens revistos; um processo sem itens segue a regra do `validar`, calculada no backend. */
  readonly podeSeguir = computed(() => {
    const itens = this.itens();
    if (!this.podeValidar() || itens.some((item) => item.estado === 'POR_VALIDAR')) {
      return false;
    }
    return itens.length > 0 || this.todosRevistos();
  });

  /** A última resposta do `PUT` manda; antes dela, o estado lido e o rascunho do detalhe. */
  private estadoDe(itemId: string, lido: EstadoValidacao): EstadoValidacao {
    return this.gravados()[itemId] ?? (this.rejeitadosNoRascunho().has(itemId) ? 'REJEITADO' : lido);
  }

  /** Sem item não há botão: o elemento da Informação Base só existe depois de a DGT capturar o processo. */
  itemDe(contexto: ContextoAcaoItem): ItemValidacaoEcra | null {
    if (contexto.tipo === 'DOCUMENTO' && contexto.documento) {
      const { id, estadoValidacao } = contexto.documento;
      return { tipo: 'DOCUMENTO', itemId: id, estado: this.estadoDe(id, estadoValidacao) };
    }
    const elemento = this.processoAtual()?.informacaoBase.find((e) => e.elemento === contexto.elemento);
    return elemento
      ? { tipo: 'INFORMACAO_BASE', itemId: elemento.id, estado: this.estadoDe(elemento.id, elemento.estadoValidacao) }
      : null;
  }

  nomeDe(contexto: ContextoAcaoItem): string {
    if (contexto.tipo === 'DOCUMENTO') {
      return contexto.documento?.nomeFicheiro ?? '';
    }
    return contexto.elemento ? ROTULO_POR_ELEMENTO[contexto.elemento] : '';
  }

  abrirRejeicao(item: ItemValidacaoEcra, contexto: ContextoAcaoItem): void {
    if (this.aValidar() !== null) {
      return;
    }
    const nome = this.nomeDe(contexto);
    this.erro.set(null);
    this.aRejeitar.set({ tipo: item.tipo, itemId: item.itemId, nome });
  }

  aposRejeitar(resposta: ValidacaoItensResponse): void {
    this.aRejeitar.set(null);
    this.registarResposta(resposta);
    this.processoAlterado.emit();
  }

  recarregarDepoisDaRejeicao(): void {
    this.aRejeitar.set(null);
    this.recarregar();
  }

  private registarResposta(resposta: ValidacaoItensResponse): void {
    this.gravados.set(Object.fromEntries(resposta.itens.map((gravado) => [gravado.itemId, gravado.estadoValidacao])));
    this.todosRevistosGravado.set(resposta.todosRevistos);
  }

  validar(item: ItemValidacaoEcra): void {
    const processo = this.processoAtual();
    if (!processo || this.aValidar() !== null) {
      return;
    }
    this.erro.set(null);
    this.aValidar.set(item.itemId);
    this.processoService
      .gravarValidacaoItens(processo.id, processo.versao, [{ tipo: item.tipo, itemId: item.itemId, decisao: 'VALIDADO' }])
      .subscribe({
        next: (resposta) => {
          this.aValidar.set(null);
          this.registarResposta(resposta);
          this.processoAlterado.emit();
        },
        error: (erro: ApiError) => {
          this.aValidar.set(null);
          this.erro.set(erro);
        },
      });
  }

  /** Depois de um 409 o detalhe e os ficheiros lidos estão velhos: volta-se a lê-los, nunca a repetir o pedido. */
  recarregar(): void {
    this.erro.set(null);
    this.gravados.set({});
    this.todosRevistosGravado.set(null);
    this.documentosRef()?.recarregar();
    this.carregarPlantas();
    this.processoAlterado.emit();
  }

  /** O estado de validação, a data e o autor vêm da lista de documentos; a página das Plantas dá o item de cada ficheiro. */
  private carregarPlantas(): void {
    const processo = this.processoAtual();
    this.erroPlantas.set(null);
    if (!processo?.exigePlantasEMetadados) {
      this.ficheirosPlantas.set([]);
      return;
    }
    forkJoin([
      this.plantasService.obterPlantas(processo.id),
      this.documentoService.listar(processo.id, {}, 0, TAMANHO_PAGINA_DOCUMENTOS),
    ]).subscribe({
      next: ([plantas, pagina]) => {
        const porId = new Map(pagina.content.map((documento) => [documento.id, documento]));
        this.ficheirosPlantas.set(
          plantas.itens.flatMap((item) => {
            const documento = item.ficheiro ? porId.get(item.ficheiro.id) : undefined;
            return documento ? [{ nomeItem: item.nome, documento }] : [];
          }),
        );
      },
      error: (erro: ApiError) => {
        this.ficheirosPlantas.set([]);
        this.erroPlantas.set(erro);
      },
    });
  }
}
