import { Component } from '@angular/core';

/** Legenda do rodapé de um ecrã com `app-marcador-estatico`: redação numa só fonte, nunca repetida em cada ecrã. */
@Component({
  selector: 'app-legenda-marcador-estatico',
  standalone: true,
  template: `<p class="legenda-marcador-estatico">* conteúdo de demonstração — ainda sem dados reais.</p>`,
  styles: [
    `
      .legenda-marcador-estatico {
        margin: var(--espaco-5) 0 0;
        font-size: var(--texto-rotulo);
        line-height: var(--texto-sm-linha);
        color: var(--cor-texto-celula);
      }
    `,
  ],
})
export class LegendaMarcadorEstaticoComponent {}
