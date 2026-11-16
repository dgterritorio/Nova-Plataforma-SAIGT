import type { PerfilInterno } from '../../core/auth/auth.models';
import { ROUTES } from '../routes';

/** `rota` nula: o item ainda não tem página e aparece desativado. Com `itens`, abre um menu. */
export interface ItemMenu {
  rotulo: string;
  rota: string | null;
  itens?: ItemMenu[];
}

const PAINEL: ItemMenu = { rotulo: 'Painel Principal', rota: ROUTES.PAINEL };
const PROCESSOS: ItemMenu = { rotulo: 'Processos', rota: ROUTES.PROCESSOS };
const NOTIFICACOES: ItemMenu = { rotulo: 'Notificações', rota: ROUTES.NOTIFICACOES };
const PLANTAS_SAIGT: ItemMenu = { rotulo: 'Plantas SAIGT', rota: ROUTES.ADMINISTRACAO_PLANTAS };
const LISTAS_VALORES: ItemMenu = { rotulo: 'Listas de Valores', rota: ROUTES.ADMINISTRACAO_LISTA_VALORES };

/**
 * Figma + LLD, por perfil. Sem "Formulários" (fora do LLD) nem página de Instrumentos: o painel do Instrumento
 * abre-se a partir dos Processos. "Configuração de Procedimentos" vem do LLD (UI-PROC-008), não do Figma.
 * A entidade mantém "Processos" no topo, como no componente de cabeçalho do Figma.
 * Só o MASTER vê a gestão do backoffice: é o mesmo conjunto que o `adminGuard` deixa entrar. "Plantas SAIGT"
 * (`plantasGuard`) e "Listas de Valores" (`listaValoresGuard`, em leitura) também aparecem ao Técnico.
 * Técnico e Visitante não têm Centro de Notificações (`notificacoesGuard`).
 */
export function menuDoPerfil(perfilInterno: PerfilInterno | null): ItemMenu[] {
  switch (perfilInterno) {
    case null:
      return [PAINEL, PROCESSOS, NOTIFICACOES];
    case 'MASTER':
      return [
        PAINEL,
        NOTIFICACOES,
        {
          rotulo: 'Administração',
          rota: null,
          itens: [
            { rotulo: 'Análise e Relatórios', rota: ROUTES.ADMINISTRACAO_PAINEL },
            { rotulo: 'Gestão de Utilizadores', rota: ROUTES.ADMINISTRACAO_UTILIZADORES },
            { rotulo: 'Alteração de Owner', rota: ROUTES.ADMINISTRACAO_ALTERACAO_OWNER },
            PROCESSOS,
            { rotulo: 'Gestão de Entidades', rota: ROUTES.ADMINISTRACAO_ENTIDADES },
            { rotulo: 'Gestão de Notificações', rota: ROUTES.ADMINISTRACAO_NOTIFICACOES },
            LISTAS_VALORES,
            { rotulo: 'Workflows', rota: ROUTES.ADMINISTRACAO_WORKFLOWS },
            { rotulo: 'Configuração de Procedimentos', rota: ROUTES.ADMINISTRACAO_CONFIGURACAO },
            PLANTAS_SAIGT,
          ],
        },
      ];
    case 'TECNICO':
      return [PAINEL, { rotulo: 'Administração', rota: null, itens: [PROCESSOS, LISTAS_VALORES, PLANTAS_SAIGT] }];
    case 'VISITANTE':
      return [PAINEL, PROCESSOS];
  }
}

/** Um menu com submenu fica ativo quando a página atual é uma das suas. */
export function menuAtivo(item: ItemMenu, url: string): boolean {
  const caminho = url.split(/[?#]/)[0];
  return (item.itens ?? []).some((filho) => filho.rota !== null && (caminho === filho.rota || caminho.startsWith(`${filho.rota}/`)));
}
