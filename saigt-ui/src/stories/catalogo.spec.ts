import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';

const raiz = process.cwd();
const ler = (caminho: string) => readFileSync(join(raiz, caminho), 'utf-8');
const pastaUi = 'src/app/shared/ui';
const componentes = readdirSync(join(raiz, pastaUi), { withFileTypes: true })
  .filter((entrada) => entrada.isDirectory())
  .map((entrada) => entrada.name);

describe('catálogo de componentes', () => {
  it('cada componente de shared/ui tem stories', () => {
    expect(componentes.filter((nome) => !existsSync(join(raiz, pastaUi, nome, `${nome}.stories.ts`)))).toEqual([]);
  });

  it.each(componentes)('as stories de %s têm autodocs e o link do Figma em parameters.design', (nome) => {
    const stories = ler(join(pastaUi, nome, `${nome}.stories.ts`));

    expect(stories).toMatch(/tags: \['autodocs'\]/);
    expect(stories).toMatch(/const meta[^]*?parameters: \{\s*design: design\('UI-\d{3}'\)/);
  });

  it('o link do Figma aponta para a pasta do ecrã no saigt-implementation', () => {
    const figma = ler('src/stories/figma.ts');
    const pastas = [...figma.matchAll(/'(UI-\d{3})': '([^']+)'/g)];

    expect(pastas.length).toBeGreaterThan(0);
    expect(pastas.filter(([, ui, pasta]) => !pasta.includes(`/${ui}_`))).toEqual([]);
    expect(figma).toContain('https://gitlab.conkord.net/delivery-iberia/saigt/saigt-implementation/-/tree/master/docs/design-to-build/mockups');
  });

  it('o index.ts de shared/ui reexporta todos os componentes', () => {
    const indice = ler(join(pastaUi, 'index.ts'));
    const ficheiros = componentes.flatMap((nome) =>
      readdirSync(join(raiz, pastaUi, nome))
        .filter((ficheiro) => /\.(component|directive)\.ts$/.test(ficheiro))
        .map((ficheiro) => `./${nome}/${ficheiro.replace(/\.ts$/, '')}`),
    );

    expect(ficheiros.filter((ficheiro) => !indice.includes(`'${ficheiro}'`))).toEqual([]);
  });

  it('as stories e as páginas ficam fora do build da app', () => {
    const tsconfigApp = JSON.parse(ler('tsconfig.app.json'));

    expect(tsconfigApp.files).toEqual(['src/main.ts']);
    expect(JSON.stringify(tsconfigApp.include ?? [])).not.toMatch(/stories|mdx/);
    expect(ler('src/main.ts')).not.toMatch(/stories/);
  });

  it('o catálogo gera-se em dist/catalogo, que não vai para o repositório', () => {
    const arquitetura = JSON.parse(ler('angular.json')).projects['saigt-ui'].architect;

    expect(arquitetura.storybook.options.port).toBe(6006);
    expect(arquitetura['build-storybook'].options.outputDir).toBe('dist/catalogo');
    expect(ler('.gitignore')).toMatch(/^dist\/$/m);
  });
});
