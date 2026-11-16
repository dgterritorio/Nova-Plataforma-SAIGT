import type { Meta, StoryObj } from '@storybook/angular';
import { design, referenciaFigma } from '../../../../stories/figma';
import { PassosComponent } from './passos.component';

// Os passos do assistente completo do Figma (UI-051).
const PASSOS_CRIAR_PROCESSO = [
  { etiqueta: 'Tipo IGT' },
  { etiqueta: 'IGT Existente' },
  { etiqueta: 'Ação' },
  { etiqueta: 'Nova Dinâmica' },
  { etiqueta: 'Formulário' },
];

const meta: Meta<PassosComponent> = {
  title: 'Componentes/Passos',
  component: PassosComponent,
  tags: ['autodocs'],
  parameters: {
    design: design('UI-051'),
    docs: {
      description: {
        component: `\`<ol>\` com \`aria-current="step"\` no passo atual; os anteriores contam como concluídos. ${referenciaFigma('UI-051', 'UI-045')}`,
      },
    },
  },
  args: { passos: PASSOS_CRIAR_PROCESSO, atual: 0 },
};

export default meta;
type Story = StoryObj<PassosComponent>;

export const Passo1: Story = { name: 'Criar Processo, passo 1', args: { atual: 0 } };
export const Passo3: Story = { name: 'Criar Processo, passo 3', args: { atual: 2 } };
export const Passo5: Story = { name: 'Criar Processo, passo 5', args: { atual: 4 } };
