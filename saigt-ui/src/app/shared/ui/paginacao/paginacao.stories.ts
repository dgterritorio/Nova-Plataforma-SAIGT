import type { Meta, StoryObj } from '@storybook/angular';
import { design, referenciaFigma } from '../../../../stories/figma';
import { PaginacaoComponent } from './paginacao.component';

const meta: Meta<PaginacaoComponent> = {
  title: 'Componentes/Paginação',
  component: PaginacaoComponent,
  tags: ['autodocs'],
  parameters: {
    design: design('UI-016'),
    docs: {
      description: {
        component: `Até 7 páginas mostra todas; acima disso, a primeira, a atual ±1, a última e "…". A página atual tem \`aria-current="page"\`. ${referenciaFigma('UI-016')}`,
      },
    },
  },
  argTypes: { paginaMudou: { action: 'paginaMudou' }, mostrados: { control: 'number' } },
  args: { pagina: 1, tamanhoPagina: 10, total: 42, etiquetaItens: 'procedimentos', mostrados: null },
  render: (args) => ({
    props: args,
    template: `<div style="max-width: 1100px; border: var(--borda-largura) solid var(--cor-borda); border-radius: var(--raio-lg); background: var(--cor-superficie)">
      <app-paginacao [pagina]="pagina" [tamanhoPagina]="tamanhoPagina" [total]="total" [etiquetaItens]="etiquetaItens" [mostrados]="mostrados" (paginaMudou)="paginaMudou($event)" />
    </div>`,
  }),
};

export default meta;
type Story = StoryObj<PaginacaoComponent>;

export const PoucasPaginas: Story = { name: 'Poucas páginas', args: { pagina: 2 } };
export const MuitasPaginas: Story = { name: 'Muitas páginas (página do meio)', args: { pagina: 5, total: 95 } };
export const Primeira: Story = { args: { pagina: 1, total: 95 } };
export const Ultima: Story = { name: 'Última', args: { pagina: 10, total: 95 } };
