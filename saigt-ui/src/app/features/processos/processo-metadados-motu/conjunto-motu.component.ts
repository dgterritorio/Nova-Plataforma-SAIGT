import { NgTemplateOutlet } from '@angular/common';
import { Component, Input, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Observable, catchError, map, of } from 'rxjs';
import { ApiError } from '../../../core/models/error-response.model';
import {
  ConjuntoMotuDados,
  FolhaConjuntoMotu,
  GravarConjuntoMotu,
  RegistoMotu,
  TipoCartografiaMotu,
} from '../../../models/plantas.model';
import { PlantasService } from '../../../services/plantas.service';
import { ConfirmacaoModalComponent } from '../../../shared/components/confirmacao-modal.component';
import { BotaoComponent, SeloComponent } from '../../../shared/ui';

type CampoConjunto = keyof ConjuntoMotuDados;

interface CampoFicha {
  campo: CampoConjunto;
  rotulo: string;
  obrigatorio: boolean;
  tipo?: 'area' | 'data';
  /** O `maxLength` do contrato; sem ele nas datas. */
  limite?: number;
}

const TIPOS_CARTOGRAFIA: { valor: TipoCartografiaMotu; rotulo: string }[] = [
  { valor: 'SERIE_CARTOGRAFICA_OFICIAL', rotulo: 'Série Cartográfica Oficial' },
  { valor: 'CARTOGRAFIA_HOMOLOGADA', rotulo: 'Cartografia Homologada' },
  { valor: 'OUTRO', rotulo: 'Outro' },
];

const CAMPOS_IDENTIFICACAO: CampoFicha[] = [
  { campo: 'tituloAlternativo', rotulo: 'Título Alternativo', obrigatorio: true, limite: 300 },
  { campo: 'resumo', rotulo: 'Resumo', obrigatorio: true, tipo: 'area', limite: 4000 },
  { campo: 'palavrasChave', rotulo: 'Palavras-chave', obrigatorio: true, limite: 500 },
  { campo: 'denominadorEscala', rotulo: 'Denominador da Escala', obrigatorio: true, limite: 50 },
];
const CAMPOS_SISTEMA: CampoFicha[] = [
  { campo: 'sistemaReferencia', rotulo: 'Sistema de Referência', obrigatorio: true, limite: 200 },
  { campo: 'codigoSistema', rotulo: 'Código Sistema', obrigatorio: false, limite: 50 },
];
const CAMPOS_CARTOGRAFIA: CampoFicha[] = [
  { campo: 'dataEdicao', rotulo: 'Data de Edição', obrigatorio: true, tipo: 'data' },
  { campo: 'dataAtualizacao', rotulo: 'Data de Atualização', obrigatorio: false, tipo: 'data' },
  { campo: 'atualizacaoComplemento', rotulo: 'Complemento da Atualização', obrigatorio: true, limite: 300 },
  { campo: 'entidadeProprietaria', rotulo: 'Entidade Proprietária', obrigatorio: true, limite: 300 },
  { campo: 'entidadeProdutora', rotulo: 'Entidade Produtora', obrigatorio: true, limite: 300 },
  { campo: 'sistemaReferenciaNome', rotulo: 'Sistema de Referência (Nome)', obrigatorio: true, limite: 200 },
  { campo: 'sistemaReferenciaCodigo', rotulo: 'Sistema de Referência (Código)', obrigatorio: true, limite: 50 },
  { campo: 'exatidaoPosicional', rotulo: 'Exatidão Posicional', obrigatorio: true, limite: 100 },
  { campo: 'exatidaoTematica', rotulo: 'Exatidão Temática', obrigatorio: true, limite: 100 },
];
const CAMPOS_OFICIAL: CampoFicha[] = [
  { campo: 'serieCartograficaOficial', rotulo: 'Série Cartográfica Oficial', obrigatorio: true, limite: 200 },
];
const CAMPOS_HOMOLOGADA: CampoFicha[] = [
  { campo: 'numeroHomologacao', rotulo: 'N.º da Homologação', obrigatorio: true, limite: 100 },
  { campo: 'dataHomologacao', rotulo: 'Data da Homologação', obrigatorio: true, tipo: 'data' },
  { campo: 'entidadeHomologacao', rotulo: 'Entidade Responsável pela Homologação', obrigatorio: true, limite: 300 },
];

const TODOS_OS_CAMPOS: CampoConjunto[] = [
  ...CAMPOS_IDENTIFICACAO,
  ...CAMPOS_SISTEMA,
  ...CAMPOS_CARTOGRAFIA,
  ...CAMPOS_OFICIAL,
  ...CAMPOS_HOMOLOGADA,
].map((campo) => campo.campo);

/** Peça gráfica do processo que se pode ligar a uma ficha opcional. */
export interface PecaGrafica {
  id: string;
  nome: string;
}

interface FolhaEditavel {
  id: string;
  nomeRegulamento: string;
  numeroFolha: string;
  nomeTiff: string | null;
}

/**
 * Conjunto de Dados Geográficos de uma peça gráfica de publicação (EF v1.5 §3.6.2; cartões do UI-053) com a tabela
 * «Plantas SAIGT SNIT» das suas folhas (EF §3.1). Grava-se como rascunho com `gravar()`; «Completo» vem da API.
 */
@Component({
  selector: 'app-conjunto-motu',
  standalone: true,
  imports: [ReactiveFormsModule, NgTemplateOutlet, SeloComponent, BotaoComponent, ConfirmacaoModalComponent],
  styleUrls: ['../processo-metadados/processo-metadados.component.css', './conjunto-motu.component.css'],
  template: `
    <div class="bloco-conjunto">
      <form class="cartoes" [formGroup]="form">
        <div class="cabecalho-conjunto">
          <h2 class="titulo-conjunto">{{ registoAtual().titulo || 'Conjunto de Dados Geográficos' }}</h2>
          @if (registoAtual().opcional) {
            <app-selo class="etiqueta-opcional" tom="info" forma="pill">Opcional</app-selo>
          }
          <span class="zip-conjunto">{{ registoAtual().zip?.nome }}</span>
          <app-selo class="estado-conjunto" [tom]="registoAtual().completo ? 'sucesso' : 'aviso'" forma="pill">
            {{ registoAtual().completo ? 'Completo' : 'Incompleto' }}
          </app-selo>
          @if (registoAtual().opcional && editavel) {
            <button type="button" app-botao variante="perigo" tamanho="pequeno" class="remover-ficha"
              [attr.aria-label]="rotuloRemover()"
              (click)="pedirRemocao($event)">
              Remover ficha
            </button>
          }
        </div>
        @if (erroGeral(); as mensagem) {
          <p class="erro erro-geral" role="alert">{{ mensagem }}</p>
        }

        <section class="cartao" [attr.aria-labelledby]="idBase + '-titulo-identificacao'">
          <h3 class="titulo" [id]="idBase + '-titulo-identificacao'">Identificação</h3>
          <div class="grelha">
            <dl class="leitura-simples coluna-inteira">
              <div class="par">
                <dt>Título</dt>
                <dd>{{ registoAtual().titulo || '—' }}</dd>
              </div>
              @if (!(registoAtual().opcional && editavel)) {
                <div class="par">
                  <dt>ZIP</dt>
                  <dd>{{ registoAtual().zip?.nome || '—' }}</dd>
                </div>
              }
            </dl>
            @if (registoAtual().opcional && editavel) {
              <div class="campo coluna-inteira">
                <label class="rotulo" [for]="idBase + '-processoDocumentoId'">ZIP da peça gráfica</label>
                <select
                  [id]="idBase + '-processoDocumentoId'"
                  class="entrada"
                  formControlName="processoDocumentoId"
                  [attr.aria-invalid]="erros()['processoDocumentoId'] ? true : null"
                  [attr.aria-describedby]="idBase + '-ajuda-zip' + (erros()['processoDocumentoId'] ? ' ' + idBase + '-erro-processoDocumentoId' : '')"
                >
                  <option value="">Sem ZIP</option>
                  @for (peca of opcoesZip(); track peca.id) {
                    <option [value]="peca.id">{{ peca.nome }}</option>
                  }
                </select>
                <span class="ajuda-campo" [id]="idBase + '-ajuda-zip'">Só aparecem as peças gráficas que ainda não têm ficha.</span>
                @if (erros()['processoDocumentoId']; as mensagem) {
                  <span class="erro" [id]="idBase + '-erro-processoDocumentoId'">{{ mensagem }}</span>
                }
              </div>
            }
            @for (c of camposIdentificacao; track c.campo) {
              <ng-container *ngTemplateOutlet="campo; context: { $implicit: c }" />
            }
          </div>
        </section>

        <section class="cartao" [attr.aria-labelledby]="idBase + '-titulo-sistema'">
          <h3 class="titulo" [id]="idBase + '-titulo-sistema'">Sistema de Referência</h3>
          <div class="grelha">
            @for (c of camposSistema; track c.campo) {
              <ng-container *ngTemplateOutlet="campo; context: { $implicit: c }" />
            }
          </div>
        </section>

        <section class="cartao" [attr.aria-labelledby]="idBase + '-titulo-cartografia'">
          <h3 class="titulo" [id]="idBase + '-titulo-cartografia'">Cartografia de Referência</h3>
          <div class="grelha">
            <div class="campo">
              <label class="rotulo" [for]="idBase + '-cartografiaTipo'">
                Tipo de Cartografia
                <span class="obrigatorio" aria-hidden="true">*</span>
              </label>
              <select
                [id]="idBase + '-cartografiaTipo'"
                class="entrada"
                formControlName="cartografiaTipo"
                aria-required="true"
                [attr.aria-invalid]="erros()['cartografiaTipo'] ? true : null"
                [attr.aria-describedby]="erros()['cartografiaTipo'] ? idBase + '-erro-cartografiaTipo' : null"
              >
                <option value="">Selecione…</option>
                @for (t of tipos; track t.valor) {
                  <option [value]="t.valor">{{ t.rotulo }}</option>
                }
              </select>
              @if (erros()['cartografiaTipo']; as mensagem) {
                <span class="erro" [id]="idBase + '-erro-cartografiaTipo'">{{ mensagem }}</span>
              }
            </div>
            @for (c of camposCartografia; track c.campo) {
              <ng-container *ngTemplateOutlet="campo; context: { $implicit: c }" />
            }
            @if (tipo() === 'SERIE_CARTOGRAFICA_OFICIAL') {
              @for (c of camposOficial; track c.campo) {
                <ng-container *ngTemplateOutlet="campo; context: { $implicit: c }" />
              }
            }
            @if (tipo() === 'CARTOGRAFIA_HOMOLOGADA') {
              @for (c of camposHomologada; track c.campo) {
                <ng-container *ngTemplateOutlet="campo; context: { $implicit: c }" />
              }
            }
          </div>
        </section>
      </form>

      <section class="cartao cartao-plantas" [attr.aria-labelledby]="idBase + '-titulo-plantas'">
        <div class="cabecalho-plantas">
          <h3 class="titulo" [id]="idBase + '-titulo-plantas'">Plantas SAIGT SNIT</h3>
          @if (editavel && folhas().length > 0 && !emEdicao()) {
            <button type="button" class="editar" (click)="editar()">Editar</button>
          }
        </div>
        @if (folhas().length === 0) {
          <p class="estado sem-folhas">Sem folhas associadas: associe as folhas na página das Plantas</p>
        } @else {
          <table class="tabela">
            <thead>
              <tr>
                <th scope="col">Nome Regulamento <span class="obrigatorio" aria-hidden="true">*</span></th>
                <th scope="col" class="coluna-folha">Folha N <span class="obrigatorio" aria-hidden="true">*</span></th>
                <th scope="col">TIFF</th>
              </tr>
            </thead>
            <tbody>
              @for (folha of folhas(); track folha.id; let i = $index) {
                <tr>
                  <td>
                    @if (emEdicao()) {
                      <input
                        class="entrada"
                        type="text"
                        maxlength="500"
                        [value]="folha.nomeRegulamento"
                        aria-required="true"
                        [attr.aria-label]="'Nome Regulamento da folha ' + (i + 1)"
                        [attr.aria-invalid]="erros()['plantas[' + i + '].nomeRegulamento'] ? true : null"
                        (input)="alterarFolha(i, 'nomeRegulamento', $event)"
                      />
                      @if (erros()['plantas[' + i + '].nomeRegulamento']; as mensagem) {
                        <span class="erro">{{ mensagem }}</span>
                      }
                    } @else {
                      {{ folha.nomeRegulamento || '—' }}
                    }
                  </td>
                  <td class="coluna-folha">
                    @if (emEdicao()) {
                      <input
                        class="entrada"
                        type="text"
                        maxlength="20"
                        [value]="folha.numeroFolha"
                        aria-required="true"
                        [attr.aria-label]="'Folha N da folha ' + (i + 1)"
                        [attr.aria-invalid]="erros()['plantas[' + i + '].numeroFolha'] ? true : null"
                        (input)="alterarFolha(i, 'numeroFolha', $event)"
                      />
                      @if (erros()['plantas[' + i + '].numeroFolha']; as mensagem) {
                        <span class="erro">{{ mensagem }}</span>
                      }
                    } @else {
                      {{ folha.numeroFolha || '—' }}
                    }
                  </td>
                  <td>{{ folha.nomeTiff || '—' }}</td>
                </tr>
              }
            </tbody>
          </table>
        }
      </section>

      <app-confirmacao-modal
        [aberto]="aConfirmarRemocao()"
        titulo="Remover esta ficha de Conjunto?"
        mensagem="A ficha opcional e os dados preenchidos são removidos. O ZIP não é apagado."
        rotuloSim="Remover ficha"
        rotuloNao="Cancelar"
        [aProcessar]="aRemover()"
        (sim)="remover()"
        (nao)="cancelarRemocao()"
      />

      <ng-template #campo let-c>
        <div class="campo" [formGroup]="form">
          <label class="rotulo" [for]="idBase + '-' + c.campo">
            {{ c.rotulo }}
            @if (c.obrigatorio) {
              <span class="obrigatorio" aria-hidden="true">*</span>
            }
          </label>
          @if (c.tipo === 'area') {
            <textarea
              [id]="idBase + '-' + c.campo"
              [attr.maxlength]="c.limite"
              class="entrada area"
              [formControlName]="c.campo"
              [attr.aria-required]="c.obrigatorio || null"
              [attr.aria-invalid]="erros()[c.campo] ? true : null"
              [attr.aria-describedby]="erros()[c.campo] ? idBase + '-erro-' + c.campo : null"
            ></textarea>
          } @else {
            <input
              [id]="idBase + '-' + c.campo"
              [attr.maxlength]="c.limite ?? null"
              class="entrada"
              [class.data]="c.tipo === 'data'"
              [type]="c.tipo === 'data' ? 'date' : 'text'"
              [formControlName]="c.campo"
              [attr.aria-required]="c.obrigatorio || null"
              [attr.aria-invalid]="erros()[c.campo] ? true : null"
              [attr.aria-describedby]="erros()[c.campo] ? idBase + '-erro-' + c.campo : null"
            />
          }
          @if (erros()[c.campo]; as mensagem) {
            <span class="erro" [id]="idBase + '-erro-' + c.campo">{{ mensagem }}</span>
          }
        </div>
      </ng-template>
    </div>
  `,
})
export class ConjuntoMotuComponent implements OnInit {
  private readonly plantasService = inject(PlantasService);
  private readonly fb = inject(FormBuilder);

  @Input({ required: true }) processoId!: string;
  @Input({ required: true }) registo!: RegistoMotu;
  @Input() editavel = true;
  /** Peças gráficas do processo ainda sem ficha, que se podem ligar a uma ficha opcional. */
  readonly pecasLivres = input<PecaGrafica[]>([]);

  readonly removida = output<string>();
  readonly gravada = output<RegistoMotu>();

  readonly tipos = TIPOS_CARTOGRAFIA;
  readonly camposIdentificacao = CAMPOS_IDENTIFICACAO;
  readonly camposSistema = CAMPOS_SISTEMA;
  readonly camposCartografia = CAMPOS_CARTOGRAFIA;
  readonly camposOficial = CAMPOS_OFICIAL;
  readonly camposHomologada = CAMPOS_HOMOLOGADA;

  readonly registoAtual = signal<RegistoMotu>({} as RegistoMotu);
  readonly tipo = signal<TipoCartografiaMotu | ''>('');
  readonly folhas = signal<FolhaEditavel[]>([]);
  readonly emEdicao = signal(false);
  readonly erros = signal<Record<string, string>>({});
  readonly erroGeral = signal<string | null>(null);
  readonly aConfirmarRemocao = signal(false);
  readonly aRemover = signal(false);
  /** A peça já ligada a esta ficha, se houver, e as peças livres. */
  readonly opcoesZip = computed(() => {
    const zip = this.registoAtual().zip;
    const livres = this.pecasLivres().filter((peca) => peca.id !== zip?.id);
    return zip ? [{ id: zip.id, nome: zip.nome }, ...livres] : livres;
  });
  /** Com várias opcionais, diz a quem usa leitor de ecrã qual das fichas se remove. */
  readonly rotuloRemover = computed(() => {
    const registo = this.registoAtual();
    const zip = registo.zip?.nome;
    return `Remover ficha ${registo.titulo || 'de Conjunto'}${zip ? ` (${zip})` : ' sem ZIP'}`;
  });
  private abridorRemocao: HTMLElement | null = null;
  private readonly folhasAlteradas = signal(false);

  readonly form = this.fb.nonNullable.group({
    tituloAlternativo: [''],
    resumo: [''],
    palavrasChave: [''],
    denominadorEscala: [''],
    sistemaReferencia: [''],
    codigoSistema: [''],
    cartografiaTipo: [''],
    dataEdicao: [''],
    dataAtualizacao: [''],
    atualizacaoComplemento: [''],
    entidadeProprietaria: [''],
    entidadeProdutora: [''],
    sistemaReferenciaNome: [''],
    sistemaReferenciaCodigo: [''],
    exatidaoPosicional: [''],
    exatidaoTematica: [''],
    processoDocumentoId: [''],
    serieCartograficaOficial: [''],
    numeroHomologacao: [''],
    dataHomologacao: [''],
    entidadeHomologacao: [''],
  });

  get idBase(): string {
    return `cdg-${this.registo.id}`;
  }

  ngOnInit(): void {
    if (!this.editavel) {
      this.form.disable();
    }
    this.aplicar(this.registo);
    this.form.controls.cartografiaTipo.valueChanges.subscribe((valor) => {
      this.tipo.set(valor as TipoCartografiaMotu | '');
      // Os campos de um tipo só existem com esse tipo: ao mudar, os do anterior saem.
      if (valor !== 'SERIE_CARTOGRAFICA_OFICIAL') {
        this.form.controls.serieCartograficaOficial.reset('');
      }
      if (valor !== 'CARTOGRAFIA_HOMOLOGADA') {
        this.form.controls.numeroHomologacao.reset('');
        this.form.controls.dataHomologacao.reset('');
        this.form.controls.entidadeHomologacao.reset('');
      }
    });
  }

  pedirRemocao(evento: Event): void {
    this.abridorRemocao = evento.currentTarget as HTMLElement;
    this.erroGeral.set(null);
    this.aConfirmarRemocao.set(true);
  }

  cancelarRemocao(): void {
    this.aConfirmarRemocao.set(false);
  }

  remover(): void {
    this.aRemover.set(true);
    this.plantasService.removerFichaConjuntoOpcional(this.processoId, this.registo.id).subscribe({
      next: () => {
        this.aRemover.set(false);
        this.aConfirmarRemocao.set(false);
        this.removida.emit(this.registo.id);
      },
      error: (erro: ApiError) => {
        this.aRemover.set(false);
        this.aConfirmarRemocao.set(false);
        this.erroGeral.set(erro.mensagem);
        this.abridorRemocao?.focus();
      },
    });
  }

  editar(): void {
    this.emEdicao.set(true);
  }

  alterarFolha(indice: number, campo: 'nomeRegulamento' | 'numeroFolha', evento: Event): void {
    const valor = (evento.target as HTMLInputElement).value;
    this.folhas.update((folhas) => folhas.map((folha, i) => (i === indice ? { ...folha, [campo]: valor } : folha)));
    this.folhasAlteradas.set(true);
  }

  /** `true` quando gravou ou não havia nada alterado; um erro de campo fica junto dele. */
  gravar(): Observable<boolean> {
    if (!this.editavel || (!this.form.dirty && !this.folhasAlteradas())) {
      return of(true);
    }
    return this.plantasService.gravarConjuntoMotu(this.processoId, this.registo.id, this.corpo()).pipe(
      map((gravado) => {
        this.erros.set({});
        this.erroGeral.set(null);
        this.aplicar(gravado);
        this.emEdicao.set(false);
        this.gravada.emit(gravado);
        return true;
      }),
      catchError((erro: ApiError) => {
        const erros: Record<string, string> = {};
        for (const campo of erro.campos ?? []) {
          if (TODOS_OS_CAMPOS.includes(campo.path as CampoConjunto) || /^plantas\[\d+\]\.\w+$/.test(campo.path)) {
            erros[campo.path] = campo.erro;
          }
        }
        if (erro.codigo === 'MOTU_PECA_COM_FICHA') {
          erros['processoDocumentoId'] = erro.mensagem;
        }
        for (const campo of erro.campos ?? []) {
          if (campo.path === 'processoDocumentoId') {
            erros['processoDocumentoId'] = campo.erro;
          }
        }
        this.erros.set(erros);
        this.erroGeral.set(Object.keys(erros).length > 0 ? null : (erro.campos?.[0]?.erro ?? erro.mensagem));
        return of(false);
      }),
    );
  }

  /** Campo vazio vale `null`; só segue o que existe com o tipo escolhido. */
  private corpo(): GravarConjuntoMotu {
    const valores = this.form.getRawValue() as Record<string, string>;
    const tipo = this.tipo();
    const corpo: Record<string, string | null> = {};
    for (const campo of TODOS_OS_CAMPOS) {
      const doOficial = CAMPOS_OFICIAL.some((c) => c.campo === campo) && tipo !== 'SERIE_CARTOGRAFICA_OFICIAL';
      const daHomologada = CAMPOS_HOMOLOGADA.some((c) => c.campo === campo) && tipo !== 'CARTOGRAFIA_HOMOLOGADA';
      if (!doOficial && !daHomologada) {
        corpo[campo] = valores[campo].trim() || null;
      }
    }
    corpo['cartografiaTipo'] = tipo || null;
    const zip = this.registo.opcional ? { processoDocumentoId: valores['processoDocumentoId'] || null } : {};
    return {
      ...corpo,
      ...zip,
      plantas: this.folhas().map((folha) => ({
        id: folha.id,
        nomeRegulamento: folha.nomeRegulamento.trim() || null,
        numeroFolha: folha.numeroFolha.trim() || null,
      })),
    } as GravarConjuntoMotu;
  }

  private aplicar(registo: RegistoMotu): void {
    this.registoAtual.set(registo);
    const valores: Record<string, string> = {};
    for (const [campo, valor] of Object.entries(registo.dados ?? {})) {
      valores[campo] = (valor as string | null) ?? '';
    }
    this.form.reset({
      ...this.form.getRawValue(),
      ...Object.fromEntries(TODOS_OS_CAMPOS.map((c) => [c, ''])),
      ...valores,
      processoDocumentoId: registo.zip?.id ?? '',
    });
    this.tipo.set((this.form.controls.cartografiaTipo.value as TipoCartografiaMotu | '') ?? '');
    this.folhas.set(
      (registo.plantas ?? []).map((folha: FolhaConjuntoMotu) => ({
        id: folha.id,
        nomeRegulamento: folha.nomeRegulamento ?? '',
        numeroFolha: folha.numeroFolha ?? '',
        nomeTiff: folha.nomeTiff,
      })),
    );
    this.folhasAlteradas.set(false);
    this.form.markAsPristine();
  }
}
