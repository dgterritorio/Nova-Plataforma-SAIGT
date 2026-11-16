import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { ApiError } from '../../core/models/error-response.model';
import {
  AtualizarEntidadeRequest,
  Concelho,
  CriarEntidadeRequest,
  Entidade,
  TIPOS_ENTIDADE,
  TipoEntidade,
} from '../../models/entidade.model';
import { EntidadeService } from '../../services/entidade.service';
import { ReferenciaSnitService } from '../../services/referencia-snit.service';
import { AlertaComponent } from '../ui/alerta/alerta.component';
import { BotaoComponent } from '../ui/botao/botao.component';
import { CampoTextoComponent } from '../ui/campo-texto/campo-texto.component';
import { ModalComponent } from '../ui/modal/modal.component';
import { OpcaoSeletor, SeletorComponent } from '../ui/seletor/seletor.component';

export type ModoEntidadeModal = 'criar' | 'editar';

type CampoEntidade = 'codigo' | 'descricaoIncm' | 'descricaoSaigt' | 'tipoEntidade' | 'nipc' | 'dico';

const CAMPOS: readonly CampoEntidade[] = ['codigo', 'descricaoIncm', 'descricaoSaigt', 'tipoEntidade', 'nipc', 'dico'];

const MENSAGENS: Record<CampoEntidade, string> = {
  codigo: 'Indique o código.',
  descricaoIncm: 'Indique a descrição INCM.',
  descricaoSaigt: 'Indique a descrição SAIGT.',
  tipoEntidade: 'Escolha o tipo de entidade.',
  nipc: 'O NIPC tem 9 dígitos.',
  dico: 'Escolha um concelho da lista.',
};

const AJUDA_CONCELHO = 'Usado para pré-preencher a NUTS II e o concelho na Elaboração de planos municipais.';
const CONCELHOS_INDISPONIVEIS = 'Lista de concelhos indisponível.';

function naoEmBranco(controlo: AbstractControl<string | null>): ValidationErrors | null {
  return (controlo.value ?? '').trim() ? null : { required: true };
}

interface ValoresEntidade {
  codigo: string;
  descricaoIncm: string;
  descricaoSaigt: string;
  tipoEntidade: TipoEntidade | null;
  nipc: string;
  dico: string;
}

let proximoId = 0;

/** Criar e editar uma entidade SAIGT. O NIPC e a descrição INCM corrigem-se aqui; o login não os altera. */
@Component({
  selector: 'app-entidade-modal',
  imports: [AlertaComponent, BotaoComponent, CampoTextoComponent, ModalComponent, ReactiveFormsModule, SeletorComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-modal [titulo]="titulo()" largura="md" [aberto]="aberto()" [fecharComFundo]="!aGravar()" (fechar)="pedirFecho()">
      @if (erroGeral(); as mensagem) {
        <app-alerta class="alerta-modal" tipo="erro">{{ mensagem }}</app-alerta>
      }
      <form class="formulario" [id]="idFormulario" [formGroup]="formulario" (ngSubmit)="gravar()">
        <app-campo-texto
          data-campo="codigo"
          formControlName="codigo"
          rotulo="Código"
          [compacto]="true"
          [obrigatorio]="true"
          [maxlength]="20"
          [erro]="erros().codigo"
        />
        <app-campo-texto
          data-campo="descricao-incm"
          formControlName="descricaoIncm"
          rotulo="Descrição INCM"
          ajuda="Exatamente como recebida da INCM. Usada para reconhecer a entidade no login."
          [compacto]="true"
          [obrigatorio]="true"
          [maxlength]="200"
          [erro]="erros().descricaoIncm"
        />
        <app-campo-texto
          data-campo="descricao-saigt"
          formControlName="descricaoSaigt"
          rotulo="Descrição SAIGT"
          [compacto]="true"
          [obrigatorio]="true"
          [maxlength]="200"
          [erro]="erros().descricaoSaigt"
        />
        <app-seletor
          data-campo="tipo"
          formControlName="tipoEntidade"
          rotulo="Tipo de entidade"
          [compacto]="true"
          [obrigatorio]="true"
          [opcoes]="opcoesTipo"
          [erro]="erros().tipoEntidade"
        />
        <app-campo-texto
          data-campo="nipc"
          formControlName="nipc"
          rotulo="NIPC"
          placeholder="9 dígitos"
          ajuda="NIPC da entidade na INCM. Usado para reconhecer a entidade no login."
          [compacto]="true"
          [maxlength]="9"
          [erro]="erros().nipc"
        />
        @if (ehCamara()) {
          <app-seletor
            data-campo="concelho"
            formControlName="dico"
            rotulo="Concelho"
            placeholder="Sem concelho"
            [compacto]="true"
            [permitirVazio]="true"
            [opcoes]="opcoesConcelho()"
            [ajuda]="concelhosErro() ? concelhosIndisponiveis : ajudaConcelho"
            [erro]="erros().dico"
          />
        }
      </form>
      @if (avisoProximoLogin()) {
        <app-alerta class="alerta-modal" tipo="info">A alteração vale no próximo login dos utilizadores desta entidade.</app-alerta>
      }
      <div rodape>
        <button type="button" app-botao variante="secundario" tamanho="pequeno" [desativado]="aGravar()" (click)="pedirFecho()">
          Cancelar
        </button>
        <button type="submit" app-botao tamanho="pequeno" class="gravar" [attr.form]="idFormulario" [desativado]="!podeGravar()">
          {{ aGravar() ? 'A guardar…' : modo() === 'criar' ? 'Adicionar' : 'Guardar' }}
        </button>
      </div>
    </app-modal>
  `,
  styles: `
    .formulario {
      display: flex;
      flex-direction: column;
      gap: var(--espaco-4);
    }

    .alerta-modal {
      margin-bottom: var(--espaco-4);
    }

    .formulario + .alerta-modal {
      margin: var(--espaco-4) 0 0;
    }

    [rodape] {
      display: contents;
    }
  `,
})
export class EntidadeModalComponent {
  private readonly entidadeService = inject(EntidadeService);
  private readonly referenciaSnitService = inject(ReferenciaSnitService);

  readonly aberto = input(false);
  readonly modo = input<ModoEntidadeModal>('criar');
  /** A entidade em edição; ignorada no modo `criar`. */
  readonly entidade = input<Entidade | null>(null);
  readonly fechar = output<void>();
  readonly gravada = output<Entidade>();

  readonly idFormulario = `entidade-formulario-${++proximoId}`;
  readonly opcoesTipo: OpcaoSeletor[] = TIPOS_ENTIDADE.map((tipo) => ({ valor: tipo.valor, rotulo: tipo.rotulo }));
  readonly ajudaConcelho = AJUDA_CONCELHO;
  readonly concelhosIndisponiveis = CONCELHOS_INDISPONIVEIS;

  readonly concelhos = signal<Concelho[]>([]);
  readonly concelhosErro = signal(false);
  readonly aGravar = signal(false);
  readonly erroGeral = signal<string | null>(null);
  private readonly errosApi = signal<Partial<Record<CampoEntidade, string>>>({});
  private readonly originais = signal<ValoresEntidade>(this.valoresDe(null));

  readonly formulario = new FormGroup({
    codigo: new FormControl('', { nonNullable: true, validators: [naoEmBranco, Validators.maxLength(20)] }),
    descricaoIncm: new FormControl('', { nonNullable: true, validators: [naoEmBranco, Validators.maxLength(200)] }),
    descricaoSaigt: new FormControl('', { nonNullable: true, validators: [naoEmBranco, Validators.maxLength(200)] }),
    tipoEntidade: new FormControl<TipoEntidade | null>(null, Validators.required),
    nipc: new FormControl('', { nonNullable: true, validators: Validators.pattern(/^\d{9}$/) }),
    dico: new FormControl<string | null>(null),
  });

  private readonly estadoFormulario = toSignal(this.formulario.events, { initialValue: null });

  readonly titulo = computed(() => (this.modo() === 'criar' ? 'Adicionar entidade' : 'Editar entidade'));

  readonly ehCamara = computed(() => {
    this.estadoFormulario();
    return this.formulario.controls.tipoEntidade.value === 'CM';
  });

  readonly opcoesConcelho = computed<OpcaoSeletor[]>(() =>
    this.concelhos().map((concelho) => ({
      valor: concelho.codigo,
      rotulo: concelho.nut2 ? `${concelho.nome} (${concelho.nut2})` : concelho.nome,
    })),
  );

  readonly erros = computed<Record<CampoEntidade, string | null>>(() => {
    this.estadoFormulario();
    const daApi = this.errosApi();
    const erros = {} as Record<CampoEntidade, string | null>;
    for (const campo of CAMPOS) {
      const controlo = this.formulario.controls[campo];
      const mostrar = controlo.invalid && (controlo.touched || controlo.dirty);
      erros[campo] = mostrar ? this.mensagem(campo, controlo) : (daApi[campo] ?? null);
    }
    return erros;
  });

  /** No modo editar, só o que mudou face ao que a entidade tinha; vazio limpa o NIPC e o concelho. */
  readonly alteracoes = computed<AtualizarEntidadeRequest>(() => {
    this.estadoFormulario();
    const atuais = this.valoresAtuais();
    const originais = this.originais();
    const alteracoes: AtualizarEntidadeRequest = {};
    if (atuais.codigo !== originais.codigo) alteracoes.codigo = atuais.codigo;
    if (atuais.descricaoIncm !== originais.descricaoIncm) alteracoes.descricaoIncm = atuais.descricaoIncm;
    if (atuais.descricaoSaigt !== originais.descricaoSaigt) alteracoes.descricaoSaigt = atuais.descricaoSaigt;
    if (atuais.tipoEntidade !== originais.tipoEntidade && atuais.tipoEntidade) alteracoes.tipoEntidade = atuais.tipoEntidade;
    if (atuais.nipc !== originais.nipc) alteracoes.nipc = atuais.nipc;
    if (atuais.dico !== originais.dico) alteracoes.dico = atuais.dico;
    return alteracoes;
  });

  readonly avisoProximoLogin = computed(() => {
    const alteracoes = this.alteracoes();
    return this.modo() === 'editar' && (alteracoes.tipoEntidade !== undefined || alteracoes.dico !== undefined);
  });

  readonly podeGravar = computed(() => {
    this.estadoFormulario();
    const semAlteracoes = this.modo() === 'editar' && Object.keys(this.alteracoes()).length === 0;
    return this.formulario.valid && !this.aGravar() && !semAlteracoes;
  });

  constructor() {
    effect(() => {
      if (this.aberto()) {
        untracked(() => this.preparar());
      }
    });
    this.formulario.controls.tipoEntidade.valueChanges.pipe(takeUntilDestroyed()).subscribe((tipo) => {
      if (tipo !== 'CM' && this.formulario.controls.dico.value) {
        this.formulario.controls.dico.setValue(null);
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
      return;
    }
    const entidade = this.entidade();
    const pedido =
      this.modo() === 'editar' && entidade
        ? this.entidadeService.atualizar(entidade.id, this.alteracoes())
        : this.entidadeService.criar(this.pedidoCriar());
    this.errosApi.set({});
    this.erroGeral.set(null);
    this.aGravar.set(true);
    pedido.subscribe({
      next: (gravada) => {
        this.aGravar.set(false);
        this.gravada.emit(gravada);
      },
      error: (erro: ApiError) => {
        this.aGravar.set(false);
        this.tratarErro(erro);
      },
    });
  }

  private pedidoCriar(): CriarEntidadeRequest {
    const valores = this.valoresAtuais();
    const pedido: CriarEntidadeRequest = {
      codigo: valores.codigo,
      descricaoIncm: valores.descricaoIncm,
      descricaoSaigt: valores.descricaoSaigt,
      tipoEntidade: valores.tipoEntidade as TipoEntidade,
    };
    if (valores.nipc) pedido.nipc = valores.nipc;
    if (valores.dico) pedido.dico = valores.dico;
    return pedido;
  }

  /** O código, o NIPC e o DTCC vão sem espaços nas pontas; as descrições vão como escritas (a INCM compara-as no login). */
  private valoresAtuais(): ValoresEntidade {
    const valores = this.formulario.getRawValue();
    return {
      codigo: valores.codigo.trim(),
      descricaoIncm: valores.descricaoIncm,
      descricaoSaigt: valores.descricaoSaigt,
      tipoEntidade: valores.tipoEntidade,
      nipc: valores.nipc.trim(),
      dico: valores.tipoEntidade === 'CM' ? (valores.dico ?? '').trim() : '',
    };
  }

  private valoresDe(entidade: Entidade | null): ValoresEntidade {
    return {
      codigo: entidade?.codigo ?? '',
      descricaoIncm: entidade?.descricaoIncm ?? '',
      descricaoSaigt: entidade?.descricaoSaigt ?? '',
      tipoEntidade: entidade?.tipoEntidade ?? null,
      nipc: entidade?.nipc ?? '',
      dico: entidade?.dico ?? '',
    };
  }

  private mensagem(campo: CampoEntidade, controlo: AbstractControl): string {
    if (controlo.hasError('maxlength')) {
      return `Tem de ter no máximo ${controlo.getError('maxlength').requiredLength} caracteres.`;
    }
    return MENSAGENS[campo];
  }

  private tratarErro(erro: ApiError): void {
    const porCampo: Partial<Record<CampoEntidade, string>> = {};
    for (const campo of erro.codigo === 'VALIDACAO' ? (erro.campos ?? []) : []) {
      if ((CAMPOS as readonly string[]).includes(campo.path)) {
        porCampo[campo.path as CampoEntidade] = campo.erro;
      }
    }
    if (Object.keys(porCampo).length > 0) {
      this.errosApi.set(porCampo);
    } else {
      this.erroGeral.set(erro.mensagem);
    }
  }

  private preparar(): void {
    const valores = this.valoresDe(this.modo() === 'editar' ? this.entidade() : null);
    this.originais.set(valores);
    this.formulario.reset({ ...valores, dico: valores.dico || null });
    this.errosApi.set({});
    this.erroGeral.set(null);
    this.aGravar.set(false);
    this.carregarConcelhos();
  }

  /** Sem a lista, o Concelho fica desativado com o aviso e o resto do formulário continua a funcionar. */
  private carregarConcelhos(): void {
    this.concelhosErro.set(false);
    this.formulario.controls.dico.enable({ emitEvent: false });
    this.referenciaSnitService.concelhos().subscribe({
      next: (concelhos) => this.concelhos.set(concelhos),
      error: () => {
        this.concelhos.set([]);
        this.concelhosErro.set(true);
        this.formulario.controls.dico.disable({ emitEvent: false });
      },
    });
  }
}
