import { DatePipe, NgTemplateOutlet } from '@angular/common';
import { Component, ElementRef, EventEmitter, HostBinding, Injector, Input, OnChanges, OnInit, Output, SimpleChanges, TemplateRef, afterNextRender, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable, catchError, forkJoin, map, of } from 'rxjs';
import { AbstractControl, FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { ApiError } from '../../../core/models/error-response.model';
import {
  CategoriaDocumento,
  DocumentoProcesso,
  EstruturaDocumental,
  ItemDocumental,
  PendenciaDocumental,
  SeparadorDocumental,
} from '../../../models/documento.model';
import {
  AtualizarDadosFormularioRequest,
  DestinoProcesso,
  ElementoInformacaoBase,
  InformacaoBase,
  InformacaoBaseDeliberacao,
  InformacaoBaseTextoAto,
  InstrumentoAbrangido,
  ProcessoDetalhe,
  TipoItemValidacao,
} from '../../../models/processo.model';
import { DescargaService } from '../../../services/descarga.service';
import { CODIGO_CARREGAMENTO_SEM_LIGACAO, DocumentoService } from '../../../services/documento.service';
import { ProcessoService } from '../../../services/processo.service';
import { DestinoProcessoComponent } from '../destino-processo/destino-processo.component';
import {
  CAMINHO_ERRO_AVALIACAO_AMBIENTAL,
  CAMINHO_ERRO_DATA_DELIBERACAO,
  CAMINHO_ERRO_PRAZO,
  InformacaoBaseAlterada,
  MENSAGEM_CAMPO_BLOQUEADO,
  errosCoerenciaInformacaoBase,
} from '../informacao-base';
import { ConfirmacaoModalComponent } from '../../../shared/components/confirmacao-modal.component';
import { ErrorBannerComponent } from '../../../shared/components/error-banner.component';
import { AlertaComponent } from '../../../shared/ui/alerta/alerta.component';
import { BotaoComponent } from '../../../shared/ui';
import { ModalComponent } from '../../../shared/ui/modal/modal.component';
import { DesbloquearCampoComponent } from '../desbloquear-campo/desbloquear-campo.component';
import { PlanosAssociadosComponent } from './planos-associados.component';

/** Sem resposta da API o interceptor só tem o texto técnico do Angular. */
const MENSAGEM_SEM_RESPOSTA = 'Não foi possível concluir a operação. Tente novamente.';

// Recusas do ficheiro que ficam no item, com o texto do servidor (API-DOC-003), e o carregamento que não chegou à API.
const CODIGOS_ERRO_DE_FICHEIRO = [
  CODIGO_CARREGAMENTO_SEM_LIGACAO,
  'VALIDACAO',
  'DOC_NOME_INVALIDO',
  'DOC_EXTENSAO_NAO_ACEITE',
  'DOC_TAMANHO_EXCEDIDO',
  'DOC_ZIP_INVALIDO',
  'DOC_TIFF_INVALIDO',
  'DOC_CLASSIFICACAO_INVALIDA',
  'DOC_CANAL_GEOCONFORM',
];

const CHAVE_OUTROS = 'outros';
const CHAVE_OUTROS_PUBLICACAO = 'outros-publicacao';
type CampoPrazo = 'prazoAnos' | 'prazoMeses' | 'prazoDias';
const CAMPOS_PRAZO: readonly CampoPrazo[] = ['prazoAnos', 'prazoMeses', 'prazoDias'];
const CHAVE_ACORDEAO_OUTROS = 'acordeao-outros';

type ChaveInformacaoBase = 'deliberacao' | 'textoAto';

const ELEMENTOS_INFORMACAO_BASE: ElementoInformacaoBase[] = ['DELIBERACAO', 'TEXTO_ATO'];
const CHAVE_POR_ELEMENTO: Record<ElementoInformacaoBase, ChaveInformacaoBase> = { DELIBERACAO: 'deliberacao', TEXTO_ATO: 'textoAto' };
const ELEMENTO_POR_CHAVE: Record<ChaveInformacaoBase, ElementoInformacaoBase> = { deliberacao: 'DELIBERACAO', textoAto: 'TEXTO_ATO' };
/** Rótulo do elemento, só para apresentação: onde o bloco aparece decide-o `elementoInformacaoBase`, nunca o nome do separador. */
export const ROTULO_POR_ELEMENTO: Record<ElementoInformacaoBase, string> = { DELIBERACAO: 'Deliberação', TEXTO_ATO: 'Texto do Ato' };

interface RemocaoPendente {
  documento: DocumentoProcesso;
  chave: string;
}

const MAXIMO_JUSTIFICACAO_DESBLOQUEIO = 2000;
const MENSAGEM_JUSTIFICACAO_EM_FALTA = 'Indique a justificação do desbloqueio.';
// O 400 da justificação fica junto da área de texto; um 409 é um alerta no modal.
const CODIGOS_ERRO_DA_JUSTIFICACAO = ['DOC_JUSTIFICACAO_EM_FALTA', 'VALIDACAO'];

/** O que o ponto de encaixe `acaoItem` recebe: o ficheiro, ou o elemento da Informação Base, a que a ação se aplica. */
export interface ContextoAcaoItem {
  tipo: TipoItemValidacao;
  documento?: DocumentoProcesso;
  elemento?: ElementoInformacaoBase;
}

function semValores(dados: object): boolean {
  return Object.values(dados).every((valor) => valor === null);
}

function itensSemFicheiro(separador: SeparadorDocumental): ItemDocumental[] {
  return [...separador.publicacao, ...separador.deposito].filter((item) => item.ficheiros.length === 0);
}

/** Um ficheiro com tags aparece também nos itens das tags: conta-se uma vez. */
function ficheirosDa(estrutura: EstruturaDocumental): DocumentoProcesso[] {
  const itens = estrutura.separadores.flatMap((separador) => [...separador.publicacao, ...separador.deposito]);
  const porId = new Map<string, DocumentoProcesso>();
  for (const ficheiro of [...itens.flatMap((item) => item.ficheiros), ...estrutura.outros]) {
    porId.set(ficheiro.id, ficheiro);
  }
  return [...porId.values()];
}


/** Item de depósito obrigatório que um ficheiro carregado noutro item pode também satisfazer (tag). */
interface ItemElegivelTag {
  documentoConfigId: number;
  rotulo: string;
  extensoesAceites: string[];
}

/** Itens documentais do processo por separador e caixa (Publicação/Depósito), com carregar, remover e descarregar. */
@Component({
  selector: 'app-processo-documentos',
  standalone: true,
  imports: [
    DatePipe,
    DesbloquearCampoComponent,
    NgTemplateOutlet,
    ReactiveFormsModule,
    BotaoComponent,
    ConfirmacaoModalComponent,
    ErrorBannerComponent,
    AlertaComponent,
    ModalComponent,
    PlanosAssociadosComponent,
    DestinoProcessoComponent,
  ],
  styleUrls: ['./processo-documentos.component.css'],
  template: `
    @if (processo && associaInstrumentos(processo) && !modoAcrescentar) {
      <app-planos-associados [processo]="processo" (associacaoGravada)="associacaoGravada.emit($event)" />
    }
    @if (processo && mostraDestino(processo)) {
      <app-destino-processo
        class="destino"
        [valor]="processo.destino"
        [editavel]="destinoEditavel(processo)"
        [comFicheiros]="comFicheiros()"
        [erro]="erroDestino()"
        [aGravar]="aGravarDestino()"
        (alterado)="gravarDestino($event)"
      />
    }
    <!-- A região existe sempre: um leitor de ecrã só anuncia o que muda dentro de uma região já presente. -->
    <p class="so-leitor" aria-live="polite">{{ anuncioDestino() }}</p>
    <app-error-banner [erro]="erro()" />
    @if (descargaService.erro(); as mensagemDescarga) {
      <p class="erro-descarga" role="alert">{{ mensagemDescarga }}</p>
    }
    @if (erro() && !estrutura() && !aCarregar()) {
      <button type="button" class="acao acao-recarregar" (click)="tentarNovamente()">Tentar novamente</button>
    }

    @if (aCarregar()) {
      <p class="estado-carregamento">A carregar…</p>
    } @else if (estrutura(); as e) {
      @if (!editavel && semFicheiros()) {
        <p class="estado-vazio">Sem documentos carregados</p>
      }
      @if (e.separadores.length === 0) {
        @if (editavel) {
          <p class="estado-vazio">Indique os elementos fundamentais afetados e guarde o rascunho para ver os itens documentais.</p>
        }
      } @else {
        <section class="grupo">
          @if (!semTitulosDeGrupo) {
            <h2>Publicação INCM e Depósito DGT</h2>
          }
          @for (separador of e.separadores; track separador.separador) {
            <div class="acordeao">
              <button
                type="button"
                class="acordeao-cabecalho"
                [attr.aria-expanded]="aberto('sep-' + separador.separador)"
                [attr.aria-controls]="'acordeao-corpo-' + $index"
                (click)="alternar('sep-' + separador.separador)"
              >
                <ng-container *ngTemplateOutlet="icone; context: { $implicit: 'separador' }" />
                <span class="acordeao-titulo">{{ separador.separador }}</span>
                <span class="seta" [class.aberta]="aberto('sep-' + separador.separador)" aria-hidden="true"></span>
              </button>
              <div class="acordeao-corpo" [id]="'acordeao-corpo-' + $index">
                @if (aberto('sep-' + separador.separador)) {
                  <div class="caixas">
                    <div class="coluna">
                      @for (item of separador.publicacao; track item.documentoConfigId) {
                        <ng-container *ngTemplateOutlet="caixaItem; context: { $implicit: item, rotulo: 'Publicação', varios: separador.publicacao.length > 1 }" />
                      }
                    </div>
                    <div class="coluna">
                      @for (item of separador.deposito; track item.documentoConfigId) {
                        <ng-container *ngTemplateOutlet="caixaItem; context: { $implicit: item, rotulo: 'Depósito', varios: separador.deposito.length > 1 }" />
                      }
                    </div>
                  </div>
                  @if (editavel && !modoAcrescentar && temItensSemFicheiro(separador)) {
                    <div class="observacoes">
                      @if (embutido) {
                        <div class="observacoes-rotulo">
                          <label [for]="'observacoes-' + $index">Observações @if (observacoesObrigatorias(separador)) {<span class="obrigatorio" aria-hidden="true">*</span>}</label>
                        </div>
                      } @else {
                        <label class="rotulo" [for]="'observacoes-' + $index">Observações @if (observacoesObrigatorias(separador)) {<span class="obrigatorio" aria-hidden="true">*</span>}</label>
                      }
                      <p class="observacoes-ajuda" [id]="'observacoes-ajuda-' + $index">
                        Se não carregar ficheiro, indique o motivo («NA» se não aplicável).
                      </p>
                      <textarea
                        [id]="'observacoes-' + $index"
                        maxlength="1000"
                        [attr.placeholder]="observacoesObrigatorias(separador) ? 'Indique o motivo de não carregar ficheiro' : 'Adicione informações adicionais sobre este documento (opcional)'"
                        [required]="observacoesObrigatorias(separador)"
                        [value]="textoObservacoes(separador)"
                        [attr.aria-describedby]="'observacoes-ajuda-' + $index + (errosObservacoes()[separador.separador] ? ' observacoes-erro-' + $index : '')"
                        (input)="alterarObservacoes(separador.separador, $event)"
                      ></textarea>
                      @if (errosObservacoes()[separador.separador]; as mensagem) {
                        <p class="erro-campo" role="alert" [id]="'observacoes-erro-' + $index">{{ mensagem }}</p>
                      }
                    </div>
                  }
                  @if (separador.elementoInformacaoBase; as elemento) {
                    <ng-container *ngTemplateOutlet="blocoInformacaoBase; context: { $implicit: chavePorElemento[elemento] }" />
                  }
                }
              </div>
            </div>
          }
        </section>
      }

      @for (elemento of elementosSemSeparador(); track elemento) {
        <section class="grupo">
          <h2>{{ rotulo(elemento) }}</h2>
          <ng-container *ngTemplateOutlet="blocoInformacaoBase; context: { $implicit: elemento }" />
        </section>
      }

      <section class="grupo">
        @if (!semTitulosDeGrupo) {
          <h2>Depósito DGT</h2>
        }
        <div class="acordeao">
          <button
            type="button"
            class="acordeao-cabecalho"
            [attr.aria-expanded]="aberto(chaveAcordeaoOutros)"
            aria-controls="acordeao-corpo-outros"
            (click)="alternar(chaveAcordeaoOutros)"
          >
            <ng-container *ngTemplateOutlet="icone; context: { $implicit: 'outros' }" />
            <span class="acordeao-titulo">Outros</span>
            <span class="seta" [class.aberta]="aberto(chaveAcordeaoOutros)" aria-hidden="true"></span>
          </button>
          <div class="acordeao-corpo" id="acordeao-corpo-outros">
            @if (aberto(chaveAcordeaoOutros)) {
              <div class="caixas">
                @if (outrosComPublicacao()) {
                  <div class="coluna">
                    <div class="caixa">
                      <div class="caixa-cabecalho"><span class="caixa-titulo">Publicação</span></div>
                      <ng-container *ngTemplateOutlet="ficheiros; context: { $implicit: outrosDa(e, 'PUBLICACAO'), chave: chaveOutrosPublicacao }" />
                      <ng-container *ngTemplateOutlet="zona; context: { chave: chaveOutrosPublicacao, categoria: 'PUBLICACAO', extensoes: [] }" />
                    </div>
                  </div>
                }
                <div class="coluna">
                  <div class="caixa">
                    <div class="caixa-cabecalho"><span class="caixa-titulo">Depósito</span></div>
                    <ng-container *ngTemplateOutlet="ficheiros; context: { $implicit: outrosDa(e, 'DEPOSITO'), chave: chaveOutros }" />
                    <ng-container *ngTemplateOutlet="zona; context: { chave: chaveOutros, categoria: 'DEPOSITO', extensoes: [] }" />
                  </div>
                </div>
              </div>
              @if (editavel && !modoAcrescentar) {
                <div class="observacoes">
                  <label class="rotulo" for="observacoes-outros">Observações</label>
                  <textarea
                    id="observacoes-outros"
                    maxlength="1000"
                    placeholder="Adicione informações adicionais sobre estes documentos (opcional)"
                    [value]="textoObservacoesOutros(e)"
                    [attr.aria-describedby]="errosObservacoes()[chaveOutros] ? 'observacoes-erro-outros' : null"
                    (input)="alterarObservacoes(chaveOutros, $event)"
                  ></textarea>
                  @if (errosObservacoes()[chaveOutros]; as mensagem) {
                    <p class="erro-campo" role="alert" id="observacoes-erro-outros">{{ mensagem }}</p>
                  }
                </div>
              } @else if (e.observacoesOutros) {
                <p class="observacoes-leitura"><span>Observações:</span> {{ e.observacoesOutros }}</p>
              }
            }
          </div>
        </div>
      </section>
    }

    <ng-template #blocoInformacaoBase let-elemento>
      <section class="informacao-base" [attr.aria-label]="'Informação Base — ' + rotulo(elemento)">
        @if (embutido) {
          <h3 class="informacao-base-titulo">
            <button
              type="button"
              class="informacao-base-alternar"
              [attr.aria-expanded]="aberto('ib-' + elemento)"
              [attr.aria-controls]="'informacao-base-corpo-' + elemento"
              (click)="alternar('ib-' + elemento)"
            >
              Informação Base
              <span class="seta" [class.aberta]="aberto('ib-' + elemento)" aria-hidden="true"></span>
            </button>
          </h3>
        } @else {
          <h3 class="informacao-base-titulo">Informação Base</h3>
        }
        <div class="informacao-base-corpo" [id]="'informacao-base-corpo-' + elemento">
          @if (!embutido || aberto('ib-' + elemento)) {
            @if (erroCampo('informacaoBase.' + elemento); as mensagem) {
              <p class="erro-campo" role="alert">{{ mensagem }}</p>
            }
            @if (editavel) {
              <form class="informacao-base-campos" [formGroup]="form" (focusout)="saiuDeUmCampo.set(true)">
                <fieldset class="campo" [formGroupName]="elemento" [attr.aria-describedby]="prazoBloqueado(elemento) ? 'bloqueado-prazo-' + elemento : null">
                  <legend class="rotulo" [id]="'prazo-rotulo-' + elemento">Prazo</legend>
                  @if (embutido) {
                    <button
                      type="button"
                      class="seletor-prazo"
                      [attr.aria-expanded]="prazoAberto(elemento)"
                      [attr.aria-controls]="'prazo-painel-' + elemento"
                      [attr.aria-labelledby]="'prazo-rotulo-' + elemento + ' prazo-resumo-' + elemento"
                      [disabled]="prazoBloqueado(elemento)"
                      (click)="alternarPrazo(elemento)"
                      (keydown.escape)="fecharPrazo(elemento, $event)"
                    >
                      <span [id]="'prazo-resumo-' + elemento">{{ resumoPrazo(elemento) }}</span>
                      <svg class="seletor-prazo-seta" [class.aberta]="prazoAberto(elemento)" viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                        <path d="m7 10 5 5 5-5" />
                      </svg>
                    </button>
                  }
                  <div class="prazo" [id]="'prazo-painel-' + elemento" [hidden]="embutido && !prazoAberto(elemento)">
                    <label class="prazo-parte">
                      <input
                        type="number"
                        min="0"
                        step="1"
                        aria-label="Prazo"
                        [value]="valorPrazo(elemento) ?? ''"
                        [disabled]="prazoBloqueado(elemento)"
                        (input)="alterarValorPrazo(elemento, $event)"
                        (keydown.escape)="fecharPrazo(elemento, $event)"
                      />
                    </label>
                    <label class="prazo-parte">
                      <span class="so-leitor">Unidade do prazo</span>
                      <select
                        [value]="unidadePrazo(elemento)"
                        [disabled]="prazoBloqueado(elemento)"
                        (change)="alterarUnidadePrazo(elemento, $event)"
                        (keydown.escape)="fecharPrazo(elemento, $event)"
                      >
                        @for (unidade of unidadesPrazo; track unidade.campo) {
                          <option [value]="unidade.campo" [selected]="unidade.campo === unidadePrazo(elemento)">{{ unidade.rotulo }}</option>
                        }
                      </select>
                    </label>
                  </div>
                  @if (prazoBloqueado(elemento)) {
                    <p class="aviso-bloqueado" [id]="'bloqueado-prazo-' + elemento">{{ mensagemBloqueado }}</p>
                    <app-desbloquear-campo [chave]="'informacaoBase.' + elemento + '.prazoAnos'" [rotulo]="'Prazo — ' + (elemento === 'deliberacao' ? 'Deliberação' : 'Texto do ato')" />
                  }
                  @for (mensagem of errosPrazo(elemento); track mensagem) {
                    <p class="erro-campo" role="alert">{{ mensagem }}</p>
                  }
                </fieldset>

                <fieldset class="campo" [formGroupName]="elemento" [attr.aria-describedby]="bloqueado(elemento, 'avaliacaoAmbiental') ? 'bloqueado-avaliacao-' + elemento : null">
                  <legend class="rotulo">Avaliação Ambiental</legend>
                  <div class="opcoes">
                    <label class="opcao"><input type="radio" formControlName="avaliacaoAmbiental" [value]="true" /> Sim</label>
                    <label class="opcao"><input type="radio" formControlName="avaliacaoAmbiental" [value]="false" /> Não</label>
                  </div>
                  @if (bloqueado(elemento, 'avaliacaoAmbiental')) {
                    <p class="aviso-bloqueado" [id]="'bloqueado-avaliacao-' + elemento">{{ mensagemBloqueado }}</p>
                    <app-desbloquear-campo [chave]="'informacaoBase.' + elemento + '.avaliacaoAmbiental'" [rotulo]="'Avaliação Ambiental — ' + (elemento === 'deliberacao' ? 'Deliberação' : 'Texto do ato')" />
                  }
                  @if (erroCampo('informacaoBase.' + elemento + '.avaliacaoAmbiental'); as mensagem) {
                    <p class="erro-campo" role="alert">{{ mensagem }}</p>
                  }
                </fieldset>

                @if (elemento === 'deliberacao') {
                  <div class="campo" formGroupName="deliberacao">
                    <label class="rotulo" for="informacao-base-data-deliberacao">Data da deliberação</label>
                    <input id="informacao-base-data-deliberacao" type="date" formControlName="dataDeliberacao" [attr.aria-describedby]="descricaoDataDeliberacao()" />
                    @if (dataLimiteDinamica) {
                      <p class="ajuda-campo" id="ajuda-data-deliberacao">
                        Tem de ser igual ou anterior a {{ dataLimiteDinamica | date: 'dd/MM/yyyy' }}, data limite da dinâmica.
                      </p>
                    }
                    @if (bloqueado('deliberacao', 'dataDeliberacao')) {
                      <p class="aviso-bloqueado" id="bloqueado-data-deliberacao">{{ mensagemBloqueado }}</p>
                      <app-desbloquear-campo chave="informacaoBase.deliberacao.dataDeliberacao" rotulo="Data da deliberação" />
                    }
                    @if (erroCampo('informacaoBase.deliberacao.dataDeliberacao'); as mensagem) {
                      <p class="erro-campo" role="alert">{{ mensagem }}</p>
                    }
                  </div>
                  <div class="campo campo-largo" formGroupName="deliberacao">
                    <label class="rotulo" for="informacao-base-texto-publicar">Texto a publicar</label>
                    <textarea id="informacao-base-texto-publicar" formControlName="textoPublicar" placeholder="Escreva aqui" [attr.aria-describedby]="bloqueado('deliberacao', 'textoPublicar') ? 'bloqueado-texto-publicar' : null"></textarea>
                    @if (bloqueado('deliberacao', 'textoPublicar')) {
                      <p class="aviso-bloqueado" id="bloqueado-texto-publicar">{{ mensagemBloqueado }}</p>
                      <app-desbloquear-campo chave="informacaoBase.deliberacao.textoPublicar" rotulo="Texto a publicar" />
                    }
                    @if (erroCampo('informacaoBase.deliberacao.textoPublicar'); as mensagem) {
                      <p class="erro-campo" role="alert">{{ mensagem }}</p>
                    }
                  </div>
                } @else {
                  <div class="campo">
                    <label class="rotulo" for="informacao-base-data-assinatura">Data de Assinatura do ato</label>
                    <input id="informacao-base-data-assinatura" type="date" formControlName="dataAssinatura" [attr.aria-describedby]="camposBloqueados.includes('dataAssinatura') ? 'bloqueado-data-assinatura' : null" />
                    @if (camposBloqueados.includes('dataAssinatura')) {
                      <p class="aviso-bloqueado" id="bloqueado-data-assinatura">{{ mensagemBloqueado }}</p>
                      <app-desbloquear-campo chave="dataAssinatura" rotulo="Data de Assinatura do ato" />
                    }
                    @if (erroCampo('dataAssinatura'); as mensagem) {
                      <p class="erro-campo" role="alert">{{ mensagem }}</p>
                    }
                  </div>
                }
              </form>
            } @else {
              <dl class="informacao-base-leitura">
                @let dados = dadosDoElemento(elemento);
                <div><dt>Prazo</dt><dd>{{ prazoLegivel(dados) }}</dd></div>
                <div><dt>Avaliação Ambiental</dt><dd>{{ simNao(dados?.avaliacaoAmbiental) }}</dd></div>
                @if (elemento === 'deliberacao') {
                  @let deliberacao = valores().informacaoBase?.deliberacao;
                  <div>
                    <dt>Data da deliberação</dt>
                    <dd>{{ deliberacao?.dataDeliberacao ? (deliberacao?.dataDeliberacao | date: 'dd/MM/yyyy') : '—' }}</dd>
                  </div>
                  <div><dt>Texto a publicar</dt><dd>{{ deliberacao?.textoPublicar || '—' }}</dd></div>
                } @else {
                  <div>
                    <dt>Data de Assinatura do ato</dt>
                    <dd>{{ valores().dataAssinatura ? (valores().dataAssinatura | date: 'dd/MM/yyyy') : '—' }}</dd>
                  </div>
                }
              </dl>
            }
            @if (acaoItem) {
              <div class="informacao-base-acao">
                <ng-container *ngTemplateOutlet="acaoItem; context: contextoInformacaoBase(elemento)" />
              </div>
            }
          }
        </div>
      </section>
    </ng-template>

    <ng-template #icone let-tipo>
      @if (embutido) {
        <span class="icone-caixa" aria-hidden="true">
          @if (tipo === 'outros') {
            <svg class="icone" viewBox="0 0 16 16" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="11.33" cy="4.67" r="3" />
              <circle cx="4" cy="8.33" r="2.33" />
              <circle cx="9.67" cy="12.33" r="1.67" />
            </svg>
          } @else {
            <svg class="icone" viewBox="0 0 16 16" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M5.67 3.67 8 1.33l2.33 2.34L8 6zM10 8.33 12.33 6l2.34 2.33-2.34 2.34zM5.67 12.33 8 10l2.33 2.33L8 14.67zM1.33 8l2.34-2.33L6 8l-2.33 2.33z" />
            </svg>
          }
        </span>
      } @else {
        <svg class="icone" viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
          <path d="M6 2h8l5 5v15H6z M14 2v5h5" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round" />
        </svg>
      }
    </ng-template>

    <ng-template #caixaItem let-item let-rotulo="rotulo" let-varios="varios">
      <div
        class="caixa"
        [class.caixa-pendente]="item.pendente"
        [attr.role]="avisoGeoconform(item) ? 'group' : null"
        [attr.aria-labelledby]="avisoGeoconform(item) ? 'item-nome-' + item.documentoConfigId : null"
        [attr.aria-describedby]="avisoGeoconform(item) ? 'aviso-geoconform-' + item.documentoConfigId : null"
      >
        <div class="caixa-cabecalho">
          @if (embutido) {
            <span class="marca-caixa" [class.marca-caixa-com]="item.obrigatorio" aria-hidden="true">
              <svg viewBox="0 0 14 14" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
                <path d="m3.5 7.3 2.3 2.2 4.7-5" />
              </svg>
            </span>
          } @else {
            <input type="checkbox" disabled [checked]="item.obrigatorio" [attr.aria-label]="rotulo + ': ' + item.nome + (item.obrigatorio ? ', obrigatório' : ', opcional')" />
          }
          <span class="caixa-titulo">{{ rotulo }}</span>
          @if (embutido) {
            <span class="so-leitor">, {{ item.obrigatorio ? 'obrigatório' : 'opcional' }}, {{ item.ficheiros.length > 0 ? 'com ficheiro' : 'sem ficheiro' }}</span>
          }
        </div>
        <div class="item-nome" [id]="'item-nome-' + item.documentoConfigId" [class.so-leitor]="embutido && !varios">{{ item.nome }}</div>
        @if (!editavel && item.observacoes) {
          <p class="observacoes-leitura"><span>Observações:</span> {{ item.observacoes }}</p>
        }
        @if (item.pendente) {
          <p class="pendencia">Falta carregar um ficheiro ou indicar o motivo nas observações.</p>
        }
        <ng-container
          *ngTemplateOutlet="ficheiros; context: { $implicit: item.ficheiros, chave: chaveItem(item), substituivel: !item.obtidoDoGeoconform, itemId: item.documentoConfigId }"
        />
        @if (avisoGeoconform(item)) {
          <app-alerta class="aviso-geoconform" tipo="info" [id]="'aviso-geoconform-' + item.documentoConfigId">
            As peças gráficas desta Dinâmica são obtidas do GeoConform.
          </app-alerta>
        } @else {
          <ng-container
            *ngTemplateOutlet="zona; context: { chave: chaveItem(item), categoria: item.categoria, documentoConfigId: item.documentoConfigId, extensoes: item.extensoesAceites }"
          />
        }
      </div>
    </ng-template>

    <ng-template #ficheiros let-lista let-chave="chave" let-substituivel="substituivel" let-itemId="itemId">
      @if (lista.length > 0) {
        <ul class="ficheiros">
          @for (ficheiro of lista; track ficheiro.id) {
            @let porTag = associadoPorTag(ficheiro, itemId);
            <li
              class="ficheiro"
              [class.ficheiro-rejeitado]="ficheirosRejeitados.has(ficheiro.id) || rejeitadoPorCorrigir(ficheiro)"
            >
              <div class="ficheiro-info">
                <span class="ficheiro-nome">{{ ficheiro.nomeFicheiro }}</span>
                <span class="ficheiro-meta">Carregado em {{ ficheiro.criadoEm | date: 'dd/MM/yyyy HH:mm' }} por {{ ficheiro.criadoPorNome }}</span>
                @if (porTag) {
                  <span class="ficheiro-meta ficheiro-tag">Associado a partir de: {{ nomeDoItemPrincipal(ficheiro) }}</span>
                }
                @if (rejeitadoPorCorrigir(ficheiro)) {
                  <span class="ficheiro-meta ficheiro-rejeitado-aviso">Rejeitado pela DGT: remova-o e carregue o ficheiro corrigido.</span>
                }
                @if (ficheiro.justificacaoDesbloqueio) {
                  <span class="ficheiro-meta ficheiro-desbloqueio">{{ rotuloJustificacao }}: {{ ficheiro.justificacaoDesbloqueio }}</span>
                }
              </div>
              <div class="ficheiro-acoes">
                <button
                  type="button"
                  class="acao"
                  [disabled]="emOperacao() === chave"
                  [attr.aria-label]="'Descarregar ' + ficheiro.nomeFicheiro"
                  (click)="descarregar(ficheiro)"
                >
                  Descarregar
                </button>
                @if (acaoItem && !porTag) {
                  <ng-container *ngTemplateOutlet="acaoItem; context: contextoDocumento(ficheiro)" />
                }
                @if (!porTag && podeAlterar(ficheiro)) {
                  @if (desbloqueado(ficheiro)) {
                    @if (substituivel !== false) {
                      <label class="acao">
                        Substituir
                        <input class="ficheiro-input" type="file" [disabled]="emOperacao() === chave" (change)="substituir($event, ficheiro, chave)" />
                      </label>
                    }
                  } @else {
                    <button type="button" class="acao acao-perigo" [disabled]="emOperacao() === chave" (click)="pedirRemocao(ficheiro, chave)">Remover</button>
                  }
                }
                @if (!porTag && podeDesbloquear(ficheiro)) {
                  <button
                    type="button"
                    class="acao"
                    [disabled]="emOperacao() === chave"
                    [attr.aria-label]="'Desbloquear ficheiro ' + ficheiro.nomeFicheiro"
                    (click)="pedirDesbloqueio(ficheiro, chave)"
                  >
                    Desbloquear ficheiro
                  </button>
                }
              </div>
            </li>
          }
        </ul>
      }
      @if (errosItem()[chave]; as mensagem) {
        <p class="erro-item" role="alert">{{ mensagem }}</p>
      }
    </ng-template>

    <ng-template #zona let-chave="chave" let-categoria="categoria" let-documentoConfigId="documentoConfigId" let-extensoes="extensoes">
      @if (editavel) {
        <label class="zona" [class.zona-sobre]="chaveSobre() === chave" (dragover)="sobre($event, chave)" (dragleave)="chaveSobre.set(null)" (drop)="largar($event, chave, categoria, documentoConfigId)">
          <input class="ficheiro-input" type="file" [disabled]="emOperacao() === chave" (change)="escolher($event, chave, categoria, documentoConfigId)" />
          @if (embutido) {
            <span class="zona-icone" aria-hidden="true">
              <svg viewBox="0 0 16 16" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M2.67 10.67v.66a2 2 0 0 0 2 2h6.66a2 2 0 0 0 2-2v-.66M5.33 5.33 8 2.67l2.67 2.66M8 2.67v8" />
              </svg>
            </span>
          }
          <span class="zona-texto">{{ emOperacao() === chave ? 'A carregar…' : 'Escolher ficheiro' }}</span>
          @if (extensoes.length > 0) {
            <span class="zona-dica">Extensões aceites: {{ extensoes.join(', ') }}</span>
          }
        </label>
        @let elegiveis = itensElegiveisTag(documentoConfigId);
        @if (elegiveis.length > 0) {
          <fieldset class="tags">
            <legend class="tags-legenda">Este ficheiro também serve para</legend>
            @for (elegivel of elegiveis; track elegivel.documentoConfigId) {
              <div class="tag">
                <input
                  type="checkbox"
                  class="tag-caixa"
                  [id]="'tag-' + chave + '-' + elegivel.documentoConfigId"
                  [checked]="tagEscolhida(chave, elegivel.documentoConfigId)"
                  [disabled]="emOperacao() === chave"
                  (change)="alternarTag(chave, elegivel.documentoConfigId, $event)"
                />
                <label class="tag-rotulo" [for]="'tag-' + chave + '-' + elegivel.documentoConfigId">{{ elegivel.rotulo }}</label>
              </div>
            }
          </fieldset>
        }
      }
    </ng-template>

    <app-confirmacao-modal
      [aberto]="remocaoPendente() !== null"
      titulo="Remover ficheiro"
      [mensagem]="mensagemRemocao()"
      [aProcessar]="emOperacao() !== null"
      (sim)="confirmarRemocao()"
      (nao)="remocaoPendente.set(null)"
    />

    <app-modal titulo="Desbloquear ficheiro" [aberto]="desbloqueioPendente() !== null" (fechar)="cancelarDesbloqueio()">
      @if (desbloqueioPendente(); as pendente) {
        <div class="desbloqueio">
          <p class="desbloqueio-ficheiro">Ficheiro: <strong>{{ pendente.documento.nomeFicheiro }}</strong></p>
          @if (erroDesbloqueio(); as mensagem) {
            <app-alerta tipo="erro">{{ mensagem }}</app-alerta>
          }
          <label class="rotulo" for="justificacao-desbloqueio">Justificação <span class="obrigatorio" aria-hidden="true">*</span></label>
          <textarea
            id="justificacao-desbloqueio"
            rows="5"
            required
            [attr.maxlength]="maximoJustificacao"
            [value]="justificacao()"
            [disabled]="aDesbloquear()"
            [attr.aria-invalid]="erroJustificacao() ? true : null"
            [attr.aria-describedby]="'justificacao-desbloqueio-ajuda justificacao-desbloqueio-contador' + (erroJustificacao() ? ' justificacao-desbloqueio-erro' : '')"
            (input)="escreverJustificacao($event)"
          ></textarea>
          <div class="desbloqueio-dicas">
            <p class="desbloqueio-ajuda" id="justificacao-desbloqueio-ajuda">A justificação fica visível à DGT na validação.</p>
            <span class="desbloqueio-contador" id="justificacao-desbloqueio-contador">{{ justificacao().length }}/{{ maximoJustificacao }}</span>
          </div>
          @if (erroJustificacao(); as mensagem) {
            <p class="erro-campo" role="alert" id="justificacao-desbloqueio-erro">{{ mensagem }}</p>
          }
        </div>
      }
      <div rodape>
        <button type="button" app-botao variante="secundario" tamanho="pequeno" [desativado]="aDesbloquear()" (click)="cancelarDesbloqueio()">Cancelar</button>
        <button type="button" app-botao tamanho="pequeno" [aCarregar]="aDesbloquear()" (click)="confirmarDesbloqueio()">Confirmar</button>
      </div>
    </app-modal>
  `,
})
export class ProcessoDocumentosComponent implements OnInit, OnChanges {
  private readonly documentoService = inject(DocumentoService);
  protected readonly descargaService = inject(DescargaService);
  private readonly fb = inject(FormBuilder);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  @Input({ required: true }) processoId!: string;
  @Input() editavel = false;
  /**
   * A PCM no processo que lhe foi atribuído só acrescenta ficheiros: cada item mantém a zona de carregamento, sem
   * substituir, remover, reclassificar, desbloquear nem observações (a API recusa-os fora de `EM_SUBMISSAO`).
   */
  @Input() modoAcrescentar = false;
  /** Valores gravados, só para repor o bloco ao retomar ou depois de gravar: não acompanham o que se escreve. */
  @Input() informacaoBase: InformacaoBase | null = null;
  @Input() dataAssinatura: string | null = null;
  /** Erros 400 do `PUT` por `path` (`informacaoBase.…` ou `dataAssinatura`), mostrados junto do campo. */
  @Input() errosCampo: Record<string, string> = {};
  /** Data de caducidade da Dinâmica (`yyyy-MM-dd`): só informa, quem recusa uma deliberação posterior é o servidor. */
  @Input() dataLimiteDinamica: string | null = null;
  /** `camposBloqueados` do processo: desativa os campos `informacaoBase.*` e `dataAssinatura` que a lista traz. */
  @Input() camposBloqueados: string[] = [];
  /** Ação por item, desenhada pelo ecrã: no fim de cada ficheiro e de cada bloco da Informação Base. */
  @Input() acaoItem: TemplateRef<{ $implicit: ContextoAcaoItem }> | null = null;
  @Input() ficheirosRejeitados: ReadonlySet<string> = new Set();
  /** Elementos que o processo tem para rever: os que nenhum separador mostra aparecem à parte, só em leitura. */
  @Input() elementosEsperados: ElementoInformacaoBase[] = [];
  /** No assistente, como no Figma da Elaboração, os acordeões ficam sem os títulos de grupo. */
  @Input() semTitulosDeGrupo = false;
  /** Dentro do cartão do formulário, com o aspeto do UI-009: faixas de secção, caixas sem moldura e o «Validar». */
  @Input() @HostBinding('class.embutido') embutido = false;
  /** Rótulo da justificação de um ficheiro desbloqueado, por baixo do nome; a validação DGT usa o seu. */
  @Input() rotuloJustificacao = 'Desbloqueado';
  /** Com o detalhe, o ecrã mostra os planos ou programas associados nas Dinâmicas que os associam aqui. */
  @Input() processo: ProcessoDetalhe | null = null;
  @Output() readonly associacaoGravada = new EventEmitter<InstrumentoAbrangido[]>();
  /**
   * O `PUT /dados-formulario` substitui tudo o que omite: o ecrã dá o pedido do formulário como está, e o destino
   * grava-se com ele. Sem esta função o grupo do destino fica só de leitura.
   */
  @Input() pedidoRascunho: (() => Omit<AtualizarDadosFormularioRequest, 'versao'> | null) | null = null;
  /** Depois de gravar o destino: a nova `versao` e o destino, para o ecrã atualizar o processo. */
  @Output() readonly destinoGravado = new EventEmitter<{ versao: number; destino: DestinoProcesso }>();

  readonly aGravarDestino = signal(false);
  readonly erroDestino = signal<string | null>(null);
  readonly anuncioDestino = signal('');

  /** O destino gravado neste ecrã, até o processo recebido o trazer. */
  private readonly destinoGravadoAqui = signal<DestinoProcesso | null>(null);

  /** Os Outros têm publicação (US-548), menos num processo só para depósito (US-287), como os itens. */
  outrosComPublicacao(): boolean {
    return (this.destinoGravadoAqui() ?? this.processo?.destino) !== 'SO_DEPOSITO';
  }

  /** Só nos processos cujo formulário admite «só depósito» (o servidor diz-o em `destinoEditavel`) ou já só para depósito. */
  mostraDestino(processo: ProcessoDetalhe): boolean {
    return processo.destinoEditavel || processo.destino === 'SO_DEPOSITO';
  }

  /** O destino fica fixo a partir do primeiro ficheiro: com ficheiros carregados neste ecrã passa a só de leitura. */
  destinoEditavel(processo: ProcessoDetalhe): boolean {
    return this.editavel && !this.modoAcrescentar && processo.destinoEditavel && this.pedidoRascunho !== null && this.semFicheiros();
  }

  /** Grava logo, com a `versao`, e relê a estrutura: os itens de publicação saem ou voltam. */
  gravarDestino(destino: DestinoProcesso): void {
    const processo = this.processo;
    const pedido = this.pedidoRascunho?.();
    if (!processo || !pedido) {
      return;
    }
    this.erroDestino.set(null);
    this.anuncioDestino.set('');
    this.aGravarDestino.set(true);
    // Pedido só quando se grava o destino: os ecrãs de leitura não precisam do serviço dos processos.
    this.injector.get(ProcessoService).guardarRascunho(processo.id, { ...pedido, versao: processo.versao, destino }).subscribe({
      next: (atualizado) => {
        this.aGravarDestino.set(false);
        this.destinoGravadoAqui.set(destino);
        this.destinoGravado.emit({ versao: atualizado.versao, destino });
        this.anuncioDestino.set('Destino gravado.');
        this.recarregar();
      },
      error: (erro: ApiError) => {
        this.aGravarDestino.set(false);
        this.erroDestino.set(
          erro.campos?.find((campo) => campo.path === 'destino')?.erro ?? erro.mensagem ?? MENSAGEM_SEM_RESPOSTA,
        );
      },
    });
  }

  /** Só a Suspensão e as Medidas Preventivas associam aqui; nas Dinâmicas `UM` o Instrumento fixa-se ao iniciá-las. */
  associaInstrumentos(processo: ProcessoDetalhe): boolean {
    return processo.associacaoInstrumentos === 'UM_OU_MAIS' || processo.associacaoInstrumentos === 'OPCIONAL';
  }
  @Output() readonly estruturaAlterada = new EventEmitter<PendenciaDocumental[]>();
  /** Todos os ficheiros da estrutura, a cada leitura. */
  @Output() readonly ficheirosAlterados = new EventEmitter<DocumentoProcesso[]>();
  @Output() readonly informacaoBaseAlterada = new EventEmitter<InformacaoBaseAlterada>();

  readonly chaveOutros = CHAVE_OUTROS;
  readonly chaveOutrosPublicacao = CHAVE_OUTROS_PUBLICACAO;
  readonly chaveAcordeaoOutros = CHAVE_ACORDEAO_OUTROS;
  readonly chavePorElemento = CHAVE_POR_ELEMENTO;
  readonly mensagemBloqueado = MENSAGEM_CAMPO_BLOQUEADO;

  /** Do componente, não do acordeão: o que se escreve sobrevive a fechar e abrir. */
  readonly form = this.fb.group({
    deliberacao: this.fb.group({
      prazoAnos: this.fb.control<number | null>(null),
      prazoMeses: this.fb.control<number | null>(null),
      prazoDias: this.fb.control<number | null>(null),
      avaliacaoAmbiental: this.fb.control<boolean | null>(null),
      dataDeliberacao: [''],
      textoPublicar: [''],
    }),
    textoAto: this.fb.group({
      prazoAnos: this.fb.control<number | null>(null),
      prazoMeses: this.fb.control<number | null>(null),
      prazoDias: this.fb.control<number | null>(null),
      avaliacaoAmbiental: this.fb.control<boolean | null>(null),
    }),
    dataAssinatura: [''],
  });
  readonly valores = signal<InformacaoBaseAlterada>({ informacaoBase: null, dataAssinatura: null });
  /** As regras de coerência só se mostram depois de o utilizador sair de um campo; o servidor volta a aplicá-las na submissão. */
  readonly saiuDeUmCampo = signal(false);
  readonly errosCoerencia = computed(() => errosCoerenciaInformacaoBase(this.valores()));
  /** Um erro do servidor de uma regra de coerência deixa de valer quando o utilizador mexe num dos campos de que ela depende. */
  private readonly errosServidorDescartados = signal<ReadonlySet<string>>(new Set());

  readonly estrutura = signal<EstruturaDocumental | null>(null);
  readonly aCarregar = signal(true);
  readonly erro = signal<ApiError | null>(null);
  readonly emOperacao = signal<string | null>(null);
  readonly errosItem = signal<Record<string, string>>({});
  readonly remocaoPendente = signal<RemocaoPendente | null>(null);
  readonly desbloqueioPendente = signal<RemocaoPendente | null>(null);
  readonly justificacao = signal('');
  readonly erroJustificacao = signal<string | null>(null);
  readonly erroDesbloqueio = signal<string | null>(null);
  readonly aDesbloquear = signal(false);
  readonly maximoJustificacao = MAXIMO_JUSTIFICACAO_DESBLOQUEIO;
  readonly chaveSobre = signal<string | null>(null);
  private readonly fechados = signal<ReadonlySet<string>>(new Set());
  private readonly prazosAbertos = signal<ReadonlySet<string>>(new Set());
  private readonly unidadesEscolhidas = signal<Record<string, CampoPrazo>>({});
  readonly unidadesPrazo: readonly { campo: CampoPrazo; rotulo: string }[] = [
    { campo: 'prazoAnos', rotulo: 'anos' },
    { campo: 'prazoMeses', rotulo: 'meses' },
    { campo: 'prazoDias', rotulo: 'dias' },
  ];
  private separadoresIniciaisFechados = false;
  /** O que se escreve nas Observações de cada separador, até ficar gravado. */
  private readonly rascunhoObservacoes = signal<Record<string, string>>({});
  readonly errosObservacoes = signal<Record<string, string>>({});

  /** Os itens de depósito obrigatório da estrutura, que um ficheiro pode satisfazer por tag (BL-DOC-021). */
  private readonly itensDeDepositoObrigatorio = computed<ItemElegivelTag[]>(() =>
    (this.estrutura()?.separadores ?? []).flatMap((separador) =>
      separador.deposito
        .filter((item) => item.obrigatorio)
        .map((item) => ({
          documentoConfigId: item.documentoConfigId,
          rotulo: `${separador.separador} — ${item.nome}`,
          extensoesAceites: item.extensoesAceites,
        })),
    ),
  );

  private readonly nomesDosItens = computed(() => {
    const nomes = new Map<number, string>();
    for (const separador of this.estrutura()?.separadores ?? []) {
      for (const item of [...separador.publicacao, ...separador.deposito]) {
        nomes.set(item.documentoConfigId, item.nome);
      }
    }
    return nomes;
  });

  /** As tags escolhidas em cada zona de carregamento, para o upload seguinte dessa zona. */
  readonly tagsEscolhidas = signal<Record<string, number[]>>({});

  readonly semFicheiros = computed(() => {
    const estrutura = this.estrutura();
    return estrutura !== null && ficheirosDa(estrutura).length === 0;
  });

  readonly comFicheiros = computed(() => {
    const estrutura = this.estrutura();
    return estrutura !== null && ficheirosDa(estrutura).length > 0;
  });

  readonly mensagemRemocao = computed(() => {
    const pendente = this.remocaoPendente();
    return pendente ? `Remover o ficheiro "${pendente.documento.nomeFicheiro}"? Esta ação não pode ser desfeita.` : '';
  });

  constructor() {
    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      const alterada = this.lerFormulario();
      this.valores.set(alterada);
      this.informacaoBaseAlterada.emit(alterada);
    });
    const { deliberacao, textoAto, dataAssinatura } = this.form.controls;
    const dependencias: [AbstractControl, string][] = [
      ...(['prazoAnos', 'prazoMeses', 'prazoDias'] as const).flatMap((campo): [AbstractControl, string][] => [
        [deliberacao.controls[campo], CAMINHO_ERRO_PRAZO],
        [textoAto.controls[campo], CAMINHO_ERRO_PRAZO],
      ]),
      [deliberacao.controls.avaliacaoAmbiental, CAMINHO_ERRO_AVALIACAO_AMBIENTAL],
      [textoAto.controls.avaliacaoAmbiental, CAMINHO_ERRO_AVALIACAO_AMBIENTAL],
      [deliberacao.controls.dataDeliberacao, CAMINHO_ERRO_DATA_DELIBERACAO],
      [dataAssinatura, CAMINHO_ERRO_DATA_DELIBERACAO],
    ];
    for (const [controlo, caminho] of dependencias) {
      controlo.valueChanges
        .pipe(takeUntilDestroyed())
        .subscribe(() => this.errosServidorDescartados.update((atuais) => new Set([...atuais, caminho])));
    }
  }

  ngOnInit(): void {
    this.recarregar();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if ('informacaoBase' in changes || 'dataAssinatura' in changes) {
      const deliberacao = this.informacaoBase?.deliberacao;
      const textoAto = this.informacaoBase?.textoAto;
      this.form.reset(
        {
          deliberacao: {
            prazoAnos: deliberacao?.prazoAnos ?? null,
            prazoMeses: deliberacao?.prazoMeses ?? null,
            prazoDias: deliberacao?.prazoDias ?? null,
            avaliacaoAmbiental: deliberacao?.avaliacaoAmbiental ?? null,
            dataDeliberacao: deliberacao?.dataDeliberacao ?? '',
            textoPublicar: deliberacao?.textoPublicar ?? '',
          },
          textoAto: {
            prazoAnos: textoAto?.prazoAnos ?? null,
            prazoMeses: textoAto?.prazoMeses ?? null,
            prazoDias: textoAto?.prazoDias ?? null,
            avaliacaoAmbiental: textoAto?.avaliacaoAmbiental ?? null,
          },
          dataAssinatura: this.dataAssinatura ?? '',
        },
        { emitEvent: false },
      );
      this.valores.set(this.lerFormulario());
    }
    if ('camposBloqueados' in changes) {
      this.aplicarBloqueios();
    }
    if ('errosCampo' in changes) {
      this.errosServidorDescartados.set(new Set());
      this.abrirOndeHaErros();
    }
  }

  /** No assistente, um erro do servidor nunca fica escondido num separador ou num bloco da Informação Base fechados. */
  private abrirOndeHaErros(): void {
    const caminhos = Object.keys(this.errosCampo);
    if (!this.embutido || caminhos.length === 0) {
      return;
    }
    const abrir: string[] = [];
    for (const separador of this.estrutura()?.separadores ?? []) {
      const chave = separador.elementoInformacaoBase ? CHAVE_POR_ELEMENTO[separador.elementoInformacaoBase] : null;
      const temErro =
        chave !== null &&
        caminhos.some((caminho) => caminho.startsWith(`informacaoBase.${chave}`) || (chave === 'textoAto' && caminho === 'dataAssinatura'));
      if (temErro) {
        abrir.push('sep-' + separador.separador, 'ib-' + chave);
      }
    }
    if (abrir.length > 0) {
      this.fechados.update((atuais) => new Set([...atuais].filter((chave) => !abrir.includes(chave))));
    }
  }

  bloqueado(elemento: string, campo: string): boolean {
    return this.camposBloqueados.includes(`informacaoBase.${elemento}.${campo}`);
  }

  prazoBloqueado(elemento: string): boolean {
    return ['prazoAnos', 'prazoMeses', 'prazoDias'].some((campo) => this.bloqueado(elemento, campo));
  }

  /** `getRawValue()` continua a levar os campos desativados, por isso o `PUT` reenvia o valor gravado. */
  private aplicarBloqueios(): void {
    const controlos: [string, AbstractControl][] = [['dataAssinatura', this.form.controls.dataAssinatura]];
    for (const elemento of ['deliberacao', 'textoAto'] as const) {
      const grupo = this.form.controls[elemento];
      for (const [campo, controlo] of Object.entries(grupo.controls)) {
        controlos.push([`informacaoBase.${elemento}.${campo}`, controlo]);
      }
    }
    for (const [chave, controlo] of controlos) {
      if (this.camposBloqueados.includes(chave)) {
        controlo.disable({ emitEvent: false });
      } else {
        controlo.enable({ emitEvent: false });
      }
    }
  }

  elementosSemSeparador(): ChaveInformacaoBase[] {
    if (this.editavel) {
      return [];
    }
    const mostrados = new Set(this.estrutura()?.separadores.map((separador) => separador.elementoInformacaoBase));
    return ELEMENTOS_INFORMACAO_BASE.filter((elemento) => this.elementosEsperados.includes(elemento) && !mostrados.has(elemento)).map(
      (elemento) => CHAVE_POR_ELEMENTO[elemento],
    );
  }

  temItensSemFicheiro(separador: SeparadorDocumental): boolean {
    return itensSemFicheiro(separador).length > 0;
  }

  /** Um item obrigatório sem ficheiro só fica completo com o motivo nas observações; os opcionais nunca bloqueiam. */
  observacoesObrigatorias(separador: SeparadorDocumental): boolean {
    return itensSemFicheiro(separador).some((item) => item.obrigatorio);
  }

  /** O Figma (UI-009) tem uma caixa por separador; o backend guarda por item (DB-DOC-004): mostra-se a do primeiro item que as tenha. */
  textoObservacoes(separador: SeparadorDocumental): string {
    return this.rascunhoObservacoes()[separador.separador] ?? (itensSemFicheiro(separador).find((item) => item.observacoes)?.observacoes ?? '');
  }

  textoObservacoesOutros(estrutura: EstruturaDocumental): string {
    return this.rascunhoObservacoes()[CHAVE_OUTROS] ?? estrutura.observacoesOutros ?? '';
  }

  outrosDa(estrutura: EstruturaDocumental, categoria: CategoriaDocumento): DocumentoProcesso[] {
    return estrutura.outros.filter((ficheiro) => ficheiro.categoria === categoria);
  }

  alterarObservacoes(separador: string, evento: Event): void {
    const valor = (evento.target as HTMLTextAreaElement).value;
    this.rascunhoObservacoes.update((atuais) => ({ ...atuais, [separador]: valor }));
  }

  /**
   * Grava o texto de cada separador alterado em todos os itens sem ficheiro desse separador. Emite `false` se algum
   * pedido falhar; o erro fica junto da caixa. Um texto apagado não remove o que já está gravado: a API não o permite.
   */
  gravarObservacoes(): Observable<boolean> {
    if (this.modoAcrescentar) {
      return of(true);
    }
    const pedidos: Observable<boolean>[] = [];
    for (const separador of this.estrutura()?.separadores ?? []) {
      const texto = this.rascunhoObservacoes()[separador.separador]?.trim();
      const itens = itensSemFicheiro(separador).filter((item) => item.observacoes !== texto);
      if (!texto || itens.length === 0) {
        continue;
      }
      pedidos.push(
        forkJoin(itens.map((item) => this.documentoService.gravarObservacoes(this.processoId, item.documentoConfigId, texto))).pipe(
          map(() => {
            this.limparObservacoes(separador.separador);
            return true;
          }),
          catchError((erro: ApiError) => {
            const mensagem = erro.campos?.find((campo) => campo.path === 'observacoes')?.erro ?? erro.mensagem;
            this.errosObservacoes.update((atuais) => ({ ...atuais, [separador.separador]: mensagem }));
            return of(false);
          }),
        ),
      );
    }
    const textoOutros = this.rascunhoObservacoes()[CHAVE_OUTROS];
    if (textoOutros !== undefined && textoOutros.trim() !== (this.estrutura()?.observacoesOutros ?? '')) {
      pedidos.push(
        this.documentoService.gravarObservacoesOutros(this.processoId, textoOutros.trim()).pipe(
          map(() => {
            this.limparObservacoes(CHAVE_OUTROS);
            return true;
          }),
          catchError((erro: ApiError) => {
            this.errosObservacoes.update((atuais) => ({ ...atuais, [CHAVE_OUTROS]: erro.mensagem }));
            return of(false);
          }),
        ),
      );
    }
    this.errosObservacoes.set({});
    return pedidos.length === 0 ? of(true) : forkJoin(pedidos).pipe(map((resultados) => resultados.every(Boolean)));
  }

  private limparObservacoes(separador: string): void {
    this.rascunhoObservacoes.update((atuais) => {
      const restantes = { ...atuais };
      delete restantes[separador];
      return restantes;
    });
  }

  prazoAberto(elemento: string): boolean {
    return this.prazosAbertos().has(elemento);
  }

  alternarPrazo(elemento: string): void {
    this.prazosAbertos.update((atuais) => {
      const proximos = new Set(atuais);
      if (!proximos.delete(elemento)) {
        proximos.add(elemento);
      }
      return proximos;
    });
  }

  fecharPrazo(elemento: string, evento: Event): void {
    if (!this.prazoAberto(elemento)) {
      return;
    }
    evento.stopPropagation();
    this.alternarPrazo(elemento);
    const campo = evento.currentTarget as HTMLElement;
    campo.closest('fieldset')?.querySelector<HTMLElement>('.seletor-prazo')?.focus();
  }

  /** EF: o prazo é um número e uma unidade da lista (anos, meses, dias); as outras unidades ficam vazias. */
  unidadePrazo(elemento: string): CampoPrazo {
    const escolhida = this.unidadesEscolhidas()[elemento];
    if (escolhida) {
      return escolhida;
    }
    const grupo = this.form.get(elemento)?.getRawValue() as Partial<Record<CampoPrazo, number | null>> | undefined;
    return CAMPOS_PRAZO.find((campo) => (grupo?.[campo] ?? 0) > 0) ?? 'prazoAnos';
  }

  valorPrazo(elemento: string): number | null {
    return (this.form.get(elemento)?.get(this.unidadePrazo(elemento))?.value as number | null | undefined) ?? null;
  }

  alterarValorPrazo(elemento: string, evento: Event): void {
    const texto = (evento.target as HTMLInputElement).value;
    this.aplicarPrazo(elemento, this.unidadePrazo(elemento), texto === '' ? null : Number(texto));
  }

  alterarUnidadePrazo(elemento: string, evento: Event): void {
    const unidade = (evento.target as HTMLSelectElement).value as CampoPrazo;
    const valor = this.valorPrazo(elemento);
    this.unidadesEscolhidas.update((atuais) => ({ ...atuais, [elemento]: unidade }));
    this.aplicarPrazo(elemento, unidade, valor);
  }

  private aplicarPrazo(elemento: string, unidade: CampoPrazo, valor: number | null): void {
    const grupo = this.form.get(elemento);
    grupo?.patchValue({ prazoAnos: null, prazoMeses: null, prazoDias: null, [unidade]: valor });
    grupo?.markAsDirty();
  }

  temPrazo(elemento: string): boolean {
    return this.prazoLegivel(this.dadosDoElemento(elemento)) !== '—';
  }

  /** O que o seletor mostra fechado: o prazo escrito, ou as unidades do Figma enquanto está vazio. */
  resumoPrazo(elemento: string): string {
    return this.temPrazo(elemento) ? this.prazoLegivel(this.dadosDoElemento(elemento)) : 'anos, meses, dias';
  }

  rotulo(chave: ChaveInformacaoBase): string {
    return ROTULO_POR_ELEMENTO[ELEMENTO_POR_CHAVE[chave]];
  }

  errosPrazo(elemento: string): string[] {
    const mensagens = ['prazoAnos', 'prazoMeses', 'prazoDias']
      .map((campo) => this.erroCampo(`informacaoBase.${elemento}.${campo}`))
      .filter((mensagem): mensagem is string => !!mensagem);
    return [...new Set(mensagens)];
  }

  /** O erro do servidor primeiro; sem ele, o da regra de coerência calculada no ecrã. */
  erroCampo(caminho: string): string | null {
    const doServidor = this.errosServidorDescartados().has(caminho) ? undefined : this.errosCampo[caminho];
    const daCoerencia = this.saiuDeUmCampo() ? this.errosCoerencia()[caminho] : undefined;
    return doServidor ?? daCoerencia ?? null;
  }

  descricaoDataDeliberacao(): string | null {
    const ids = [
      this.dataLimiteDinamica ? 'ajuda-data-deliberacao' : null,
      this.bloqueado('deliberacao', 'dataDeliberacao') ? 'bloqueado-data-deliberacao' : null,
    ].filter((id) => id !== null);
    return ids.length > 0 ? ids.join(' ') : null;
  }

  dadosDoElemento(elemento: string): InformacaoBaseTextoAto | InformacaoBaseDeliberacao | null {
    const informacaoBase = this.valores().informacaoBase;
    return (elemento === 'deliberacao' ? informacaoBase?.deliberacao : informacaoBase?.textoAto) ?? null;
  }

  prazoLegivel(dados: InformacaoBaseTextoAto | null): string {
    const partes = [
      dados?.prazoAnos ? `${dados.prazoAnos} ${dados.prazoAnos === 1 ? 'ano' : 'anos'}` : null,
      dados?.prazoMeses ? `${dados.prazoMeses} ${dados.prazoMeses === 1 ? 'mês' : 'meses'}` : null,
      dados?.prazoDias ? `${dados.prazoDias} ${dados.prazoDias === 1 ? 'dia' : 'dias'}` : null,
    ].filter((parte) => parte !== null);
    return partes.length > 0 ? partes.join(', ') : '—';
  }

  simNao(valor: boolean | null | undefined): string {
    return valor === null || valor === undefined ? '—' : valor ? 'Sim' : 'Não';
  }

  /** Campo vazio vale `null`; um elemento sem nenhum valor não se envia, e sem elementos a Informação Base fica nula. */
  private lerFormulario(): InformacaoBaseAlterada {
    const valores = this.form.getRawValue();
    const deliberacao: InformacaoBaseDeliberacao = {
      ...valores.deliberacao,
      dataDeliberacao: valores.deliberacao.dataDeliberacao || null,
      textoPublicar: valores.deliberacao.textoPublicar || null,
    };
    const textoAto: InformacaoBaseTextoAto = { ...valores.textoAto };
    const semDeliberacao = semValores(deliberacao);
    const semTextoAto = semValores(textoAto);
    return {
      informacaoBase:
        semDeliberacao && semTextoAto
          ? null
          : { deliberacao: semDeliberacao ? null : deliberacao, textoAto: semTextoAto ? null : textoAto },
      dataAssinatura: valores.dataAssinatura || null,
    };
  }

  chaveItem(item: ItemDocumental): string {
    return `item-${item.documentoConfigId}`;
  }

  contextoDocumento(documento: DocumentoProcesso): { $implicit: ContextoAcaoItem } {
    return { $implicit: { tipo: 'DOCUMENTO', documento } };
  }

  contextoInformacaoBase(chave: ChaveInformacaoBase): { $implicit: ContextoAcaoItem } {
    return { $implicit: { tipo: 'INFORMACAO_BASE', elemento: ELEMENTO_POR_CHAVE[chave] } };
  }

  aberto(chave: string): boolean {
    return !this.fechados().has(chave);
  }

  alternar(chave: string): void {
    this.fechados.update((atuais) => {
      const proximos = new Set(atuais);
      if (!proximos.delete(chave)) {
        proximos.add(chave);
      }
      return proximos;
    });
  }

  podeAlterar(ficheiro: DocumentoProcesso): boolean {
    return this.editavel && !this.modoAcrescentar && !ficheiro.bloqueadoEdicao;
  }

  podeDesbloquear(ficheiro: DocumentoProcesso): boolean {
    return this.editavel && !this.modoAcrescentar && ficheiro.bloqueadoEdicao;
  }

  /** Na correção, só os rejeitados pela DGT ficam por corrigir: a submissão recusa-os enquanto lá estiverem. */
  rejeitadoPorCorrigir(ficheiro: DocumentoProcesso): boolean {
    return this.editavel && ficheiro.estadoValidacao === 'REJEITADO';
  }

  /** Um validado desbloqueado na correção substitui-se, não se remove. */
  desbloqueado(ficheiro: DocumentoProcesso): boolean {
    return !ficheiro.bloqueadoEdicao && ficheiro.justificacaoDesbloqueio !== null && ficheiro.estadoValidacao === 'VALIDADO';
  }

  pedirDesbloqueio(documento: DocumentoProcesso, chave: string): void {
    this.justificacao.set('');
    this.erroJustificacao.set(null);
    this.erroDesbloqueio.set(null);
    this.desbloqueioPendente.set({ documento, chave });
  }

  escreverJustificacao(evento: Event): void {
    this.justificacao.set((evento.target as HTMLTextAreaElement).value);
    this.erroJustificacao.set(null);
  }

  cancelarDesbloqueio(): void {
    if (!this.aDesbloquear()) {
      this.desbloqueioPendente.set(null);
    }
  }

  confirmarDesbloqueio(): void {
    const pendente = this.desbloqueioPendente();
    if (!pendente || this.aDesbloquear()) {
      return;
    }
    const texto = this.justificacao().trim();
    if (texto === '') {
      this.erroJustificacao.set(MENSAGEM_JUSTIFICACAO_EM_FALTA);
      return;
    }
    this.aDesbloquear.set(true);
    this.erroDesbloqueio.set(null);
    this.documentoService.desbloquear(this.processoId, pendente.documento.id, texto).subscribe({
      next: () => {
        this.aDesbloquear.set(false);
        this.desbloqueioPendente.set(null);
        this.recarregar();
      },
      error: (erro: ApiError) => {
        this.aDesbloquear.set(false);
        if (CODIGOS_ERRO_DA_JUSTIFICACAO.includes(erro.codigo)) {
          this.erroJustificacao.set(erro.campos?.find((campo) => campo.path === 'justificacao')?.erro ?? erro.mensagem);
          return;
        }
        this.erroDesbloqueio.set(erro.codigo === 'ERRO_DESCONHECIDO' ? MENSAGEM_SEM_RESPOSTA : erro.mensagem);
        if (erro.codigo === 'DOC_ESTADO_INVALIDO') {
          this.recarregar();
        }
      },
    });
  }

  /**
   * EF v1.5, Recolha de ficheiros: só um ficheiro para depósito obrigatório tem mais do que um tag. Oferecem-se os
   * outros itens de depósito obrigatório que aceitam pelo menos uma das extensões do item de origem.
   */
  itensElegiveisTag(documentoConfigId?: number): ItemElegivelTag[] {
    const itens = this.itensDeDepositoObrigatorio();
    const origem = itens.find((item) => item.documentoConfigId === documentoConfigId);
    if (!origem) {
      return [];
    }
    return itens.filter(
      (item) =>
        item.documentoConfigId !== documentoConfigId &&
        item.extensoesAceites.some((extensao) => origem.extensoesAceites.includes(extensao)),
    );
  }

  tagEscolhida(chave: string, documentoConfigId: number): boolean {
    return this.tagsEscolhidas()[chave]?.includes(documentoConfigId) ?? false;
  }

  alternarTag(chave: string, documentoConfigId: number, evento: Event): void {
    const escolhida = (evento.target as HTMLInputElement).checked;
    this.tagsEscolhidas.update((atuais) => {
      const daZona = (atuais[chave] ?? []).filter((id) => id !== documentoConfigId);
      return { ...atuais, [chave]: escolhida ? [...daZona, documentoConfigId] : daZona };
    });
  }

  private limparTags(chave: string): void {
    this.tagsEscolhidas.update((atuais) => {
      const restantes = { ...atuais };
      delete restantes[chave];
      return restantes;
    });
  }

  /** Num item, um ficheiro carregado noutro item (ou no balde «outros») só lá está por tag. */
  associadoPorTag(ficheiro: DocumentoProcesso, itemId: number | undefined): boolean {
    return itemId !== undefined && ficheiro.documentoConfigId !== itemId;
  }

  nomeDoItemPrincipal(ficheiro: DocumentoProcesso): string {
    return ficheiro.documentoConfigId === null ? 'Outros' : (this.nomesDosItens().get(ficheiro.documentoConfigId) ?? 'Outros');
  }

  /** Só no ecrã editável: é o aviso que ocupa o lugar da zona de carregar. */
  avisoGeoconform(item: ItemDocumental): boolean {
    return this.editavel && item.obtidoDoGeoconform;
  }

  sobre(evento: DragEvent, chave: string): void {
    evento.preventDefault();
    this.chaveSobre.set(chave);
  }

  largar(evento: DragEvent, chave: string, categoria: CategoriaDocumento, documentoConfigId?: number): void {
    evento.preventDefault();
    this.chaveSobre.set(null);
    const ficheiro = evento.dataTransfer?.files?.[0];
    if (ficheiro) {
      this.carregar(chave, ficheiro, categoria, documentoConfigId);
    }
  }

  escolher(evento: Event, chave: string, categoria: CategoriaDocumento, documentoConfigId?: number): void {
    const ficheiro = this.ficheiroEscolhido(evento);
    if (ficheiro) {
      this.carregar(chave, ficheiro, categoria, documentoConfigId);
    }
  }

  substituir(evento: Event, documento: DocumentoProcesso, chave: string): void {
    const ficheiro = this.ficheiroEscolhido(evento);
    if (!ficheiro) {
      return;
    }
    this.iniciarOperacao(chave);
    this.documentoService.substituir(this.processoId, documento.id, ficheiro).subscribe({
      next: () => this.concluirOperacao(),
      error: (erro: ApiError) => this.falharOperacao(chave, erro),
    });
  }

  pedirRemocao(documento: DocumentoProcesso, chave: string): void {
    this.remocaoPendente.set({ documento, chave });
  }

  confirmarRemocao(): void {
    const pendente = this.remocaoPendente();
    if (!pendente) {
      return;
    }
    this.iniciarOperacao(pendente.chave);
    this.documentoService.remover(this.processoId, pendente.documento.id).subscribe({
      next: () => {
        this.remocaoPendente.set(null);
        this.concluirOperacao();
      },
      error: (erro: ApiError) => {
        this.remocaoPendente.set(null);
        this.falharOperacao(pendente.chave, erro);
      },
    });
  }

  descarregar(documento: DocumentoProcesso): void {
    this.descargaService.descarregar(this.processoId, documento.id);
  }

  private carregar(chave: string, ficheiro: File, categoria: CategoriaDocumento, documentoConfigId?: number): void {
    if (!this.editavel) {
      return;
    }
    const tags = this.tagsEscolhidas()[chave] ?? [];
    this.iniciarOperacao(chave);
    this.documentoService.carregar(this.processoId, ficheiro, categoria, documentoConfigId, tags).subscribe({
      next: () => {
        this.limparTags(chave);
        this.concluirOperacao();
      },
      error: (erro: ApiError) => {
        this.limparTags(chave);
        this.falharOperacao(chave, erro);
      },
    });
  }

  private ficheiroEscolhido(evento: Event): File | undefined {
    const campo = evento.target as HTMLInputElement;
    const ficheiro = campo.files?.[0];
    // Permite escolher de novo o mesmo ficheiro depois de um erro.
    campo.value = '';
    return ficheiro;
  }

  private iniciarOperacao(chave: string): void {
    this.emOperacao.set(chave);
    this.erro.set(null);
    this.errosItem.update((atuais) => {
      const restantes = { ...atuais };
      delete restantes[chave];
      return restantes;
    });
  }

  private concluirOperacao(): void {
    this.emOperacao.set(null);
    this.recarregar();
  }

  /**
   * 400 com campos fica no item e a lista não muda; 409 `DOC_CANAL_GEOCONFORM` também fica no item, mas relê a
   * estrutura para o item passar a mostrar o aviso; os outros 409 e as restantes falhas vão para o banner, e a
   * estrutura é relida nos estados.
   */
  private falharOperacao(chave: string, erro: ApiError): void {
    this.emOperacao.set(null);
    if (CODIGOS_ERRO_DE_FICHEIRO.includes(erro.codigo)) {
      // As recusas de classificação vêm em `categoria` ou `tags`, não em `ficheiro`.
      const mensagem = erro.campos?.find((campo) => campo.path === 'ficheiro')?.erro ?? erro.campos?.[0]?.erro ?? erro.mensagem;
      this.errosItem.update((atuais) => ({ ...atuais, [chave]: mensagem }));
      if (erro.codigo === 'DOC_CANAL_GEOCONFORM') {
        this.recarregar();
      }
      return;
    }
    this.erro.set(
      erro.codigo === 'ERRO_DESCONHECIDO'
        ? new ApiError(erro.codigo, MENSAGEM_SEM_RESPOSTA, erro.traceId, erro.campos)
        : erro,
    );
    // O banner fica no topo da lista, longe do item onde se clicou.
    afterNextRender(() => this.host.nativeElement.querySelector('app-error-banner [role="alert"]')?.scrollIntoView?.({ block: 'nearest' }), {
      injector: this.injector,
    });
    if (erro.codigo === 'ESTADO_INVALIDO' || erro.codigo === 'DOC_ESTADO_INVALIDO' || erro.codigo === 'DOC_BLOQUEADO') {
      this.recarregar();
    }
  }

  /** No formulário (UI-009) só o primeiro separador abre; os restantes e «Outros» começam fechados. Só na primeira leitura com separadores. */
  private fecharSeparadoresIniciais(estrutura: EstruturaDocumental): void {
    if (!this.embutido || this.separadoresIniciaisFechados || estrutura.separadores.length === 0) {
      return;
    }
    this.separadoresIniciaisFechados = true;
    const fechar = [...estrutura.separadores.slice(1).map((separador) => 'sep-' + separador.separador), CHAVE_ACORDEAO_OUTROS];
    this.fechados.update((atuais) => new Set([...atuais, ...fechar]));
  }

  tentarNovamente(): void {
    this.erro.set(null);
    this.aCarregar.set(true);
    this.recarregar();
  }

  /** O ecrã chama-o depois de gravar: os elementos afetados do formulário mudam os itens da estrutura. */
  recarregar(): void {
    this.documentoService.estrutura(this.processoId).subscribe({
      next: (estrutura) => {
        this.fecharSeparadoresIniciais(estrutura);
        this.estrutura.set(estrutura);
        this.abrirOndeHaErros();
        this.aCarregar.set(false);
        this.estruturaAlterada.emit(estrutura.pendencias);
        this.ficheirosAlterados.emit(ficheirosDa(estrutura));
      },
      error: (erro: ApiError) => {
        this.erro.set(erro);
        this.aCarregar.set(false);
      },
    });
  }
}
