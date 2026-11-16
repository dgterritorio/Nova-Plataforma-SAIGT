import type { Meta, StoryObj } from '@storybook/angular';
import { design, referenciaFigma } from '../../../../stories/figma';
import { AlertaComponent, TipoAlerta } from './alerta.component';

interface ArgsAlerta {
  tipo: TipoAlerta;
  titulo: string | null;
  fechavel: boolean;
  mensagem: string;
  fechar: () => void;
}

const meta: Meta<ArgsAlerta> = {
  title: 'Componentes/Alerta',
  component: AlertaComponent,
  tags: ['autodocs'],
  parameters: {
    design: design('UI-026'),
    docs: {
      description: { component: `\`role="alert"\` no tipo erro e \`role="status"\` nos outros. ${referenciaFigma('UI-026', 'UI-050')}` },
    },
  },
  argTypes: {
    tipo: { control: 'inline-radio', options: ['info', 'sucesso', 'aviso', 'erro'] },
    fechar: { action: 'fechar' },
  },
  args: { tipo: 'info', titulo: null, fechavel: false, mensagem: 'A autenticação é feita através da INCM.' },
  render: (args) => ({
    props: args,
    template: `<div style="max-width: 480px"><app-alerta [tipo]="tipo" [titulo]="titulo" [fechavel]="fechavel" (fechar)="fechar()">{{ mensagem }}</app-alerta></div>`,
  }),
};

export default meta;
type Story = StoryObj<ArgsAlerta>;

export const Info: Story = {};
export const Sucesso: Story = { args: { tipo: 'sucesso', mensagem: 'Entidade escolhida. Pode continuar.' } };
export const Aviso: Story = { args: { tipo: 'aviso', titulo: 'Dados geridos pela INCM', mensagem: 'Alterações feitas no portal da INCM.' } };
export const Erro: Story = { args: { tipo: 'erro', titulo: 'Não foi possível entrar', mensagem: 'Email ou palavra-passe incorretos.' } };
export const Fechavel: Story = { name: 'Fechável', args: { tipo: 'sucesso', fechavel: true, mensagem: 'Sessão terminada com sucesso.' } };
