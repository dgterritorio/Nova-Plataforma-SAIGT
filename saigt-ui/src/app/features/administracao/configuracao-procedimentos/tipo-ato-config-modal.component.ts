import { Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { ApiError } from '../../../core/models/error-response.model';
import {
  PADRAO_CODIGO,
  TAMANHO_MAXIMO_CODIGO_ATO_REDE,
  TAMANHO_MAXIMO_NOME,
  TipoAtoConfig,
  TipoAtoConfigRequest,
} from '../../../models/configuracao.model';
import { AlertaComponent } from '../../../shared/ui/alerta/alerta.component';
import { BotaoComponent } from '../../../shared/ui/botao/botao.component';
import { CampoTextoComponent } from '../../../shared/ui/campo-texto/campo-texto.component';
import { ModalComponent } from '../../../shared/ui/modal/modal.component';
import { inteiroPositivo, numeroOuNulo, separarErroServidor } from './erros-formulario';

type CampoTipoAto = 'codigo' | 'nome' | 'incmActTypeIdSerie1' | 'incmActTypeIdSerie2' | 'ordem';

const CAMPOS: readonly CampoTipoAto[] = ['codigo', 'nome', 'incmActTypeIdSerie1', 'incmActTypeIdSerie2', 'ordem'];

export const ERRO_SEM_SERIE = 'Indique pelo menos uma série.';

function peloMenosUmaSerie(grupo: AbstractControl): ValidationErrors | null {
  const valor = grupo.value as { incmActTypeIdSerie1: string | null; incmActTypeIdSerie2: string | null };
  return numeroOuNulo(valor.incmActTypeIdSerie1) === null && numeroOuNulo(valor.incmActTypeIdSerie2) === null ? { semSerie: true } : null;
}

let proximoId = 0;

/**
 * Cria ou edita um tipo de ato. "Guardar" fica sempre ativo: o erro de grupo (pelo menos uma série) só aparece
 * quando o Master tenta gravar, e não há pedido enquanto houver erros.
 */
@Component({
  selector: 'app-tipo-ato-config-modal',
  imports: [AlertaComponent, BotaoComponent, CampoTextoComponent, ModalComponent, ReactiveFormsModule],
  templateUrl: './tipo-ato-config-modal.component.html',
  styleUrl: './configuracao-formulario.css',
})
export class TipoAtoConfigModalComponent {
  private readonly formBuilder = inject(FormBuilder);

  readonly aberto = input(false);
  /** Nulo: criação. */
  readonly tipoAto = input<TipoAtoConfig | null>(null);
  readonly aGravar = input(false);
  readonly erroServidor = input<ApiError | null>(null);
  readonly guardar = output<TipoAtoConfigRequest>();
  readonly fechar = output<void>();

  readonly idFormulario = `formulario-tipo-ato-${++proximoId}`;
  readonly erroSemSerie = ERRO_SEM_SERIE;
  readonly tamanhoMaximoCodigo = TAMANHO_MAXIMO_CODIGO_ATO_REDE;
  readonly tamanhoMaximoNome = TAMANHO_MAXIMO_NOME;
  readonly titulo = computed(() => (this.tipoAto() ? 'Editar tipo de ato' : 'Adicionar tipo de ato'));

  readonly errosServidor = signal<Partial<Record<CampoTipoAto, string>>>({});
  readonly erroGeral = signal<ApiError | null>(null);
  readonly submetido = signal(false);

  readonly formulario = this.formBuilder.group(
    {
      codigo: this.formBuilder.nonNullable.control('', [
        Validators.required,
        Validators.pattern(PADRAO_CODIGO),
        Validators.maxLength(TAMANHO_MAXIMO_CODIGO_ATO_REDE),
      ]),
      nome: this.formBuilder.nonNullable.control('', [Validators.required, Validators.maxLength(TAMANHO_MAXIMO_NOME)]),
      incmActTypeIdSerie1: this.formBuilder.control<string | null>('', inteiroPositivo),
      incmActTypeIdSerie2: this.formBuilder.control<string | null>('', inteiroPositivo),
      ordem: this.formBuilder.control<string | null>('', [Validators.required, inteiroPositivo]),
    },
    { validators: peloMenosUmaSerie },
  );

  readonly valorFormulario = signal(this.formulario.getRawValue());
  readonly semSerie = computed(() => {
    this.valorFormulario();
    return this.submetido() && this.formulario.hasError('semSerie');
  });

  constructor() {
    effect(() => {
      if (this.aberto()) {
        const tipoAto = this.tipoAto();
        untracked(() => this.preparar(tipoAto));
      }
    });
    effect(() => {
      const erro = this.erroServidor();
      untracked(() => {
        const { porCampo, geral } = separarErroServidor(erro, CAMPOS);
        this.errosServidor.set(porCampo);
        this.erroGeral.set(geral);
      });
    });
    for (const campo of CAMPOS) {
      this.formulario.get(campo)!.valueChanges.subscribe(() => {
        if (this.errosServidor()[campo]) {
          this.errosServidor.update((erros) => ({ ...erros, [campo]: undefined }));
        }
      });
    }
    this.formulario.valueChanges.subscribe(() => this.valorFormulario.set(this.formulario.getRawValue()));
  }

  erroDoCampo(campo: CampoTipoAto): string | null {
    const doServidor = this.errosServidor()[campo];
    if (doServidor) {
      return doServidor;
    }
    const controlo = this.formulario.controls[campo];
    if (controlo.errors && (controlo.dirty || controlo.touched || this.submetido())) {
      if (controlo.errors['required']) {
        return { codigo: 'Indique o código.', nome: 'Indique o nome.', ordem: 'Indique a ordem.' }[campo as 'codigo' | 'nome' | 'ordem'];
      }
      if (controlo.errors['pattern']) {
        return 'Use só letras maiúsculas sem acentos, algarismos e _ (sem espaços).';
      }
      if (controlo.errors['maxlength']) {
        return `Até ${controlo.errors['maxlength'].requiredLength} caracteres.`;
      }
      if (controlo.errors['inteiroPositivo']) {
        return 'Use um número inteiro igual ou superior a 1.';
      }
    }
    if ((campo === 'incmActTypeIdSerie1' || campo === 'incmActTypeIdSerie2') && this.semSerie()) {
      return ERRO_SEM_SERIE;
    }
    return null;
  }

  submeter(): void {
    this.submetido.set(true);
    this.formulario.markAllAsTouched();
    this.valorFormulario.set(this.formulario.getRawValue());
    if (this.formulario.invalid || this.aGravar()) {
      return;
    }
    const valor = this.formulario.getRawValue();
    this.guardar.emit({
      codigo: valor.codigo,
      nome: valor.nome.trim(),
      incmActTypeIdSerie1: numeroOuNulo(valor.incmActTypeIdSerie1),
      incmActTypeIdSerie2: numeroOuNulo(valor.incmActTypeIdSerie2),
      ordem: Number(valor.ordem),
      ativo: this.tipoAto()?.ativo ?? true,
    });
  }

  private preparar(tipoAto: TipoAtoConfig | null): void {
    this.submetido.set(false);
    this.errosServidor.set({});
    this.erroGeral.set(null);
    this.formulario.reset({
      codigo: tipoAto?.codigo ?? '',
      nome: tipoAto?.nome ?? '',
      incmActTypeIdSerie1: tipoAto?.incmActTypeIdSerie1 == null ? '' : String(tipoAto.incmActTypeIdSerie1),
      incmActTypeIdSerie2: tipoAto?.incmActTypeIdSerie2 == null ? '' : String(tipoAto.incmActTypeIdSerie2),
      ordem: tipoAto ? String(tipoAto.ordem) : '',
    });
  }
}
