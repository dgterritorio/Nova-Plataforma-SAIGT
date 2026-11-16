import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { moduleMetadata, type Meta, type StoryObj } from '@storybook/angular';
import { design, referenciaFigma } from '../../../../stories/figma';
import { CampoTextoComponent } from './campo-texto.component';

const meta: Meta<CampoTextoComponent> = {
  title: 'Componentes/Campo de texto',
  component: CampoTextoComponent,
  tags: ['autodocs'],
  decorators: [moduleMetadata({ imports: [ReactiveFormsModule] })],
  parameters: {
    design: design('UI-051'),
    docs: {
      description: {
        component: `Campo com rótulo ligado por \`for\`/\`id\`; ajuda e erro ligados por \`aria-describedby\`. Funciona com Reactive Forms (ControlValueAccessor). ${referenciaFigma('UI-051', 'UI-026', 'UI-069')}`,
      },
    },
  },
  argTypes: {
    tipo: { control: 'select', options: ['text', 'email', 'tel', 'number', 'date', 'password'] },
    rotulo: { control: 'text' },
    placeholder: { control: 'text' },
    ajuda: { control: 'text' },
    erro: { control: 'text' },
    maxlength: { control: 'number' },
    autocomplete: { control: 'text' },
  },
  args: {
    rotulo: 'Designação do processo',
    placeholder: 'Ex.: Alteração do PDM de Exemplo',
    obrigatorio: false,
    ajuda: null,
    erro: null,
    tipo: 'text',
    multilinha: false,
    compacto: false,
    preenchido: false,
    rotuloMaiusculas: false,
    maxlength: null,
    autocomplete: null,
  },
};

export default meta;
type Story = StoryObj<CampoTextoComponent>;

export const Normal: Story = {};
export const Obrigatorio: Story = { name: 'Obrigatório', args: { obrigatorio: true } };
export const ComAjuda: Story = { name: 'Com ajuda', args: { ajuda: 'Usa o nome que aparece no Diário da República.' } };
export const ComErro: Story = { name: 'Com erro', args: { obrigatorio: true, erro: 'Indica a designação do processo.' } };
export const Compacto: Story = { args: { compacto: true, rotulo: 'Motivo' }, parameters: { design: design('UI-069') } };
export const Multilinha: Story = { args: { multilinha: true, rotulo: 'Motivo', placeholder: 'Descreve o motivo da alteração' } };
export const PalavraPasse: Story = {
  name: 'Palavra-passe',
  args: { tipo: 'password', rotulo: 'Palavra-passe', placeholder: '', preenchido: true },
  parameters: { design: design('UI-026') },
};
export const RotuloEmMaiusculas: Story = { name: 'Rótulo em maiúsculas', args: { rotuloMaiusculas: true, rotulo: 'Cargo / função' } };

export const Desativado: Story = {
  render: (args) => ({
    props: { ...args, controlo: new FormControl({ value: 'Câmara Municipal de Exemplo', disabled: true }) },
    template: `<app-campo-texto [formControl]="controlo" [rotulo]="rotulo" [compacto]="compacto" />`,
  }),
  args: { rotulo: 'Entidade' },
};
