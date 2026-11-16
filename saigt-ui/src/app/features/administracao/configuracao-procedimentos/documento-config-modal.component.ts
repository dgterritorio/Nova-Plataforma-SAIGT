import { Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { ApiError } from '../../../core/models/error-response.model';
import { CategoriaDocumento, FormatoDocumental, TipoElementoDocumental } from '../../../models/documento.model';
import {
  AtualizarDocumentoConfigPedido,
  BYTES_POR_MB,
  CompressaoTiff,
  DocumentoConfig,
  EXTENSOES_EF,
  PADRAO_CODIGO,
  REGRAS_TIFF_INICIAIS,
  ROTULOS_CAIXA,
  ROTULOS_FORMATO,
  ROTULOS_INFORMACAO_BASE,
  ROTULOS_REGRA_NOMENCLATURA,
  ROTULOS_TIPO_ELEMENTO,
  RegraNomenclatura,
  RegrasTiff,
} from '../../../models/configuracao.model';
import { ElementoInformacaoBase } from '../../../models/processo.model';
import { AlertaComponent } from '../../../shared/ui/alerta/alerta.component';
import { BotaoComponent } from '../../../shared/ui/botao/botao.component';
import { CampoTextoComponent } from '../../../shared/ui/campo-texto/campo-texto.component';
import { ModalComponent } from '../../../shared/ui/modal/modal.component';
import { OpcaoSeletor, SeletorComponent } from '../../../shared/ui/seletor/seletor.component';
import { inteiroPositivo, numeroOuNulo } from './erros-formulario';

/** O que o modal devolve: o pedido do `PUT`, mais o código quando cria. */
export interface ValorDocumentoConfig {
  codigo: string | null;
  pedido: AtualizarDocumentoConfigPedido;
}

type CampoDocumentoConfig =
  | 'codigo'
  | 'nome'
  | 'separador'
  | 'descricao'
  | 'categoria'
  | 'tipoElemento'
  | 'formato'
  | 'extensoesAceites'
  | 'ordem'
  | 'elementoInformacaoBase'
  | 'regraNomenclatura'
  | 'tamanhoMaximoFicheiro'
  | 'tamanhoMaximoTotal'
  | 'numeroMaximoFicheiros'
  | 'sistemasReferencia'
  | 'compressoesAceites'
  | 'modeloCor'
  | 'bitsPorBanda'
  | 'resolucaoDpi'
  | 'elementosLegenda';

/** `path` do envelope de erro → campo do formulário; `regrasTiff` sozinho vai para o grupo TIFF. */
const CAMPO_DO_PATH: Record<string, CampoDocumentoConfig | 'regrasTiff'> = {
  codigo: 'codigo',
  nome: 'nome',
  separador: 'separador',
  descricao: 'descricao',
  categoria: 'categoria',
  tipoElemento: 'tipoElemento',
  formato: 'formato',
  extensoesAceites: 'extensoesAceites',
  ordem: 'ordem',
  elementoInformacaoBase: 'elementoInformacaoBase',
  regraNomenclatura: 'regraNomenclatura',
  tamanhoMaximoFicheiro: 'tamanhoMaximoFicheiro',
  tamanhoMaximoTotal: 'tamanhoMaximoTotal',
  numeroMaximoFicheiros: 'numeroMaximoFicheiros',
  regrasTiff: 'regrasTiff',
  'regrasTiff.sistemasReferencia': 'sistemasReferencia',
  'regrasTiff.compressoesAceites': 'compressoesAceites',
  'regrasTiff.modeloCor': 'modeloCor',
  'regrasTiff.bitsPorBanda': 'bitsPorBanda',
  'regrasTiff.resolucaoDpi': 'resolucaoDpi',
  'regrasTiff.elementosLegenda': 'elementosLegenda',
};

const CAMPOS_TIFF = ['sistemasReferencia', 'compressaoNone', 'compressaoLzw', 'bandaAlfaTolerada', 'modeloCor', 'bitsPorBanda', 'resolucaoDpi', 'elementosLegenda'] as const;

const PADRAO_EXTENSAO = /^\.?[A-Za-z0-9]{1,10}$/;
const PADRAO_EPSG = /^EPSG:[0-9]{4,5}$/;
const TAMANHO_MAXIMO_ELEMENTO_LEGENDA = 100;

export const AJUDA_SEPARADOR = 'Itens com o mesmo separador aparecem juntos no ecrã de submissão.';
export const AJUDA_LEGENDA = 'Um por linha. Verificados pela DGT na checklist, não automaticamente.';

function lista(texto: string | null, separador: RegExp): string[] {
  return (texto ?? '')
    .split(separador)
    .map((parte) => parte.trim())
    .filter((parte) => parte !== '');
}

function listaExtensoes(controlo: AbstractControl<string | null>): ValidationErrors | null {
  const extensoes = lista(controlo.value, /,/);
  if (extensoes.length === 0) {
    return null;
  }
  return extensoes.every((extensao) => PADRAO_EXTENSAO.test(extensao)) ? null : { extensoes: true };
}

function listaSistemasReferencia(controlo: AbstractControl<string | null>): ValidationErrors | null {
  const sistemas = lista(controlo.value, /,/);
  const validos = sistemas.every((sistema) => PADRAO_EPSG.test(sistema)) && new Set(sistemas).size === sistemas.length;
  return validos ? null : { sistemasReferencia: true };
}

function listaLegenda(controlo: AbstractControl<string | null>): ValidationErrors | null {
  return lista(controlo.value, /\n/).every((elemento) => elemento.length <= TAMANHO_MAXIMO_ELEMENTO_LEGENDA) ? null : { legenda: true };
}

function opcoes<T extends string>(rotulos: Record<T, string>): OpcaoSeletor[] {
  return (Object.keys(rotulos) as T[]).map((valor) => ({ valor, rotulo: rotulos[valor] }));
}

/** Sem arredondar: um limite gravado que não é um número inteiro de MB volta a dar os mesmos bytes ao gravar. */
function megabytes(bytes: number | null): string {
  return bytes === null ? '' : String(bytes / BYTES_POR_MB);
}

function bytesDe(texto: string | null): number | null {
  const valor = (texto ?? '').trim();
  return valor === '' ? null : Math.round(Number(valor.replace(',', '.')) * BYTES_POR_MB);
}

function megabytesPositivo(controlo: AbstractControl<string | null>): ValidationErrors | null {
  const valor = (controlo.value ?? '').trim();
  if (valor === '') {
    return null;
  }
  return /^\d+([.,]\d+)?$/.test(valor) && (bytesDe(valor) ?? 0) >= 1 ? null : { megabytes: true };
}

let proximoId = 0;

/**
 * Cria ou edita um item documental de um Procedimento. Os limites editam-se em MB e seguem em bytes; o grupo das
 * regras do GeoTIFF só existe numa peça gráfica, e só aí o pedido leva `regrasTiff`.
 */
@Component({
  selector: 'app-documento-config-modal',
  imports: [AlertaComponent, BotaoComponent, CampoTextoComponent, ModalComponent, SeletorComponent, ReactiveFormsModule],
  templateUrl: './documento-config-modal.component.html',
  styleUrls: ['./configuracao-formulario.css', './documento-config-modal.component.css'],
})
export class DocumentoConfigModalComponent {
  private readonly formBuilder = inject(FormBuilder);

  readonly aberto = input(false);
  /** Ex.: "PDM — Alteração — Decisão de início". */
  readonly procedimento = input.required<string>();
  /** Nulo: criação. */
  readonly item = input<DocumentoConfig | null>(null);
  readonly aGravar = input(false);
  readonly erroServidor = input<ApiError | null>(null);
  readonly guardar = output<ValorDocumentoConfig>();
  readonly fechar = output<void>();

  readonly idFormulario = `formulario-documento-config-${++proximoId}`;
  readonly idAjudaExtensoes = `${this.idFormulario}-extensoes-ef`;
  readonly ajudaSeparador = AJUDA_SEPARADOR;
  readonly ajudaLegenda = AJUDA_LEGENDA;
  readonly opcoesCaixa = opcoes(ROTULOS_CAIXA);
  readonly opcoesTipo = opcoes(ROTULOS_TIPO_ELEMENTO);
  readonly opcoesFormato = opcoes(ROTULOS_FORMATO);
  readonly opcoesInformacaoBase: readonly OpcaoSeletor[] = [{ valor: '', rotulo: 'Nenhuma' }, ...opcoes(ROTULOS_INFORMACAO_BASE)];
  readonly opcoesRegraNome = opcoes(ROTULOS_REGRA_NOMENCLATURA);
  readonly opcoesModeloCor: readonly OpcaoSeletor[] = [
    { valor: '', rotulo: 'Não verificar' },
    { valor: 'RGB', rotulo: 'RGB' },
  ];
  readonly titulo = computed(() => {
    const item = this.item();
    return item ? `Editar ${item.nome}` : 'Novo item documental';
  });

  readonly tipoEscolhido = signal<TipoElementoDocumental | null>(null);
  readonly caixaEscolhida = signal<CategoriaDocumento | null>(null);
  readonly pecaGrafica = computed(() => this.tipoEscolhido() === 'PECA_GRAFICA');
  readonly extensoesEf = computed(() => {
    const tipo = this.tipoEscolhido();
    const caixa = this.caixaEscolhida();
    if (!tipo || !caixa) {
      return 'Escolha a caixa e o tipo de elemento para ver as extensões da EF.';
    }
    const coluna = EXTENSOES_EF[tipo]?.[caixa];
    return coluna
      ? `Extensões da EF v1.5 para ${ROTULOS_TIPO_ELEMENTO[tipo].toLowerCase()} de ${ROTULOS_CAIXA[caixa].toLowerCase()}: ${coluna.join(', ')}.`
      : 'A EF v1.5 não restringe as extensões deste tipo de elemento.';
  });

  readonly errosServidor = signal<Partial<Record<CampoDocumentoConfig, string>>>({});
  readonly erroTiff = signal<string | null>(null);
  readonly erroGeral = signal<ApiError | null>(null);
  private submetido = false;

  readonly formulario = this.formBuilder.group({
    codigo: this.formBuilder.control<string | null>('', [Validators.required, Validators.maxLength(50), Validators.pattern(PADRAO_CODIGO)]),
    nome: this.formBuilder.control<string | null>('', [Validators.required, Validators.maxLength(100)]),
    separador: this.formBuilder.control<string | null>('', [Validators.required, Validators.maxLength(100)]),
    descricao: this.formBuilder.control<string | null>('', Validators.maxLength(300)),
    categoria: this.formBuilder.control<string | null>(null, Validators.required),
    tipoElemento: this.formBuilder.control<string | null>(null, Validators.required),
    formato: this.formBuilder.control<string | null>(null, Validators.required),
    extensoesAceites: this.formBuilder.control<string | null>('', [Validators.required, listaExtensoes]),
    obrigatorio: this.formBuilder.nonNullable.control(false),
    ordem: this.formBuilder.control<string | null>('', [Validators.required, inteiroPositivo]),
    elementoInformacaoBase: this.formBuilder.control<string | null>(''),
    regraNomenclatura: this.formBuilder.control<string | null>('GERAL', Validators.required),
    /** Em MB. */
    tamanhoMaximoFicheiro: this.formBuilder.control<string | null>('', megabytesPositivo),
    /** Em MB. */
    tamanhoMaximoTotal: this.formBuilder.control<string | null>('', megabytesPositivo),
    numeroMaximoFicheiros: this.formBuilder.control<string | null>('', inteiroPositivo),
    sistemasReferencia: this.formBuilder.control<string | null>('', listaSistemasReferencia),
    compressaoNone: this.formBuilder.nonNullable.control(true),
    compressaoLzw: this.formBuilder.nonNullable.control(true),
    bandaAlfaTolerada: this.formBuilder.nonNullable.control(true),
    modeloCor: this.formBuilder.control<string | null>(''),
    bitsPorBanda: this.formBuilder.control<string | null>('', inteiroPositivo),
    resolucaoDpi: this.formBuilder.control<string | null>('', inteiroPositivo),
    elementosLegenda: this.formBuilder.control<string | null>('', listaLegenda),
    ativo: this.formBuilder.nonNullable.control(true),
  }, { validators: (grupo) => this.compressoesEmFalta(grupo) });

  constructor() {
    effect(() => {
      if (this.aberto()) {
        const item = this.item();
        untracked(() => this.preparar(item));
      }
    });
    effect(() => {
      const erro = this.erroServidor();
      untracked(() => this.aplicarErroServidor(erro));
    });
    for (const campo of Object.keys(this.formulario.controls)) {
      this.formulario.get(campo)!.valueChanges.subscribe(() => this.limparErroServidor(campo));
    }
    this.formulario.controls.tipoElemento.valueChanges.subscribe((valor) => {
      this.tipoEscolhido.set(valor as TipoElementoDocumental | null);
      this.ativarGrupoTiff();
    });
    this.formulario.controls.categoria.valueChanges.subscribe((valor) => {
      this.caixaEscolhida.set(valor as CategoriaDocumento | null);
      this.proporFormato();
    });
  }

  erroDoCampo(campo: CampoDocumentoConfig): string | null {
    const doServidor = this.errosServidor()[campo];
    if (doServidor) {
      return doServidor;
    }
    if (campo === 'compressoesAceites') {
      const mostrar = this.formulario.errors?.['compressoes'] && (this.submetido || this.formulario.controls.compressaoNone.dirty || this.formulario.controls.compressaoLzw.dirty);
      return mostrar ? 'Marque pelo menos uma compressão aceite.' : null;
    }
    const controlo = this.formulario.controls[campo];
    if (!controlo.errors || !(controlo.dirty || controlo.touched || this.submetido)) {
      return null;
    }
    const erros = controlo.errors;
    if (erros['required']) {
      return {
        codigo: 'Indique o código.',
        nome: 'Indique o nome.',
        separador: 'Indique o separador.',
        categoria: 'Escolha a caixa.',
        tipoElemento: 'Escolha o tipo de elemento.',
        formato: 'Escolha o formato.',
        extensoesAceites: 'Indique pelo menos uma extensão.',
        ordem: 'Indique a ordem.',
        regraNomenclatura: 'Escolha a regra de nome.',
      }[campo as string] ?? 'Campo obrigatório.';
    }
    if (erros['maxlength']) {
      return `Use no máximo ${erros['maxlength'].requiredLength} caracteres.`;
    }
    if (erros['pattern']) {
      return 'Use só maiúsculas, algarismos e «_».';
    }
    if (erros['extensoes']) {
      return 'Separe as extensões por vírgulas; cada uma só com letras e algarismos (até 10).';
    }
    if (erros['sistemasReferencia']) {
      return 'Use o formato EPSG:<código>, separados por vírgulas e sem repetidos.';
    }
    if (erros['legenda']) {
      return `Cada elemento da legenda tem no máximo ${TAMANHO_MAXIMO_ELEMENTO_LEGENDA} caracteres.`;
    }
    if (erros['megabytes']) {
      return 'Use um número de MB superior a 0 (ex.: 500 ou 0,5), ou deixe vazio.';
    }
    if (erros['inteiroPositivo']) {
      return campo === 'ordem'
        ? 'Use um número inteiro igual ou superior a 1.'
        : 'Use um número inteiro igual ou superior a 1, ou deixe vazio.';
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
    const pedido: AtualizarDocumentoConfigPedido = {
      nome: (valor.nome ?? '').trim(),
      separador: (valor.separador ?? '').trim(),
      descricao: (valor.descricao ?? '').trim() || null,
      categoria: valor.categoria as CategoriaDocumento,
      tipoElemento: valor.tipoElemento as TipoElementoDocumental,
      formato: valor.formato as FormatoDocumental,
      extensoesAceites: lista(valor.extensoesAceites, /,/),
      obrigatorio: valor.obrigatorio,
      ordem: Number(valor.ordem),
      elementoInformacaoBase: (valor.elementoInformacaoBase || null) as ElementoInformacaoBase | null,
      tamanhoMaximoFicheiro: bytesDe(valor.tamanhoMaximoFicheiro),
      tamanhoMaximoTotal: bytesDe(valor.tamanhoMaximoTotal),
      numeroMaximoFicheiros: numeroOuNulo(valor.numeroMaximoFicheiros),
      linkPublicoPermitido: this.linkPublicoPermitido(valor.categoria as CategoriaDocumento),
      ativo: valor.ativo,
      regraNomenclatura: valor.regraNomenclatura as RegraNomenclatura,
    };
    if (pedido.tipoElemento === 'PECA_GRAFICA') {
      const compressoes: CompressaoTiff[] = [];
      if (valor.compressaoNone) compressoes.push('NONE');
      if (valor.compressaoLzw) compressoes.push('LZW');
      pedido.regrasTiff = {
        sistemasReferencia: lista(valor.sistemasReferencia, /,/),
        compressoesAceites: compressoes,
        bandaAlfaTolerada: valor.bandaAlfaTolerada,
        modeloCor: valor.modeloCor === 'RGB' ? 'RGB' : null,
        bitsPorBanda: numeroOuNulo(valor.bitsPorBanda),
        resolucaoDpi: numeroOuNulo(valor.resolucaoDpi),
        elementosLegenda: lista(valor.elementosLegenda, /\n/),
      };
    }
    this.guardar.emit({ codigo: this.item() ? null : (valor.codigo ?? '').trim(), pedido });
  }

  /**
   * Os itens de publicação têm link público (DB-DOC-001): sem ele não seguem para a INCM. Ao criar ou mudar de caixa
   * parte da caixa; de resto mantém-se o gravado, que o ecrã não edita.
   */
  private linkPublicoPermitido(categoria: CategoriaDocumento): boolean {
    const item = this.item();
    return item && item.categoria === categoria ? item.linkPublicoPermitido : categoria === 'PUBLICACAO';
  }

  private compressoesEmFalta(grupo: AbstractControl): ValidationErrors | null {
    if (grupo.get('tipoElemento')?.value !== 'PECA_GRAFICA') {
      return null;
    }
    return grupo.get('compressaoNone')?.value || grupo.get('compressaoLzw')?.value ? null : { compressoes: true };
  }

  /** Numa peça escrita, a caixa propõe o formato do BL-DOC-023; a escolha fica livre e o servidor decide. */
  private proporFormato(): void {
    const { categoria, tipoElemento, formato } = this.formulario.controls;
    if (tipoElemento.value !== 'PECA_ESCRITA' || !categoria.value) {
      return;
    }
    formato.setValue(categoria.value === 'PUBLICACAO' ? 'EDITAVEL' : 'NAO_EDITAVEL');
    formato.markAsDirty();
  }

  /** Fora das peças gráficas o grupo não existe: os campos desativados não contam para a validade. */
  private ativarGrupoTiff(): void {
    for (const campo of CAMPOS_TIFF) {
      const controlo = this.formulario.controls[campo];
      if (this.pecaGrafica()) {
        controlo.enable({ emitEvent: false });
      } else {
        controlo.disable({ emitEvent: false });
      }
    }
    this.formulario.updateValueAndValidity({ emitEvent: false });
  }

  private aplicarErroServidor(erro: ApiError | null): void {
    const porCampo: Partial<Record<CampoDocumentoConfig, string>> = {};
    let tiff: string | null = null;
    const semCampo: string[] = [];
    for (const campo of erro?.campos ?? []) {
      const destino = CAMPO_DO_PATH[campo.path.replace(/\[\d+\]/g, '')];
      if (destino === 'regrasTiff') {
        tiff = campo.erro;
      } else if (destino) {
        porCampo[destino] ??= campo.erro;
      } else {
        semCampo.push(campo.erro);
      }
    }
    this.errosServidor.set(porCampo);
    this.erroTiff.set(tiff);
    if (!erro) {
      this.erroGeral.set(null);
    } else if (semCampo.length > 0) {
      // Um path que o formulário não tem não pode perder-se: a mensagem vai para o alerta do topo.
      this.erroGeral.set(new ApiError(erro.codigo, semCampo.join(' '), erro.traceId, erro.campos));
    } else {
      this.erroGeral.set(Object.keys(porCampo).length === 0 && tiff === null ? erro : null);
    }
  }

  private limparErroServidor(campo: string): void {
    const destino = campo === 'compressaoNone' || campo === 'compressaoLzw' ? 'compressoesAceites' : campo;
    if (this.errosServidor()[destino as CampoDocumentoConfig]) {
      this.errosServidor.update((erros) => ({ ...erros, [destino]: undefined }));
    }
    if (campo === 'tipoElemento') {
      this.erroTiff.set(null);
    }
  }

  private preparar(item: DocumentoConfig | null): void {
    this.submetido = false;
    this.errosServidor.set({});
    this.erroTiff.set(null);
    this.erroGeral.set(null);
    const regras: RegrasTiff = item?.regrasTiff ?? REGRAS_TIFF_INICIAIS;
    this.formulario.reset(
      {
        codigo: item?.codigo ?? '',
        nome: item?.nome ?? '',
        separador: item?.separador ?? '',
        descricao: item?.descricao ?? '',
        categoria: item?.categoria ?? null,
        tipoElemento: item?.tipoElemento ?? null,
        formato: item?.formato ?? null,
        extensoesAceites: item ? item.extensoesAceites.join(', ') : '',
        obrigatorio: item?.obrigatorio ?? false,
        ordem: item ? String(item.ordem) : '',
        elementoInformacaoBase: item?.elementoInformacaoBase ?? '',
        regraNomenclatura: item?.regraNomenclatura ?? 'GERAL',
        tamanhoMaximoFicheiro: megabytes(item?.tamanhoMaximoFicheiro ?? null),
        tamanhoMaximoTotal: megabytes(item?.tamanhoMaximoTotal ?? null),
        numeroMaximoFicheiros: item?.numeroMaximoFicheiros == null ? '' : String(item.numeroMaximoFicheiros),
        sistemasReferencia: regras.sistemasReferencia.join(', '),
        compressaoNone: regras.compressoesAceites.includes('NONE'),
        compressaoLzw: regras.compressoesAceites.includes('LZW'),
        bandaAlfaTolerada: regras.bandaAlfaTolerada,
        modeloCor: regras.modeloCor ?? '',
        bitsPorBanda: regras.bitsPorBanda === null ? '' : String(regras.bitsPorBanda),
        resolucaoDpi: regras.resolucaoDpi === null ? '' : String(regras.resolucaoDpi),
        elementosLegenda: regras.elementosLegenda.join('\n'),
        ativo: item?.ativo ?? true,
      },
      { emitEvent: false },
    );
    const codigo = this.formulario.controls.codigo;
    if (item) {
      codigo.disable({ emitEvent: false });
    } else {
      codigo.enable({ emitEvent: false });
    }
    this.tipoEscolhido.set(item?.tipoElemento ?? null);
    this.caixaEscolhida.set(item?.categoria ?? null);
    this.ativarGrupoTiff();
  }
}
