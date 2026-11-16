import { componentWrapperDecorator, type Preview } from '@storybook/angular';

const preview: Preview = {
  // Na app, os componentes vivem sobre cartões e modais brancos: o contraste mede-se sobre essa superfície.
  decorators: [componentWrapperDecorator((story) => `<div style="padding: var(--espaco-6); background: var(--cor-superficie)">${story}</div>`)],
  parameters: {
    layout: 'fullscreen',
    backgrounds: { disable: true },
    controls: { expanded: true },
    a11y: { test: 'error' },
    viewport: {
      options: {
        figma: { name: 'Figma (1577px)', styles: { width: '1577px', height: '1000px' } },
      },
    },
  },
  initialGlobals: { viewport: { value: 'figma', isRotated: false } },
};

export default preview;
