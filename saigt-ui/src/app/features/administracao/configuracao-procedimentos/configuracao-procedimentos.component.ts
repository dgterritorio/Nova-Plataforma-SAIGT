import { Component, ElementRef, inject, signal, viewChild } from '@angular/core';
import { AppShellComponent } from '../../../shared/components/app-shell.component';
import { Separador, SeparadoresComponent } from '../../../shared/ui/separadores/separadores.component';
import { ConfiguracaoAtosRedesComponent } from './configuracao-atos-redes.component';
import { ConfiguracaoDinamicasComponent } from './configuracao-dinamicas.component';
import { ConfiguracaoItensDocumentaisComponent, SelecaoProcedimento } from './configuracao-itens-documentais.component';
import { ConfiguracaoProcedimentosDinamicaComponent } from './configuracao-procedimentos-dinamica.component';
import { ConfiguracaoRegrasComponent } from './configuracao-regras.component';
import { ConfiguracaoTiposComponent } from './configuracao-tipos.component';

/** Ecrã do LLD (UI-PROC-008), sem Figma. Só o separador ativo fica montado: cada um carrega os seus dados ao abrir. */
@Component({
  selector: 'app-configuracao-procedimentos',
  imports: [
    AppShellComponent,
    SeparadoresComponent,
    ConfiguracaoTiposComponent,
    ConfiguracaoDinamicasComponent,
    ConfiguracaoProcedimentosDinamicaComponent,
    ConfiguracaoRegrasComponent,
    ConfiguracaoItensDocumentaisComponent,
    ConfiguracaoAtosRedesComponent,
  ],
  templateUrl: './configuracao-procedimentos.component.html',
  styleUrls: ['../backoffice.css', './configuracao-procedimentos.component.css'],
})
export class ConfiguracaoProcedimentosComponent {
  readonly separadores: readonly Separador[] = [
    { id: 'tipos', etiqueta: 'Tipos' },
    { id: 'dinamicas', etiqueta: 'Dinâmicas' },
    { id: 'procedimentos', etiqueta: 'Procedimentos' },
    { id: 'regras', etiqueta: 'Regras' },
    { id: 'itens-documentais', etiqueta: 'Itens documentais' },
    { id: 'atos-redes', etiqueta: 'Tipos de ato e redes' },
  ];
  readonly ativo = signal<string>('tipos');
  readonly selecaoItens = signal<SelecaoProcedimento | null>(null);

  private readonly separadoresRef = viewChild.required(SeparadoresComponent);
  private readonly anfitriao = inject<ElementRef<HTMLElement>>(ElementRef);

  /** O botão clicado desaparece com o separador dos Procedimentos: o foco passa para o separador aberto. */
  abrirItensDocumentais(selecao: SelecaoProcedimento): void {
    this.selecaoItens.set(selecao);
    this.ativo.set('itens-documentais');
    const id = this.separadoresRef().idSeparador('itens-documentais');
    setTimeout(() => this.anfitriao.nativeElement.querySelector<HTMLElement>(`#${id}`)?.focus());
  }

  /** Só a ação dos Procedimentos pré-escolhe; abrir o separador à mão começa do início. */
  mudarSeparador(): void {
    this.selecaoItens.set(null);
  }
}
