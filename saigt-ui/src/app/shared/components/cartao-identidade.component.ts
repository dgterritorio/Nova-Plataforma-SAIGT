import { Component, Input } from '@angular/core';

/** Cartão de identidade partilhado pelas duas variantes do Perfil (`origem` INCM/AD_DGT). */
@Component({
  selector: 'app-cartao-identidade',
  standalone: true,
  styleUrl: './cartao-identidade.component.css',
  template: `
    <section class="cartao-identidade">
      <div class="identidade">
        <div class="avatar">{{ iniciais() }}</div>
        <div>
          <h2 class="nome">{{ nome ?? '—' }}</h2>
          @if (subtitulo) {
            <p class="subtitulo">{{ subtitulo }}</p>
          }
          @if (email) {
            <p class="email">{{ email }}</p>
          }
        </div>
      </div>
      <span class="estado" [class.estado-verde]="variante === 'ad-dgt'">
        <span class="ponto"></span>
        <span>{{ estadoTexto }}</span>
      </span>
    </section>
  `,
})
export class CartaoIdentidadeComponent {
  @Input() nome: string | null = null;
  @Input() subtitulo: string | null = null;
  @Input() email: string | null = null;
  @Input({ required: true }) estadoTexto!: string;
  @Input() variante: 'padrao' | 'ad-dgt' = 'padrao';

  iniciais(): string {
    if (!this.nome) {
      return '—';
    }
    const partes = this.nome.trim().split(/\s+/).filter(Boolean);
    if (partes.length === 0) {
      return '—';
    }
    const primeira = partes[0][0];
    const ultima = partes.length > 1 ? (partes.at(-1)?.[0] ?? '') : '';
    return (primeira + ultima).toUpperCase();
  }
}
