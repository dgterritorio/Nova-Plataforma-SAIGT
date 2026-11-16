import type { Meta, StoryObj } from '@storybook/angular';
import { design, referenciaFigma } from '../../../../stories/figma';
import { CampoPesquisaComponent } from './campo-pesquisa.component';

const meta: Meta<CampoPesquisaComponent> = {
  title: 'Componentes/Pesquisa',
  component: CampoPesquisaComponent,
  tags: ['autodocs'],
  parameters: {
    design: design('UI-016'),
    docs: {
      description: {
        component: `Emite \`pesquisar\` 300ms depois da última tecla. ${referenciaFigma('UI-016', 'UI-060')}`,
      },
    },
  },
  argTypes: {
    largura: { control: 'inline-radio', options: ['total', 'cabecalho'] },
    pesquisar: { action: 'pesquisar' },
  },
  args: { placeholder: 'Pesquisar por designação…', largura: 'total' },
  render: (args) => ({
    props: args,
    template: `<div style="display: flex; max-width: 900px"><app-campo-pesquisa [placeholder]="placeholder" [largura]="largura" (pesquisar)="pesquisar($event)" /></div>`,
  }),
};

export default meta;
type Story = StoryObj<CampoPesquisaComponent>;

export const Total: Story = {};
export const Cabecalho: Story = {
  name: 'Cabeçalho',
  args: { largura: 'cabecalho', placeholder: 'Pesquisar utilizador…' },
  parameters: { design: design('UI-060') },
};
