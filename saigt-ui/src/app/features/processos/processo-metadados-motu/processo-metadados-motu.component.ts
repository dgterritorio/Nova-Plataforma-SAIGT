import { NgTemplateOutlet } from '@angular/common';
import {
  Component,
  ElementRef,
  Injector,
  Input,
  OnInit,
  afterNextRender,
  computed,
  inject,
  signal,
  viewChild,
  viewChildren,
} from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Observable, catchError, forkJoin, map, of, switchMap } from 'rxjs';
import { ApiError } from '../../../core/models/error-response.model';
import { MetadadosMotu, RegistoMotu, ResponsavelMetadados, SerieMotuDados } from '../../../models/plantas.model';
import { DocumentoService } from '../../../services/documento.service';
import { PlantasService } from '../../../services/plantas.service';
import { BotaoComponent, ModalComponent, SeloComponent } from '../../../shared/ui';
import { ConjuntoMotuComponent, PecaGrafica } from './conjunto-motu.component';

type CampoSerie = keyof SerieMotuDados;

interface CampoFicha {
  campo: CampoSerie;
  rotulo: string;
  obrigatorio: boolean;
  tipo?: 'area';
  /** O `maxLength` do contrato. */
  limite: number;
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
const CAMPOS_QUALIDADE: CampoFicha[] = [
  { campo: 'historico', rotulo: 'Histórico', obrigatorio: true, tipo: 'area', limite: 4000 },
  { campo: 'relatorio', rotulo: 'Relatório', obrigatorio: true, tipo: 'area', limite: 4000 },
];

/**
 * Série de Dados Geográficos da ficha MOTU do procedimento final (EF v1.5 §3.6.1; cartões do UI-053). Grava-se como
 * rascunho com `gravar()`; a indicação «Completo» vem da API.
 */
@Component({
  selector: 'app-processo-metadados-motu',
  standalone: true,
  imports: [ReactiveFormsModule, NgTemplateOutlet, SeloComponent, BotaoComponent, ModalComponent, ConjuntoMotuComponent],
  styleUrls: ['../processo-metadados/processo-metadados.component.css', './processo-metadados-motu.component.css'],
  template: `
    @if (aCarregar()) {
      <p class="estado">A carregar…</p>
    } @else if (ficha(); as f) {
      @if (erroGeral(); as mensagem) {
        <p class="erro erro-geral" role="alert">{{ mensagem }}</p>
      }
      <form class="cartoes" [formGroup]="form">
        <div class="cabecalho-serie">
          <h2 class="titulo-serie">Série de Dados Geográficos</h2>
          @if (serie(); as s) {
            <app-selo class="estado-serie" [tom]="s.completo ? 'sucesso' : 'aviso'" forma="pill">
              {{ s.completo ? 'Completo' : 'Incompleto' }}
            </app-selo>
          }
        </div>

        <section class="cartao" aria-labelledby="motu-titulo-responsavel">
          <h3 class="titulo" id="motu-titulo-responsavel">Responsável</h3>
          <dl class="grelha leitura">
            @for (linha of responsavel; track linha.chave) {
              <div class="par">
                <dt>{{ linha.rotulo }}</dt>
                <dd>{{ f.responsavel[linha.chave] || '—' }}</dd>
              </div>
            }
          </dl>
        </section>

        <section class="cartao" aria-labelledby="motu-titulo-identificacao">
          <h3 class="titulo" id="motu-titulo-identificacao">Identificação</h3>
          <div class="grelha">
            <dl class="leitura-simples coluna-inteira">
              <div class="par">
                <dt>Título</dt>
                <dd>{{ serie()?.titulo || '—' }}</dd>
              </div>
            </dl>
            @for (c of camposIdentificacao; track c.campo) {
              <ng-container *ngTemplateOutlet="campo; context: { $implicit: c }" />
            }
          </div>
        </section>

        <section class="cartao" aria-labelledby="motu-titulo-sistema">
          <h3 class="titulo" id="motu-titulo-sistema">Sistema de Referência</h3>
          <div class="grelha">
            @for (c of camposSistema; track c.campo) {
              <ng-container *ngTemplateOutlet="campo; context: { $implicit: c }" />
            }
          </div>
        </section>

        <section class="cartao" aria-labelledby="motu-titulo-qualidade">
          <h3 class="titulo" id="motu-titulo-qualidade">Qualidade</h3>
          <div class="grelha">
            @for (c of camposQualidade; track c.campo) {
              <ng-container *ngTemplateOutlet="campo; context: { $implicit: c }" />
            }
          </div>
        </section>
      </form>

      @for (conjunto of conjuntos(); track conjunto.id) {
        <app-conjunto-motu [processoId]="processoId" [registo]="conjunto" [editavel]="editavel" />
      }

      @for (opcional of opcionais(); track opcional.id) {
        <app-conjunto-motu
          [processoId]="processoId"
          [registo]="opcional"
          [editavel]="editavel"
          [pecasLivres]="pecasLivres()"
          (gravada)="aoGravarOpcional($event)"
          (removida)="aoRemoverOpcional($event)"
        />
      }

      @if (editavel && f.aplicaFichaMotu) {
        <div class="acoes-fichas">
          <button #botaoAdicionar type="button" app-botao variante="secundario" class="adicionar-ficha" (click)="abrirAdicionar()">
            Adicionar ficha de Conjunto
          </button>
        </div>
        <app-modal
          [aberto]="aAdicionar()"
          titulo="Adicionar ficha de Conjunto"
          descritoPor="motu-ajuda-reproduzir"
          largura="sm"
          (fechar)="fecharAdicionar()"
        >
          <label class="opcao-reproduzir">
            <input type="checkbox" class="reproduzir-ultima" [checked]="reproduzirUltima()" (change)="alterarReproduzir($event)" />
            Pretende que a próxima ficha de metadados reproduza os dados constantes da última ficha
          </label>
          <p class="ajuda-campo" id="motu-ajuda-reproduzir">A reprodução não inclui o Título Alternativo nem o ZIP.</p>
          @if (erroAdicionar(); as mensagem) {
            <p class="erro" role="alert">{{ mensagem }}</p>
          }
          <div rodape class="rodape-confirmacao">
            <button type="button" app-botao variante="secundario" class="cancelar-adicionar" (click)="fecharAdicionar()">Cancelar</button>
            <button type="button" app-botao class="confirmar-adicionar" [aCarregar]="aCriar()" (click)="adicionar()">Adicionar</button>
          </div>
        </app-modal>
      }

      <ng-template #campo let-c>
        <div class="campo" [formGroup]="form">
          <label class="rotulo" [for]="'motu-' + c.campo">
            {{ c.rotulo }}
            @if (c.obrigatorio) {
              <span class="obrigatorio" aria-hidden="true">*</span>
            }
          </label>
          @if (c.tipo === 'area') {
            <textarea
              [id]="'motu-' + c.campo"
              [attr.maxlength]="c.limite"
              class="entrada area"
              [formControlName]="c.campo"
              [attr.aria-required]="c.obrigatorio || null"
              [attr.aria-invalid]="erros()[c.campo] ? true : null"
              [attr.aria-describedby]="erros()[c.campo] ? 'motu-erro-' + c.campo : null"
            ></textarea>
          } @else {
            <input
              [id]="'motu-' + c.campo"
              [attr.maxlength]="c.limite"
              class="entrada"
              type="text"
              [formControlName]="c.campo"
              [attr.aria-required]="c.obrigatorio || null"
              [attr.aria-invalid]="erros()[c.campo] ? true : null"
              [attr.aria-describedby]="erros()[c.campo] ? 'motu-erro-' + c.campo : null"
            />
          }
          @if (erros()[c.campo]; as mensagem) {
            <span class="erro" [id]="'motu-erro-' + c.campo">{{ mensagem }}</span>
          }
        </div>
      </ng-template>
    } @else if (erroLeitura()) {
      <p class="erro" role="alert">{{ erroLeitura() }}</p>
    }
  `,
})
export class ProcessoMetadadosMotuComponent implements OnInit {
  private readonly plantasService = inject(PlantasService);
  private readonly documentoService = inject(DocumentoService);
  private readonly injector = inject(Injector);
  private readonly fb = inject(FormBuilder);

  @Input({ required: true }) processoId!: string;
  @Input() editavel = true;

  readonly responsavel = RESPONSAVEL;
  readonly camposIdentificacao = CAMPOS_IDENTIFICACAO;
  readonly camposSistema = CAMPOS_SISTEMA;
  readonly camposQualidade = CAMPOS_QUALIDADE;

  readonly ficha = signal<MetadadosMotu | null>(null);
  readonly aCarregar = signal(true);
  readonly erroLeitura = signal<string | null>(null);
  readonly erros = signal<Record<string, string>>({});
  /** Erro da gravação que não é de um campo da Série. */
  readonly erroGeral = signal<string | null>(null);
  readonly conjuntos = computed(() => this.ficha()?.registos.filter((registo) => registo.tipo === 'CDG' && !registo.opcional) ?? []);
  readonly opcionais = computed(() => this.ficha()?.registos.filter((registo) => registo.tipo === 'CDG' && registo.opcional) ?? []);
  private readonly pecas = signal<PecaGrafica[]>([]);
  /** As peças gráficas do processo que ainda não têm ficha. */
  readonly pecasLivres = computed(() => {
    const comFicha = new Set((this.ficha()?.registos ?? []).map((registo) => registo.zip?.id));
    return this.pecas().filter((peca) => !comFicha.has(peca.id));
  });
  readonly aAdicionar = signal(false);
  readonly aCriar = signal(false);
  readonly reproduzirUltima = signal(false);
  readonly erroAdicionar = signal<string | null>(null);
  private readonly botaoAdicionar = viewChild('botaoAdicionar', { read: ElementRef<HTMLButtonElement> });
  private readonly blocos = viewChildren(ConjuntoMotuComponent);
  readonly serie = computed(() => this.ficha()?.registos.find((registo) => registo.tipo === 'SERIE') ?? null);

  readonly form = this.fb.nonNullable.group({
    tituloAlternativo: [''],
    resumo: [''],
    palavrasChave: [''],
    denominadorEscala: [''],
    sistemaReferencia: [''],
    codigoSistema: [''],
    historico: [''],
    relatorio: [''],
  });

  ngOnInit(): void {
    if (!this.editavel) {
      this.form.disable();
    }
    this.plantasService.obterMetadadosMotu(this.processoId).subscribe({
      next: (ficha) => {
        this.ficha.set(ficha);
        this.aplicar(ficha.registos.find((registo) => registo.tipo === 'SERIE') ?? null);
        this.aCarregar.set(false);
        if (this.editavel && ficha.aplicaFichaMotu) {
          this.carregarPecas();
        }
      },
      error: (erro: ApiError) => {
        this.aCarregar.set(false);
        this.erroLeitura.set(erro.mensagem);
      },
    });
  }

  abrirAdicionar(): void {
    this.reproduzirUltima.set(false);
    this.erroAdicionar.set(null);
    this.aAdicionar.set(true);
  }

  fecharAdicionar(): void {
    this.aAdicionar.set(false);
  }

  alterarReproduzir(evento: Event): void {
    this.reproduzirUltima.set((evento.target as HTMLInputElement).checked);
  }

  adicionar(): void {
    this.aCriar.set(true);
    this.plantasService.criarFichaConjuntoOpcional(this.processoId, this.reproduzirUltima()).subscribe({
      next: (registo) => {
        this.aCriar.set(false);
        this.aAdicionar.set(false);
        this.ficha.update((atual) => (atual ? { ...atual, registos: [...atual.registos, registo] } : atual));
      },
      error: (erro: ApiError) => {
        this.aCriar.set(false);
        this.erroAdicionar.set(erro.mensagem);
      },
    });
  }

  aoGravarOpcional(gravado: RegistoMotu): void {
    this.ficha.update((atual) =>
      atual ? { ...atual, registos: atual.registos.map((registo) => (registo.id === gravado.id ? gravado : registo)) } : atual,
    );
  }

  aoRemoverOpcional(id: string): void {
    this.ficha.update((atual) => (atual ? { ...atual, registos: atual.registos.filter((registo) => registo.id !== id) } : atual));
    afterNextRender(() => this.botaoAdicionar()?.nativeElement.focus(), { injector: this.injector });
  }

  /** Sem a lista, o seletor fica só com «Sem ZIP» e a peça ligada. */
  private carregarPecas(): void {
    this.documentoService.estrutura(this.processoId).subscribe({
      next: (estrutura) => {
        const pecas = estrutura.separadores
          .flatMap((separador) => [...separador.publicacao, ...separador.deposito])
          .filter((item) => item.tipoElemento === 'PECA_GRAFICA')
          .flatMap((item) => item.ficheiros)
          .filter((ficheiro) => ficheiro.extensao.toLowerCase().replace('.', '') === 'zip')
          .map((ficheiro) => ({ id: ficheiro.id, nome: ficheiro.nomeFicheiro }));
        this.pecas.set(pecas);
      },
      error: () => this.pecas.set([]),
    });
  }

  /** `true` quando gravou a Série e cada Conjunto alterado; um erro de campo fica junto dele e a página não avança. */
  gravar(): Observable<boolean> {
    return this.gravarSerie().pipe(
      switchMap((serieGravada) => {
        const blocos = this.blocos();
        if (blocos.length === 0) {
          return of(serieGravada);
        }
        return forkJoin(blocos.map((bloco) => bloco.gravar())).pipe(
          map((resultados) => serieGravada && resultados.every(Boolean)),
        );
      }),
    );
  }

  private gravarSerie(): Observable<boolean> {
    const serie = this.serie();
    if (!this.editavel || serie === null) {
      return of(true);
    }
    return this.plantasService.gravarSerieMotu(this.processoId, serie.id, this.dados()).pipe(
      map((gravado) => {
        this.erros.set({});
        this.erroGeral.set(null);
        this.ficha.update((atual) =>
          atual ? { ...atual, registos: atual.registos.map((registo) => (registo.id === gravado.id ? gravado : registo)) } : atual,
        );
        this.aplicar(gravado);
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

  /** Campo vazio vale `null`, como na ficha única. */
  private dados(): SerieMotuDados {
    const dados = {} as SerieMotuDados;
    for (const [campo, valor] of Object.entries(this.form.getRawValue()) as [CampoSerie, string][]) {
      dados[campo] = valor.trim() || null;
    }
    return dados;
  }

  private aplicar(registo: RegistoMotu | null): void {
    const valores: Record<string, string> = {};
    for (const [campo, valor] of Object.entries(registo?.dados ?? {})) {
      valores[campo] = (valor as string | null) ?? '';
    }
    this.form.patchValue(valores);
  }
}
