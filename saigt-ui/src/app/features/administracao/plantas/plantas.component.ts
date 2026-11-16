import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, FormControl, ReactiveFormsModule } from '@angular/forms';
import { ApiError } from '../../../core/models/error-response.model';
import { AtualizarPlantaRequest, LIMITES_PLANTA, Planta, PlantaFiltro } from '../../../models/planta.model';
import { PlantaService } from '../../../services/planta.service';
import { AppShellComponent } from '../../../shared/components/app-shell.component';
import { ErrorBannerComponent } from '../../../shared/components/error-banner.component';
import { AlertaComponent } from '../../../shared/ui/alerta/alerta.component';
import { BotaoComponent } from '../../../shared/ui/botao/botao.component';
import { CampoPesquisaComponent } from '../../../shared/ui/campo-pesquisa/campo-pesquisa.component';
import { CampoTextoComponent } from '../../../shared/ui/campo-texto/campo-texto.component';
import { CartaoComponent } from '../../../shared/ui/cartao/cartao.component';
import { ModalComponent } from '../../../shared/ui/modal/modal.component';
import { PaginacaoComponent } from '../../../shared/ui/paginacao/paginacao.component';
import { OpcaoSeletor, SeletorComponent } from '../../../shared/ui/seletor/seletor.component';
import { SeloComponent } from '../../../shared/ui/selo/selo.component';
import { CelulaTabelaDirective } from '../../../shared/ui/tabela/celula-tabela.directive';
import { ColunaTabela, TabelaComponent } from '../../../shared/ui/tabela/tabela.component';

const TAMANHO_PAGINA = 20;

type CampoPlanta = 'nomeRegulamento' | 'numeroFolha' | 'nomeTiff' | 'nomeZip';

const CAMPOS_TEXTO: readonly CampoPlanta[] = ['nomeRegulamento', 'numeroFolha', 'nomeTiff', 'nomeZip'];

/** Plantas SAIGT/SNIT (Master e Técnico). O link da imagem é gerado pelo sistema e não se edita aqui. */
@Component({
  selector: 'app-plantas',
  imports: [
    AppShellComponent,
    ErrorBannerComponent,
    AlertaComponent,
    BotaoComponent,
    CampoPesquisaComponent,
    CampoTextoComponent,
    CartaoComponent,
    ModalComponent,
    PaginacaoComponent,
    SeletorComponent,
    SeloComponent,
    TabelaComponent,
    CelulaTabelaDirective,
    ReactiveFormsModule,
    DatePipe,
  ],
  templateUrl: './plantas.component.html',
  styleUrls: ['../backoffice.css', './plantas.component.css'],
})
export class PlantasComponent implements OnInit {
  private readonly plantaService = inject(PlantaService);
  private readonly formBuilder = inject(FormBuilder);

  readonly tamanhoPagina = TAMANHO_PAGINA;
  readonly limites = LIMITES_PLANTA;
  readonly opcoesEmVigor: readonly OpcaoSeletor[] = [
    { valor: 'TODOS', rotulo: 'Todos' },
    { valor: 'SIM', rotulo: 'Sim' },
    { valor: 'NAO', rotulo: 'Não' },
  ];
  readonly colunas: ColunaTabela[] = [
    { chave: 'numeroProcesso', titulo: 'N.º do processo', largura: '9%' },
    { chave: 'numeroDeposito', titulo: 'N.º de depósito', largura: '12%' },
    { chave: 'idus', titulo: 'IDUS', largura: '14%' },
    { chave: 'nomeRegulamento', titulo: 'Nome conforme o regulamento', largura: '14%' },
    { chave: 'numeroFolha', titulo: 'N.º de folha', largura: '7%' },
    { chave: 'nomeTiff', titulo: 'TIFF', largura: '10%' },
    { chave: 'nomeZip', titulo: 'ZIP', largura: '10%' },
    { chave: 'emVigor', titulo: 'Em vigor', largura: '7%' },
    { chave: 'atualizacao', titulo: 'Última atualização', largura: '10%' },
    { chave: 'acoes', titulo: 'Ações', largura: '7%' },
  ];

  readonly emVigor = new FormControl<string | null>('TODOS');
  readonly plantas = signal<Planta[]>([]);
  readonly pagina = signal(0);
  readonly totalElementos = signal(0);
  readonly aCarregar = signal(true);
  readonly erro = signal<ApiError | null>(null);

  readonly emEdicao = signal<Planta | null>(null);
  readonly aGravar = signal(false);
  readonly errosCampos = signal<Partial<Record<CampoPlanta, string>>>({});
  readonly erroGravacao = signal<ApiError | null>(null);
  /** Lido pelo leitor de ecrã na região `role="status"`, que existe desde o início para o anúncio não se perder. */
  readonly anuncio = signal('');

  readonly formulario = this.formBuilder.nonNullable.group({
    nomeRegulamento: [''],
    numeroFolha: [''],
    nomeTiff: [''],
    nomeZip: [''],
    emVigor: [false],
  });

  private numeroProcesso = '';
  private numeroDeposito = '';

  ngOnInit(): void {
    this.emVigor.valueChanges.subscribe(() => this.recarregar());
    this.carregar();
  }

  /** Chega já com a espera do `app-campo-pesquisa`: um pedido por pausa na escrita. */
  pesquisarProcesso(valor: string): void {
    this.numeroProcesso = valor.trim();
    this.recarregar();
  }

  pesquisarDeposito(valor: string): void {
    this.numeroDeposito = valor.trim();
    this.recarregar();
  }

  irParaPagina(pagina: number): void {
    this.pagina.set(pagina);
    this.carregar();
  }

  editar(planta: Planta): void {
    this.formulario.reset({
      nomeRegulamento: planta.nomeRegulamento ?? '',
      numeroFolha: planta.numeroFolha ?? '',
      nomeTiff: planta.nomeTiff ?? '',
      nomeZip: planta.nomeZip ?? '',
      emVigor: planta.emVigor,
    });
    this.errosCampos.set({});
    this.erroGravacao.set(null);
    this.anuncio.set('');
    this.emEdicao.set(planta);
  }

  fecharEdicao(): void {
    this.emEdicao.set(null);
  }

  guardar(): void {
    const planta = this.emEdicao();
    if (!planta || this.aGravar()) {
      return;
    }
    this.aGravar.set(true);
    this.errosCampos.set({});
    this.erroGravacao.set(null);
    this.plantaService.atualizar(planta.id, this.alteracoes()).subscribe({
      next: (atualizada) => {
        this.aGravar.set(false);
        this.plantas.update((lista) => lista.map((linha) => (linha.id === atualizada.id ? atualizada : linha)));
        this.emEdicao.set(null);
        this.anuncio.set(`Planta ${atualizada.nomeTiff ?? atualizada.numeroProcesso} gravada.`);
      },
      error: (erro: ApiError) => {
        this.aGravar.set(false);
        const porCampo = this.errosPorCampo(erro);
        if (Object.keys(porCampo).length > 0) {
          this.errosCampos.set(porCampo);
        } else {
          this.erroGravacao.set(erro);
        }
      },
    });
  }

  private alteracoes(): AtualizarPlantaRequest {
    const valor = this.formulario.getRawValue();
    return {
      nomeRegulamento: valor.nomeRegulamento,
      numeroFolha: valor.numeroFolha,
      nomeTiff: valor.nomeTiff,
      nomeZip: valor.nomeZip,
      emVigor: valor.emVigor,
    };
  }

  private errosPorCampo(erro: ApiError): Partial<Record<CampoPlanta, string>> {
    const porCampo: Partial<Record<CampoPlanta, string>> = {};
    for (const campo of erro.campos ?? []) {
      if ((CAMPOS_TEXTO as readonly string[]).includes(campo.path)) {
        porCampo[campo.path as CampoPlanta] = campo.erro;
      }
    }
    return porCampo;
  }

  private recarregar(): void {
    this.pagina.set(0);
    this.carregar();
  }

  private filtro(): PlantaFiltro {
    const emVigor = this.emVigor.value;
    return {
      numeroProcesso: this.numeroProcesso || undefined,
      numeroDeposito: this.numeroDeposito || undefined,
      emVigor: emVigor === 'SIM' ? true : emVigor === 'NAO' ? false : undefined,
    };
  }

  private carregar(): void {
    this.aCarregar.set(true);
    this.erro.set(null);
    this.plantaService.listar(this.filtro(), this.pagina(), TAMANHO_PAGINA).subscribe({
      next: (resposta) => {
        this.plantas.set(resposta.content);
        this.totalElementos.set(resposta.totalElements);
        this.aCarregar.set(false);
      },
      error: (erro: ApiError) => {
        this.erro.set(erro);
        this.aCarregar.set(false);
      },
    });
  }
}
