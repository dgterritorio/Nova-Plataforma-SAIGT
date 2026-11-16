import { moduleMetadata, type Meta, type StoryObj } from '@storybook/angular';
import { design, referenciaFigma } from '../../../../stories/figma';
import { BotaoComponent } from '../botao/botao.component';
import { CartaoComponent } from './cartao.component';

const meta: Meta<CartaoComponent> = {
  title: 'Componentes/Cartão',
  component: CartaoComponent,
  tags: ['autodocs'],
  decorators: [moduleMetadata({ imports: [BotaoComponent] })],
  parameters: {
    design: design('UI-001'),
    docs: {
      description: {
        component: `Contentor com borda, cantos de 12px e conteúdo cortado pelos cantos. Ações do cabeçalho no slot \`[cabecalho-acoes]\`. ${referenciaFigma('UI-001', 'UI-069')}`,
      },
    },
  },
  argTypes: {
    titulo: { control: 'text' },
    variante: { control: 'inline-radio', options: ['normal', 'elevado', 'indicador'] },
    espacamento: { control: 'inline-radio', options: ['normal', 'nenhum'] },
  },
  args: { titulo: undefined, variante: 'normal', espacamento: 'normal' },
  render: (args) => ({
    props: args,
    template: `
      <app-cartao [titulo]="titulo" [variante]="variante" [espacamento]="espacamento" style="max-width: 480px">
        <p style="margin: 0">Processo PDM de Exemplo — Alteração. Submetido a 12/05/2026.</p>
      </app-cartao>`,
  }),
};

export default meta;
type Story = StoryObj<CartaoComponent>;

export const Simples: Story = {};
export const ComTitulo: Story = { name: 'Com título', args: { titulo: 'Ficheiros remetidos' }, parameters: { design: design('UI-069') } };
export const ComAcoesNoCabecalho: Story = {
  name: 'Com ações no cabeçalho',
  args: { titulo: 'Processos recentes' },
  render: (args) => ({
    props: args,
    template: `
      <app-cartao [titulo]="titulo" [variante]="variante" style="max-width: 480px">
        <button cabecalho-acoes app-botao type="button" variante="texto" tamanho="pequeno">Ver todos</button>
        <p style="margin: 0">Três processos em submissão.</p>
      </app-cartao>`,
  }),
};
export const Elevado: Story = { args: { variante: 'elevado', titulo: 'Utilizadores' }, parameters: { design: design('UI-060') } };
export const Indicador: Story = { args: { variante: 'indicador' } };
