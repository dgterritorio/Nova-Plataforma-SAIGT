import type { Meta, StoryObj } from '@storybook/angular';
import { design, referenciaFigma } from '../../../../stories/figma';
import { Separador, SeparadoresComponent } from './separadores.component';

const SEPARADORES: Separador[] = [
  { id: 'perfis', etiqueta: 'Perfis e permissões', icone: 'icones/administracao/perfis.svg' },
  { id: 'utilizadores', etiqueta: 'Utilizadores', icone: 'icones/administracao/utilizadores.svg', contador: 4 },
];

const meta: Meta<SeparadoresComponent> = {
  title: 'Componentes/Separadores',
  component: SeparadoresComponent,
  tags: ['autodocs'],
  parameters: {
    design: design('UI-060'),
    docs: {
      description: {
        component: `Padrão WAI-ARIA tabs com ativação manual: as setas, Home e End mudam o foco; Enter, Espaço ou clique ativam. O painel fica no ecrã, com \`idPainel(id)\`. ${referenciaFigma('UI-060')}`,
      },
    },
  },
  argTypes: { mudou: { action: 'mudou' } },
  args: { separadores: SEPARADORES, ativo: 'utilizadores', rotulo: 'Utilizadores e permissões' },
  render: (args) => ({
    props: args,
    template: `
      <app-separadores #seps [separadores]="separadores" [(ativo)]="ativo" [rotulo]="rotulo" (mudou)="mudou($event)" />
      <div role="tabpanel" [id]="seps.idPainel(ativo)" [attr.aria-labelledby]="seps.idSeparador(ativo)" style="padding: var(--espaco-4) 0">
        Conteúdo do separador «{{ ativo }}».
      </div>`,
  }),
};

export default meta;
type Story = StoryObj<SeparadoresComponent>;

export const ComIconesEContadores: Story = { name: 'Com ícones e contadores' };
export const SemIcones: Story = {
  name: 'Sem ícones',
  args: {
    ativo: 'dados',
    separadores: [
      { id: 'dados', etiqueta: 'Dados gerais' },
      { id: 'documentos', etiqueta: 'Documentos', contador: 12 },
      { id: 'historico', etiqueta: 'Histórico' },
    ],
  },
};
