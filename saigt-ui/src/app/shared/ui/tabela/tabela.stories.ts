import { moduleMetadata, type Meta, type StoryObj } from '@storybook/angular';
import { design, referenciaFigma } from '../../../../stories/figma';
import { EstadoProcessoBadgeComponent } from '../../components/estado-processo-badge.component';
import { PaginacaoComponent } from '../paginacao/paginacao.component';
import { CelulaTabelaDirective } from './celula-tabela.directive';
import { ColunaTabela, FamiliaTabela, TabelaComponent } from './tabela.component';

interface ProcessoExemplo {
  instrumento: string;
  tipo: string;
  designacao: string;
  dinamica: string;
  procedimento: string;
  concelho: string;
  estado: string;
  prazoAExpirar: boolean;
}

interface ArgsTabela {
  familia: FamiliaTabela;
  aCarregar: boolean;
  clicavel: boolean;
  linhaClicada: (linha: ProcessoExemplo) => void;
  mensagemVazia: string;
  linhas: readonly ProcessoExemplo[];
  colunas: ColunaTabela[];
}

const CONCELHOS = ['Exemplo Norte', 'Exemplo Sul', 'Vila Nova de Teste', 'Serra Fictícia', 'Porto Demo'];
const ESTADOS = ['EM_SUBMISSAO', 'EM_VALIDACAO_DGT', 'VALIDADO_DGT', 'EM_VALIDACAO_INCM', 'REJEITADO_DGT'];
const TIPOS = ['PDM', 'PU', 'PP'];

/** Dados fictícios: nenhum concelho, processo ou pessoa real. */
const PROCESSOS: ProcessoExemplo[] = Array.from({ length: 10 }, (_, i) => ({
  instrumento: `IGT-2026-${String(101 + i).padStart(4, '0')}`,
  tipo: TIPOS[i % TIPOS.length],
  designacao: `${TIPOS[i % TIPOS.length]} de ${CONCELHOS[i % CONCELHOS.length]}`,
  dinamica: i % 3 === 0 ? 'Elaboração' : 'Alteração',
  procedimento: i % 2 === 0 ? 'Decisão de início' : 'Discussão pública',
  concelho: CONCELHOS[i % CONCELHOS.length],
  estado: ESTADOS[i % ESTADOS.length],
  prazoAExpirar: i === 2,
}));

const COLUNAS: ColunaTabela[] = [
  { chave: 'instrumento', titulo: 'Instrumento', largura: '14%' },
  { chave: 'tipo', titulo: 'Tipo IGT', largura: '8%' },
  { chave: 'designacao', titulo: 'Designação' },
  { chave: 'dinamica', titulo: 'Dinâmica', largura: '11%' },
  { chave: 'procedimento', titulo: 'Procedimento', largura: '15%' },
  { chave: 'concelho', titulo: 'Concelho', largura: '13%' },
  { chave: 'estado', titulo: 'Estado', largura: '16%' },
];

const COLUNAS_COMPACTAS: ColunaTabela[] = [
  { chave: 'instrumento', titulo: 'Instrumento' },
  { chave: 'designacao', titulo: 'Designação' },
  { chave: 'estado', titulo: 'Estado', largura: '24%' },
];

const meta: Meta<ArgsTabela> = {
  title: 'Componentes/Tabela',
  component: TabelaComponent,
  tags: ['autodocs'],
  decorators: [moduleMetadata({ imports: [CelulaTabelaDirective, EstadoProcessoBadgeComponent, PaginacaoComponent] })],
  parameters: {
    design: design('UI-016'),
    docs: {
      description: {
        component: `\`<table>\` semântica com \`<th scope="col">\`. Células próprias com \`<ng-template appCelula="chave">\`; a paginação entra pelo slot \`[rodape]\`. Dados fictícios. ${referenciaFigma('UI-016', 'UI-001', 'UI-058')}`,
      },
    },
  },
  argTypes: {
    familia: { control: 'inline-radio', options: ['listagem', 'compacta', 'relatorio'] },
    mensagemVazia: { control: 'text' },
    linhaClicada: { action: 'linhaClicada' },
  },
  args: { familia: 'listagem', aCarregar: false, clicavel: false, mensagemVazia: 'Sem processos para os filtros indicados.', linhas: PROCESSOS, colunas: COLUNAS },
  render: (args) => ({
    props: args,
    template: `
      <app-tabela [familia]="familia" [colunas]="colunas" [linhas]="linhas" [aCarregar]="aCarregar" [mensagemVazia]="mensagemVazia" [clicavel]="clicavel" (linhaClicada)="linhaClicada($event)">
        <ng-template appCelula="estado" let-processo><app-estado-processo-badge [estado]="processo.estado" /></ng-template>
        <app-paginacao rodape [pagina]="1" [tamanhoPagina]="10" [total]="48" etiquetaItens="procedimentos" />
      </app-tabela>`,
  }),
};

export default meta;
type Story = StoryObj<ArgsTabela>;

export const Listagem: Story = {};
export const Compacta: Story = {
  args: { familia: 'compacta', colunas: COLUNAS_COMPACTAS, linhas: PROCESSOS.slice(0, 4) },
  parameters: { design: design('UI-001') },
};
export const Relatorio: Story = {
  name: 'Relatório',
  args: { familia: 'relatorio', colunas: COLUNAS_COMPACTAS, linhas: PROCESSOS.slice(0, 4) },
  parameters: { design: design('UI-058') },
};
export const ACarregar: Story = { name: 'A carregar', args: { aCarregar: true } };
export const Vazia: Story = { args: { linhas: [] } };
export const ComLinhaDestacada: Story = {
  name: 'Com linha destacada',
  parameters: { docs: { description: { story: 'A terceira linha tem o prazo a expirar (fundo de aviso, como no Figma).' } } },
  render: (args) => ({
    props: { ...args, destacar: (linha: ProcessoExemplo) => linha.prazoAExpirar },
    template: `
      <app-tabela [familia]="familia" [colunas]="colunas" [linhas]="linhas" [linhaDestacada]="destacar">
        <ng-template appCelula="estado" let-processo><app-estado-processo-badge [estado]="processo.estado" /></ng-template>
      </app-tabela>`,
  }),
};
