import { moduleMetadata, type Meta, type StoryObj } from '@storybook/angular';
import { design, referenciaFigma } from '../../../../stories/figma';
import { BotaoComponent } from '../botao/botao.component';
import { ModalComponent } from './modal.component';

const meta: Meta<ModalComponent> = {
  title: 'Componentes/Modal',
  component: ModalComponent,
  tags: ['autodocs'],
  decorators: [moduleMetadata({ imports: [BotaoComponent] })],
  parameters: {
    design: design('UI-069'),
    layout: 'fullscreen',
    docs: {
      story: { inline: false, height: '520px' },
      description: {
        component: `\`role="dialog"\` com \`aria-modal\`: o foco fica preso na caixa, Esc e o × fecham, e o foco volta ao elemento que o abriu. Ações no slot \`[rodape]\`. ${referenciaFigma('UI-069', 'UI-050')}`,
      },
    },
  },
  argTypes: {
    subtitulo: { control: 'text' },
    largura: { control: 'inline-radio', options: ['sm', 'md', 'lg', 'xl'] },
    variante: { control: 'inline-radio', options: ['normal', 'confirmacao'] },
    fechar: { action: 'fechar' },
  },
  args: { aberto: true, titulo: 'Alterar estado do processo', subtitulo: '', largura: 'md', variante: 'normal', fecharComFundo: true },
  render: (args) => ({
    props: args,
    template: `
      <app-modal [aberto]="aberto" [titulo]="titulo" [subtitulo]="subtitulo" [largura]="largura" [variante]="variante" [fecharComFundo]="fecharComFundo" (fechar)="fechar()">
        <p style="margin: 0">O processo passa para o estado escolhido e o histórico regista quem fez a alteração.</p>
        <div rodape style="display: contents">
          <button app-botao type="button" variante="secundario" tamanho="pequeno">Cancelar</button>
          <button app-botao type="button" tamanho="pequeno">Confirmar</button>
        </div>
      </app-modal>`,
  }),
};

export default meta;
type Story = StoryObj<ModalComponent>;

export const Normal: Story = {};
export const Confirmacao: Story = {
  name: 'Confirmação',
  args: {
    variante: 'confirmacao',
    largura: 'sm',
    titulo: 'Confirmar o pedido?',
    subtitulo: 'Depois de confirmar, o pedido segue para a DGT.',
  },
  parameters: { design: design('UI-050') },
};
export const ComConteudoLongo: Story = {
  name: 'Com conteúdo longo',
  render: (args) => ({
    props: { ...args, paragrafos: Array.from({ length: 14 }, (_, i) => i + 1) },
    template: `
      <app-modal [aberto]="aberto" titulo="Termos de utilização" [subtitulo]="subtitulo" [largura]="largura" (fechar)="fechar()">
        @for (n of paragrafos; track n) {
          <p>Parágrafo {{ n }} de exemplo: o corpo do modal faz scroll e o cabeçalho e o rodapé ficam visíveis.</p>
        }
        <div rodape style="display: contents">
          <button app-botao type="button" variante="secundario" tamanho="pequeno">Recusar</button>
          <button app-botao type="button" tamanho="pequeno">Aceitar</button>
        </div>
      </app-modal>`,
  }),
};
