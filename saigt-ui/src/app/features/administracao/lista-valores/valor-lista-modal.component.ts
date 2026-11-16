import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { ApiError } from '../../../core/models/error-response.model';
import {
  AtualizarListaValoresRequest,
  CriarListaValoresRequest,
  ListaValoresItem,
  TipoListaValores,
} from '../../../models/lista-valores.model';
import { ListaValoresService } from '../../../services/lista-valores.service';
import { ConfirmacaoModalComponent } from '../../../shared/components/confirmacao-modal.component';
import { ErrorBannerComponent } from '../../../shared/components/error-banner.component';
import { BotaoComponent } from '../../../shared/ui/botao/botao.component';
import { CampoTextoComponent } from '../../../shared/ui/campo-texto/campo-texto.component';
import { ModalComponent } from '../../../shared/ui/modal/modal.component';
import { OpcaoSeletor, SeletorComponent } from '../../../shared/ui/seletor/seletor.component';

type CampoValor = 'codigo' | 'descricao' | 'ordem' | 'paiId';

const CAMPOS: readonly CampoValor[] = ['codigo', 'descricao', 'ordem', 'paiId'];
const MAX_CODIGO = 20;
const MAX_DESCRICAO = 300;

export const MENSAGEM_ULTIMO_ATIVO =
  'Ao desativar este valor, a lista fica vazia e o campo deixa de aparecer nos formulários.';

function naoEmBranco(controlo: AbstractControl<string | null>): ValidationErrors | null {
  return (controlo.value ?? '').trim() ? null : { required: true };
}

function inteiroOuVazio(controlo: AbstractControl<string | null>): ValidationErrors | null {
  const valor = String(controlo.value ?? '').trim();
  return valor === '' || /^\d+$/.test(valor) ? null : { inteiro: true };
}

let proximoId = 0;

/**
 * Acrescentar (`valor` nulo) ou corrigir um valor de uma lista. O código e o pai só se escolhem ao criar: o código é o que
 * fica guardado nos processos. Desativar o último valor ativo pede confirmação, porque o campo sai dos formulários.
 */
@Component({
  selector: 'app-valor-lista-modal',
  imports: [
    BotaoComponent,
    CampoTextoComponent,
    ConfirmacaoModalComponent,
    ErrorBannerComponent,
    ModalComponent,
    ReactiveFormsModule,
    SeletorComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-modal [titulo]="titulo()" [subtitulo]="tipo().descricao" largura="md" [aberto]="aberto()" [fecharComFundo]="!aGravar()" (fechar)="pedirFecho()">
      <app-error-banner class="erro-modal" [erro]="erroGeral()" [mensagemDoServidor]="true" />
      <form class="formulario" [id]="idFormulario" [formGroup]="formulario" (ngSubmit)="gravar()">
        @if (rotuloPai(); as rotulo) {
          @if (emEdicao()) {
            <p class="leitura" data-campo="pai"><span class="leitura-rotulo">{{ rotulo }}</span> {{ descricaoPai() }}</p>
          } @else {
            <app-seletor
              data-campo="pai"
              formControlName="paiId"
              [rotulo]="rotulo"
              [compacto]="true"
              [obrigatorio]="true"
              [opcoes]="opcoesPai()"
              [ajuda]="opcoesPai().length === 0 ? 'A lista ' + rotulo + ' não tem valores ativos.' : null"
              [erro]="erros().paiId"
            />
          }
        }
        @if (emEdicao()) {
          <p class="leitura" data-campo="codigo"><span class="leitura-rotulo">Código</span> {{ valor()?.codigo }}</p>
        } @else {
          <app-campo-texto
            data-campo="codigo"
            formControlName="codigo"
            rotulo="Código"
            ajuda="Maiúsculas, algarismos e _, sem espaços. Não se altera depois de gravado."
            [compacto]="true"
            [obrigatorio]="true"
            [maxlength]="maxCodigo"
            [erro]="erros().codigo"
          />
        }
        <app-campo-texto
          data-campo="descricao"
          formControlName="descricao"
          rotulo="Descrição"
          [compacto]="true"
          [obrigatorio]="true"
          [maxlength]="maxDescricao"
          [erro]="erros().descricao"
        />
        <app-campo-texto
          data-campo="ordem"
          formControlName="ordem"
          rotulo="Ordem"
          modoEntrada="numeric"
          [ajuda]="emEdicao() ? null : 'Por omissão, a seguir ao último.'"
          [compacto]="true"
          [erro]="erros().ordem"
        />
        @if (emEdicao()) {
          <label class="caixa-ativo" data-campo="ativo">
            <input type="checkbox" formControlName="ativo" />
            <span>Ativo (oferecido nos formulários)</span>
          </label>
        }
      </form>
      <div rodape>
        <button type="button" app-botao variante="secundario" tamanho="pequeno" [desativado]="aGravar()" (click)="pedirFecho()">
          Cancelar
        </button>
        <button type="submit" app-botao tamanho="pequeno" class="gravar" [attr.form]="idFormulario" [desativado]="!podeGravar()">
          {{ aGravar() ? 'A guardar…' : emEdicao() ? 'Guardar' : 'Acrescentar' }}
        </button>
      </div>
    </app-modal>
    <app-confirmacao-modal
      titulo="Desativar o último valor"
      rotuloSim="Desativar"
      rotuloNao="Cancelar"
      [mensagem]="mensagemUltimoAtivo"
      [aberto]="confirmarDesativacao()"
      [aProcessar]="aGravar()"
      (sim)="confirmarEGravar()"
      (nao)="confirmarDesativacao.set(false)"
    />
  `,
  styles: `
    .formulario {
      display: flex;
      flex-direction: column;
      gap: var(--espaco-4);
    }

    .erro-modal:not(:empty) {
      display: block;
      margin-bottom: var(--espaco-4);
    }

    .leitura {
      display: flex;
      flex-direction: column;
      gap: var(--espaco-1);
      margin: 0;
      font-size: var(--texto-sm);
      line-height: var(--texto-sm-linha);
      color: var(--cor-texto);
    }

    .leitura-rotulo {
      font-size: var(--texto-xs);
      font-weight: var(--peso-semi);
      color: var(--cor-texto-rotulo);
    }

    .caixa-ativo {
      display: flex;
      align-items: center;
      gap: var(--espaco-2);
      font-size: var(--texto-sm);
      line-height: var(--texto-sm-linha);
      color: var(--cor-texto-rotulo);
      cursor: pointer;
    }

    .caixa-ativo input {
      width: 16px;
      height: 16px;
      margin: 0;
      accent-color: var(--cor-primaria);
    }

    [rodape] {
      display: contents;
    }
  `,
})
export class ValorListaModalComponent {
  private readonly listaValoresService = inject(ListaValoresService);

  readonly aberto = input(false);
  readonly tipo = input.required<TipoListaValores>();
  /** O valor em edição; nulo para acrescentar. */
  readonly valor = input<ListaValoresItem | null>(null);
  /** Rótulo da lista do nível acima, só nas listas com hierarquia. */
  readonly rotuloPai = input<string | null>(null);
  /** Valores ativos da lista do nível acima (ao criar) ou todos (para mostrar o pai em edição). */
  readonly valoresPai = input<readonly ListaValoresItem[]>([]);
  readonly ordemSugerida = input(1);
  /** Em edição: é o único valor ativo da lista. */
  readonly ultimoAtivo = input(false);
  readonly fechar = output<void>();
  readonly gravado = output<ListaValoresItem>();

  readonly idFormulario = `valor-lista-formulario-${++proximoId}`;
  readonly maxCodigo = MAX_CODIGO;
  readonly maxDescricao = MAX_DESCRICAO;
  readonly mensagemUltimoAtivo = MENSAGEM_ULTIMO_ATIVO;

  readonly aGravar = signal(false);
  readonly erroGeral = signal<ApiError | null>(null);
  readonly confirmarDesativacao = signal(false);
  private readonly errosApi = signal<Partial<Record<CampoValor, string>>>({});

  readonly formulario = new FormGroup({
    codigo: new FormControl('', {
      nonNullable: true,
      validators: [naoEmBranco, Validators.maxLength(MAX_CODIGO), Validators.pattern(/^\s*[A-Z0-9_]+\s*$/)],
    }),
    descricao: new FormControl('', { nonNullable: true, validators: [naoEmBranco, Validators.maxLength(MAX_DESCRICAO)] }),
    ordem: new FormControl('', { nonNullable: true, validators: inteiroOuVazio }),
    paiId: new FormControl<string | null>(null),
    ativo: new FormControl(true, { nonNullable: true }),
  });

  private readonly estadoFormulario = toSignal(this.formulario.events, { initialValue: null });

  readonly emEdicao = computed(() => this.valor() !== null);
  readonly titulo = computed(() => (this.emEdicao() ? `Editar ${this.valor()?.descricao}` : 'Acrescentar valor'));

  readonly opcoesPai = computed<OpcaoSeletor[]>(() =>
    this.valoresPai()
      .filter((pai) => pai.ativo)
      .map((pai) => ({ valor: String(pai.id), rotulo: pai.descricao })),
  );

  readonly descricaoPai = computed(() => {
    const paiId = this.valor()?.paiId;
    return this.valoresPai().find((pai) => pai.id === paiId)?.descricao ?? '—';
  });

  readonly erros = computed<Record<CampoValor, string | null>>(() => {
    this.estadoFormulario();
    const daApi = this.errosApi();
    const erros = {} as Record<CampoValor, string | null>;
    for (const campo of CAMPOS) {
      const controlo = this.formulario.controls[campo];
      const mostrar = controlo.enabled && controlo.invalid && (controlo.touched || controlo.dirty);
      erros[campo] = mostrar ? this.mensagem(campo, controlo) : (daApi[campo] ?? null);
    }
    return erros;
  });

  readonly alteracoes = computed<AtualizarListaValoresRequest>(() => {
    this.estadoFormulario();
    const valor = this.valor();
    if (!valor) {
      return {};
    }
    const atuais = this.formulario.getRawValue();
    const alteracoes: AtualizarListaValoresRequest = {};
    if (atuais.descricao.trim() !== valor.descricao) alteracoes.descricao = atuais.descricao.trim();
    const ordem = atuais.ordem.trim();
    if (ordem !== '' && Number(ordem) !== valor.ordem) alteracoes.ordem = Number(ordem);
    if (atuais.ativo !== valor.ativo) alteracoes.ativo = atuais.ativo;
    return alteracoes;
  });

  readonly podeGravar = computed(() => {
    this.estadoFormulario();
    const semAlteracoes = this.emEdicao() && Object.keys(this.alteracoes()).length === 0;
    return this.formulario.valid && !this.aGravar() && !semAlteracoes;
  });

  constructor() {
    effect(() => {
      if (this.aberto()) {
        untracked(() => this.preparar());
      }
    });
    for (const campo of CAMPOS) {
      (this.formulario.controls[campo] as AbstractControl).valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
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

  pedirFecho(): void {
    if (!this.aGravar()) {
      this.fechar.emit();
    }
  }

  gravar(): void {
    if (!this.podeGravar()) {
      this.formulario.markAllAsTouched();
      return;
    }
    if (this.emEdicao() && this.alteracoes().ativo === false && this.ultimoAtivo()) {
      this.confirmarDesativacao.set(true);
      return;
    }
    this.enviar();
  }

  confirmarEGravar(): void {
    this.enviar();
  }

  private enviar(): void {
    const valor = this.valor();
    const pedido = valor
      ? this.listaValoresService.atualizar(valor.id, this.alteracoes())
      : this.listaValoresService.criar(this.pedidoCriar());
    this.errosApi.set({});
    this.erroGeral.set(null);
    this.aGravar.set(true);
    pedido.subscribe({
      next: (gravado) => {
        this.aGravar.set(false);
        this.confirmarDesativacao.set(false);
        this.gravado.emit(gravado);
      },
      error: (erro: ApiError) => {
        this.aGravar.set(false);
        this.confirmarDesativacao.set(false);
        this.tratarErro(erro);
      },
    });
  }

  private pedidoCriar(): CriarListaValoresRequest {
    const valores = this.formulario.getRawValue();
    const ordem = valores.ordem.trim();
    const pedido: CriarListaValoresRequest = {
      tipo: this.tipo().tipo,
      codigo: valores.codigo.trim(),
      descricao: valores.descricao.trim(),
      ordem: ordem === '' ? this.ordemSugerida() : Number(ordem),
    };
    if (this.rotuloPai() && valores.paiId) {
      pedido.paiId = Number(valores.paiId);
    }
    return pedido;
  }

  private mensagem(campo: CampoValor, controlo: AbstractControl): string {
    if (controlo.hasError('maxlength')) {
      return `Tem de ter no máximo ${controlo.getError('maxlength').requiredLength} caracteres.`;
    }
    switch (campo) {
      case 'codigo':
        return controlo.hasError('pattern') ? 'Use só maiúsculas, algarismos e _, sem espaços.' : 'Indique o código.';
      case 'descricao':
        return 'Indique a descrição.';
      case 'ordem':
        return 'Indique um número inteiro igual ou maior que zero.';
      case 'paiId':
        return `Escolha o valor de ${this.rotuloPai()} de que este depende.`;
    }
  }

  /** O código repetido chega como 409 sem campo: ao criar, é sempre o código, o único campo único do valor. */
  private tratarErro(erro: ApiError): void {
    const porCampo: Partial<Record<CampoValor, string>> = {};
    if (erro.codigo === 'VALIDACAO' || erro.codigo === 'REFERENCIA_DUPLICADA') {
      for (const campo of erro.campos ?? []) {
        if ((CAMPOS as readonly string[]).includes(campo.path)) {
          porCampo[campo.path as CampoValor] = campo.erro;
        }
      }
    }
    if (erro.codigo === 'REFERENCIA_DUPLICADA' && !this.emEdicao() && Object.keys(porCampo).length === 0) {
      porCampo.codigo = erro.mensagem;
    }
    if (Object.keys(porCampo).length > 0) {
      this.errosApi.set(porCampo);
    } else {
      this.erroGeral.set(erro);
    }
  }

  private preparar(): void {
    const valor = this.valor();
    this.formulario.reset({
      codigo: '',
      descricao: valor?.descricao ?? '',
      ordem: valor ? String(valor.ordem) : '',
      paiId: null,
      ativo: valor?.ativo ?? true,
    });
    const paiId = this.formulario.controls.paiId;
    if (this.rotuloPai() && !valor) {
      paiId.setValidators(Validators.required);
      paiId.enable({ emitEvent: false });
    } else {
      paiId.clearValidators();
      paiId.disable({ emitEvent: false });
    }
    if (valor) {
      this.formulario.controls.codigo.disable({ emitEvent: false });
    } else {
      this.formulario.controls.codigo.enable({ emitEvent: false });
    }
    paiId.updateValueAndValidity({ emitEvent: false });
    this.errosApi.set({});
    this.erroGeral.set(null);
    this.aGravar.set(false);
    this.confirmarDesativacao.set(false);
  }
}
