import { spawnSync } from 'node:child_process';
import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';

const raiz = process.cwd();
const ler = (caminho: string) => readFileSync(join(raiz, caminho), 'utf-8');

const TOKENS_SEMANTICOS = [
  '--cor-primaria', '--cor-primaria-hover', '--cor-primaria-contraste', '--cor-primaria-variante',
  '--cor-acento', '--cor-destaque',
  '--cor-texto', '--cor-texto-secundario', '--cor-texto-terciario', '--cor-texto-rotulo', '--cor-texto-nav',
  '--cor-fundo', '--cor-superficie', '--cor-superficie-alt', '--cor-sobreposicao',
  '--cor-borda', '--cor-borda-suave', '--cor-borda-controlo', '--borda-largura',
  '--cor-sucesso', '--cor-sucesso-fundo', '--cor-aviso', '--cor-aviso-fundo',
  '--cor-erro', '--cor-erro-fundo', '--cor-info', '--cor-info-fundo',
  '--raio-sm', '--raio-md', '--raio-lg', '--raio-xl', '--raio-pill',
  '--sombra-cartao', '--sombra-dropdown', '--sombra-modal', '--sombra-cabecalho',
  '--fonte-base', '--fonte-titulo',
  '--texto-xs', '--texto-sm', '--texto-base', '--texto-md', '--texto-lg', '--texto-xl',
  '--peso-normal', '--peso-medio', '--peso-semi', '--peso-negrito',
  '--espaco-0-5', '--espaco-1', '--espaco-1-5', '--espaco-2', '--espaco-2-5', '--espaco-3',
  '--espaco-4', '--espaco-5', '--espaco-6', '--espaco-7', '--espaco-8',
  '--altura-controlo', '--altura-controlo-sm',
  '--contentor-lista', '--contentor-formulario', '--contentor-assistente', '--anel-foco',
];

function declaracoes(css: string): Map<string, string> {
  const mapa = new Map<string, string>();
  for (const [, nome, valor] of css.matchAll(/(--[\w-]+)\s*:\s*([^;]+);/g)) {
    mapa.set(nome, valor.trim());
  }
  return mapa;
}

function stylelint(caminho: string, css: string): string[] {
  const resultado = spawnSync(
    process.execPath,
    [join(raiz, 'node_modules/stylelint/bin/stylelint.mjs'), '--stdin-filename', caminho, '--formatter', 'json'],
    { cwd: raiz, input: css, encoding: 'utf-8' },
  );
  const saida = JSON.parse(resultado.stdout || resultado.stderr || '[]') as { warnings: { rule: string }[] }[];
  return saida.flatMap((ficheiro) => ficheiro.warnings.map((aviso) => aviso.rule));
}

describe('camada semântica', () => {
  const semantica = declaracoes(ler('src/styles/semantica.css'));

  it('define todos os tokens semânticos', () => {
    expect(TOKENS_SEMANTICOS.filter((nome) => !semantica.has(nome))).toEqual([]);
  });

  it('só usa var(--…) ou dimensões numéricas, sem hex nem funções de cor', () => {
    const invalidos = [...semantica].filter(
      ([, valor]) => /#[0-9a-f]{3,8}\b|\b(rgba?|hsla?)\(/i.test(valor)
        || !/^(var\(--[\w-]+\)|\d+(px)?|\d+px solid var\(--[\w-]+\))$/.test(valor),
    );
    expect(invalidos).toEqual([]);
  });

  it('só aponta para tokens que existem em tokens.css, tokens-complementares.css ou fontes.css', () => {
    const brutos = new Set([
      ...declaracoes(ler('src/styles/tokens.css')).keys(),
      ...declaracoes(ler('src/styles/tokens-complementares.css')).keys(),
      ...declaracoes(ler('src/styles/fontes.css')).keys(),
      ...semantica.keys(),
    ]);
    const referidos = [...semantica.values()].flatMap((valor) => [...valor.matchAll(/var\((--[\w-]+)\)/g)].map((m) => m[1]));
    expect(referidos.filter((nome) => !brutos.has(nome))).toEqual([]);
  });
});

describe('fontes', () => {
  const fontes = ler('src/styles/fontes.css');
  const origens = [...fontes.matchAll(/url\("([^"]+)"\)/g)].map((m) => m[1]);

  it('serve as 10 fontes de /fontes/ com font-display: swap', () => {
    expect(origens).toHaveLength(10);
    expect(origens.every((url) => url.startsWith('/fontes/') && existsSync(join(raiz, 'public', url)))).toBe(true);
    expect(fontes.match(/font-display: swap/g)).toHaveLength(10);
  });

  it('distribui a licença OFL de cada família ao lado das fontes', () => {
    // A OFL obriga a redistribuir o texto da licença com as fontes.
    const familias = new Set(origens.map((url) => url.split('/').pop()!.split('-')[0]));
    const semLicenca = [...familias].filter((familia) => {
      const licenca = join(raiz, 'public/fontes', `OFL-${familia}.txt`);
      return !existsSync(licenca) || !readFileSync(licenca, 'utf-8').includes('SIL Open Font License, Version 1.1');
    });
    expect(semLicenca).toEqual([]);
  });
});

describe('estilos globais', () => {
  const globais = ler('src/styles.css');

  it('mantém os aliases antigos sobre os tokens semânticos', () => {
    const aliases = declaracoes(globais);
    expect(aliases.get('--color-bg')).toBe('var(--cor-superficie)');
    expect(aliases.get('--color-text')).toBe('var(--cor-texto)');
    expect(aliases.get('--color-primary')).toBe('var(--cor-primaria)');
  });

  it('aplica a fonte base, o fundo da página e o anel de foco', () => {
    expect(globais).toMatch(/body\s*{[^}]*font-family: var\(--fonte-base\)[^}]*var\(--cor-fundo\)/);
    expect(globais).toMatch(/:focus-visible\s*{[^}]*outline: var\(--anel-foco\)/);
  });

  it('o app-shell não tapa o fundo da página com um fundo próprio', () => {
    const shell = ler('src/app/shared/components/app-shell.component.css').match(/\.shell\s*{[^}]*}/)?.[0] ?? '';
    expect(shell).not.toMatch(/background/);
  });

  it('nenhum componente troca a fonte base pela fonte do sistema', () => {
    const ficheiros = readdirSync(join(raiz, 'src/app'), { recursive: true, encoding: 'utf-8' })
      .filter((ficheiro) => /\.(css|ts)$/.test(ficheiro) && !ficheiro.endsWith('.spec.ts'));
    const comFonteDoSistema = ficheiros.filter((ficheiro) => ler(join('src/app', ficheiro)).includes('-apple-system'));
    expect(comFonteDoSistema).toEqual([]);
    // Lê todos os ficheiros de src/app: num disco montado (Docker em Windows) passa dos 5s por omissão.
  }, 30_000);

  it('carrega tokens, tokens complementares, fontes, semântica e globais por esta ordem', () => {
    const angular = JSON.parse(ler('angular.json'));
    expect(angular.projects['saigt-ui'].architect.build.options.styles).toEqual([
      'src/styles/tokens.css',
      'src/styles/tokens-complementares.css',
      'src/styles/fontes.css',
      'src/styles/semantica.css',
      'src/styles.css',
    ]);
  });
});

// Cada chamada ao stylelint arranca um processo Node: no runner do CI leva 3–4s.
describe('lint de estilos', { timeout: 30_000 }, () => {
  it('falha com color-no-hex num CSS de shared/ui', () => {
    expect(stylelint('src/app/shared/ui/exemplo.css', '.a {\n  color: #ff0000;\n}\n')).toContain('color-no-hex');
  });

  it('proíbe rgb() e hsl() literais em shared/ui', () => {
    expect(stylelint('src/app/shared/ui/exemplo.css', '.a {\n  color: rgb(0 0 0);\n}\n')).toContain('function-disallowed-list');
  });

  it('ignora os CSS das features ainda não migradas', () => {
    expect(stylelint('src/app/features/processos/processo-historico/exemplo.css', '.a {\n  color: #ff0000;\n}\n')).toEqual([]);
  });

  it('aplica as regras de cor às features já migradas', () => {
    expect(stylelint('src/app/features/processos/processos.component.css', '.a {\n  color: #ff0000;\n}\n')).toContain('color-no-hex');
  });
});
