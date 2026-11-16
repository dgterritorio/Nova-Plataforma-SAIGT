import { describe, expect, it } from 'vitest';
import { ItemMenu, menuAtivo, menuDoPerfil } from './app-shell.menu';

const rotulos = (itens: ItemMenu[]) => itens.map((item) => item.rotulo);
const administracao = (itens: ItemMenu[]) => itens.find((item) => item.rotulo === 'Administração');

describe('menuDoPerfil', () => {
  it('entidade: Painel Principal · Processos · Notificações, sem Administração', () => {
    const menu = menuDoPerfil(null);
    expect(rotulos(menu)).toEqual(['Painel Principal', 'Processos', 'Notificações']);
    expect(administracao(menu)).toBeUndefined();
  });

  it('DGT Master: Administração com os 9 itens, sem Formulários', () => {
    const menu = menuDoPerfil('MASTER');
    expect(rotulos(menu)).toEqual(['Painel Principal', 'Notificações', 'Administração']);
    expect(rotulos(administracao(menu)!.itens!)).toEqual([
      'Análise e Relatórios',
      'Gestão de Utilizadores',
      'Alteração de Owner',
      'Processos',
      'Gestão de Entidades',
      'Gestão de Notificações',
      'Listas de Valores',
      'Workflows',
      'Configuração de Procedimentos',
      'Plantas SAIGT',
    ]);
    expect(administracao(menu)!.itens!.map((item) => item.rota)).toEqual([
      '/administracao/painel',
      '/administracao/utilizadores',
      '/administracao/alteracao-owner',
      '/processos',
      '/administracao/entidades',
      '/administracao/notificacoes',
      '/administracao/lista-valores',
      '/administracao/workflows',
      '/administracao/configuracao-procedimentos',
      '/administracao/plantas',
    ]);
  });

  it.each([null, 'TECNICO', 'VISITANTE'] as const)('%s: nunca tem Gestão de Notificações', (perfil) => {
    const todos = menuDoPerfil(perfil).flatMap((item) => [item, ...(item.itens ?? [])]);
    expect(rotulos(todos)).not.toContain('Gestão de Notificações');
  });

  it.each([null, 'TECNICO', 'VISITANTE'] as const)('%s: nunca tem Configuração de Procedimentos', (perfil) => {
    const todos = menuDoPerfil(perfil).flatMap((item) => [item, ...(item.itens ?? [])]);
    expect(rotulos(todos)).not.toContain('Configuração de Procedimentos');
    expect(todos.map((item) => item.rota)).not.toContain('/administracao/configuracao-procedimentos');
  });

  it('DGT Técnico: Administração com Processos, Listas de Valores e Plantas SAIGT, sem Notificações', () => {
    const menu = menuDoPerfil('TECNICO');
    expect(rotulos(menu)).toEqual(['Painel Principal', 'Administração']);
    expect(rotulos(administracao(menu)!.itens!)).toEqual(['Processos', 'Listas de Valores', 'Plantas SAIGT']);
    expect(administracao(menu)!.itens![1].rota).toBe('/administracao/lista-valores');
    expect(administracao(menu)!.itens![2].rota).toBe('/administracao/plantas');
  });

  it('DGT Visitante: Processos no topo, sem Administração nem Notificações', () => {
    const menu = menuDoPerfil('VISITANTE');
    expect(rotulos(menu)).toEqual(['Painel Principal', 'Processos']);
    expect(administracao(menu)).toBeUndefined();
  });

  it.each([null, 'MASTER', 'TECNICO', 'VISITANTE'] as const)('%s: não tem a página de Instrumentos', (perfil) => {
    const todos = menuDoPerfil(perfil).flatMap((item) => [item, ...(item.itens ?? [])]);
    expect(rotulos(todos)).not.toContain('Instrumentos');
    expect(todos.map((item) => item.rota)).not.toContain('/instrumentos');
  });

  it.each([null, 'MASTER', 'TECNICO', 'VISITANTE'] as const)('%s: nunca tem Formulários', (perfil) => {
    const todos = menuDoPerfil(perfil).flatMap((item) => [item, ...(item.itens ?? [])]);
    expect(rotulos(todos)).not.toContain('Formulários');
  });

  it.each([null, 'MASTER'] as const)('%s: Notificações abre o Centro de Notificações', (perfil) => {
    expect(menuDoPerfil(perfil).find((item) => item.rotulo === 'Notificações')?.rota).toBe('/notificacoes');
  });
});

describe('menuAtivo', () => {
  const admin = administracao(menuDoPerfil('MASTER'))!;

  it('fica ativo numa página do submenu, com subpáginas e query string', () => {
    expect(menuAtivo(admin, '/administracao/utilizadores')).toBe(true);
    expect(menuAtivo(admin, '/processos/abc?x=1')).toBe(true);
  });

  it('não fica ativo noutras páginas nem por prefixo parcial', () => {
    expect(menuAtivo(admin, '/painel')).toBe(false);
    expect(menuAtivo(admin, '/processos-antigos')).toBe(false);
  });
});
