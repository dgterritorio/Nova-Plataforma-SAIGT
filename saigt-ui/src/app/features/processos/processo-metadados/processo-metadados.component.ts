import { NgTemplateOutlet } from '@angular/common';
import { Component, Input, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Observable, catchError, map, of } from 'rxjs';
import { ApiError } from '../../../core/models/error-response.model';
import { Metadados, MetadadosProcesso, ResponsavelMetadados } from '../../../models/plantas.model';
import { PlantasService } from '../../../services/plantas.service';

type CampoMetadados = keyof MetadadosProcesso;

interface CampoFicha {
  campo: CampoMetadados;
  rotulo: string;
  obrigatorio: boolean;
  tipo?: 'data' | 'area';
  /** O `maxLength` do contrato. */
  limite?: number;
}

const RESPONSAVEL: { chave: keyof ResponsavelMetadados; rotulo: string }[] = [
  { chave: 'nomeOrganizacao', rotulo: 'Nome da Organização' },
  { chave: 'funcao', rotulo: 'Função' },
  { chave: 'correioEletronico', rotulo: 'Correio Eletrónico' },
  { chave: 'enderecoUrl', rotulo: 'Endereço URL' },
  { chave: 'rua', rotulo: 'Rua' },
  { chave: 'localidade', rotulo: 'Localidade' },
  { chave: 'codigoPostal', rotulo: 'Código Postal' },
  { chave: 'pais', rotulo: 'País' },
  { chave: 'telefone', rotulo: 'Telefone' },
  { chave: 'fax', rotulo: 'Fax' },
];

/** Grupos e campos do Figma «5.1.1.Metadados» (2111:4090); o * é o do Figma. */
const CAMPOS_RESPONSAVEL: CampoFicha[] = [
  { campo: 'palavrasChave', rotulo: 'Palavras-chave', obrigatorio: true, limite: 500 },
  { campo: 'resolucaoEspacialEscala', rotulo: 'Resolução Espacial Escala', obrigatorio: true, limite: 50 },
];
const CAMPOS_SISTEMA: CampoFicha[] = [
  { campo: 'sistemaReferencia', rotulo: 'Sistema de Referência', obrigatorio: true, limite: 200 },
  { campo: 'codigoSistema', rotulo: 'Código Sistema', obrigatorio: false, limite: 50 },
];
const CAMPOS_CARTOGRAFIA: CampoFicha[] = [
  { campo: 'cartografiaTipo', rotulo: 'Tipo', obrigatorio: true, limite: 100 },
  { campo: 'entidadeProprietaria', rotulo: 'Entidade Proprietária', obrigatorio: true, limite: 300 },
  { campo: 'dataEdicao', rotulo: 'Data Edição', obrigatorio: true, tipo: 'data' },
  { campo: 'entidadeProdutora', rotulo: 'Entidade Produtora', obrigatorio: true, limite: 300 },
  { campo: 'dataAtualizacao', rotulo: 'Data de Atualização/Complemento', obrigatorio: false, tipo: 'data' },
  { campo: 'sistemaReferenciaNome', rotulo: 'Sistema de referência - nome', obrigatorio: true, limite: 200 },
  { campo: 'atualizacaoComplemento', rotulo: 'Atualização Complemento', obrigatorio: true, limite: 300 },
  { campo: 'sistemaReferenciaCodigo', rotulo: 'Sistema de Referência - Código', obrigatorio: false, limite: 50 },
  { campo: 'exatidaoPosicional', rotulo: 'Exatidão Posicional', obrigatorio: true, limite: 100 },
  { campo: 'exatidaoTematica', rotulo: 'Exatidão Temática', obrigatorio: true, limite: 100 },
];

const PREFIXO_ERRO = 'metadados.';

/**
 * Ficha de metadados (MOTU) de um processo sem GeoConform (EF v1.5 §3.6; Figma «5.1.1.Metadados»). Grava-se como
 * rascunho com `gravar()`; os obrigatórios só se exigem na submissão, que devolve os campos em falta.
 */
@Component({
  selector: 'app-processo-metadados',
  standalone: true,
  imports: [ReactiveFormsModule, NgTemplateOutlet],
  styleUrl: './processo-metadados.component.css',
  template: `
    @if (aCarregar()) {
      <p class="estado">A carregar…</p>
    } @else if (metadados(); as m) {
      @if (erroGeral(); as mensagem) {
        <p class="erro erro-geral" role="alert">{{ mensagem }}</p>
      }
      <form class="cartoes" [formGroup]="form">
        <section class="cartao" aria-labelledby="titulo-responsavel">
          <h2 class="titulo" id="titulo-responsavel">Responsável</h2>
          <dl class="grelha leitura">
            @for (linha of responsavel; track linha.chave) {
              <div class="par">
                <dt>{{ linha.rotulo }}</dt>
                <dd>{{ m.responsavel[linha.chave] || '—' }}</dd>
              </div>
            }
          </dl>
          <div class="grelha">
            @for (c of camposResponsavel; track c.campo) {
              <ng-container *ngTemplateOutlet="campo; context: { $implicit: c }" />
            }
          </div>
        </section>

        <section class="cartao" aria-labelledby="titulo-identificacao">
          <h2 class="titulo" id="titulo-identificacao">Identificação</h2>
          <div class="grelha">
            <div class="coluna">
              <dl class="leitura-simples">
                <div class="par">
                  <dt>Título</dt>
                  <dd>{{ m.titulo }}</dd>
                </div>
              </dl>
              <ng-container *ngTemplateOutlet="campo; context: { $implicit: tituloAlternativo }" />
            </div>
            <ng-container *ngTemplateOutlet="campo; context: { $implicit: resumo }" />
          </div>
        </section>

        <section class="cartao" aria-labelledby="titulo-sistema">
          <h2 class="titulo" id="titulo-sistema">Sistema de Referência</h2>
          <div class="grelha">
            @for (c of camposSistema; track c.campo) {
              <ng-container *ngTemplateOutlet="campo; context: { $implicit: c }" />
            }
          </div>
        </section>

        <section class="cartao" aria-labelledby="titulo-cartografia">
          <h2 class="titulo" id="titulo-cartografia">Cartografia de Referência</h2>
          <div class="grelha">
            @for (c of camposCartografia; track c.campo) {
              <ng-container *ngTemplateOutlet="campo; context: { $implicit: c }" />
            }
          </div>
        </section>
      </form>

      <ng-template #campo let-c>
        <div class="campo" [formGroup]="form">
          <label class="rotulo" [for]="'metadados-' + c.campo">
            {{ c.rotulo }}
            @if (c.obrigatorio) {
              <span class="obrigatorio" aria-hidden="true">*</span>
            }
          </label>
          @switch (c.tipo) {
            @case ('area') {
              <textarea
                [id]="'metadados-' + c.campo"
                [attr.maxlength]="c.limite ?? null"
                class="entrada area"
                [formControlName]="c.campo"
                [attr.aria-required]="c.obrigatorio || null"
                [attr.aria-invalid]="erros()[c.campo] ? true : null"
                [attr.aria-describedby]="erros()[c.campo] ? 'erro-' + c.campo : null"
              ></textarea>
            }
            @case ('data') {
              <input
                [id]="'metadados-' + c.campo"
                class="entrada data"
                type="date"
                [formControlName]="c.campo"
                [attr.aria-required]="c.obrigatorio || null"
                [attr.aria-invalid]="erros()[c.campo] ? true : null"
                [attr.aria-describedby]="erros()[c.campo] ? 'erro-' + c.campo : null"
              />
            }
            @default {
              <input
                [id]="'metadados-' + c.campo"
                [attr.maxlength]="c.limite ?? null"
                class="entrada"
                type="text"
                [formControlName]="c.campo"
                [attr.aria-required]="c.obrigatorio || null"
                [attr.aria-invalid]="erros()[c.campo] ? true : null"
                [attr.aria-describedby]="erros()[c.campo] ? 'erro-' + c.campo : null"
              />
            }
          }
          @if (erros()[c.campo]; as mensagem) {
            <span class="erro" [id]="'erro-' + c.campo">{{ mensagem }}</span>
          }
        </div>
      </ng-template>
    } @else if (erroLeitura()) {
      <p class="erro" role="alert">{{ erroLeitura() }}</p>
    }
  `,
})
export class ProcessoMetadadosComponent implements OnInit {
  private readonly plantasService = inject(PlantasService);
  private readonly fb = inject(FormBuilder);

  @Input({ required: true }) processoId!: string;
  @Input() editavel = true;
  /** Erros da submissão (`metadados.<campo>`), mostrados junto do campo. */
  @Input() set errosSubmissao(campos: { path: string; erro: string }[] | null) {
    const erros: Record<string, string> = {};
    for (const campo of campos ?? []) {
      if (campo.path.startsWith(PREFIXO_ERRO)) {
        erros[campo.path.slice(PREFIXO_ERRO.length)] = campo.erro;
      }
    }
    this.erros.set(erros);
  }

  readonly responsavel = RESPONSAVEL;
  readonly camposResponsavel = CAMPOS_RESPONSAVEL;
  readonly camposSistema = CAMPOS_SISTEMA;
  readonly camposCartografia = CAMPOS_CARTOGRAFIA;
  readonly tituloAlternativo: CampoFicha = { campo: 'tituloAlternativo', rotulo: 'Título Alternativo', obrigatorio: true, limite: 300 };
  readonly resumo: CampoFicha = { campo: 'resumo', rotulo: 'Resumo', obrigatorio: true, tipo: 'area', limite: 4000 };

  readonly metadados = signal<Metadados | null>(null);
  readonly aCarregar = signal(true);
  readonly erroLeitura = signal<string | null>(null);
  readonly erros = signal<Record<string, string>>({});
  /** Erro da gravação que não é de um campo da ficha. */
  readonly erroGeral = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    palavrasChave: [''],
    resolucaoEspacialEscala: [''],
    tituloAlternativo: [''],
    resumo: [''],
    sistemaReferencia: [''],
    codigoSistema: [''],
    cartografiaTipo: [''],
    entidadeProprietaria: [''],
    dataEdicao: [''],
    entidadeProdutora: [''],
    dataAtualizacao: [''],
    atualizacaoComplemento: [''],
    sistemaReferenciaNome: [''],
    sistemaReferenciaCodigo: [''],
    exatidaoPosicional: [''],
    exatidaoTematica: [''],
  });

  ngOnInit(): void {
    if (!this.editavel) {
      this.form.disable();
    }
    this.plantasService.obterMetadados(this.processoId).subscribe({
      next: (metadados) => this.aplicar(metadados),
      error: (erro: ApiError) => {
        this.aCarregar.set(false);
        this.erroLeitura.set(erro.mensagem);
      },
    });
  }

  /** `true` quando gravou; um erro de campo fica junto dele. */
  gravar(): Observable<boolean> {
    if (!this.editavel || this.metadados() === null) {
      return of(true);
    }
    return this.plantasService.gravarMetadados(this.processoId, this.dados()).pipe(
      map((gravados) => {
        this.erros.set({});
        this.erroGeral.set(null);
        this.aplicar(gravados);
        return true;
      }),
      catchError((erro: ApiError) => {
        const erros: Record<string, string> = {};
        const campos = Object.keys(this.form.controls);
        for (const campo of erro.campos ?? []) {
          if (campos.includes(campo.path)) {
            erros[campo.path] = campo.erro;
          }
        }
        this.erros.set(erros);
        this.erroGeral.set(Object.keys(erros).length > 0 ? null : (erro.campos?.[0]?.erro ?? erro.mensagem));
        return of(false);
      }),
    );
  }

  /** Campo vazio vale `null`, como no formulário inicial. */
  private dados(): MetadadosProcesso {
    const valores = this.form.getRawValue();
    const dados = {} as MetadadosProcesso;
    for (const [campo, valor] of Object.entries(valores) as [CampoMetadados, string][]) {
      dados[campo] = valor.trim() || null;
    }
    return dados;
  }

  private aplicar(metadados: Metadados): void {
    const valores: Record<string, string> = {};
    for (const [campo, valor] of Object.entries(metadados.dados)) {
      valores[campo] = valor ?? '';
    }
    this.form.patchValue(valores);
    this.metadados.set(metadados);
    this.aCarregar.set(false);
    this.erroLeitura.set(null);
  }
}
