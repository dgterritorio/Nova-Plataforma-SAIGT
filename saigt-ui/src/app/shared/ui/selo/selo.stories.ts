import { moduleMetadata, type Meta, type StoryObj } from '@storybook/angular';
import { design, referenciaFigma } from '../../../../stories/figma';
import { ESTADOS_PROCESSO, EstadoProcessoBadgeComponent } from '../../components/estado-processo-badge.component';
import { SeloComponent, TomSelo } from './selo.component';

interface ArgsSelo {
  tom: TomSelo;
  forma: 'retangulo' | 'pill';
  tamanho: 'normal' | 'pequeno';
  texto: string;
}

const TONS: TomSelo[] = ['neutro', 'info', 'aviso', 'sucesso', 'erro', 'cinza', 'laranja', 'violeta', 'ciano'];

const meta: Meta<ArgsSelo> = {
  title: 'Componentes/Selo',
  component: SeloComponent,
  tags: ['autodocs'],
  decorators: [moduleMetadata({ imports: [EstadoProcessoBadgeComponent] })],
  parameters: {
    design: design('UI-016'),
    docs: { description: { component: `Etiqueta de estado. ${referenciaFigma('UI-016', 'UI-063')}` } },
  },
  argTypes: {
    tom: { control: 'select', options: TONS },
    forma: { control: 'inline-radio', options: ['retangulo', 'pill'] },
    tamanho: { control: 'inline-radio', options: ['normal', 'pequeno'] },
    texto: { control: 'text' },
  },
  args: { tom: 'info', forma: 'retangulo', tamanho: 'normal', texto: 'Em submissão' },
  render: (args) => ({
    props: args,
    template: `<app-selo [tom]="tom" [forma]="forma" [tamanho]="tamanho">{{ texto }}</app-selo>`,
  }),
};

export default meta;
type Story = StoryObj<ArgsSelo>;

export const Normal: Story = {};
export const Pequeno: Story = { args: { tom: 'sucesso', tamanho: 'pequeno', texto: 'Ativo' }, parameters: { design: design('UI-063') } };

export const TodosOsTons: Story = {
  name: 'Todos os tons',
  parameters: { controls: { disable: true } },
  render: () => ({
    props: { tons: TONS },
    template: `
      <table style="border-collapse: separate; border-spacing: var(--espaco-4) var(--espaco-3)">
        <thead><tr><th>Tom</th><th>Retângulo</th><th>Pill</th><th>Pequeno</th></tr></thead>
        <tbody>
          @for (tom of tons; track tom) {
            <tr>
              <th scope="row" style="text-align: left">{{ tom }}</th>
              <td><app-selo [tom]="tom">Selo {{ tom }}</app-selo></td>
              <td><app-selo [tom]="tom" forma="pill">Selo {{ tom }}</app-selo></td>
              <td><app-selo [tom]="tom" tamanho="pequeno">{{ tom }}</app-selo></td>
            </tr>
          }
        </tbody>
      </table>`,
  }),
};

export const EstadosDoProcesso: Story = {
  name: 'Estados do processo',
  parameters: {
    controls: { disable: true },
    docs: { description: { story: 'O `EstadoProcessoBadgeComponent` para cada estado do processo, com o tom e o ícone de cada um.' } },
  },
  render: () => ({
    props: { estados: ESTADOS_PROCESSO },
    template: `
      <ul style="display: grid; gap: var(--espaco-3); margin: 0; padding: 0; list-style: none">
        @for (estado of estados; track estado) {
          <li style="display: flex; gap: var(--espaco-4); align-items: center">
            <code style="width: 180px">{{ estado }}</code>
            <app-estado-processo-badge [estado]="estado" />
          </li>
        }
      </ul>`,
  }),
};
