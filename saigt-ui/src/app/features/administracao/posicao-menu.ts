export interface PosicaoMenu {
  topo: number;
  direita: number;
}

const AFASTAMENTO_MENU_PX = 4;

/**
 * O menu "···" abre em `position: fixed` junto ao botão: dentro do cartão e da tabela (que cortam o que
 * transborda) a última linha ficava com o menu escondido.
 */
export function posicaoMenu(evento: Event): PosicaoMenu {
  const botao = (evento.currentTarget as HTMLElement | null)?.getBoundingClientRect();
  if (!botao) {
    return { topo: 0, direita: 0 };
  }
  return { topo: botao.bottom + AFASTAMENTO_MENU_PX, direita: window.innerWidth - botao.right };
}
