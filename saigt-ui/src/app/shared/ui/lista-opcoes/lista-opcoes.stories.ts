import type { Meta, StoryObj } from '@storybook/angular';
import { design, referenciaFigma } from '../../../../stories/figma';
import { ListaOpcoesComponent } from './lista-opcoes.component';

const meta: Meta<ListaOpcoesComponent> = {
  title: 'Componentes/Lista de opções',
  component: ListaOpcoesComponent,
  tags: ['autodocs'],
  parameters: {
    design: design('UI-045'),
    docs: {
      description: {
        component: `Radios nativos apresentados como cartões, com a legenda lida pelo leitor de ecrã. ${referenciaFigma('UI-045', 'UI-046', 'UI-047')}`,
      },
    },
  },
  args: { legenda: 'Tipo de IGT', valor: null },
};

export default meta;
type Story = StoryObj<ListaOpcoesComponent>;

export const TiposDeIgt: Story = {
  name: 'Tipo IGT (UI-045)',
  args: {
    opcoes: [
      { valor: '1', titulo: 'Plano Diretor Municipal', etiqueta: 'PDM' },
      { valor: '2', titulo: 'Plano de Urbanização', etiqueta: 'PU' },
      { valor: '3', titulo: 'Plano de Pormenor', etiqueta: 'PP' },
    ],
    valor: '1',
  },
};

export const IgtExistente: Story = {
  name: 'IGT existente (UI-047)',
  args: {
    legenda: 'IGT existente',
    opcoes: [
      { valor: 'a', titulo: 'PDM_Lisboa', etiqueta: 'PDM', selo: 'Ativo' },
      { valor: 'b', titulo: 'PDM_DGT', etiqueta: 'PDM', selo: 'Ativo' },
    ],
    valor: 'a',
  },
};

export const ComPrazo: Story = {
  name: 'Com prazo de caducidade',
  args: {
    legenda: 'Dinâmica em curso',
    opcoes: [
      {
        valor: 'proxima',
        titulo: 'Alteração',
        descricao: 'Alteração do regulamento',
        citacao: true,
        data: '01/02/2026',
        prazo: { rotulo: 'Caduca em', data: '16/11/2026', datetime: '2026-11-16', selo: 'Faltam 10 dias' },
      },
      {
        valor: 'normal',
        titulo: 'Revisão',
        descricao: 'Revisão do plano',
        citacao: true,
        data: '15/03/2026',
        prazo: { rotulo: 'Caduca em', data: '06/02/2027', datetime: '2027-02-06' },
      },
    ],
    valor: 'proxima',
  },
};

export const NovoOuExistente: Story = {
  name: 'Novo ou existente (UI-046)',
  args: {
    legenda: 'Origem do IGT',
    disposicao: 'grelha',
    opcoes: [
      { valor: 'novo', titulo: 'Novo IGT', descricao: 'Criar um novo instrumento de gestão territorial.', icone: 'mais' },
      { valor: 'existente', titulo: 'IGT Existente', descricao: 'Selecionar um IGT já criado para a sua entidade.', icone: 'prancheta' },
    ],
  },
};
