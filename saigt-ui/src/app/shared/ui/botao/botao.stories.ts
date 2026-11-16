import type { Meta, StoryObj } from '@storybook/angular';
import { design, referenciaFigma } from '../../../../stories/figma';
import { BotaoComponent, TamanhoBotao, VarianteBotao } from './botao.component';

interface ArgsBotao {
  variante: VarianteBotao;
  tamanho: TamanhoBotao;
  desativado: boolean;
  aCarregar: boolean;
  comIcone: boolean;
  texto: string;
}

const VARIANTES: VarianteBotao[] = ['primario', 'secundario', 'suave', 'contorno', 'texto', 'perigo'];
const ICONE = `<svg icone viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" aria-hidden="true"><path d="M8 3.5v9M3.5 8h9" /></svg>`;

const meta: Meta<ArgsBotao> = {
  title: 'Componentes/Botão',
  component: BotaoComponent,
  tags: ['autodocs'],
  parameters: {
    design: design('UI-016'),
    docs: {
      description: {
        component: `Seletor de atributo (\`button[app-botao]\`, \`a[app-botao]\`): o elemento continua nativo. ${referenciaFigma('UI-016', 'UI-026', 'UI-069')}`,
      },
    },
  },
  argTypes: {
    variante: { control: 'select', options: VARIANTES },
    tamanho: { control: 'inline-radio', options: ['normal', 'pequeno'] },
    desativado: { control: 'boolean' },
    aCarregar: { control: 'boolean' },
    comIcone: { control: 'boolean' },
    texto: { control: 'text' },
  },
  args: { variante: 'primario', tamanho: 'normal', desativado: false, aCarregar: false, comIcone: false, texto: 'Guardar' },
  render: (args) => ({
    props: args,
    template: `<button app-botao type="button" [variante]="variante" [tamanho]="tamanho" [desativado]="desativado" [aCarregar]="aCarregar">
      @if (comIcone) {${ICONE}}{{ texto }}</button>`,
  }),
};

export default meta;
type Story = StoryObj<ArgsBotao>;

export const Primario: Story = { name: 'Primário', args: { variante: 'primario' } };
export const Secundario: Story = { name: 'Secundário', args: { variante: 'secundario', texto: 'Cancelar' } };
export const Suave: Story = { args: { variante: 'suave', texto: 'Exportar CSV' }, parameters: { design: design('UI-058') } };
export const Contorno: Story = { args: { variante: 'contorno', texto: 'Filtros' } };
export const Texto: Story = { args: { variante: 'texto', texto: 'Ver detalhe' } };
export const Perigo: Story = { args: { variante: 'perigo', texto: 'Remover' }, parameters: { design: design('UI-069') } };
export const Pequeno: Story = { args: { tamanho: 'pequeno', texto: 'Confirmar' }, parameters: { design: design('UI-069') } };
export const ComIcone: Story = { name: 'Com ícone', args: { comIcone: true, texto: 'Criar Processo' } };
export const Desativado: Story = { args: { desativado: true } };
export const ACarregar: Story = { name: 'A carregar', args: { aCarregar: true, texto: 'A guardar' } };

export const Todos: Story = {
  parameters: { controls: { disable: true } },
  render: () => ({
    props: { variantes: VARIANTES },
    template: `
      <table style="border-collapse: separate; border-spacing: var(--espaco-4) var(--espaco-3)">
        <thead><tr><th></th><th>Normal</th><th>Pequeno</th><th>Com ícone</th><th>Desativado</th><th>A carregar</th></tr></thead>
        <tbody>
          @for (variante of variantes; track variante) {
            <tr>
              <th scope="row" style="text-align: left">{{ variante }}</th>
              <td><button app-botao type="button" [variante]="variante">Botão</button></td>
              <td><button app-botao type="button" [variante]="variante" tamanho="pequeno">Botão</button></td>
              <td><button app-botao type="button" [variante]="variante">
                <svg icone viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" aria-hidden="true"><path d="M8 3.5v9M3.5 8h9" /></svg>Botão</button></td>
              <td><button app-botao type="button" [variante]="variante" [desativado]="true">Botão</button></td>
              <td><button app-botao type="button" [variante]="variante" [aCarregar]="true">Botão</button></td>
            </tr>
          }
        </tbody>
      </table>`,
  }),
};
