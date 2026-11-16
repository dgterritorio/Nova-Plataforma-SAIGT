import { Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ApiError } from '../../../core/models/error-response.model';
import {
  CatalogoTipos,
  CategoriaTipoInstrumento,
  PADRAO_CODIGO,
  ROTULOS_CATEGORIA,
  TAMANHO_MAXIMO_CODIGO,
  TAMANHO_MAXIMO_NOME,
  TipoConfig,
  TipoConfigRequest,
} from '../../../models/configuracao.model';
import { AlertaComponent } from '../../../shared/ui/alerta/alerta.component';
import { BotaoComponent } from '../../../shared/ui/botao/botao.component';
import { CampoTextoComponent } from '../../../shared/ui/campo-texto/campo-texto.component';
import { ModalComponent } from '../../../shared/ui/modal/modal.component';
import { OpcaoSeletor, SeletorComponent } from '../../../shared/ui/seletor/seletor.component';

type CampoTipo = 'codigo' | 'nome' | 'categoria';

const CAMPOS: readonly CampoTipo[] = ['codigo', 'nome', 'categoria'];

const NOMES_CATALOGO: Record<CatalogoTipos, string> = {
  instrumento: 'Tipos de Instrumento',
  dinamica: 'Tipos de Dinâmica',
  procedimento: 'Tipos de Procedimento',
};

let proximoId = 0;

/** Cria ou edita um tipo de um dos três catálogos. O pedido é feito por quem o abre, que devolve o erro em `erroServidor`. */
@Component({
  selector: 'app-tipo-config-modal',
  imports: [AlertaComponent, BotaoComponent, CampoTextoComponent, ModalComponent, SeletorComponent, ReactiveFormsModule],
  templateUrl: './tipo-config-modal.component.html',
  styles: `
    .formulario {
      display: flex;
      flex-direction: column;
      gap: var(--espaco-4);
    }

    [rodape] {
      display: contents;
    }
  `,
})
export class TipoConfigModalComponent {
  private readonly formBuilder = inject(FormBuilder);

  readonly aberto = input(false);
  readonly catalogo = input.required<CatalogoTipos>();
  /** Nulo: criação. */
  readonly tipo = input<TipoConfig | null>(null);
  readonly aGravar = input(false);
  readonly erroServidor = input<ApiError | null>(null);
  readonly guardar = output<TipoConfigRequest>();
  readonly fechar = output<void>();

  readonly idFormulario = `formulario-tipo-${++proximoId}`;
  readonly tamanhoMaximoNome = TAMANHO_MAXIMO_NOME;
  readonly opcoesCategoria: readonly OpcaoSeletor[] = (Object.keys(ROTULOS_CATEGORIA) as CategoriaTipoInstrumento[]).map(
    (valor) => ({ valor, rotulo: ROTULOS_CATEGORIA[valor] }),
  );

  readonly titulo = computed(() => (this.tipo() ? 'Editar tipo' : 'Adicionar tipo'));
  readonly subtitulo = computed(() => NOMES_CATALOGO[this.catalogo()]);
  readonly tamanhoMaximoCodigo = computed(() => TAMANHO_MAXIMO_CODIGO[this.catalogo()]);
  readonly comCategoria = computed(() => this.catalogo() === 'instrumento');
  readonly avisoSemFormulario = computed(() => this.catalogo() === 'procedimento' && !this.tipo());

  /** Erros devolvidos pelo servidor, por campo; saem quando o campo muda. */
  readonly errosServidor = signal<Partial<Record<CampoTipo, string>>>({});
  /** 409 `EM_USO` ou outro erro sem campo a assinalar. */
  readonly erroGeral = signal<ApiError | null>(null);
  private submetido = false;

  readonly formulario = this.formBuilder.nonNullable.group({
    codigo: ['', [Validators.required, Validators.pattern(PADRAO_CODIGO)]],
    nome: ['', [Validators.required, Validators.maxLength(TAMANHO_MAXIMO_NOME)]],
    categoria: this.formBuilder.control<string | null>(null),
  });

  constructor() {
    effect(() => {
      if (this.aberto()) {
        const tipo = this.tipo();
        const catalogo = this.catalogo();
        untracked(() => this.preparar(catalogo, tipo));
      }
    });
    effect(() => {
      const erro = this.erroServidor();
      untracked(() => this.mostrarErroServidor(erro));
    });
    for (const campo of CAMPOS) {
      this.formulario.controls[campo].valueChanges.subscribe(() => {
        if (this.errosServidor()[campo]) {
          this.errosServidor.update((erros) => ({ ...erros, [campo]: undefined }));
        }
      });
    }
  }

  erroDoCampo(campo: CampoTipo): string | null {
    const doServidor = this.errosServidor()[campo];
    if (doServidor) {
      return doServidor;
    }
    const controlo = this.formulario.controls[campo];
    if (!controlo.errors || !(controlo.dirty || controlo.touched || this.submetido)) {
      return null;
    }
    if (controlo.errors['required']) {
      return campo === 'categoria' ? 'Escolha a categoria.' : campo === 'codigo' ? 'Indique o código.' : 'Indique o nome.';
    }
    if (controlo.errors['pattern']) {
      return 'Use só letras maiúsculas sem acentos, algarismos e _ (sem espaços).';
    }
    if (controlo.errors['maxlength']) {
      return `Até ${controlo.errors['maxlength'].requiredLength} caracteres.`;
    }
    return null;
  }

  submeter(): void {
    this.submetido = true;
    this.formulario.markAllAsTouched();
    if (this.formulario.invalid || this.aGravar()) {
      return;
    }
    const valor = this.formulario.getRawValue();
    const base = { codigo: valor.codigo, nome: valor.nome.trim(), ativo: this.tipo()?.ativo ?? true };
    this.guardar.emit(this.comCategoria() ? { ...base, categoria: valor.categoria as CategoriaTipoInstrumento } : base);
  }

  private preparar(catalogo: CatalogoTipos, tipo: TipoConfig | null): void {
    this.submetido = false;
    this.errosServidor.set({});
    this.erroGeral.set(null);
    const controlos = this.formulario.controls;
    controlos.codigo.setValidators([
      Validators.required,
      Validators.pattern(PADRAO_CODIGO),
      Validators.maxLength(TAMANHO_MAXIMO_CODIGO[catalogo]),
    ]);
    controlos.categoria.setValidators(catalogo === 'instrumento' ? [Validators.required] : []);
    this.formulario.reset({
      codigo: tipo?.codigo ?? '',
      nome: tipo?.nome ?? '',
      categoria: tipo && 'categoria' in tipo ? tipo.categoria : null,
    });
  }

  private mostrarErroServidor(erro: ApiError | null): void {
    if (!erro) {
      this.errosServidor.set({});
      this.erroGeral.set(null);
      return;
    }
    const porCampo: Partial<Record<CampoTipo, string>> = {};
    for (const campo of erro.campos ?? []) {
      if ((CAMPOS as readonly string[]).includes(campo.path)) {
        porCampo[campo.path as CampoTipo] = campo.erro;
      }
    }
    this.errosServidor.set(porCampo);
    this.erroGeral.set(Object.keys(porCampo).length > 0 ? null : erro);
  }
}
