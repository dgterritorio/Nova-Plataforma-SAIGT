import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { ApiError } from '../../../core/models/error-response.model';
import { ListaValoresItem } from '../../../models/lista-valores.model';
import { ProcessoDetalhe, TipoItemValidacao, ValidacaoItensResponse } from '../../../models/processo.model';
import { ListaValoresService } from '../../../services/lista-valores.service';
import { ProcessoService } from '../../../services/processo.service';
import { ErrorBannerComponent } from '../../../shared/components/error-banner.component';
import { BotaoComponent } from '../../../shared/ui/botao/botao.component';
import { CampoTextoComponent } from '../../../shared/ui/campo-texto/campo-texto.component';
import { ModalComponent } from '../../../shared/ui/modal/modal.component';
import { OpcaoSeletor, SeletorComponent } from '../../../shared/ui/seletor/seletor.component';

const TIPO_CATEGORIA = 'CATEGORIA_REJEICAO_DGT';
const TIPO_MOTIVO = 'MOTIVO_REJEICAO_DGT';
const CODIGOS_DE_CONFLITO = ['VERSAO_DESATUALIZADA', 'ESTADO_INVALIDO'];

export interface ItemARejeitar {
  tipo: TipoItemValidacao;
  itemId: string;
  nome: string;
}

function alternar(controlo: FormControl, desativado: boolean): void {
  if (desativado) {
    controlo.disable({ emitEvent: false });
  } else {
    controlo.enable({ emitEvent: false });
  }
}

@Component({
  selector: 'app-rejeitar-item-modal',
  imports: [BotaoComponent, CampoTextoComponent, ErrorBannerComponent, ModalComponent, ReactiveFormsModule, SeletorComponent],
  templateUrl: './rejeitar-item-modal.component.html',
  styleUrl: './rejeitar-item-modal.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RejeitarItemModalComponent {
  private readonly processoService = inject(ProcessoService);
  private readonly listaValoresService = inject(ListaValoresService);

  readonly aberto = input(false);
  readonly processo = input.required<ProcessoDetalhe>();
  readonly item = input<ItemARejeitar | null>(null);

  readonly fechar = output<void>();
  /** A resposta do `PUT`, com o estado de todos os itens do processo. */
  readonly rejeitado = output<ValidacaoItensResponse>();
  /** Pedido depois de um 409: o ecrã relê o processo. */
  readonly recarregar = output<void>();

  /** Só os ativos: o backend recusa os inativos. Nulas enquanto carregam. */
  readonly categorias = signal<ListaValoresItem[] | null>(null);
  readonly motivos = signal<ListaValoresItem[] | null>(null);
  readonly falhaListas = signal(false);
  readonly categoriaId = signal<number | null>(null);
  readonly motivoId = signal<number | null>(null);
  readonly notas = signal('');
  readonly erroCategoria = signal<string | null>(null);
  readonly erroMotivo = signal<string | null>(null);
  readonly erroNotas = signal<string | null>(null);
  readonly aProcessar = signal(false);
  readonly erro = signal<ApiError | null>(null);

  readonly controloCategoria = new FormControl<string | null>(null);
  readonly controloMotivo = new FormControl<string | null>(null);
  readonly controloNotas = new FormControl<string>('', { nonNullable: true });

  readonly listasCarregadas = computed(() => this.categorias() !== null && this.motivos() !== null);
  readonly mostrarCategoria = computed(() => (this.categorias()?.length ?? 0) > 0);
  readonly mostrarMotivo = computed(() => (this.motivos()?.length ?? 0) > 0);
  readonly opcoesCategoria = computed<OpcaoSeletor[]>(() => paraOpcoes(this.categorias()));
  readonly opcoesMotivo = computed<OpcaoSeletor[]>(() => paraOpcoes(this.motivos()));
  readonly conflito = computed(() => CODIGOS_DE_CONFLITO.includes(this.erro()?.codigo ?? ''));

  readonly podeConfirmar = computed(
    () =>
      this.listasCarregadas() &&
      (!this.mostrarCategoria() || this.categoriaId() !== null) &&
      (!this.mostrarMotivo() || this.motivoId() !== null) &&
      this.notas().trim() !== '' &&
      !this.aProcessar() &&
      !this.conflito(),
  );

  constructor() {
    effect(() => {
      if (this.aberto()) {
        untracked(() => {
          this.limpar();
          this.carregarListas();
        });
      }
    });
    effect(() => {
      const aProcessar = this.aProcessar();
      alternar(this.controloCategoria, aProcessar);
      alternar(this.controloMotivo, aProcessar);
      alternar(this.controloNotas, aProcessar);
    });
    this.controloCategoria.valueChanges.pipe(takeUntilDestroyed()).subscribe((valor) => {
      this.categoriaId.set(valor ? Number(valor) : null);
      this.erroCategoria.set(null);
    });
    this.controloMotivo.valueChanges.pipe(takeUntilDestroyed()).subscribe((valor) => {
      this.motivoId.set(valor ? Number(valor) : null);
      this.erroMotivo.set(null);
    });
    this.controloNotas.valueChanges.pipe(takeUntilDestroyed()).subscribe((valor) => {
      this.notas.set(valor);
      this.erroNotas.set(null);
    });
  }

  carregarListas(): void {
    this.falhaListas.set(false);
    this.categorias.set(null);
    this.motivos.set(null);
    forkJoin([this.listaValoresService.listar(TIPO_CATEGORIA), this.listaValoresService.listar(TIPO_MOTIVO)]).subscribe({
      next: ([categorias, motivos]) => {
        this.categorias.set(categorias.filter((valor) => valor.ativo));
        this.motivos.set(motivos.filter((valor) => valor.ativo));
      },
      error: (erro: ApiError) => {
        this.falhaListas.set(true);
        this.erro.set(erro);
      },
    });
  }

  confirmar(): void {
    const item = this.item();
    if (!item || !this.podeConfirmar()) {
      return;
    }
    const processo = this.processo();
    this.erro.set(null);
    this.aProcessar.set(true);
    this.processoService
      .gravarValidacaoItens(processo.id, processo.versao, [
        {
          tipo: item.tipo,
          itemId: item.itemId,
          decisao: 'REJEITADO',
          categoriaId: this.mostrarCategoria() ? this.categoriaId() : null,
          motivoId: this.mostrarMotivo() ? this.motivoId() : null,
          notas: this.notas().trim(),
        },
      ])
      .subscribe({
        next: (resposta) => {
          this.aProcessar.set(false);
          this.rejeitado.emit(resposta);
        },
        error: (erro: ApiError) => {
          this.aProcessar.set(false);
          this.tratarErro(erro);
        },
      });
  }

  private tratarErro(erro: ApiError): void {
    const campos = erro.codigo === 'VALIDACAO' ? (erro.campos ?? []) : [];
    const doCampo = (sufixo: string) => campos.find((campo) => campo.path.endsWith(`.${sufixo}`))?.erro ?? null;
    const categoria = doCampo('categoriaId');
    const motivo = doCampo('motivoId');
    const notas = doCampo('notas');
    if (categoria === null && motivo === null && notas === null) {
      this.erro.set(erro);
      return;
    }
    this.erroCategoria.set(categoria);
    this.erroMotivo.set(motivo);
    this.erroNotas.set(notas);
    if (categoria !== null) {
      this.controloCategoria.reset(null, { emitEvent: false });
      this.categoriaId.set(null);
    }
    if (motivo !== null) {
      this.controloMotivo.reset(null, { emitEvent: false });
      this.motivoId.set(null);
    }
    if (categoria !== null || motivo !== null) {
      this.carregarListas();
    }
  }

  private limpar(): void {
    this.categoriaId.set(null);
    this.motivoId.set(null);
    this.notas.set('');
    this.erroCategoria.set(null);
    this.erroMotivo.set(null);
    this.erroNotas.set(null);
    this.erro.set(null);
    this.aProcessar.set(false);
    this.controloCategoria.reset(null, { emitEvent: false });
    this.controloMotivo.reset(null, { emitEvent: false });
    this.controloNotas.reset('', { emitEvent: false });
  }
}

function paraOpcoes(valores: ListaValoresItem[] | null): OpcaoSeletor[] {
  return (valores ?? []).map((valor) => ({ valor: String(valor.id), rotulo: valor.descricao }));
}
