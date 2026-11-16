import { Component, DestroyRef, ElementRef, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormArray, FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { pairwise, startWith } from 'rxjs';
import { ApiError } from '../../../core/models/error-response.model';
import {
  AVALIADORES,
  Avaliador,
  CondicaoConfig,
  CondicaoConfigRequest,
  ESTADOS_DINAMICA,
  OperadorRegra,
  ParametrosCondicao,
  ROTULOS_AVALIADOR,
  ROTULOS_TIPO_OCORRENCIA,
  RegraConfig,
  RegraConfigRequest,
  TAMANHO_MAXIMO_DESCRICAO_REGRA,
  TipoDinamicaConfig,
  TipoOcorrencia,
  TipoProcedimentoConfig,
} from '../../../models/configuracao.model';
import { ConfiguracaoService } from '../../../services/configuracao.service';
import { ESTADOS_PROCESSO, rotuloEstado } from '../../../shared/components/estado-processo-badge.component';
import { AlertaComponent } from '../../../shared/ui/alerta/alerta.component';
import { BotaoComponent } from '../../../shared/ui/botao/botao.component';
import { CampoTextoComponent } from '../../../shared/ui/campo-texto/campo-texto.component';
import { ModalComponent } from '../../../shared/ui/modal/modal.component';
import { OpcaoSeletor, SeletorComponent } from '../../../shared/ui/seletor/seletor.component';
import { CondicaoADescrever, descreverRegra } from './descrever-regra';

/** Dinâmica (regra de elegibilidade) ou Procedimento (exceção ao sequenciamento) a que a regra se aplica. */
export interface AlvoRegra {
  tipo: 'DINAMICA' | 'PROCEDIMENTO';
  /** `instrumentoDinamicaConfigId` ou `instrumentoProcedimentoConfigId`. */
  id: number;
  /** Ex.: "Elaboração" ou "Decisão de início". */
  nome: string;
  /** Por baixo do título; ex.: "PDM — Alteração". */
  contexto: string;
}

type CampoCondicao = 'avaliador' | 'tipoOcorrencia' | 'codigo' | 'estado';

interface ControlosCondicao {
  id: FormControl<number | null>;
  avaliador: FormControl<Avaliador | null>;
  tipoOcorrencia: FormControl<TipoOcorrencia | null>;
  codigo: FormControl<string | null>;
  estado: FormControl<string | null>;
  /** N.º do grupo no ecrã (1, 2, …); mudá-lo move a condição para esse grupo. */
  grupo: FormControl<string>;
  ativo: FormControl<boolean>;
}

export type FormCondicao = FormGroup<ControlosCondicao>;
export type FormGrupo = FormGroup<{ condicoes: FormArray<FormCondicao> }>;

export const MENSAGEM_SEM_CONDICAO_ATIVA = 'Indique pelo menos uma condição ativa.';

const OCORRENCIAS: readonly Avaliador[] = ['OCORRENCIA_EM_ESTADO', 'OCORRENCIA_NAO_EXISTE'];
const CAMINHO_CONDICAO = /^condicoes\[(\d+)\]/;

let proximoId = 0;
let proximaChave = 0;

/**
 * Cria ou edita uma regra do motor. Os parâmetros de cada avaliador montam-se a partir de listas, nunca em JSON,
 * e as condições mudam de grupo pelo seletor "Grupo" (teclado e leitor de ecrã), em vez de arrastar.
 */
@Component({
  selector: 'app-regra-editor-modal',
  imports: [AlertaComponent, BotaoComponent, CampoTextoComponent, ModalComponent, ReactiveFormsModule, SeletorComponent],
  templateUrl: './regra-editor-modal.component.html',
  styleUrls: ['./configuracao-formulario.css', './regra-editor-modal.component.css'],
})
export class RegraEditorModalComponent {
  private readonly configuracaoService = inject(ConfiguracaoService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly anfitriao = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly aberto = input(false);
  readonly alvo = input<AlvoRegra | null>(null);
  /** Nula: regra nova para o alvo. */
  readonly regra = input<RegraConfig | null>(null);
  /** Todos, incluindo os inativos: uma condição pode citar um tipo inativo. */
  readonly tiposDinamica = input<readonly TipoDinamicaConfig[]>([]);
  readonly tiposProcedimento = input<readonly TipoProcedimentoConfig[]>([]);
  readonly gravada = output<RegraConfig>();
  readonly fechar = output<void>();

  readonly idFormulario = `formulario-regra-${++proximoId}`;
  readonly idResumo = `${this.idFormulario}-resumo`;
  readonly idErroGeral = `${this.idFormulario}-erro`;
  readonly tamanhoMaximoDescricao = TAMANHO_MAXIMO_DESCRICAO_REGRA;
  readonly mensagemSemCondicaoAtiva = MENSAGEM_SEM_CONDICAO_ATIVA;
  readonly opcoesAvaliador: readonly OpcaoSeletor[] = AVALIADORES.map((valor) => ({ valor, rotulo: ROTULOS_AVALIADOR[valor] }));
  readonly opcoesTipoOcorrencia: readonly OpcaoSeletor[] = (Object.keys(ROTULOS_TIPO_OCORRENCIA) as TipoOcorrencia[]).map((valor) => ({
    valor,
    rotulo: ROTULOS_TIPO_OCORRENCIA[valor],
  }));
  readonly opcoesEstadoDinamica: readonly OpcaoSeletor[] = ESTADOS_DINAMICA.map((valor) => ({
    valor,
    rotulo: { EM_CURSO: 'Em curso', TERMINADA: 'Terminada', CADUCADA: 'Caducada' }[valor],
  }));
  readonly opcoesEstadoProcesso: readonly OpcaoSeletor[] = ESTADOS_PROCESSO.map((valor) => ({ valor, rotulo: rotuloEstado(valor) }));
  readonly opcoesCodigoDinamica = computed<OpcaoSeletor[]>(() =>
    this.tiposDinamica().map((tipo) => ({ valor: tipo.codigo, rotulo: `${tipo.nome} (${tipo.codigo})` })),
  );
  readonly opcoesCodigoProcedimento = computed<OpcaoSeletor[]>(() =>
    this.tiposProcedimento().map((tipo) => ({ valor: tipo.codigo, rotulo: `${tipo.nome} (${tipo.codigo})` })),
  );

  readonly excecao = computed(() => this.alvo()?.tipo === 'PROCEDIMENTO');
  readonly titulo = computed(() => {
    const alvo = this.alvo();
    if (!alvo) {
      return '';
    }
    return alvo.tipo === 'PROCEDIMENTO' ? `Exceção ao sequenciamento — ${alvo.nome}` : `Regra de elegibilidade — ${alvo.nome}`;
  });

  readonly numeroGrupos = signal(0);
  readonly opcoesGrupo = computed<OpcaoSeletor[]>(() =>
    Array.from({ length: this.numeroGrupos() }, (_, i) => ({ valor: String(i + 1), rotulo: `Grupo ${i + 1}` })),
  );
  readonly resumo = signal('');
  readonly aGravar = signal(false);
  readonly submetido = signal(false);
  readonly semCondicaoAtiva = signal(false);
  readonly erroGeral = signal<ApiError | null>(null);
  /** Motivos do servidor que não são de uma condição (alvo, `substituiSequenciamento`, `condicoes`). */
  readonly motivosGerais = signal<string[]>([]);
  readonly errosServidorCondicao = signal<ReadonlyMap<FormCondicao, string[]>>(new Map());

  readonly formulario = new FormGroup({
    descricao: new FormControl('', { nonNullable: true }),
    operador: new FormControl<OperadorRegra>('AND', { nonNullable: true }),
    substituiSequenciamento: new FormControl(false, { nonNullable: true }),
    ativo: new FormControl(true, { nonNullable: true }),
    grupos: new FormArray<FormGrupo>([]),
  });

  private readonly chaves = new WeakMap<object, number>();

  constructor() {
    effect(() => {
      if (this.aberto()) {
        const regra = this.regra();
        untracked(() => this.preparar(regra));
      }
    });
    effect(() => {
      this.tiposDinamica();
      this.tiposProcedimento();
      untracked(() => this.atualizarResumo());
    });
    this.formulario.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      this.atualizarResumo();
      if (this.semCondicaoAtiva() && this.temCondicaoAtiva()) {
        this.semCondicaoAtiva.set(false);
      }
    });
  }

  get grupos(): FormArray<FormGrupo> {
    return this.formulario.controls.grupos;
  }

  chave(controlo: object): number {
    let chave = this.chaves.get(controlo);
    if (chave === undefined) {
      chave = ++proximaChave;
      this.chaves.set(controlo, chave);
    }
    return chave;
  }

  idCondicao(condicao: FormCondicao): string {
    return `${this.idFormulario}-condicao-${this.chave(condicao)}`;
  }

  idErroCondicao(condicao: FormCondicao): string {
    return `${this.idCondicao(condicao)}-erro`;
  }

  idAdicionarCondicao(grupo: FormGrupo): string {
    return `${this.idFormulario}-grupo-${this.chave(grupo)}-adicionar`;
  }

  nomeCondicao(indiceGrupo: number, indiceCondicao: number): string {
    return `Condição ${indiceCondicao + 1} do grupo ${indiceGrupo + 1}`;
  }

  /** Um grupo com condições já gravadas não se remove: as condições removem-se uma a uma (e ficam inativas). */
  podeRemoverGrupo(grupo: FormGrupo): boolean {
    return grupo.controls.condicoes.controls.every((condicao) => condicao.controls.id.value === null);
  }

  mostraTipoOcorrencia(condicao: FormCondicao): boolean {
    return OCORRENCIAS.includes(condicao.controls.avaliador.value as Avaliador);
  }

  mostraCodigo(condicao: FormCondicao): boolean {
    const avaliador = condicao.controls.avaliador.value;
    return avaliador === 'DINAMICA_BLOQUEADA_LEGADO' || (this.mostraTipoOcorrencia(condicao) && condicao.controls.tipoOcorrencia.value !== null);
  }

  mostraEstado(condicao: FormCondicao): boolean {
    return this.mostraTipoOcorrencia(condicao) && condicao.controls.tipoOcorrencia.value !== null;
  }

  opcoesCodigo(condicao: FormCondicao): readonly OpcaoSeletor[] {
    return condicao.controls.tipoOcorrencia.value === 'PROCESSO' ? this.opcoesCodigoProcedimento() : this.opcoesCodigoDinamica();
  }

  opcoesEstado(condicao: FormCondicao): readonly OpcaoSeletor[] {
    return condicao.controls.tipoOcorrencia.value === 'PROCESSO' ? this.opcoesEstadoProcesso : this.opcoesEstadoDinamica;
  }

  /** "Qualquer Dinâmica" só em "Não existe ocorrência" sobre Dinâmicas. */
  codigoOpcional(condicao: FormCondicao): boolean {
    return condicao.controls.avaliador.value === 'OCORRENCIA_NAO_EXISTE' && condicao.controls.tipoOcorrencia.value === 'DINAMICA';
  }

  estadoOpcional(condicao: FormCondicao): boolean {
    return condicao.controls.avaliador.value === 'OCORRENCIA_NAO_EXISTE';
  }

  erroCampo(condicao: FormCondicao, campo: CampoCondicao): string | null {
    if (!this.submetido() && !condicao.controls[campo].touched) {
      return null;
    }
    return this.errosCliente(condicao)[campo] ?? null;
  }

  errosServidor(condicao: FormCondicao): string[] {
    return this.errosServidorCondicao().get(condicao) ?? [];
  }

  erroDescricao(): string | null {
    return this.formulario.controls.descricao.value.length > TAMANHO_MAXIMO_DESCRICAO_REGRA
      ? `Use no máximo ${TAMANHO_MAXIMO_DESCRICAO_REGRA} caracteres.`
      : null;
  }

  adicionarGrupo(): void {
    const condicao = this.criarCondicao(null);
    this.grupos.push(this.criarGrupo([condicao]));
    this.sincronizarGrupos();
    this.focarCondicao(condicao, 'select');
  }

  removerGrupo(indice: number): void {
    const grupo = this.grupos.at(indice);
    if (!grupo || !this.podeRemoverGrupo(grupo)) {
      return;
    }
    this.grupos.removeAt(indice);
    this.sincronizarGrupos();
    const seguinte = this.grupos.at(indice);
    this.focar(seguinte ? this.idAdicionarCondicao(seguinte) : `${this.idFormulario}-adicionar-grupo`);
  }

  adicionarCondicao(indiceGrupo: number): void {
    const condicao = this.criarCondicao(null);
    this.grupos.at(indiceGrupo).controls.condicoes.push(condicao);
    this.sincronizarGrupos();
    this.focarCondicao(condicao, 'select');
  }

  /** Uma condição gravada sai do pedido e o servidor passa-a a inativa; uma nova desaparece. */
  removerCondicao(indiceGrupo: number, indiceCondicao: number): void {
    const grupo = this.grupos.at(indiceGrupo);
    const condicoes = grupo.controls.condicoes;
    condicoes.removeAt(indiceCondicao);
    this.sincronizarGrupos();
    const seguinte = condicoes.at(indiceCondicao);
    if (seguinte) {
      this.focarCondicao(seguinte, '.acao-remover-condicao');
    } else {
      this.focar(this.idAdicionarCondicao(grupo));
    }
  }

  submeter(): void {
    if (this.aGravar()) {
      return;
    }
    this.submetido.set(true);
    this.limparErrosServidor();
    const condicoes = this.grupos.controls.flatMap((grupo) => grupo.controls.condicoes.controls);
    const invalida = condicoes.find((condicao) => Object.keys(this.errosCliente(condicao)).length > 0);
    if (invalida || this.erroDescricao()) {
      if (invalida) {
        this.focarCondicao(invalida, '[aria-invalid="true"]');
      }
      return;
    }
    const valor = this.formulario.getRawValue();
    if (valor.ativo && !this.temCondicaoAtiva()) {
      this.semCondicaoAtiva.set(true);
      return;
    }
    const alvo = this.alvo();
    if (!alvo) {
      return;
    }
    const { pedido, enviadas } = this.montarPedido(alvo);
    const regra = this.regra();
    this.aGravar.set(true);
    const gravacao = regra ? this.configuracaoService.atualizarRegra(regra.id, pedido) : this.configuracaoService.criarRegra(pedido);
    gravacao.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (gravada) => {
        this.aGravar.set(false);
        this.gravada.emit(gravada);
      },
      error: (erro: ApiError) => {
        this.aGravar.set(false);
        this.distribuirErro(erro, enviadas);
      },
    });
  }

  private montarPedido(alvo: AlvoRegra): { pedido: RegraConfigRequest; enviadas: FormCondicao[] } {
    const valor = this.formulario.getRawValue();
    const enviadas: FormCondicao[] = [];
    const condicoes: CondicaoConfigRequest[] = [];
    let numero = 0;
    for (const grupo of this.grupos.controls) {
      const doGrupo = grupo.controls.condicoes.controls;
      if (doGrupo.length === 0) {
        continue;
      }
      numero++;
      for (const condicao of doGrupo) {
        const controlos = condicao.getRawValue();
        enviadas.push(condicao);
        condicoes.push({
          ...(controlos.id !== null ? { id: controlos.id } : {}),
          avaliador: controlos.avaliador as Avaliador,
          parametros: parametrosDe(condicao),
          grupo: numero,
          ativo: controlos.ativo,
        });
      }
    }
    const descricao = valor.descricao.trim();
    return {
      enviadas,
      pedido: {
        instrumentoDinamicaConfigId: alvo.tipo === 'DINAMICA' ? alvo.id : null,
        instrumentoProcedimentoConfigId: alvo.tipo === 'PROCEDIMENTO' ? alvo.id : null,
        substituiSequenciamento: alvo.tipo === 'PROCEDIMENTO' && valor.substituiSequenciamento,
        operador: valor.operador,
        descricao: descricao === '' ? null : descricao,
        ativo: valor.ativo,
        condicoes,
      },
    };
  }

  /** `condicoes[k]…` fica na condição que foi na posição `k` do pedido; o resto vai para o topo do modal. */
  private distribuirErro(erro: ApiError, enviadas: readonly FormCondicao[]): void {
    const porCondicao = new Map<FormCondicao, string[]>();
    const gerais: string[] = [];
    for (const campo of erro.campos ?? []) {
      const correspondencia = CAMINHO_CONDICAO.exec(campo.path);
      const condicao = correspondencia ? enviadas[Number(correspondencia[1])] : undefined;
      if (condicao) {
        porCondicao.set(condicao, [...(porCondicao.get(condicao) ?? []), campo.erro]);
      } else if (campo.erro !== erro.mensagem) {
        gerais.push(campo.erro);
      }
    }
    this.erroGeral.set(erro);
    this.motivosGerais.set([...new Set(gerais)]);
    this.errosServidorCondicao.set(porCondicao);
    setTimeout(() => this.anfitriao.nativeElement.ownerDocument.getElementById(this.idErroGeral)?.scrollIntoView?.({ block: 'nearest' }));
  }

  private limparErrosServidor(): void {
    this.erroGeral.set(null);
    this.motivosGerais.set([]);
    this.errosServidorCondicao.set(new Map());
    this.semCondicaoAtiva.set(false);
  }

  private errosCliente(condicao: FormCondicao): Partial<Record<CampoCondicao, string>> {
    const { avaliador, tipoOcorrencia, codigo, estado } = condicao.getRawValue();
    const erros: Partial<Record<CampoCondicao, string>> = {};
    if (!avaliador) {
      erros.avaliador = 'Escolha o avaliador.';
      return erros;
    }
    if (avaliador === 'DINAMICA_BLOQUEADA_LEGADO') {
      if (!codigo) {
        erros.codigo = 'Escolha a Dinâmica.';
      }
      return erros;
    }
    if (!OCORRENCIAS.includes(avaliador)) {
      return erros;
    }
    if (!tipoOcorrencia) {
      erros.tipoOcorrencia = 'Escolha o tipo de ocorrência.';
      return erros;
    }
    const codigoObrigatorio = !(avaliador === 'OCORRENCIA_NAO_EXISTE' && tipoOcorrencia === 'DINAMICA');
    if (codigoObrigatorio && !codigo) {
      erros.codigo = 'Escolha o código.';
    }
    if (avaliador === 'OCORRENCIA_EM_ESTADO' && !estado) {
      erros.estado = 'Escolha o estado.';
    }
    return erros;
  }

  private temCondicaoAtiva(): boolean {
    return this.grupos.controls.some((grupo) => grupo.controls.condicoes.controls.some((condicao) => condicao.controls.ativo.value));
  }

  private preparar(regra: RegraConfig | null): void {
    this.submetido.set(false);
    this.aGravar.set(false);
    this.limparErrosServidor();
    this.formulario.reset(
      {
        descricao: regra?.descricao ?? '',
        operador: regra?.operador ?? 'AND',
        substituiSequenciamento: regra?.substituiSequenciamento ?? false,
        ativo: regra?.ativo ?? true,
      },
      { emitEvent: false },
    );
    this.grupos.clear({ emitEvent: false });
    const porGrupo = new Map<number, CondicaoConfig[]>();
    for (const condicao of regra?.condicoes ?? []) {
      porGrupo.set(condicao.grupo, [...(porGrupo.get(condicao.grupo) ?? []), condicao]);
    }
    const numeros = [...porGrupo.keys()].sort((a, b) => a - b);
    for (const numero of numeros) {
      this.grupos.push(this.criarGrupo(porGrupo.get(numero)!.map((condicao) => this.criarCondicao(condicao))), { emitEvent: false });
    }
    if (this.grupos.length === 0) {
      this.grupos.push(this.criarGrupo([this.criarCondicao(null)]), { emitEvent: false });
    }
    this.sincronizarGrupos();
    this.atualizarResumo();
  }

  private criarGrupo(condicoes: FormCondicao[]): FormGrupo {
    return new FormGroup({ condicoes: new FormArray<FormCondicao>(condicoes) });
  }

  private criarCondicao(gravada: CondicaoConfig | null): FormCondicao {
    const parametros = gravada?.parametros ?? {};
    const condicao: FormCondicao = new FormGroup({
      id: new FormControl<number | null>(gravada?.id ?? null),
      avaliador: new FormControl<Avaliador | null>(gravada?.avaliador ?? null),
      tipoOcorrencia: new FormControl<TipoOcorrencia | null>(parametros.tipo_ocorrencia ?? null),
      codigo: new FormControl<string | null>(parametros.codigo ?? null),
      estado: new FormControl<string | null>(parametros.estado ?? null),
      grupo: new FormControl('1', { nonNullable: true }),
      ativo: new FormControl(gravada?.ativo ?? true, { nonNullable: true }),
    });
    const { avaliador, tipoOcorrencia, codigo, estado, grupo } = condicao.controls;
    avaliador.valueChanges
      .pipe(startWith(avaliador.value), pairwise(), takeUntilDestroyed(this.destroyRef))
      .subscribe(([anterior, atual]) => {
        if (!(anterior && atual && OCORRENCIAS.includes(anterior) && OCORRENCIAS.includes(atual))) {
          const manterCodigo = atual === 'DINAMICA_BLOQUEADA_LEGADO' && tipoOcorrencia.value === 'DINAMICA';
          tipoOcorrencia.setValue(null, { emitEvent: false });
          estado.setValue(null);
          if (!manterCodigo) {
            codigo.setValue(null);
          }
        }
      });
    tipoOcorrencia.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      codigo.setValue(null);
      estado.setValue(null);
    });
    grupo.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((destino) => this.moverCondicao(condicao, Number(destino) - 1));
    return condicao;
  }

  private moverCondicao(condicao: FormCondicao, destino: number): void {
    const origem = this.grupos.controls.findIndex((grupo) => grupo.controls.condicoes.controls.includes(condicao));
    const grupoDestino = this.grupos.at(destino);
    if (origem < 0 || origem === destino || !grupoDestino) {
      return;
    }
    const condicoes = this.grupos.at(origem).controls.condicoes;
    condicoes.removeAt(condicoes.controls.indexOf(condicao));
    grupoDestino.controls.condicoes.push(condicao);
    this.sincronizarGrupos();
    this.focarCondicao(condicao, '.campo-grupo select');
  }

  private sincronizarGrupos(): void {
    this.grupos.controls.forEach((grupo, indice) => {
      for (const condicao of grupo.controls.condicoes.controls) {
        condicao.controls.grupo.setValue(String(indice + 1), { emitEvent: false });
      }
    });
    this.numeroGrupos.set(this.grupos.length);
  }

  private atualizarResumo(): void {
    const condicoes: CondicaoADescrever[] = this.grupos.controls.flatMap((grupo, indice) =>
      grupo.controls.condicoes.controls.map((condicao) => ({
        avaliador: condicao.controls.avaliador.value,
        parametros: parametrosDe(condicao),
        grupo: indice + 1,
        ativo: condicao.controls.ativo.value,
      })),
    );
    this.resumo.set(
      descreverRegra(
        { operador: this.formulario.controls.operador.value, condicoes },
        { tiposDinamica: this.tiposDinamica(), tiposProcedimento: this.tiposProcedimento() },
      ),
    );
  }

  private focarCondicao(condicao: FormCondicao, seletor: string): void {
    setTimeout(() =>
      this.anfitriao.nativeElement.ownerDocument.getElementById(this.idCondicao(condicao))?.querySelector<HTMLElement>(seletor)?.focus(),
    );
  }

  private focar(id: string): void {
    setTimeout(() => this.anfitriao.nativeElement.ownerDocument.getElementById(id)?.focus());
  }
}

/** Só as chaves que o avaliador lê: o servidor recusa uma chave a mais. */
function parametrosDe(condicao: FormCondicao): ParametrosCondicao {
  const { avaliador, tipoOcorrencia, codigo, estado } = condicao.getRawValue();
  switch (avaliador) {
    case 'OCORRENCIA_EM_ESTADO':
    case 'OCORRENCIA_NAO_EXISTE':
      return {
        ...(tipoOcorrencia ? { tipo_ocorrencia: tipoOcorrencia } : {}),
        ...(codigo ? { codigo } : {}),
        ...(estado ? { estado } : {}),
      };
    case 'DINAMICA_BLOQUEADA_LEGADO':
      return codigo ? { codigo } : {};
    default:
      return {};
  }
}
