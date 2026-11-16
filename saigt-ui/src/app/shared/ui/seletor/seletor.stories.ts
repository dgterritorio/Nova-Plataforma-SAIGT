import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { moduleMetadata, type Meta, type StoryObj } from '@storybook/angular';
import { design, referenciaFigma } from '../../../../stories/figma';
import { OpcaoSeletor, SeletorComponent } from './seletor.component';

const ESTADOS: OpcaoSeletor[] = [
  { valor: 'EM_VALIDACAO_DGT', rotulo: 'Em validação DGT' },
  { valor: 'VALIDADO_DGT', rotulo: 'Validado DGT' },
  { valor: 'REJEITADO_DGT', rotulo: 'Rejeitado DGT' },
];

const meta: Meta<SeletorComponent> = {
  title: 'Componentes/Seletor',
  component: SeletorComponent,
  tags: ['autodocs'],
  decorators: [moduleMetadata({ imports: [ReactiveFormsModule] })],
  parameters: {
    design: design('UI-069'),
    docs: {
      description: {
        component: `\`<select>\` nativo: o teclado, o leitor de ecrã e o telemóvel já o sabem usar. Sem escolha, o valor é \`null\`. ${referenciaFigma('UI-069')}`,
      },
    },
  },
  args: { rotulo: 'Novo estado', opcoes: ESTADOS, placeholder: 'Selecione…', obrigatorio: false, erro: null, compacto: false },
  render: (args) => ({
    props: args,
    template: `<div style="max-width: 360px"><app-seletor [rotulo]="rotulo" [opcoes]="opcoes" [placeholder]="placeholder" [obrigatorio]="obrigatorio" [erro]="erro" [compacto]="compacto" /></div>`,
  }),
};

export default meta;
type Story = StoryObj<SeletorComponent>;

export const Normal: Story = { args: { obrigatorio: true } };
export const ComPlaceholder: Story = { name: 'Com placeholder', args: { placeholder: 'Escolha o estado de destino' } };
export const ComErro: Story = { name: 'Com erro', args: { obrigatorio: true, erro: 'Escolha o novo estado.' } };
export const Compacto: Story = { args: { compacto: true } };
export const Desativado: Story = {
  render: (args) => ({
    props: { ...args, controlo: new FormControl({ value: 'VALIDADO_DGT', disabled: true }) },
    template: `<div style="max-width: 360px"><app-seletor [formControl]="controlo" [rotulo]="rotulo" [opcoes]="opcoes" /></div>`,
  }),
};
